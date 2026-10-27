> 3.5.1: playback recovery and Media3 HLS error handling updated; system media controls and the latest developer portrait added. See RELEASE_NOTES_3.5.1.md and TEST_REPORT.md.

# Mousike Native — Sound In motion

An Android music app in Kotlin and Jetpack Compose. This version replaces the earlier embedded YouTube player with a native Media3 audio player and an on-device yt-dlp runtime.

## Install the APK

1. Download **Mousike-3.5.1-arm64.apk** on your Android phone.
2. Open it from Downloads. Allow that browser or file manager to install unknown apps if Android asks.
3. Tap Install, then Open. Android 8.0 or newer is required. The arm64 APK is intended for modern 64-bit phones, including the Moto G54.
4. Choose languages, genres and artists, or skip onboarding. No account or API key is required.
5. Search for a song, artist, album, or paste a YouTube link. Search and streaming require internet.
6. Open Settings to change themes, audio quality, Play Next, downloads and optional Gemini support.

This is a signed development APK for direct installation, not a Play Store production release. The debug package is `com.mousike.app.fast`, labeled **Mousike Fast**, and installs alongside the previous app because its signing key was not available. Keep the previous app installed, export likes/playlists through Settings, then import that JSON in Mousike Fast. No uninstall is required. The included development signing key keeps future personal debug builds update-compatible; use your own private key for a distributed release.

## Frontend update — 3.5.0

Settings now opens an organised overview with Personalisation, Appearance, Playback & audio, Downloads, Display & motion, AI recommendations, Library & privacy, and About & support. Existing controls and actions remain available inside these sections.

The expanded player has cover transitions, cover-derived ambience, a responsive title/artist hierarchy, a smooth seek bar, like feedback and labelled Lyrics/Queue/Download shortcuts. Playlist, equalizer, sleep timer and recommendation feedback actions are in its options menu. Artwork remains fitted.

Home includes a cancellable floating Mousike feature spotlight, automatically capped at one appearance per device-local calendar day. It waits on Home and is suppressed during album inspection or themed mix building. It has no advertising SDK, tracking request or player side effect. Settings → About & support → Discover Mousike can preview it explicitly.

Eight daily collections include the original Daily Mix 1/2 plus English Mix, Bollywood Punch, Indie Afterhours, Soft Focus, On Repeat and Discovery Dose. The six extra collections present existing activity-ranked recommendations through metadata filters; the recommendation engine and optional Gemini service are unchanged. They are not a new pure-generative AI system. Themed mixes need matching songs in the loaded catalog; empty collections explain how to grow them through Search. Building opens an album/song list and never starts music.

The 3.5.0 frontend release preserved the 3.4.1 playback backend. The current 3.5.1 release deliberately updates playback recovery, state reporting, MediaSession notifications and Media3 HLS handling to address the newly reported stuck-buffering issue. Extraction, ranged transport, recommendation, download and settings logic are retained. See `RELEASE_NOTES_3.5.1.md` and `TEST_REPORT.md` for this release’s changes and validation.

Install over **Mousike Fast**, without uninstalling or clearing data. This remains a signed development build for direct installation.

## Included

- Native Compose home, search, discovery, library, album, artist, playlist, queue, lyrics, settings and expanded player screens.
- Mini player, background media service, system media notification, lock-screen/headset controls, audio focus and unplug-to-pause.
- Playback speed, shuffle, repeat, sleep timer and device equalizer controls.
- Song likes, following artists, saved albums, playlists, feedback, JSON backup and restore.
- Persistent offline downloads with Wi-Fi-only preference, progress, cancel, retry and delete.
- On-device Python, yt-dlp 2026.10.21, QuickJS, EJS scripts and FFmpeg are packaged by the Android runtime dependencies. The C++ runtime required by FFmpeg is included in this source tree.
- Midnight, AMOLED, Light and System themes; four accent choices; onboarding and user taste settings.
- Artwork uses **Fit** throughout, preserving the complete image. The expanded player and album page derive ambient color from actual artwork.
- Local content-based recommendations using metadata tokens and character n-grams, long-term and recent listening context, likes, completion, skips, language/genre/mood/artist affinities, duplicate suppression, exploration, recent-repeat penalties and artist diversity.
- Optional Gemini candidate reranking, disabled by default. The default verified model ID is `gemini-3.5-flash-lite`. Settings allows another model ID and tests model access using your key. API keys are encrypted with Android Keystore and excluded from backups.
- Original bundled sound-check instrumentals for testing playback without internet. These are explicitly labeled demos and do not substitute for failed live catalog results.

## Long recordings and albums

A **click on a video longer than eight minutes opens album inspection**. Eight minutes is a routing heuristic; it does not prove a video contains multiple songs.

The resolver tries:

1. Source chapters, with start/end validation and overlap clamping.
2. Publisher description timestamps, including `mm:ss`, `h:mm:ss`, numbered rows, title-before-time and explicit ranges.
3. A plausible YouTube Music release match, requiring title overlap and total-duration agreement. These songs play their own sources, and the UI states that they are not verified offsets in the original video.
4. An unresolved album screen with full-recording playback and an editor for a timestamped track list.

Verified chapter tracks retain a source video ID plus clip start and end. Media3 clips playback to those bounds and exposes a track-relative seek bar. The final song ends at the known recording duration. Likes, playlists, saved metadata and backups retain the offsets.

**The app does not invent exact song boundaries or titles when evidence is missing.** It does not perform acoustic fingerprint recognition, source separation, cloud transcription, or a full audio-model analysis of an arbitrary untimestamped recording. Some long single songs and concert recordings will be routed to inspection; use Play full recording in that case.

## Optional Gemini setup

1. Create your own API key at https://aistudio.google.com/apikey.
2. In Settings → Optional Gemini boost, enter the key and model ID, then Save.
3. Tap Test connection, then enable AI boost.

When enabled, selected candidate song titles, artists, genre/language metadata and your chosen taste preferences are sent to Google. The key is sent only to the Google Generative Language endpoint. Invalid output IDs are discarded. Network, quota and model errors leave local ranking active. There is no bundled developer API key and no requirement to enable AI. Provider API quotas and charges are separate from this app.

## Build from source

Requirements: Android Studio, JDK 17, Android SDK Platform 35 and Build Tools 35.0.0. A first build downloads dependencies from Google Maven and Maven Central.

Open this folder in Android Studio and let Gradle sync. Choose the `app` configuration, select your device and press Run.

Windows:

```bat
gradlew.bat :app:assembleDebug
```

macOS/Linux:

```sh
./gradlew :app:assembleDebug
```

Outputs are under `app/build/outputs/apk/debug/`. ABI splits produce an arm64 phone APK and an x86_64 emulator APK. The APK contains runtime assets, but browsing YouTube Music and obtaining streams still need internet. No Python server, Node server, Termux, browser player or external runtime installation is required on the phone.

For a production release, create your own signing key and signing configuration. Keep that key private. The delivered APK uses development signing.

## Test

```sh
./gradlew :app:testDebugUnitTest
./gradlew :app:connectedDebugAndroidTest
./gradlew :app:lintDebug
```

See **TEST_REPORT.md** for the actual checks completed for this delivery, including unverified paths. Tests use original bundled audio for repeatability. A debug launch with `--ez offline_fixture true` uses a labeled deterministic catalog for emulator checks. Normal launches use the live service.

## Limits

- This is an independent music client, not Spotify, and does not have Spotify's collaborative user dataset, licensed catalog, Connect service or commercial feature parity.
- YouTube Music catalog endpoints are unofficial and may change. yt-dlp extraction can be rejected by YouTube, source restrictions or network policy. Restricted or unavailable content is surfaced as an error; there is no sign-in bypass.
- Downloads are for content you have permission to save. DRM-protected and paid-source access is not implemented.
- Synced lyrics are fetched only when a matching provider result exists. Missing lyrics are not fabricated.
- Equalizer availability depends on the device's audio effects implementation.
- This native runtime version has upstream reports about 16 KB page-size compatibility. This delivery's emulator validation uses a 4 KB Android 9 (API 28) environment; 16 KB devices are not certified.
- Gemini paid/live requests require a user key and cannot be fully verified without one.

License: GPL-3.0. See LICENSE and THIRD_PARTY_NOTICES.md.
