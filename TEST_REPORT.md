# Mousike 3.0 — verification report

Date: 24 October 2026 (India time).
Environment: Python 3.12, Node.js 24, installed real FastAPI/httpx/yt-dlp, FFmpeg,
Playwright and headless Chromium. No missing-yt-dlp shim was used.

## Results

| Check | Result |
|---|---|
| Python/API/recommendation regressions | **44 passed** |
| Full browser integration checks | **20 passed** |
| Browser JavaScript/CSP errors in that suite | **0** |
| Python and all 13 shipped JavaScript modules | Parse successfully |
| Desktop/tablet/mobile layout checks | 1440, 1024, 768, 390 and 360 px; no horizontal overflow |
| Original supplied photographs and full SVG sources | Preserved; byte comparisons performed |

The backend suite retains and updates the original regression coverage. It checks
multilingual identity, cross-publisher duplicates, duration/version differences,
numbered song names, independent taste axes, artist floods, explicit negative
feedback, repeat protection, real-ID-only Gemini proposals, API failure fallback,
search interpretation, bounded caches, metadata single flight, request boundaries,
rate limiting, byte ranges, expired-source refresh/connection cleanup, timestamp
parsing, metadata-only album fallback, licensed LRC parsing and file routes.

FFmpeg chapter export was exercised using a generated local audio fixture. A
warning from the installed Starlette test client concerns its future HTTP client
migration; it did not affect the 44 passing tests.

The browser suite uses the actual HTML/CSS/JS, FastAPI routes and streaming proxy.
An isolated test server provides deterministic catalog/extraction responses and
generates a sine-wave WAV. It is never imported by production `main.py`. These
fixtures verify integration, not live provider availability or music relevance.

The 20 browser checks cover:

1. Home's backend-backed sections and artist diversity.
2. Playback through the actual FastAPI byte-stream proxy.
3. Continuous playback while navigating.
4. One Space press produces one toggle.
5. Same-track recovery and position restoration.
6. Full-screen progress, liking and queue controls.
7. Connected Web Audio EQ and a real FFT spectrum.
8. Seeking does not fabricate meaningful listen/completion signals.
9. Live settings and playlist-inclusive export.
10. Appearance, text-size and corner preferences across views.
11. Sidebar track menus.
12. Playlist creation, saving and explicit feedback persistence.
13. Semantic search, actual returned artist identities and collection navigation.
14. Same-source chapter transitions without replacing the media session.
15. Distinct shuffle visits and manual Next during repeat-one.
16. Two-deck crossfade overlap and cleanup.
17. Local synchronized lyrics import.
18. Responsive Home/Now Playing, including mobile controls.
19. Onboarding persistence and the transition on Home.
20. Absence of page errors and CSP violations during those operations.

## External verification still required

- **YouTube:** a live extraction attempt in this environment failed the network's
  certificate trust check. Real YouTube AAC/Opus playback, access/challenge handling,
  source authorization, remote seeking and latency must be tested on your network.
  TLS verification was not disabled to force a result.
- **MusicBrainz/Wikimedia:** live catalog access returned a non-JSON response through
  this environment's network path. Deterministic catalog/portrait tests pass; live
  matching and remote-image availability are not certified by them.
- **Gemini:** no real API key was supplied. Sanitization, real-candidate guards,
  failure fallback and mocked analysis/reranking were tested. A real connection
  test remains necessary for your chosen model, account and quota.
- **Neural multilingual embeddings:** the optional encoder integration and fallback
  are present. The large optional model was not downloaded or benchmarked here.
  The default concept/Unicode vectors were exercised without external APIs.
- **Other browsers/OS devices:** Chromium was tested, including responsive/touch
  layouts. Native Windows audio, Safari/iOS media policy and real mobile hardware
  require device testing. Separate-stream sample-accurate gapless is not promised.
- **Recommendation quality:** behavioral invariants were tested, not an online
  relevance benchmark or acoustic similarity evaluation. Actual quality depends
  on source metadata and the listener's accumulated feedback.

## Reproduce

```cmd
py -m pip install -r requirements-dev.txt
py -m pytest -q
npm install
npx playwright install chromium
py tests/run_browser.py
```

Browser test screenshots and JSON are generated in `test-results/`. They are not
production music data. Read `README.md` for running the ordinary app, optional
embeddings, source requirements, lyrics and provider/content rights.


## Artwork fit correction — 24 October 2026

Home, search, library and album cards now fit the complete source image inside
their square frame, including wide YouTube thumbnails and portrait artwork.
Image intrinsic dimensions cannot stretch the frame; hover does not zoom/crop.
Native CSS container queries choose two to six cards from each shelf's actual
width, recalculating automatically with window resizing, browser zoom, density
changes or panel resizing. No polling or additional API requests are needed.
The stylesheet URL is versioned so a cached previous stylesheet is refreshed.

Focused Chromium layout check passed at viewport widths 1920, 1440, 1024,
768, 390 and 360 pixels: image/frame bounds match, object-fit is contain,
hover transform is disabled and there is no document horizontal overflow.
Shrinking a shelf independently of the viewport from desktop width to 420px
changed its card count from five to two. Previous full-suite results above
were not rerun for this CSS-only correction.


## Music-first Home and personalization — 24 October 2026

Removed the Stay curious / Keep listening panel and the Semantic Radio status
note (including the malformed HTML text). Artists, recent listening and albums
now appear above mixes and recommendation shelves. Removed obsolete panel CSS.
Self-hosted Latin variable fonts: Manrope for headings, Inter for interface
text, Roboto for utility labels. Font licenses ship in assets/fonts. Other
scripts retain browser/system font fallback. No external font requests.
Dark green surfaces and restrained glass chrome support solid/balanced/deep
settings. Added saved typography, artwork-fit, card-size and four Home section
visibility controls under Personalization; existing taste/discovery options remain.

Focused Chromium check passed: removed panels absent; artists start at about
258 CSS pixels on desktop; all three local font faces load; settings affect
the live main window, persist through reload, and retain zero document overflow
at 1440/768/390/360 widths. Modified JavaScript syntax checks passed. Previous
full backend/browser suites were not rerun for this interface-only change.


## Time-aware experience, waveform and Flash-Lite — 24 October 2026

Verified Google's documented stable ID gemini-3.5-flash-lite and pinned the
transport to it, including when an older environment model alias remains.
Two offline transport tests passed: exact URL/key header, minimal thinking,
thought-safe JSON parsing, caching, fresh manual tests and safe quota errors.
No real Gemini credentials were provided; live connectivity, quota and project
access are not certified. Missing/unavailable Gemini still uses local discovery.

Focused Chromium experience check passed: nine local-hour greeting samples,
six single-tone and six dual-tone templates, light/dark split contrast and
persistence, real audio playback with supplied waveform branding, buffering
and pause transitions in both header and fullscreen player, and the complete
four-stage post-onboarding welcome. It checks document overflow at viewport
widths 1440, 768, 390 and 360. Zero page errors in the successful final check.
The welcome uses bounded timers; it never waits on an animation completion
promise that might stall in an inactive/slow-rendering browser.

The unchanged uploaded SVG is bundled alongside its optimized display
derivative. Original 48 traveling slices are retained; their repeatedly drawn
waveform is pre-rendered at 192px inside the SVG, with the display viewport
96px. This avoids duplicating 581 vector elements in every animated slice.
Python AST and modified JavaScript syntax checks passed. Previous full suites
were not rerun for this focused update; earlier live-service limitations remain.


## Album rows, likes, library and immediate selection — 24 October 2026

Track tables now adapt to their own container width. Album/download columns
collapse when necessary; very narrow rows also drop the number column while
retaining Like, duration and options. Song thumbnails fit complete artwork.
A functional like button appears immediately left of duration, uses canonical
song matching and saves through the existing liked-song workflow.

Fixed malformed library-radio markup that was visible as HTML text; its real
Play mix button remains connected. Static and animated brand display derivatives
remove the original white surround, with a larger header frame. Original source
logo assets remain bundled unchanged.

Selected track metadata/artwork now appears synchronously in the bottom player
before metadata extraction or streaming. Preparing audio status and a restrained
loading ring explain the wait; queue/session commit and meaningful play events
still happen only when playback starts. The same pending preview reaches the
fullscreen player. Cancel loading invalidates the old request; failures revert
the display to the actual current song. No claim is made of lower live YouTube
source-resolution latency.

Focused Chromium test passed: valid library markup; enlarged cropped mark;
likes without accidental playback; selected album song displayed while a real
stream request is held; cancellation without committing the song; actual audio
start and loading-state cleanup. At 1440/1024/768/390/360 viewport widths,
Like remains left of duration, controls remain in their row, artwork uses
contain, and there is no document horizontal overflow. Zero page errors.
Modified JavaScript syntax checks passed. Full older suites were not rerun.
Reproduce this focused check with python tests/run_browser.py track_fixes.cjs.


## Borderless logo correction — 24 October 2026

The remaining outline was intrinsic to the original logo frame: antialiased
pale pixels in the static raster and an opaque pale surround in the animated
SVG. Replaced both display frames with a matching solid rounded vector plate
and transparent exterior, retaining the supplied waveform and its animation.
Original source assets remain unchanged. All application logo references now
use versioned URLs so old outlines cannot persist through asset caching.

Rendered both display SVGs at 240px, visually inspected them, and checked
that there are zero near-white visible pixels and fully transparent corners.
Modified player/experience JavaScript syntax checks passed. This is an asset
correction; playback/backend suites were not rerun.


## Search frames and Made for You priority — 24 October 2026

Fixed the separate compact search-results layout: it was still using three
columns after track actions expanded, so options wrapped into a second row.
The compact results now use a proper responsive track container and five
visible columns. Their artwork and top-result artwork use contain without
shrinking the image frame. The featured card aligns to the top and stacks
above the song list when its actual container narrows.

Made for You is now the first Home music shelf, uses ranked recommendation
tracks through a dedicated playback source and appears only once. Recent
listening is a separate shelf below recommendations; its dedicated tab remains.
Artists appear in larger portrait-led cards with responsive spacing and real
artist-page links. Existing visibility controls remain honored.

Personalization requests now combine up to 500 retained listening/feedback
events with up to 200 unique saved-library signals from likes, playlists,
follows and older retained history. Types are balanced and duplicates of
existing events are skipped. This is bounded retained evidence, not a claim
of unlimited lifetime history. Learning-off continues to send explicit
feedback only. The same activity context feeds local and optional Gemini
ranking, and in-app Settings AI actions reuse that shared context.
Only cached recommendations are invalidated; user collections are retained.

Focused Chromium regression passed: Made for You is first and unique, does
not copy history into its recommendations, larger portraits render, library
signal types are included, and keyed/unkeyed payloads share the same activity.
At 1440/1024/820/768/390/360 widths, full search artwork remains inside frames,
row controls do not wrap or spill and the document has no horizontal overflow.
Zero page errors; modified JavaScript syntax checks passed. These tests use
explicit provider fixtures and a dummy key; live Gemini access is not verified.
Run python tests/run_browser.py search_home.cjs to reproduce the focused check.
