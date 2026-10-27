package com.mousike.app.performance

import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

/** Album metadata does not require media formats, JS challenges, FFmpeg, or audio downloads. */
class FastMetadata(private val directory: File,private val http: OkHttpClient=OkHttpClient.Builder()
    .connectTimeout(3,TimeUnit.SECONDS).readTimeout(4,TimeUnit.SECONDS).callTimeout(3,TimeUnit.SECONDS).build()) {
    fun read(id: String): JSONObject? = runCatching {
        val f=File(directory,"$id.json")
        if(!f.isFile || System.currentTimeMillis()-f.lastModified()>24*60*60*1000L)return@runCatching null
        JSONObject(f.readText()).takeIf { it.optString("id")==id && it.optDouble("duration")>0 }
    }.getOrNull()
    fun remember(id: String,metadata: JSONObject) {
        runCatching {
            directory.mkdirs()
            // Whitelist: never persist expiring audio URLs, cookies, or headers.
            val safe=JSONObject().put("id",id)
            listOf("title","track","artist","uploader","album","duration","thumbnail","description","chapters","language").forEach { k ->
                if(metadata.has(k))safe.put(k,metadata.get(k))
            }
            if(safe.optDouble("duration")<=0)return
            val tmp=File(directory,"$id-${Thread.currentThread().id}.tmp")
            try { tmp.writeText(safe.toString()); tmp.renameTo(File(directory,"$id.json")) } finally { tmp.delete() }
            directory.listFiles { f -> f.extension=="json" }?.sortedByDescending { it.lastModified() }?.drop(128)?.forEach { it.delete() }
        }
    }
    fun fetch(id: String): JSONObject? {
        require(id.matches(Regex("[A-Za-z0-9_-]{11}")))
        read(id)?.let {return it}
        val req=Request.Builder().url("https://www.youtube.com/watch?v=$id&hl=en")
            .header("User-Agent","Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/131.0.0.0 Mobile Safari/537.36").build()
        return runCatching { http.newCall(req).execute().use { r ->
            if(!r.isSuccessful)return@use null
            parse(id,r.body?.string().orEmpty())?.also { remember(id,it) }
        } }.getOrNull()
    }
    companion object {
        /** Balanced scanner tolerates nested objects and escaped braces in descriptions. */
        fun jsonAfter(html: String,marker: String,accept: (JSONObject)->Boolean = {true}): JSONObject? {
            var from=0
            while(from<html.length) {
                val hit=html.indexOf(marker,from);if(hit<0)return null
                from=hit+marker.length
                val start=html.indexOf('{',from)
                if(start<0 || start-from>160)continue
                var depth=0;var quoted=false;var escaped=false
                for(i in start until html.length) {
                    val c=html[i]
                    if(quoted) { if(escaped)escaped=false else if(c=='\\')escaped=true else if(c=='"')quoted=false }
                    else when(c) { '"'->quoted=true; '{'->depth++; '}'->{depth--;if(depth==0) {
                        val o=runCatching { JSONObject(html.substring(start,i+1)) }.getOrNull()
                        if(o!=null && accept(o))return o
                        break
                    }} }
                }
            };return null
        }
        fun parse(id: String,html: String): JSONObject? {
            val player=jsonAfter(html,"ytInitialPlayerResponse") { it.optJSONObject("videoDetails")?.optString("videoId")==id } ?: return null
            val d=player.optJSONObject("videoDetails") ?: return null
            val duration=d.optString("lengthSeconds").toDoubleOrNull() ?: return null
            if(duration<=0)return null
            val thumbnails=d.optJSONObject("thumbnail")?.optJSONArray("thumbnails")
            val cover=thumbnails?.optJSONObject(thumbnails.length()-1)?.optString("url").orEmpty()
            val o=JSONObject().put("id",id).put("title",d.optString("title")).put("uploader",d.optString("author"))
                .put("description",d.optString("shortDescription")).put("duration",duration).put("thumbnail",cover)
            val boundaries=sortedMapOf<Double,String>()
            fun text(n: JSONObject?): String = n?.optString("simpleText")?.takeIf { it.isNotBlank() }
                ?: n?.optJSONArray("runs")?.let { a -> (0 until a.length()).joinToString("") { a.optJSONObject(it)?.optString("text").orEmpty() } }.orEmpty()
            fun visit(n: Any?) {
                when(n) {
                    is JSONObject -> n.keys().forEach { key ->
                        val child=n.opt(key)
                        if(child is JSONObject) {
                            val start=when(key) {
                                "chapterRenderer" -> child.optDouble("timeRangeStartMillis",Double.NaN)/1000
                                "macroMarkersListItemRenderer" -> com.mousike.app.album.AlbumDetector.parseTime(text(child.optJSONObject("timeDescription")))?.div(1000.0) ?: Double.NaN
                                else -> Double.NaN
                            }
                            val title=text(child.optJSONObject("title"))
                            if(start.isFinite() && start>=0 && start<duration && title.isNotBlank())boundaries[start]=title
                        }
                        visit(child)
                    }
                    is JSONArray -> for(i in 0 until n.length())visit(n.opt(i))
                }
            }
            jsonAfter(html,"ytInitialData")?.let {visit(it)}
            if(boundaries.size>=2) {
                val chapters=JSONArray();val points=boundaries.entries.toList()
                points.forEachIndexed { i,e -> chapters.put(JSONObject().put("title",e.value).put("start_time",e.key).put("end_time",points.getOrNull(i+1)?.key ?: duration)) }
                o.put("chapters",chapters)
            };return o
        }
    }
}
