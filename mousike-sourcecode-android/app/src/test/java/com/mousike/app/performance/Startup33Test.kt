package com.mousike.app.performance

import com.mousike.app.playback.*
import com.mousike.app.data.remote.YouTubeMusicClient
import com.mousike.app.ui.RefreshRateController
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import kotlinx.coroutines.*
import org.junit.Test
import org.junit.Assert.*
import java.util.concurrent.*
import java.util.concurrent.atomic.AtomicReference

@org.junit.runner.RunWith(org.robolectric.RobolectricTestRunner::class)
@org.robolectric.annotation.Config(sdk=[28],manifest=org.robolectric.annotation.Config.NONE)
class Startup33Test {
    private fun resolver(direct: String)=NativeAudioResolver(OkHttpClient.Builder().addInterceptor {chain ->
        val json="""{"playabilityStatus":{"status":"OK"},"videoDetails":{"videoId":"abcdefghijk","title":"Song","lengthSeconds":"200"},"streamingData":{"hlsManifestUrl":"https://audio.test/master.m3u8","adaptiveFormats":[{"mimeType":"audio/mp4","bitrate":128000,"url":"$direct"}]}}"""
        Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("OK").body(json.toResponseBody("application/json".toMediaType())).build()
    }.build(),"https://test.example/",MemoryVisitorStore().apply {put("warm-session")})
    @Test fun directAudioAvoidsManifestRoundTripsAndRetainsHlsRecovery() {
        val result=resolver("https://audio.test/song.m4a").resolve("abcdefghijk","High",ResolutionSignal())!!
        assertTrue(result.stream.url.startsWith("https://audio.test/song.m4a?cpn="));assertEquals(16,result.stream.url.substringAfter("cpn=").length);assertFalse(result.stream.isHls)
        assertTrue(result.alternate!!.isHls);assertEquals("https://audio.test/master.m3u8",result.alternate!!.url)
    }
    @Test fun transformedDirectUrlNeverOverridesPlayableHls() {
        val result=resolver("https://audio.test/song.m4a?n=unsolved").resolve("abcdefghijk","High",ResolutionSignal())!!
        assertTrue(result.stream.isHls);assertNull(result.alternate)
    }
    @Test fun differentAudioFormatsUseSeparateByteCaches() {
        val original=androidx.media3.common.MediaItem.Builder().setMediaId("abcdefghijk").setUri("mousike://video/abcdefghijk").setCustomCacheKey("audio:High:abcdefghijk").build()
        val a=StreamingMediaSource.resolvedItem(original,YtDlpExtractor.Stream("https://audio.test/a",emptyMap(),Long.MAX_VALUE,format="140"))
        val b=StreamingMediaSource.resolvedItem(original,YtDlpExtractor.Stream("https://audio.test/b",emptyMap(),Long.MAX_VALUE,format="251"))
        assertNotEquals(a.localConfiguration!!.customCacheKey,b.localConfiguration!!.customCacheKey)
    }
    @Test fun preparedNativeConnectionIsReusedWithoutAnotherPlayerRequest() {
        val context=androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
        com.mousike.app.RuntimeMode.offlineFixture=false
        com.mousike.app.settings.AppSettings.get(context).update {it.copy(preloadNext=true)}
        val count=java.util.concurrent.atomic.AtomicInteger()
        val http=OkHttpClient.Builder().addInterceptor {chain ->
            count.incrementAndGet()
            val id=chain.request().url.queryParameter("id")
            val json="""{"playabilityStatus":{"status":"OK"},"videoDetails":{"videoId":"$id","title":"Warm song","lengthSeconds":"200"},"streamingData":{"adaptiveFormats":[{"itag":140,"mimeType":"audio/mp4","bitrate":128000,"url":"https://audio.test/warm.m4a"}]}}"""
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("OK").body(json.toResponseBody("application/json".toMediaType())).build()
        }.build()
        val extractor=YtDlpExtractor.get(context)
        val bytes=byteArrayOf(0,0,0,24)+"ftypM4A ".toByteArray()+ByteArray(100)
        val probe=OkHttpClient.Builder().addInterceptor {chain->Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("OK").body(bytes.toResponseBody("audio/mp4".toMediaType())).build()}.build()
        org.robolectric.util.ReflectionHelpers.setField(extractor,"verifier",StreamVerifier(probe))
        org.robolectric.util.ReflectionHelpers.setField(extractor,"native",NativeAudioResolver(http,"https://test.example/",MemoryVisitorStore().apply {put("cached-session")}))
        val track=com.mousike.app.data.model.Track("warmqueue01","Warm song","Artist","",200,"","","Indie",2026)
        extractor.prefetch(listOf(track))
        val deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(3)
        while(!extractor.hasCachedStream(track.id,"High") && System.nanoTime()<deadline)Thread.sleep(10)
        assertTrue(extractor.hasCachedStream(track.id,"High"))
        assertTrue(extractor.resolve(track.id,quality="High").url.startsWith("https://audio.test/warm.m4a?cpn="))
        assertEquals(1,count.get())
    }
    @Test fun autoRefreshLeavesPlatformInControl() {assertNull(RefreshRateController.choose(listOf(60f,120f),0))}
    @Test fun refreshRateNeverInventsUnsupportedMode() {
        assertEquals(90f,RefreshRateController.choose(listOf(60f,90f),120))
        assertEquals(60f,RefreshRateController.choose(listOf(60f,120f),90))
        assertEquals(119.88f,RefreshRateController.choose(listOf(60f,119.88f),120))
    }
    @Test fun cancellingOldSearchCancelsItsHttpCall()=runBlocking {
        val started=CountDownLatch(1);val active=AtomicReference<Call>()
        val http=OkHttpClient.Builder().addInterceptor {chain ->
            active.set(chain.call());started.countDown()
            while(!chain.call().isCanceled())Thread.sleep(5)
            throw java.io.IOException("Cancelled")
        }.build()
        val job=launch(Dispatchers.Default){YouTubeMusicClient(http).search("old search")}
        assertTrue(withContext(Dispatchers.IO){started.await(3,TimeUnit.SECONDS)})
        withTimeout(1000){job.cancelAndJoin()};assertTrue(active.get().isCanceled())
    }
}
