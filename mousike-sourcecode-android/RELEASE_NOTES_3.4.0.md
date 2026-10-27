# Mousike 3.4.0 — stream stability

## Defects found in the previous implementation

1. Extraction success was treated as playback success. Native and compatibility routes could win on a returned URL even when the media host returned HTML, a denied address or unrecognised bytes.
2. All audio formats were eligible for progressive playback, including explicitly segmented OTF formats. Highest bitrate alone could choose another audio track or a less suitable container. Cache identity omitted audio-track and DRC variants.
3. Native and compatibility closures wrote shared stream state before the route race selected its winner. Background preparation could also publish after a selection changed.
4. Tapping an in-flight preparation cancelled it and issued a new request instead of using work already underway.
5. Recovery largely depended on transport errors and a long startup timeout. There was no representation-specific buffering timer or detector for a playing clock that stopped advancing. The minimum steady-state buffer was only three seconds.

These are code-level defects and bottlenecks. The user's individual phone failures were not captured in device logs, so they are not presented as a proven diagnosis of every reported song.

## Changes

- Validate actual bounded media responses, not just player JSON. Check status, MIME and initial container/playlist bytes. Race up to three distinct candidates with a short preferred-source head start. Bad media responses cannot win the resolver race.
- Prefer default-track AAC/MP4 audio, retain HLS/other supported alternatives, and exclude known OTF/segmented-only progressive formats. Byte-cache identity includes format, audio-track ID and DRC variant.
- Share the media connection pool between validation and ExoPlayer to reuse DNS/TLS/HTTP connections. Validation is a small additional range request on cold connections; prepared and recently checked cached connections avoid repeating it. This is a stability tradeoff, not a claim that validation is free.
- Publish only the selected result. Promoted preparation does not publish independently, and a generation guard prevents older speculative work overwriting a newer selection's cache.
- Use a cancellable, reference-counted connection-work pool. A selected song promotes matching in-flight work. A cancelled subscriber cannot cancel another active subscriber. Obsolete speculative work is cancelled and all selected waits have deadlines.
- Reject recently failed addresses temporarily, revalidate older cached addresses, and do not immediately retry an address already known to be bad. Alternate/fresh/compatibility recovery preserves queue, track and relative playback position, with a maximum of three recovery attempts per selection.
- Detect unresolved buffering after source preparation (7.5 seconds) and a frozen playing clock. The overall startup/rebuffer episode is capped at 30 seconds. A source that never provides usable media ends with a clear error rather than an endless spinner. These deadlines are failure limits, not intentional playback delays.
- Increase steady-state audio buffering to 15–60 seconds while keeping the initial playback threshold at 300 ms. Rebuffer threshold is 1.5 seconds. Stop repeatedly retrying the same failed load before choosing recovery.
- Replace the old connecting label with **Connecting**.

## Onboarding

The preparation sequence now has an orbiting filled brand mark, a larger animated stage headline, smooth milestone/progress transitions and a fade/scale/slide into Home. Rotation is applied to a graphics layer rather than recomposing the full page on every frame. The layout scrolls on smaller/larger-text displays and respects Reduce motion. Daily Mix uses the same refined preparation surface.

## Preserved

Developer photograph and credit, filled adaptive icon, bundled typography, personalization, Daily Mix song-list behaviour without autoplay, optional Gemini, downloads and supported display refresh-rate settings remain. Install over Mousike Fast; the application ID and signing certificate are retained.

## Primary references

- https://developer.android.com/media/media3/exoplayer/troubleshooting
- https://github.com/TeamNewPipe/NewPipeExtractor/blob/v0.26.5/extractor/src/main/java/org/schabi/newpipe/extractor/services/youtube/extractors/YoutubeStreamExtractor.java
- https://github.com/androidx/media/blob/release/libraries/exoplayer/src/main/java/androidx/media3/exoplayer/upstream/DefaultLoadErrorHandlingPolicy.java

See TEST_REPORT.md for the exact validation scope and online-delivery limitations.
