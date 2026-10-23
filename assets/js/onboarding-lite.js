/* Mousike · Sound In motion. Minimal, responsive, browser-only onboarding. */
(() => {
  'use strict';
  const $ = s => document.querySelector(s);
  const $$ = s => [...document.querySelectorAll(s)];
  const KEY = 'aurora_taste_profile';
  const ID = 'aurora_client_id';
  const defaultProfile = {
    client_id: localStorage.getItem(ID) || ('aur_' + (crypto.randomUUID?.() || String(Date.now()))),
    name: '',
    avatar: 'icon:headphones',
    avatar_data: '',
    country: 'India',
    languages: [],
    artists: [],
    genres: [],
    moods: [],
    decades: [],
    discovery: 35,
    palette: 'auto'
  };
  let profile = {
    ...defaultProfile
  };
  try {
    Object.assign(profile, JSON.parse(localStorage.getItem(KEY) || '{}'))
  } catch {}
  localStorage.setItem(ID, profile.client_id);
  const options = {
    languages: ['Hindi', 'English', 'Marathi', 'Punjabi', 'Tamil', 'Telugu', 'Kannada', 'Malayalam', 'Bengali', 'Gujarati', 'Urdu', 'Spanish', 'Korean', 'Japanese', 'French', 'Arabic'],
    artists: ['Arijit Singh', 'Shreya Ghoshal', 'A. R. Rahman', 'Sonu Nigam', 'Pritam', 'Atif Aslam', 'The Weeknd', 'Taylor Swift', 'Billie Eilish', 'Lana Del Rey', 'Diljit Dosanjh', 'KK', 'Kishore Kumar', 'Lata Mangeshkar', 'Badshah', 'AP Dhillon', 'Drake', 'Coldplay', 'BTS', 'Ed Sheeran'],
    genres: ['Pop', 'Bollywood', 'Indie', 'Rock', 'Hip-Hop', 'Classical', 'Lo-fi', 'Electronic', 'R&B', 'Devotional', 'Jazz', 'Instrumental', 'Folk', 'Soul'],
    moods: ['Chill', 'Romantic', 'Energetic', 'Focus', 'Happy', 'Melancholic', 'Workout', 'Sleep', 'Party', 'Nostalgic'],
    decades: ['70s', '80s', '90s', '2000s', '2010s', '2020s']
  };
  const palettes = [
    ['auto', 'Automatic', 'conic-gradient(#bdf29b,#c4a2fc,#8bc8dd,#bdf29b)'],
    ['lime', 'Matcha', '#b8f471'],
    ['violet', 'Violet', '#b9a2ff'],
    ['coral', 'Coral', '#ffac89'],
    ['ocean', 'Ocean', '#88ddec'],
    ['rose', 'Rose', '#ff9cbf']
  ];
  const avatars = [
    ['headphones', 'Listener'],
    ['disc', 'Collector'],
    ['moon', 'Night owl'],
    ['wave', 'Flow'],
    ['mic', 'Singer']
  ];

  function svg(name) {
    return `<svg aria-hidden="true"><use href="#ic-${name}"/></svg>`
  }

  function esc(x) {
    return String(x ?? '').replace(/[&<>"']/g, c => ({
      '&': '&amp;',
      '<': '&lt;',
      '>': '&gt;',
      '"': '&quot;',
      "'": '&#39;'
    } [c]))
  }
  let step = 0;
  const activeArtists = new Map();
  const pending = new Set();
  let artistSerial = 0;

  function pick(listName, items) {
    const root = $('#' + listName);
    const render = () => {
      root.innerHTML = items.map(x => `<button type="button" class="pill ${profile[listName].includes(x)?'selected':''}" data-choice="${esc(x)}" aria-pressed="${profile[listName].includes(x)}">${esc(x)}</button>`).join('');
      if (listName === 'languages') $('#languageCount').textContent = profile.languages.length + ' selected'
    };
    root.addEventListener('click', e => {
      const button = e.target.closest('[data-choice]');
      if (!button) return;
      const x = button.dataset.choice;
      profile[listName] = profile[listName].includes(x) ? profile[listName].filter(v => v !== x) : [...profile[listName], x].slice(0, listName === 'languages' ? 8 : 12);
      render()
    });
    render()
  }
  for (const n of ['languages', 'genres', 'moods', 'decades']) pick(n, options[n]);

  function renderAvatars() {
    $('#avatars').innerHTML = avatars.map(([id, label]) => `<button class="avatar-choice ${profile.avatar==='icon:'+id?'selected':''}" data-avatar="${id}" type="button" aria-label="${esc(label)} profile icon">${svg(id)}<span>${esc(label)}</span></button>`).join('')
  }
  renderAvatars();
  $('#avatars').addEventListener('click', e => {
    const el = e.target.closest('[data-avatar]');
    if (!el) return;
    profile.avatar = 'icon:' + el.dataset.avatar;
    profile.avatar_data = '';
    renderAvatars()
  });
  $('#name').value = profile.name;
  $('#country').value = profile.country || 'India';
  $('#discovery').value = profile.discovery ?? 35;
  $('#discoveryLabel').textContent = $('#discovery').value + '%';
  $('#discovery').addEventListener('input', e => $('#discoveryLabel').textContent = e.target.value + '%');

  function renderPalettes() {
    $('#palettes').innerHTML = palettes.map(([id, label, color]) => `<button type="button" class="pill ${profile.palette===id?'selected':''}" data-palette="${id}"><span class="tone" style="--tone:${color}"></span>${label}</button>`).join('')
  }
  renderPalettes();
  $('#palettes').onclick = e => {
    const item = e.target.closest('[data-palette]');
    if (item) {
      profile.palette = item.dataset.palette;
      renderPalettes()
    }
  };
  $('#avatarFile').onchange = async e => {
    const file = e.target.files?.[0];
    if (!file) return;
    if (file.size > 3_000_000) {
      $('#avatarStatus').textContent = 'Choose an image under 3 MB';
      return
    }
    const url = URL.createObjectURL(file);
    try {
      const img = new Image();
      img.src = url;
      await img.decode();
      const w = 144,
        canvas = document.createElement('canvas');
      canvas.width = w;
      canvas.height = w;
      const ctx = canvas.getContext('2d');
      const scale = Math.max(w / img.width, w / img.height);
      ctx.drawImage(img, (w - img.width * scale) / 2, (w - img.height * scale) / 2, img.width * scale, img.height * scale);
      profile.avatar_data = canvas.toDataURL('image/jpeg', .75);
      profile.avatar = 'photo';
      $('#avatarStatus').textContent = 'Photo ready'
    } catch {
      $('#avatarStatus').textContent = 'Image could not be read'
    } finally {
      URL.revokeObjectURL(url)
    }
  };

  function renderArtists() {
    let q = $('#artistFilter').value.trim().toLocaleLowerCase();
    let names = [...new Set([...options.artists, ...profile.artists])].filter(x => !q || x.toLocaleLowerCase().includes(q));
    $('#artists').innerHTML = names.map((name, i) => {
      const chosen = profile.artists.includes(name);
      return `<button type="button" class="artist-button ${chosen?'selected':''}" data-artist="${esc(name)}" aria-label="${esc(name)}" aria-pressed="${chosen}"><span class="portrait"><svg class="portrait-fallback"><use href="#ic-mic"/></svg><img alt="" data-photo="${esc(name)}" loading="${i<8?'eager':'lazy'}" decoding="async"><span class="selected-mark">${svg('check')}</span></span><span class="artist-name">${esc(name)}</span></button>`
    }).join('');
    $('#artistCount').textContent = profile.artists.length + ' selected';
    paintArtists()
  }

  function paintArtists() {
    for (const el of $$('[data-photo]')) {
      const name = el.dataset.photo;
      const url = window.MOUSIKE_ARTIST_IMAGES?.[name] || activeArtists.get(name);
      if (!url || el.dataset.src === url) continue;
      el.dataset.src = url;
      el.onload = () => {
        el.classList.add('loaded')
      };
      el.onerror = () => {
        el.classList.remove('loaded');
        el.dataset.src = ''
      };
      el.src = url
    }
  }
  const PORTRAIT_CACHE = 'mousike-onboarding-photos-v4';
  try {
    const box = JSON.parse(sessionStorage.getItem(PORTRAIT_CACHE) || '{}');
    if (box?.at && Date.now() - box.at < 6 * 3600_000)
      for (const [n, u] of Object.entries(box.data || {})) activeArtists.set(n, u)
  } catch {}

  function savePortraitCache() {
    try {
      sessionStorage.setItem(PORTRAIT_CACHE, JSON.stringify({
        at: Date.now(),
        data: Object.fromEntries(activeArtists)
      }))
    } catch {}
  }
  async function preloadPortraits(names = options.artists) {
    const current = ++artistSerial;
    const list = [...new Set(names)].filter(n => !window.MOUSIKE_ARTIST_IMAGES?.[n] && !activeArtists.has(n) && !pending.has(n));
    if (!list.length) {
      paintArtists();
      return
    } // Two small groups in parallel; no 20-person blocking request.
    const chunks = [];
    for (let i = 0; i < list.length; i += 6) chunks.push(list.slice(i, i + 6));
    let index = 0;
    async function worker() {
      while (index < chunks.length) {
        const group = chunks[index++];
        group.forEach(x => pending.add(x));
        try {
          const response = await fetch('/api/artist-portraits', {
            method: 'POST',
            headers: {
              'Content-Type': 'application/json'
            },
            body: JSON.stringify({
              names: group
            }),
            signal: AbortSignal.timeout?.(8500)
          });
          if (!response.ok) throw Error('Wikipedia unavailable');
          const data = await response.json();
          for (const [name, entry] of Object.entries(data.portraits || {})) {
            if (entry?.local_image) activeArtists.set(name, entry.local_image)
          }
          savePortraitCache();
          paintArtists()
        } catch (e) {
          console.debug('Artist portraits not available:', e.message)
        } finally {
          group.forEach(x => pending.delete(x))
        }
      }
    }
    await Promise.all([worker(), worker()]);
    if (current === artistSerial) paintArtists()
  }
  renderArtists();
  $('#artistFilter').oninput = renderArtists;
  $('#artists').onclick = e => {
    const b = e.target.closest('[data-artist]');
    if (!b) return;
    const name = b.dataset.artist;
    profile.artists = profile.artists.includes(name) ? profile.artists.filter(x => x !== name) : [...profile.artists, name].slice(0, 15);
    renderArtists()
  };

  function addArtist() {
    const name = $('#customArtist').value.trim().slice(0, 70);
    if (!name) return;
    if (profile.artists.length >= 15) return;
    if (!profile.artists.some(n => n.toLowerCase() === name.toLowerCase())) profile.artists.push(name);
    $('#customArtist').value = '';
    $('#artistFilter').value = '';
    renderArtists();
    preloadPortraits([name])
  }
  $('#addArtist').onclick = addArtist;
  $('#customArtist').addEventListener('keydown', e => {
    if (e.key === 'Enter') {
      e.preventDefault();
      addArtist()
    }
  });

  function save() {
    profile.name = $('#name').value.trim().slice(0, 48) || 'Listener';
    profile.country = $('#country').value;
    profile.discovery = Number($('#discovery').value);
    localStorage.setItem(KEY, JSON.stringify(profile))
  }

  function show() {
    for (const el of $$('.view')) {
      const active = Number(el.dataset.step) === step;
      el.hidden = !active;
      el.classList.toggle('visible', active)
    }
    $('#stepLabel').textContent = `STEP ${String(step+1).padStart(2,'0')} OF 04`;
    $('#bar').style.width = (25 * (step + 1)) + '%';
    $('#back').hidden = step === 0;
    $('#continue').textContent = step === 3 ? 'Make it mine' : 'Continue';
    if (step === 2) {
      paintArtists();
      preloadPortraits()
    }
    $('#feedback').textContent = 'Change your choices anytime in Settings'
  }
  $('#back').onclick = () => {
    if (step) {
      step--;
      show();
      window.scrollTo({
        top: 0,
        behavior: 'instant'
      })
    }
  };
  $('#continue').onclick = () => {
    save();
    if (step < 3) {
      step++;
      show();
      window.scrollTo({
        top: 0,
        behavior: 'instant'
      })
    } else finish()
  };
  $('#skip').onclick = e => {
    e.preventDefault();
    finish()
  };

  function finish() {
    save();
    const ui = MousikeData.read('mousike-ui-v1', {});
    const paletteMap = {
      lime: 'mint',
      violet: 'amethyst',
      coral: 'sunset',
      ocean: 'tidal',
      rose: 'rose'
    };
    const mood = (profile.moods || []).join(' ').toLowerCase();
    ui.palette = paletteMap[profile.palette] || (/romantic|melancholic|nostalgic/.test(mood) ? 'rose' : /focus|sleep|chill/.test(mood) ? 'tidal' : /party|energetic|workout/.test(mood) ? 'sunset' : 'mint');
    MousikeData.write('mousike-ui-v1', ui);
    localStorage.setItem('aurora_onboarded', '1');
    sessionStorage.setItem('mousike-arrival', '1');
    location.replace('/');
  }

  show(); // Preload only a small first batch during idle time; never delay first render.
  const warm = () => preloadPortraits(options.artists.slice(0, 6));
  if ('requestIdleCallback' in window) requestIdleCallback(warm, {
    timeout: 1800
  });
  else setTimeout(warm, 750);
})();
