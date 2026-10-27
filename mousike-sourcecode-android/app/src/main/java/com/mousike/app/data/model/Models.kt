package com.mousike.app.data.model

enum class RepeatMode {
    OFF, ALL, ONE
}

data class LyricLine(
    val timeMs: Long,
    val text: String
)

data class Track(
    val id: String,
    val title: String,
    val artist: String,
    val album: String,
    val durationSec: Int,
    val streamUrl: String,
    val coverUrl: String,
    val genre: String,
    val year: Int,
    val language: String = "English",
    val lyrics: String = "",
    val bpm: Int = 120,
    val isExplicit: Boolean = false,
    val videoId: String = id,
    val startMs: Long = 0L,
    val endMs: Long? = null,
    val evidence: String = "",
    val mood: String = "",
    val musicVideoType: String = ""
) {
    fun parseLrcLyrics(): List<LyricLine> {
        if (lyrics.isBlank()) return emptyList()
        val list = mutableListOf<LyricLine>()
        val regex = Regex("""\[(\d{2}):(\d{2})\.?(\d{2,3})?\](.*)""")
        lyrics.lines().forEach { line ->
            val match = regex.matchEntire(line.trim())
            if (match != null) {
                val min = match.groupValues[1].toLongOrNull() ?: 0L
                val sec = match.groupValues[2].toLongOrNull() ?: 0L
                val msStr = match.groupValues[3]
                val ms = when {
                    msStr.length == 2 -> (msStr.toLongOrNull() ?: 0L) * 10
                    msStr.length == 3 -> msStr.toLongOrNull() ?: 0L
                    else -> 0L
                }
                val totalMs = (min * 60 + sec) * 1000 + ms
                val text = match.groupValues[4].trim()
                if (text.isNotEmpty()) {
                    list.add(LyricLine(totalMs, text))
                }
            }
        }
        return list.sortedBy { it.timeMs }
    }
}

data class Album(
    val id: String,
    val title: String,
    val artist: String,
    val coverUrl: String,
    val year: Int,
    val genre: String,
    val trackIds: List<String>,
    val sourceVideoId: String = "",
    val resolutionNote: String = "",
    val confidence: String = "catalog"
)

data class Artist(
    val id: String,
    val name: String,
    val avatarUrl: String,
    val bio: String,
    val genres: List<String>,
    val topTrackIds: List<String>
)

data class UserPlaylist(
    val id: String,
    val name: String,
    val description: String = "",
    val coverUrl: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val trackIds: List<String> = emptyList()
)

data class PlaybackState(
    val currentTrack: Track? = null,
    val error: String? = null,
    val loadingLabel: String = "",
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false,
    val playWhenReady: Boolean = false,
    val currentPositionMs: Long = 0L,
    val durationMs: Long = 0L,
    val isShuffle: Boolean = false,
    val repeatMode: RepeatMode = RepeatMode.OFF,
    val playbackSpeed: Float = 1.0f,
    val queue: List<Track> = emptyList(),
    val queueIndex: Int = 0,
    val sleepTimerMinutes: Int? = null,
    val sleepTimerRemainingSec: Int = 0,
    val equalizerPreset: String = "Balanced",
    val bassBoost: Float = 0.5f,
    val vocalClarity: Float = 0.5f,
    val trebleBoost: Float = 0.5f
)

data class GenreCategory(
    val id: String,
    val name: String,
    val gradientStartHex: Long,
    val gradientEndHex: Long,
    val iconName: String = "music"
)

data class RecommendationFeedback(
    val trackId: String,
    val feedbackType: String, // MORE_LIKE_THIS, NOT_INTERESTED, LESS_OF_ARTIST, DONT_RECOMMEND
    val timestamp: Long = System.currentTimeMillis()
)
