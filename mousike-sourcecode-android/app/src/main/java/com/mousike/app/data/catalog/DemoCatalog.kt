package com.mousike.app.data.catalog

import android.content.Context
import com.mousike.app.R
import com.mousike.app.data.model.*

/** Original instrumental sound checks, kept separate from the live catalog. */
object DemoCatalog {
    fun install(context: Context) {
        val covers=listOf(R.drawable.cover_dusk,R.drawable.cover_orbit,R.drawable.cover_dawn)
        val titles=listOf("Dusk Geometry","Soft Orbit","First Light")
        val tracks=titles.mapIndexed { i,title -> Track("demo-track-$i",title,"Mousike Studio","Sound In motion • Sound check",30,"asset:///demo/soundcheck-$i.wav", "android.resource://${context.packageName}/${covers[i]}",listOf("Lo-Fi","Electronic","Ambient")[i],2026,language="Instrumental",evidence="original bundled demo") }
        MusicCatalog.putTracks(tracks)
        MusicCatalog.putAlbums(listOf(Album("demo-sound-check","Sound In motion","Mousike Studio",tracks[0].coverUrl,2026,"Original sound check",tracks.map { it.id },resolutionNote="Original bundled instrumentals for testing offline playback.")))
        MusicCatalog.putArtists(listOf(Artist("demo-studio","Mousike Studio",tracks[1].coverUrl,"Original instrumental sound checks.",listOf("Ambient"),tracks.map { it.id })))
        CatalogCache.save(context)
    }
}
