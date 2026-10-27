package com.mousike.app.data.catalog

import com.mousike.app.data.model.Album
import com.mousike.app.data.model.Artist
import com.mousike.app.data.model.GenreCategory
import com.mousike.app.data.model.Track
import java.util.concurrent.ConcurrentHashMap

/** A live, normalized music registry. No fictional tracks or placeholder stream URLs. */
object MusicCatalog {
    val GENRES = listOf(
        GenreCategory("bollywood", "Bollywood", 0xFFF59E0B, 0xFFEF4444, "heart"),
        GenreCategory("indie", "Indie", 0xFF10B981, 0xFF06B6D4, "music"),
        GenreCategory("pop", "Pop", 0xFFEC4899, 0xFF8B5CF6, "sparkles"),
        GenreCategory("hip-hop", "Hip-Hop", 0xFFF97316, 0xFFE11D48, "flame"),
        GenreCategory("lofi", "Lo-Fi", 0xFF6366F1, 0xFF8B5CF6, "coffee"),
        GenreCategory("electronic", "Electronic", 0xFF06B6D4, 0xFF3B82F6, "zap"),
        GenreCategory("rock", "Rock", 0xFFDC2626, 0xFF7C3AED, "radio"),
        GenreCategory("classical", "Classical", 0xFF8B5CF6, 0xFF3B82F6, "music")
    )
    private val tracks = ConcurrentHashMap<String, Track>()
    private val artists = ConcurrentHashMap<String, Artist>()
    private val albums = ConcurrentHashMap<String, Album>()

    @Volatile private var trackSnapshot: List<Track> = emptyList()
    @Volatile private var artistSnapshot: List<Artist> = emptyList()
    @Volatile private var albumSnapshot: List<Album> = emptyList()
    private val version=kotlinx.coroutines.flow.MutableStateFlow(0)
    val revision: kotlinx.coroutines.flow.StateFlow<Int> = version
    val TRACKS: List<Track> get() = trackSnapshot
    val ARTISTS: List<Artist> get() = artistSnapshot
    val ALBUMS: List<Album> get() = albumSnapshot

    @Synchronized fun clear() { tracks.clear(); albums.clear(); artists.clear();trackSnapshot=emptyList();artistSnapshot=emptyList();albumSnapshot=emptyList();version.value++ }
    @Synchronized fun putTracks(items: List<Track>) { var changed=false;items.forEach { if(tracks.put(it.id,it)!=it)changed=true };if(changed){trackSnapshot=tracks.values.sortedBy {it.title};version.value++} }
    @Synchronized fun putArtists(items: List<Artist>) { var changed=false;items.forEach { if(artists.put(it.id,it)!=it)changed=true };if(changed){artistSnapshot=artists.values.sortedBy {it.name};version.value++} }
    @Synchronized fun putAlbums(items: List<Album>) { var changed=false;items.forEach { if(albums.put(it.id,it)!=it)changed=true };if(changed){albumSnapshot=albums.values.sortedBy {it.title};version.value++} }
    fun getTrackById(id: String): Track? = tracks[id]
    fun getAlbumById(id: String): Album? = albums[id]
    fun getArtistById(id: String): Artist? = artists[id]
    fun getTracksForArtist(artistName: String): List<Track> = TRACKS.filter { it.artist.equals(artistName, true) }
    fun getTracksForAlbum(albumTitle: String): List<Track> = TRACKS.filter { it.album.equals(albumTitle, true) }
    fun getTracksForGenre(genre: String): List<Track> = TRACKS.filter { it.genre.contains(genre, true) }
}
