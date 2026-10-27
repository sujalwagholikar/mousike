# Mousike 3.2.1 — artwork and appearance

This update addresses empty artist/album covers and refines the existing native interface. Playback resolution, queue commands, download implementation, Gemini model and recommendation algorithms are unchanged.

## Artwork repair

YouTube Music returns thumbnails as objects inside arrays. The previous image traversal visited nested keyed objects, but never examined the thumbnail array elements themselves. Song cards often masked this bug by using a video-thumbnail fallback; artist and album cards had no equivalent and appeared black. The thumbnail reader now examines objects and arrays and selects the largest supported HTTPS thumbnail. Protocol-relative image URLs are normalized, and image hosts are checked by hostname.

Every artwork surface now provides a deterministic coloured identity with initials while loading or when an image is missing. Successful artwork replaces that identity. Existing related song artwork is used on the Home shelves when older cached entries have no cover. These identities are placeholders, not fabricated artist photos. Loading uses Coil's existing bounded image/cache pipeline; no per-card metadata lookup or background audio extraction was added. Images retain their complete aspect ratio.

## Typography and brand

The supplied SVG is preserved in assets. Its original mark is rendered for launcher, onboarding, Home and About; only the surrounding canvas margin is removed for the display variants. Static Manrope (400/600/700/800), Inter (400/500/600/700) and Roboto (400/500/700) font weights are bundled. No font download is needed at runtime. Manrope establishes headings, Inter provides reading text, and Roboto supplies controls and navigation. Cards have two-line titles and consistent line height. Font licences are included in assets/font-licenses.

## Appearance controls

Settings adds Small/Default/Large text size (composed with Android's accessibility font size), Compact/Comfortable/Spacious artwork-card density, Square/Rounded/Soft card covers, optional artwork ambience, independent Home shelf switches for mixes/artists/albums, and Reset appearance. Existing themes, accents, reduced motion, taste/profile and discovery settings remain. Appearance-only changes no longer start a recommendation rerank.

Install over Mousike Fast. The application ID and signing key are preserved; no uninstall or clearing data is required.

## Font sources

- https://github.com/google/fonts/tree/main/ofl/manrope
- https://github.com/google/fonts/tree/main/ofl/inter
- https://github.com/google/fonts/tree/main/ofl/roboto

Exact verification results and APK hashes are in TEST_REPORT.md.
