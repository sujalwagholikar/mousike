# Mousiké for Windows — third-party notices

Mousiké 3.6.0 is distributed with its GPL-3.0 license in `LICENSE`. The project
contains an adaptation of the supplied Mousiké Android 3.6.0 source. Original
Android comparison files are preserved under `reference/android-3.6.0` in the
source ZIP. This notice does not relicense upstream components.

| Component | Version / license | Source and notices |
| --- | --- | --- |
| Electron | 45.0.0 / MIT plus bundled notices | https://github.com/electron/electron/tree/v45.0.0 ; the installed folder includes `LICENSE.electron.txt` and `LICENSES.chromium.html` |
| Chromium, Node.js and bundled libraries | As embedded in Electron | Electron's `LICENSES.chromium.html`, https://github.com/nodejs/node/blob/main/LICENSE and https://github.com/electron/electron/blob/v45.0.0/LICENSE |
| yt-dlp standalone Windows build | 2026.10.21 / Unlicense for the project; bundled dependency licenses also apply | https://github.com/yt-dlp/yt-dlp/tree/2026.10.21 ; https://github.com/yt-dlp/yt-dlp/blob/2026.10.21/THIRD_PARTY_LICENSES |
| FFmpeg + ffprobe, Gyan essentials | 9.0.2 / GPLv3 static build | `resources/runtime/LICENSE`, `resources/runtime/FFmpeg-README.txt`; https://www.gyan.dev/ffmpeg/builds/ ; exact FFmpeg source https://github.com/FFmpeg/FFmpeg/commit/946fcce07b ; build distribution https://github.com/GyanD/codexffmpeg/releases/tag/9.0.2 |
| Hls.js | 1.7.3 / Apache-2.0 | https://github.com/video-dev/hls.js/tree/v1.7.3 ; bundled renderer retains legal comments |
| Lucide | 1.61.0 / ISC | https://github.com/lucide-icons/lucide ; license copied to `assets/third-party-licenses/lucide-LICENSE` |
| Manrope, Inter, Roboto, Outfit, DM Sans, Lora, Plus Jakarta Sans | Bundled Google Fonts / see per-family license | `assets/font-licenses` and `assets/font-licenses-extra` retain the supplied font licenses; https://github.com/google/fonts |
| esbuild / electron-builder / resedit / Playwright | Development tools only | Exact versions in package-lock.json; MIT licenses in their upstream repositories / Apache-2.0 for Playwright |
| NSIS | Installer compiler and runtime | https://nsis.sourceforge.io/License ; https://github.com/kichik/nsis ; NSIS notices copied to assets/third-party-licenses |

The developer portrait and filled waveform logo are user-supplied Mousiké
assets. The three short offline sound-check WAV files were supplied with the
Android source and are bundled solely as original demo instrumentals.
Remote music, artist images, covers and lyrics are fetched on request and are
not part of the app's source licensing. Download only material you may save.

No Spotify SDK, Spotify catalog, Spotify login, advertising SDK, paid audio
entitlement, Google API key or hosted user database is included.

For redistribution, retain this file, all runtime licenses, font licenses and
Electron/Chromium notices, and provide the applicable corresponding source
and build instructions for GPL components. The source package contains all
Mousiké-specific application and packaging code. Upstream projects' complete
source and build configurations are linked above; no authorship over them
is claimed.
