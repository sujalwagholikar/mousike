package com.mousike.app.performance

import com.mousike.app.album.AlbumDetector
import com.mousike.app.data.model.Track
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.nio.file.Files
import java.util.concurrent.*
import java.util.concurrent.atomic.AtomicInteger

class PerformanceRegressionTest {
    @Test fun concurrentRequestsShareOneExtraction() {
        val cache=SingleFlightCache<String,String>(8)
        val calls=AtomicInteger();val start=CountDownLatch(1)
        val pool=Executors.newFixedThreadPool(8)
        try {
            val before=System.nanoTime()
            val results=(1..8).map {pool.submit(Callable {start.await();cache.get("song") {calls.incrementAndGet();Thread.sleep(150);"audio"}})}
            start.countDown();assertEquals(List(8){"audio"},results.map {it.get(5,TimeUnit.SECONDS)})
            assertEquals(1,calls.get())
            println("COALESCED_8_REQUESTS_MS=${(System.nanoTime()-before)/1000000};EXTRACTIONS=${calls.get()}")
        } finally {pool.shutdownNow()}
    }
    @Test fun warmCacheDoesNotRepeatWork() {
        val cache=SingleFlightCache<String,String>(8);val calls=AtomicInteger()
        cache.get("song") {calls.incrementAndGet();"audio"}
        val before=System.nanoTime()
        repeat(1000) {assertEquals("audio",cache.get("song") {calls.incrementAndGet();"bad"})}
        assertEquals(1,calls.get());println("WARM_1000_CACHE_LOOKUPS_MS=${(System.nanoTime()-before)/1000000}")
    }
    @Test fun failedWorkIsRetryable() {
        val cache=SingleFlightCache<String,String>(8)
        runCatching {cache.get("song") {error("temporary network failure")}}.onSuccess {fail("Expected failure")}
        assertEquals("retry",cache.get("song") {"retry"})
    }
    @Test fun expirationAndQualityKeysAreIndependent() {
        var now=0
        val cache=SingleFlightCache<String,Int>(8) {it>now}
        assertEquals(1,cache.get("song:high") {1});assertEquals(8,cache.get("song:low") {8})
        now=2
        assertEquals(9,cache.get("song:high") {9});assertEquals(8,cache.get("song:low") {error("Already cached")})
    }
    @Test fun inFlightEntrySurvivesEviction() {
        val cache=SingleFlightCache<String,String>(1);val started=CountDownLatch(1);val finish=CountDownLatch(1)
        val pool=Executors.newSingleThreadExecutor()
        try {
            val f=pool.submit(Callable {cache.get("pending") {started.countDown();finish.await();"ready"}})
            assertTrue(started.await(2,TimeUnit.SECONDS));cache.get("second") {"second"};finish.countDown()
            assertEquals("ready",f.get(2,TimeUnit.SECONDS))
            assertEquals("ready",cache.get("pending") {error("In-flight work was evicted")})
        } finally {finish.countDown();pool.shutdownNow()}
    }
    private val id="abcdefghijk"
    private fun html()="""<script>var ytInitialPlayerResponse = {"videoDetails":{"videoId":"abcdefghijk","title":"Album {special}","author":"Artist","lengthSeconds":"600","shortDescription":"00:00 Song One\n05:00 Song Two","thumbnail":{"thumbnails":[{"url":"https://i.ytimg.com/cover.jpg"}]}}};var ytInitialData={"markers":[{"chapterRenderer":{"title":{"simpleText":"Song One"},"timeRangeStartMillis":0}},{"chapterRenderer":{"title":{"runs":[{"text":"Song Two"}]},"timeRangeStartMillis":300000}}]};</script>"""
    @Test fun lightweightPageBuildsAlbumWithoutMediaExtraction() {
        val o=FastMetadata.parse(id,html())!!
        assertEquals(600,o.getInt("duration"));assertEquals("Album {special}",o.getString("title"))
        val t=Track(id,"Album","Artist","Album",600,"","","Music",2026)
        val songs=AlbumDetector.fromMetadata(t,o).tracks
        assertEquals(2,songs.size);assertEquals(300000L,songs[0].endMs);assertEquals(300000L,songs[1].startMs)
    }
    @Test fun malformedOrWrongVideoPageIsRejected() {
        assertNull(FastMetadata.parse("differentid",html()));assertNull(FastMetadata.parse(id,"ytInitialPlayerResponse={oops"))
    }
    @Test fun parserSkipsMisleadingMarker() {
        val o=FastMetadata.parse(id,"ytInitialPlayerResponse={\"fake\":true};"+html())
        assertNotNull(o);assertEquals(id,o!!.getString("id"))
    }
    @Test fun diskMetadataAvoidsSecondNetworkRequestAndDropsSignedUrls() {
        val dir=Files.createTempDirectory("mousike-meta").toFile();val calls=AtomicInteger()
        val http=OkHttpClient.Builder().addInterceptor { chain ->
            calls.incrementAndGet();Thread.sleep(150)
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("OK").body(html().toResponseBody("text/html".toMediaType())).build()
        }.build()
        try {
            val first=FastMetadata(dir,http);val before=System.nanoTime();val o=first.fetch(id)!!
            val cold=(System.nanoTime()-before)/1000000
            o.put("url","https://audio.example/signed").put("formats",listOf("secret"));first.remember(id,o)
            val warmBefore=System.nanoTime();val warm=FastMetadata(dir,http).fetch(id)!!
            val warmMs=(System.nanoTime()-warmBefore)/1000000
            assertEquals(1,calls.get());assertFalse(warm.has("url"));assertFalse(warm.has("formats"))
            println("METADATA_CONTROLLED_COLD_MS=$cold;DISK_HIT_MS=$warmMs;NETWORK_REQUESTS=${calls.get()}")
        } finally {dir.deleteRecursively()}
    }
}
