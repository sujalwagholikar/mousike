/* Shared persistence, safe migration and portable exports. Legacy keys retained. */
(() => {
  'use strict';
  const K = {
    profile: 'aurora_taste_profile',
    prefs: 'aurora_aurora-preferences-v2',
    studio: 'aurora_studio-v1',
    ui: 'mousike-ui-v1',
    likes: 'aurora_likes',
    history: 'aurora_history',
    playlists: 'aurora_user-playlists-v3',
    following: 'aurora_followed-artists-v3',
    events: 'aurora-intelligence-v2-events',
    analysis: 'aurora-intelligence-v2-analysis',
    smart: 'aurora-intelligence-v2-settings',
    feed: 'aurora-intelligence-v2-feed',
    session: 'mousike-session-v3',
    lyrics: 'mousike-lyrics-v3'
  };
  const read = (key, fallback = null) => {
    try {
      return JSON.parse(localStorage.getItem(key)) ?? fallback;
    } catch {
      return fallback;
    }
  };
  const write = (key, value) => {
    try {
      localStorage.setItem(key, JSON.stringify(value));
      return true;
    } catch {
      window.dispatchEvent(new CustomEvent('mousike:storage-full'));
      return false;
    }
  };
  const safeTrack = t => {
    if (!t || typeof t !== 'object' || typeof t.title !== 'string' || !t.id) return null;
    const track = {};
    for (const k of ['id', 'title', 'artist', 'performer', 'performer_source', 'fingerprint', 'fingerprint_type', 'album', 'image', 'url', 'kind', 'source_video_id', 'source', 'canonical_title', 'recording_id', 'isrc']) {
      if (typeof t[k] === 'string') track[k] = t[k].slice(0, k === 'image' ? 1800 : 300);
    }
    for (const k of ['duration', 'start', 'end', 'replaygain_db'])
      if (Number.isFinite(Number(t[k])) && (k === 'replaygain_db' || Number(t[k]) >= 0)) track[k] = Number(t[k]);
    for (const k of ['segment', 'unresolved'])
      if (typeof t[k] === 'boolean') track[k] = t[k];
    for (const k of ['genres', 'moods', 'languages', 'tags'])
      if (Array.isArray(t[k])) track[k] = t[k].filter(v => typeof v === 'string').slice(0, 12);
    return track;
  };
  const tracks = (v, limit = 500) => Array.isArray(v) ? v.slice(0, limit).map(safeTrack).filter(Boolean) : [];

  function migrate() {
    // A previous settings page exported the wrong playlist key. Recover it only
    // when the actual playlist store is absent, preserving both otherwise.
    if (!localStorage.getItem(K.playlists)) {
      const old = read('aurora_playlists-v3', null);
      if (Array.isArray(old)) write(K.playlists, old);
    }
    for (const key of [K.likes, K.history]) {
      const value = read(key, []);
      if (!Array.isArray(value)) write(key, []);
    }
    localStorage.setItem('mousike-schema-version', '3');
  }

  function exportData() {
    const result = {
      schema: 'mousike-3',
      exported_at: new Date().toISOString()
    };
    for (const [name, key] of Object.entries(K))
      if (name !== 'feed' && name !== 'session') result[name] = read(key, ['likes', 'history', 'playlists', 'following', 'events'].includes(name) ? [] : {});
    return result;
  }

  function downloadData() {
    const blob = new Blob([JSON.stringify(exportData(), null, 2)], {
      type: 'application/json'
    });
    const url = URL.createObjectURL(blob),
      a = document.createElement('a');
    a.href = url;
    a.download = 'mousike-listening-data.json';
    a.click();
    setTimeout(() => URL.revokeObjectURL(url), 1500);
  }

  function importData(value) {
    if (!value || typeof value !== 'object' || !['mousike-3', undefined].includes(value.schema)) throw Error('Unsupported data file');
    const imported = {
      ...value,
      likes: value.likes || value.favorites,
      history: value.history || value.recent,
      prefs: value.prefs || value.preferences,
      ui: value.ui || value.appearance,
      studio: value.studio || value.audio,
      smart: value.smart || value.intelligence_settings,
      events: value.events || value.activity
    };
    const plan = [];
    for (const [name, key] of Object.entries(K)) {
      let v = imported[name];
      if (v === undefined || ['feed', 'session'].includes(name)) continue;
      if (['likes', 'history'].includes(name)) v = tracks(v);
      else if (name === 'playlists') {
        if (!Array.isArray(v)) throw Error('Invalid playlists');
        v = v.slice(0, 100).filter(p => p && typeof p.id === 'string' && typeof p.title === 'string').map(p => ({
          id: p.id.slice(0, 80),
          title: p.title.slice(0, 90),
          description: String(p.description || '').slice(0, 500),
          tracks: tracks(p.tracks, 250)
        }));
      } else if (['events', 'following'].includes(name)) {
        if (!Array.isArray(v)) throw Error('Invalid ' + name);
        v = v.slice(-700);
      } else if (!v || typeof v !== 'object' || Array.isArray(v)) throw Error('Invalid ' + name);
      plan.push([key, v]);
    }
    // Validate the complete payload before the first write. Browser keys/API keys
    // are never imported. Existing individual collections merge by ID.
    for (const [key, v] of plan) {
      if ([K.likes, K.history, K.playlists].includes(key)) {
        const old = read(key, []),
          byID = new Map((Array.isArray(old) ? old : []).map(x => [x.id, x]));
        v.forEach(x => byID.set(x.id, x));
        if (!write(key, [...byID.values()].slice(0, 500))) throw Error('Browser storage is full');
      } else if (!write(key, v)) throw Error('Browser storage is full');
    }
    migrate();
    window.dispatchEvent(new CustomEvent('mousike:data-imported'));
  }
  const apiKey = () => sessionStorage.getItem('mousike-gemini-key') || localStorage.getItem('aurora-intelligence-v2-api-key') || '';
  window.MousikeData = {
    keys: K,
    read,
    write,
    tracks,
    safeTrack,
    migrate,
    exportData,
    downloadData,
    importData,
    apiKey
  };
  migrate();
})();
