# Validation — Windows adaptation 3.6.0

Build date: 2026-10-27. Host: Linux x64, Node.js 24.22.1. Target: Windows x64.
This report distinguishes executed checks from required destination-PC checks.

## Executed

| Check | Result |
| --- | --- |
| Node core and transport suite | 25 tests passed; no failures |
| Quality selection and native candidate policy | Passed for three quality tiers, default publisher audio, OTF rejection and HLS availability |
| Ranking and bulk Home feed | Passed activity influence, feedback exclusions, diversity, deduplication and real-item capacity limits |
| Long recording metadata | Passed chapter clipping, description/manual timestamps and unresolved evidence handling |
| Watchdog | Passed byte progress, stale-wait reset, recovery and final timeout |
| Backup/import and preferences | Passed Android-shaped metadata, offsets, library persistence and key/signed-URL exclusions |
| Local HTTP media transport | Passed ranges, seeking, interrupted-read resume, mismatched range rejection, token restrictions and HLS URI rewriting |
| Browser renderer with real Node service | Passed startup, actual original WAV playback and advancing playhead, expanded player, pause/resume, next song, font/theme changes and album track list |
| Small laptop bounds | Passed 960×700 with no horizontal overflow |
| JavaScript renderer errors in browser flow | None |
| Windows runtime provenance | yt-dlp.exe checksum pinned; FFmpeg 9.0.2 Gyan 7z matches official SHA-256; extracted executables match pinned hashes |
| Android source preservation | Compared 100 original Kotlin files against the supplied source ZIP: zero differences |
| Windows package structure | x64 PE executable/runtime files, ASAR source/assets and installer archive inspected during packaging |

The browser UI flow uses three original offline instruments and real loopback
HTTP transport. It does not substitute for live YouTube or Windows audio tests.
A test queue initially selected its last item and expected another pre-existing
item after Next; the fixture was corrected to start from the first ranked item.
Pause/resume itself worked. No playback engine workaround was needed for that
fixture error.

## Not executed here

Native Windows installation/uninstallation, Windows audio-device output, GPU
compositor performance, 120 Hz measurement, sleep/resume, DPI/multi-monitor,
Bluetooth output, hardware media keys, tray persistence, Windows media panel,
DPAPI key round-trip and live YouTube playback on Windows were not run.
The environment is Linux; a native Electron window could not start through its
restricted display/socket environment. Browser renderer checks were used instead.
No device-level smoothness, instant-source or universal stability claim is made.

## Windows acceptance — short destination-PC run

1. Install the x64 build as a standard user; launch from Start and Desktop.
2. Complete onboarding, or import a phone JSON library. Verify the portrait,
   filled icon, coral defaults, square covers and six font combinations.
3. Play Offline sound check; pause/resume; seek; next/previous; open a mix.
4. Search a public music song and play; switch quickly between several tracks;
   confirm old requests cannot start an earlier selection or leave stale loading.
5. Open a real album and its song list. Test a chaptered long recording.
6. Change quality; download one permitted recording and play it offline.
7. Minimize, close to tray and use tray/media keys; inspect the OS media panel.
8. Restart, confirm queue/preferences/library; test optional key save/remove.
9. Check 100%, 125% and 150% display scaling and a 960×700 window.
10. Uninstall; verify only app program files are removed and user data remains.

`npm run test:desktop` and the included Windows GitHub Actions workflow automate
part of this matrix. That workflow is provided but has not been run for this
cross-built delivery. The build is unsigned and has no automatic update channel.

The installer build was changed to compile to a unique temporary filename and
rename only after success. This avoids a concurrent earlier packaging attempt
writing into a later build. Only the isolated, archive-verified output is delivered.
