# Mousike 3.5.0 — frontend refinement

## Scope

The user approved 3.4.1 playback and requested frontend changes only. Runtime source, media/request handling, extraction, buffers, cache transport, downloads, data storage, recommendation engines and ViewModel are preserved byte for byte against the working source. No dependency, manifest, native runtime or bundled music change is introduced. Version name/code advance to 3.5.0/350 for an in-place update using the existing signing key.

## Settings

Eight navigable sections replace the long combined settings page. Profile, appearance, playback, offline listening, motion/display, AI, data and support have distinct landing rows. Back returns from a section to the overview. Light/dark palettes, existing type choices, large text and all prior controls are retained. Feature previews are available under About & support.

## Expanded player

The player uses a responsive fitted artwork stage, animated ambience, artwork scale/track transitions, a gently animated seek bar, like response and play/pause transitions. Reduce motion uses a direct seek-progress value. Primary transport controls remain explicit. Lyrics, queue and download have labelled shortcuts; equalizer, sleep timer, playlist and recommendation-feedback actions are available in the options menu. Callbacks still invoke the unchanged ViewModel/player methods.

## Daily feature spotlight

A branded floating card highlights ad-free listening, quality choices, appearance customisation and offline downloads. It has an immediately available close button, outside/Back dismissal, Keep listening and a settings CTA. A persisted device-local date caps automatic appearances to once per calendar day, on Home. It waits during album inspection and themed mix building. Opening/dismissing it does not pause, seek or replace music. Entry/exit use graphics-layer fade, scale and translation; the content stays measured throughout its transition. Reduce motion removes motion. No advertising SDK, tracker, account or remote ad service is added.

## Personal collections

The existing Daily Mix 1/2 builders are unchanged. English Mix, Bollywood Punch, Indie Afterhours, Soft Focus, On Repeat and Discovery Dose assemble collections from the current personalised candidate order, with language/genre/mood filters and an artist cap. Existing optional Gemini-boosted Made For You candidates remain available; no new AI service or mandatory key is introduced. These are not claimed to be wholly AI-generated music lists.

The six new builders run selection off the UI thread, show cancellable preparation stages, register the resulting album through existing catalog APIs, and use existing album navigation. They do not start or modify playback. Matching catalog metadata is required; no unrelated songs are substituted when a themed collection has no matches. Artistic cover templates fit source artwork and appear on both Home cards and mix album pages. Lists remain playable through the existing album controls.

## Verification

See TEST_REPORT.md for the completed unit/Android checks, visual review, unchanged-backend evidence and practical limits. This frontend delivery is not a Play Store certification, production signing migration or a new live-stream benchmark.
