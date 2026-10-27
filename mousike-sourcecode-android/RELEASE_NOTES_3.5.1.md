# Mousike 3.5.1

This update addresses playback waiting/recovery and the system media notification, and adds the developer photo supplied for this release.

- Upgrade all Media3 modules from 1.6.1 to 1.8.0, the first stable release containing the HLS playlist-error propagation fix (androidx/media #2401). This prevents that loader failure from remaining hidden behind buffering.
- Replace callback-dependent buffering timers with one continuously sampled watchdog. A brief start followed by buffering is monitored again automatically.
- Count actual transferred bytes as progress while the media extractor reads its index. A zero buffered duration alone no longer rejects a growing download.
- Rebuild a stalled prepared source once using its resolved address and cached bytes before trying another connection. Preserve the queue and current position.
- Keep a continuous overall waiting deadline across retries, and show a retry action when no usable source can be reached.
- Derive loading/playing status from the actual player snapshot. Clear stale loading/error text when playback starts.
- Keep pause available during loading, preserving the prepared source for a quick resume.
- Let MediaSessionService own its native MediaStyle notification instead of overwriting it with a plain loading notification. Include track/artist/artwork, queue transport controls, a seek timeline where Android supports it, and an activity link to the expanded player.
- Bundle the newly uploaded portrait and display it directly beside the Sujal Wagholikar developer credit. It needs no network lookup.

The existing ranged transport, extractor, recommendation engine, downloads and frontend designs remain in place. This release changes the playback state/recovery coordinator as explicitly requested; it does not claim that a remote network can deliver every stream instantly.

Install this signed development APK over Mousike Fast without uninstalling. The package ID and development signing key are retained.
