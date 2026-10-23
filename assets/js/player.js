/* Premium player view. All audio and queue mutations run in its parent session. */
(() => {
  'use strict';
  const $ = id => document.getElementById(id),
    esc = s => String(s ?? '').replace(/[&<>"']/g, c => ({
      '&': '&amp;',
      '<': '&lt;',
      '>': '&gt;',
      '"': '&quot;',
      "'": '&#39;'
    } [c]));
  const fmt = n => `${Math.floor(Math.max(0,n||0)/60)}:${String(Math.floor(Math.max(0,n||0)%60)).padStart(2,'0')}`;
  const icon = n => `<svg class="ui-icon" aria-hidden="true"><use href="#i-${n}"/></svg>`;
  let data = {},
    trackID = '',
    queueKey = '',
    dragging = false,
    lines = [],
    activeLine = -1,
    lyricsSerial = 0,
    lastLogo = '';
  const fold = s => String(s || '').normalize('NFKC').toLocaleLowerCase().replace(/[^\p{L}\p{N}\p{M}]+/gu, ' ').trim();
  const lyricKey = t => fold(t?.artist) + '|' + fold(t?.canonical_title || t?.title);
  const art = t => {
    const url = String(t?.image || '');
    return (url.startsWith('/assets/') || url.startsWith('https://')) && !/[\s"'<>()[\]\\]/.test(url) ? url : '/assets/images/placeholder.svg';
  };

  function cmd(action, extra = {}) {
    if (parent !== window) parent.postMessage({
      type: 'mousike:player:cmd',
      action,
      ...extra
    }, location.origin);
    else notice('Open Now Playing from the Mousike music window.');
  }
  let toastTimer;

  function notice(text) {
    $('toast').textContent = text;
    $('toast').classList.add('visible');
    clearTimeout(toastTimer);
    toastTimer = setTimeout(() => $('toast').classList.remove('visible'), 3500);
  }

  function show(state) {
    data = state || {};
    const track = data.current;
    $('song').textContent = track?.title || 'Your next favorite awaits';
    $('artist').textContent = track?.artist || 'Choose something to play';
    const id = (track?.id || '') + '|' + (track?.title || '');
    if (id !== trackID) {
      trackID = id;
      $('art').src = art(track);
      $('ambient').style.backgroundImage = `url("${art(track)}")`;
      loadLyrics(track);
    }
    $('playIcon').innerHTML = data.paused ? '<path d="m8 5 12 7-12 7Z" fill="currentColor" stroke="none"/>' : '<path d="M7 5h4v14H7zm7 0h4v14h-4z" fill="currentColor" stroke="none"/>';
    $('play').setAttribute('aria-label', data.paused ? 'Play' : 'Pause');
    $('play').classList.toggle('pending', !!data.pending);
    const reduce = document.documentElement.dataset.mousikeMotion === 'reduced' || matchMedia('(prefers-reduced-motion: reduce)').matches;
    const logo = data.paused || !data.audiblePlaying || reduce || document.hidden ? 'static' : 'waveform';
    if (logo !== lastLogo) {
      $('movingLogo').src = '/assets/brand/mousike-mark-' + logo + '.svg?v=6';
      lastLogo = logo;
    }
    $('playingLabel').textContent = data.paused ? 'Ready when you are' : 'Sound In motion';
    $('strategy').textContent = data.aiUsed ? 'ENHANCED DISCOVERY' : 'SEMANTIC RADIO';
    $('radioMode').textContent = data.aiUsed ? 'A little more room for discovery' : 'Shaped by your listening';
    $('listeningStatus').textContent = data.paused ? 'PAUSED' : 'PLAYING';
    $('elapsed').textContent = fmt(data.position);
    $('duration').textContent = fmt(data.duration);
    if (!dragging) $('seek').value = data.duration > 0 ? data.position / data.duration * 1000 : 0;
    $('seek').setAttribute('aria-valuetext', `${fmt(data.position)} of ${fmt(data.duration)}`);
    $('volume').value = Math.round((data.volume ?? .75) * 100);
    $('shuffle').classList.toggle('active', !!data.shuffle);
    $('shuffle').setAttribute('aria-pressed', String(!!data.shuffle));
    $('repeat').classList.toggle('active', !!data.repeat);
    $('repeat').setAttribute('aria-pressed', String(!!data.repeat));
    $('repeat').dataset.mode = data.repeat;
    $('repeat').title = ['Repeat off', 'Repeat queue', 'Repeat one'][data.repeat || 0];
    $('repeat').setAttribute('aria-label', $('repeat').title);
    $('like').classList.toggle('on', !!data.liked);
    $('like').setAttribute('aria-pressed', String(!!data.liked));
    const vid = track?.source_video_id || track?.id;
    const source = /^[\w-]{11}$/.test(vid || '') ? 'https://www.youtube.com/watch?v=' + vid : 'https://www.youtube.com/';
    $('sourceLink').href = source;
    const key = JSON.stringify([data.queueOffset, (data.queue || []).map(t => [t.id, t.title])]);
    if (key !== queueKey) {
      queueKey = key;
      renderQueue();
    }
    updateLyrics();
    if (!spectrumFrame) spectrumFrame = requestAnimationFrame(drawSpectrum);
  }

  let spectrumFrame = 0,
    spectrumTime = 0,
    spectrumValues = new Float32Array(32);

  function drawSpectrum(now = 0) {
    const canvas = $('spectrum'),
      ctx = canvas.getContext('2d');
    spectrumFrame = 0;
    if (!ctx) return;
    if (!data.spectrum?.length || data.paused || document.hidden || document.documentElement.dataset.mousikeMotion === 'reduced' || matchMedia('(prefers-reduced-motion: reduce)').matches) {
      ctx.clearRect(0, 0, canvas.width, canvas.height);
      canvas.hidden = true;
      return;
    }
    canvas.hidden = false;
    if (now - spectrumTime > 30) {
      ctx.clearRect(0, 0, canvas.width, canvas.height);
      ctx.fillStyle = getComputedStyle(document.documentElement).getPropertyValue('--accent').trim() || '#c8e6a0';
      for (let i = 0; i < 32; i++) {
        spectrumValues[i] += (data.spectrum[i] - spectrumValues[i]) * .3;
        const height = Math.max(1, spectrumValues[i] / 255 * canvas.height);
        ctx.globalAlpha = .25 + spectrumValues[i] / 510;
        ctx.fillRect(i * canvas.width / 32, (canvas.height - height) / 2, canvas.width / 32 - 5, height);
      }
      spectrumTime = now;
    }
    spectrumFrame = requestAnimationFrame(drawSpectrum);
  }
  document.addEventListener('visibilitychange', () => {
    if (!document.hidden && !spectrumFrame) spectrumFrame = requestAnimationFrame(drawSpectrum);
  });

  function renderQueue() {
    const queue = data.queue || [];
    const offset = data.queueOffset || 0;
    $('queueCount').textContent = String(data.queueTotal ?? queue.length);
    $('queue').innerHTML = queue.length ? queue.map((t, i) => `<div class="queue-song"><button class="queue-play" data-queue-play="${offset+i}" aria-label="Play ${esc(t.title)}"><span class="num">${String(i+1).padStart(2,'0')}</span><img src="${esc(art(t))}" alt="" loading="lazy"><span class="details"><strong>${esc(t.title)}</strong><small>${esc(t.artist||'Unknown artist')}</small></span></button><div class="queue-tools"><button data-move="${offset+i}" data-direction="-1" aria-label="Move ${esc(t.title)} earlier" ${i===0?'disabled':''}><svg class="ui-icon" viewBox="0 0 24 24"><path d="m6 14 6-6 6 6"/></svg></button><button data-move="${offset+i}" data-direction="1" aria-label="Move ${esc(t.title)} later" ${i===queue.length-1?'disabled':''}><svg class="ui-icon" viewBox="0 0 24 24"><path d="m6 10 6 6 6-6"/></svg></button><button data-remove="${offset+i}" aria-label="Remove ${esc(t.title)}">${icon('close')}</button></div></div>`).join('') : '<div class="empty-queue">A little room for your next discovery. Start Radio to keep listening.</div>';
  }
  $('queue').onclick = e => {
    const play = e.target.closest('[data-queue-play]'),
      remove = e.target.closest('[data-remove]'),
      move = e.target.closest('[data-move]');
    if (play) cmd('queuePlay', {
      index: Number(play.dataset.queuePlay)
    });
    if (remove) cmd('queueRemove', {
      index: Number(remove.dataset.remove)
    });
    if (move) cmd('queueMove', {
      index: Number(move.dataset.move),
      direction: Number(move.dataset.direction)
    });
  };
  for (const action of ['play', 'prev', 'next', 'shuffle', 'repeat', 'like', 'download', 'copy', 'refresh', 'settings', 'close', 'smartNext']) $(action).onclick = () => cmd(action);
  $('seek').onpointerdown = () => dragging = true;
  $('seek').onchange = () => {
    dragging = false;
    cmd('seek', {
      percent: Number($('seek').value) / 1000
    });
  };
  $('seek').onpointercancel = () => dragging = false;
  $('volume').oninput = () => cmd('volume', {
    volume: Number($('volume').value) / 100
  });
  $('brandLink').onclick = e => {
    if (parent !== window) {
      e.preventDefault();
      cmd('close');
    }
  };

  function selectPanel(lyrics) {
    $('queueTab').setAttribute('aria-selected', String(!lyrics));
    $('lyricsTab').setAttribute('aria-selected', String(lyrics));
    $('queuePanel').hidden = lyrics;
    $('lyricsPanel').hidden = !lyrics;
    updateLyrics();
  }
  $('queueTab').onclick = () => selectPanel(false);
  $('lyricsTab').onclick = () => selectPanel(true);

  function parseLRC(text) {
    const result = [];
    for (const row of text.split(/\r?\n/).slice(0, 2500)) {
      const value = row.replace(/\[[^\]]*\]/g, '').trim().slice(0, 400);
      for (const m of row.matchAll(/\[(\d{1,3}):(\d{2})(?:[.:](\d{1,3}))?\]/g))
        if (value && Number(m[2]) < 60) result.push({
          at: Number(m[1]) * 60 + Number(m[2]) + (m[3] ? Number(m[3]) / 10 ** m[3].length : 0),
          text: value
        });
    }
    return result.sort((a, b) => a.at - b.at);
  }
  async function loadLyrics(track) {
    const serial = ++lyricsSerial;
    activeLine = -1;
    lines = [];
    const saved = MousikeData.read('mousike-lyrics-v3', {})[lyricKey(track)];
    if (saved?.lines?.length) {
      lines = saved.lines;
      $('lyricsSource').textContent = 'Your imported lyrics';
      renderLyrics();
      return;
    }
    if (!track) {
      renderLyrics();
      return;
    }
    try {
      const params = new URLSearchParams({
        title: track.canonical_title || track.title,
        artist: track.artist || ''
      });
      const response = await fetch('/api/lyrics?' + params);
      const result = await response.json();
      if (serial !== lyricsSerial) return;
      if (response.ok && result.available) {
        lines = result.lines;
        $('lyricsSource').textContent = result.source;
      } else $('lyricsSource').textContent = 'Import lyrics you have permission to use.';
    } catch {
      if (serial !== lyricsSerial) return;
    }
    renderLyrics();
  }

  function renderLyrics() {
    $('lyricsLines').innerHTML = lines.length ? lines.map((l, i) => `<button data-line="${i}">${esc(l.text)}</button>`).join('') : '<p class="lyrics-empty">No lyrics supplied for this recording. You can import a synchronized .lrc file.</p>';
    updateLyrics();
  }

  function updateLyrics() {
    if ($('lyricsPanel').hidden || !lines.length) return;
    let index = -1;
    for (let i = 0; i < lines.length; i++) {
      if (lines[i].at <= Number(data.position || 0) + .1) index = i;
      else break;
    }
    if (index === activeLine) return;
    activeLine = index;
    $('lyricsLines').querySelectorAll('button').forEach((button, i) => button.classList.toggle('current', i === index));
    $('lyricsLines').querySelector(`[data-line="${index}"]`)?.scrollIntoView({
      block: 'center',
      behavior: matchMedia('(prefers-reduced-motion: reduce)').matches ? 'instant' : 'smooth'
    });
  }
  $('lyricsLines').onclick = e => {
    const line = e.target.closest('[data-line]');
    if (line && data.duration > 0) cmd('seek', {
      percent: lines[Number(line.dataset.line)].at / data.duration
    });
  };
  $('lyricsFile').onchange = async e => {
    const file = e.target.files?.[0];
    if (!file || !data.current) return;
    if (file.size > 400000) return notice('Choose a lyrics file smaller than 400 KB.');
    const parsed = parseLRC(await file.text());
    if (!parsed.length) return notice('This file has no valid synchronized lyric lines.');
    const existing = MousikeData.read('mousike-lyrics-v3', {});
    existing[lyricKey(data.current)] = {
      lines: parsed,
      source: 'Your imported lyrics'
    };
    const entries = Object.entries(existing).slice(-100);
    MousikeData.write('mousike-lyrics-v3', Object.fromEntries(entries));
    lines = parsed;
    activeLine = -1;
    $('lyricsSource').textContent = 'Your imported lyrics';
    renderLyrics();
  };
  window.addEventListener('message', e => {
    if (e.origin === location.origin && e.source === parent && e.data?.type === 'mousike:player:state') show(e.data.state);
  });
  document.addEventListener('keydown', e => {
    if (e.key === 'Escape') cmd('close');
    if (e.target.closest('input,textarea,button')) return;
    if (e.code === 'Space') {
      e.preventDefault();
      cmd('play');
    }
    if (e.altKey && e.key === 'ArrowRight') cmd('next');
    if (e.altKey && e.key === 'ArrowLeft') cmd('prev');
  });
  document.addEventListener('error', e => {
    if (e.target.tagName === 'IMG' && !e.target.dataset.fallback) {
      e.target.dataset.fallback = '1';
      e.target.src = '/assets/images/placeholder.svg';
    }
  }, true);
  const ui = MousikeData.read(MousikeData.keys.ui, {});
  document.documentElement.dataset.mousikeTheme = ui.theme || 'dark';
  document.documentElement.dataset.mousikeTypography = ui.typography || 'curated';
  document.documentElement.dataset.mousikeGlass = ui.glass || 'balanced';
  document.documentElement.dataset.mousikeMotion = ui.motion ? 'reduced' : 'normal';
  if (/^#[\da-f]{6}$/i.test(ui.custom) && ui.palette === 'custom') document.documentElement.style.setProperty('--accent', ui.custom);
  document.addEventListener('visibilitychange', () => show(data));
  if (parent !== window) cmd('hello');
  else notice('Open Now Playing from the Mousike music window to use playback controls.');
})();
