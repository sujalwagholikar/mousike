package com.mousike.app.performance

import com.mousike.app.playback.*
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

class OneTapRecoveryTest {
    private val player="""{"playabilityStatus":{"status":"OK"},"videoDetails":{"videoId":"abcdefghijk","title":"Song","lengthSeconds":"200"},"streamingData":{"hlsManifestUrl":"https://audio.example/master.m3u8"}}"""
    @Test fun visitorSessionIsEstablishedAndSentBeforePlayerRequest() {
        val routes=mutableListOf<String>();val store=MemoryVisitorStore()
        val http=OkHttpClient.Builder().addInterceptor {chain ->
            val r=chain.request();routes+=r.url.encodedPath
            val output=if(r.url.encodedPath.endsWith("visitor_id"))"""{"responseContext":{"visitorData":"real-session"}}""" else {
                val buffer=Buffer();r.body!!.writeTo(buffer);val body=JSONObject(buffer.readUtf8())
                assertEquals("real-session",body.getJSONObject("context").getJSONObject("client").getString("visitorData"))
                assertEquals("real-session",r.header("X-Goog-Visitor-Id"));assertEquals("abcdefghijk",r.url.queryParameter("id"));assertEquals(12,r.url.queryParameter("t")!!.length);assertEquals(16,body.getString("cpn").length);player
            }
            Response.Builder().request(r).protocol(Protocol.HTTP_1_1).code(200).message("OK").body(output.toResponseBody("application/json".toMediaType())).build()
        }.build()
        val result=NativeAudioResolver(http,"https://test.example/",store).resolve("abcdefghijk","High",ResolutionSignal())
        assertNotNull(result);assertTrue(result!!.stream.isHls)
        assertEquals(listOf("/visitor_id","/player"),routes);assertEquals("real-session",store.get())
    }
    @Test fun warmVisitorSessionNeedsOnlyOnePlayerRequest() {
        val calls=AtomicInteger();val store=MemoryVisitorStore().apply {put("cached-session")}
        val http=OkHttpClient.Builder().addInterceptor {chain ->
            calls.incrementAndGet();assertEquals("/player",chain.request().url.encodedPath)
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("OK").body(player.toResponseBody("application/json".toMediaType())).build()
        }.build()
        assertNotNull(NativeAudioResolver(http,"https://test.example/",store).resolve("abcdefghijk","High",ResolutionSignal()))
        assertEquals(1,calls.get())
    }
    @Test fun nativeSuccessDoesNotStartCompatibilityRuntime() {
        val fallback=AtomicBoolean()
        assertEquals("native",ForegroundResolver.resolve(ResolutionSignal(),delayMs=1000,budgetMs=2000,primary={"native"},fallback={fallback.set(true);"slow"}))
        Thread.sleep(40);assertFalse(fallback.get())
    }
    @Test fun failedPrimaryStartsRecoveryWithoutAnotherUserTapOrDelay() {
        val before=System.nanoTime()
        val result=ForegroundResolver.resolve(ResolutionSignal(),delayMs=10000,budgetMs=2000,primary={throw IOException("unavailable")},fallback={"recovered"})
        assertEquals("recovered",result);assertTrue((System.nanoTime()-before)/1000000<1500)
    }
    @Test fun winnerCancelsOtherRoute() {
        val cancelled=CountDownLatch(1)
        val result=ForegroundResolver.resolve(ResolutionSignal(),delayMs=30,budgetMs=2000,primary={s ->
            s.onCancel {cancelled.countDown()};while(true){s.check();Thread.sleep(10)}
            @Suppress("UNREACHABLE_CODE") "unused"
        },fallback={"working"})
        assertEquals("working",result);assertTrue(cancelled.await(1,TimeUnit.SECONDS))
    }
    @Test fun overallDeadlineStopsBothRoutes() {
        val a=AtomicBoolean();val b=AtomicBoolean()
        fun slow(s: ResolutionSignal,flag: AtomicBoolean): String {s.onCancel {flag.set(true)};while(true){s.check();Thread.sleep(5)}}
        try {ForegroundResolver.resolve(ResolutionSignal(),delayMs=0,budgetMs=100,primary={slow(it,a)},fallback={slow(it,b)});fail("Expected timeout")}
        catch(_: IOException) {assertTrue(a.get());assertTrue(b.get())}
    }
}
