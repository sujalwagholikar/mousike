/* Shell: appearance, settings overlay, search, arrival and accessible dialogs. */
(() => {
  'use strict';
  const D = MousikeData;
  const palettes = {
    mint: '#64d98b',
    amethyst: '#c1b5dc',
    sunset: '#e8b89f',
    tidal: '#a1cdd0',
    rose: '#deafbc',
    gold: '#dfcc9f'
  };

  function applyAppearance() {
    const ui = D.read(D.keys.ui, {}),
      root = document.documentElement;
    root.dataset.mousikeTheme = ['dark', 'graphite', 'midnight', 'forest', 'light', 'dusk', 'dual-emerald', 'dual-plum', 'dual-copper', 'dual-ocean', 'dual-ivory', 'dual-lilac'].includes(ui.theme) ? ui.theme : 'dark';
    root.dataset.mousikeDensity = ui.density || 'balanced';
    root.dataset.mousikeTypography = ui.typography || 'curated';
    root.dataset.mousikeGlass = ui.glass || 'balanced';
    root.dataset.mousikeArtworkFit = ui.artworkFit || 'contain';
    root.dataset.mousikeCardSize = ui.cardSize || 'balanced';
    root.dataset.mousikeRound = ui.rounding || 'rounded';
    root.dataset.mousikeFont = ui.fontScale || 'normal';
    root.dataset.mousikeMotion = ui.motion || matchMedia('(prefers-reduced-motion: reduce)').matches ? 'reduced' : 'normal';
    const color = ui.palette === 'custom' && /^#[a-f\d]{6}$/i.test(ui.custom) ? ui.custom : palettes[ui.palette] || palettes.mint;
    root.style.setProperty('--accent', color);
    root.style.setProperty('--taste-accent', color);
    document.querySelector('meta[name=theme-color]')?.setAttribute('content', ui.theme === 'light' ? '#f0f1eb' : '#0c0f0e');
  }
  const layer = document.createElement('div');
  layer.id = 'mousikeSettingsLayer';
  layer.className = 'mousike-settings-overlay';
  layer.setAttribute('role', 'dialog');
  layer.setAttribute('aria-modal', 'true');
  layer.setAttribute('aria-label', 'Mousike settings');
  layer.innerHTML = '<iframe title="Mousike settings"></iframe>';
  document.body.append(layer);
  let lastFocus = null;

  function openSettings(section = 'appearance') {
    if (layer.classList.contains('open')) return;
    lastFocus = document.activeElement;
    layer.classList.add('open');
    $('#appShell').inert = true;$('.premium-player').inert=true;
    layer.querySelector('iframe').src = '/settings.html#' + encodeURIComponent(section);
    layer.querySelector('iframe').focus();
  }

  function closeSettings() {
    layer.classList.remove('open');
    layer.querySelector('iframe').removeAttribute('src');
    $('#appShell').inert = false;$('.premium-player').inert=false;
    lastFocus?.focus?.();
  }
  window.MousikeSettings = {
    open: openSettings,
    close: closeSettings
  };
  settings = open => open === false ? closeSettings() : openSettings();
  $('#settingsNav').onclick = () => openSettings();
  $('#tasteProfileButton').onclick = () => openSettings('personal');
  $('#sidebarTaste').onclick = () => openSettings('personal');
  $('#discover').onclick = () => window.AuroraSmart?.discover();
  window.addEventListener('message', e => {
    if (e.origin !== location.origin || e.source !== layer.querySelector('iframe').contentWindow) return;
    if (e.data?.type === 'mousike:settings:close') closeSettings();
  });
  document.addEventListener('keydown', e => {
    if (e.key === 'Escape') {
      closeSettings();
      $('#searchSuggestions').hidden = true;
    }
    if ((e.ctrlKey || e.metaKey) && e.key.toLowerCase() === 'k') {
      e.preventDefault();
      $('#query').focus();
    }
  });
  window.addEventListener('storage', e => {
    if (e.key === D.keys.ui) { applyAppearance(); updateNow(); if (state.page === 'home') render(); }
    if (e.key === D.keys.prefs) {
      Object.assign(prefs, D.read(D.keys.prefs, {}));
      applyPrefs();
    }
    if (e.key === D.keys.studio) {
      Object.assign(studio, D.read(D.keys.studio, {}));
      applyStudio();
    }
    if (e.key === D.keys.profile) {
      tasteProfile = D.read(D.keys.profile, {});
      updateProfileButton();
      if (state.page === 'home') render();
    }
  });
  async function promptDialog(title, value = '') {
    const dialog = $('#textDialog'),
      input = $('#textDialogInput');
    $('#textDialogTitle').textContent = title;
    input.value = value;
    input.required = true;
    dialog.returnValue = '';
    dialog.showModal();
    input.focus();
    input.select();
    return new Promise(resolve => dialog.addEventListener('close', () => resolve(dialog.returnValue === 'ok' ? input.value.trim() : null), {
      once: true
    }));
  }
  async function confirmDialog(title) {
    const dialog = $('#confirmDialog');
    $('#confirmDialogTitle').textContent = title;
    dialog.returnValue = '';
    dialog.showModal();
    return new Promise(resolve => dialog.addEventListener('close', () => resolve(dialog.returnValue === 'ok'), {
      once: true
    }));
  }
  window.MousikeUI = {
    prompt: promptDialog,
    confirm: confirmDialog,
    appearance: applyAppearance
  };
  $('#closeTasteDialog').onclick = () => $('#tasteDialog').close();
  // Suggestions use known artist names and cached real catalog results only.
  const input = $('#query'),
    list = $('#searchSuggestions');
  let suggestionCtl = null,
    suggestionTimer = 0,
    liveTimer = 0,
    selected = -1;

  function closeSuggestions() {
    list.hidden = true;
    input.setAttribute('aria-expanded', 'false');
    input.removeAttribute('aria-activedescendant');
    selected = -1;
  }

  function choose(button) {
    if (!button) return;
    input.value = button.dataset.query;
    closeSuggestions();
    clearTimeout(liveTimer);
    search(input.value);
  }
  list.onclick = e => choose(e.target.closest('[data-query]'));
  input.addEventListener('input', e => {
    if (e.isComposing) return;
    clearTimeout(suggestionTimer);
    clearTimeout(liveTimer);
    suggestionCtl?.abort();
    const query = input.value.trim();
    if (query.length < 2) {
      closeSuggestions();
      return;
    }
    suggestionTimer = setTimeout(async () => {
      suggestionCtl = new AbortController();
      try {
        const data = await apiJSON('/api/suggestions?q=' + encodeURIComponent(query), {
          signal: suggestionCtl.signal
        });
        if (input.value.trim() !== query) return;
        list.innerHTML = (data.suggestions || []).map((s, i) => `<button id="suggestion-${i}" role="option" aria-selected="false" data-query="${esc(s.query)}"><svg class="ui-icon" aria-hidden="true"><use href="#i-${s.type==='artist'?'user':'search'}"/></svg><span>${esc(s.label)}<small>${esc(s.type)}</small></span></button>`).join('');
        list.hidden = !data.suggestions?.length;
        input.setAttribute('aria-expanded', String(!list.hidden));
        selected = -1;
      } catch {}
    }, 170);
    if (query.length >= 3) liveTimer = setTimeout(() => {
      closeSuggestions();
      search(query);
    }, 900);
  });
  input.addEventListener('compositionend', () => input.dispatchEvent(new Event('input')));
  input.addEventListener('keydown', e => {
    const options = [...list.querySelectorAll('button')];
    if (!list.hidden && ['ArrowDown', 'ArrowUp'].includes(e.key)) {
      e.preventDefault();
      clearTimeout(liveTimer);
      selected = (selected + (e.key === 'ArrowDown' ? 1 : -1) + options.length) % options.length;
      options.forEach((b, i) => b.setAttribute('aria-selected', String(i === selected)));
      input.setAttribute('aria-activedescendant', options[selected].id);
    }
    if (e.key === 'Enter') {
      e.preventDefault();
      clearTimeout(liveTimer);
      clearTimeout(suggestionTimer);
      if (!list.hidden && selected >= 0) choose(options[selected]);
      else {
        closeSuggestions();
        search(input.value);
      }
    }
    if (e.key === 'Escape') closeSuggestions();
  });
  document.addEventListener('click', e => {
    if (!e.target.closest('.search')) closeSuggestions();
  });

  const arrival = () => window.MousikeExperience?.arrival();
  applyAppearance();
  arrival();
})();
