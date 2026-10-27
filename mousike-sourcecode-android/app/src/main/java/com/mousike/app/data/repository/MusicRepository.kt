package com.mousike.app.data.repository

import android.content.Context
import com.mousike.app.data.catalog.MusicCatalog
import com.mousike.app.data.catalog.CatalogCache
import com.mousike.app.data.remote.YouTubeMusicClient
import com.mousike.app.data.remote.LyricsClient
import com.mousike.app.data.local.FeedbackEntity
import com.mousike.app.data.local.HistoryEntity
import com.mousike.app.data.local.LikedSongEntity
import com.mousike.app.data.local.MousikeDao
import com.mousike.app.data.local.MousikeDatabase
import com.mousike.app.data.local.PlaylistEntity
import com.mousike.app.data.local.PlaylistTrackEntity
import com.mousike.app.data.local.SavedAlbumEntity
import com.mousike.app.data.local.SavedArtistEntity
import com.mousike.app.data.local.SearchQueryEntity
import com.mousike.app.data.model.Album
import com.mousike.app.data.model.Artist
import com.mousike.app.data.model.Track
import com.mousike.app.data.model.UserPlaylist
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import com.mousike.app.settings.AppSettings
import com.mousike.app.recommendation.TasteRanker
import com.mousike.app.recommendation.GeminiRecommender
import com.mousike.app.album.AlbumDetector
import com.mousike.app.playback.YtDlpExtractor
import com.mousike.app.data.model.TrackCodec
import androidx.room.withTransaction
import java.util.Calendar
import java.util.UUID

class MusicRepository(private val context: Context) {

    private val db = MousikeDatabase.getDatabase(context)
    private val dao: MousikeDao = db.dao()
    private val settings = AppSettings.get(context)
    private val music = YouTubeMusicClient(musicFirst={settings.state.value.musicFirst})
    private val lyricsClient = LyricsClient()
    val gemini = GeminiRecommender(settings)
    private val extractor = YtDlpExtractor.get(context)
    private val homeExpansionLock=kotlinx.coroutines.sync.Mutex()
    private var lastHomeExpansion=0L
    suspend fun homeFeed(): com.mousike.app.recommendation.HomeFeed {
        val ranked=rankCandidates(MusicCatalog.TRACKS)
        return com.mousike.app.recommendation.HomeFeedSelection.build(ranked,MusicCatalog.ALBUMS,settings.state.value)
    }
    /** Bounded metadata discovery in the background. Never resolves or prepares an audio source. */
    suspend fun expandHomeCatalog(onBatch: suspend ()->Unit)=withContext(Dispatchers.IO) {
        if(com.mousike.app.RuntimeMode.offlineFixture || !homeExpansionLock.tryLock())return@withContext
        try {
            val now=System.currentTimeMillis()
            if(now-lastHomeExpansion<15*60_000)return@withContext
            lastHomeExpansion=now
            val p=settings.state.value
            val recent=dao.getListeningHistoryList().sortedByDescending {it.playedAt}.filter {!it.wasSkipped}.take(20).mapNotNull {MusicCatalog.getTrackById(it.trackId)}
            val likes=dao.getLikedTrackIdsList().mapNotNull {MusicCatalog.getTrackById(it)}
            val seeds=(p.artists.toList()+recent.map {it.artist}+likes.map {it.artist}+rankCandidates(MusicCatalog.TRACKS).take(16).map {it.artist}).filter {it.isNotBlank() && it!="Unknown artist"}.distinct().take(4)
            val albumQueries=(seeds+p.languages.take(2).map {"$it ${p.genres.firstOrNull() ?: "music"}"}).distinct().take(6)
            val requests=albumQueries.map {it to true}+p.languages.take(2).map {"$it ${p.genres.firstOrNull() ?: "music"}" to false}
            var received=false
            for(batch in requests.chunked(2)) {
                val pages=coroutineScope {batch.map {(q,albums)->async {
                    try {music.homeSearch(q,albums)} catch(e: kotlinx.coroutines.CancellationException){throw e} catch(_: Exception){null}
                }}.awaitAll()}
                pages.filterNotNull().forEach {page->registerPage(page);if(page.albums.isNotEmpty() || page.tracks.isNotEmpty())received=true}
                onBatch()
            }
            if(!received)lastHomeExpansion=0L
        } finally {homeExpansionLock.unlock()}
    }

    suspend fun refreshCatalog(): Result<Int> = withContext(Dispatchers.IO) {
        runCatching {
            if(com.mousike.app.RuntimeMode.offlineFixture) return@runCatching MusicCatalog.TRACKS.size
            val catalog = music.home()
            registerPage(catalog)
            catalog.tracks.size
        }
    }

    suspend fun seedPreferences() = withContext(Dispatchers.IO) {
        if(com.mousike.app.RuntimeMode.offlineFixture) return@withContext
        val p=settings.state.value
        val queries=(p.artists.take(2).map { "$it songs" } + p.languages.take(2).map { "$it ${p.genres.firstOrNull() ?: "music"} songs" }).take(2)
        kotlinx.coroutines.coroutineScope {queries.map {q -> async {runCatching {remoteSearch(q)}}}.awaitAll()}
    }
    suspend fun refreshExplore(): Result<Int> = withContext(Dispatchers.IO) {
        runCatching {
            val catalog = music.explore()
            registerPage(catalog)
            catalog.tracks.size
        }
    }

    suspend fun remoteSearch(query: String): SearchResults = withContext(Dispatchers.IO) {
        if(com.mousike.app.RuntimeMode.offlineFixture) return@withContext searchAll(query)
        val directId = extractVideoId(query)
        val result = if (directId != null) {
            val raw = Track(directId,"Loading music","","",0,"","https://i.ytimg.com/vi/$directId/hqdefault.jpg","Music",0,language="")
            SearchResults(tracks=listOf(hydrateMetadata(raw)))
        } else music.search(query)
        MusicCatalog.putTracks(result.tracks)
        MusicCatalog.putAlbums(result.albums)
        MusicCatalog.putArtists(result.artists)
        CatalogCache.saveSoon(context)
        result
    }

    suspend fun relatedTracks(videoId: String): List<Track> = withContext(Dispatchers.IO) {
        val page = music.related(MusicCatalog.getTrackById(videoId)?.videoId ?: videoId)
        registerPage(page)
        val candidates=page.tracks.filter { it.id != videoId }.distinctBy { it.id }
        val ranked=rankCandidates(candidates,MusicCatalog.getTrackById(videoId))
        com.mousike.app.data.remote.MusicPreference.order(gemini.rerank(ranked,MusicCatalog.getTrackById(videoId)),settings.state.value.musicFirst)
    }

    suspend fun lyricsFor(track: Track): String = lyricsClient.forTrack(track)

    suspend fun loadAlbum(album: Album): Album = withContext(Dispatchers.IO) {
        val cached=MusicCatalog.getAlbumById(album.id) ?: album
        if (cached.sourceVideoId.isNotBlank() || cached.id.startsWith("demo-") || (cached.trackIds.isNotEmpty() && cached.trackIds.all {MusicCatalog.getTrackById(it)!=null})) return@withContext cached
        val page = music.browse(album.id)
        registerPage(page)
        val songs=page.tracks.map { it.copy(album=album.title, artist=it.artist.takeUnless { a -> a=="Unknown artist" } ?: album.artist) }
        MusicCatalog.putTracks(songs)
        val updated = album.copy(trackIds = songs.map { it.id },resolutionNote="Official catalog track list")
        MusicCatalog.putAlbums(listOf(updated))
        CatalogCache.saveSoon(context)
        updated
    }

    suspend fun loadArtist(artist: Artist): Artist = withContext(Dispatchers.IO) {
        val page = music.browse(artist.id)
        registerPage(page)
        val updated = artist.copy(topTrackIds = page.tracks.map { it.id })
        MusicCatalog.putArtists(listOf(updated))
        CatalogCache.saveSoon(context)
        updated
    }

    private fun registerPage(page: YouTubeMusicClient.CatalogPage) {
        MusicCatalog.putTracks(page.tracks)
        MusicCatalog.putAlbums(page.albums)
        MusicCatalog.putArtists(page.artists)
        CatalogCache.saveSoon(context)
    }

    // --- CATALOG ACCESS ---
    fun getAllTracks(): List<Track> = MusicCatalog.TRACKS
    fun getTrackById(id: String): Track? = MusicCatalog.getTrackById(id)
    fun getAllAlbums(): List<Album> = MusicCatalog.ALBUMS
    fun getAlbumById(id: String): Album? = MusicCatalog.getAlbumById(id)
    fun getAllArtists(): List<Artist> = MusicCatalog.ARTISTS
    fun getArtistById(id: String): Artist? = MusicCatalog.getArtistById(id)

    // --- LIKED SONGS ---
    val likedTrackIdsFlow: Flow<List<String>> = dao.getLikedTrackIds()

    val likedTracksFlow: Flow<List<Track>> = combine(dao.getLikedTrackIds(),MusicCatalog.revision) { ids,_ ->
        ids.mapNotNull { MusicCatalog.getTrackById(it) }
    }

    suspend fun toggleLike(trackId: String): Boolean = withContext(Dispatchers.IO) {
        val currentIds = dao.getLikedTrackIdsList()
        val isLiked = currentIds.contains(trackId)
        if (isLiked) {
            dao.deleteLikedSong(trackId)
            false
        } else {
            dao.insertLikedSong(LikedSongEntity(trackId = trackId))
            true
        }
    }

    suspend fun isSongLiked(trackId: String): Boolean = withContext(Dispatchers.IO) {
        dao.getLikedTrackIdsList().contains(trackId)
    }

    // --- PLAYLISTS ---
    val playlistsFlow: Flow<List<UserPlaylist>> = dao.getAllPlaylists().map { entities ->
        entities.map { entity ->
            val trackIds = dao.getPlaylistTrackEntities(entity.id).map { it.trackId }
            UserPlaylist(
                id = entity.id,
                name = entity.name,
                description = entity.description,
                coverUrl = entity.coverUrl,
                createdAt = entity.createdAt,
                trackIds = trackIds
            )
        }
    }

    suspend fun createPlaylist(name: String, description: String = ""): String = withContext(Dispatchers.IO) {
        val id = UUID.randomUUID().toString()
        dao.insertPlaylist(
            PlaylistEntity(
                id = id,
                name = name.ifBlank { "New Playlist" },
                description = description,
                coverUrl = ""
            )
        )
        id
    }

    suspend fun renamePlaylist(playlistId: String, newName: String) = withContext(Dispatchers.IO) {
        dao.renamePlaylist(playlistId, newName)
    }

    suspend fun deletePlaylist(playlistId: String) = withContext(Dispatchers.IO) {
        dao.deletePlaylistTracks(playlistId)
        dao.deletePlaylist(playlistId)
    }

    suspend fun addTrackToPlaylist(playlistId: String, trackId: String) = withContext(Dispatchers.IO) {
        db.withTransaction {
            val existing = dao.getPlaylistTrackEntities(playlistId)
            if(existing.none { it.trackId==trackId }) dao.insertPlaylistTrack(PlaylistTrackEntity(playlistId,trackId,(existing.maxOfOrNull { it.orderIndex } ?: -1)+1))
        }
    }

    suspend fun removeTrackFromPlaylist(playlistId: String, trackId: String) = withContext(Dispatchers.IO) {
        dao.removeTrackFromPlaylist(playlistId, trackId)
    }

    suspend fun getTracksForPlaylist(playlistId: String): List<Track> = withContext(Dispatchers.IO) {
        val trackIds = dao.getPlaylistTrackEntities(playlistId).map { it.trackId }
        trackIds.mapNotNull { MusicCatalog.getTrackById(it) }
    }

    // --- HISTORY & STATS ---
    val historyFlow: Flow<List<HistoryEntity>> = dao.getListeningHistory()

    val recentlyPlayedTracksFlow: Flow<List<Track>> = combine(dao.getListeningHistory(),MusicCatalog.revision) { history,_ ->
        history.mapNotNull { MusicCatalog.getTrackById(it.trackId) }.distinctBy { it.id }
    }

    suspend fun recordTrackPlayed(trackId: String, completionPercent: Int = 100, wasSkipped: Boolean = false) = withContext(Dispatchers.IO) {
        dao.insertHistory(
            HistoryEntity(
                trackId = trackId,
                playedAt = System.currentTimeMillis(),
                completionPercent = completionPercent,
                wasSkipped = wasSkipped
            )
        )
    }

    suspend fun clearHistory() = withContext(Dispatchers.IO) {
        dao.clearHistory()
    }

    // --- SAVED ALBUMS & ARTISTS ---
    val savedAlbumIdsFlow: Flow<List<String>> = dao.getSavedAlbumIds()
    val savedArtistIdsFlow: Flow<List<String>> = dao.getSavedArtistIds()

    suspend fun toggleSaveAlbum(albumId: String): Boolean = withContext(Dispatchers.IO) {
        val ids = dao.getSavedAlbumIds().first()
        if (ids.contains(albumId)) {
            dao.deleteSavedAlbum(albumId)
            false
        } else {
            dao.insertSavedAlbum(SavedAlbumEntity(albumId))
            true
        }
    }

    suspend fun toggleSaveArtist(artistId: String): Boolean = withContext(Dispatchers.IO) {
        val ids = dao.getSavedArtistIds().first()
        if (ids.contains(artistId)) {
            dao.deleteSavedArtist(artistId)
            false
        } else {
            dao.insertSavedArtist(SavedArtistEntity(artistId))
            true
        }
    }

    // --- SEARCH & SEARCH HISTORY ---
    val searchHistoryFlow: Flow<List<String>> = dao.getSearchHistory()

    suspend fun recordSearchQuery(query: String) = withContext(Dispatchers.IO) {
        if (query.isNotBlank()) {
            dao.insertSearchQuery(SearchQueryEntity(query.trim()))
        }
    }

    suspend fun removeSearchQuery(query: String) = withContext(Dispatchers.IO) {
        dao.deleteSearchQuery(query)
    }

    suspend fun clearSearchHistory() = withContext(Dispatchers.IO) {
        dao.clearSearchHistory()
    }

    fun searchAll(query: String): SearchResults {
        val q = query.trim().lowercase()
        if (q.isEmpty()) return SearchResults()

        val matchingTracks = MusicCatalog.TRACKS.filter {
            it.title.lowercase().contains(q) ||
            it.artist.lowercase().contains(q) ||
            it.album.lowercase().contains(q) ||
            it.genre.lowercase().contains(q) ||
            it.lyrics.lowercase().contains(q)
        }
        val matchingArtists = MusicCatalog.ARTISTS.filter {
            it.name.lowercase().contains(q) || it.bio.lowercase().contains(q) || it.genres.any { g -> g.lowercase().contains(q) }
        }
        val matchingAlbums = MusicCatalog.ALBUMS.filter {
            it.title.lowercase().contains(q) || it.artist.lowercase().contains(q) || it.genre.lowercase().contains(q)
        }
        return SearchResults(tracks = matchingTracks, artists = matchingArtists, albums = matchingAlbums)
    }

    // --- RECOMMENDATION FEEDBACK ---
    suspend fun recordFeedback(track: Track, feedbackType: String) = withContext(Dispatchers.IO) {
        dao.insertFeedback(
            FeedbackEntity(
                trackId = track.id,
                artistName = track.artist,
                genre = track.genre,
                feedbackType = feedbackType
            )
        )
    }

    // --- CONTENT-BASED RECOMMENDATIONS ---
    suspend fun rankCandidates(candidates: List<Track>,current: Track?=null): List<Track> = withContext(Dispatchers.IO) {
        com.mousike.app.data.remote.MusicPreference.order(TasteRanker.rank(candidates,MusicCatalog.TRACKS,dao.getLikedTrackIdsList().toSet(),dao.getListeningHistoryList(),dao.getAllFeedback(),settings.state.value,current),settings.state.value.musicFirst)
    }
    suspend fun generateRecommendations(): RecommendationResult = withContext(Dispatchers.IO) {
        val ranked=rankCandidates(MusicCatalog.TRACKS)
        val liked=dao.getLikedTrackIdsList().toSet()
        val history=dao.getListeningHistoryList()
        RecommendationResult(
            madeForYou=ranked.take(12), discoverWeekly=ranked.filter { it.id !in liked }.drop(3).take(12),
            dailyMix1=ranked.filterIndexed { i,_ -> i%2==0 }.take(12),dailyMix2=ranked.filterIndexed { i,_ -> i%2==1 }.take(12),
            heavyRotation=history.filter { !it.wasSkipped && it.completionPercent>=50 }.groupBy { it.trackId }.entries.sortedByDescending { it.value.size }
                .mapNotNull { MusicCatalog.getTrackById(it.key) }.take(6).ifEmpty { ranked.takeLast(6) },
            trending=MusicCatalog.TRACKS.take(12))
    }
    suspend fun aiBoost(result: RecommendationResult): RecommendationResult {
        val boosted=com.mousike.app.data.remote.MusicPreference.order(gemini.rerank(result.madeForYou),settings.state.value.musicFirst)
        return result.copy(madeForYou=boosted)
    }
    suspend fun hydrateMetadata(track: Track): Track = withContext(Dispatchers.IO) {
        val o=extractor.info(track.videoId)
        track.copy(title=o.optString("track").ifBlank { o.optString("title",track.title) },
            artist=o.optString("artist").ifBlank { o.optString("uploader",track.artist) },album=o.optString("album",track.album),
            durationSec=o.optDouble("duration",track.durationSec.toDouble()).toInt(),coverUrl=o.optString("thumbnail",track.coverUrl),
            language=o.optString("language",track.language))
    }
    fun cachedVideoAlbum(track: Track): Album? = MusicCatalog.getAlbumById("video-${track.videoId}")?.takeIf { it.confidence!="inspecting" }
    private fun hydrated(track: Track,o: JSONObject)=track.copy(
        title=o.optString("track").ifBlank {o.optString("title",track.title)},
        artist=o.optString("artist").ifBlank {o.optString("uploader",track.artist)},
        album=o.optString("album",track.album),durationSec=o.optDouble("duration",track.durationSec.toDouble()).toInt(),
        coverUrl=o.optString("thumbnail",track.coverUrl),language=o.optString("language",track.language))
    suspend fun resolveLongVideo(track: Track,onPartial: (Album)->Unit = {}): Album = withContext(Dispatchers.IO) {
        cachedVideoAlbum(track)?.let {return@withContext it}
        val o=extractor.info(track.videoId)
        val source=hydrated(track,o).copy(evidence="full recording")
        MusicCatalog.putTracks(listOf(source))
        val detection=AlbumDetector.fromMetadata(source,o)
        fun publish(songs: List<Track>,note: String,confidence: String): Album {
            MusicCatalog.putTracks(songs)
            val album=Album("video-${source.videoId}",source.title,source.artist,source.coverUrl,source.year,source.genre,songs.map {it.id},source.videoId,note,confidence)
            MusicCatalog.putAlbums(listOf(album));CatalogCache.saveSoon(context);return album
        }
        if(detection.tracks.isNotEmpty())return@withContext publish(detection.tracks,detection.note,detection.confidence)
        // Make the recording and track-list editor usable before optional catalog matching.
        val partial=publish(emptyList(),detection.note,detection.confidence)
        withContext(Dispatchers.Main) {onPartial(partial)}
        val query=source.title.replace(Regex("(?i)full album|official|jukebox|audio|video|all songs")," ").trim()
        val result=runCatching {music.search(query)}.getOrNull()
        val matches=coroutineScope {result?.albums.orEmpty().take(2).map {candidate -> async {
            runCatching {loadAlbum(candidate)}.getOrNull()?.let { release ->
                val songs=release.trackIds.mapNotNull {MusicCatalog.getTrackById(it)}
                if(AlbumDetector.plausibleRelease(source,candidate.title,songs))songs else null
            }
        }}.awaitAll()}
        val songs=matches.firstOrNull {it!=null}
        if(songs!=null)publish(songs,"Matched catalog release: these are separate songs, not verified offsets in the original video.","catalog match") else partial
    }
    suspend fun applyTracklist(album: Album,text: String): Album = withContext(Dispatchers.IO) {
        val source=MusicCatalog.getTrackById(album.sourceVideoId) ?: error("Original recording unavailable")
        val tracks=AlbumDetector.build(source,AlbumDetector.descriptionBoundaries(text),source.durationSec*1000L,"your timestamped track list")
        require(tracks.size>=2) { "Use at least two valid timestamps, for example 00:00 Song One and 03:12 Song Two" }
        MusicCatalog.putTracks(tracks)
        val updated=album.copy(trackIds=tracks.map { it.id },confidence="user supplied",resolutionNote="Timings from your track list. Check the boundaries during playback.")
        MusicCatalog.putAlbums(listOf(updated));CatalogCache.saveSoon(context);updated
    }
    private fun extractVideoId(value: String): String? {
        val s=value.trim()
        if(s.matches(Regex("[A-Za-z0-9_-]{11}"))) return s
        val u=runCatching { java.net.URI(s) }.getOrNull() ?: return null
        val host=u.host?.lowercase() ?: return null
        return when {
            host=="youtu.be" -> u.path.trim('/').substringBefore('/')
            host=="youtube.com" || host.endsWith(".youtube.com") -> u.rawQuery?.split('&')?.firstOrNull { it.startsWith("v=") }?.removePrefix("v=")
                ?: u.path.split('/').lastOrNull()
            else -> null
        }?.takeIf { it.matches(Regex("[A-Za-z0-9_-]{11}")) }
    }

    // --- JSON EXPORT & IMPORT ---
    suspend fun exportLibraryToJson(): String = withContext(Dispatchers.IO) {
        val root = JSONObject()
        val liked = JSONArray()
        dao.getLikedTrackIdsList().forEach { liked.put(it) }
        root.put("likedSongs", liked)

        val playlists = JSONArray()
        val playlistList = dao.getAllPlaylistsList()
        for (pl in playlistList) {
            val plObj = JSONObject()
            plObj.put("id", pl.id)
            plObj.put("name", pl.name)
            plObj.put("description", pl.description)
            plObj.put("createdAt", pl.createdAt)
            val trkArr = JSONArray()
            val trks = dao.getPlaylistTrackEntities(pl.id)
            trks.forEach { trkArr.put(it.trackId) }
            plObj.put("trackIds", trkArr)
            playlists.put(plObj)
        }
        root.put("playlists", playlists)
        // Preserve canonical song metadata: library IDs alone cannot be restored on a new device.
        val referencedIds = dao.getLikedTrackIdsList().toMutableSet()
        for (pl in playlistList) {
            for (song in dao.getPlaylistTrackEntities(pl.id)) referencedIds.add(song.trackId)
        }
        val catalog = JSONArray()
        referencedIds.mapNotNull { MusicCatalog.getTrackById(it) }.forEach { catalog.put(TrackCodec.encode(it)) }
        root.put("catalog", catalog)
        root.put("exportedAt", System.currentTimeMillis())
        root.put("app", "Mousike")
        root.toString(2)
    }

    suspend fun importLibraryFromJson(jsonString: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val root = JSONObject(jsonString)
            val restored = root.optJSONArray("catalog") ?: JSONArray()
            val parsedTracks = (0 until restored.length()).mapNotNull { index ->
                val o = restored.optJSONObject(index) ?: return@mapNotNull null
                runCatching { TrackCodec.decode(o) }.getOrNull()

            }
            MusicCatalog.putTracks(parsedTracks)
            CatalogCache.saveSoon(context)
            if (root.has("likedSongs")) {
                val liked = root.getJSONArray("likedSongs")
                for (i in 0 until liked.length()) {
                    dao.insertLikedSong(LikedSongEntity(trackId = liked.getString(i)))
                }
            }
            if (root.has("playlists")) {
                val pls = root.getJSONArray("playlists")
                for (i in 0 until pls.length()) {
                    val p = pls.getJSONObject(i)
                    val id = p.optString("id", UUID.randomUUID().toString())
                    val name = p.optString("name", "Restored Playlist")
                    val desc = p.optString("description", "")
                    val createdAt = p.optLong("createdAt", System.currentTimeMillis())
                    dao.insertPlaylist(PlaylistEntity(id = id, name = name, description = desc, createdAt = createdAt))
                    if (p.has("trackIds")) {
                        val tracks = p.getJSONArray("trackIds")
                        for (j in 0 until tracks.length()) {
                            dao.insertPlaylistTrack(PlaylistTrackEntity(id, tracks.getString(j), j))
                        }
                    }
                }
            }
            true
        } catch (e: Exception) {
            false
        }
    }

    // --- CLEAR ALL DATA ---
    suspend fun clearAllUserData() = withContext(Dispatchers.IO) {
        dao.clearLikedSongs()
        dao.clearPlaylists()
        dao.clearAllPlaylistTracks()
        dao.clearHistory()
        dao.clearSavedAlbums()
        dao.clearSavedArtists()
        dao.clearSearchHistory()
        dao.clearAllFeedback()
    }
}

data class SearchResults(
    val tracks: List<Track> = emptyList(),
    val artists: List<Artist> = emptyList(),
    val albums: List<Album> = emptyList()
)

data class RecommendationResult(
    val madeForYou: List<Track> = emptyList(),
    val discoverWeekly: List<Track> = emptyList(),
    val dailyMix1: List<Track> = emptyList(),
    val dailyMix2: List<Track> = emptyList(),
    val heavyRotation: List<Track> = emptyList(),
    val trending: List<Track> = emptyList()
)
