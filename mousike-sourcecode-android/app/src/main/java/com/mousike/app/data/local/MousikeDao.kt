package com.mousike.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface MousikeDao {
    // Liked Songs
    @Query("SELECT trackId FROM liked_songs ORDER BY addedAt DESC")
    fun getLikedTrackIds(): Flow<List<String>>

    @Query("SELECT trackId FROM liked_songs ORDER BY addedAt DESC")
    suspend fun getLikedTrackIdsList(): List<String>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLikedSong(likedSong: LikedSongEntity)

    @Query("DELETE FROM liked_songs WHERE trackId = :trackId")
    suspend fun deleteLikedSong(trackId: String)

    @Query("SELECT EXISTS(SELECT 1 FROM liked_songs WHERE trackId = :trackId)")
    fun isSongLikedFlow(trackId: String): Flow<Boolean>

    // Playlists
    @Query("SELECT user_playlists.* FROM user_playlists LEFT JOIN playlist_tracks ON user_playlists.id = playlist_tracks.playlistId GROUP BY user_playlists.id ORDER BY user_playlists.createdAt DESC")
    fun getAllPlaylists(): Flow<List<PlaylistEntity>>

    @Query("SELECT user_playlists.* FROM user_playlists LEFT JOIN playlist_tracks ON user_playlists.id = playlist_tracks.playlistId GROUP BY user_playlists.id ORDER BY user_playlists.createdAt DESC")
    suspend fun getAllPlaylistsList(): List<PlaylistEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaylist(playlist: PlaylistEntity)

    @Query("UPDATE user_playlists SET name = :newName WHERE id = :playlistId")
    suspend fun renamePlaylist(playlistId: String, newName: String)

    @Query("DELETE FROM user_playlists WHERE id = :playlistId")
    suspend fun deletePlaylist(playlistId: String)

    @Query("DELETE FROM playlist_tracks WHERE playlistId = :playlistId")
    suspend fun deletePlaylistTracks(playlistId: String)

    // Playlist Tracks
    @Query("SELECT trackId FROM playlist_tracks WHERE playlistId = :playlistId ORDER BY orderIndex ASC")
    fun getPlaylistTrackIds(playlistId: String): Flow<List<String>>

    @Query("SELECT * FROM playlist_tracks WHERE playlistId = :playlistId ORDER BY orderIndex ASC")
    suspend fun getPlaylistTrackEntities(playlistId: String): List<PlaylistTrackEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaylistTrack(item: PlaylistTrackEntity)

    @Query("DELETE FROM playlist_tracks WHERE playlistId = :playlistId AND trackId = :trackId")
    suspend fun removeTrackFromPlaylist(playlistId: String, trackId: String)

    // History
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHistory(history: HistoryEntity)

    @Query("SELECT * FROM listening_history ORDER BY playedAt DESC LIMIT 1000")
    fun getListeningHistory(): Flow<List<HistoryEntity>>

    @Query("SELECT * FROM listening_history ORDER BY playedAt DESC LIMIT 1000")
    suspend fun getListeningHistoryList(): List<HistoryEntity>

    @Query("DELETE FROM listening_history")
    suspend fun clearHistory()

    // Saved Albums
    @Query("SELECT albumId FROM saved_albums ORDER BY savedAt DESC")
    fun getSavedAlbumIds(): Flow<List<String>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSavedAlbum(entity: SavedAlbumEntity)

    @Query("DELETE FROM saved_albums WHERE albumId = :albumId")
    suspend fun deleteSavedAlbum(albumId: String)

    // Saved Artists
    @Query("SELECT artistId FROM saved_artists ORDER BY savedAt DESC")
    fun getSavedArtistIds(): Flow<List<String>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSavedArtist(entity: SavedArtistEntity)

    @Query("DELETE FROM saved_artists WHERE artistId = :artistId")
    suspend fun deleteSavedArtist(artistId: String)

    // Search History
    @Query("SELECT query FROM search_history ORDER BY timestamp DESC LIMIT 20")
    fun getSearchHistory(): Flow<List<String>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSearchQuery(query: SearchQueryEntity)

    @Query("DELETE FROM search_history WHERE query = :query")
    suspend fun deleteSearchQuery(query: String)

    @Query("DELETE FROM search_history")
    suspend fun clearSearchHistory()

    // Feedback
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFeedback(feedback: FeedbackEntity)

    @Query("SELECT * FROM recommendation_feedback ORDER BY timestamp DESC")
    suspend fun getAllFeedback(): List<FeedbackEntity>

    // Reset All Data
    @Query("DELETE FROM liked_songs")
    suspend fun clearLikedSongs()

    @Query("DELETE FROM user_playlists")
    suspend fun clearPlaylists()

    @Query("DELETE FROM playlist_tracks")
    suspend fun clearAllPlaylistTracks()

    @Query("DELETE FROM saved_albums")
    suspend fun clearSavedAlbums()

    @Query("DELETE FROM saved_artists")
    suspend fun clearSavedArtists()

    @Query("DELETE FROM recommendation_feedback")
    suspend fun clearAllFeedback()
}
