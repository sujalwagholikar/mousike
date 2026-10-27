# Architecture — Mousiké 3.6.0

## Current additions

`AppearanceStudio` and `ComponentStudio` edit the same persisted `Preferences` StateFlow. `MousikeTheme` supplies semantic fonts, palette and display scaling. Explicit `Manrope`, `Inter` and `Roboto` references are heading/body/control roles backed by the selected six-way font combination, so screens do not ignore the user's font choice. Covers share a shape helper and keep `ContentScale.Fit`.

`MusicPreference` stably orders search/recommendation candidates by catalog music type. `TrackCodec` retains that optional field and decodes older backups without it. Generic catalog parsing and album browsing preserve source order, and tapped IDs are never replaced.

`AudioQualityPolicy` isolates format/bitrate choices. `QualitySelectionBinding` is a standard Media3 listener attached by the existing ViewModel; it applies the current bitrate preference at item transitions and removes itself on ViewModel cleanup. No current-song prepare, stop or retry is introduced. The existing native resolver consumes one format-selection expression; yt-dlp consumes one format-selector expression and the music watch URL. Transport, verification, caching, timeouts, watchdog and MediaSession code are identical to 3.5.1.

## Existing system (retained)

`MainActivity` hosts Compose screens and onboarding. `MousikeViewModel` coordinates screen state and catalog operations. `AppSettings` exposes a preference StateFlow and encrypts the user-supplied Gemini key through Android Keystore.

`YouTubeMusicClient` sends anonymous WEB_REMIX catalog requests. Its parser distinguishes primary album/artist browse navigation from nested playback overlays, reads song duration from fixed columns, and follows browse continuations with an explicit bound. Search accepts public YouTube links as well as text.

`MusicRepository` normalizes catalog metadata, uses Room for user library/history/feedback, persists complete track definitions atomically, fetches matching lyrics and coordinates long-recording detection. It keeps signed audio URLs out of durable caches and backup files.

`AlbumDetector` is a pure evidence-based segment builder. A virtual song is identified by source video ID and start/end milliseconds. `Album` records a confidence/source note, and catalog matches are explicitly distinguished from verified source offsets.

`AudioPlayerEngine` owns ExoPlayer on the main thread. `StreamingMediaSource` maps `mousike://video/ID` to a validated current audio URL before selecting the HLS/progressive parser. The established native fast resolver remains first, with the existing bounded yt-dlp/Python/QuickJS fallback. Resolved URLs stay in memory until near expiry. The retained 3.5.1 continuous watchdog and bounded recovery policy handle stalled preparation and rebuffering, including one same-source soft rebuild before alternate/fresh selection. Media3 clipping makes seek and duration relative to the virtual track.

`PlaybackService` exposes a MediaSession in a foreground media playback service. The service records playback signals even when the activity is no longer visible. Audio focus, noisy-output handling and system controls belong to the player/session layer.

`MusicDownloadWorker` performs persistent unique WorkManager jobs, using source video IDs and section bounds. `DownloadStore` retains progress and successful files. Offline files are selected before any network resolution. Download requests wait for an unmetered connection by default.

`TasteRanker` builds local preference scores from positive and negative listening signals and separate artist, genre, language and mood maps. Token/character-ngram similarity combines long-term, short-term and current-song context. Diversity, novelty, recent repeats and canonical duplicates affect ranking. It is a content-based ranker, not a trained multilingual embedding model or a collaborative recommender.

`GeminiRecommender` is optional. It sends a bounded candidate list and preferences over HTTPS, validates returned IDs, reapplies adjacency diversity, rate limits requests and caches results. Local ranking appears before optional AI enrichment. No API key is compiled into the app.

Safety and resilience choices: no broad storage permission; app-private downloads; no plaintext key in backups/logs; no arbitrary model-selected playback URL; bounds validation; cancellable inspection UI; atomic metadata storage; network failures preserve available local data.
