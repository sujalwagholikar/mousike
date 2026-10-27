package com.mousike.app.data.catalog

import android.content.Context
import android.util.AtomicFile
import com.mousike.app.data.model.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import kotlinx.coroutines.*

/** Durable metadata, including chapter offsets. Expiring signed audio URLs are never stored. */
object CatalogCache {
    private val io=CoroutineScope(SupervisorJob()+Dispatchers.IO.limitedParallelism(1))
    private var pending: Job?=null
    private var loaded=false
    @Synchronized fun loadOnce(context: Context) {if(!loaded){load(context);loaded=true}}
    @Synchronized fun saveSoon(context: Context) {pending?.cancel();pending=io.launch {delay(500);save(context.applicationContext)}}
    @Synchronized fun load(context: Context) { runCatching {
        val f=AtomicFile(File(context.filesDir,"catalog-v3.json")); if(!f.baseFile.exists()) return
        val root=JSONObject(String(f.readFully(),Charsets.UTF_8))
        val songs=root.optJSONArray("tracks") ?: JSONArray()
        MusicCatalog.putTracks((0 until songs.length()).mapNotNull { runCatching { TrackCodec.decode(songs.getJSONObject(it)) }.getOrNull() })
        val albums=root.optJSONArray("albums") ?: JSONArray()
        MusicCatalog.putAlbums((0 until albums.length()).map { i -> val o=albums.getJSONObject(i)
            Album(o.getString("id"),o.optString("title"),o.optString("artist"),o.optString("cover"),o.optInt("year"),o.optString("genre","Music"),
                (o.optJSONArray("tracks") ?: JSONArray()).let { a -> (0 until a.length()).map { a.getString(it) } },o.optString("source"),o.optString("note"),o.optString("confidence","catalog")) })
        val artists=root.optJSONArray("artists") ?: JSONArray()
        MusicCatalog.putArtists((0 until artists.length()).map { i -> val o=artists.getJSONObject(i)
            Artist(o.getString("id"),o.optString("name"),o.optString("cover"),o.optString("bio"),emptyList(),(o.optJSONArray("tracks") ?: JSONArray()).let { a -> (0 until a.length()).map { a.getString(it) } }) })
    } }
    @Synchronized fun save(context: Context) { runCatching {
        val tracks=JSONArray(); MusicCatalog.TRACKS.forEach { tracks.put(TrackCodec.encode(it)) }
        val albums=JSONArray(); MusicCatalog.ALBUMS.forEach { a -> albums.put(JSONObject().put("id",a.id).put("title",a.title).put("artist",a.artist).put("cover",a.coverUrl)
            .put("year",a.year).put("genre",a.genre).put("tracks",JSONArray(a.trackIds)).put("source",a.sourceVideoId).put("note",a.resolutionNote).put("confidence",a.confidence)) }
        val artists=JSONArray(); MusicCatalog.ARTISTS.forEach { a -> artists.put(JSONObject().put("id",a.id).put("name",a.name).put("cover",a.avatarUrl).put("bio",a.bio).put("tracks",JSONArray(a.topTrackIds))) }
        val f=AtomicFile(File(context.filesDir,"catalog-v3.json")); var stream: java.io.FileOutputStream?=null
        try { stream=f.startWrite(); stream.write(JSONObject().put("tracks",tracks).put("albums",albums).put("artists",artists).toString().toByteArray(Charsets.UTF_8)); f.finishWrite(stream) }
        catch(e: Exception) { if(stream!=null) f.failWrite(stream); throw e }
    } }
    @Synchronized fun clear(context: Context) { pending?.cancel();loaded=false;File(context.filesDir,"catalog-v3.json").delete() }
}
