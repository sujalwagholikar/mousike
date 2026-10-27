package com.mousike.app.recommendation

import com.mousike.app.data.local.HistoryEntity
import com.mousike.app.data.local.FeedbackEntity
import com.mousike.app.data.model.Track
import com.mousike.app.settings.Preferences
import java.text.Normalizer
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.sqrt

/** Local content-based ranking, independent of API keys; no claim of Spotify's collaborative data. */
object TasteRanker {
    private val filler=Regex("\\b(official|audio|video|lyrics|lyrical|hd|4k|remastered|full|song)\\b")
    private fun norm(s: String)=Normalizer.normalize(s.lowercase(),Normalizer.Form.NFKC)
        .replace(filler," ").replace(Regex("[^\\p{L}\\p{N} ]")," ").trim().replace(Regex("\\s+")," ")
    fun canonical(t: Track)=norm(t.artist)+":"+norm(t.title)+":"+(t.durationSec/15)
    private fun vector(t: Track): Map<String,Double> {
        val words=norm("${t.title} ${t.album} ${t.genre} ${t.mood} ${t.language}").split(' ').filter { it.length>1 }
        val out=mutableMapOf<String,Double>(); words.forEach { w ->
            out["w:$w"]=(out["w:$w"] ?: 0.0)+1
            if(w.length>=4) w.windowed(3).forEach { out["n:$it"]=(out["n:$it"] ?: 0.0)+.2 }
        }; return out
    }
    private fun similarity(a: Map<String,Double>,b: Map<String,Double>): Double {
        val denom=sqrt(a.values.sumOf { it*it }*b.values.sumOf { it*it })
        return if(denom==0.0) 0.0 else a.entries.sumOf { it.value*(b[it.key] ?: 0.0) }/denom
    }
    fun rank(candidates: List<Track>,catalog: List<Track>,liked: Set<String>,history: List<HistoryEntity>,
             feedback: List<FeedbackEntity>,prefs: Preferences,current: Track?=null,now: Long=System.currentTimeMillis()): List<Track> {
        val byId=catalog.associateBy { it.id }
        val artist=mutableMapOf<String,Double>(); val genre=mutableMapOf<String,Double>()
        val language=mutableMapOf<String,Double>(); val mood=mutableMapOf<String,Double>()
        val longVector=mutableMapOf<String,Double>(); val shortVector=mutableMapOf<String,Double>()
        fun add(t: Track,weight: Double,recent: Boolean=false) {
            artist[t.artist]=(artist[t.artist] ?: 0.0)+weight
            if(t.genre!="Music") genre[t.genre]=(genre[t.genre] ?: 0.0)+weight
            if(t.language.isNotBlank()) language[t.language]=(language[t.language] ?: 0.0)+weight
            if(t.mood.isNotBlank()) mood[t.mood]=(mood[t.mood] ?: 0.0)+weight
            vector(t).forEach { (k,v) -> longVector[k]=(longVector[k] ?: 0.0)+v*weight
                if(recent) shortVector[k]=(shortVector[k] ?: 0.0)+v*weight }
        }
        liked.forEach { byId[it]?.let { t -> add(t,2.0) } }
        history.sortedByDescending { it.playedAt }.take(500).forEachIndexed { index,h ->
            val decay=exp(-(now-h.playedAt).coerceAtLeast(0)/1000.0/86400/21)
            val w=when { h.wasSkipped && h.completionPercent<25 -> -1.5; h.completionPercent>=75 -> 1.2; h.completionPercent>25 -> .4; else -> 0.0 }
            byId[h.trackId]?.let { add(it,w*decay,index<12) }
        }
        val latest=feedback.sortedByDescending { it.timestamp }.distinctBy { it.trackId }
        val blocked=latest.filter { it.feedbackType in setOf("DONT_RECOMMEND","NOT_INTERESTED") }.map { it.trackId }.toSet()
        val suppressedArtists=latest.filter { it.feedbackType=="LESS_OF_ARTIST" }.map { it.artistName }.toSet()
        latest.filter { it.feedbackType=="MORE_LIKE_THIS" }.forEach { byId[it.trackId]?.let { t -> add(t,2.5) } }
        val currentVec=current?.let { vector(it) } ?: emptyMap()
        val recentIds=history.filter { now-it.playedAt<6*3600000 }.map { it.trackId }.toSet()
        val heard=history.map { it.trackId }.toSet()
        val eligible=candidates.filter { it.id !in blocked && it.id!=current?.id && it.artist !in suppressedArtists }
            .distinctBy { canonical(it) }
        val ranked=eligible.map { t ->
            val v=vector(t)
            var score=1.0+2.2*similarity(v,longVector)+1.4*similarity(v,shortVector)+2.8*similarity(v,currentVec)
            score+=(artist[t.artist] ?: 0.0).coerceIn(-4.0,5.0)*.3
            score+=(genre[t.genre] ?: 0.0).coerceIn(-3.0,4.0)*.2
            score+=(language[t.language] ?: 0.0).coerceIn(-2.0,3.0)*.2
            score+=(mood[t.mood] ?: 0.0).coerceIn(-2.0,3.0)*.15
            if(prefs.artists.any { t.artist.contains(it,true) }) score+=1.5
            if(prefs.genres.any { "${t.genre} ${t.title} ${t.album}".contains(it,true) }) score+=.8
            if(prefs.languages.any { it.equals(t.language,true) }) score+=.7
            if(t.id in liked) score+=.35
            if(t.id in recentIds) score-=2.8
            if(t.id !in heard) score+=prefs.discovery*2
            // Stable novelty tie-breaker keeps results deterministic for testing.
            score+=(t.id.hashCode().toLong().and(0xffff)/65535.0)*.2
            t to score
        }.toMutableList()
        val out=mutableListOf<Track>(); val counts=mutableMapOf<String,Int>()
        while(ranked.isNotEmpty()) {
            val pick=ranked.maxBy { (t,s) -> s-(counts[t.artist] ?: 0)*1.8-(if(out.lastOrNull()?.artist==t.artist) 2.5 else 0.0) }
            // At most two songs from one artist per initial 12 items, when alternatives exist.
            if(out.size<12 && (counts[pick.first.artist] ?: 0)>=2 && ranked.any { (counts[it.first.artist] ?: 0)<2 }) {
                val alternate=ranked.filter { (counts[it.first.artist] ?: 0)<2 }.maxBy { it.second }
                ranked.remove(alternate); out.add(alternate.first); counts[alternate.first.artist]=(counts[alternate.first.artist] ?: 0)+1
            } else { ranked.remove(pick); out.add(pick.first); counts[pick.first.artist]=(counts[pick.first.artist] ?: 0)+1 }
        }
        return out
    }
}
