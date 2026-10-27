MOUSIKÉ 3.6.0 — WINDOWS DESKTOP
Sound In motion · Developed by Sujal Wagholikar

START
Installer: run Mousike-3.6.0-Windows-x64-Setup.exe and open Mousiké.
Portable: extract the entire ZIP, then run Mousike.exe inside its folder.
Keep its resources, DLLs and supporting files together with the executable.

REQUIREMENTS
64-bit Windows 10 or Windows 11. This build is x64, not 32-bit or ARM64.
An internet connection is needed for online music, artwork and optional AI.
The installer includes Electron/Chromium, Node.js, yt-dlp and FFmpeg.
No Android emulator, Java, Python or separate Node.js installation is needed.
The distribution is unsigned; no trusted publisher certificate is included.

FIRST OPEN
Choose a name, languages, sounds and optional favourite artists.
Search for music, open albums, build a daily mix, or import your library.
Daily mixes open a song list; they do not start playback automatically.
Settings > Library & privacy > Import reads Mousiké Android JSON backups.
Copy the backup from your phone to your PC before selecting it.
Audio files and API keys are not transferred by a JSON library backup.

PLAYER AND WINDOWS CONTROLS
Space: play/pause (when not typing or focusing a button).
Ctrl+K: search. Ctrl+Right: next. Ctrl+Left: previous. Esc: collapse.
Use the tray menu for playback controls and Quit.
Close-to-tray keeps music running. Settings > Windows & motion changes it.
Chromium Media Session supplies media-key and Windows media-panel hooks.
Their display depends on Windows, driver and OS settings.
Audio quality: Super high, High or Low, constrained by available sources.
These choose sources; they do not turn a low-quality source into lossless.

MAKE IT YOURS
Settings groups appearance, component layouts, listening taste, audio,
Windows & motion, optional AI, library/privacy and About.
Coral and square full-fit covers are the defaults.
Six font combinations and three expanded-player/album layouts are included.
Hardware acceleration and Reduce motion are available for different PCs.
Animation follows your monitor; a 120 Hz display does not force all PCs to 120 FPS.

OPTIONAL AI
Local recommendations work without a key. If you enable Gemini, add your own
key and a model ID available to your account. Windows encrypts the saved key
through Electron safeStorage/DPAPI. Exported libraries never include that key.
The key and ranking requests go to Google only when you use AI features.

OFFLINE SOUND CHECK
Settings > About & support > Offline sound check adds three original short
instrumentals, so you can verify local audio output without YouTube.
Downloaded audio is saved on this computer and is used before online sources.

DATA AND REMOVAL
Preferences, library and downloads are under %APPDATA%\Mousike.
The portable build also uses this location; it is portable to launch, not a
USB-isolated data profile. MOUSIKE_DATA_DIR can override it for advanced use.
Uninstall removes installed program files but retains your personal app data.
Installer and portable editions share this library when used on one account.

VALIDATION
Core and media-transport tests and browser-based renderer/audio checks passed.
Native Windows installation, secure storage, tray and OS media-panel checks
still require a Windows PC. See docs/VALIDATION.md in the source package.
Online service availability, network conditions and source changes affect
stream readiness; uninterrupted or instant playback is not guaranteed.

ANDROID CORE
The Android APK and source remain unchanged. This is a desktop adaptation:
its catalog, ranking, quality and timestamp rules are translated from 3.6.0.
Chromium/Hls.js replaces Android's Media3 playback runtime on Windows.

CONTACT
GitHub: https://github.com/sujalwagholikar
Instagram: https://www.instagram.com/sujalwagholikar/
Not affiliated with Spotify or YouTube. Music/artwork belong to their owners.
See LICENSE and THIRD_PARTY_NOTICES.md for licenses and upstream source links.
