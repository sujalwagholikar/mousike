/* Semantic Radio: real sources, meaningful feedback, browser-owned personalization. */
(() => {
  'use strict';
  const D = MousikeData,
    K = D.keys,
    PREFIX = 'aurora-intelligence-v2-';
  const defaults = {
    radio: true,
    overrideQueue: false,
    activity: true,
    autoFeed: true,
    feedInterval: 30
  };
  const settings = () => ({
    ...defaults,
    ...D.read(K.smart, {})
  });
  let events = D.read(K.events, []);
  if (!Array.isArray(events)) events = [];
  let learned = D.read(K.analysis, {}),
    feed = D.read(K.feed, {
      tracks: [],
      sections: []
    });
  if (!Array.isArray(feed?.tracks)) feed = {
    tracks: [],
    sections: []
  };
  feed.sections = Array.isArray(feed.sections) ? feed.sections : [];
  let lastAt = Number(localStorage.getItem(PREFIX + 'feed-at')) || 0;
  let activeRequest = null,
    fetching = false,
    nextRequest = null,
    discoveryBoost = false,
    signals = 0;
  let status = {
    configured: !!D.apiKey(),
    server_configured: false
  };
  if (localStorage.getItem(PREFIX + 'engine-version') !== 'mousike-3.0-home7') {
    // Invalidate only recommendations, retaining all preferences and collections.
    feed = {
      tracks: [],
      sections: []
    };
    lastAt = 0;
    localStorage.setItem(PREFIX + 'engine-version', 'mousike-3.0-home7');
  }
  const interval = () => [10, 30, 60, 360, 1440].includes(Number(settings().feedInterval)) ? Number(settings().feedInterval) * 60000 : 1800000;

  function profile() {
    const raw = D.read(K.profile, {}),
      p = {};
    for (const k of ['artists', 'languages', 'genres', 'moods', 'decades', 'discovery']) p[k] = raw[k];
    if (discoveryBoost) p.discovery = 95;
    return p;
  }

  function activitySnapshot() {
    const explicit = ['more_like', 'less_artist', 'not_interested', 'dislike'];
    if (!settings().activity) return events.filter(event => explicit.includes(event.kind)).slice(-500);
    const retained = events.slice(-500);
    const key = (kind, track) => kind + '|' + String(track?.id || track?.artist || track?.title || '').toLowerCase();
    const known = new Set(retained.map(event => key(event.kind, event.track)));
    const library = [];
    const timestamp = Date.now() / 1000;
    function add(kind, track) {
      const identity = key(kind, track);
      if (known.has(identity) || library.length >= 200 || !track?.title) return;
      known.add(identity);
      library.push({kind, track, at: timestamp - 86400, origin: 'saved-library'});
    }
    for (const track of D.tracks(D.read(K.likes, []), 100)) add('like', track);
    const playlists = D.read(K.playlists, []);
    let playlistSignals = 0;
    if (Array.isArray(playlists)) for (const playlist of playlists) {
      for (const track of D.tracks(playlist.tracks, 50)) {
        if (playlistSignals >= 60) break;
        const before = library.length;
        add('playlist_add', track);
        if (library.length > before) playlistSignals++;
      }
      if (playlistSignals >= 60) break;
    }
    const following = D.read(K.following, []);
    if (Array.isArray(following)) for (const name of following.slice(0, 20)) add('follow', {title: String(name), artist: String(name)});
    for (const track of D.tracks(D.read(K.history, []), 100)) add('play', track);
    return [...library, ...retained];
  }

  function payload(extra = {}) {
    return {
      profile: profile(),
      activity: activitySnapshot(),
      learned,
      api_key: D.apiKey(),
      current: state.current ? D.safeTrack(state.current) : {},
      ...extra
    };
  }
  async function post(path, extra = {}, timeout = 65000) {
    const ctl = new AbortController(),
      timer = setTimeout(() => ctl.abort(), timeout);
    try {
      return await apiJSON(path, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json'
        },
        body: JSON.stringify(payload(extra)),
        signal: ctl.signal
      });
    } finally {
      clearTimeout(timer);
    }
  }

  function record(kind, track = {}, extra = {}) {
    if (!settings().activity && !['more_like', 'less_artist', 'not_interested', 'dislike'].includes(kind)) return;
    const item = {
      kind,
      track: kind === 'search' ? {
        title: String(extra.query || '').slice(0, 120)
      } : D.safeTrack(track),
      at: Date.now() / 1000
    };
    if (!item.track) return;
    const prev = events.at(-1);
    if (prev && prev.kind === kind && prev.track?.id === item.track.id && prev.track?.title === item.track.title && item.at - prev.at < 3) return;
    events.push(item);
    events = events.slice(-500);
    D.write(K.events, events);
    signals++;
    window.dispatchEvent(new CustomEvent('mousike:taste-updated'));
    if (signals >= 6 && settings().autoFeed && Date.now() - lastAt > 180000) {
      signals = 0;
      refresh(false);
    }
  }
  window.addEventListener('mousike:playback', e => {
    if (['play', 'listen', 'complete', 'skip', 'like', 'unlike'].includes(e.detail.kind)) record(e.detail.kind, e.detail.track);
  });

  function pinWeekly(result) {
    const day = Math.floor(Date.now() / 86400000),
      week = Math.floor((day + 3) / 7);
    const signature = JSON.stringify(profile());
    const old = D.read('mousike-weekly-v3', {}),
      weekly = (result.sections || []).find(s => s.id === 'weekly');
    if (weekly && old.week === week && old.signature === signature && old.tracks?.length) weekly.tracks = D.tracks(old.tracks).filter(t => !events.slice(-100).some(e => ['dislike', 'not_interested'].includes(e.kind) && sameSong(e.track, t)));
    else if (weekly) D.write('mousike-weekly-v3', {
      week,
      signature,
      tracks: weekly.tracks
    });
    // Protect against a pinned weekly collection overlapping newly ranked shelves.
    const used = [...(weekly?.tracks || [])];
    for (const sec of result.sections || []) {
      if (sec.id === 'weekly') continue;
      sec.tracks = (sec.tracks || []).filter(t => {
        if (used.some(x => sameSong(t, x))) return false;
        used.push(t);
        return true;
      });
    }
    result.sections = (result.sections || []).filter(s => s.tracks?.length);
  }

  function saveFeed(result) {
    feed = result;
    D.write(K.feed, feed);
    lastAt = Date.now();
    localStorage.setItem(PREFIX + 'feed-at', String(lastAt));
    window.dispatchEvent(new CustomEvent('mousike:feed-updated', {
      detail: feed
    }));
  }
  async function refresh(force = true) {
    if (activeRequest) return activeRequest;
    if (!force && feed.tracks.length && Date.now() - lastAt < interval()) return feed;
    fetching = true;
    if (state.page === 'home') render();
    window.dispatchEvent(new CustomEvent('mousike:curation-start'));
    activeRequest = (async () => {
      try {
        const result = await post('/api/ai/feed');
        result.tracks = D.tracks(result.tracks, 70);
        if (!result.tracks.length && feed.tracks.length) {
          if (force) notify('Discovery sources are unavailable. Your saved mix is ready to play.');
          return feed;
        }
        pinWeekly(result);
        saveFeed(result);
        if (result.analysis) {
          learned = result.analysis;
          D.write(K.analysis, learned);
        }
        if (force && !result.tracks.length) notify(result.reason || 'Try a search while discovery sources reconnect.');
        loadAlbums(result.album_queries || []);
        return result;
      } catch (e) {
        if (force) notify(e.name === 'AbortError' ? 'Discovery is taking longer than usual. Try again shortly.' : e.message);
        return feed;
      } finally {
        fetching = false;
        activeRequest = null;
        discoveryBoost = false;
        if (state.page === 'home') render();
        window.dispatchEvent(new CustomEvent('mousike:curation-end'));
      }
    })();
    return activeRequest;
  }
  let albumSignature = '';
  async function loadAlbums(queries) {
    const names = [...new Set(queries)].slice(0, 3);
    if (!names.length) return;
    const signature = names.join('|');
    if (signature === albumSignature) return;
    albumSignature = signature;
    const cache = D.read('mousike-home-albums-v3', {});
    if (cache.signature === signature && Date.now() - cache.at < 6 * 3600000) {
      state.homeAlbums = cache.albums || [];
      if (state.page === 'home') render();
      return;
    }
    const groups = [];
    for (const name of names) {
      try {
        const result = await apiJSON('/api/albums?q=' + encodeURIComponent(name) + '&limit=4');
        groups.push(...(result.albums || []));
      } catch {}
    }
    const seen = new Set();
    state.homeAlbums = groups.filter(a => {
      const key = foldMusic(a.title + '|' + a.artist);
      if (seen.has(key)) return false;
      seen.add(key);
      return true;
    });
    D.write('mousike-home-albums-v3', {
      signature,
      at: Date.now(),
      albums: state.homeAlbums
    });
    if (state.page === 'home') render();
  }
  async function nextTrack() {
    if (!settings().radio) return null;
    const recent = [state.current, ...state.history.slice(0, 15), ...state.queue.slice(state.index + 1)].filter(Boolean);
    const local = feed.tracks.find(t => !recent.some(r => sameSong(t, r)) && trackPerformer(t) !== trackPerformer(state.current));
    if (local) return local;
    if (nextRequest) return nextRequest;
    nextRequest = post('/api/ai/next', {
      exclude: recent.map(t => t.id).filter(Boolean).slice(0, 100)
    }).then(r => r.track && !recent.some(t => sameSong(t, r.track)) ? r.track : null).catch(() => null).finally(() => {
      nextRequest = null;
    });
    return nextRequest;
  }
  async function feedback(kind, track) {
    if (kind === 'discover') return discover();
    const types = {
      'more-like': 'more_like',
      'not-interested': 'not_interested',
      'less-artist': 'less_artist'
    };
    const event = types[kind];
    if (!event || !track) return;
    record(event, track);
    const remove = t => kind === 'not-interested' ? sameSong(t, track) : kind === 'less-artist' ? trackPerformer(t) === trackPerformer(track) : false;
    if (kind !== 'more-like') {
      feed.tracks = feed.tracks.filter(t => !remove(t));
      for (const section of feed.sections || []) section.tracks = section.tracks.filter(t => !remove(t));
      D.write(K.feed, feed);
      loadPersonalHome();
      const upcoming = state.queue.slice(state.index + 1).filter(t => !remove(t));
      state.queue = state.queue.slice(0, state.index + 1).concat(upcoming);
      MousikePlayback.queueChanged();
    }
    notify(kind === 'more-like' ? 'More music with this feel.' : kind === 'less-artist' ? 'Giving other artists more room.' : 'This song will stay out of your recommendations.');
    refresh(true);
  }
  async function discover() {
    discoveryBoost = true;
    window.AuroraPremium?.go('home');
    await refresh(true);
  }
  async function playRadio() {
    if (!feed.tracks.length) await refresh(true);
    if (feed.tracks.length) return playAt(0, feed.tracks);
  }
  async function openInsights() {
    const dialog = document.getElementById('tasteDialog');
    if (!dialog) return;
    const body = dialog.querySelector('.taste-content');
    body.textContent = 'Reading your listening preferences…';
    dialog.showModal();
    try {
      const ins = await post('/api/ai/insights', {}, 10000);
      body.innerHTML = `<p class="taste-summary">${esc(ins.analysis?.summary||'Your selected tastes shape your mix.')}</p><div class="taste-tags">${(ins.analysis?.taste_tags||[]).map(t=>`<span>${esc(t)}</span>`).join('')}</div><div class="taste-stats"><div><strong>${ins.events||0}</strong><span>listening signals</span></div><div><strong>${ins.liked_count||0}</strong><span>likes</span></div><div><strong>${ins.completed_count||0}</strong><span>completed songs</span></div></div><div class="taste-axis-grid">${['long_term','short_term','genre','mood','language','artist'].map(axis=>`<div><h3>${esc(axis.replaceAll('_',' '))}</h3>${Object.entries(ins.preferences?.[axis]||{}).sort((a,b)=>b[1]-a[1]).slice(0,5).map(([label,value])=>`<p><span>${esc(label)}</span><meter min="0" max="12" value="${Math.max(0,value)}"></meter></p>`).join('') || '<p>More listening will refine this.</p>'
  }</div>`).join('')}</div>`;
    } catch(e){body.textContent=e.message;}
  }
  function homeMarkup() { return ''; }
  function libraryMarkup() {
    return '<section class="library-radio"><img src="/assets/brand/mousike-mark-static.svg?v=6" alt="" width="44" height="44"><div><h2>Your Radio</h2><p>One mix. Room for every part of your taste.</p></div><button class="soft-cta" data-smart="play-radio">Play mix</button></section>';
  }
  document.addEventListener('click', e => {
    const b = e.target.closest('[data-smart]');
    if (!b) return;
    if (b.dataset.smart === 'insights') openInsights();
    if (b.dataset.smart === 'play-radio') playRadio();
  });
  window.addEventListener('storage', e => {
    if (e.key === K.events) {
      events = D.read(K.events, []);
      if (!Array.isArray(events)) events = [];
    }
    if (e.key === K.analysis) learned = D.read(K.analysis, {});
    if (e.key === K.feed) {
      feed = D.read(K.feed, {
        tracks: [],
        sections: []
      });
      lastAt = Number(localStorage.getItem(PREFIX + 'feed-at')) || 0;
      loadPersonalHome();
    }
    if (e.key === K.profile) {
      tasteProfile = D.read(K.profile, {});
      refresh(true);
    }
    if (e.key === K.smart && settings().autoFeed && Date.now() - lastAt >= interval()) refresh(false);
  });
  window.AuroraSmart = {
    tracks: () => feed.tracks,
    feed: () => feed,
    fetching: () => fetching,
    settings,
    payload,
    record,
    feedback,
    discover,
    playRadio,
    nextTrack,
    refresh,
    homeMarkup,
    libraryMarkup,
    openInsights,
    get status() {
      return status;
    }
  };
  // One-time migration of existing likes/history/follows into bounded signals.
  if (!localStorage.getItem(PREFIX + 'imported-v1') && settings().activity) {
    const at = Date.now() / 1000;
    for (const [kind, tracks] of [
        ['play', state.history.slice(0, 50)],
        ['like', state.likes.slice(0, 60)]
      ]) tracks.forEach((t, i) => events.push({
      kind,
      track: D.safeTrack(t),
      at: at - i * 60,
      imported: true
    }));
    events = events.slice(-500);
    D.write(K.events, events);
    localStorage.setItem(PREFIX + 'imported-v1', '1');
  }
  apiJSON('/api/capabilities').then(cap => {
    status.server_configured = !!cap.server_key_configured;
    status.model = cap.gemini_model;
  }).catch(() => {});
  loadPersonalHome();
  if (feed.tracks.length) loadAlbums(feed.album_queries || profile().artists || []);
  if (settings().autoFeed && (!feed.tracks.length || Date.now() - lastAt >= interval())) refresh(false);
  setInterval(() => {
    if (settings().autoFeed && !document.hidden && Date.now() - lastAt >= interval()) refresh(false);
  }, 30000);
  document.addEventListener('visibilitychange', () => {
    if (!document.hidden && settings().autoFeed && Date.now() - lastAt >= interval()) refresh(false);
  });
})();
