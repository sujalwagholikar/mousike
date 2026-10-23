# Mousike 3.0 — Sound In motion

A complete upgrade of the supplied Mousike app: FastAPI, the original hybrid
YouTube/MusicBrainz album engine, Semantic Radio, artist pages, playlists, Audio
Studio, optional Gemini and a persistent premium player. No Node build is needed
to run the app. Production pages use real provider results and your saved music.

## Start on Windows

1. Extract the ZIP. Open Command Prompt inside **Mousike**, next to `main.py`.
2. Install **Python 3.11 or newer**. Install **Node.js 22+** for yt-dlp's JavaScript
   challenges. Node is a media-extraction runtime here, not a frontend build step.
3. Install the declared Python dependencies:

   ```cmd
   py -m pip install -r requirements.txt
   ```

4. Start the app:

   ```cmd
   py main.py
   ```

   `start.bat` is also available after installing the dependencies.

5. Open **http://127.0.0.1:8000/**. On first launch, choose your artists, languages
   and styles. The welcome animation now appears on Home, and discovery continues
   loading there. Use **Ctrl + Shift + R** after replacing an older installation.

On macOS/Linux, use `python3 -m pip install -r requirements.txt`, then
`python3 main.py` or `./start.sh`. A virtual environment is recommended.

**Keep the same browser, hostname and port when upgrading.** Browser storage for
`localhost` is different from storage for `127.0.0.1`. Existing `aurora_` storage
keys are intentionally retained to preserve likes, playlists and preferences.
Only recommendation caches are invalidated during migration.

## What changed

- A consistent dark editorial design, responsive Home, real discovery shelves,
  artist heroes, albums, playlists, search, empty states and touch controls.
- Independent long-term, recent-session, artist, genre, mood and language taste
  axes; capped repetitive signals, recent-repeat penalties and artist rotation.
- Canonical song matching across publishers with duration/version safeguards,
  Unicode text preservation, optional ISRC/fingerprint evidence, and immediate
  **More Like This**, **Not Interested**, **Less of This Artist**, **Discover New
  Music** actions in track menus.
- Instant suggestions from known artist identities and cached real song results,
  typo-tolerant artist search, multilingual mood queries, personal ranking,
  songs/artists/albums/local-playlists grouping and related discoveries.
- One playback controller, metadata warming, optional media prebuffering,
  independent-source crossfade, guarded transitions and expired-source recovery.
  The player remains alive while browsing pages or opening Settings/Now Playing.
- Queue reordering/removal in Now Playing, stable shuffle, repeat modes, volume,
  browser media controls, shortcuts and playback-session restoration (paused).
- The existing eight-band EQ, presets, bass/vocals/treble, stereo balance,
  ambience and compression are connected to the shared Web Audio graph. A live
  spectrum appears in Now Playing. Optional normalization uses supplied ReplayGain
  or a gradual RMS estimate; it is not an ITU loudness measurement.
- Optional synchronized lyrics from permitted `.lrc` imports or installation
  sidecars. No lyrics scraping or invented lyrics.
- Complete JSON export/import, including playlists, follows and imported lyrics.
  API keys are excluded. Data stays in the browser; export before clearing it.

Home shelves are populated only when sources return enough suitable recordings.
Scarce results produce fewer tracks instead of fake music. Trending is ranked
within retrieved discovery candidates, not an official global music chart.

## Gemini is optional

Leave the key blank for the local recommendation engine. You can configure a
Gemini key in **Settings → Mousike Intelligence**. Session storage is the default
for a new key; **Remember key on this device** is optional. Older saved keys are
preserved. Browser storage is accessible to scripts/extensions with origin access.

For a server-held key, copy `.env.example` to `.env`, set `GEMINI_API_KEY` and
restart. The backend never returns the key to the browser. `.env` is not served.
`gemini-3.5-flash-lite` is the exact pinned Gemini model. Legacy `MOUSIKE_GEMINI_MODEL` aliases are ignored. No alternate Gemini model is selected automatically.

Gemini can interpret minimized music preferences, expand discovery searches and
softly rerank actual candidate IDs. It cannot bypass deduplication, negative
feedback or artist limits. Invalid keys, rate limits and API failures fall back to
local ranking. Bounded, short-lived RAM caches reduce repeated AI calls; keys and
personal profiles are not written to server files.

Refresh intervals are **10 minutes, 30 minutes, 1 hour, 6 hours or 1 day**, while
Mousike is open. Returning to a background tab checks whether a refresh is due.
Discovery intensity is adjustable in **Settings → Personalization**. Turning off
passive learning stops listening telemetry; explicit recommendation feedback still
applies. The weekly collection is pinned in your browser for the week unless
preferences or feedback change it.

## Multilingual embeddings

The default lightweight encoder combines shared multilingual music-concept
vectors with Unicode lexical/subword features. It needs no API or model download.
It represents **metadata**, not the audio waveform, and is not a trained neural
embedding model.

To enable the implemented neural encoder:

```cmd
py -m pip install -r requirements-embeddings.txt
```

Set `MOUSIKE_EMBEDDINGS=transformer` in `.env` and restart. The default model is
`sentence-transformers/paraphrase-multilingual-MiniLM-L12-v2`. Its initial download
needs Internet access and additional RAM/disk space. It warms in the background;
concept vectors remain available if loading fails. `/api/capabilities` reports
which encoder is actually active. Neural-model download/inference was not verified
in this build environment.

## Albums and playback

Long uploads use actual published chapters or description timestamps for virtual
cuts. When trustworthy cuts are absent, Auto mode looks for a MusicBrainz tracklist
and resolves separate recordings on selection. It never invents song boundaries.
Manual title/artist corrections and the original-upload option are preserved.

Adjacent chapters can continue on one media stream. Separate compressed streams
have browser/codec-dependent transition timing; sample-accurate gapless playback
is not guaranteed. Crossfade requires two playable buffered recordings and Web
Audio. It falls back to an ordinary transition when preparation is unavailable.
Prebuffering is optional and uses extra bandwidth; source metadata is warmed first.

**FFmpeg** is optional for exporting a permitted timestamp segment as MP3. Install
it and add it to `PATH`. Regular downloads retain source AAC/Opus without a costly
conversion. Downloads are initiated only by the listener. Availability, quality,
seeking and service restrictions depend on the original source.

Keyboard: **Space** play/pause; **/** or **Ctrl/Cmd+K** search; **Alt+Right/Left**
next/previous; **E** Audio Studio; **Escape** close overlays. Shortcuts exclude text
fields. Click the current artwork/title to open Now Playing on mobile.

## Rights and service access

Use only content you are authorized to stream or download and follow each
provider's current terms. This project does not grant media, photo or lyrics
rights, bypass DRM, or promise that arbitrary YouTube audio extraction is permitted.
For a public/commercial service, use a licensed catalog and provider-approved
playback. This build keeps the existing local discovery/streaming architecture;
it is not a commercial Spotify replacement or a multi-user hosting platform.
Artist-photo provenance is retained in `sources.json`; original supplied SVGs and
photographs are preserved. See `lyrics/README.md` for permitted lyric sidecars.

## Tests and documentation

```cmd
py -m pip install -r requirements-dev.txt
py -m pytest -q
```

Optional browser integration checks (Node.js required):

```cmd
npm install
npx playwright install chromium
py tests/run_browser.py
```

The browser runner starts a separate fixture server on port 8765. Catalog fixtures
and generated sine-wave audio exist only in tests; production `main.py` never
imports the fixture server. Results/screenshots are written to `test-results/`.

- `TEST_REPORT.md`: verified behaviors and external checks still required.
- `docs/ARCHITECTURE.md`: module ownership, recommendation flow and data migration.
- `ARTIST_IMAGES_HOW_TO_EDIT.txt`: portrait mapping and attribution.

If live playback fails, update the extractor with
`py -m pip install -U "yt-dlp[default]"`, verify Node/FFmpeg where needed, and try a
source that permits playback on your network. Certificate failures should be
fixed in the network's trust configuration; this app does not disable TLS checks.


### Home and appearance personalization
Open Settings → Personalization to choose visible Home sections, font pairing,
glass intensity, complete/cropped artwork and album card size. Existing themes,
spacing, discovery intensity and taste controls remain available. Manrope, Inter
and Roboto fonts are served locally with their SIL Open Font License files.


### Time, welcome and theme templates
Home greets you using your device's local time: Night owl, Early bird, morning,
afternoon and evening. It refreshes while open and when returning to the tab.
Settings → Personalization offers warm/minimal greeting copy and a welcome
animation toggle. The post-onboarding handoff reads your saved preferences,
starts or joins the actual feed request, customizes your space and reveals Home.
It is skippable, bounded and respects reduced motion; it does not fabricate
a saved playlist or hold the interface indefinitely for an external service.

Settings → Appearance groups twelve templates into Single tone and Dual tone.
Dual tone separates navigation/player surfaces from the music canvas; the
Forest / Ivory and Plum / Pearl templates combine a dark rail with a light
canvas and matching readable text. Existing settings and data are retained.

The uploaded waveform logo animates in the header and Now Playing during real
playback. Pause, buffering, seeking, errors and page visibility update the logo;
reduced motion uses the existing static mark. The unchanged supplied SVG ships
as assets/brand/uploaded-mousike-waveform.svg. The playback derivative preserves
the frame and traveling slices while using a small embedded waveform raster to
avoid redrawing hundreds of paths in each of 48 animated slices.

Gemini is pinned exclusively to gemini-3.5-flash-lite. Test connection can test
a newly pasted key without first saving it, bypasses old test caches/cooldown,
and reports authentication, permission, quota, model-access and network issues.
No real key was supplied for live verification. Google reference:
https://ai.google.dev/gemini-api/docs/models/gemini-3.5-flash-lite

Focused regression checks (after installing optional test dependencies):
python -m pytest -q tests/test_gemini_transport.py
python tests/run_browser.py experience.cjs


### Album rows and immediate player feedback
Track lists adapt to their container, with a saved Like control beside duration.
The library's Radio block renders as a real card. Cropped display logos remove
the white surround without editing the original supplied logo files. Selecting
a song updates player artwork/title immediately and shows Preparing audio while
the source resolves. Press the loading playback control to cancel. Real audio
startup still depends on the provider and your connection.


### Made for You and search layout
Made for You is the first Home music section, ranked from retained listening
activity, feedback and saved-library evidence with or without Gemini. Recently
Played remains separate, lower on Home and in its own tab. Enlarged artist
portrait cards open the real artist pages. Search's top result and compact
tracks now fit complete artwork and adjust to available container width.
Recommendation inputs stay bounded (500 events plus up to 200 library signals)
and respect the existing learning switch; user data and collections are retained.

## Vercel deployment — Premium Glass Edition

This ZIP includes a Vercel-compatible FastAPI deployment configuration, bundled Node.js runtime for yt-dlp EJS and bundled FFmpeg support. **See [`VERCEL_DEPLOY.md`](VERCEL_DEPLOY.md)** for the complete deployment walkthrough, required custom-domain setting and third-party audio service limitations. No frontend changes are needed.
