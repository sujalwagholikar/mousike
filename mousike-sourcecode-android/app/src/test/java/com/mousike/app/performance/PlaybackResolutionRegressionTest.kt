package com.mousike.app.performance

import android.app.Application
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import com.mousike.app.playback.NativeAudioResolver
import com.mousike.app.playback.StreamingMediaSource
import com.mousike.app.playback.YtDlpExtractor
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.InterruptedIOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[30],application=Application::class)
class PlaybackResolutionRegressionTest {
    private val id="abcdefghijk"
    private fun response()=JSONObject("""{"playabilityStatus":{"status":"OK"},"videoDetails":{"videoId":"abcdefghijk","title":"Song","author":"Artist","lengthSeconds":"200"},"streamingData":{"hlsManifestUrl":"https://audio.example/manifest.m3u8","expiresInSeconds":600}}""")
    @Test fun hlsOnlyResponseRemainsPlayableWithoutPython() {
        val result=NativeAudioResolver.parse(id,response(),"High")!!
        assertTrue(result.stream.isHls);assertTrue(result.stream.native);assertEquals("Song",result.metadata.getString("title"))
    }
    @Test fun directAudioQualityIsChosenWithoutVideo() {
        val o=response().apply {getJSONObject("streamingData").put("adaptiveFormats",org.json.JSONArray("""[{"mimeType":"audio/mp4","bitrate":64000,"url":"https://audio.example/low"},{"mimeType":"audio/mp4","bitrate":128000,"url":"https://audio.example/high"},{"mimeType":"video/mp4","bitrate":900000,"url":"https://audio.example/video"}]"""))}
        assertEquals("https://audio.example/high",NativeAudioResolver.parse(id,o,"High")!!.stream.url)
        assertEquals("https://audio.example/low",NativeAudioResolver.parse(id,o,"Data saver")!!.stream.url)
    }
    @Test fun cipheredAudioDoesNotMasqueradeAsReadyAudio() {
        val o=response().apply {getJSONObject("streamingData").remove("hlsManifestUrl");getJSONObject("streamingData").put("adaptiveFormats",org.json.JSONArray("""[{"mimeType":"audio/mp4","url":"https://audio.example/bad?n=needs_deciphering"},{"mimeType":"audio/mp4","signatureCipher":"encrypted"}]"""))}
        assertNull(NativeAudioResolver.parse(id,o,"High"))
    }
    @Test fun restrictedAndMismatchedResponsesAreRejected() {
        assertNull(NativeAudioResolver.parse("differentid",response(),"High"))
        val o=response();o.getJSONObject("playabilityStatus").put("status","LOGIN_REQUIRED")
        assertNull(NativeAudioResolver.parse(id,o,"High"))
    }
    @Test fun hlsParserIsChosenAfterResolvingAndSharedSegmentKeyRemoved() {
        val original=MediaItem.Builder().setMediaId(id).setUri("mousike://video/$id").setCustomCacheKey("audio:High:$id")
            .setClippingConfiguration(MediaItem.ClippingConfiguration.Builder().setStartPositionMs(1000).setEndPositionMs(5000).build()).build()
        val item=StreamingMediaSource.resolvedItem(original,YtDlpExtractor.Stream("https://audio.example/master.m3u8",emptyMap(),Long.MAX_VALUE,true))
        assertEquals(MimeTypes.APPLICATION_M3U8,item.localConfiguration!!.mimeType)
        assertNull(item.localConfiguration!!.customCacheKey);assertEquals(id,item.mediaId)
        assertEquals(1000L,item.clippingConfiguration.startPositionMs);assertEquals(5000L,item.clippingConfiguration.endPositionMs)
    }
    @Test fun progressiveAudioKeepsItsQualitySpecificCacheKey() {
        val original=MediaItem.Builder().setUri("mousike://video/$id").setCustomCacheKey("audio:High:$id").build()
        val item=StreamingMediaSource.resolvedItem(original,YtDlpExtractor.Stream("https://audio.example/file.m4a",emptyMap(),Long.MAX_VALUE))
        assertNull(item.localConfiguration!!.mimeType);assertEquals("audio:High:$id:transport:ranges-v1",item.localConfiguration!!.customCacheKey)
    }
    @Test fun slowExtractionHasRealDeadlineAndCancelsItsProcessHook() {
        val signal=ResolutionSignal();val killed=AtomicBoolean();signal.onCancel {killed.set(true)}
        val start=System.nanoTime()
        try {BoundedResolution.run(80,signal) {Thread.sleep(10000)};fail("Expected timeout")}
        catch(_: InterruptedIOException) {assertTrue(killed.get());assertTrue(signal.isCancelled)}
        assertTrue("Timeout must bound the caller",(System.nanoTime()-start)/1000000<1500)
    }
    @Test fun replacingTrackCancelsBlockedResolutionPromptly() {
        val signal=ResolutionSignal();val entered=CountDownLatch(1);val finished=CountDownLatch(1)
        val t=Thread {try {BoundedResolution.run(10000,signal) {entered.countDown();Thread.sleep(10000)}}catch(_: InterruptedIOException){}finally {finished.countDown()}}
        t.start();assertTrue(entered.await(2,TimeUnit.SECONDS));signal.cancel()
        assertTrue(finished.await(1,TimeUnit.SECONDS))
    }
    @Test fun alreadyCancelledRequestCannotStartNetworkWork() {
        val signal=ResolutionSignal();signal.cancel();val ran=AtomicBoolean()
        try {BoundedResolution.run(100,signal) {ran.set(true)};fail("Expected cancellation")}catch(_: InterruptedIOException){}
        assertFalse(ran.get())
    }
}
