/* The full-screen surface is a view of the existing playback session. */
(() => {
  'use strict';
  const layer = document.createElement('div');
  layer.className = 'mousike-player-layer';
  layer.id = 'mousikePlayerLayer';
  layer.setAttribute('role', 'dialog');
  layer.setAttribute('aria-modal', 'true');
  layer.setAttribute('aria-label', 'Now Playing');
  layer.innerHTML = '<iframe title="Mousike Now Playing"></iframe>';
  document.body.append(layer);
  const frame = layer.querySelector('iframe');
  let ready = false,
    lastAt = 0,
    lastFocus = null;

  function packet() {
    const preview = MousikePlayback.status.pendingTrack;
    const track = preview || state.current,
      duration = preview ? Number(preview.duration) || 0 : relativeClipDuration();
    const analyser = MousikeAudio.analyser;
    let spectrum = [];
    if (analyser && studio.visualizer && !audio.paused) {
      const values = new Uint8Array(analyser.frequencyBinCount);
      analyser.getByteFrequencyData(values);
      spectrum = Array.from({
        length: 32
      }, (_, i) => values[i * 2] || 0);
    }
    return {
      spectrum,
      current: track,
      queue: state.queue.slice(state.index + 1, state.index + 51),
      queueOffset: state.index + 1,
      queueTotal: Math.max(0, state.queue.length - state.index - 1),
      position: preview ? 0 : relativeClipTime(),
      duration,
      paused: audio.paused,
      audiblePlaying: !audio.paused && !audio.ended && audio.dataset.mousikePlaying === "true",
      volume: prefs.volume / 100,
      shuffle: state.shuffle,
      repeat: state.repeat,
      liked: !!track && state.likes.some(t => sameSong(t, track)),
      aiUsed: !!window.AuroraSmart?.feed().ai_used,
      pending: MousikePlayback.status.pending,
      preloaded: MousikePlayback.status.prepared,
      crossfade: Number(prefs.crossfade) || 0
    };
  }

  function send(force = false) {
    if (!ready || !layer.classList.contains('open')) return;
    if (!force && performance.now() - lastAt < 230) return;
    lastAt = performance.now();
    frame.contentWindow.postMessage({
      type: 'mousike:player:state',
      state: packet()
    }, location.origin);
  }

  function open() {
    if (layer.classList.contains('open')) return;
    lastFocus = document.activeElement;
    layer.classList.add('open');
    $('#appShell').inert = true;$('.premium-player').inert=true;
    ready = false;
    frame.src = '/player.html';
    frame.focus();
  }

  function close() {
    if (!layer.classList.contains('open')) return;
    layer.classList.remove('open');
    frame.removeAttribute('src');
    ready = false;
    $('#appShell').inert = false;$('.premium-player').inert=false;
    lastFocus?.focus?.();
  }
  async function download() {
    const t = state.current;
    if (!t) return;
    try {
      const track = await resolveRecording(t),
        vid = track.segment ? track.source_video_id : track.id;
      const params = new URLSearchParams({
        title: track.title
      });
      if (track.segment) {
        params.set('start', track.start);
        params.set('end', track.end);
      }
      const link = document.createElement('a');
      link.href = '/api/download/' + vid + '?' + params;
      link.download = '';
      link.click();
    } catch (e) {
      notify(e.message);
    }
  }
  window.addEventListener('message', async e => {
    if (e.origin !== location.origin || e.source !== frame.contentWindow || !layer.classList.contains('open') || e.data?.type !== 'mousike:player:cmd') return;
    const o = e.data;
    if (o.action === 'hello') {
      ready = true;
      send(true);
      return;
    }
    if (o.action === 'close') return close();
    if (o.action === 'play') await togglePlayback();
    if (o.action === 'prev') prev();
    if (o.action === 'next' || o.action === 'smartNext') await next();
    if (o.action === 'seek') clipSeek(Math.max(0, Math.min(1, Number(o.percent) || 0)));
    if (o.action === 'volume') {
      prefs.volume = Math.max(0, Math.min(100, Number(o.volume) * 100 || 0));
      decks.forEach(d => d.volume = prefs.volume / 100);
      $('#volume').value = prefs.volume;
      savePrefs();
    }
    if (o.action === 'shuffle') $('#shuffle').click();
    if (o.action === 'repeat') $('#repeat').click();
    if (o.action === 'like') toggleLike(state.current);
    if (o.action === 'download') await download();
    if (o.action === 'refresh') window.AuroraSmart?.refresh();
    if (o.action === 'feedback') window.AuroraSmart?.feedback(o.kind, state.current);
    if (o.action === 'copy' && state.current) {
      try {
        await navigator.clipboard.writeText(state.current.url || 'https://www.youtube.com/watch?v=' + (state.current.source_video_id || state.current.id));
      } catch {}
    }
    if (o.action === 'queuePlay' && Number.isInteger(o.index) && o.index >= 0 && o.index < state.queue.length) await playAt(o.index, state.queue);
    if (o.action === 'queueRemove' && Number.isInteger(o.index) && o.index > state.index && o.index < state.queue.length) {
      state.queue.splice(o.index, 1);
      MousikePlayback.queueChanged();
      if (state.page === 'queue') render();
    }
    if (o.action === 'queueMove' && Number.isInteger(o.index) && [-1, 1].includes(o.direction)) {
      const n = o.index,
        dest = n + o.direction;
      if (n > state.index && n < state.queue.length && dest > state.index && dest < state.queue.length) {
        [state.queue[n], state.queue[dest]] = [state.queue[dest], state.queue[n]];
        MousikePlayback.queueChanged();
      }
    }
    if (o.action === 'settings') {
      close();
      window.MousikeSettings.open('playback');
    }
    send(true);
  });
  expanded = show => show === false ? close() : open();
  $('#openExpanded').onclick = open;
  for (const event of ['mousike:player-state', 'mousike:progress', 'mousike:queue-changed', 'mousike:feed-updated']) window.addEventListener(event, () => send(event !== 'mousike:progress'));
  window.addEventListener('keydown', e => {
    if (e.key === 'Escape') close();
  });
  window.MousikePlayer = {
    open,
    close,
    sendState: () => send(true)
  };
})();
