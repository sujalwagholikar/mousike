# Support and troubleshooting

Maintained by **Sujal Wagholikar**. Public project: https://github.com/sujalwagholikar/mousike

## Choose the right route

| Request | Route |
| --- | --- |
| General question or reproducible ordinary bug | Repository issue/discussion if enabled |
| Credential exposure or vulnerability | Private process in SECURITY.md |
| Privacy/rights concern | Request an appropriate private channel; see PRIVACY.md or COPYRIGHT.md |
| Google API billing/account problem | The API provider's account support |

Public handles do not imply a staffed help desk or a promised response time. Never publish a real API key, personal backup, payment details or private logs.

## Diagnose step by step

1. Record the exact platform and installed version/commit. The Android download filename and source version may differ; inspect the actual app version.
2. Distinguish website demo behavior from native application behavior.
3. Test bundled offline sound-check audio where available. If it plays, basic output works; online source availability still needs checking.
4. Try another permitted public song and record the failure stage, elapsed time and whether playhead/audio actually advances.
5. Check quality choice, network state and the default audio device. Avoid repeatedly pressing Play while a current source is resolving.
6. For Windows visual problems, update the relevant driver or test Hardware acceleration off through the app's settings, then restart. Restore your preference if it does not help.
7. For website animation problems, test reduced motion and record browser/device versions. GSAP is embedded in the current index; keep the assets folder and its relative paths intact.
8. Export/back up local data before clearing storage or reinstalling. Use the tray's Quit command to stop the desktop app fully.

## Bug-report template

- Platform, OS version and browser/app version:
- Build filename, source commit and optional SHA-256:
- Steps using non-sensitive sample data:
- Expected behavior / actual behavior:
- Frequency and stage: search, resolution, buffering, playback, download or UI:
- Connection type and relevant settings:
- Relevant redacted error text:
- Checks already performed:

Keep a minimal reproduction. Do not upload entire caches, user profiles, exported libraries or diagnostic archives without inspecting and redacting them first.
