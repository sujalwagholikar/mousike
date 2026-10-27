# Android 3.6.0 boundary and desktop adaptation

The supplied Android project `Mousike-GitHub-Ready` was read, not edited. Its
source ZIP and APK were not rebuilt or replaced for this task. The desktop app
retains the Mousiké brand, 3.6.0 version, portrait, filled logo, fonts, coral and
square-cover defaults. `reference/android-3.6.0/SHA256SUMS.txt` identifies copied
Kotlin comparison files byte-for-byte.

**There is no byte-identical cross-platform Android playback engine.** Kotlin
Android/Media3 depends on Android services and cannot execute in a Windows EXE.
The Windows edition uses translated pure rules plus desktop-specific adapters.
It preserves intent and data identifiers rather than claiming identical binary
behavior. The original source remains the authority for Android behavior.

| Android area | Windows counterpart | Boundary |
| --- | --- | --- |
| TasteRanker, HomeFeed | algorithms.cjs + ranker-worker.cjs | Translated ranking weights, preference/history inputs, bulk shelves and diversity; separate implementation |
| AudioQualityPolicy | algorithms.cjs + resolver.cjs | Same three quality labels and source-selection intent; available codecs differ |
| AlbumDetector, FastMetadata | algorithms.cjs + service.inspect | Evidence-based chapters/timestamps and segment offsets, no fabricated boundaries |
| YouTubeMusicClient, MusicPreference | catalog.cjs | WEB_REMIX catalog and music-first preference; desktop request implementation |
| NativeAudioResolver, YtDlpExtractor | resolver.cjs | Native VISIONOS route, publisher audio, standalone yt-dlp fallback; desktop timeouts/cancellation |
| BufferingWatchdog, StreamVerifier | algorithms.cjs + resolver.cjs | Progress-aware bounded recovery and media prefix validation |
| ResumingAudioDataSource | media-server.cjs | Desktop HTTP Range chunk/resume adapter with exact-offset checks |
| Media3 AudioPlayerEngine | renderer/player.js + Chromium/Hls.js | Replaced runtime, not executed or repackaged Android code |
| PlaybackService and Android media notification | Electron main/tray + Media Session | Windows media-key/system-panel hooks, not Android notification UI |
| Room and DownloadStore | store.cjs + JSON + downloads.cjs | Desktop persistence and bounded native download jobs |
| GeminiRecommender | service.ai | Optional next-song reranking with existing candidate IDs; local fallback |
| Compose screens | renderer views/CSS | Redesigned desktop layout with the existing brand and settings choices |
| Android backup codec | store.import/export | Track/segment metadata, likes, playlists, saved records and history; keys/audio excluded |

There is no automatic cloud synchronization. Android downloads, encrypted keys,
Android-only screen settings and Media3 disk cache are not transferred. A
Windows data folder can be exported/imported without modifying the phone.

Native Windows testing is still required before calling this an accepted stable
release on all target PCs. See VALIDATION.md for exactly what ran here.
