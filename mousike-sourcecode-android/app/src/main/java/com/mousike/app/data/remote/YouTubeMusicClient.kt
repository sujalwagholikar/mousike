package com.mousike.app.data.remote

import com.mousike.app.data.model.*
import com.mousike.app.data.repository.SearchResults
import kotlinx.coroutines.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

/** Anonymous catalog only. Audio resolution is handled on-device by yt-dlp. */
class YouTubeMusicClient(private val http: OkHttpClient=OkHttpClient.Builder().connectTimeout(4,TimeUnit.SECONDS).readTimeout(8,TimeUnit.SECONDS).callTimeout(9,TimeUnit.SECONDS).build(),private val musicFirst: ()->Boolean={true}) {
    private val endpoint="https://music.youtube.com/youtubei/v1/"
    private val version="1.20261021.01.00"
    data class CatalogPage(val tracks: List<Track> = emptyList(),val albums: List<Album> = emptyList(),val artists: List<Artist> = emptyList())
    /** Extra Home metadata only. Filtered catalog search, with one bounded continuation. */
    suspend fun homeSearch(query: String,albums: Boolean): CatalogPage=withContext(Dispatchers.IO) {
        val params=if(albums)"EgWKAQIYAWoKEAkQChAFEAMQBA%3D%3D" else "EgWKAQIIAWoKEAkQChAFEAMQBA%3D%3D"
        val root=post("search",JSONObject().put("query",query).put("params",params))
        var page=extract(root)
        val token=continuation(root)
        if(token.isNotBlank()) {
            try {page=merge(listOf(page,extract(post("search",JSONObject().put("continuation",token)))))}
            catch(e: CancellationException){throw e} catch(_: Exception) { /* Keep the first page. */ }
        }
        if(albums && page.albums.isEmpty())page=merge(listOf(page,extract(post("search",JSONObject().put("query","$query albums")))))
        page
    }
    suspend fun search(query: String): SearchResults=withContext(Dispatchers.IO) {
        if(query.isBlank()) return@withContext SearchResults()
        val page=extract(post("search",JSONObject().put("query",query.trim())))
        SearchResults(MusicPreference.order(page.tracks,musicFirst()),page.artists,page.albums)
    }
    suspend fun home(): CatalogPage=withContext(Dispatchers.IO) {
        val primary=runCatching { extract(post("browse",JSONObject().put("browseId","FEmusic_home"))) }.getOrDefault(CatalogPage())
        if(primary.tracks.size>=12) primary else coroutineScope {
            val pages=listOf("Hindi music songs","English indie songs","Marathi songs").map { q -> async { runCatching { extract(post("search",JSONObject().put("query",q))) }.getOrDefault(CatalogPage()) } }.awaitAll()
            merge(listOf(primary)+pages).also { if(it.tracks.isEmpty()) throw IOException("No catalog results. Check connection and retry.") }
        }
    }
    suspend fun related(id: String): CatalogPage=withContext(Dispatchers.IO) {
        if(!id.matches(Regex("[A-Za-z0-9_-]{11}"))) return@withContext CatalogPage()
        extract(post("next",JSONObject().put("videoId",id).put("playlistId","RDAMVM$id")))
    }
    suspend fun explore(): CatalogPage=browse("FEmusic_explore")
    suspend fun browse(id: String): CatalogPage=withContext(Dispatchers.IO) {
        require(id.matches(Regex("[A-Za-z0-9_-]{1,180}")))
        val root=post("browse",JSONObject().put("browseId",id)); var page=extract(root)
        // Album shelves can paginate. Follow up to four continuation pages.
        var token=continuation(root)
        var n=0
        while(token.isNotBlank() && n++<4) {
            val next=post("browse",JSONObject().put("continuation",token))
            page=merge(listOf(page,extract(next))); token=continuation(next)
        }; page
    }
    private fun continuation(root: JSONObject): String {
        var found=""; visit(root) { key,o -> if(found.isBlank() && key=="nextContinuationData") found=o.optString("continuation") }; return found
    }
    private fun merge(pages: List<CatalogPage>)=CatalogPage(pages.flatMap { it.tracks }.distinctBy { it.id },pages.flatMap { it.albums }.distinctBy { it.id },pages.flatMap { it.artists }.distinctBy { it.id })
    private suspend fun post(route: String,params: JSONObject): JSONObject {
        params.put("context",JSONObject().put("client",JSONObject().put("clientName","WEB_REMIX").put("clientVersion",version).put("hl","en").put("gl","IN")))
        val req=Request.Builder().url("$endpoint$route?prettyPrint=false").header("User-Agent","Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/131.0.0.0 Mobile Safari/537.36")
            .header("Origin","https://music.youtube.com").header("Referer","https://music.youtube.com/")
            .header("X-YouTube-Client-Name","67").header("X-YouTube-Client-Version",version)
            .post(params.toString().toRequestBody("application/json".toMediaType())).build()
        return suspendCancellableCoroutine {continuation ->
            val call=http.newCall(req)
            continuation.invokeOnCancellation {call.cancel()}
            call.enqueue(object: okhttp3.Callback {
                override fun onFailure(call: okhttp3.Call,e: IOException) {if(continuation.isActive)continuation.resumeWith(Result.failure(e))}
                override fun onResponse(call: okhttp3.Call,response: okhttp3.Response) {
                    val result=runCatching {response.use {r ->
                        if(!r.isSuccessful)throw IOException("Catalog HTTP ${r.code}")
                        JSONObject(r.body?.string() ?: "{}").also {o ->o.optJSONObject("responseContext")?.optString("visitorData")?.takeIf {it.isNotBlank()}?.let {com.mousike.app.playback.VisitorSession.put(it)}}
                    }}
                    if(continuation.isActive)continuation.resumeWith(result)
                }
            })
        }
    }
    fun extract(root: JSONObject): CatalogPage {
        val tracks=linkedMapOf<String,Track>(); val albums=linkedMapOf<String,Album>(); val artists=linkedMapOf<String,Artist>()
        visit(root) { name,item ->
            if(name !in setOf("musicResponsiveListItemRenderer","musicTwoRowItemRenderer","musicMultiRowListItemRenderer","playlistPanelVideoRenderer")) return@visit
            val flex=item.optJSONArray("flexColumns")
            val titleObj=flex?.optJSONObject(0)?.optJSONObject("musicResponsiveListItemFlexColumnRenderer")?.optJSONObject("text") ?: item.optJSONObject("title")
            val title=text(titleObj); if(title.isBlank()) return@visit
            val secondary=flex?.optJSONObject(1)?.optJSONObject("musicResponsiveListItemFlexColumnRenderer")?.optJSONObject("text")
                ?: item.optJSONObject("subtitle") ?: item.optJSONObject("longBylineText")
            val subtitle=text(secondary)
            val nav=item.optJSONObject("navigationEndpoint") ?: titleObj?.optJSONArray("runs")?.optJSONObject(0)?.optJSONObject("navigationEndpoint")
            val browse=nav?.optJSONObject("browseEndpoint")?.optString("browseId").orEmpty()
            val mainType=nav?.optJSONObject("browseEndpoint")?.optJSONObject("browseEndpointContextSupportedConfigs")
                ?.optJSONObject("browseEndpointContextMusicConfig")?.optString("pageType").orEmpty()
            var video=item.optString("videoId").ifBlank { item.optJSONObject("playlistItemData")?.optString("videoId").orEmpty() }
            if(video.isBlank()) video=nav?.optJSONObject("watchEndpoint")?.optString("videoId").orEmpty()
            if(video.isBlank()) visit(item) { k,o -> if(k=="watchEndpoint" && video.isBlank()) video=o.optString("videoId") }
            val cover=cover(item)
            when {
                browse.startsWith("MPRE") || mainType=="MUSIC_PAGE_TYPE_ALBUM" -> albums[browse]=Album(browse,title,subtitle,cover,Regex("\\b(20\\d{2}|19\\d{2})\\b").find(subtitle)?.value?.toIntOrNull() ?: 0,"Music",emptyList())
                browse.startsWith("UC") && name=="musicTwoRowItemRenderer" || mainType=="MUSIC_PAGE_TYPE_ARTIST" -> artists[browse]=Artist(browse,title,cover,"",emptyList(),emptyList())
                video.matches(Regex("[A-Za-z0-9_-]{11}")) -> {
                    var artist=""; var album=""; var albumId=""
                    val runs=secondary?.optJSONArray("runs")
                    if(runs!=null) for(i in 0 until runs.length()) {
                        val r=runs.getJSONObject(i); val bid=r.optJSONObject("navigationEndpoint")?.optJSONObject("browseEndpoint")?.optString("browseId").orEmpty()
                        if(bid.startsWith("UC") && artist.isBlank()) artist=r.optString("text")
                        if(bid.startsWith("MPRE")) { album=r.optString("text"); albumId=bid }
                    }
                    if(artist.isBlank()) artist=subtitle.split('•','·').map { it.trim() }.firstOrNull { it.isNotBlank() && it !in setOf("Song","Video","Album","Single") && !it.contains("views",true) }.orEmpty()
                    var duration=text(item.optJSONObject("lengthText"))
                    if(duration.isBlank()) visit(item) { k,o -> if(k=="musicResponsiveListItemFixedColumnRenderer") duration=text(o.optJSONObject("text")) }
                    if(duration.isBlank()) duration=Regex("(?:\\d+:)?\\d{1,2}:\\d{2}").find(subtitle)?.value.orEmpty()
                    val seconds=duration.split(':').mapNotNull { it.toIntOrNull() }.fold(0){a,b->a*60+b}
                    val inferred=when { Regex("[\\u0900-\\u097F]").containsMatchIn(title)->"Hindi"; Regex("[\\uAC00-\\uD7AF]").containsMatchIn(title)->"Korean"; else->"" }
                    var videoType=""
                    visit(item){key,value->if(key=="watchEndpointMusicSupportedConfigs")videoType=value.optJSONObject("watchEndpointMusicConfig")?.optString("musicVideoType").orEmpty()}
                    tracks[video]=Track(video,title,artist.ifBlank { "Unknown artist" },album,seconds,"",cover.ifBlank { "https://i.ytimg.com/vi/$video/hqdefault.jpg" },"Music",0,language=inferred,musicVideoType=videoType)
                    if(albumId.isNotBlank() && albumId !in albums) albums[albumId]=Album(albumId,album,artist,cover,0,"Music",emptyList())
                }
                browse.startsWith("UC") -> artists[browse]=Artist(browse,title,cover,"",emptyList(),emptyList())
            }
        }; return CatalogPage(tracks.values.toList(),albums.values.toList(),artists.values.toList())
    }
    private fun cover(item: JSONObject): String {
        var url=""; var width=-1
        fun scan(node: Any?) {
            when(node) {
                is JSONObject -> {
                    val raw=node.optString("url")
                    val u=if(raw.startsWith("//")) "https:$raw" else raw
                    val host=runCatching { java.net.URI(u).host.orEmpty() }.getOrDefault("")
                    val imageHost=listOf("ytimg.com","googleusercontent.com","ggpht.com").any {host==it || host.endsWith(".$it")}
                    val w=node.optInt("width",0)
                    if(u.startsWith("https://") && imageHost && w>=width) {url=u;width=w}
                    node.keys().forEach {scan(node.opt(it))}
                }
                is JSONArray -> for(i in 0 until node.length()) scan(node.opt(i))
            }
        }
        scan(item);return url
    }
    private fun text(o: JSONObject?): String { if(o==null)return ""; if(o.has("simpleText"))return o.optString("simpleText"); val a=o.optJSONArray("runs") ?: return "";return (0 until a.length()).joinToString(""){a.getJSONObject(it).optString("text")} }
    private fun visit(node: Any?,action: (String,JSONObject)->Unit) {
        when(node){is JSONObject -> node.keys().forEach { key -> val o=node.opt(key);if(o is JSONObject)action(key,o);if(o is JSONObject || o is JSONArray)visit(o,action) };is JSONArray -> for(i in 0 until node.length())visit(node.opt(i),action)}
    }
}
