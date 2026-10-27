package com.mousike.app.album

import com.mousike.app.data.model.Track
import org.json.JSONObject
import kotlin.math.abs

/** Evidence-only segmentation: a clip's time must occur in the source metadata. */
object AlbumDetector {
    const val THRESHOLD_SECONDS=480
    data class Boundary(val title: String,val startMs: Long,val endMs: Long?=null)
    data class Detection(val tracks: List<Track>,val confidence: String,val note: String)
    fun isLong(t: Track)=t.durationSec>THRESHOLD_SECONDS && t.endMs==null
    private val timestamp=Regex("(?<![\\d:])(?:\\d{1,3}:)?\\d{1,2}:\\d{2}(?![\\d:])")
    fun parseTime(raw: String): Long? {
        val parts=raw.trim().split(':').map { it.toLongOrNull() ?: return null }
        if(parts.size !in 2..3 || parts.drop(1).any { it !in 0..59 } || parts.first()<0) return null
        return parts.fold(0L) { total,n -> total*60+n }*1000
    }
    fun descriptionBoundaries(text: String): List<Boundary> = text.lines().mapNotNull { line ->
        val match=timestamp.find(line) ?: return@mapNotNull null
        val time=parseTime(match.value) ?: return@mapNotNull null
        val second=timestamp.find(line,match.range.last+1)
        val explicitEnd=second?.takeIf { line.substring(match.range.last+1,it.range.first).trim() in listOf("-","–","—","to") }
        var title=line.removeRange(match.range)
        if(explicitEnd!=null) title=title.replace(explicitEnd.value,"")
        title=title.replace(Regex("^\\s*\\d{1,2}[.)]\\s*"),"")
            .trim(' ','\t','-','–','—','|',':','[',']','(',')','•')
        if(title.length<2 || title.startsWith("http") || title.equals("tracklist",true)) return@mapNotNull null
        Boundary(title,time,explicitEnd?.let { parseTime(it.value) })
    }
    fun fromMetadata(track: Track,metadata: JSONObject): Detection {
        val duration=(metadata.optDouble("duration",track.durationSec.toDouble())*1000).toLong()
        val chapters=metadata.optJSONArray("chapters")
        val chapterBoundaries= if(chapters==null) emptyList() else (0 until chapters.length()).mapNotNull { i ->
            val o=chapters.optJSONObject(i) ?: return@mapNotNull null
            val start=o.optDouble("start_time",Double.NaN)
            val end=o.optDouble("end_time",Double.NaN)
            if(!start.isFinite()) null else Boundary(o.optString("title","Track ${i+1}"),(start*1000).toLong(),
                end.takeIf { it.isFinite() }?.times(1000)?.toLong())
        }
        val authoritative=build(track,chapterBoundaries,duration,"chapters")
        if(authoritative.size>=2) return Detection(authoritative,"verified","Track timings from source chapters. Each song seeks within the original recording.")
        val timestamps=build(track,descriptionBoundaries(metadata.optString("description")),duration,"description timestamps")
        if(timestamps.size>=2) return Detection(timestamps,"verified","Track timings from the publisher's timestamped description.")
        return Detection(emptyList(),"unresolved","No reliable track timings were found. Try matching a release, paste a timestamped track list, or play the full recording.")
    }
    fun build(track: Track,raw: List<Boundary>,durationMs: Long,source: String): List<Track> {
        if(durationMs<=0 || raw.size<2) return emptyList()
        val points=raw.filter { it.startMs>=0 && it.startMs<durationMs && it.title.isNotBlank() }
            .sortedBy { it.startMs }.distinctBy { it.startMs }
        if(points.size<2) return emptyList()
        return points.mapIndexedNotNull { i,b ->
            val next=points.getOrNull(i+1)?.startMs ?: durationMs
            val end=minOf(b.endMs ?: next,next,durationMs)
            if(end-b.startMs<10000) return@mapIndexedNotNull null
            track.copy(id="${track.videoId}@${b.startMs}:$end",title=b.title,
                album=track.album.ifBlank { track.title },durationSec=((end-b.startMs)/1000).toInt(),
                startMs=b.startMs,endMs=end,evidence=source)
        }.takeIf { it.size>=2 } ?: emptyList()
    }
    /** Matching a catalog release means playing its own songs, never inventing source offsets. */
    fun plausibleRelease(original: Track,albumTitle: String,tracks: List<Track>): Boolean {
        val clean: (String)->Set<String> = { s -> s.lowercase().replace(Regex("[^\\p{L}\\p{N} ]")," ").split(' ')
            .filter { it.length>2 && it !in setOf("full","album","jukebox","official","audio","songs","video") }.toSet() }
        val a=clean(original.title); val b=clean(albumTitle)
        val overlap=a.intersect(b).size.toDouble()/b.size.coerceAtLeast(1)
        val total=tracks.sumOf { it.durationSec }
        return tracks.size>=2 && overlap>=.6 && total>0 && abs(total-original.durationSec)<=maxOf(60,original.durationSec/8)
    }
}
