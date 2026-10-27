# Mousiké 3.6.0

This is the original Mousike 3.5.1 project updated for the requested final appearance release. The installed application is now named **Mousiké**. The package remains `com.mousike.app.fast` and the retained development signing key allows installation over the previous APK.

## Appearance and personalisation

- Coral accent and square artwork are applied once on upgrade. Later changes stay saved. Your profile, listening history, likes, playlists, downloads and optional encrypted API key remain in place.
- Seven accents; Midnight, AMOLED, Light, Warm and System themes; Soft, Flat and Outlined component treatments.
- Four quick looks: Signature, Studio, Afterhours and Editorial.
- Six bundled Google Fonts combinations using Manrope, Inter, Roboto, Outfit, DM Sans, Lora and Plus Jakarta Sans. Choices affect both Material typography and explicitly styled headings, reading text and controls.
- Classic, Immersive and Compact expanded players; three artwork sizes; artwork, solid or accent backgrounds; accent or white play button; optional context and quick actions. Hidden quick actions remain available in the overflow menu.
- Editorial, Gallery and Compact album openers. All preserve the canonical track sequence and show the full cover without cropping.
- Adjustable song spacing, song artwork and durations; floating or edge mini player, optional progress line and navigation labels. Existing density, text-size, home shelf, refresh-rate and reduced-motion controls remain available.
- About us keeps the bundled Sujal Wagholikar portrait and adds Instagram and GitHub logo links.

## Small selection changes

| Choice | Requested source policy |
| --- | --- |
| Super high | Highest available original/default audio track; no bitrate ceiling |
| High | Target up to 160 kbps, retaining the established AAC preference |
| Low | Target up to 64 kbps; choose the lowest available source when the target is unavailable |

The existing yt-dlp fallback uses matching audio format selectors and a `music.youtube.com` watch URL. The existing native fast path remains first. Standard Media3 audio bitrate constraints are applied at media-item transitions without restarting the current song. Source availability, original-track selection and compatibility/recovery fallbacks determine the actual stream; quality labels do not promise lossless or a fixed bitrate.

YouTube Music catalog recordings are preferred over general videos when the preference is enabled. The catalog parser retains the official music-video type through cache and backup serialization. Search and recommendation candidates are stably prioritised; a tapped song ID is never replaced with another search result, and album lists retain their original order. No extra network search is added to the playback path.

## Playback preservation

`AudioPlayerEngine`, `StreamingMediaSource`, `ResumingAudioDataSource`, `PlaybackService`, `StreamVerifier`, `AudioCache`, `AudioNetwork`, watchdog, connection work and timeout/recovery policies are unchanged from 3.5.1. Media3, yt-dlp, Python, QuickJS, FFmpeg and all declared dependency versions remain pinned to the same versions. Only the two selection expressions in the existing resolvers and the fallback watch URL are changed in the playback package.

See `validation/changes-360.json` for a source comparison and hashes, `TEST_REPORT.md` for this build's checks, and `docs/CUSTOMISATION.md` for a user guide. Historical validation files describe their original versions, not new device tests.

## Same-version correction: Mousiké and expanded Home

The displayed name is corrected to **Mousiké**. Version name remains **3.6.0** and version code **360**. No appearance preference, audio selection, playback implementation or dependency is changed by this correction.

Home now offers up to 60 personalised songs, 48 discovery songs and 96 unique albums across three shelves of 32. Real availability determines the counts. Album ordering follows the existing taste-ranked tracks, favourite artists, track membership and genre, with daily variety and an initial artist-diversity pass. Likes, listening and skips trigger the existing refresh hooks; album lists retain canonical track order when opened.

Additional YouTube Music song/album metadata is fetched in the background in batches of two, using up to eight taste-based queries and one continuation per query. A 15-minute cooldown and single-flight lock avoid repeated discovery jobs; successful pages are cached and remain usable when another query fails. This work never resolves or prepares audio, and Home/playback do not await it. Cached Home selections are re-ranked on activity even during the network cooldown.
