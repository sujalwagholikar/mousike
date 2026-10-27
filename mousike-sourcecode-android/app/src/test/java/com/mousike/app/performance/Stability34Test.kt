package com.mousike.app.performance
import com.mousike.app.playback.*
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.json.JSONObject
import org.junit.Test
import org.junit.Assert.*
import java.util.concurrent.*
import java.util.concurrent.atomic.*

class Stability34Test {
    private val mp4=byteArrayOf(0,0,0,24)+"ftypM4A ".toByteArray()+ByteArray(100)
    private fun stream(name: String,hls: Boolean=false)=YtDlpExtractor.Stream("https://media.test/$name",emptyMap(),Long.MAX_VALUE,hls,true)
    @Test fun htmlIsNotAcceptedJustBecauseStatusIsSuccessfulOrMimeSaysAudio() {
        for(mime in listOf("text/html","audio/mp4","application/octet-stream")) {
            try {StreamVerifier.validate(false,mime,"<!doctype html>Blocked".toByteArray());fail("HTML must fail")}
            catch(_: java.io.IOException){}
        }
    }
    @Test fun mediaContainerAndHlsSignaturesAreValidated() {
        StreamVerifier.validate(false,"application/octet-stream",mp4)
        StreamVerifier.validate(true,"application/vnd.apple.mpegurl","#EXTM3U\n#EXT-X-VERSION:3".toByteArray())
        try {StreamVerifier.validate(true,"audio/mpeg","garbage".toByteArray());fail("Invalid manifest")}
        catch(_: java.io.IOException){}
    }
    @Test fun badDirectAddressCannotWinOverActualPlaylist() {
        val http=OkHttpClient.Builder().addInterceptor {chain ->
            val hls=chain.request().url.encodedPath.contains("hls")
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                .body((if(hls)"#EXTM3U\n#EXT-X-VERSION:3\nsegment.ts" else "<html>Unavailable</html>").toResponseBody((if(hls)"application/vnd.apple.mpegurl" else "audio/mp4").toMediaType())).build()
        }.build()
        val rejected=java.util.concurrent.ConcurrentLinkedQueue<String>()
        val chosen=StreamVerifier(http).select(listOf(stream("bad"),stream("hls",true)),ResolutionSignal()){rejected.add(it.url)}
        assertTrue(chosen.isHls);assertTrue(rejected.any {it.endsWith("bad")})
    }
    @Test fun slowDirectDoesNotHoldBackResponsiveAlternative() {
        val cancelled=AtomicBoolean()
        val http=OkHttpClient.Builder().addInterceptor {chain ->
            if(chain.request().url.encodedPath=="/slow") {
                try {Thread.sleep(5000)}catch(_: InterruptedException){cancelled.set(true);throw java.io.IOException("cancelled")}
            }
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("OK").body(mp4.toResponseBody("audio/mp4".toMediaType())).build()
        }.build()
        val before=System.nanoTime()
        assertTrue(StreamVerifier(http).select(listOf(stream("slow"),stream("fast")),ResolutionSignal()).url.endsWith("fast"))
        assertTrue(TimeUnit.NANOSECONDS.toMillis(System.nanoTime()-before)<1500)
    }
    @Test fun segmentedOtfCannotEnterProgressivePlayer() {
        val root=JSONObject("""{"playabilityStatus":{"status":"OK"},"videoDetails":{"videoId":"abcdefghijk"},"streamingData":{"adaptiveFormats":[{"mimeType":"audio/mp4","url":"https://media.test/otf","type":"FORMAT_STREAM_TYPE_OTF"}]}}""")
        assertNull(NativeAudioResolver.parse("abcdefghijk",root,"High"))
        root.getJSONObject("streamingData").put("hlsManifestUrl","https://media.test/hls.m3u8")
        assertTrue(NativeAudioResolver.parse("abcdefghijk",root,"High")!!.stream.isHls)
    }
    @Test fun stableAacDefaultTrackIsPreferredOverAlternateAndHigherBitrateWebm() {
        val root=JSONObject("""{"playabilityStatus":{"status":"OK"},"videoDetails":{"videoId":"abcdefghijk"},"streamingData":{"adaptiveFormats":[{"itag":140,"mimeType":"audio/mp4","bitrate":128000,"url":"https://media.test/default"},{"itag":251,"mimeType":"audio/webm","bitrate":160000,"url":"https://media.test/webm"},{"itag":141,"mimeType":"audio/mp4","bitrate":256000,"url":"https://media.test/alternate","audioTrack":{"audioIsDefault":false}}]}}""")
        assertTrue(NativeAudioResolver.parse("abcdefghijk",root,"High")!!.stream.url.endsWith("default"))
    }
    @Test fun tapPromotesInFlightPreparationInsteadOfStartingAgain() {
        val pool=ConnectionWork<String,String>();val entered=CountDownLatch(1);val release=CountDownLatch(1);val count=AtomicInteger();val published=AtomicInteger()
        pool.prepare("song",{count.incrementAndGet();entered.countDown();release.await();"ready"},{published.incrementAndGet()})
        assertTrue(entered.await(1,TimeUnit.SECONDS))
        val t=Thread {Thread.sleep(80);release.countDown()};t.start()
        assertEquals("ready",pool.get("song",ResolutionSignal(),1000){count.incrementAndGet();"restarted"})
        assertEquals(1,count.get());assertEquals(0,published.get())
    }
    @Test fun cancelledSelectionCancelsItsHttpWork() {
        val pool=ConnectionWork<String,String>();val signal=ResolutionSignal();val entered=CountDownLatch(1);val stopped=CountDownLatch(1)
        val executor=Executors.newSingleThreadExecutor()
        val task=executor.submit<String> {pool.get("song",signal,5000){s->s.onCancel {stopped.countDown()};entered.countDown();while(true){s.check();Thread.sleep(10)};@Suppress("UNREACHABLE_CODE") "unused"}}
        assertTrue(entered.await(1,TimeUnit.SECONDS));signal.cancel()
        assertTrue(stopped.await(1,TimeUnit.SECONDS));runCatching {task.get(1,TimeUnit.SECONDS)};executor.shutdownNow()
    }
    @Test fun preparationForAnotherSongIsCancelledOnSelection() {
        val pool=ConnectionWork<String,String>();val entered=CountDownLatch(1);val stopped=CountDownLatch(1)
        pool.prepare("old",{s->s.onCancel {stopped.countDown()};entered.countDown();while(true){s.check();Thread.sleep(10)};@Suppress("UNREACHABLE_CODE") "old"},{})
        assertTrue(entered.await(1,TimeUnit.SECONDS));assertEquals("new",pool.get("new",ResolutionSignal(),500){"new"})
        assertTrue(stopped.await(1,TimeUnit.SECONDS))
    }
    @Test fun selectedDeadlineCancelsUnresponsivePreparation() {
        val pool=ConnectionWork<String,String>();val stopped=CountDownLatch(1)
        try {pool.get("slow",ResolutionSignal(),80){s->s.onCancel {stopped.countDown()};while(true){s.check();Thread.sleep(10)};@Suppress("UNREACHABLE_CODE") "none"};fail("Must time out")}
        catch(_: java.io.IOException){assertTrue(stopped.await(1,TimeUnit.SECONDS))}
    }
    @Test fun frozenPlayingClockIsDetectedButPauseAndSeekAreNot() {
        val guard=PlaybackProgress()
        assertFalse(guard.stalled(100,true,0,1000))
        assertFalse(guard.stalled(200,true,800,1000))
        assertTrue(guard.stalled(200,true,1900,1000))
        assertFalse(guard.stalled(200,false,4000,1000))
        assertFalse(guard.stalled(20,true,5000,1000))
    }
    @Test fun audioTrackAndDrcVariantsHaveDifferentCacheIdentities() {
        val root=JSONObject("""{"playabilityStatus":{"status":"OK"},"videoDetails":{"videoId":"abcdefghijk"},"streamingData":{"adaptiveFormats":[{"itag":140,"mimeType":"audio/mp4","url":"https://media.test/audio","audioTrack":{"id":"en.1"}}]}}""")
        val plain=NativeAudioResolver.parse("abcdefghijk",root,"High")!!.stream.format
        root.getJSONObject("streamingData").getJSONArray("adaptiveFormats").getJSONObject(0).put("isDrc",true)
        val drc=NativeAudioResolver.parse("abcdefghijk",root,"High")!!.stream.format
        assertNotEquals(plain,drc)
    }
    @Test fun failedSpeculationDoesNotPoisonNextSelectedRequest() {
        val pool=ConnectionWork<String,String>();val failed=CountDownLatch(1)
        pool.prepare("song",{failed.countDown();throw java.io.IOException("failed")},{})
        assertTrue(failed.await(1,TimeUnit.SECONDS));Thread.sleep(40)
        assertEquals("fresh",pool.get("song",ResolutionSignal(),500){"fresh"})
    }
}
