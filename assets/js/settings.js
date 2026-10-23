/* Mousike · dedicated settings controller. Persistent data lives only in browser localStorage. */
(() => {
  'use strict';
  const $ = selector => document.querySelector(selector);
  const KEY = {
    ui: 'mousike-ui-v1',
    prefs: 'aurora_aurora-preferences-v2',
    studio: 'aurora_studio-v1',
    smart: 'aurora-intelligence-v2-settings',
    api: 'aurora-intelligence-v2-api-key',
    profile: 'aurora_taste_profile',
    events: 'aurora-intelligence-v2-events',
    analysis: 'aurora-intelligence-v2-analysis',
    feed: 'aurora-intelligence-v2-feed'
  };
  const read = (k, f) => {
    try {
      return JSON.parse(localStorage.getItem(k)) ?? f
    } catch {
      return f
    }
  };
  const put = (k, v) => {
    localStorage.setItem(k, JSON.stringify(v));
    saved()
  };
  const html = s => String(s ?? '').replace(/[&<>"']/g, c => ({
    '&': '&amp;',
    '<': '&lt;',
    '>': '&gt;',
    '"': '&quot;',
    "'": '&#39;'
  } [c]));
  const defaultUI = {
    theme: 'dark',
    palette: 'mint',
    custom: '#c8e6a0',
    density: 'balanced',
    rounding: 'rounded',
    fontScale: 'normal',
    motion: false,
    greetingTone: 'warm',
    welcomeAnimation: true,
    typography: 'curated',
    glass: 'balanced',
    artworkFit: 'contain',
    cardSize: 'balanced',
    homeArtists: true,
    homeRecent: true,
    homeMixes: true,
    homeAlbums: true
  };
  const defaultPrefs = {
    accent: '#c8e6a0',
    speed: 1,
    autoplay: true,
    motion: false,
    compact: false,
    volume: 75,
    albumMode: 'auto',
    crossfade: 0,
    prebuffer: false,
    normalization: false,
    sleep: 0
  };
  const defaultStudio = {
    enabled: true,
    preset: 'Flat',
    bands: [0, 0, 0, 0, 0, 0, 0, 0],
    bass: 0,
    vocals: 0,
    treble: 0,
    pan: 0,
    space: 0,
    compress: false,
    visualizer: true,
    searchDelay: 290
  };
  const defaultSmart = {
    radio: true,
    overrideQueue: false,
    activity: true,
    autoFeed: true,
    feedInterval: 30
  };
  let ui = {
      ...defaultUI,
      ...read(KEY.ui, {})
    },
    prefs = {
      ...defaultPrefs,
      ...read(KEY.prefs, {})
    };
  let studio = {
    ...defaultStudio,
    ...read(KEY.studio, {})
  };
  studio.bands = Array.isArray(studio.bands) && studio.bands.length === 8 ? studio.bands : defaultStudio.bands.slice();
  let smart = {
    ...defaultSmart,
    ...read(KEY.smart, {})
  };
  const themes = [
    ['dark', 'Carbon', '#0b0e0d'],
    ['graphite', 'Graphite', '#202026'],
    ['midnight', 'Midnight', '#111e39'],
    ['forest', 'Forest', '#1a3627'],
    ['light', 'Daylight', '#edf2ed'],
    ['dusk', 'Dusk', '#292031'],
    ['dual-emerald', 'Ink / Emerald', 'linear-gradient(90deg,#080e0b 50%,#173a28 50%)'],
    ['dual-plum', 'Midnight / Plum', 'linear-gradient(90deg,#0b1425 50%,#301e32 50%)'],
    ['dual-copper', 'Graphite / Copper', 'linear-gradient(90deg,#121717 50%,#35271f 50%)'],
    ['dual-ocean', 'Carbon / Ocean', 'linear-gradient(90deg,#080e12 50%,#12313a 50%)'],
    ['dual-ivory', 'Forest / Ivory', 'linear-gradient(90deg,#10261a 50%,#eff2e8 50%)'],
    ['dual-lilac', 'Plum / Pearl', 'linear-gradient(90deg,#241b2e 50%,#f0edf5 50%)']
  ];
  const palettes = [
    ['mint', 'Evergreen', '#64d98b'],
    ['amethyst', 'Amethyst', '#c1b5dc'],
    ['sunset', 'Sunset', '#e8b89f'],
    ['tidal', 'Tidal', '#a1cdd0'],
    ['rose', 'Rose', '#deafbc'],
    ['gold', 'Gold', '#dfcc9f']
  ];
  const presets = {
    Flat: [0, 0, 0, 0, 0, 0, 0, 0],
    Bass: [7, 5, 2, 0, -1, 0, 0, 1],
    Vocal: [-3, -2, -1, 1, 4, 5, 2, 0],
    Acoustic: [3, 2, 0, 1, 2, 2, 3, 2],
    Electronic: [5, 3, 1, -2, -1, 2, 4, 5],
    Classical: [2, 2, 1, 0, 0, 1, 2, 3],
    Rock: [4, 3, 0, -2, -1, 2, 4, 3],
    Cinema: [5, 4, 1, 0, 2, 3, 4, 4],
    LoFi: [3, 2, 1, 0, -2, -4, -6, -8],
    Podcast: [-5, -3, 0, 3, 5, 4, 0, -4]
  };

  function saved(msg = 'Saved automatically') {
    const s = $('#saveStatus');
    s.textContent = msg;
    s.classList.add('flash');
    setTimeout(() => s.classList.remove('flash'), 450)
  }

  function applyTheme() {
    const root = document.documentElement;
    root.dataset.mousikeTheme = ui.theme;
    root.dataset.mousikeTypography = ui.typography || 'curated';
    root.dataset.mousikeGlass = ui.glass || 'balanced';
    root.dataset.mousikeArtworkFit = ui.artworkFit || 'contain';
    root.dataset.mousikeCardSize = ui.cardSize || 'balanced';
    root.dataset.mousikePalette = ui.palette;
    root.dataset.mousikeDensity = ui.density;
    root.dataset.mousikeRound = ui.rounding;
    root.dataset.mousikeFont = ui.fontScale;
    root.dataset.mousikeMotion = ui.motion ? 'reduced' : 'normal';
    root.style.setProperty('--mousike-custom', ui.custom);
    const accent = ui.palette === 'custom' ? ui.custom : palettes.find(x => x[0] === ui.palette)?.[2] || '#c8e6a0';
    root.style.setProperty('--accent', accent);
    document.querySelector('meta[name="theme-color"]')?.setAttribute('content', ui.theme === 'light' ? '#edf2ed' : '#0b100f');
  }

  function setUI(k, v) {
    ui[k] = v;
    put(KEY.ui, ui);
    if (k === 'palette' || k === 'custom') {
      prefs.accent = ui.palette === 'custom' ? ui.custom : palettes.find(x => x[0] === ui.palette)?.[2] || '#c8e6a0';
      put(KEY.prefs, prefs)
    }
    if (k === 'motion') {
      prefs.motion = !!v;
      put(KEY.prefs, prefs)
    }
    if (k === 'density') {
      prefs.compact = v === 'compact';
      put(KEY.prefs, prefs)
    }
    applyTheme();
    drawThemes()
  }

  function drawThemes() {
    const ts = $('#themes');
    ts.innerHTML = themes.filter(([id]) => id.startsWith('dual-') === (document.querySelector('[data-theme-group].selected')?.dataset.themeGroup === 'dual')).map(([id, name, color]) => `<button type="button" class="theme-card ${ui.theme===id?'selected':''}" data-theme="${id}" aria-pressed="${ui.theme===id}"><span class="theme-art" style="background:${color}"></span><strong>${name}</strong></button>`).join('');
    $('#palettes').innerHTML = palettes.map(([id, name, color]) => `<button type="button" class="palette-choice ${ui.palette===id?'selected':''}" data-palette="${id}" aria-pressed="${ui.palette===id}"><span style="background:${color}"></span><strong>${name}</strong></button>`).join('');
    $('#customAccent').value = ui.custom;
  }

  function selectSection(id) {
    if (!document.getElementById('section-' + id)) return;
    document.querySelectorAll('.settings-section').forEach(e => e.classList.toggle('active', e.id === 'section-' + id));
    document.querySelectorAll('[data-section]').forEach(e => {
      e.classList.toggle('selected', e.dataset.section === id);
      e.setAttribute('aria-current', e.dataset.section === id ? 'page' : 'false')
    });
    const labels = {
      appearance: ['Appearance', 'Make every detail feel like yours.'],
      playback: ['Playback', 'Your music. Your pace.'],
      audio: ['Audio studio', 'Fine-tune the frequency.'],
      intelligence: ['Mousike Intelligence', 'A smarter soundtrack, shaped by you.'],
      personal: ['Personalization', 'Artists, languages, and your own listening identity.'],
      privacy: ['Privacy & data', 'Your listening belongs to you.'],
      shortcuts: ['Keyboard shortcuts', 'Less clicking, more music.'],
      about: ['About Mousike', 'Sound In motion.']
    };
    $('#pageTitle').textContent = labels[id][0];
    $('#pageSubtitle').textContent = labels[id][1];
    $('.settings-body').scrollTop = 0;
    history.replaceState(null, '', '#' + id);
    if (id === 'personal') drawProfile();
    if (id === 'privacy') drawStats()
  }

  function setPrefs(k, v) {
    prefs[k] = v;
    put(KEY.prefs, prefs)
  }

  function setStudio(k, v) {
    studio[k] = v;
    put(KEY.studio, studio)
  }

  function eqRender() {
    const hz = ['60', '170', '350', '700', '1.4k', '3k', '6k', '12k'];
    $('#eqBands').innerHTML = studio.bands.map((n, i) => `<label class="eq-band"><output id="db${i}">${n>0?'+':''}${n} dB</output><input type="range" min="-12" max="12" step="1" value="${n}" data-eq="${i}" aria-label="${hz[i]} Hz gain"><small>${hz[i]} Hz</small></label>`).join('');
    $('#studioPreset').value = studio.preset in presets ? studio.preset : 'Custom';
  }

  function studioRanges() {
    const fields = [
      ['bass', 'Bass and punch', -12, 12, ' dB'],
      ['vocals', 'Vocal presence', -12, 12, ' dB'],
      ['treble', 'High-frequency clarity', -12, 12, ' dB'],
      ['pan', 'Stereo balance', -100, 100, '%'],
      ['space', 'Room ambience', 0, 100, '%']
    ];
    $('#soundRanges').innerHTML = fields.map(([k, n, min, max, u]) => `<div class="field-row"><label for="snd-${k}">${n}</label><div class="range-label"><input id="snd-${k}" data-studio="${k}" type="range" min="${min}" max="${max}" value="${studio[k]}"><output id="sndVal-${k}">${studio[k]}${u}</output></div></div>`).join('');
  }

  function applyForms() {
    for (const [id, val] of Object.entries({
        ...ui,
        density: ui.density,
        rounding: ui.rounding,
        fontScale: ui.fontScale,
        motion: ui.motion,
        speed: prefs.speed,
        autoplay: prefs.autoplay,
        volume: prefs.volume,
        albumMode: prefs.albumMode,
        crossfade: prefs.crossfade,
        prebuffer: prefs.prebuffer,
        normalization: prefs.normalization,
        sleep: prefs.sleep,
        studioEnabled: studio.enabled,
        compress: studio.compress,
        visualizer: studio.visualizer,
        ...smart
      })) {
      const el = $('#' + id);
      if (el) {
        if (el.type === 'checkbox') el.checked = !!val;
        else el.value = val;
      }
    }
    $('#volumeValue').textContent = prefs.volume + '%';
    $('#studioPreset').innerHTML = Object.keys(presets).concat('Custom').map(name => `<option value="${name}">${name}</option>`).join('');
    eqRender();
    studioRanges();
    drawThemes()
  }
  const artistNames = ['Arijit Singh', 'Shreya Ghoshal', 'A. R. Rahman', 'Sonu Nigam', 'Pritam', 'Atif Aslam', 'The Weeknd', 'Taylor Swift', 'Billie Eilish', 'Lana Del Rey', 'Diljit Dosanjh', 'KK', 'Kishore Kumar', 'Lata Mangeshkar', 'Drake', 'BTS'];

  function drawProfile() {
    const p = read(KEY.profile, {});
    $('#profileName').textContent = p.name || 'Your music identity';
    $('#profileCountry').textContent = p.country || 'Where your listening journey begins';
    const av = $('#profileAvatar');
    av.replaceChildren();
    if (typeof p.avatar_data === 'string' && p.avatar_data.startsWith('data:image/') && p.avatar_data.length < 300000) {
      const img = document.createElement('img');
      img.src = p.avatar_data;
      img.alt = 'Your avatar';
      av.append(img)
    } else {
      const img = document.createElement('img');
      img.src = '/assets/brand/mousike-mark-static.svg?v=6';
      img.alt = 'Mousike icon';
      av.append(img)
    }
    const groups = [
      ['Artists', p.artists],
      ['Languages', p.languages],
      ['Genres', p.genres],
      ['Moods', p.moods],
      ['Decades', p.decades]
    ];
    $('#tasteSummary').innerHTML = groups.map(([n, a]) => `<div class="taste-group"><strong>${n}</strong><div class="taste-chips">${(Array.isArray(a)&&a.length?a:['Not selected']).map(x=>`<span>${html(x)}</span>`).join('')}</div></div>`).join('');
    const names = Array.isArray(p.artists) ? p.artists.slice(0, 20) : [];
    const gal = $('#favoriteArtistGallery');
    gal.innerHTML = names.length ? names.map(n => `<div class="fav-artist" data-portrait="${html(n)}"><img alt="${html(n)}" src="/assets/images/placeholder.svg" loading="lazy"><span>${html(n)}</span></div>`).join('') : '<p>Choose artists during onboarding to see their portraits here.</p>';
    if (names.length) loadPortraits(names)
  }
  async function loadPortraits(names) {
    try {
      let cache = read('mousike-wikipedia-portraits-v1', {});
      const manual = window.MOUSIKE_ARTIST_IMAGES || {};
      const missing = names.filter(n => !manual[n] && !cache[n]?.image).slice(0, 30);
      if (missing.length) {
        const r = await fetch('/api/artist-portraits', {
          method: 'POST',
          headers: {
            'Content-Type': 'application/json'
          },
          body: JSON.stringify({
            names: missing
          })
        });
        if (r.ok) {
          const d = await r.json();
          cache = {
            ...cache,
            ...(d.portraits || {})
          };
          put('mousike-wikipedia-portraits-v1', cache)
        }
      }
      document.querySelectorAll('[data-portrait]').forEach(el => {
        const n = el.dataset.portrait,
          src = manual[n] || cache[n]?.image;
        if (src && (src.startsWith('/assets/artists/') || /^https:\/\/upload\.wikimedia\.org\//.test(src))) {
          el.querySelector('img').src = src;
          el.title = 'Portrait from Wikipedia / Wikimedia Commons';
        }
      })
    } catch (e) {
      console.info('Wikipedia portraits unavailable', e)
    }
  }

  function drawStats() {
    const ev = read(KEY.events, []);
    const ls = read('aurora_likes', []);
    const playlists = read('aurora_user-playlists-v3', []);
    $('#dataStats').innerHTML = [
      ['Listening signals', Array.isArray(ev) ? ev.length : 0],
      ['Liked songs', Array.isArray(ls) ? ls.length : 0],
      ['Local playlists', Array.isArray(playlists) ? playlists.length : 0]
    ].map(([name, value]) => `<div class="stat"><strong>${value}</strong><small>${name}</small></div>`).join('')
  }

  function close() {
    if (window.parent !== window) {
      window.parent.postMessage({
        type: 'mousike:settings:close'
      }, location.origin)
    } else {
      location.href = '/'
    }
  }
  async function callAI(path, draftKey = '') {
    const key = draftKey || MousikeData.apiKey();
    const profile = read(KEY.profile, {}),
      activity = read(KEY.events, []),
      learned = read(KEY.analysis, {});
    const shared = parent !== window && parent.AuroraSmart?.payload ? parent.AuroraSmart.payload() : null;
    const data = shared ? {...shared, api_key: key} : {
      profile,
      activity: Array.isArray(activity) ? activity.slice(-500) : [],
      learned,
      api_key: key,
      current: {},
      exclude: []
    };
    const ctrl = new AbortController();
    const t = setTimeout(() => ctrl.abort(), 65000);
    try {
      const r = await fetch(path, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json'
        },
        body: JSON.stringify(data),
        signal: ctrl.signal
      });
      const body = await r.json().catch(() => ({}));
      if (!r.ok) throw Error(body.detail || `HTTP ${r.status}`);
      return body
    } finally {
      clearTimeout(t)
    }
  }

  function aiStatus() {
    const configured = !!MousikeData.apiKey();
    $('#aiStatus').textContent = configured ? 'Gemini key supplied · Test connection to verify access' : 'Local recommendation engine active · Gemini key optional'
  }

  function wire() {
    document.querySelectorAll('[data-section]').forEach(el => el.addEventListener('click', () => selectSection(el.dataset.section)));
    $('#closeSettings').onclick = close;
    $('#backToMusic').onclick = close;
    document.querySelectorAll('[data-theme-group]').forEach(button => button.onclick = () => {
      document.querySelectorAll('[data-theme-group]').forEach(b => { b.classList.toggle('selected', b === button); b.setAttribute('aria-pressed', String(b === button)); });
      drawThemes();
    });
    if (ui.theme.startsWith('dual-')) document.querySelector('[data-theme-group="dual"]').click();
    $('#themes').onclick = e => {
      const b = e.target.closest('[data-theme]');
      if (b) setUI('theme', b.dataset.theme)
    };
    $('#palettes').onclick = e => {
      const b = e.target.closest('[data-palette]');
      if (b) setUI('palette', b.dataset.palette)
    };
    $('#customAccent').oninput = e => {
      ui.custom = e.target.value;
      setUI('palette', 'custom')
    };
    for (const [id, k] of [
        ['density', 'density'],
        ['rounding', 'rounding'],
        ['fontScale', 'fontScale'],
        ['greetingTone', 'greetingTone'],
        ['typography', 'typography'],
        ['glass', 'glass'],
        ['artworkFit', 'artworkFit'],
        ['cardSize', 'cardSize']
      ]) $('#' + id).onchange = e => setUI(k, e.target.value);
    for (const key of ['homeArtists', 'homeRecent', 'homeMixes', 'homeAlbums', 'welcomeAnimation']) {
      $('#' + key).onchange = e => setUI(key, e.target.checked);
    }
    $('#motion').onchange = e => setUI('motion', e.target.checked);
    for (const [id, k] of [
        ['speed', 'speed'],
        ['autoplay', 'autoplay'],
        ['albumMode', 'albumMode'],
        ['crossfade', 'crossfade'],
        ['prebuffer', 'prebuffer'],
        ['normalization', 'normalization'],
        ['sleep', 'sleep']
      ]) $('#' + id).onchange = e => setPrefs(k, e.target.type === 'checkbox' ? e.target.checked : e.target.value);
    $('#volume').oninput = e => {
      setPrefs('volume', +e.target.value);
      $('#volumeValue').textContent = e.target.value + '%'
    };
    $('#studioEnabled').onchange = e => setStudio('enabled', e.target.checked);
    $('#compress').onchange = e => setStudio('compress', e.target.checked);
    $('#visualizer').onchange = e => setStudio('visualizer', e.target.checked);
    $('#studioPreset').onchange = e => {
      studio.preset = e.target.value;
      if (presets[studio.preset]) studio.bands = [...presets[studio.preset]];
      put(KEY.studio, studio);
      eqRender()
    };
    $('#eqBands').oninput = e => {
      const el = e.target.closest('[data-eq]');
      if (!el) return;
      const i = +el.dataset.eq;
      studio.bands[i] = +el.value;
      studio.preset = 'Custom';
      put(KEY.studio, studio);
      $('#db' + i).textContent = (+el.value > 0 ? '+' : '') + el.value + ' dB';
      $('#studioPreset').value = 'Custom'
    };
    $('#soundRanges').oninput = e => {
      const el = e.target.closest('[data-studio]');
      if (!el) return;
      setStudio(el.dataset.studio, +el.value);
      $('#sndVal-' + el.dataset.studio).textContent = el.value + (['pan', 'space'].includes(el.dataset.studio) ? '%' : ' dB')
    };
    $('#eqFlat').onclick = () => {
      studio.bands = [...presets.Flat];
      studio.preset = 'Flat';
      put(KEY.studio, studio);
      eqRender()
    };
    $('#eqReset').onclick = () => {
      studio = {
        ...defaultStudio,
        bands: [...defaultStudio.bands]
      };
      put(KEY.studio, studio);
      applyForms()
    };
    for (const id of Object.keys(defaultSmart)) $('#' + id).onchange = e => {
      smart[id] = e.target.type === 'checkbox' ? e.target.checked : Number(e.target.value);
      put(KEY.smart, smart)
    };
    $('#keySave').onclick = () => {
      const input = $('#geminiKey'),
        value = input.value.trim();
      if (!value || value.length < 12 || value.length > 300 || /\s/.test(value)) {
        saved('Please enter a valid Gemini API key');
        return
      }
      if ($('#rememberKey').checked) {
        localStorage.setItem(KEY.api, value);
        sessionStorage.removeItem('mousike-gemini-key');
      } else {
        sessionStorage.setItem('mousike-gemini-key', value);
        localStorage.removeItem(KEY.api);
      }
      input.value = '';
      saved('Key saved in this browser');
      aiStatus()
    };
    $('#keyRemove').onclick = () => {
      localStorage.removeItem(KEY.api);
      sessionStorage.removeItem('mousike-gemini-key');
      $('#geminiKey').value = '';
      aiStatus();
      saved('Key removed')
    };
    $('#keyTest').onclick = async () => {
      saved('Checking Gemini…');
      try {
        const v = await callAI('/api/ai/test', $('#geminiKey').value.trim());
        saved('Connected · ' + String(v.model || 'gemini-3.5-flash-lite')); $('#aiStatus').textContent = 'Connected · ' + v.model;
      } catch (e) {
        saved('Test failed: ' + e.message)
      }
    };
    $('#analyze').onclick = async () => {
      saved('Analyzing your music taste…');
      try {
        const v = await callAI('/api/ai/analyze');
        const learned = v.analysis || v.learned || v;
        put(KEY.analysis, learned);
        $('#intelligenceOutput').textContent = String(learned.summary || learned.vibe || v.reason || 'Analysis completed.').slice(0, 900);
        saved('Music-taste analysis updated')
      } catch (e) {
        saved('Analysis unavailable: ' + e.message)
      }
    };
    $('#refreshFeed').onclick = async () => {
      saved('Building your soundtrack…');
      try {
        const v = await callAI('/api/ai/feed');
        put(KEY.feed, v);
        localStorage.setItem('aurora-intelligence-v2-feed-at', String(Date.now()));
        $('#intelligenceOutput').textContent = String(v.reason || 'Recommendations updated').slice(0, 700);
        saved('Recommendations updated')
      } catch (e) {
        saved('Recommendation error: ' + e.message)
      }
    };
    $('#editTaste').onclick = () => location.href = '/onboarding.html?edit=1';
    $('#exportData').onclick = () => {
      MousikeData.downloadData();
      saved('Music data exported · API key excluded')
    };
    $('#importData').onchange = async e => {
      const file = e.target.files?.[0];
      if (!file) return;
      if (file.size > 5000000) return saved('Choose a data file under 5 MB');
      try {
        MousikeData.importData(JSON.parse(await file.text()));
        saved('Music data imported');
        drawStats();
        drawProfile();
      } catch (err) {
        saved('Import failed: ' + err.message)
      }
    };
    $('#discoveryLevel').value = read(KEY.profile, {}).discovery ?? 35;
    $('#discoveryValue').textContent = $('#discoveryLevel').value + '%';
    $('#discoveryLevel').oninput = e => {
      $('#discoveryValue').textContent = e.target.value + '%'
    };
    $('#discoveryLevel').onchange = e => {
      const profile = read(KEY.profile, {});
      profile.discovery = Number(e.target.value);
      put(KEY.profile, profile)
    };
    $('#rememberKey').checked = !!localStorage.getItem(KEY.api);
    $('#rememberKey').onchange = e => {
      const key = MousikeData.apiKey();
      if (!key) return;
      if (e.target.checked) {
        localStorage.setItem(KEY.api, key);
        sessionStorage.removeItem('mousike-gemini-key');
      } else {
        sessionStorage.setItem('mousike-gemini-key', key);
        localStorage.removeItem(KEY.api);
      }
      saved('Key storage preference updated')
    };
    $('#clearHistory').onclick = () => {
      if (confirm('Clear recently played songs in this browser?')) {
        localStorage.removeItem('aurora_history');
        saved('Recent history cleared')
      }
    };
    $('#eraseIntelligence').onclick = () => {
      if (!confirm('Delete learning signals, AI analysis, cached recommendations and Gemini key?')) return;
      for (const id of ['events', 'analysis', 'feed', 'feed-at', 'api-key', 'analyzed-at']) localStorage.removeItem('aurora-intelligence-v2-' + id);
      sessionStorage.removeItem('mousike-gemini-key');
      localStorage.removeItem('mousike-weekly-v3');
      aiStatus();
      drawStats();
      saved('Listening intelligence and key deleted')
    };
    window.addEventListener('keydown', e => {
      if (e.key === 'Escape' || (e.key === 'Backspace' && !['INPUT', 'TEXTAREA', 'SELECT'].includes(document.activeElement?.tagName))) {
        e.preventDefault();
        close()
      }
    });
    window.addEventListener('storage', e => {
      if (e.key === KEY.profile && $('#section-personal').classList.contains('active')) drawProfile()
    });
  }
  applyTheme();
  applyForms();
  wire();
  aiStatus();
  fetch('/api/capabilities').then(r => r.json()).then(c => {
    if (c.server_key_configured) $('#aiStatus').textContent = 'Gemini configured by your server · ' + c.gemini_model;
  }).catch(() => {});
  selectSection((location.hash || '#appearance').slice(1));
})();
