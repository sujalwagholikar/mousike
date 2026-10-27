package com.mousike.app.settings

import com.mousike.app.data.model.*
import com.mousike.app.recommendation.HomeFeedSelection
import org.junit.Assert.*
import org.junit.Test

class HomeFeedTest {
    private fun song(i: Int,artist: String="Artist $i")=Track("t$i","Song $i",artist,"Release $i",180,"","","Pop",2026)
    private fun album(i: Int,artist: String="Artist $i")=Album("a$i","Release $i",artist,"",2026,"Pop",listOf("t$i"))
    @Test fun bulkShelvesUseAvailableRealCatalogWithoutDuplicatingIds() {
        val feed=HomeFeedSelection.build((0..149).map {song(it)},(0..119).map {album(it)},Preferences(),1)
        assertEquals(60,feed.songs.size);assertEquals(48,feed.discoveries.size);assertEquals(96,feed.albums.size)
        assertEquals(96,feed.albums.distinctBy {it.id}.size)
    }
    @Test fun listeningRankingChangesAlbumOrder() {
        val albums=listOf(album(1,"One"),album(2,"Two"))
        assertEquals("a1",HomeFeedSelection.build(listOf(song(1,"One")),albums,Preferences(),1).albums.first().id)
        assertEquals("a2",HomeFeedSelection.build(listOf(song(2,"Two")),albums,Preferences(),1).albums.first().id)
    }
    @Test fun explicitTasteAndDiversityKeepOneArtistFromOwningTheFirstShelf() {
        val albums=(0..9).map {album(it,"Favourite")}+album(30,"Other")
        val feed=HomeFeedSelection.build(emptyList(),albums,Preferences(artists=setOf("Favourite")),1)
        assertEquals(11,feed.albums.size)
        assertTrue(feed.albums.take(4).any {it.artist=="Other"})
    }
    @Test fun sparseAndOfflineCatalogsStayUsable() {
        assertTrue(HomeFeedSelection.build(emptyList(),emptyList(),Preferences()).albums.isEmpty())
        val song=song(1);val album=album(1)
        val feed=HomeFeedSelection.build(listOf(song,song),listOf(album,album),Preferences())
        assertEquals(listOf(song),feed.songs);assertEquals(listOf(album),feed.albums)
    }
}
