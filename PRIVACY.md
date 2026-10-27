# Privacy notice

Project: **Mousiké — Sound In motion**  
Maintainer: **Sujal Wagholikar**  
Source review date: **27 October 2026**

This notice describes the supplied application/source and static showcase, not every historical build or a future hosted service. Review it whenever a new SDK, endpoint, sync service, advertising system or host is added. No regulatory certification or universal absence of third-party logging is claimed.

## Data and purpose

| Data | Why it is used | Typical location |
| --- | --- | --- |
| Chosen name, languages, artists, genres and appearance | Personalization and interface preferences | Device/browser local storage |
| Searches, likes, skips, completion history, playlists and queue | Library features and recommendation ranking | App's local database/state |
| Song metadata, artwork and lyrics | Display, discovery and playback context | App cache and provider responses |
| Downloaded audio | User-requested offline listening | Device storage |
| Optional Gemini API key | User-enabled recommendation enhancement | Separate encrypted app storage in the reviewed native implementations |
| JSON library export | User-requested backup and migration | User-selected file |

No sign-in is required by the supplied Mousiké interface. This does not make online playback anonymous to service providers.

## Network requests

Online catalog/search and media resolution contact YouTube/YouTube Music and media/image servers. Lyrics functionality may contact LRCLIB. Optional Gemini features send relevant taste/preferences and candidate song metadata to Google's API when enabled. Recipients necessarily receive ordinary connection information, such as IP address and request metadata. Their policies and retention apply independently.

The supplied showcase stores appearance choices locally and plays bundled demo audio. The updated index embeds GSAP/ScrollTrigger and uses local fonts/screenshots, so those particular assets need no CDN request. Download links may lead to GitHub Releases or another configured host, which may record access logs. Hosts, reverse proxies and administrators can have their own logging; inspect the actual deployment configuration before making a no-logging claim.

No advertising/analytics SDK was identified in the reviewed app/website paths. A dismissible feature spotlight is app promotion, not evidence of an advertising network. This statement is limited to reviewed code and is not a complete dependency audit.

## Optional AI and credentials

Local ranking works without Gemini. The user chooses whether to enable AI and supply a key/model available to their account. Windows source encrypts the key through Electron safeStorage; Android source uses Android Keystore-backed encryption. Encryption does not protect against every compromised-device scenario. API providers may apply usage limits and charges to the user's account.

Never include a real key in a bug report, screenshot, source commit or shared export. Library backups should exclude the key and downloaded audio; review any export before sharing it.

## Retention and controls

- Appearance, taste and library records stay until changed, cleared or removed by the user/app's storage management.
- Clear listening history and searches through the relevant settings where available; this need not remove likes or playlists.
- Disable AI and remove its key in Settings. Revoke the provider key as well if it may have leaked.
- Delete downloaded tracks through Downloads. An exported backup is a separate file and must be deleted separately.
- On Windows, installed and portable editions normally use `%APPDATA%\Mousike`. Quit from the tray before copying or deleting that folder. Uninstall may retain it. Back it up before intentional deletion.
- Android app-storage clearing/uninstallation can remove local records; separately saved/shared files or OS backups may remain.
- Clear the website's site data/local storage in the browser to reset its saved appearance choices.

Deleting local data does not delete copies retained by Google, GitHub, the host, an export recipient or the operating system. Ask the relevant provider about its controls.

## Contact and sensitive data

For a privacy question, request a private channel through https://github.com/sujalwagholikar . Do not publish private exports or identifiers in issues. The app is not designed to collect government IDs, health records or payment-card data; do not submit these as support evidence. No special child-directed service, age-verification program or parental-consent mechanism is asserted here.
