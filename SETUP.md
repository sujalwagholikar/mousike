# Setup guide — website, Android and Windows

Owner/maintainer: **Sujal Wagholikar**. These instructions refer to the complete Mousiké project bundle; this documentation-only ZIP does not contain that application.

## 1. Identify the project root

The root contains index.html, assets/, downloads/, mousike-sourcecode-android/ and mousike-sourcecode-windows/. Keep directory names and filename capitalization intact. Replace a downloaded index file only after renaming it to index.html. Keep a backup of the prior page.

## 2. Run the showcase

1. Open a terminal in the root.
2. If Python is installed, run `python -m http.server 8000`.
3. Open http://localhost:8000/ . The simple server is for local preview, not hardened public hosting.
4. Check local fonts, all screenshot images, demo audio and responsive navigation.
5. Confirm the hero downloads Android, the device download section follows the hero and Windows buttons point to the actual EXE or its release URL.
6. Test normal and reduced motion. The current page embeds GSAP and ScrollTrigger; asset images/fonts still require the accompanying assets folder.

The preview player uses demo audio. It does not provide the installed app's full streaming service.

## 3. Build Android

1. Install Android Studio with the SDK/build tools required by the supplied Gradle project.
2. Open mousike-sourcecode-android/, the directory containing settings.gradle.kts and gradlew.bat.
3. Select the project's documented JDK; the supplied instructions specify JDK 17. Do not replace the wrapper with a newer-Java-compiled wrapper to work around unrelated errors.
4. Allow Gradle dependency resolution, then run `.\gradlew.bat :app:assembleDebug` on Windows.
5. Find the ARM64 split under app/build/outputs/apk/debug/ and verify its actual version, package ID and signing identity.
6. Test on a compatible Android 8+ device. A successful build is not a device playback test.

The included development signing key is not a private production identity. Read signing/README.md before preparing a production release.

## 4. Build Windows

1. Install Node.js 24 and npm for development.
2. In mousike-sourcecode-windows/, run `npm ci`, then `npm run runtime`.
3. The runtime script restores pinned Windows yt-dlp/FFmpeg executables and verifies their checksums.
4. Run `npm test`, `npm run bundle` and `npm start`.
5. To build an installer, install NSIS 3. In PowerShell set `$env:MAKENSIS = 'C:\Program Files (x86)\NSIS\makensis.exe'`, then run `npm run dist:win`.
6. Check the resulting installer/archive and test it on a Windows 10/11 x64 PC. End users of the bundled installer do not need a separate Node.js, Java, Python or emulator installation.

## 5. Transfer a library

Export an Android JSON backup, copy it to the PC and import through Windows Settings → Library & privacy. Confirm playlists and timed segments after import. Re-enter optional keys and re-download audio; these are not transferred by the JSON metadata backup. There is no automatic cross-device cloud synchronization in the supplied implementation.
