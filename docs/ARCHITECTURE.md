# Architecture and migration

The supplied project was inspected before edits: Python endpoints and hybrid album
logic, all HTML/JS/CSS entrypoints, tests, asset mappings, original SVGs and photo
provenance. The original backend/album/catalog routes and playlist structures were
extended rather than replaced with a different framework.

## Ownership

| Module | Responsibility |
|---|---|
| `main.py` | FastAPI routes; existing MusicBrainz, chapters, metadata fallback, yt-dlp, portraits and permitted downloads |
| `infrastructure.py` | Bounded public caches, per-query single flight, separate search/media workers |
| `music_semantics.py` | Canonical song identity, metadata relevance, diversity and repeat protection |
| `taste.py` | Decayed independent taste axes and saturation of repetitive signals |
| `embeddings.py` | Local multilingual concept vectors and optional Sentence Transformer encoder |
| `recommender.py` | Real candidate retrieval, local ranking, optional minimized Gemini analysis/reranking |
| `discovery.py` | Multilingual query interpretation, high-confidence artist correction, suggestions and search ranking |
| `security.py` | Host/origin checks, bounded request bodies, rate budgets and response policies |
| `lyrics.py` | Operator-supplied permitted LRC sidecars |
| `assets/js/data.js` | Versioned browser persistence, data export/import and storage-key migration |
| `assets/js/core.js` | Existing album/metadata UI flows and shared state |
| `assets/js/audio-studio.js` | Two decks into one actual EQ/effects/normalization graph |
| `assets/js/playback.js` | Sole media owner; queue, sessions, preloading, shuffle, crossfade, recovery |
| `assets/js/premium.js` | Existing views/playlists/artist navigation, refined Home and track menus |
| `assets/js/smart.js` | Listening signals, feedback, feed scheduling and weekly pinning |
| `assets/js/mousike.js` | Settings overlay, shared appearance, search suggestions, Home arrival and dialogs |
| `assets/js/player-host.js` / `player.js` | Same-origin state/command bridge and full-screen listening UI |
| `assets/js/onboarding-lite.js` / `settings.js` | Browser-owned preference selection and controls |
| `assets/js/portraits.js` / `artists.js` | Progressive public portrait hydration and original local photo mapping |

## Recommendations

1. Sanitize declared tastes and up to 700 bounded music events. Names, photos,
   browser IDs and API keys are excluded from Gemini prompts.
2. Build separate long-term/recent-session artist signals and genre/mood/language
   axes. A repeated action has diminishing weight. Actual played wall time, not
   seek position, produces listen/completion signals.
3. Rotate queries through languages, styles and at most two artist seeds. Real
   yt-dlp candidates are cached by public query, not by user identity.
4. Rank metadata vectors, selected tastes, recent context, novelty and feedback.
5. Filter duplicate uploads, rejected recordings and very recent repeats. Retain
   version and duration differences to avoid silently merging distinct songs.
6. Select an interleaved sequence with a first-screen artist cap, a full-feed cap,
   soft diversity penalties and optional Gemini ranking bonuses.
7. Split the curated sequence into disjoint visible shelves. The weekly shelf is
   pinned client-side and deduplicated against refreshed shelves. Mix cards are
   collections of actual tracks; opening a mix does not create a saved playlist.

Gemini receives a compact music summary and real IDs. It can supply descriptive
search expansion and soft ranking, never new IDs. Analysis has source/timestamp/
revision metadata. Cached local summaries do not prevent later Gemini analysis.

Canonical identity is evidence-based metadata matching, not an assertion that the
app heard the recordings. Optional matching ISRC/Chromaprint metadata is accepted;
`scripts/fingerprint.py` can fingerprint a local file using installed `fpcalc`.
Fingerprint matching is exact, not a full acoustic-identification service.

## Playback

The app owns two HTML audio decks. One shared Web Audio graph preserves sound
settings across transitions; only the active deck owns progress and event logic.
An iframe displays Now Playing and sends validated commands to the parent. Settings
uses another same-origin iframe. Neither view owns a competing audio session.

Identical public metadata requests share a flight. Search and source extraction use
separate two-worker pools, protecting playback from feed work. Source URLs are held
briefly in bounded RAM. A 401/403/404/410 refreshes extraction once while preserving
the requested byte range. Browser recovery retries the same source at its position.
Streaming responses close upstream connections on completion/disconnect.

Preloading first warms source metadata. Opt-in media prebuffering/crossfade prepares
one next deck, not a whole playlist. Adjacent chapter transitions reuse their source.
Normalization prefers supplied ReplayGain and otherwise uses slowly smoothed stream
RMS with limited gain and peak control. It is not acoustic enhancement or measured
integrated LUFS. FFT visualization is computed locally and never sent to Gemini.

## Browser data

The existing keys for profile, likes, history, follows, Studio and playlists remain
valid. `aurora_user-playlists-v3` is the actual playlist key. The previous settings
page's alternate `aurora_playlists-v3` is recovered only when that store is missing.
No unrelated key is deleted. The schema revision is `mousike-schema-version=3`.

New keys cover the paused playback session, weekly shelf, permitted imported lyrics
and home-album cache. Corrupt/missing values fall back without erasing good data.
Imports validate first and merge collections by ID. Exports contain actual playlists
and follows; session/API keys are omitted. No profile database is created on disk.
Gemini responses may be held temporarily in a bounded volatile cache indexed by
one-way request digests; keys and raw identifying profiles are not saved.

Legacy standalone hidden settings/player DOM, obsolete onboarding layouts, competing
playback handlers, inline scripts and stacked CSS layers were removed. Thirteen
readable JS modules and one shared design-token sheet now own their respective work.

## Service references

- [Gemini models](https://ai.google.dev/gemini-api/docs/models) and
  [content API](https://ai.google.dev/api/generate-content)
- [Sentence Transformer multilingual models](https://www.sbert.net/docs/sentence_transformer/pretrained_models.html)
- [MusicBrainz API](https://musicbrainz.org/doc/MusicBrainz_API) — calls are serialized
  and spaced at least 1.1 seconds apart, with shared public-response caching.
- [YouTube developer policies](https://developers.google.com/youtube/terms/developer-policies)
- [Web Audio API](https://developer.mozilla.org/en-US/docs/Web/API/Web_Audio_API)
