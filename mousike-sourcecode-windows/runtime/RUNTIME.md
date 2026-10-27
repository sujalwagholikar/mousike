# Windows x64 bundled runtimes

Run `npm run runtime` after `npm ci`. The script downloads pinned releases,
verifies their SHA-256 checksums, then places yt-dlp.exe, ffmpeg.exe,
ffprobe.exe and FFmpeg notices in `runtime/win-x64`. Packaging copies them
outside app.asar, where Windows can execute them.

The delivered installer and portable ZIP already contain these runtimes.
Source ZIPs intentionally omit these compiled upstream executables and npm
packages; lockfiles and the checked download script reconstruct them.

FFmpeg 9.0.2 essentials ZIP: SHA-256
`60f467265b1e312373dbcd92200c2618a74850f98d3d078e94296bb3fa2047ba`

The corresponding Gyan 7z archive was independently checked during this build:
`4705843ccaaf54257c16ad90f3e952ece33c17df964ecf7bfdbb0f49c7171077`.
Extracted ffmpeg.exe and ffprobe.exe hashes match the download script.

The JavaScript runtime used by yt-dlp is Electron's bundled Node.js, spawned
with ELECTRON_RUN_AS_NODE=1. Normal users do not install Java, Python or Node.
Runtimes are never downloaded or updated silently when the app starts.
