package com.mousike.app

import android.content.Context
import android.os.SystemClock
import androidx.media3.common.*
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mousike.app.playback.*
import com.mousike.app.performance.BoundedResolution
import com.mousike.app.data.model.Track
import com.mousike.app.settings.AppSettings
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@RunWith(AndroidJUnit4::class)
class PlaybackPipelineTest {
    private val context get()=ApplicationProvider.getApplicationContext<Context>()
    private fun main(action: ()->Unit)=InstrumentationRegistry.getInstrumentation().runOnMainSync(action)
    private fun waitUntil(ms: Long=10000,predicate: ()->Boolean) {
        val deadline=SystemClock.elapsedRealtime()+ms
        while(!predicate()) {assertTrue("Playback condition timed out",SystemClock.elapsedRealtime()<deadline);Thread.sleep(20)}
    }
    private fun virtual(id: String)=MediaItem.Builder().setMediaId(id).setUri("mousike://video/$id").setCustomCacheKey("audio:High:$id").build()
    @Test fun hlsVirtualClipAndQueueTransitionUseCorrectParser() {
        lateinit var player: ExoPlayer
        val first="abcdefghijk";val next="lmnopqrstuv"
        main {
            val local=DefaultMediaSourceFactory(DefaultDataSource.Factory(context))
            val factory=StreamingMediaSourceFactory(local,{id,_,_ ->
                if(id==first)YtDlpExtractor.Stream("asset:///playback-check/audio.m3u8",emptyMap(),Long.MAX_VALUE,true)
                else YtDlpExtractor.Stream("asset:///demo/soundcheck-1.wav",emptyMap(),Long.MAX_VALUE)
            },{local})
            player=ExoPlayer.Builder(context).setMediaSourceFactory(factory).build()
            val clipped=virtual(first).buildUpon().setClippingConfiguration(MediaItem.ClippingConfiguration.Builder().setStartPositionMs(1000).setEndPositionMs(4000).build()).build()
            player.setMediaItems(listOf(clipped,virtual(next)));player.prepare();player.play()
        }
        try {
            waitUntil {var ready=false;main {ready=player.isPlaying};ready}
            main {assertEquals(3000L,player.duration);player.seekTo(1000)}
            main {player.seekToNextMediaItem()}
            waitUntil {var ready=false;main {ready=player.currentMediaItem?.mediaId==next && player.isPlaying};ready}
        } finally {main {player.release()}}
    }
    @Test fun replacingSongCancelsPreviousResolver() {
        lateinit var player: ExoPlayer
        val entered=CountDownLatch(1);val cancelled=AtomicBoolean()
        main {
            val local=DefaultMediaSourceFactory(DefaultDataSource.Factory(context))
            val factory=StreamingMediaSourceFactory(local,{id,_,signal ->
                if(id=="abcdefghijk") {
                    signal.onCancel {cancelled.set(true)};entered.countDown()
                    while(true){signal.check();Thread.sleep(20)}
                    @Suppress("UNREACHABLE_CODE") YtDlpExtractor.Stream("",emptyMap(),0)
                } else YtDlpExtractor.Stream("asset:///demo/soundcheck-1.wav",emptyMap(),Long.MAX_VALUE)
            },{local})
            player=ExoPlayer.Builder(context).setMediaSourceFactory(factory).build()
            player.setMediaItem(virtual("abcdefghijk"));player.prepare();player.play()
        }
        try {
            assertTrue(entered.await(3,TimeUnit.SECONDS))
            main {player.setMediaItem(virtual("lmnopqrstuv"));player.prepare();player.play()}
            waitUntil(2000){cancelled.get()}
            waitUntil {var ready=false;main {ready=player.isPlaying && player.currentMediaItem?.mediaId=="lmnopqrstuv"};ready}
        } finally {main {player.release()}}
    }
    @Test fun timedOutResolverReportsErrorInsteadOfBufferingForever() {
        lateinit var player: ExoPlayer;val error=CountDownLatch(1)
        main {
            val local=DefaultMediaSourceFactory(context)
            val factory=StreamingMediaSourceFactory(local,{_,_,signal ->
                BoundedResolution.run(150,signal) {Thread.sleep(20000);YtDlpExtractor.Stream("",emptyMap(),0)}
            },{local})
            player=ExoPlayer.Builder(context).setMediaSourceFactory(factory).build()
            player.addListener(object: Player.Listener {override fun onPlayerError(e: PlaybackException){error.countDown()}})
            player.setMediaItem(virtual("abcdefghijk"));player.prepare();player.play()
        }
        try {assertTrue("Resolution error must reach the player",error.await(5,TimeUnit.SECONDS));main {assertEquals(Player.STATE_IDLE,player.playbackState)}}
        finally {main {player.release()}}
    }
    @Test fun actualEnginePlaysAssetClipWithoutPythonOrNetworkCache() {
        lateinit var engine: AudioPlayerEngine
        AppSettings.get(context).update {it.copy(autoplay=false)}
        val clip=Track("check-clip","Local check","Mousike","",3,"asset:///demo/soundcheck-0.wav","","Music",2026,startMs=1000,endMs=4000)
        val start=SystemClock.elapsedRealtime()
        main {engine=AudioPlayerEngine.getInstance(context);engine.playTrack(clip)}
        try {
            waitUntil {engine.playbackState.value.isPlaying}
            main {assertEquals(3000L,engine.playbackState.value.durationMs);engine.seekTo(1000)}
            android.util.Log.i("MousikeSpeed","V311_LOCAL_CLIP_START_MS=${SystemClock.elapsedRealtime()-start}")
        } finally {main {engine.pause()}}
    }
    @Test fun nextAfterFailedSourceStartsWithoutAnotherPlayTap() {
        lateinit var engine: AudioPlayerEngine
        val bad=Track("bad-file","Bad source","Mousike","",30,"asset:///missing.wav","","Music",2026)
        val good=bad.copy(id="good-file",title="Good source",streamUrl="asset:///demo/soundcheck-1.wav")
        main {engine=AudioPlayerEngine.getInstance(context);engine.playTrack(bad,listOf(bad,good))}
        try {
            waitUntil {engine.playbackState.value.error!=null}
            main {engine.playNext()}
            waitUntil {engine.playbackState.value.currentTrack?.id==good.id && engine.playbackState.value.isPlaying}
            assertNull(engine.playbackState.value.error)
        } finally {main {engine.reset()}}
    }
    @Test fun actualEngineWatchdogClearsIndefiniteLoading() {
        lateinit var engine: AudioPlayerEngine
        val cancelled=AtomicBoolean();val id="abcdefghijk"
        val track=Track(id,"Stall check","Mousike","",200,"asset:///demo/soundcheck-0.wav","","Music",2026)
        main {
            engine=AudioPlayerEngine.getInstance(context);engine.startupTimeoutMs=1500;engine.playTrack(track)
            val local=DefaultMediaSourceFactory(context)
            val factory=StreamingMediaSourceFactory(local,{_,_,signal ->
                signal.onCancel {cancelled.set(true)}
                while(true){signal.check();Thread.sleep(20)}
                @Suppress("UNREACHABLE_CODE") YtDlpExtractor.Stream("",emptyMap(),0)
            },{local})
            engine.player.setMediaSource(factory.createMediaSource(virtual(id)));engine.player.prepare();engine.player.play()
        }
        try {
            main {repeat(3){engine.togglePlayPause()};assertTrue(engine.player.playWhenReady)}
            waitUntil(6000){engine.playbackState.value.error!=null}
            assertFalse(engine.playbackState.value.isBuffering)
            main {assertFalse(engine.player.playWhenReady)}
            waitUntil(2000){cancelled.get()}
        } finally {main {engine.reset();engine.startupTimeoutMs=30000}}
    }
    // Mid-playback recovery is exercised with actual stalled HTTPS audio in SustainedPlaybackTest.
}
