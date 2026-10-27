package com.mousike.app.recommendation

import com.mousike.app.data.model.Album
import com.mousike.app.data.model.Track
import com.mousike.app.settings.Preferences

data class HomeFeed(val songs: List<Track> = emptyList(),val discoveries: List<Track> = emptyList(),val albums: List<Album> = emptyList())

/** Home presentation only; never changes autoplay, album track order or the player queue. */
object HomeFeedSelection {
    fun build(ranked: List<Track>,albums: List<Album>,prefs: Preferences,day: Long=System.currentTimeMillis()/86_400_000): HomeFeed {
        val artists=mutableMapOf<String,Double>()
        ranked.take(100).forEachIndexed {i,t->artists[t.artist.lowercase()]=maxOf(artists[t.artist.lowercase()] ?: 0.0,1.0-i/120.0)}
        val positions=ranked.mapIndexed {i,t->t.id to i}.toMap()
        val sorted=albums.distinctBy {it.artist.lowercase()+":"+it.title.lowercase()}.sortedByDescending {a->
            val artist=artists.entries.filter {a.artist.contains(it.key,true)}.maxOfOrNull {it.value} ?: 0.0
            val explicit=if(prefs.artists.any {a.artist.contains(it,true)})3.0 else 0.0
            val tracks=a.trackIds.mapNotNull {positions[it]}.minOrNull()?.let {2.0/(1+it/10.0)} ?: 0.0
            val genre=if(prefs.genres.any {"${a.title} ${a.genre}".contains(it,true)}).7 else 0.0
            val rotation=((a.id+day).hashCode().toLong() and 0x7fffffff)%1000/1000.0
            artist*3+explicit+tracks+genre+rotation*.5
        }
        // Offer a varied first pass, then fill the larger collection with remaining releases.
        val counts=mutableMapOf<String,Int>()
        val diverse=sorted.filter {a->val key=a.artist.lowercase();val count=counts[key] ?: 0;counts[key]=count+1;count<3}
        val ordered=(diverse+sorted).distinctBy {it.id}.take(96)
        val unique=ranked.distinctBy {TasteRanker.canonical(it)}
        return HomeFeed(unique.take(60),unique.drop(60).take(48).ifEmpty {unique.drop(12).take(48)},ordered)
    }
}
