/* Single playback owner: two decks, one queue, guarded transitions and recovery. */
'use strict';
const foldMusic = s => String(s || '').normalize('NFKC').toLocaleLowerCase().replace(/&/g, ' and ').replace(/[^\p{L}\p{N}\p{M}]+/gu, ' ').trim().replace(/\s+/g, ' ');

function canonicalTrack(t) {
  if (t?.canonical_title) return foldMusic(t.canonical_title);
  let value = String(t?.title || '').replace(/\b(official|music video|lyrics|lyric video|audio|video|remaster(?:ed)?|4k|hd|vevo|topic)\b/gi, ' ').replace(/\(\s*\)|\[\s*\]/g, ' ');
  const artists = [...(tasteProfile?.artists || []), ...Object.keys(window.MOUSIKE_ARTIST_IMAGES || {}), t?.performer, t?.artist].filter(Boolean).sort((a, b) => b.length - a.length);
  for (const artist of artists) value = value.replace(new RegExp('(?<![\\p{L}\\p{N}])' + artist.replace(/[.*+?^${}()|[\]\\]/g, '\\$&') + '(?![\\p{L}\\p{N}])', 'giu'), ' ');
  return foldMusic(value.split(/\s+[-–—|]\s+/).find(v => foldMusic(v)) || value);
}

function trackPerformer(t) {
  if (t?.performer) return foldMusic(t.performer);
  const text = ' ' + foldMusic((t?.title || '') + ' ' + (t?.artist || '')) + ' ';
  const name = [...(tasteProfile?.artists || []), ...Object.keys(window.MOUSIKE_ARTIST_IMAGES || {})].sort((a, b) => b.length - a.length).find(a => text.includes(' ' + foldMusic(a) + ' '));
  return foldMusic(name || String(t?.artist || '').replace(/\s*(?:- Topic|VEVO|Official)$/i, ''));
}

function sameSong(a, b) {
  if (!a || !b) return false;
  if (a.id && a.id === b.id) return true;
  if (a.fingerprint && a.fingerprint === b.fingerprint && a.fingerprint_type === b.fingerprint_type) {
    const da = Number(a.duration) || 0,
      db = Number(b.duration) || 0;
    if (!(da && db) || Math.abs(da - db) < 8) return true;
  }
  const x = canonicalTrack(a),
    y = canonicalTrack(b);
  if (!x || !y || x !== y) return false;
  const aa = trackPerformer(a),
    bb = trackPerformer(b);
  const ambiguous = a.performer_source === 'channel' || b.performer_source === 'channel';
  if (aa && bb && aa !== bb && !ambiguous && !/upload|music|lyrics|records|label|unknown/.test(aa + ' ' + bb)) return false;
  if (ambiguous && Math.min(x.length, y.length) < 8) return false;
  const da = Number(a.duration) || 0,
    db = Number(b.duration) || 0;
  return !(da && db) || Math.abs(da - db) <= Math.max(8, Math.min(da, db) * .055);
}
const decks = [$('#audio'), $('#audioNext')];
let playSequence = 0,
  pending = false,
  pendingTrack = null,
  transitioning = false,
  prepared = null,
  preparing = false;
let shuffleBag = [],
  played = [],
  recoveryCount = 0,
  session = null,
  tickAt = 0,
  saveAt = 0,
  lastPaint = 0;
let outgoingTimer = 0,
  sleepTimer = 0,
  restorePosition = 0,
  wantsPlayback = false;
let liveClip = null;
const clipStart = () => state.current?.segment ? Number(state.current.start) || 0 : 0;

function relativeClipDuration() {
  if (state.current?.segment) return Math.max(0, Number(state.current.end) - clipStart());
  return Number.isFinite(audio.duration) && audio.duration > 0 ? audio.duration : Number(state.current?.duration) || 0;
}

function relativeClipTime() {
  return Math.max(0, (Number(audio.currentTime) || 0) - clipStart());
}

function clipSeek(fraction) {
  if (pendingTrack) return;
  const duration = relativeClipDuration();
  if (duration > 0) {
    audio.currentTime = clipStart() + Math.max(0, Math.min(1, Number(fraction) || 0)) * Math.max(0, duration - .04);
    tickAt = performance.now();
    updateProgress();
  }
}

function emitPlayback(kind, track = state.current, extra = {}) {
  window.dispatchEvent(new CustomEvent('mousike:playback', {
    detail: {
      kind,
      track: {
        ...track
      },
      ...extra
    }
  }));
}

function saveSession() {
  if (!state.current) return;
  MousikeData.write('mousike-session-v3', {
    queue: state.queue.slice(0, 250),
    index: state.index,
    current: state.current,
    position: relativeClipTime(),
    shuffle: state.shuffle,
    repeat: state.repeat
  });
}

function finishSession(completed = false) {
  if (!session) return;
  if (session.duration > 0 && session.heard / session.duration >= .85) {
    if (!session.completed) {
      session.completed = true;
      emitPlayback('complete', session.track, {
        heard: session.heard
      });
    }
  } else if (!completed && session.heard >= 3 && !session.completed) emitPlayback('skip', session.track, {
    heard: session.heard
  });
  session = null;
}

function startSession(track) {
  session = {
    track: {
      ...track
    },
    heard: 0,
    duration: relativeClipDuration(),
    listened: false,
    completed: false
  };
  emitPlayback('play', track);
  tickAt = performance.now();
  state.history = [track, ...state.history.filter(t => !sameSong(t, track))].slice(0, 100);
  store('history', state.history);
  if (track?.id) played.push(track.id);
  if (played.length > 300) played = played.slice(-300);
}

function updateProgress() {
  const duration = pendingTrack ? Number(pendingTrack.duration) || 0 : relativeClipDuration(),
    position = pendingTrack ? 0 : Math.min(relativeClipTime(), duration || Infinity);
  $('#elapsed').textContent = fmt(position);
  $('#total').textContent = fmt(duration);
  if (!$('#seek').matches(':active')) $('#seek').value = duration > 0 ? position / duration * 100 : 0;
  $('#seek').setAttribute('aria-valuetext', `${fmt(position)} of ${fmt(duration)}`);
  if (navigator.mediaSession && duration > 0 && position <= duration) {
    try {
      navigator.mediaSession.setPositionState({
        duration,
        playbackRate: audio.playbackRate,
        position
      });
    } catch {}
  }
}

function updateNow() {
  const t = pendingTrack || state.current;
  const coverURL = safeImg(t);
  if ($('#cover').getAttribute('src') !== coverURL) $('#cover').src = coverURL;
  const player = document.querySelector('.premium-player');
  player.classList.toggle('is-resolving', !!pendingTrack);
  player.setAttribute('aria-busy', String(!!pendingTrack));
  const status = document.getElementById('playerLoadStatus');
  if (status) status.textContent = pendingTrack ? 'Preparing audio…' : '';
  const selection = String(t?.id || t?.title || '') + '|' + String(t?.start || 0);
  if (player.dataset.selection !== selection) {
    player.dataset.selection = selection;
    player.classList.remove('selection-changed');
    requestAnimationFrame(() => player.classList.add('selection-changed'));
  }
  $('#nowtitle').textContent = t?.title || 'Your next favorite awaits';
  $('#nowartist').textContent = t?.artist || 'Choose something to play';
  const liked = !!t && state.likes.some(x => sameSong(x, t));
  $('#likeNow').classList.toggle('on', liked);
  $('#likeNow').setAttribute('aria-pressed', String(liked));
  $('#toggle').innerHTML = `<svg class="ui-icon" aria-hidden="true"><use href="#i-${audio.paused?'play':'pause'}"/></svg>`;
  $('#toggle').setAttribute('aria-label', pendingTrack ? 'Cancel loading' : audio.paused ? 'Play' : 'Pause');
  $('#shuffle').classList.toggle('on', state.shuffle);
  $('#shuffle').setAttribute('aria-pressed', String(state.shuffle));
  $('#repeat').classList.toggle('on', state.repeat > 0);
  $('#repeat').dataset.mode = state.repeat;
  $('#repeat').title = ['Repeat off', 'Repeat queue', 'Repeat one'][state.repeat];
  $('#repeat').setAttribute('aria-label', $('#repeat').title);
  $('#repeat').setAttribute('aria-pressed', String(state.repeat > 0));
  document.body.classList.toggle('is-playing', !audio.paused);
  const animateLogo = !audio.paused && !audio.ended && audio.dataset.mousikePlaying === 'true' && !document.hidden && document.documentElement.dataset.mousikeMotion !== 'reduced';
  const brandSource = animateLogo ? '/assets/brand/mousike-mark-waveform.svg?v=6' : '/assets/brand/mousike-mark-static.svg?v=6';
  for (const logo of document.querySelectorAll('.brand > img, #playerBrandLogo')) {
    if (logo.getAttribute('src') !== brandSource) logo.src = brandSource;
    logo.dataset.playing = String(animateLogo);
  }
  if (navigator.mediaSession && t) {
    try {
      navigator.mediaSession.metadata = new MediaMetadata({
        title: t.title,
        artist: t.artist || '',
        album: t.album || '',
        artwork: [{
          src: new URL(safeImg(t), location.origin).href
        }]
      });
      navigator.mediaSession.playbackState = audio.paused ? 'paused' : 'playing';
    } catch {}
  }
  updateProgress();
  window.dispatchEvent(new CustomEvent('mousike:player-state'));
}

function toggleLike(t) {
  if (!t) return;
  const i = state.likes.findIndex(x => sameSong(x, t));
  if (i >= 0) state.likes.splice(i, 1);
  else state.likes.unshift(t);
  store('likes', state.likes);
  emitPlayback(i >= 0 ? 'unlike' : 'like', t);
  updateNow();
  render();
}

function dropPrepared() {
  prepared = null;
  const idle = decks.find(d => d !== audio);
  if (idle && !outgoingTimer) {
    idle.pause();
    idle.removeAttribute('src');
    idle.load();
  }
}

function shuffledIndex() {
  if (!shuffleBag.length) {
    shuffleBag = state.queue.map((_, i) => i).filter(i => i !== state.index && !played.includes(state.queue[i].id));
    for (let i = shuffleBag.length - 1; i > 0; i--) {
      const j = Math.floor(Math.random() * (i + 1));
      [shuffleBag[i], shuffleBag[j]] = [shuffleBag[j], shuffleBag[i]];
    }
  }
  while (shuffleBag.length) {
    const i = shuffleBag.pop();
    if (state.queue[i] && !sameSong(state.queue[i], state.current)) return i;
  }
  return -1;
}

function nextQueueIndex(commit = false) {
  if (state.shuffle) {
    if (commit) return shuffledIndex();
    if (prepared?.index >= 0) return prepared.index;
    return -1;
  }
  let i = state.index + 1;
  while (i < state.queue.length && sameSong(state.queue[i], state.current)) i++;
  if (i < state.queue.length) return i;
  if (state.repeat === 1) {
    i = 0;
    while (i < state.queue.length && sameSong(state.queue[i], state.current)) i++;
    return i < state.queue.length ? i : -1;
  }
  return -1;
}
async function resolveRecording(t) {
  if (t.segment) return t;
  if (t.unresolved || !/^[\w-]{11}$/.test(t.id)) {
    const params = new URLSearchParams({
      title: t.title,
      artist: t.artist || '',
      duration: String(Math.round(Number(t.duration) || 0))
    });
    const d = await apiJSON('/api/resolve?' + params);
    return {
      ...d.track,
      title: t.title,
      canonical_title: t.canonical_title || t.title,
      artist: t.artist,
      performer: t.performer || t.artist,
      album: t.album,
      image: t.image || d.track.image,
      unresolved: false
    };
  }
  return t;
}

function waitMedia(el, event, ms = 18000) {
  return new Promise((resolve, reject) => {
    const done = () => {
        cleanup();
        resolve();
      },
      bad = () => {
        cleanup();
        reject(Error('This recording could not be loaded'));
      };
    const timer = setTimeout(bad, ms);

    function cleanup() {
      clearTimeout(timer);
      el.removeEventListener(event, done);
      el.removeEventListener('error', bad);
    }
    el.addEventListener(event, done, {
      once: true
    });
    el.addEventListener('error', bad, {
      once: true
    });
  });
}
async function playAt(index, list, options = {}) {
  if (index < 0 || index >= list.length) return;
  const original = list[index];
  if (!original.original && !original.segment && !original.unresolved && (original.kind === 'long_video' || Number(original.duration) > 480)) return openHybrid(original.id);
  const serial = ++playSequence;
  pending = true;
  pendingTrack = original;
  $('#toggle').classList.add('loading');
  updateNow();
  try {
    MousikeAudio.resume();
    const t = prepared && sameSong(prepared.track, original) ? prepared.track : await resolveRecording(original);
    if (serial !== playSequence) return;
    pendingTrack = t;
    updateNow();
    const vid = t.segment ? t.source_video_id : t.id;
    if (!/^[\w-]{11}$/.test(vid)) throw Error('No playable source for this song');
    if (t.segment && !(Number(t.end) > Number(t.start))) throw Error('This song has no valid chapter boundaries');
    const sameSource = !!(state.current?.segment && t.segment && state.current.source_video_id === vid && audio.src && audio.readyState >= 1);
    const old = audio,
      oldTrack = state.current;
    let target = audio;
    if (!sameSource) {
      target = prepared && sameSong(prepared.track, t) && prepared.deck?.readyState >= 2 ? prepared.deck : decks.find(d => d !== audio);
      if (target.getAttribute('src') !== ('/api/stream/' + vid) || target.error) {
        target.pause();
        MousikeAudio.gain(target, 1);
        target.src = '/api/stream/' + vid;
        target.preload = 'auto';
        const metadata = waitMedia(target, 'loadedmetadata');
        target.load();
        await metadata;
      }
      if (serial !== playSequence) return;
      target.currentTime = t.segment ? Number(t.start) : options.position || 0;
      target.playbackRate = prefs.speed;
      target.volume = prefs.volume / 100;
      MousikeAudio.normalize(target, t);
      const fade = Math.min(12, Math.max(0, Number(options.fade) || 0));
      MousikeAudio.gain(target, fade > 0 ? 0 : 1);
      await target.play();
      if (serial !== playSequence) {
        target.pause();
        return;
      }
      if (fade > 0 && MousikeAudio.context && !old.paused) {
        MousikeAudio.gain(target, 1, fade);
        MousikeAudio.gain(old, 0, fade);
        clearTimeout(outgoingTimer);
        outgoingTimer = setTimeout(() => {
          old.pause();
          MousikeAudio.gain(old, 1);
          outgoingTimer = 0;
        }, fade * 1000 + 80);
      } else {
        old.pause();
        clearTimeout(outgoingTimer);
        outgoingTimer = 0;
        MousikeAudio.gain(old, 1);
      }
      old.id = 'audioNext';
      target.id = 'audio';
      audio = target;
    } else {
      const start = Number(t.start);
      // Consecutive published chapters continue on the same decoded media stream.
      if (Math.abs(audio.currentTime - start) > .35) audio.currentTime = start;
      if (audio.paused) await audio.play();
    }
    finishSession(!!options.natural);
    state.queue = [...list];
    state.queue[index] = t;
    state.index = index;
    state.current = t;
    pendingTrack = null;
    liveClip = t.segment ? {
      start: Number(t.start),
      end: Number(t.end),
      video: vid
    } : null;
    if (!options.keepBag) {
      shuffleBag = [];
      played = [];
    }
    prepared = null;
    recoveryCount = 0;
    wantsPlayback = true;
    startSession(t);
    saveSession();
    updateNow();
    window.dispatchEvent(new CustomEvent('mousike:track-changed', {
      detail: {
        track: t,
        previous: oldTrack
      }
    }));
    if (['queue', 'album', 'playlist'].includes(state.page)) render();
    setTimeout(prepareNext, 900);
  } catch (e) {
    if (serial !== playSequence) return;
    notify(e.name === 'NotAllowedError' ? 'Press Play to allow audio in this browser.' : e.message || 'This recording is unavailable');
    emitPlayback('unavailable', original);
  } finally {
    if (serial === playSequence) {
      pending = false;
      pendingTrack = null;
      transitioning = false;
      $('#toggle').classList.remove('loading');
      updateNow();
    }
  }
}
async function prepareNext() {
  if (preparing || prepared || !state.current || pending || outgoingTimer) return;
  preparing = true;
  const serial = playSequence;
  try {
    const adaptive = window.AuroraSmart?.settings().overrideQueue;
    let index = adaptive ? -1 : nextQueueIndex(false);
    if (state.shuffle && !adaptive) {
      index = shuffledIndex();
      if (index >= 0) shuffleBag.push(index);
    }
    let track = index >= 0 ? state.queue[index] : await window.AuroraSmart?.nextTrack();
    if (!track || sameSong(track, state.current)) return;
    track = await resolveRecording(track);
    if (serial !== playSequence) return;
    const vid = track.segment ? track.source_video_id : track.id;
    if (!/^[\w-]{11}$/.test(vid)) return;
    await apiJSON('/api/source/' + vid);
    if (serial !== playSequence) return;
    const deck = decks.find(d => d !== audio);
    prepared = {
      track,
      index,
      deck: null
    };
    if (prefs.prebuffer || Number(prefs.crossfade) > 0) {
      deck.pause();
      deck.preload = 'auto';
      deck.src = '/api/stream/' + vid;
      deck.load();
      prepared.deck = deck;
    }
  } catch {
    /* Preparation cannot interrupt a playing song. */
  } finally {
    preparing = false;
  }
}
async function next(options = {}) {
  if (transitioning || pending) return;
  transitioning = true;
  try {
    if (options.natural && state.repeat === 2) {
      clipSeek(0);
      finishSession(true);
      startSession(state.current);
      await audio.play();
      return;
    }
    const adaptive = window.AuroraSmart?.settings().overrideQueue;
    const index = adaptive ? -1 : nextQueueIndex(true);
    if (index >= 0) return await playAt(index, state.queue, {
      ...options,
      keepBag: true
    });
    const track = prepared?.index === -1 ? prepared.track : await window.AuroraSmart?.nextTrack();
    if (track && !sameSong(track, state.current)) {
      const queue = state.queue.slice(0, state.index + 1).concat(track);
      return await playAt(queue.length - 1, queue, {
        ...options,
        keepBag: true
      });
    }
    if (options.natural) finishSession(true);
    audio.pause();
    wantsPlayback = false;
    saveSession();
    updateNow();
    if (!options.natural) notify('Your queue is clear. Discover more music to keep listening.');
  } finally {
    transitioning = false;
  }
}

function prev() {
  if (relativeClipTime() > 3) {
    clipSeek(0);
    return;
  }
  if (state.index > 0) playAt(state.index - 1, state.queue, {
    keepBag: true
  });
  else clipSeek(0);
}
async function togglePlayback() {
  if (pending) {
    ++playSequence;
    pending = false;
    pendingTrack = null;
    transitioning = false;
    wantsPlayback = false;
    decks.forEach(deck => deck.pause());
    $('#toggle').classList.remove('loading');
    updateNow();
    return;
  }
  MousikeAudio.resume();
  if (!state.current) {
    if (state.tracks.length) return playAt(0, state.tracks);
    return notify('Choose a song or start Smart Radio.');
  }
  if (!audio.getAttribute('src')) return playAt(state.index, state.queue, {
    position: restorePosition
  });
  if (audio.paused) {
    wantsPlayback = true;
    try {
      await audio.play();
    } catch {
      wantsPlayback = false;
      notify('This song could not start. Try the next recording.');
    }
  } else {
    wantsPlayback = false;
    audio.pause();
    decks.filter(d => d !== audio).forEach(d => d.pause());
    clearTimeout(outgoingTimer);
    outgoingTimer = 0;
    MousikeAudio.gain(audio, 1);
  }
  updateNow();
  saveSession();
}
async function recover(el) {
  if (el !== audio || pending || !state.current) return;
  if (recoveryCount >= 2) {
    wantsPlayback = false;
    audio.pause();
    notify('This recording is unavailable. Choose another song or open its source.');
    updateNow();
    return;
  }
  const seq = playSequence,
    pos = audio.currentTime,
    wasPlaying = wantsPlayback;
  recoveryCount++;
  try {
    const vid = state.current.segment ? state.current.source_video_id : state.current.id;
    await apiJSON('/api/source/' + vid + '?refresh=true');
    if (seq !== playSequence) return;
    const ready = waitMedia(audio, 'loadedmetadata');
    audio.src = '/api/stream/' + vid + '?retry=' + recoveryCount;
    audio.load();
    await ready;
    if (seq !== playSequence) return;
    audio.currentTime = pos;
    if (wasPlaying) await audio.play();
    updateNow();
  } catch {
    if (seq === playSequence) recover(audio);
  }
}

function monitor() {
  const now = performance.now(),
    delta = tickAt ? Math.min(1, (now - tickAt) / 1000) : 0;
  tickAt = now;
  if (!audio.paused && !audio.seeking && audio.readyState >= 3 && session) {
    MousikeAudio.level(audio, delta);
    session.heard += delta;
    session.duration = relativeClipDuration();
    if (!session.listened && session.heard >= Math.min(30, Math.max(12, session.duration * .2))) {
      session.listened = true;
      emitPlayback('listen', session.track, {
        heard: session.heard
      });
    }
    if (!session.completed && session.duration > 0 && session.heard >= session.duration * .85) {
      session.completed = true;
      emitPlayback('complete', session.track, {
        heard: session.heard
      });
    }
  }
  if (!audio.paused && !pending && !transitioning) {
    const remaining = relativeClipDuration() - relativeClipTime();
    const fade = Math.max(0, Math.min(12, Number(prefs.crossfade) || 0));
    const nextTrack = prepared?.track;
    const adjacent = !!(state.current?.segment && nextTrack?.segment && nextTrack.source_video_id === state.current.source_video_id);
    if (fade > 0 && remaining > 0 && remaining <= fade && prepared?.deck?.readyState >= 3 && !adjacent && state.repeat !== 2 && prefs.autoplay) next({
      natural: true,
      fade: remaining
    });
    else if (state.current?.segment && remaining <= .035) {
      if (prefs.autoplay || state.repeat === 2) next({
        natural: true
      });
      else {
        audio.pause();
        finishSession(true);
        updateNow();
      }
    }
  }
  if (now - lastPaint > 250) {
    updateProgress();
    lastPaint = now;
    window.dispatchEvent(new CustomEvent('mousike:progress'));
  }
  if (now - saveAt > 5000) {
    saveSession();
    saveAt = now;
  }
}
for (const deck of decks) {
  for (const name of ['play', 'pause', 'playing', 'waiting', 'stalled', 'seeking', 'seeked', 'ended', 'error', 'loadedmetadata', 'volumechange']) deck.addEventListener(name, () => {
    if (name === 'playing' || (name === 'seeked' && !deck.paused && deck.readyState >= 3)) deck.dataset.mousikePlaying = 'true';
    if (['pause', 'waiting', 'seeking', 'ended', 'error'].includes(name)) deck.dataset.mousikePlaying = 'false';
    if (deck === audio) updateNow();
  });
  deck.addEventListener('timeupdate', () => {
    if (deck === audio) monitor();
  });
  deck.addEventListener('ended', () => {
    if (deck !== audio || transitioning || pending) return;
    if (prefs.autoplay || state.repeat) next({
      natural: true
    });
    else {
      finishSession(true);
      updateNow();
    }
  });
  deck.addEventListener('error', () => recover(deck));
}
setInterval(() => {
  if (!audio.paused) monitor();
}, 100);
$('#toggle').onclick = togglePlayback;
$('#prev').onclick = prev;
$('#next').onclick = () => next();
$('#shuffle').onclick = () => {
  state.shuffle = !state.shuffle;
  shuffleBag = [];
  played = [state.current?.id];
  dropPrepared();
  updateNow();
  saveSession();
  prepareNext();
};
$('#repeat').onclick = () => {
  state.repeat = (state.repeat + 1) % 3;
  updateNow();
  saveSession();
};
$('#likeNow').onclick = () => toggleLike(pendingTrack || state.current);
$('#seek').oninput = e => clipSeek(Number(e.target.value) / 100);
$('#volume').oninput = e => {
  prefs.volume = Number(e.target.value);
  decks.forEach(d => d.volume = prefs.volume / 100);
};
$('#volume').onchange = () => savePrefs();
$('#openExpanded').onclick = () => expanded(true);
$('#cover').onclick = () => expanded(true);
$('#nowtitle').onclick = () => expanded(true);
$('#queueBtn').onclick = () => window.AuroraPremium?.go('queue');
$('#studioQuick').onclick = () => window.MousikeSettings?.open('audio');

function setSleep(minutes) {
  clearTimeout(sleepTimer);
  if (minutes > 0) sleepTimer = setTimeout(() => {
    decks.forEach(d => d.pause());
    notify('Sleep timer finished');
    updateNow();
  }, Math.min(180, minutes) * 60000);
}

function preferencesChanged() {
  normalizeDeck(audio, state.current);
  setSleep(Number(prefs.sleep) || 0);
  applyStudio();
  dropPrepared();
  if (state.current) prepareNext();
}
window.MousikePlayback = {
  get media() {
    return audio;
  },
  playAt,
  next,
  prev,
  toggle: togglePlayback,
  seek: clipSeek,
  sameSong,
  canonical: canonicalTrack,
  prepareNext,
  get status() {
    return {
      pending,
      pendingTrack,
      prepared: !!prepared,
      position: relativeClipTime(),
      duration: relativeClipDuration()
    };
  },
  preferencesChanged,
  queueChanged: () => {
    dropPrepared();
    shuffleBag = [];
    saveSession();
    prepareNext();
    window.dispatchEvent(new CustomEvent('mousike:queue-changed'));
  }
};
window.addEventListener('pagehide', saveSession);
const saved = MousikeData.read('mousike-session-v3', null);
if (saved && Array.isArray(saved.queue)) {
  state.queue = MousikeData.tracks(saved.queue, 250);
  state.index = Math.min(state.queue.length - 1, Math.max(0, Number(saved.index) || 0));
  state.current = state.queue[state.index] || null;
  restorePosition = Math.max(0, Number(saved.position) || 0);
  state.shuffle = !!saved.shuffle;
  state.repeat = [0, 1, 2].includes(saved.repeat) ? saved.repeat : 0;
  updateNow();
}
if (navigator.mediaSession)
  for (const [action, handler] of Object.entries({
      play: togglePlayback,
      pause: () => {
        wantsPlayback = false;
        audio.pause();
        updateNow();
      },
      previoustrack: prev,
      nexttrack: () => next(),
      seekto: e => clipSeek(e.seekTime / relativeClipDuration())
    })) try {
    navigator.mediaSession.setActionHandler(action, handler);
  } catch {}
// One shortcut handler avoids duplicate Space toggles inherited from earlier builds.
document.addEventListener('keydown', e => {
  const editing = e.target.closest('input,textarea,select,[contenteditable="true"]');
  if (editing) return;
  if (e.code === 'Space' && !e.target.closest('button')) {
    e.preventDefault();
    togglePlayback();
  } else if (e.altKey && e.key === 'ArrowRight') {
    e.preventDefault();
    next();
  } else if (e.altKey && e.key === 'ArrowLeft') {
    e.preventDefault();
    prev();
  } else if (e.key === '/') {
    e.preventDefault();
    $('#query').focus();
  } else if (e.key.toLowerCase() === 'e' && !e.ctrlKey && !e.metaKey) window.MousikeSettings?.open('audio');
});
updateNow();

document.addEventListener("visibilitychange", updateNow);
