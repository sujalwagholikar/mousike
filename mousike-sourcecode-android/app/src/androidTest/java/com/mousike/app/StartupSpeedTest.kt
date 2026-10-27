package com.mousike.app

import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.util.Log
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.lifecycle.ViewModelProvider
import com.mousike.app.data.catalog.MusicCatalog
import com.mousike.app.playback.AudioPlayerEngine
import com.mousike.app.settings.AppSettings
import com.mousike.app.settings.Preferences
import com.mousike.app.ui.viewmodel.MousikeViewModel
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collect
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.atomic.AtomicInteger

@RunWith(AndroidJUnit4::class)
class StartupSpeedTest {
    private fun waitFor(timeout: Long=15000,predicate: ()->Boolean) {
        val until=SystemClock.elapsedRealtime()+timeout
        while(!predicate()) {assertTrue("Timed out waiting for playback",SystemClock.elapsedRealtime()<until);Thread.sleep(20)}
    }
    @Test fun resetCancelsPendingQueueWrite() {
        val context=ApplicationProvider.getApplicationContext<Context>()
        AppSettings.get(context).update {Preferences(onboarded=true,autoplay=false)}
        val scenario=ActivityScenario.launch<MainActivity>(Intent(context,MainActivity::class.java).putExtra("offline_fixture",true))
        val engine=AudioPlayerEngine.getInstance(context)
        try {
            waitFor {MusicCatalog.getTrackById("demo-track-0")!=null}
            scenario.onActivity {
                engine.playTrack(MusicCatalog.getTrackById("demo-track-0")!!)
                engine.reset()
            }
            Thread.sleep(700)
            assertEquals("[]",context.getSharedPreferences("mousike_queue",Context.MODE_PRIVATE).getString("tracks","[]"))
            assertTrue(engine.playbackState.value.queue.isEmpty())
        } finally {scenario.close()}
    }
    @Test fun localStartupAndBrowsingUpdates() {
        val context=ApplicationProvider.getApplicationContext<Context>()
        AppSettings.get(context).update {Preferences(onboarded=true,autoplay=false)}
        val scenario=ActivityScenario.launch<MainActivity>(Intent(context,MainActivity::class.java).putExtra("offline_fixture",true))
        val engine=AudioPlayerEngine.getInstance(context)
        try {
            waitFor {MusicCatalog.getTrackById("demo-track-0")!=null}
            val samples=mutableListOf<Long>()
            repeat(6) {i ->
                scenario.onActivity {engine.pause()};waitFor { !engine.playbackState.value.isPlaying }
                val t=MusicCatalog.getTrackById("demo-track-${i%3}")!!
                val start=SystemClock.elapsedRealtime()
                scenario.onActivity {activity -> ViewModelProvider(activity)[MousikeViewModel::class.java].playTrack(t,listOf(t))
                    assertEquals(t.id,engine.playbackState.value.currentTrack?.id)
                }
                waitFor {engine.playbackState.value.isPlaying}
                samples+=SystemClock.elapsedRealtime()-start
            }
            val browse=AtomicInteger();val full=AtomicInteger()
            val scope=CoroutineScope(SupervisorJob()+Dispatchers.Main.immediate)
            scenario.onActivity {a -> val vm=ViewModelProvider(a)[MousikeViewModel::class.java]
                scope.launch {vm.browsingPlaybackState.collect {browse.incrementAndGet()}}
                scope.launch {vm.playbackState.collect {full.incrementAndGet()}}
            }
            Thread.sleep(600);val b=browse.get();val f=full.get();Thread.sleep(2100)
            val browsing=browse.get()-b;val progress=full.get()-f
            scope.cancel()
            assertTrue("Progress should continue updating",progress>=3)
            assertTrue("Browsing pages should ignore progress ticks",browsing<progress)
            val line="MOUSIKE_SPEED coldLocalMs=${samples.first()} warmMedianLocalMs=${samples.drop(1).sorted()[2]} samples=$samples browseEmissions=$browsing fullEmissions=$progress"
            Log.i("MousikeSpeed",line);println(line)
        } finally {scenario.onActivity {engine.pause()};scenario.close()}
    }
}
