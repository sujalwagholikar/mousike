# Performance scope — Mousiké 3.6.0

This is a presentation/customisation release. It does not claim a new playback-speed benchmark. Core transport, recovery, prefetch, cache and player source are retained from 3.5.1; all native runtime payloads and yt-dlp bytes are identical. Catalog prioritisation adds no network lookup to a tap. Bundled font changes do not perform font downloads. New settings use the existing StateFlow and apply locally.

The selected settings test confirms a quality change leaves current queue/play intent intact and applies the requested Media3 cap at the next item. Existing stability/watchdog tests also pass. See `TEST_REPORT.md` for the exact checks and device/network limits. The old 3.5.1 report is retained under `docs/` as historical evidence.
