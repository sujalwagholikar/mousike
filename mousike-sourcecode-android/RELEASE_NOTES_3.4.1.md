# Mousike 3.4.1 — playback after the first sound

## Problem and repair

The earlier validation checked a small media prefix. Progressive playback then used an unbounded HTTP request; that connection could deliver a playable burst and then stop. Increasing steady-state buffer limits did not repair the read itself. The previous synthetic recovery check stalled resolution rather than an actual media response and did not reproduce post-start delivery.

`ResumingAudioDataSource` is now the progressive upstream under Media3's byte cache. It uses finite 256 KiB requests, validates Content-Range offsets/total length, and exposes the full resource length to the player. Successful chunk boundaries remain internal to the reader. A timeout or premature EOF resumes at the exact next byte; at most two reconnects occur per chunk. Denied addresses and invalid ranges fail immediately for source recovery. A source ignoring Range can still play once from zero, but is never resumed by duplicating its prefix. Local files/assets and HLS retain their existing loaders. No new proxy, external server or runtime is required.

Media read timeout is at most three seconds; continuous, progressing reads have no whole-file deadline. Initial buffer threshold is now 1,000 ms instead of 300 ms; rebuffer threshold is 2,500 ms instead of 1,500 ms. These thresholds describe media buffered, not mandatory waiting time. Normal fast transfers fill them quickly. Steady buffering remains 15–60 seconds.

Resolved-source markers are explicitly cleared/set on media preparation. Buffer recovery observes actual buffered-position progress and combined Media3 events rather than a UI label. Resolution work is protected from being treated as a stalled media read. Recovery preserves queue and position, remains bounded, and ordinary stalled loading uses a 4.5-second no-progress threshold. User play intent follows notification/headset play/pause and repeated taps on the current track. Failed sources cannot hold Next behind another Play tap.

The progressive cache key includes a transport version so older resource-length metadata cannot truncate the new reader. Likes, settings, playlists and downloads retain their identities. Install over Mousike Fast; package and signing key are unchanged.

## Verification approach

New unit checks exercise byte-exact continuation after timeouts/EOF, ranges across the complete resource, seeks/length bounds, mismatched response rejection, retry limits, forbidden addresses and servers ignoring Range. Android checks use a test-only localhost HTTPS server that sends real audio and then deliberately stops. The TLS trust override and certificate live in the Android test fixture only. Production trusts normal system certificates.

See TEST_REPORT.md for final results and the live-network limits. Onboarding/design were not changed in this repair.

## Primary references reviewed

- https://developer.android.com/media/media3/exoplayer/listening-to-player-events
- https://developer.android.com/media/media3/exoplayer/troubleshooting
- https://github.com/androidx/media/blob/1.6.1/libraries/datasource_okhttp/src/main/java/androidx/media3/datasource/okhttp/OkHttpDataSource.java
- https://github.com/yt-dlp/yt-dlp/blob/master/yt_dlp/downloader/http.py
