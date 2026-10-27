# Mousiké 3.6.0 — name correction and expanded Home

Version name **3.6.0**, version code **360**, package `com.mousike.app.fast` and update signing identity remain unchanged.

## Changed

- Displayed brand corrected from mosuiké to **Mousiké** throughout the app and launcher.
- Home offers up to **60 personalised songs**, **48 discovery songs**, and **96 unique albums in three shelves of 32**, as available from the real catalog.
- Album presentation follows the existing taste ranking, favourite artists, album track membership, genres and daily variety, with an initial artist-diversity pass. Listening, likes and skips refresh the selections using existing activity hooks.
- Additional song and album metadata is fetched in background batches of two with pagination bounds, cached successful pages, failure isolation, a single-flight lock and a 15-minute cooldown. No audio resolution or preparation is done by this metadata job.

## Preserved

All playback, performance/recovery, settings/quality, download and album-analysis source files are byte-identical to the preceding 3.6.0 build. Existing appearance values, font combinations, player designs, social links, global recommendation sizes and dependencies are unchanged. Only Home has the expanded song/album presentation. All eight arm64 native payloads and bundled yt-dlp resource match the preceding APK.

## Checks

- Final APK build successful.
- **4 focused Home feed tests pass**: bulk limits and deduplication, activity-driven album reordering, artist diversity with explicit taste, and sparse/offline catalogs.
- APK integrity, signature continuity, corrected display name and unchanged version verified.
- Runtime/settings source comparison against the preceding 3.6.0 source passed.

The preceding build's 32 focused checks remain historical evidence and were not repeated for this small correction. No physical-device playback test or live catalog-volume benchmark was performed. Counts are limits, not fabricated entries; the upstream catalog and available taste data determine the actual content.

SHA-256: `88b510f4e1a4200f95662213feda8232a4f0ce6e89463ecead3166bd4ebb3bb7`
