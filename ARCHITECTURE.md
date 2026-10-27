# Architecture and working overview

**Mousiké · Sound In motion — Sujal Wagholikar**

## Three separate surfaces

| Surface | Implementation | Main responsibility |
| --- | --- | --- |
| Showcase | HTML/CSS/JavaScript with embedded GSAP/ScrollTrigger | Presentation, screenshots, local demo player and release downloads |
| Android | Kotlin, Jetpack Compose, Media3, Room and platform background components | Native music UI, playback, local library and Android integration |
| Windows | Electron main/preload, renderer, Node services, Chromium/Hls.js and ranking worker | Desktop UI, Windows playback adapter, native tools, tray and media-session hooks |

The website is not a hosted music API. The Windows edition translates catalog, recommendation, quality and album rules; it does not execute Android's Media3 engine. Keeping the Android project unchanged does not imply identical Windows binary behavior.

## Playback flow

1. A user selects a real catalog record or an imported/library track.
2. The player records playback intent and invalidates obsolete work when a newer track is selected.
3. Resolution obtains and validates media candidates, using the platform's native route and bundled extraction fallback as implemented.
4. Quality policy selects available formats without claiming lossless conversion.
5. The platform player starts ready audio, updates progress and observes buffering/network progress.
6. Bounded recovery, alternate/fresh resolution or an explicit error handles a stalled/unavailable source.
7. Completed downloaded files can be selected before an online resolution.

For Windows, a tokenized loopback media server handles local/remote delivery and range/HLS adaptation. Ranking runs separately from the renderer. IPC exposes a restricted application interface; the renderer is not given general Node access.

## Recommendation and albums

Local taste ranking uses preferences, metadata, likes, listening completion/skips and feedback, with diversity/exploration rules. Optional Gemini reranks existing candidates when enabled and available; it does not supply an embedded Google key or guarantee a playable record.

Daily mixes create collections and open their lists without starting music automatically. A long recording can become timed segments only when reliable chapters, description timestamps or explicit manual timing supply evidence. Unresolved boundaries should remain identified as unresolved rather than fabricated.

## Persistence and trust

Android uses its local platform storage and encrypted key handling; Windows stores library/preferences/download state under its app-data profile and the optional key separately in encrypted storage. Exported JSON is a separate user-managed copy; downloads and credentials are not automatically migrated.

Untrusted boundaries include network responses, metadata strings, image URLs, lyrics, AI output, source URLs, import files, subprocess output and local HTTP requests. Validate before executing, rendering, writing paths or forwarding requests. See SECURITY.md and PRIVACY.md for the operating policies, and each platform's detailed architecture for implementation-level paths.
