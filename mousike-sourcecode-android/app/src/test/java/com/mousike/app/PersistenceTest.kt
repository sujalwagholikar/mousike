package com.mousike.app
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.mousike.app.data.catalog.*
import com.mousike.app.data.model.*
import com.mousike.app.data.repository.MusicRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
@RunWith(RobolectricTestRunner::class)
@Config(sdk=[30],application=android.app.Application::class)
class PersistenceTest {
 @Test fun chapterCatalogColdLoad(){val c=ApplicationProvider.getApplicationContext<Context>();MusicCatalog.clear();val t=Track("abcdefghijk@60000:180000","Song","Artist","Album",120,"","","Music",2026,videoId="abcdefghijk",startMs=60000,endMs=180000,evidence="chapters");MusicCatalog.putTracks(listOf(t));MusicCatalog.putAlbums(listOf(Album("video-abcdefghijk","Album","Artist","",2026,"Music",listOf(t.id),"abcdefghijk","From chapters","verified")));CatalogCache.save(c);MusicCatalog.clear();CatalogCache.load(c);assertEquals(t,MusicCatalog.getTrackById(t.id));assertEquals("abcdefghijk",MusicCatalog.getAlbumById("video-abcdefghijk")?.sourceVideoId)}
 @Test fun backupRestoresLikedChapterAndPlaylist()=runBlocking{val c=ApplicationProvider.getApplicationContext<Context>();val r=MusicRepository(c);r.clearAllUserData();MusicCatalog.clear();val t=Track("abcdefghijk@0:180000","Opening","Artist","Album",180,"","","Music",2026,videoId="abcdefghijk",endMs=180000);MusicCatalog.putTracks(listOf(t));r.toggleLike(t.id);val id=r.createPlaylist("My Mix");r.addTrackToPlaylist(id,t.id);val j=r.exportLibraryToJson();r.clearAllUserData();MusicCatalog.clear();assertTrue(r.importLibraryFromJson(j));assertTrue(r.isSongLiked(t.id));assertEquals(t,MusicCatalog.getTrackById(t.id));assertEquals(listOf(t),r.getTracksForPlaylist(id))}
}
