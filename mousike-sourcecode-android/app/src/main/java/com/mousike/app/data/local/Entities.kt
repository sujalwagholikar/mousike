package com.mousike.app.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "liked_songs")
data class LikedSongEntity(
    @PrimaryKey val trackId: String,
    val addedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "user_playlists")
data class PlaylistEntity(
    @PrimaryKey val id: String,
    val name: String,
    val description: String = "",
    val coverUrl: String = "",
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "playlist_tracks", primaryKeys = ["playlistId", "trackId"])
data class PlaylistTrackEntity(
    val playlistId: String,
    val trackId: String,
    val orderIndex: Int = 0
)

@Entity(tableName = "listening_history")
data class HistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val trackId: String,
    val playedAt: Long = System.currentTimeMillis(),
    val completionPercent: Int = 100,
    val wasSkipped: Boolean = false
)

@Entity(tableName = "saved_albums")
data class SavedAlbumEntity(
    @PrimaryKey val albumId: String,
    val savedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "saved_artists")
data class SavedArtistEntity(
    @PrimaryKey val artistId: String,
    val savedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "recommendation_feedback")
data class FeedbackEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val trackId: String,
    val artistName: String,
    val genre: String,
    val feedbackType: String,
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "search_history")
data class SearchQueryEntity(
    @PrimaryKey val query: String,
    val timestamp: Long = System.currentTimeMillis()
)
