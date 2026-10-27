# mosuiké 3.6.0 — focused release validation

## Result

The arm64 development APK builds successfully and **32 focused JVM/Robolectric checks pass with zero failures or errors**. Package `com.mousike.app.fast`, version 3.6.0 (360), displayed name **mosuiké**. The signer matches 3.5.1, so this APK can update that installed app without uninstalling or clearing its data.

| Check suite | Tests |
| --- | ---: |
| Three quality tiers, constrained-source fallback, HLS availability, music preference, old backup compatibility and album order | 8 |
| Upgrade defaults, persistent customisation, appearance-only reset and next-song quality changes preserving queue/play intent | 4 |
| Existing source validation, native-format selection and connection-work regression checks | 13 |
| Existing buffering watchdog regression checks | 7 |

## Artifact verification

- Final `assembleDebug` and the four selected test suites completed successfully. The first dependency build also succeeded; final selected checks took 122.8 seconds.
- APK archive integrity, Android signature, installed label, version, package and arm64 ABI verified.
- All **21 protected Kotlin files** in the playback/performance/download/recommendation/album packages, excluding the two deliberately adjusted resolver-selection files, are identical to the 3.5.1 source. The protected set includes the core player, source lifecycle, byte-range transport, watchdog, verifier, network/cache, notification service and retry/concurrency policies.
- All **8 arm64 native runtime payloads**, bundled yt-dlp resource, developer portrait and declared dependency versions match the previous 3.5.1 APK/source. No runtime or Media3 upgrade was introduced.
- All **23 font files** are packaged. Original Manrope/Inter/Roboto weights are retained; four Google Fonts families and their complete OFL notices are added. Instagram and GitHub logo resources are packaged.
- Three-tier native format selection is exercised against representative source metadata. The real Media3 player settings test confirms a setting change does not replace the current song, clear the queue or turn off play intent; the bitrate constraint applies on the next transition.

## Scope and limits

No emulator visual suite, physical Moto G54 installation or live-network playback end-to-end run was performed for this appearance update. Successful builds, JVM checks and byte comparisons are not phone/network benchmarks. Quality is a requested source preference; source availability and compatibility fallbacks can yield a different bitrate. Existing downloads are not re-encoded. Upstream service availability can still affect streaming.

Historical reports and screenshots under `validation/history-*` and `docs/` are preserved as history, not claimed as new 3.6.0 device passes.

## APK checksum

SHA-256: `a6cc7951841065828ba1c69af5cf4c81f961c11b6d0c697d3c1b435e481a4424`

See `RELEASE_NOTES_3.6.0.md`, `docs/CUSTOMISATION.md` and `validation/changes-360.json` for the update scope, user guide and source comparison.
