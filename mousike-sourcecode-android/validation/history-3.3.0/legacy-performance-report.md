# Mousike 3.2.0 — connection, controls and layout repair

26 October 2026. Signed development APK for arm64 phones, Android 8.0 or newer.

## Install

Install `Mousike-3.2.0-arm64.apk` over **Mousike Fast**. Package `com.mousike.app.fast`, version code 320. Its signing certificate matches 3.1.1, so no uninstall or data clearing is required.

## Playback changes

The 3.1.1 screenshot showed a source-resolution timeout before audio started. The previous native request omitted the anonymous visitor-session step used by the upstream extractor. The nine-second fallback deadline could also kill legitimate cold initialization/extraction.

- Establish/cache a valid anonymous visitor session and send it with the player request. Reuse a visitor returned by the catalog when available. Include the request nonce and playback nonce. Use the web endpoint that returned an OK response in the live probe.
- Prefer the HLS route returned by that player response, with correct HLS parsing and audio-only track selection.
- Race only the selected song's routes. A quick native result avoids starting Python; a failed primary starts compatibility recovery automatically, with no second user tap. Compatibility starts after a short head start if needed and runs at background thread priority. Obsolete selections cancel both routes.
- Replace the premature nine-second kill with a 25-second compatibility budget and a 35-second resolution budget. The overall 45-second startup limit is a final failure bound, not an intended playback delay. Status remains visible while recovery is active.
- Keep play intent separate from the transient player state. Repeated taps during connection do not cancel/restart preparation. The loading play button is disabled while Next remains available.
- After an error, Next clears the old error, prepares the next item when the player is idle, and starts it automatically. If the queue has no next item, local taste ranking is used before a network recommendation lookup; Gemini does not gate that local choice.
- Allow one media-request retry. Keep remote byte caching and reject HTML responses instead of caching them as audio. Local files/assets bypass the network cache.

## Navigation, player and settings

- Put mini-player/navigation in the actual Scaffold bottom-bar slot, reserve its measured height and apply system navigation insets once. Page content no longer sits underneath the footer.
- Replace the fixed-height custom navigation row with Material 3 navigation items. Verify at 1.25× system font scale.
- Make expanded-player artwork height responsive, retain the complete cover with Fit, keep controls scrollable, and show unknown duration as a dash rather than a false zero.
- Display playback failures inline; remove the oversized error snackbar that covered the controls.
- Stop collecting every progress tick in the Settings root. Read saved-key availability off the UI thread once; debounce preference-driven recommendation recalculation.
- Add Settings → Support & Information → About us. It includes the Mousike brand/tagline, version, **Developed by Sujal Wagholikar**, feature details, privacy information and third-party credits.
- Add Copy playback diagnostics in Settings. It copies the current video ID and connection-stage history, with URLs redacted and no API key or visitor session included.

## Verification

**53/53 unit tests passed on the final build.** New cases verify visitor establishment before the player call, visitor reuse, request nonces, avoiding compatibility startup on a fast native success, automatic recovery after primary failure, loser cancellation and the overall resolution bound.

**Nine unique targeted Android checks passed:** an eight-test playback/layout suite in 110.453 seconds, followed by a final targeted Next-after-error check in 11.650 seconds after that repair. Tests cover cancellation on song replacement, source-error reporting, the engine startup guard (with an accelerated test timeout), HLS clip/seek/queue transition, local clipped audio, inline errors, full-player control bounds, About Us attribution, footer/content separation, and Next starting audio after a failed source without another Play tap. Layout checks used a portrait emulator and 1.25× font scale. The About Us screen was visually inspected.

Compilation, APK ZIP integrity and Android signature verification passed. The full legacy UI suite and lint were not repeated.

## Live YouTube probe and remaining limit

The official `EqkBRVukQmE` upload of Arctic Monkeys' “When The Sun Goes Down” was probed. With a real visitor session, the web player endpoint returned **OK**, the correct video ID, an HLS URL and 17 adaptive formats (8.37 seconds for the uncached visitor + player sequence in this environment). The Google APIs endpoint returned a login/bot check and was not chosen as the primary route.

The media-manifest request from this environment returned a 195-byte **HTML “Site Unavailable”** response instead of HLS. **Actual live audio delivery and startup time on the Moto G54 remain unverified.** The passing Android playback tests use controlled audio sources. This release is not a guarantee of subsecond online playback or access to a source restricted by YouTube. The new diagnostic panel records the connection stages so a remaining phone failure can be identified instead of inferred from a spinner.

## Artifact

APK SHA-256: `41be6042c5d079bfff03de61c9b6932279531cc1ea18e56aaf518327ffdfa370`

Signing certificate SHA-256: `7090ce85e42cea840a7b11dbaead24ea10e52cb1bf29bdc1fce89dd097260b82`

This is a development build. The complete source includes the public development signing key for reproducible personal updates. Use a separate private key for a production distribution.

## Primary references

- [NewPipe visitor-session and player request implementation](https://github.com/TeamNewPipe/NewPipeExtractor/blob/v0.26.5/extractor/src/main/java/org/schabi/newpipe/extractor/services/youtube/YoutubeStreamHelper.java)
- [NewPipe visitor-data helper](https://github.com/TeamNewPipe/NewPipeExtractor/blob/v0.26.5/extractor/src/main/java/org/schabi/newpipe/extractor/services/youtube/YoutubeParsingHelper.java)
- [Compose Scaffold content padding and bottom bars](https://developer.android.com/develop/ui/compose/components/scaffold)
- [yt-dlp external JavaScript requirements](https://github.com/yt-dlp/yt-dlp/wiki/EJS)
