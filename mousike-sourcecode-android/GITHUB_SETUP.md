# Upload and build Mousiké on GitHub

This ZIP contains the original source for the latest **Mousiké 3.6.0**, including the corrected name and expanded personalised Home. Application code, assets, fonts, audio runtime resources, Gradle versions and app version are unchanged from the delivered APK. Only repository packaging, ignore rules, this guide and the GitHub build workflow differ.

## Upload the project

1. Extract the ZIP into a new folder.
2. Create an empty GitHub repository. Do not initialise another README or licence.
3. Open a terminal in the extracted folder containing `gradlew`, `app/` and `.github/`.
4. Run these commands, substituting the repository URL shown by GitHub:

```sh
git init
git add .
git commit -m "Add Mousike 3.6.0 Android source"
git branch -M main
git remote add origin YOUR_REPOSITORY_HTTPS_URL
git push -u origin main
```

Before pushing, `git ls-files .github signing` should show the workflow and `signing/mousike-development.keystore`. The ignore rules explicitly retain this public development key because debug builds use it. Do not omit `.github/`, `gradle/` or `gradlew` when uploading manually.

## Get the APK

Open the repository's **Actions** tab → **Build Mousike APK** → open the latest successful run → download **Mousike-3.6.0-APKs** under Artifacts. Extract it and install `app-arm64-v8a-debug.apk` on the Moto G54. The other split is for x86_64 environments. GitHub may require you to enable Actions first.

The workflow builds APKs on pushes to main/master, pull requests and manual runs. It does not automatically publish a GitHub Release or run the longer test suite. Build status depends on the GitHub runner and dependency/network availability; this workflow has not been executed on GitHub during packaging. The underlying APK project was already built and its focused checks are documented in `TEST_REPORT.md`.

## Local Android Studio build

Open this extracted directory in Android Studio. Use JDK 17, Android SDK 35 and Build Tools 35.0.0. Run `./gradlew :app:assembleDebug` (Windows: `.\gradlew.bat :app:assembleDebug`). A first build downloads pinned dependencies. App output is under `app/build/outputs/apk/debug/`.

## What remains unchanged

- Name **Mousiké**, version **3.6.0**, code **360**, package `com.mousike.app.fast`.
- Playback engine, quality options, customisation and expanded Home.
- Development signing identity for updates over the delivered APK.

The included development key is public and is not a private production signing key. Keep a separate production key outside a public repository if you later configure release distribution. No personal Gemini API key is included. Third-party dependencies are fetched by Gradle; their entire upstream source trees are not vendored here. Keep the GPL and third-party/font notices with redistributed source.

See `README.md`, `RELEASE_NOTES_3.6.0.md`, `docs/CUSTOMISATION.md`, `docs/ARCHITECTURE.md` and `TEST_REPORT.md` for the full app documentation.
