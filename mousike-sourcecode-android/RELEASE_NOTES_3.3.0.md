# Mousike 3.3.0 — connection and experience update

## Playback

The 3.2.1 frontend update retained the same 12 playback/performance Kotlin files as the earlier working 3.2.0 source. A phone-specific latency regression was not reproduced here. This release removes identifiable startup work rather than claiming a measured phone speedup.

The native player now chooses usable direct audio before an HLS master. It keeps the already-resolved HLS stream as an automatic alternative, then uses bounded yt-dlp compatibility recovery if necessary. Signed URLs requiring an unsolved n-transform are still rejected. Direct format cache keys include the representation ID to prevent M4A and WebM byte collisions. Selected connection work uses normal thread priority; disk metadata writes happen outside the tap-to-source path.

One upcoming/visible song can prepare its native connection after a short idle period. This does not download media or run Python. A selected song cancels speculative work and does not wait on a shared background future. Prepared results are reused from the in-memory expiring stream cache. Settings can disable this preparation. Temporary native failures now expire after a minute instead of forcing the compatibility route for the rest of the process. Artwork network concurrency is bounded separately.

Catalog HTTP calls are now cancellable. Editing a search cancels obsolete calls instead of leaving a blocking request active while another query waits.

## Daily Mix

Daily Mix cards now build personal track lists from the existing local taste ranking. They show a cancellable preparation screen, balance the selected mix and open an album/song-list view. Building a mix never issues a Play command or replaces the current queue. Known long recordings are excluded from the individual-song mix. Playback starts only when the listener selects Play or a song. The resulting mix can be saved through the existing album controls.

## Onboarding and search

The onboarding transition shows four stages: Analysing your preferences, Customising for you, Building your playlist, Here we go. Existing data is ranked locally; initial preference lookups use at most two concurrent catalog requests and cannot hold the visual transition indefinitely. The home transition fades smoothly and respects Reduce motion. Music catalog availability remains separate from completing personal setup; no fictional songs are inserted.

Typing immediately enters a search-loading state during debounce and HTTP work. Subtle skeleton rows replace the misleading empty-result flash. Actual empty filters show useful search guidance; connection errors have a retry action.

## Brand and developer

The supplied waveform design now fills a navy adaptive icon with no cream/white surround or outline. Launcher, round legacy icons and in-app marks use the filled artwork. The original uploaded SVG is preserved alongside the filled variant. About Us includes the supplied developer portrait, Sujal Wagholikar's name and the existing developer credit. Existing Manrope/Inter/Roboto typography and appearance controls remain.

## Display smoothness

Settings → Smoothness & Response offers Auto, 60 Hz, 90 Hz and 120 Hz only when the current-resolution display mode supports them. Auto leaves the platform in control. The app requests an actual supported mode and displays the current panel rate; Android or battery saving may choose another rate. Compose animations use system frame scheduling; audio progress does not waste work polling at 120 Hz. Reduce motion, artwork ambience and card density remain available. A 120 Hz device performance benchmark was not run in the emulator.

## Install

Install the arm64 APK over Mousike Fast. The application ID and signing certificate are preserved, with version code 330. Uninstalling is unnecessary.

## Primary references

- https://developer.android.com/media/optimize/performance/frame-rate
- https://source.android.com/docs/core/graphics/multiple-refresh-rate
- https://developer.android.com/media/media3/exoplayer/supported-formats
- https://developer.android.com/blog/posts/elevating-media-playback-introducing-preloading-with-media3-part-1

Exact validation results, APK hash and live-network limitations are recorded in TEST_REPORT.md.
