/* Mousike · artist portraits from Wikipedia; same-origin proxy, progressive hydration. */
(() => {
  'use strict';
  const KEY = 'mousike-portrait-catalog-v4',
    TTL = 7 * 86400_000;
  const popular = ['Arijit Singh', 'Shreya Ghoshal', 'A. R. Rahman', 'Sonu Nigam', 'Pritam', 'Atif Aslam', 'The Weeknd', 'Taylor Swift', 'Billie Eilish', 'Lana Del Rey', 'Diljit Dosanjh', 'KK', 'Kishore Kumar', 'Lata Mangeshkar', 'Badshah', 'AP Dhillon', 'Drake', 'Coldplay', 'BTS', 'Ed Sheeran'];
  const normalize = n => String(n || '').trim().replace(/\s+/g, ' ').toLocaleLowerCase();
  let catalogue = {};
  try {
    const x = JSON.parse(localStorage.getItem(KEY) || '{}');
    if (x && typeof x === 'object') catalogue = x
  } catch {}
  let scheduled = false;
  let busy = false;
  const pending = new Set();
  const localUrl = name => '/api/portrait?name=' + encodeURIComponent(name);
  const validName = n => typeof n === 'string' && n.length > 0 && n.length <= 80;

  function get(name) {
    const manual = window.MOUSIKE_ARTIST_IMAGES?.[name];
    if (manual) return manual;
    const hit = catalogue[normalize(name)];
    return hit && hit.found && Date.now() - hit.at < TTL ? localUrl(name) : ''
  }

  function update() {
    document.querySelectorAll('[data-mousike-portrait]').forEach(el => {
      const name = el.dataset.mousikePortrait;
      const url = get(name);
      if (!url) return;
      if (el.tagName === 'IMG') {
        if (el.dataset.portraitSrc === url) return;
        el.dataset.portraitSrc = url;
        el.onerror = () => {
          el.style.opacity = '0'
        };
        el.loading = el.closest('.artist-hero') ? 'eager' : 'lazy';
        el.decoding = 'async';
        el.src = url;
      } else {
        let img = el.querySelector(':scope > .mousike-artist-photo');
        if (!img) {
          img = document.createElement('img');
          img.className = 'mousike-artist-photo';
          img.alt = name + ' photo from Wikipedia';
          img.loading = 'lazy';
          img.decoding = 'async';
          el.prepend(img)
        }
        if (img.dataset.portraitSrc === url) return;
        img.dataset.portraitSrc = url;
        img.onerror = () => {
          img.style.display = 'none'
        };
        img.src = url;
      }
    })
  }

  function queuePaint() {
    if (scheduled) return;
    scheduled = true;
    requestAnimationFrame(() => {
      scheduled = false;
      update()
    })
  }

  function save() {
    try {
      const entries = Object.entries(catalogue).sort((a, b) => b[1].at - a[1].at).slice(0, 80);
      catalogue = Object.fromEntries(entries);
      localStorage.setItem(KEY, JSON.stringify(catalogue))
    } catch {}
  }
  // Request only tiny batches of *public* names; Wikipedia is queried by Python, never directly from browser.
  async function warm(names = []) {
    const all = [...new Set([...names, ...popular].filter(validName))];
    const missing = all.filter(n => !get(n) && !pending.has(normalize(n)) && !(catalogue[normalize(n)]?.at && Date.now() - catalogue[normalize(n)].at < 3600000));
    if (!missing.length) {
      queuePaint();
      return
    }
    const groups = [];
    for (let i = 0; i < missing.length; i += 6) groups.push(missing.slice(i, i + 6));
    let idx = 0;
    async function worker() {
      while (idx < groups.length) {
        const group = groups[idx++];
        group.forEach(n => pending.add(normalize(n)));
        try {
          const r = await fetch('/api/artist-portraits', {
            method: 'POST',
            headers: {
              'Content-Type': 'application/json'
            },
            body: JSON.stringify({
              names: group
            })
          });
          if (!r.ok) throw Error('Image lookup unavailable');
          const obj = await r.json();
          for (const name of group) {
            catalogue[normalize(name)] = {
              found: !!obj.portraits?.[name]?.local_image,
              at: Date.now()
            }
          };
          save();
          queuePaint();
        } catch (err) {
          console.debug('Wikipedia portrait lookup:', err.message)
        } finally {
          group.forEach(n => pending.delete(normalize(n)))
        }
      }
    }
    await Promise.all([worker(), worker()]);
  }
  window.MousikePortraits = {
    get,
    warm,
    update,
    popular
  };

  function boot() {
    const root = document.getElementById('content') || document.body;
    new MutationObserver(queuePaint).observe(root, {
      subtree: true,
      childList: true
    });
    queuePaint();
    let preferred = [];
    try {
      preferred = (JSON.parse(localStorage.getItem('aurora_taste_profile') || '{}').artists || []).slice(0, 12)
    } catch {}
    // Warm user's most relevant artists first, then the popular catalog after the page becomes idle.
    warm(preferred.slice(0, 6)).catch(() => {});
    const later = () => warm(preferred).catch(() => {});
    if ('requestIdleCallback' in window) requestIdleCallback(later, {
      timeout: 2000
    });
    else setTimeout(later, 1200);
  }
  if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', boot, {
    once: true
  });
  else boot();
})();
