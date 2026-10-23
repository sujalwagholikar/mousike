# Mousike 3.0 — Premium Glass UI

This is a presentation-only update inspired by the current dark, artwork-led layout conventions of major music streaming desktop players. Mousike keeps its own name, branding, original artwork-fit behavior, and the exact tagline **Sound In motion**.

## Improvements
- Floating, translucent and softly outlined top navigation, side library, Now Playing panel and persistent playback dock.
- More considered typography, contrast, search focus treatment, active navigation, chip states, hover feedback and keyboard-focus affordances.
- Artwork-led recommendations, editorial shelves, artist portrait gallery, custom mixes, track lists and collection pages with subtle interaction polish.
- Matching visual treatment in onboarding, settings and the expanded player.
- Responsive positioning for 360px phones through ultrawide screens, reduced-motion support, reduced-transparency support and respect for the existing “Glass off” setting.

## Scope / safeguards
- **No changes** to `main.py`, recommendation engines, streaming/playback APIs, `assets/js/`, model providers, database/localStorage identifiers, user preferences or backend dependencies.
- Existing `object-fit: contain` artwork handling is preserved to avoid cutting off covers.
- Existing buttons, IDs, `data-action` hooks, keyboard shortcuts, nav routes and DOM behavior are preserved.
- CSS is additive. The new files `assets/css/glass-premium.css` and `assets/css/glass-pages.css` load after the original styles. Delete the two new stylesheet references in the HTML files to return to the previous visuals.

## Launch
Use `start.bat` on Windows or the launch command from the original README. Clear your browser's cache / hard-refresh once to load the versioned CSS. No new dependencies are required for the UI layer.
