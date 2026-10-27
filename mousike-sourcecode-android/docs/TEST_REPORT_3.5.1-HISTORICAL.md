# Mousike 3.5.1 — changes and focused validation

## Delivered

- Signed arm64 development APK, version 3.5.1 (351), package `com.mousike.app.fast`. The signing key is retained, so install over Mousike Fast without uninstalling or clearing data.
- Latest uploaded developer portrait, bundled and displayed directly under About & support → About us, with **Sujal Wagholikar** credit.
- Native MediaSession notification with title, artist, artwork and queue transport controls. Android determines the system presentation, seek bar and output selector. A notification tap opens the expanded player.
- Playback recovery changes described in RELEASE_NOTES_3.5.1.md.

## Findings and fixes

1. **Confirmed upstream HLS failure mode:** Media3 1.6.1 can fail to propagate a playlist load error after it stops retrying. The upstream fix is included in 1.8.0; the app now uses 1.8.0 consistently for its four declared Media3 modules. The replacement source was checked for the corrected playlist-error bookkeeping.
2. **Application recovery weaknesses:** the old logic relied on callback-armed jobs and only watched buffered duration. Progressive audio can download its index while buffered duration stays at zero. The new continuously sampled watchdog observes byte and buffer progress, handles rebuffering after the first sound, and keeps a bounded deadline across retries.
3. **Next/back workaround:** a stalled prepared source now gets one automatic rebuild using its already resolved connection and cached bytes before an alternate/fresh route is tried. The queue and position are retained.
4. **Loading UI:** combined player state is authoritative, playing clears stale loading/error text, and pause remains available while waiting. Pausing keeps the prepared source for resume.
5. **Notification overwrite:** a plain foreground loading notification reused the media notification ID on every service start. That override is removed; MediaSessionService now owns its native MediaStyle notification lifecycle.

The upstream issue is relevant to the reported symptom; it does not prove that every failed stream on the user's phone had the same cause. No device logs were supplied for the current failure.

## Checks completed for the final build

**35 focused JVM/Robolectric tests passed; zero failures/errors.**

| Suite | Tests |
| --- | ---: |
| Buffering watchdog (index progress, first-sound rebuffer, rapid transitions, continuous deadline, pause, next, endless trickle) | 7 |
| Ranged transport continuation, timeout, EOF, seeking, response validation and bounded retry | 8 |
| Foreground selection/cancellation and single-tap fallback | 6 |
| Source validation, warmup promotion/cancellation and playback progress | 13 |
| Actual Media3 notification contains session token, title/artist and three compact controls | 1 |

- Final `assembleDebug` and selected unit-test tasks completed successfully with Media3 1.8.0.
- APK ZIP integrity and Android signature verified; signer matches the retained source keystore.
- APK package, version and arm64 ABI verified.
- The portrait extracted from the final APK matches all pixels of the uploaded image.
- Source comparison against 3.5.0 records 8 intentionally modified runtime files, 3 additions and 116 unchanged runtime files. The extractor, native resolver, byte-range data source, recommendation, downloads, settings storage and ViewModel source are retained.

## Limits

No emulator frontend suite or Moto G54/Android 15 end-to-end playback run was performed for this update. Live YouTube startup speed, notification presentation on that phone and the exact user-reported stream remain unverified. Network, CDN and source availability still affect startup; this release does not promise zero buffering or instantaneous playback for every stream. Earlier UI results in `validation/history-*` are historical, not new passes for 3.5.1. The long frontend test suite was not repeated.

## Research

- https://github.com/androidx/media/issues/2401
- https://github.com/androidx/media/commit/0eca87babbabfff3d09850fa0a1a3f51feb80179
- https://github.com/androidx/media/blob/release/RELEASENOTES.md (1.8.0 HLS fixes)
- https://developer.android.com/media/media3/exoplayer/listening-to-player-events
- https://developer.android.com/media/media3/session/background-playback
- https://developer.android.com/media/implement/surfaces/mobile

APK SHA-256: `032b32786574154ccfd7337ebdbe15f0d8d61a05daae410e77a0fc5e1ad577c7`
