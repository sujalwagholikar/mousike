package com.mousike.app.data.remote

import com.mousike.app.data.model.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

/** Public LRCLIB metadata lookup; synchronized lyrics only when actually provided. */
class LyricsClient(private val client: OkHttpClient = OkHttpClient.Builder()
    .connectTimeout(8, TimeUnit.SECONDS).readTimeout(12, TimeUnit.SECONDS).build()) {
    suspend fun forTrack(track: Track): String = withContext(Dispatchers.IO) {
        val title = URLEncoder.encode(track.title, "UTF-8")
        val artist = URLEncoder.encode(track.artist, "UTF-8")
        val url = "https://lrclib.net/api/search?track_name=$title&artist_name=$artist"
        val request = Request.Builder().url(url)
            .header("User-Agent", "Mousike/2.0 (Android native music client)")
            .build()
        runCatching {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@use ""
                val json = org.json.JSONArray(response.body?.string().orEmpty())
                val normalizedTitle = track.title.trim().lowercase()
                val normalizedArtist = track.artist.trim().lowercase()
                val best = (0 until json.length()).mapNotNull { json.optJSONObject(it) }
                    .filter { o ->
                        o.optString("trackName").trim().lowercase() == normalizedTitle &&
                        o.optString("artistName").trim().lowercase().contains(normalizedArtist)
                    }
                    .minByOrNull { o -> kotlin.math.abs(o.optDouble("duration", 0.0) - track.durationSec) }
                best?.optString("syncedLyrics").orEmpty()
            }
        }.getOrDefault("")
    }
}
