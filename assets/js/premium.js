/* Mousike premium workspace / design system
   Keeps yt-dlp playback, clip slicing, MusicBrainz, user taste and Audio Studio
   from the pre-existing engine. Adds pages, playlists, artists and the new UI. */
(() => {
  'use strict';
  const icon = key => `<svg class="ui-icon" aria-hidden="true"><use href="#i-${key}"></use></svg>`;
  const safe = v => esc(String(v ?? ''));
  const escapeAttr = safe;
  const shell = document.getElementById('appShell');
  const main = document.getElementById('main');
  const content = document.getElementById('content');
  const panelBody = document.getElementById('sidePanelBody');
  const palette = ['#9be2b4', '#89b9e7', '#d1a4c9', '#edbc8b', '#b3e18c', '#baa6ec', '#d5a6a1'];
  const getColor = (key, fallback = 0) => {
    let h = 0;
    for (const s of String(key ?? '')) h = (Math.imul(31, h) + s.codePointAt(0)) | 0;
    return palette[Math.abs(h || fallback) % palette.length]
  };
  const rgbFromHex = hex => [1, 3, 5].map(i => parseInt(hex.slice(i, i + 2), 16)).join(',');
  let collectionMode = 'all';
  let artistModel = null,
    artistSerial = 0;
  let openPlaylistId = null;
  let playlists = load('user-playlists-v3', []);
  let followedArtists = load('followed-artists-v3', []);
  let showPanel = window.innerWidth > 1500 && !!state.current;
  let menuEl = null,
    menuTrack = null;
  let searchType = 'all';
  let fromPage = 'home';

  function setAmbient(source) {
    const color = getColor(source?.id || source?.title || source);
    const base = rgbFromHex(color);
    const rgb = base.split(',').map(x => Math.round(+x * .42)).join(',');
    document.documentElement.style.setProperty('--ambient-rgb', rgb);
  }

  function preferArtwork(t) {
    return safeImg(t) || (t?.id && /^[\w-]{11}$/.test(t.id) ? `https://i.ytimg.com/vi/${t.id}/hqdefault.jpg` : '');
  }

  function artwork(t, className = '', opts = '') {
    const src = preferArtwork(t);
    return src ? `<img class="${className}" loading="lazy" decoding="async" src="${escapeAttr(src)}" alt="${escapeAttr(t?.title || 'Artwork')}" ${opts}>` : `<span class="art-placeholder">${icon('music')}</span>`;
  }

  function cleanArtist(t) {
    return (t?.performer || t?.artist || 'Unknown artist').replace(/\s*(?:- Topic|VEVO|Official)\s*$/i, '').trim() || 'Unknown artist';
  }

  function playing(t) {
    return !!(t && state.current && (state.current.id === t.id || (t.source_video_id && state.current.source_video_id === t.source_video_id && state.current.start === t.start)));
  }

  function trackKind(t) {
    return (Number(t.duration) > 480 || t.kind === 'long_video') && !t.segment && !t.unresolved ? 'album_video' : 'track';
  }

  function miniArtwork(items) {
    return items?.slice(0, 3).map(t => artwork(t, '', 'aria-hidden="true"')).join('') || '';
  }

  function sectionHeader(title, subtitle = '', button = '') {
    return `<div class="shelf-title-row"><div><h2>${safe(title)}</h2>${subtitle?`<p>${safe(subtitle)}</p>`:''}</div>${button}</div>`;
  }

  function buttonAll(query) {
    return `<button class="see-all" data-action="search" data-query="${escapeAttr(query)}">Show all</button>`
  }

  function card(t, i, source = 'search') {
    const isLong = trackKind(t) === 'album_video';
    return `<article class="music-card"><button class="card-main" data-action="play" data-source="${escapeAttr(source)}" data-index="${i}" aria-label="${isLong?'Open album':'Play'} ${escapeAttr(t.title)}"><span class="music-art">${artwork(t)}<span class="card-float">${icon(isLong?'disc':'play')}</span>${isLong?'<span class="card-badge">ALBUM UPLOAD</span>':''}</span><span class="music-card-title">${safe(t.title)}</span><span class="music-card-sub">${safe(cleanArtist(t))}</span></button><button class="card-menu" data-action="menu" data-source="${escapeAttr(source)}" data-index="${i}" aria-label="Options for ${escapeAttr(t.title)}">${icon('more')}</button></article>`;
  }

  function mixCard(section, index) {
    const tone = getColor(section.id || section.title);
    let items = section.tracks || [];
    return `<button class="music-card generated-card" data-action="mix" data-mix="${escapeAttr(section.id)}" title="Open ${escapeAttr(section.title)}"><span class="music-art" style="--mixtone:${tone}"><span class="mix-chip">MOUSIKE MIX ${String(index+1).padStart(2,'0')}</span><span class="mix-mini-art">${miniArtwork(items)}</span><span class="mix-art-label">${safe(section.title.replace(/^More from /,'').replace(/^On repeat in /,'').replace(/^Inspired by your listening/,'On repeat'))}</span><span class="card-float">${icon('play')}</span></span><span class="music-card-title">${safe(section.title)}</span><span class="music-card-sub">Made for ${safe(tasteProfile?.name?.split(' ')[0] || 'you')} · ${items.length || 'Fresh'} tracks</span></button>`;
  }

  function shelf(title, subtitle, items, source, query = '') {
    const body = items?.length ? items.map((t, i) => card(t, i, source)).join('') :
      ((state.busy || window.AuroraSmart?.fetching()) ? Array.from({
        length: 6
      }, () => '<div class="music-card skeleton-card" aria-hidden="true"><div class="music-art premium-skeleton"></div><div class="skeleton-line"></div><div class="skeleton-line short"></div></div>').join('') : '<div class="shelf-empty"><p>No tracks here yet.</p><button class="soft-cta" data-action="page" data-page="search">Find music</button></div>');
    return `<section class="editorial-shelf">${sectionHeader(title,subtitle,query?buttonAll(query):'')}<div class="shelf-track">${body}</div></section>`;
  }

  function go(page, save = true) {
    if (state.page !== page && save) {
      fromPage = state.page;
      ++state.requestId;
    }
    state.page = page;
    if (page === 'home' && personalSections.length === 0) loadPersonalHome();
    render();
    main.scrollTop = 0;
    if (save) history.pushState({
      mousikePage: page
    }, '', '#' + encodeURIComponent(page));
  }

  function actionsTrack(t, i, source, compact = false) {
    const isLive = playing(t),
      long = trackKind(t) === 'album_video',
      liked = state.likes.some(item => sameSong(item, t));
    return `<div class="song-line ${isLive?'playing':''}" data-action="play" data-source="${escapeAttr(source)}" data-index="${i}" role="button" tabindex="0" aria-label="${escapeAttr(long?'Open album':'Play')} ${escapeAttr(t.title)}"><span class="song-number">${isLive?icon('music'):i+1}</span><div class="song-primary">${artwork(t,'song-thumb')}<div class="song-words"><span class="song-title">${safe(t.title)} ${long?'<span class="artist-label">· Album upload</span>':''}</span><span class="song-artist"><button data-action="artist" data-artist="${escapeAttr(cleanArtist(t))}">${safe(cleanArtist(t))}</button>${t.segment?` · ${fmt(t.start)}–${fmt(t.end)}`:''}</span></div></div><div class="song-album">${safe(t.album || (long?'Long-form upload':cleanArtist(t)))}</div><button class="song-like ${liked?'on':''}" data-action="like-track" data-source="${escapeAttr(source)}" data-index="${i}" aria-pressed="${liked}" aria-label="${liked?'Unlike':'Like'} ${escapeAttr(t.title)}" title="${liked?'Remove from liked songs':'Save to liked songs'}">${icon('heart')}</button><span class="song-duration">${t.duration?fmt(t.duration):'—'}</span><button class="song-download" data-action="download" data-source="${escapeAttr(source)}" data-index="${i}" title="${long?'Open this album to download individual songs':'Download audio'}" aria-label="Download ${escapeAttr(t.title)}">${downloadIcon}</button><button class="song-more" data-action="menu" data-source="${escapeAttr(source)}" data-index="${i}" title="More options">${icon('more')}</button></div>`;
  }
  const downloadIcon = '<svg class="ui-icon" viewBox="0 0 24 24" aria-hidden="true"><path d="M12 3v11m-4-4 4 4 4-4M4 16v4h16v-4" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"/></svg>';

  function tableHeader() {
    return `<div class="table-head"><span class="number-heading">#</span><span>Title</span><span class="album-heading">Album</span><span class="like-heading" aria-label="Likes"></span><span class="duration-heading">${icon('clock')}</span><span class="download-heading"></span><span></span></div>`
  }

  function trackTable(items, source = 'search', compact = false) {
    return `<div class="track-table">${!compact?tableHeader():''}${items.map((t,i)=>actionsTrack(t,i,source,compact)).join('')}</div>`
  }

  function likesCollection() {
    return state.likes || []
  }

  function madeForYouTracks() {
    const feed = window.AuroraSmart?.feed() || {};
    const candidates = (feed.sections || []).find(section => section.id === 'for-you')?.tracks || feed.tracks || [];
    const result = [];
    for (const track of candidates) {
      if (!result.some(item => sameSong(item, track))) result.push(track);
      if (result.length >= 12) break;
    }
    return result;
  }

  function sourceList(source) {
    if (source === 'made-for-you') return madeForYouTracks();
    if (source === 'smart') return window.AuroraSmart?.tracks() || [];
    if (source === 'history') return state.history;
    if (source === 'favorites') return state.likes;
    if (source === 'queue') return state.queue;
    if (source === 'search') return state.tracks;
    if (source === 'related-search') return state.searchDiscoveries || [];
    if (source === 'search-short') return state.tracks.filter(t => trackKind(t) === 'track');
    if (source === 'search-long') return state.tracks.filter(t => trackKind(t) === 'album_video');
    if (source === 'album') return state.collection?.tracks || [];
    if (source === 'artist') return artistModel?.tracks || [];
    if (source === 'playlist') return playlists.find(p => p.id === openPlaylistId)?.tracks || [];
    if (source.startsWith('shelf:')) return personalSections.find(s => s.id === source.slice(6))?.tracks || [];
    if (source.startsWith('mix:')) return personalSections.find(s => s.id === source.slice(4))?.tracks || [];
    return state.tracks;
  }

  function homePage() {
    const profile = tasteProfile || {},
      name = (profile.name || 'listener').split(' ')[0],
      greeting = window.MousikeExperience.greeting();
    const sections = personalSections || [];
    const loading = window.AuroraSmart?.fetching();
    const modes = `<div class="home-filters"><button class="filter-pill ${collectionMode==='all'?'selected':''}" data-action="home-filter" data-filter="all">For you</button><button class="filter-pill ${collectionMode==='discover'?'selected':''}" data-action="home-filter" data-filter="discover">Discover</button><button class="filter-pill ${collectionMode==='recent'?'selected':''}" data-action="home-filter" data-filter="recent">Recently played</button></div>`;
    const heading = `<div class="home-intro"><div><span class="page-kicker">SOUND IN MOTION</span><h1>${safe(greeting.title)}, ${safe(name)}.</h1><p class="greeting-subtitle">${safe(greeting.subtitle)}</p></div><button class="home-refresh" data-action="refresh-home" aria-label="Refresh your recommendations">${icon('refresh')}<span>${loading?'Finding music':'Refresh'}</span></button></div>`;
    if (collectionMode === 'recent') return `<div class="fade-enter">${modes}${heading}${shelf('Recently Played','Pick up where you left off',state.history,'history')}</div>`;
    const homeUI = MousikeData.read(MousikeData.keys.ui, {});
    const artists = [...new Set([...(profile.artists || []), ...followedArtists])].slice(0, 8);
    const quick = artists.length ? `<section class="editorial-shelf artist-gallery-section">${sectionHeader('Your artists','Familiar voices. A world to explore.')}<div class="artist-shortcuts artist-gallery">${artists.map(n=>`<button data-action="artist" data-artist="${escapeAttr(n)}" aria-label="Explore ${escapeAttr(n)}" style="--artist-tone:${getColor(n)}"><span data-mousike-portrait="${escapeAttr(n)}">${safe(n[0])}</span><strong>${safe(n)}</strong><small>Artist</small></button>`).join('')}</div></section>` : '';
    const made = collectionMode === 'all' ? shelf('Made for You','Shaped by your listening, likes, saved music and feedback',madeForYouTracks(),'made-for-you') : '';
    const mixes = sections.filter(x => x.tracks?.length).slice(0, 4);
    const daily = mixes.length ? `<section class="editorial-shelf">${sectionHeader('Daily Mixes','Small collections for the way you listen')}<div class="mix-grid">${mixes.map(mixCard).join('')}</div></section>` : '';
    const visible = collectionMode === 'discover' ? sections.filter(x => ['discover', 'weekly', 'mood'].includes(x.id)) : sections.filter(section => section.id !== 'for-you');
    const shelves = visible.map(sec => shelf(sec.title, sec.description, sec.tracks, 'shelf:' + sec.id)).join('');
    const albumShelf = state.homeAlbums.length ? `<section class="editorial-shelf">${sectionHeader('Albums For You','Full releases from the artists in your rotation')}<div class="shelf-track">${state.homeAlbums.map(albumCard).join('')}</div></section>` : '';
    const recent = collectionMode === 'all' && state.history.length ? shelf('Recently Played', 'A good place to return to', state.history.slice(0, 8), 'history') : '';
    return `<div class="fade-enter home-music-first">${modes}${heading}${made}${homeUI.homeArtists!==false?quick:''}${homeUI.homeMixes!==false?daily:''}${homeUI.homeAlbums!==false?albumShelf:''}${shelves}${homeUI.homeRecent!==false?recent:''}<footer class="home-footer"><img src="/assets/brand/mousike-mark-static.svg?v=6" alt="" width="32" height="32"><div><strong>Mousike</strong><span>Sound In motion</span></div><button data-action="onboarding">Tune your taste</button></footer></div>`;
  }

  function searchPage() {
    const title = state.query || '';
    const tracks = state.tracks || [];
    const albums = state.albums || [];
    const short = tracks.filter(x => trackKind(x) === 'track');
    const longs = tracks.filter(x => trackKind(x) === 'album_video');
    const activeTracks = searchType === 'long' ? longs : short;
    const first = short[0] || tracks[0];
    const suggestions = (tasteProfile?.artists || []).slice(0, 5);
    if (!title) {
      return `<div class="fade-enter"><div class="page-intro"><div><span class="page-kicker">EXPLORE THE SOUND</span><h1 class="page-title">Search everything.</h1><p class="page-caption">Songs, artists, albums, playlists and long-form mixes.</p></div></div><div class="editorial-shelf">${sectionHeader('Your artists','Music you already love')}<div class="shelf-track">${suggestions.map(n=>artistBubble(n)).join('')}</div></div>${shelf('Popular right now','Fresh music to start with',state.tracks.slice(0,14),'search')}</div>`
    }
    const pick = (searchType === 'all' || searchType === 'songs') && first ? `<div class="search-top"><button class="search-featured" data-action="play" data-source="search" data-index="${tracks.indexOf(first)}">${artwork(first)}<span><span class="page-kicker">TOP RESULT</span><strong>${safe(first.title)}</strong><small>${safe(cleanArtist(first))}</small></span></button><div class="search-mini-list"><div class="track-table">${activeTracks.slice(0,4).map(t=>actionsTrack(t,tracks.indexOf(t),'search',true)).join('')}</div></div></div>` : '';
    const artistGuess = (state.searchArtists || []).map(a => a.name).slice(0, 7);
    return `<div class="fade-enter search-results"><div class="search-results-head"><div><span class="page-kicker">SEARCH RESULTS</span><h1>Results for “${safe(title)}”</h1></div>${state.busy?'<span class="page-kicker">LOOKING FOR MUSIC…</span>':`<span class="sub">${tracks.length} YouTube results</span>`}</div><div class="search-types"><button data-action="search-type" data-type="all" class="${searchType==='all'?'selected':''}">All</button><button data-action="search-type" data-type="songs" class="${searchType==='songs'?'selected':''}">Songs & videos</button><button data-action="search-type" data-type="albums" class="${searchType==='albums'?'selected':''}">Albums</button><button data-action="search-type" data-type="artists" class="${searchType==='artists'?'selected':''}">Artists</button><button data-action="search-type" data-type="playlists" class="${searchType==='playlists'?'selected':''}">Playlists</button><button data-action="search-type" data-type="long" class="${searchType==='long'?'selected':''}">Long uploads</button></div>${state.busy?'<div class="loading-bar"></div>':''}${pick}${!['albums','artists','playlists'].includes(searchType)?shelf(searchType==='long'?'Long-form recordings':'Songs & videos',searchType==='long'?'Expand uploads into playable tracks':'Play a song instantly, or open long videos as albums',activeTracks,searchType==='long'?'search-long':'search-short',''):''}${searchType==='all'||searchType==='artists'?`<section class="editorial-shelf">${sectionHeader('Artists','Explore popular music and discographies')}<div class="shelf-track">${artistGuess.map(artistBubble).join('')}</div></section>`:''}${searchType==='all'||searchType==='albums'?`<section class="editorial-shelf">${sectionHeader('Albums','Open a release to see every track')}<div class="shelf-track">${state.albumLoading?Array.from({length:6},()=>'<div class="music-card"><div class="music-art premium-skeleton"></div></div>').join(''):(albums.length?albums.map(albumCard).join(''):'<div class="sub" style="padding:20px">No matching releases yet. Try searching for a specific album title.</div>')}</div></section>`:''}${searchType==='all'&&longs.length?shelf('Full albums and longer videos','Choose splitting or individual-song playback',longs,'search-long',''):''}${['all','playlists'].includes(searchType)?searchPlaylists(title):''}${searchType==='all'&&state.searchDiscoveries?.length?shelf('Related Discoveries','A little further from your search',state.searchDiscoveries,'related-search'):''}${state.searchPlan?.correction?`<p class="search-note">Also searched for ${safe(state.searchPlan.correction)}</p>`:''}</div>`;
  }

  function searchPlaylists(query) {
    const matched = playlists.filter(p => (p.title + ' ' + p.description).toLocaleLowerCase().includes(query.toLocaleLowerCase()));
    return `<section class="editorial-shelf">${sectionHeader('Playlists','From your saved library')}<div class="shelf-track">${matched.length?matched.map(p=>`<button class="music-card" data-action="open-playlist" data-id="${escapeAttr(p.id)}"><span class="music-art">${p.tracks?.[0]?artwork(p.tracks[0]):icon('disc')}</span><span class="music-card-title">${safe(p.title)}</span><span class="music-card-sub">${p.tracks?.length||0} songs</span></button>`).join(''):'<p class="sub">No saved playlists match this search.</p>'}</div></section>`;
  }

  function albumCard(a, i) {
    return `<button class="music-card" data-action="album" data-id="${escapeAttr(a.id)}" data-kind="${escapeAttr(a.kind||'album')}"><span class="music-art">${artwork(a)}<span class="card-float">${icon('disc')}</span></span><span class="music-card-title">${safe(a.title)}</span><span class="music-card-sub">${safe(a.artist)} · ${safe(a.year||'Album')}</span></button>`
  }

  function artistBubble(name) {
    return `<button class="search-artist-bubble" data-action="artist" data-artist="${escapeAttr(name)}"><span class="circle-art" data-mousike-portrait="${escapeAttr(name)}" style="background:linear-gradient(150deg,${getColor(name)},#172326)">${safe(name).slice(0,1).toUpperCase()}</span><strong>${safe(name)}</strong><span>Artist</span></button>`
  }

  function collectionPage() {
    const c = state.collection;
    if (state.albumLoading) return `<div class="fade-enter"><div class="collection-hero"><div class="collection-cover premium-skeleton"></div><div class="collection-info"><span class="page-kicker">BUILDING YOUR TRACKLIST</span><h1>Finding every song…</h1><p class="collection-desc">Reading chapter timestamps and album metadata without guessing boundaries.</p></div></div><div style="padding:24px 0">${Array.from({length:6},()=>'<div class="premium-skeleton" style="height:51px;margin:9px 0"></div>').join('')}</div></div>`;
    if (!c) {
      return `<div class="empty-state">${icon('disc')}<strong>Album unavailable</strong><p>${safe(state.hybridError||'We could not open this album.')}</p>${state.hybridVideoId?`<div class="method-bar"><input id="manualAlbumTitle" placeholder="Album title"><input id="manualAlbumArtist" placeholder="Artist"><select id="hybridMode"><option value="auto">Auto</option><option value="split">Split upload</option><option value="metadata">Find songs</option></select><button class="soft-cta" id="retryAlbum">Try again</button><button class="soft-cta" id="playOriginalUpload">Play original</button></div>`:''}<button class="soft-cta" data-action="back">Back to results</button></div>`
    }
    const a = c.album || {};
    const tracks = c.tracks || [];
    const hybrid = a.kind === 'album_video';
    const origin = ['playlist', 'mix'].includes(a.kind) ? '<span class="analysis-tag">Your music collection</span>' : hybrid ? '<span class="analysis-tag">YouTube upload analysed</span>' : '<span class="analysis-tag">MusicBrainz album tracklist</span>';
    const split = hybrid && a.method === 'split';
    return `<div class="fade-enter"><div class="collection-hero"><div class="collection-cover">${artwork(a)}</div><div class="collection-info"><span class="collection-eyebrow">${['playlist','mix'].includes(a.kind)?'PLAYLIST':hybrid?'EXPANDED ALBUM VIDEO':'ALBUM'}</span><h1>${safe(a.title)}</h1><div class="collection-meta"><span>${safe(a.artist||'Various artists')}</span> · ${safe(a.year||'')} · ${tracks.length} songs${hybrid&&a.duration?' · '+fmt(a.duration):''}</div><div class="collection-desc">${hybrid?'Individual track browsing for this upload. Select any song, or choose another analysis method.':'A complete tracklist you can browse, play and add to your playlists.'}</div></div></div><div class="collection-actions"><button class="big-play" data-action="play-all" title="Play album">${icon('play')}</button><button class="surface-icon" data-action="shuffle-all" title="Shuffle album">${icon('spark')}</button><button class="surface-icon" data-action="queue-all" title="Add album to queue">${icon('plus')}</button><button class="soft-cta" data-action="save-collection">Save as playlist</button>${hybrid?`<button class="soft-cta" id="playOriginalUpload">Play original video</button>`:''}</div>${hybrid?`<div class="analysis-tags">${origin}<span class="analysis-tag">${split?'Time-coded segments':'Separate recordings'}</span><span class="analysis-tag">${tracks.length} detected tracks</span></div><div class="method-bar"><label for="hybridMode">Playback method</label><select id="hybridMode"><option value="auto" ${state.hybridMode==='auto'?'selected':''}>Auto · Smart fallback</option><option value="split" ${state.hybridMode==='split'?'selected':''}>Split original timestamps</option><option value="metadata" ${state.hybridMode==='metadata'?'selected':''}>Find individual songs</option></select>${a.url?`<a href="${escapeAttr(a.url)}" target="_blank" rel="noopener">View source</a>`:''}</div>${(c.warnings||[]).length?`<p class="detail-note">${safe(c.warnings.join(' · '))}</p>`:''}`:''}<div class="shelf-title-row" style="margin-top:24px"><h2>Tracks</h2><span class="sub">${tracks.length} songs</span></div>${tracks.length?trackTable(tracks,'album'):'<div class="empty-state"><p>No tracklist could be determined for this source.</p></div>'}<div class="detail-note">${split?'This album uses virtual chapter boundaries; songs are not re-encoded.':'Album tracks resolve their YouTube audio when selected.'}</div></div>`;
  }

  function favoritePage(page) {
    const items = page === 'favorites' ? state.likes : page === 'history' ? state.history : state.queue;
    const labels = {
      favorites: ['Liked songs', 'All your favorites in one place'],
      history: ['Recently played', 'Return to your latest discoveries'],
      queue: ['Your play queue', 'Everything queued for this session']
    };
    const [title, caption] = labels[page];
    return `<div class="fade-enter"><div class="collection-hero"><div class="collection-cover" style="background:linear-gradient(135deg,${page==='favorites'?'#623ad2':page==='queue'?'#2c6364':'#493d62'},#1a2e34);font-size:96px;color:var(--taste-accent)">${page==='favorites'?icon('heart'):page==='history'?icon('clock'):icon('list')}</div><div class="collection-info"><span class="collection-eyebrow">YOUR LIBRARY</span><h1>${title}</h1><div class="collection-meta">${safe(tasteProfile?.name||'You')} · ${items.length} tracks</div><p class="collection-desc">${caption}</p></div></div>${items.length?`<div class="collection-actions"><button class="big-play" data-action="play-list" data-source="${page}">${icon('play')}</button><button class="surface-icon" data-action="shuffle-list" data-source="${page}" title="Shuffle">${icon('spark')}</button>${page==='queue'?'<button class="soft-cta" data-action="clear-queue">Clear queue</button>':''}</div>${trackTable(items,page)}`:`<div class="empty-state">${icon('heart')}<strong>Your collection is waiting.</strong><p>Find some music and it will be here when you come back.</p><button class="soft-cta" data-action="page" data-page="search">Explore music</button></div>`}</div>`
  }

  function playlistView(p) {
    const items = p?.tracks || [];
    return `<div class="fade-enter"><div class="collection-hero"><div class="collection-cover" style="background:linear-gradient(155deg,${getColor(p.title)},#1a1d27)">${items.length?artwork(items[0]):icon('music')}</div><div class="collection-info"><span class="collection-eyebrow">YOUR PLAYLIST · MOUSIKE</span><h1>${safe(p.title)}</h1><div class="collection-meta">Made by ${safe(tasteProfile?.name||'You')} · ${items.length} songs</div><p class="collection-desc">${safe(p.description||'A personal collection for every listening moment.')}</p></div></div><div class="collection-actions"><button class="big-play" data-action="play-list" data-source="playlist" title="Play playlist">${icon('play')}</button><button class="surface-icon" data-action="shuffle-list" data-source="playlist" title="Shuffle playlist">${icon('spark')}</button><button class="soft-cta" data-action="rename-playlist">Rename</button><button class="soft-cta" data-action="delete-playlist">Delete playlist</button></div>${items.length?trackTable(items,'playlist'):`<div class="empty-state">${icon('music')}<strong>Add your first song</strong><p>Search for a track, open its options menu and choose this playlist.</p><button class="soft-cta" data-action="page" data-page="search">Find tracks</button></div>`}</div>`;
  }

  function libraryPage() {
    return `<div class="fade-enter"><div class="page-intro"><div><span class="page-kicker">YOUR MUSIC SPACE</span><h1 class="page-title">Your library</h1><p class="page-caption">Your playlists, favorite tracks and followed artists.</p></div><button class="soft-cta" data-action="create-playlist">+ New playlist</button></div><section class="editorial-shelf">${sectionHeader('Playlists','Made by you')}<div class="shelf-track">${playlists.length?playlists.map(p=>`<button class="music-card" data-action="open-playlist" data-id="${escapeAttr(p.id)}"><span class="music-art" style="background:linear-gradient(145deg,${getColor(p.title)},#191e21)">${p.tracks?.length?artwork(p.tracks[0]):icon('music')}</span><span class="music-card-title">${safe(p.title)}</span><span class="music-card-sub">Playlist · ${p.tracks?.length||0} songs</span></button>`).join(''):'<div class="sub" style="padding:18px">You haven’t created any playlists yet.</div>'}</div></section>${window.AuroraSmart?.libraryMarkup?.() || ''}${shelf('Liked songs','Tracks you saved',state.likes||[],'favorites')}${followedArtists.length?`<section class="editorial-shelf">${sectionHeader('Following','Artists you love')}<div class="shelf-track">${followedArtists.map(artistBubble).join('')}</div></section>`:''}</div>`;
  }

  function artistPage() {
    const a = artistModel || {};
    const name = a.name || state.artistName || 'Artist';
    const tracks = a.tracks || [];
    const albums = a.albums || [];
    const followed = followedArtists.some(x => x.toLowerCase() === name.toLowerCase());
    const top = tracks[0];
    if (a.loading) return `<div class="fade-enter"><div class="artist-hero"><div class="collection-info"><span class="collection-eyebrow">ARTIST</span><div class="mousike-artist-hero-portrait" data-mousike-portrait="${escapeAttr(name)}"></div><h1>${safe(name)}</h1><div class="artist-meta">Exploring this artist’s music…</div></div></div>${Array.from({length:6},()=>'<div class="premium-skeleton" style="height:54px;margin:10px 0"></div>').join('')}</div>`;
    return `<div class="fade-enter"><div class="artist-hero">${top?`<div class="artist-back" style="background-image:url('${escapeAttr(preferArtwork(top))}')"></div>`:''}<div class="collection-info"><span class="collection-eyebrow">ARTIST</span><div class="mousike-artist-hero-portrait" data-mousike-portrait="${escapeAttr(name)}"></div><h1>${safe(name)}</h1><div class="artist-meta">${safe(a.country||'')} ${a.country?'·':''} Songs, albums & more</div>${a.description?`<p class="artist-bio">${safe(a.description)}</p>`:''}</div></div><div class="collection-actions"><button class="big-play" data-action="play-list" data-source="artist" title="Play popular songs">${icon('play')}</button><button class="follow-pill ${followed?'on':''}" data-action="follow-artist" data-artist="${escapeAttr(name)}">${followed?'Following':'Follow'}</button><button class="surface-icon" data-action="shuffle-list" data-source="artist" title="Shuffle">${icon('spark')}</button></div><div class="shelf-title-row"><div><h2>Popular</h2><p>Top YouTube results for ${safe(name)}</p></div></div>${tracks.length?trackTable(tracks.slice(0,12),'artist'):'<div class="empty-state"><p>No matching recordings found right now.</p></div>'}<section class="editorial-shelf">${sectionHeader('Discography','Albums and releases')}<div class="shelf-track">${albums.length?albums.map(albumCard).join(''):'<span class="sub">No matching albums found in the catalog.</span>'}</div></section></div>`;
  }

  function renderPremium() {
    const page = state.page;
    if (page === 'album' && state.collection?.album) setAmbient(state.collection.album);
    if (page === 'playlist') {
      const p = playlists.find(x => x.id === openPlaylistId);
      if (p) setAmbient(p.title)
    }
    document.querySelectorAll('.rail-action[data-page]').forEach(b => b.classList.toggle('active', b.dataset.page === page));
    if (page === 'home') content.innerHTML = homePage();
    else if (page === 'search') content.innerHTML = searchPage();
    else if (page === 'album') content.innerHTML = collectionPage();
    else if (['favorites', 'history', 'queue'].includes(page)) content.innerHTML = favoritePage(page);
    else if (page === 'artist') content.innerHTML = artistPage();
    else if (page === 'playlist') content.innerHTML = playlistView(playlists.find(p => p.id === openPlaylistId));
    else if (page === 'library') content.innerHTML = libraryPage();
    else content.innerHTML = homePage();
    content.onclick = contentClicked;
    refreshRail();
    updatePanel();
    window.MousikePortraits?.update();
  }
  render = renderPremium;

  function refreshRail() {
    let bits = `<button class="library-bubble" data-action="page" data-page="favorites" title="Liked songs" aria-label="Liked songs" style="background:linear-gradient(145deg,#623ec8,#34644f)"><span class="library-heart">♥</span></button>`;
    for (const p of playlists.slice(0, 5)) {
      bits += `<button class="library-bubble" data-action="open-playlist" data-id="${escapeAttr(p.id)}" title="${escapeAttr(p.title)}">${p.tracks?.[0]?artwork(p.tracks[0]):icon('disc')}</button>`
    }
    for (const name of followedArtists.slice(0, 5)) {
      bits += `<button class="library-bubble artist" data-action="artist" data-artist="${escapeAttr(name)}" title="${escapeAttr(name)}">${safe(name.slice(0,1))}</button>`
    }
    document.getElementById('railLibrary').innerHTML = bits;
  }

  function updatePanel() {
    const t = state.current;
    if (!t) {
      panelBody.innerHTML = `<div class="side-empty">${icon('music')}<strong>Nothing playing yet</strong><p>Choose a track and your listening view will come to life here.</p><button class="soft-cta" data-action="page" data-page="search">Find music</button></div>`;
      return
    }
    let upcoming = (state.queue || []).slice(state.index + 1, state.index + 6);
    panelBody.innerHTML = `<div class="side-art" data-action="expand">${artwork(t)}</div><div class="side-song-title">${safe(t.title)}</div><button class="side-song-artist" data-action="artist" data-artist="${escapeAttr(cleanArtist(t))}">${safe(cleanArtist(t))}</button><div class="side-actions"><button data-action="like-current" class="${state.likes.some(x=>x.id===t.id)?'fav':''}">${icon('heart')} ${state.likes.some(x=>x.id===t.id)?'Liked':'Like'}</button><button data-action="menu-current">${icon('more')} More</button><button data-action="expand">${icon('panel')} Expand</button></div><div class="side-divider"></div><div class="side-section-title"><span>Next in queue</span><button data-action="page" data-page="queue">Open queue</button></div>${upcoming.length?upcoming.map((next,i)=>`<div class="side-queue-row" data-action="play" data-source="queue" data-index="${state.index+i+1}">${artwork(next)}<div style="min-width:0"><strong>${safe(next.title)}</strong><small>${safe(cleanArtist(next))}</small></div></div>`).join(''):'<div class="side-art-note">Play a playlist or album to see what comes next.</div>'}<div class="side-divider"></div><div class="side-section-title">About this track</div><p class="side-art-note">${t.segment?'A precise '+fmt(t.start)+'–'+fmt(t.end)+' segment from its original upload.':t.unresolved?'This recording will be located on YouTube before playback.':'Audio is delivered from available source recordings through Mousike’s local music engine.'}</p>`;
  }
  window.addEventListener('mousike:player-state', updatePanel);
  window.addEventListener('mousike:queue-changed', () => {
    updatePanel();
    if (state.page === 'queue') render();
  });

  let searchController = null;
  search = async function(q, page = 'search') {
    q = String(q || '').trim().slice(0, 120);
    if (!q) return;
    const listId = q.match(/[?&]list=([\w-]{10,90})/);
    if (listId) return openAlbum(listId[1], 'playlist');
    const urlId = q.match(/(?:youtu\.be\/|[?&]v=)([\w-]{11})/);
    if (urlId) {
      const info = await apiJSON('/api/inspect/' + urlId[1]).catch(() => null);
      if (info?.long_video) return openHybrid(urlId[1]);
    }
    searchController?.abort();
    searchController = new AbortController();
    const serial = ++state.requestId,
      signal = searchController.signal;
    state.page = page;
    state.query = q;
    state.busy = true;
    state.tracks = [];
    state.albums = [];
    state.searchArtists = [];
    state.searchDiscoveries = [];
    state.albumLoading = true;
    searchType = 'all';
    render();
    main.scrollTop = 0;
    window.AuroraSmart?.record('search', {}, {
      query: q
    });
    const payload = window.AuroraSmart?.payload({
      query: q,
      limit: 28
    }) || {
      query: q,
      limit: 28
    };
    const songs = apiJSON('/api/discovery/search', {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json'
      },
      body: JSON.stringify(payload),
      signal
    });
    const albums = apiJSON('/api/albums?q=' + encodeURIComponent(q) + '&limit=10', {
      signal
    });
    await Promise.allSettled([
      songs.then(d => {
        if (serial !== state.requestId) return;
        state.tracks = d.tracks || [];
        state.searchArtists = d.artists || [];
        state.searchPlan = d;
        state.searchDiscoveries = d.discoveries || [];
        state.busy = false;
        render();
        if (d.intent === 'mood') {
          apiJSON('/api/discovery/related', {
            method: 'POST',
            headers: {
              'Content-Type': 'application/json'
            },
            body: JSON.stringify({
              ...payload,
              existing: d.tracks.slice(0, 40)
            }),
            signal
          }).then(related => {
            if (serial === state.requestId) {
              state.searchDiscoveries = related.tracks || [];
              render();
            }
          }).catch(() => {});
        }
      }).catch(e => {
        if (e.name === 'AbortError' || serial !== state.requestId) return;
        state.busy = false;
        state.searchError = e.message;
        render();
        notify(e.message);
      }),
      albums.then(d => {
        if (serial !== state.requestId) return;
        state.albums = d.albums || [];
        state.albumLoading = false;
        render();
      }).catch(e => {
        if (e.name === 'AbortError' || serial !== state.requestId) return;
        state.albumLoading = false;
        state.catalogError = e.message;
        render();
      })
    ]);
  };

  function createPlaylist(name, items = [], description = '') {
    name = (name || 'New playlist').trim().slice(0, 90);
    if (!name) return null;
    const p = {
      id: 'pl_' + Date.now().toString(36) + Math.random().toString(36).slice(2, 7),
      title: name,
      description,
      tracks: items.slice(0, 250)
    };
    playlists.unshift(p);
    store('user-playlists-v3', playlists);
    refreshRail();
    return p
  }

  function openPlaylist(id) {
    if (!playlists.some(p => p.id === id)) return notify('Playlist not found');
    openPlaylistId = id;
    go('playlist')
  }

  function addToPlaylist(id, t) {
    const p = playlists.find(p => p.id === id);
    if (!p || !t) return;
    if (p.tracks.some(x => sameSong(x, t))) {
      notify('Already in ' + p.title);
      return
    }
    p.tracks.push(t);
    window.AuroraSmart?.record('playlist_add', t);
    store('user-playlists-v3', playlists);
    refreshRail();
    notify('Added to ' + p.title)
  }
  async function loadArtist(name) {
    if (!name) return;
    const serial = ++artistSerial;
    state.artistName = name;
    artistModel = {
      name,
      loading: true,
      tracks: [],
      albums: []
    };
    go('artist');
    setAmbient(name);
    const q = encodeURIComponent(name);
    const jobs = [fetch('/api/search?q=' + q + '%20songs%20official%20audio&limit=22').then(r => r.ok ? r.json() : {}).catch(() => ({})), fetch('/api/albums?q=' + q + '&limit=14').then(r => r.ok ? r.json() : {}).catch(() => ({})), fetch('/api/artist?name=' + q).then(r => r.ok ? r.json() : {}).catch(() => ({}))];
    const [songs, albums, artist] = await Promise.all(jobs);
    if (serial !== artistSerial) return;
    artistModel = {
      name: artist.name || name,
      country: artist.country || '',
      loading: false,
      tracks: (songs.tracks || []).filter(t => trackKind(t) !== 'album_video').slice(0, 22),
      albums: albums.albums || [],
      related: artist.related || [],
      description: artist.description || ''
    };
    if (state.page === 'artist') render()
  }

  function openLocalMix(id) {
    const sec = personalSections.find(x => x.id === id);
    if (!sec?.tracks?.length) return;
    state.collection = {
      album: {
        id: 'mix:' + id,
        title: sec.title,
        artist: 'Mousike',
        kind: 'mix',
        image: sec.tracks[0].image,
        tracks: sec.tracks.length
      },
      tracks: sec.tracks
    };
    state.albumLoading = false;
    state.hybridVideoId = null;
    go('album');
  }

  function closeMenu() {
    menuEl?.remove();
    menuEl = null;
    menuTrack = null
  }

  function popupMenu(t, anchor) {
    closeMenu();
    if (!t) return;
    menuTrack = t;
    const rect = anchor.getBoundingClientRect();
    const menu = document.createElement('div');
    menu.className = 'menu-pop';
    menu.setAttribute('role', 'menu');
    let buttons = `<button data-menu="next">${icon('play')} Play next</button><button data-menu="more-like">${icon('heart')} More like this</button><button data-menu="not-interested">${icon('minus')} Not interested</button><button data-menu="less-artist">${icon('user')} Less of this artist</button><button data-menu="discover">${icon('disc')} Discover new music</button><div class="menu-divider"></div><button data-menu="queue">${icon('list')} Add to queue</button><button data-menu="like">${icon('heart')} ${state.likes.some(x=>x.id===t.id)?'Remove from likes':'Save to liked songs'}</button><button data-menu="artist">${icon('user')} Go to artist</button><button data-menu="copy">${icon('disc')} Copy YouTube link</button><div class="menu-divider"></div>`;
    if (playlists.length) buttons += playlists.slice(0, 12).map(p => `<button data-menu="add-playlist" data-id="${escapeAttr(p.id)}">${icon('plus')} ${safe(p.title)}</button>`).join('');
    if (state.page === 'playlist') buttons += `<button data-menu="remove-playlist">${icon('minus')} Remove from this playlist</button>`;
    if (state.page === 'queue') buttons += `<button data-menu="remove-queue">${icon('minus')} Remove from queue</button>`;
    buttons += `<button data-menu="new-playlist">${icon('plus')} New playlist with this song</button>`;
    menu.innerHTML = buttons;
    document.body.append(menu);
    menu.style.top = Math.max(70, Math.min(innerHeight - menu.offsetHeight - 8, rect.top)) + 'px';
    menu.style.left = Math.max(6, Math.min(innerWidth - menu.offsetWidth - 10, rect.left - 190)) + 'px';
    menuEl = menu;
    menu.querySelectorAll('button').forEach(b => b.setAttribute('role', 'menuitem'));
    menu.querySelector('button')?.focus();
    menu.onkeydown = e => {
      const buttons = [...menu.querySelectorAll('button')];
      if (['ArrowDown', 'ArrowUp'].includes(e.key)) {
        e.preventDefault();
        buttons[(buttons.indexOf(document.activeElement) + (e.key === 'ArrowDown' ? 1 : -1) + buttons.length) % buttons.length]?.focus();
      }
    };
    menu.onclick = async e => {
      const b = e.target.closest('[data-menu]');
      if (!b) return;
      const cmd = b.dataset.menu;
      const song = menuTrack;
      closeMenu();
      if (['more-like', 'not-interested', 'less-artist', 'discover'].includes(cmd)) {
        window.AuroraSmart?.feedback(cmd, song);
        return;
      }
      if (cmd === 'next') {
        state.queue.splice(Math.max(0, state.index + 1), 0, song);
        MousikePlayback.queueChanged();
        notify('Playing next');
        return;
      }
      if (cmd === 'remove-queue') {
        const n = state.queue.findIndex((x, i) => i !== state.index && x.id === song.id);
        if (n >= 0) {
          state.queue.splice(n, 1);
          if (n < state.index) state.index--;
          MousikePlayback.queueChanged();
          render();
        }
        return;
      }
      if (cmd === 'remove-playlist') {
        const p = playlists.find(x => x.id === openPlaylistId);
        if (p) {
          p.tracks = p.tracks.filter(x => x.id !== song.id);
          store('user-playlists-v3', playlists);
          render();
        }
        return;
      }
      if (cmd === 'queue') {
        state.queue.push(song);
        MousikePlayback.queueChanged();
        notify('Added to your queue');
        updatePanel()
      };
      if (cmd === 'like') toggleLike(song);
      if (cmd === 'artist') loadArtist(cleanArtist(song));
      if (cmd === 'copy') {
        try {
          await navigator.clipboard.writeText(song.url || 'https://www.youtube.com/watch?v=' + song.id);
          notify('Song link copied')
        } catch {
          notify('Clipboard unavailable')
        }
      }
      if (cmd === 'add-playlist') addToPlaylist(b.dataset.id, song);
      if (cmd === 'new-playlist') {
        const name = await MousikeUI.prompt('Name your new playlist', song.title + ' mix');
        if (name) {
          const p = createPlaylist(name, [song]);
          openPlaylist(p.id)
        }
      }
    };
  }

  function sourcePlay(el) {
    const source = el.dataset.source || 'search',
      list = sourceList(source),
      i = +el.dataset.index;
    if (!list?.[i]) return notify('Track unavailable');
    playAt(i, list)
  }
  async function downloadSong(button) {
    const source = button.dataset.source || 'search';
    const t = sourceList(source)?.[Number(button.dataset.index)];
    if (!t) return notify('Song unavailable');
    if (trackKind(t) === 'album_video') {
      notify('Open this album and choose an individual song to download');
      return sourcePlay(button);
    }
    button.disabled = true;
    button.classList.add('preparing');
    try {
      let vid = t.segment ? t.source_video_id : t.id;
      if (t.unresolved || !vid || !/^[\w-]{11}$/.test(vid)) {
        const params = new URLSearchParams({
          title: t.title || '',
          artist: t.artist || '',
          duration: String(t.duration || 0)
        });
        const r = await fetch('/api/resolve?' + params);
        const data = await r.json();
        if (!r.ok || !data.track?.id) throw Error(data.detail || 'No recording matched');
        vid = data.track.id;
      }
      const params = new URLSearchParams({
        title: t.title || 'Mousike song'
      });
      if (t.segment && Number.isFinite(+t.start) && Number.isFinite(+t.end) && +t.end > +t.start) {
        params.set('start', String(t.start));
        params.set('end', String(t.end));
      }
      // Browser downloads straight from the local FastAPI endpoint. No large JS blobs.
      const href = '/api/download/' + encodeURIComponent(vid) + '?' + params.toString();
      notify(t.segment ? 'Preparing this song segment…' : 'Starting download…');
      const anchor = document.createElement('a');
      anchor.href = href;
      anchor.download = '';
      anchor.hidden = true;
      document.body.append(anchor);
      anchor.click();
      anchor.remove();
    } catch (err) {
      notify('Download unavailable: ' + err.message)
    } finally {
      button.disabled = false;
      button.classList.remove('preparing')
    }
  }
  async function contentClicked(e) {
    const b = e.target.closest('[data-action]');
    if (!b) return;
    const action = b.dataset.action;
    if (action === 'refresh-home') return window.AuroraSmart?.refresh();
    if (action === 'play-radio') return window.AuroraSmart?.playRadio();
    if (action === 'discover-new') return window.AuroraSmart?.discover();
    if (action === 'like-track') {
      e.stopPropagation();
      const track = sourceList(b.dataset.source || 'search')[+b.dataset.index];
      if (track) toggleLike(track);
      return;
    }
    if (action === 'play') return sourcePlay(b);
    if (action === 'download') {
      e.stopPropagation();
      return downloadSong(b)
    }
    if (action === 'menu') {
      e.stopPropagation();
      const list = sourceList(b.dataset.source || 'search');
      return popupMenu(list[+b.dataset.index], b)
    }
    if (action === 'menu-current') return popupMenu(state.current, b);
    if (action === 'search') {
      document.getElementById('query').value = b.dataset.query;
      search(b.dataset.query);
      main.scrollTop = 0;
      return
    }
    if (action === 'page') return go(b.dataset.page);
    if (action === 'onboarding') return location.assign('/onboarding.html');
    if (action === 'home-filter') {
      collectionMode = b.dataset.filter;
      return render()
    }
    if (action === 'search-type') {
      searchType = b.dataset.type;
      return render()
    }
    if (action === 'shortcut') {
      const k = b.dataset.quickKind;
      if (k === 'likes') return go('favorites');
      if (k === 'artist') return loadArtist(b.dataset.name);
      if (k === 'playlist') return openPlaylist(b.dataset.playlist);
      if (k === 'query') {
        document.getElementById('query').value = b.dataset.query;
        return search(b.dataset.query)
      }
      return
    }
    if (action === 'mix') return openLocalMix(b.dataset.mix);
    if (action === 'artist') return loadArtist(b.dataset.artist);
    if (action === 'album') return openAlbum(b.dataset.id, b.dataset.kind || 'album');
    if (action === 'back') return go('search');
    if (action === 'play-all' || action === 'shuffle-all') {
      const tracks = state.collection?.tracks || [];
      if (!tracks.length) return notify('No tracks found');
      if (action === 'shuffle-all') {
        state.shuffle = true;
        return playAt(Math.floor(Math.random() * tracks.length), tracks);
      }
      return playAt(0, tracks)
    }
    if (action === 'queue-all') {
      state.queue.push(...(state.collection?.tracks || []));
      MousikePlayback.queueChanged();
      updatePanel();
      return notify('Album tracks added to queue')
    }
    if (action === 'save-collection') {
      const a = state.collection?.album;
      const p = createPlaylist(a?.title || 'Saved album', state.collection?.tracks || [], 'Saved from ' + (a?.artist || 'Mousike'));
      if (p) notify('Saved playlist: ' + p.title);
      return
    }
    if (action === 'open-playlist') return openPlaylist(b.dataset.id);
    if (action === 'play-list' || action === 'shuffle-list') {
      const src = b.dataset.source;
      const list = sourceList(src);
      if (!list?.length) return notify('No tracks here yet');
      if (action === 'shuffle-list') state.shuffle = true;
      return playAt(action === 'shuffle-list' ? Math.floor(Math.random() * list.length) : 0, list)
    }
    if (action === 'like-current') return toggleLike(state.current);
    if (action === 'expand') return expanded(true);
    if (action === 'clear-queue') {
      state.queue = state.current ? [state.current] : [];
      state.index = state.current ? 0 : -1;
      MousikePlayback.queueChanged();
      updatePanel();
      render();
      return notify('Queue cleared')
    }
    if (action === 'follow-artist') {
      const name = b.dataset.artist;
      const idx = followedArtists.findIndex(x => x.toLowerCase() === name.toLowerCase());
      idx < 0 ? followedArtists.push(name) : followedArtists.splice(idx, 1);
      store('followed-artists-v3', followedArtists);
      window.AuroraSmart?.record(idx < 0 ? 'follow' : 'unfollow', {
        title: name,
        artist: name
      });
      render();
      return
    }
    if (action === 'create-playlist') {
      let name = await MousikeUI.prompt('Playlist name', 'My playlist');
      if (!name) return;
      return openPlaylist(createPlaylist(name).id)
    }
    if (action === 'rename-playlist') {
      let p = playlists.find(p => p.id === openPlaylistId);
      if (!p) return;
      const newName = await MousikeUI.prompt('Rename playlist', p.title);
      if (newName?.trim()) {
        p.title = newName.trim().slice(0, 90);
        store('user-playlists-v3', playlists);
        render()
      }
      return
    }
    if (action === 'delete-playlist') {
      const p = playlists.find(p => p.id === openPlaylistId);
      if (!p || !await MousikeUI.confirm('Delete playlist “' + p.title + '”?')) return;
      playlists = playlists.filter(x => x.id !== p.id);
      store('user-playlists-v3', playlists);
      go('library');
      return
    }
  }
  panelBody.onclick = contentClicked;
  document.getElementById('railLibrary').onclick = contentClicked;
  content.addEventListener('keydown', e => {
    if ((e.key === 'Enter' || e.key === ' ') && e.target.classList.contains('song-line')) {
      e.preventDefault();
      e.target.click()
    }
  });
  // The old hybrid mode switch and retry buttons are handled by its original capture listeners.
  // Keep original settings selectors, updated premium navigation, and audio processing intact.
  document.querySelectorAll('.rail-action[data-page]').forEach(b => b.onclick = () => go(b.dataset.page));
  document.getElementById('brandHome').onclick = () => go('home');
  document.getElementById('headerHome').onclick = () => go('home');
  document.getElementById('railCreate').onclick = async () => {
    const name = await MousikeUI.prompt('Name your playlist', 'New playlist');
    if (name) openPlaylist(createPlaylist(name).id)
  };
  document.getElementById('topStudio').onclick = () => window.MousikeSettings?.open('audio');
  document.getElementById('closeNowPanel').onclick = () => {
    showPanel = false;
    shell.classList.remove('panel-visible');
    if (innerWidth > 1060) shell.style.setProperty('--manual-panel', '0');
    shell.classList.add('panel-hidden')
  };
  // Studio remains a working shortcut; the expanded player and right sidebar are separate.
  let panelToggle = document.createElement('button');
  panelToggle.className = 'player-panel-toggle';
  panelToggle.title = 'Toggle Now Playing sidebar';
  panelToggle.setAttribute('aria-label', 'Toggle Now Playing sidebar');
  panelToggle.innerHTML = icon('panel');
  document.querySelector('.premium-player .right').prepend(panelToggle);
  panelToggle.onclick = () => {
    showPanel = !showPanel;
    shell.classList.toggle('panel-visible', showPanel);
    shell.classList.toggle('panel-hidden', !showPanel);
    panelToggle.classList.toggle('active', showPanel)
  };
  if (!showPanel) shell.classList.add('panel-hidden');
  // Styles can be rewritten by runtime theme preferences; keep the final chosen palette.
  const navMobile = document.createElement('nav');
  navMobile.className = 'mobile-nav';
  navMobile.innerHTML = `<button data-nav="home">${icon('home')}Home</button><button data-nav="search">${icon('search')}Search</button><button data-nav="library">${icon('library')}Library</button><button data-nav="queue">${icon('list')}Queue</button><button data-nav="settings">${icon('settings')}Settings</button>`;
  document.body.append(navMobile);
  navMobile.onclick = e => {
    const b = e.target.closest('[data-nav]');
    if (!b) return;
    if (b.dataset.nav === 'settings') settings(true);
    else go(b.dataset.nav)
  };
  window.addEventListener('resize', () => {
    if (innerWidth <= 760) {
      showPanel = false;
      shell.classList.remove('panel-visible');
      shell.classList.add('panel-hidden')
    } else if (innerWidth > 1060) {
      shell.classList.toggle('panel-visible', showPanel)
    } else shell.classList.toggle('panel-visible', showPanel)
  });
  const actualPremiumRender = render;
  render = function() {
    actualPremiumRender();
    navMobile.querySelectorAll('button').forEach(b => b.classList.toggle('active', b.dataset.nav === state.page))
  };
  const getBack = () => {
    closeMenu();
    if (state.page === 'album' || state.page === 'playlist' || state.page === 'artist') go(fromPage === 'album' ? 'home' : fromPage)
  };
  document.addEventListener('click', e => {
    if (menuEl && !menuEl.contains(e.target) && !e.target.closest('[data-action="menu"],[data-action="menu-current"]')) closeMenu()
  });
  document.addEventListener('keydown', e => {
    if (e.key === 'Escape') closeMenu();
    if ((e.ctrlKey || e.metaKey) && e.key.toLowerCase() === 'k') {
      e.preventDefault();
      document.getElementById('query').focus()
    }
  });
  window.addEventListener('popstate', () => {
    const p = decodeURIComponent(location.hash.slice(1));
    go(['home', 'search', 'library', 'favorites', 'history', 'queue'].includes(p) ? p : 'home', false);
  });
  setAmbient(state.current || tasteProfile?.artists?.[0] || 'mousike');
  render();
  window.addEventListener('storage', e => {
    if (e.key === 'aurora_user-playlists-v3') {
      playlists = load('user-playlists-v3', []);
      render();
    }
    if (e.key === 'aurora_history') {
      state.history = MousikeData.tracks(load('history'));
      if (state.page === 'history') render();
    }
    if (e.key === 'aurora_likes') {
      state.likes = MousikeData.tracks(load('likes'));
      updateNow();
      render();
    }
  });
  window.AuroraPremium = {
    go,
    loadArtist,
    createPlaylist,
    openPlaylist,
    get state() {
      return state
    },
    get playlists() {
      return playlists
    },
    get artist() {
      return artistModel
    },
    menu: popupMenu,
    sourceList
  };
})();
