# Primary references checked for this build

Checked for this delivery on 25 October 2026.

- Gemini model code: [`gemini-3.5-flash-lite`](https://ai.google.dev/gemini-api/docs/models/gemini-3.5-flash-lite). The model stays optional and editable in Settings.
- Gemini generation API: https://ai.google.dev/api/generate-content. Candidate IDs are validated locally before any ranked result is used.
- Android yt-dlp wrapper: https://github.com/yausername/youtubedl-android. Maven dependencies are pinned to 0.18.1 and provide the Android Python/QuickJS/FFmpeg integration.
- yt-dlp current release: https://github.com/yt-dlp/yt-dlp/releases/tag/2026.10.21. The Maven wrapper's older extractor resource is overridden by this upstream bundle in `app/src/main/res/raw/ytdlp`, including its bundled EJS scripts.
- YouTube JavaScript challenge runtime setup: https://github.com/yt-dlp/yt-dlp/wiki/EJS. A JavaScript engine alone is insufficient; compatible challenge solver scripts are also needed.
- Media3 background playback: https://developer.android.com/media/media3/session/background-playback. Player and MediaSession support belongs in a foreground media playback service.
- Media3 clipping: https://developer.android.com/reference/androidx/media3/common/MediaItem.ClippingConfiguration.Builder. Each virtual album song uses explicit source-relative boundaries with track-relative player controls.
- YouTube Music integration reference: https://github.com/sigma67/ytmusicapi and https://ytmusicapi.readthedocs.io/. This app uses its own Kotlin anonymous catalog adapter rather than a hosted Python server. The catalog API is unofficial and changeable.

No external catalog sign-in, developer Gemini key, Spotify credentials or hosted backend is bundled.
