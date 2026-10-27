# Third-party software

Mousike's source is licensed under GPL-3.0 to accommodate the bundled Android yt-dlp wrapper. This file identifies major dependencies and their upstream source/license locations. Keep the license notices and source references with redistributed builds.

| Component | Version | License / source |
| --- | --- | --- |
| youtubedl-android | 0.18.1 | GPL-3.0 — https://github.com/yausername/youtubedl-android/tree/0.18.1 |
| yt-dlp and bundled EJS | 2026.10.21 upstream Unix bundle | Unlicense / upstream component notices — https://github.com/yt-dlp/yt-dlp and https://github.com/yt-dlp/ejs |
| Python runtime | supplied by Android wrapper | Python Software Foundation license — https://github.com/python/cpython |
| QuickJS runtime | supplied by Android wrapper | MIT — https://bellard.org/quickjs/ |
| FFmpeg runtime and codecs | supplied by Android wrapper | FFmpeg's component-specific LGPL/GPL notices apply — https://ffmpeg.org/legal.html; wrapper source/build instructions: https://github.com/yausername/youtubedl-android/blob/master/BUILD_FFMPEG.md |
| LLVM libc++ shared runtime | Android NDK r27c | Apache-2.0 with LLVM exception — https://github.com/llvm/llvm-project/blob/llvmorg-18.1.8/libcxx/LICENSE.TXT; https://developer.android.com/ndk/downloads/older_releases |
| AndroidX, Compose, Media3, Room, WorkManager, Palette | pinned in app/build.gradle.kts | Apache-2.0 — https://cs.android.com/androidx/platform/frameworks/support |
| Kotlin and coroutines | pinned in build files | Apache-2.0 — https://github.com/JetBrains/kotlin and https://github.com/Kotlin/kotlinx.coroutines |
| OkHttp | 4.12.0 | Apache-2.0 — https://github.com/square/okhttp |
| Coil | 2.7.0 | Apache-2.0 — https://github.com/coil-kt/coil |
| Material icons | resolved by Compose BOM | Apache-2.0 — https://github.com/google/material-design-icons |

Original sound-check WAV files and vector covers are generated specifically for this project and are included under the project license. No commercial song recording is included in the APK.

The wrapper's Maven AARs contain the Python/FFmpeg/QuickJS executables. Their upstream build files and source locations are required when preparing a redistributable production release. This development delivery does not assert that the upstream FFmpeg binary configuration is suitable for every store's distribution requirements.

## Bundled Google Fonts
Manrope, Inter, Roboto, Outfit, DM Sans, Lora and Plus Jakarta Sans are distributed under the SIL Open Font License. Complete licences and copyright statements are bundled in `app/src/main/assets/font-licenses/` and `app/src/main/assets/fonts/`. Fonts were obtained from the official google/fonts repository.
