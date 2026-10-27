# Mousike 3.0.0 — Test report

Delivery date: 25 October 2026. Package: `com.mousike.app`. This is a signed development build for direct installation.

## Result

- **Build passed**: phone APK, emulator APK and instrumentation APK produced.
- **29/29 unit and persistence tests passed**, with no failures or skipped tests.
- **Five unique Android device flows passed** on an Android 9 / API 28 x86_64 emulator with 4 KB pages and software acceleration.
- **Android lint passed with zero errors and 90 warnings**. Warnings are retained in `docs/lint-results.txt`; this is not a warning-free production certification.
- Phone APK signature verification passed using APK Signature Scheme v2. Archive CRC verification passed.
- The packaged phone APK contains Python, QuickJS, FFmpeg, the required C++ shared library, and the fresh yt-dlp/EJS bundle.

## Android flows exercised

| Flow | Result |
| --- | --- |
| Onboarding → search → real native audio decode → like → expanded player → pause/resume → queue next/reorder → background progress | Passed using original bundled sound-check audio |
| Album card → complete track list → Play Album → theme/accent persistence after activity recreation | Passed on targeted recheck |
| Virtual chapter playback and track-relative seek/duration | Passed; a 10-second clip of the original 30-second recording exposed a 10-second player timeline |
| WorkManager download → completed file → offline playback selected from a file URI | Passed using original bundled audio; no network was required |
| Bundled QuickJS expression, FFmpeg process startup/version and Python/yt-dlp CLI startup/version | Passed on the emulator |

The final five-test run passed four flows. The album test initially tapped a title while it was beneath the floating player. Its test was corrected to scroll the album above the player and wait for the screen transition; that flow then passed. The final source contains that corrected test. Logs of the suite and targeted recheck are included; the report does not claim a single final five-test run with zero failures.

Earlier device checks exposed a network constraint on local demo downloads and an incomplete library path in the FFmpeg test. Local downloads now use no network constraint, and the runtime test uses the same Python/FFmpeg library search paths as the app. The required C++ runtime is included in the APK.

## Unit and persistence coverage

- 13 album detector tests: threshold behavior, chapter boundaries, description timestamps, hour formats, numbered rows, title-before-time, explicit ranges, overlap clamping, invalid and zero-length bounds, missing-evidence fallback, clip metadata serialization and release-match agreement.
- 10 recommendation tests: current-track exclusion, blocked songs/artists, canonical duplicates, artist diversity, recent repeats, early skips, stable ranking and candidate-only results.
- 4 catalog parser tests: album overlay vs primary browse navigation, fixed-column durations, empty renderer handling and parsing a real captured public music-catalog response.
- 2 Android persistence tests: cold catalog load with chapter offsets and liked chapter/playlist backup restoration.

## Live network checks and limits

- YouTube Music catalog request returned **HTTP 200 with 32 music renderers**.
- The fresh yt-dlp bundle resolved a real song returned by that catalog to a public HTTPS audio URL (format 140; source duration 251 seconds).
- Fetching the resulting media URL from this environment returned an HTML **Site Unavailable** page instead of audio. **Actual live YouTube CDN playback, network song downloads and network chapter exports were therefore not fully verified end to end here.** Those need a check on a device/network that can reach the media CDN.
- Gemini model code was verified from Google's documentation as `gemini-3.5-flash-lite`. No user key was supplied, so live key/model access and paid generation requests were not tested. The app works with AI disabled.
- Background audio and notifications were exercised on API 28. Android 13+ notification permission behavior, Android 14+ service restrictions, 16 KB devices, OEM battery management and a physical Moto G54 were not separately tested.
- No acoustic fingerprinting or transcription model is included. Missing exact boundaries remain unresolved unless publisher timestamps/chapters, a catalog release match, or a user-supplied track list provides evidence.

## Visual review

The actual emulator interface was inspected. Narrow-screen bottom navigation was changed to equal-width tabs with single-line labels; the floating player and status icons were checked after that fix. Artwork uses Fit and preserves its full aspect ratio. The included home preview is the explicitly labeled original sound-check catalog, not a screenshot of verified live music streaming.

## Reproduce

```sh
./gradlew :app:testDebugUnitTest
./gradlew :app:connectedDebugAndroidTest
./gradlew :app:lintDebug
```

Use an Android 8.0+ device or emulator. Instrumentation tests launch the debug-only offline fixture and use original bundled audio. A first source build needs internet for Gradle dependencies.

Phone APK SHA-256:

`041b68cea3971417a58c4d668ee2b0555ea4ba21df1434484fed003644b7d8f3`
