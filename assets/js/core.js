/* Mousike core: existing album/metadata flows, browser-owned state and utilities. */
'use strict';
const $ = selector => document.querySelector(selector);
const esc = value => String(value ?? '').replace(/[&<>"']/g, c => ({
  '&': '&amp;',
  '<': '&lt;',
  '>': '&gt;',
  '"': '&quot;',
  "'": '&#39;'
} [c]));
const load = (key, fallback = []) => MousikeData.read('aurora_' + key, fallback);
const store = (key, value) => MousikeData.write('aurora_' + key, value);
const fmt = value => {
  const n = Math.max(0, Math.floor(Number(value) || 0));
  return `${Math.floor(n/60)}:${String(n%60).padStart(2,'0')}`;
};

function safeImg(track) {
  const url = String(track?.image || '');
  if ((url.startsWith('/assets/') || url.startsWith('/api/portrait?') || url.startsWith('https://')) && !/[\s"'<>()[\]\\]/.test(url)) return url;
  return /^[\w-]{11}$/.test(track?.id || '') ? `https://i.ytimg.com/vi/${track.id}/hqdefault.jpg` : '/assets/images/placeholder.svg';
}
const state = {
  page: 'home',
  tracks: [],
  albums: [],
  homeAlbums: [],
  queue: [],
  index: -1,
  current: null,
  likes: MousikeData.tracks(load('likes')),
  history: MousikeData.tracks(load('history'), 100),
  shuffle: false,
  repeat: 0,
  busy: false,
  query: '',
  collection: null,
  albumLoading: false,
  requestId: 0,
  hybridVideoId: null,
  hybridError: '',
  searchArtists: [],
  searchDiscoveries: []
};
let audio = $('#audio');
let prefs = {
  accent: '#c8e6a0',
  speed: 1,
  autoplay: true,
  motion: false,
  volume: 75,
  albumMode: 'auto',
  crossfade: 0,
  prebuffer: false,
  normalization: false,
  sleep: 0,
  ...load('aurora-preferences-v2', {})
};
let tasteProfile = MousikeData.read('aurora_taste_profile', {}) || {};
const personalSections = [];
let render = () => {};
let search = async () => {};

function savePrefs() {
  store('aurora-preferences-v2', prefs);
}

function applyPrefs() {
  prefs.speed = Math.max(.5, Math.min(2, Number(prefs.speed) || 1));
  prefs.volume = Math.max(0, Math.min(100, Number(prefs.volume) || 0));
  document.querySelectorAll('audio').forEach(el => {
    el.playbackRate = prefs.speed;
    el.volume = prefs.volume / 100;
  });
  $('#volume').value = prefs.volume;
  window.MousikePlayback?.preferencesChanged();
}
let settings = (open = true) => {
  if (open) location.href = '/settings.html';
};
let expanded = (open = true) => {
  if (open) location.href = '/player.html';
};

function updateModeHint() {}
const albumPreference = () => ['auto', 'split', 'metadata'].includes(prefs.albumMode) ? prefs.albumMode : 'auto';
const tasteAvatar = () => {
  const photo = String(tasteProfile.avatar_data || '');
  return /^data:image\/(?:png|jpeg|webp);base64,[A-Za-z0-9+/=]+$/.test(photo) && photo.length < 300000 ? `<img src="${photo}" alt="Your profile">` : `<svg class="ui-icon" aria-hidden="true"><use href="#i-user"/></svg>`;
};

function updateProfileButton() {
  $('#tasteProfileButton').innerHTML = tasteAvatar();
}
let toastTimer;

function notify(message) {
  const el = $('#appToast');
  el.textContent = String(message).slice(0, 240);
  el.classList.add('visible');
  clearTimeout(toastTimer);
  toastTimer = setTimeout(() => el.classList.remove('visible'), 4200);
}
async function apiJSON(url, options = {}) {
  const response = await fetch(url, {
    credentials: 'same-origin',
    ...options
  });
  const data = await response.json().catch(() => ({}));
  if (!response.ok) throw Error(typeof data.detail === 'string' ? data.detail : `Request failed (${response.status})`);
  return data;
}
let collectionRequest = 0,
  collectionController = null;
async function openAlbum(id, kind = 'album') {
  if (kind === 'album_video') return openHybrid(id);
  const serial = ++collectionRequest;
  ++state.requestId;
  collectionController?.abort();
  collectionController = new AbortController();
  state.page = 'album';
  state.albumLoading = true;
  state.collection = null;
  state.hybridVideoId = null;
  state.hybridError = '';
  render();
  try {
    const data = await apiJSON('/api/' + (kind === 'playlist' ? 'playlist/' : 'album/') + encodeURIComponent(id), {
      signal: collectionController.signal
    });
    if (serial !== collectionRequest) return;
    state.collection = data;
    state.albumLoading = false;
    render();
  } catch (e) {
    if (e.name === 'AbortError') return;
    state.albumLoading = false;
    state.hybridError = e.message;
    render();
    notify(e.message);
  }
}
async function openHybrid(id, mode = albumPreference(), albumTitle = '', albumArtist = '') {
  if (!/^[\w-]{11}$/.test(id)) return notify('Invalid YouTube recording');
  const serial = ++collectionRequest;
  ++state.requestId;
  collectionController?.abort();
  collectionController = new AbortController();
  state.page = 'album';
  state.albumLoading = true;
  state.collection = null;
  state.hybridVideoId = id;
  state.hybridMode = mode;
  state.hybridError = '';
  render();
  try {
    const params = new URLSearchParams({
      mode
    });
    if (albumTitle) params.set('album_title', albumTitle);
    if (albumArtist) params.set('album_artist', albumArtist);
    const data = await apiJSON('/api/album-video/' + id + '?' + params, {
      signal: collectionController.signal
    });
    if (serial !== collectionRequest) return;
    state.collection = data;
    state.albumLoading = false;
    render();
    if (data.method === 'split') {
      apiJSON('/api/album-video/' + id + '/art', {
        signal: collectionController.signal
      }).then(art => {
        if (serial !== collectionRequest || !art.matched || !state.collection) return;
        const previous = data.album.image;
        data.album.fallback_image = previous;
        data.album.image = art.image;
        data.tracks.forEach(t => {
          t.image = art.image;
        });
        if (state.page === 'album') render();
      }).catch(() => {});
    }
  } catch (e) {
    if (e.name === 'AbortError') return;
    if (serial !== collectionRequest) return;
    state.albumLoading = false;
    state.hybridError = e.message;
    render();
    notify(e.message);
  }
}

function playOriginalVideo(id) {
  const track = state.tracks.find(t => t.id === id) || {
    id,
    title: state.collection?.album?.title || 'Original upload',
    artist: state.collection?.album?.artist || 'YouTube',
    duration: 0
  };
  return playAt(0, [{
    ...track,
    kind: 'video',
    original: true
  }]);
}

function loadPersonalHome() {
  const feed = window.AuroraSmart?.feed();
  if (!feed) return;
  personalSections.splice(0, personalSections.length, ...(feed.sections || []));
  if (state.page === 'home') render();
}
$('#content').addEventListener('change', e => {
  if (e.target.id === 'hybridMode' && state.hybridVideoId) openHybrid(state.hybridVideoId, e.target.value);
});
$('#content').addEventListener('click', e => {
  if (e.target.closest('#retryAlbum') && state.hybridVideoId) {
    e.stopImmediatePropagation();
    openHybrid(state.hybridVideoId, $('#hybridMode')?.value || 'auto', $('#manualAlbumTitle')?.value || '', $('#manualAlbumArtist')?.value || '');
  }
  if (e.target.closest('#playOriginalUpload') && state.hybridVideoId) {
    e.stopImmediatePropagation();
    playOriginalVideo(state.hybridVideoId);
  }
}, true);
// Shared image fallback works under a strict script policy, without inline handlers.
document.addEventListener('error', e => {
  const image = e.target;
  if (image.tagName === 'IMG' && !image.dataset.fellBack) {
    image.dataset.fellBack = '1';
    image.src = '/assets/images/placeholder.svg';
  }
}, true);
window.addEventListener('mousike:storage-full', () => notify('Device storage is full. Export your data in Settings before clearing space.'));
window.addEventListener('mousike:feed-updated', loadPersonalHome);
applyPrefs();
updateProfileButton();
if (!localStorage.getItem('aurora_onboarded')) location.replace('/onboarding.html');
