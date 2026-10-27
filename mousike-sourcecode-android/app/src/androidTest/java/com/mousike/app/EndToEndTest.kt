package com.mousike.app

import android.content.Context
import android.content.Intent
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import com.mousike.app.data.catalog.*
import com.mousike.app.data.model.Track
import com.mousike.app.playback.*
import com.mousike.app.settings.*
import com.mousike.app.download.*
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class EndToEndTest {
 @get:Rule val compose=createEmptyComposeRule()
 private val context get()=ApplicationProvider.getApplicationContext<Context>()
 private lateinit var scenario: ActivityScenario<MainActivity>
 @Before fun start(){
  AppSettings.get(context).update { Preferences(onboarded=false,autoplay=false,wifiDownloads=false) }
  scenario=ActivityScenario.launch(Intent(context,MainActivity::class.java).putExtra("offline_fixture",true))
 }
 @After fun finish(){scenario.onActivity { AudioPlayerEngine.getInstance(it).pause() };scenario.close()}
 private fun skip(){compose.onNodeWithTag("onboarding_skip").performClick();compose.waitUntil(15000){compose.onAllNodesWithTag("home_settings_btn").fetchSemanticsNodes().isNotEmpty()}}
 @Test fun onboardingSearchPlayLikeQueueAndBackground(){
  compose.onNodeWithTag("onboarding_name").performTextInput("Sujal")
  repeat(3){compose.onNodeWithTag("onboarding_continue").performScrollTo().performClick()}
  compose.waitUntil(15000){compose.onAllNodesWithTag("nav_search").fetchSemanticsNodes().isNotEmpty()}
  compose.onNodeWithTag("nav_search").performClick();compose.onNodeWithTag("search_text_input").performTextInput("Dusk Geometry")
  compose.waitUntil(10000){compose.onAllNodesWithTag("track_item_demo-track-0").fetchSemanticsNodes().isNotEmpty()}
  compose.onNodeWithTag("track_item_demo-track-0").performClick()
  val engine=AudioPlayerEngine.getInstance(context)
  compose.waitUntil(15000){engine.playbackState.value.isPlaying}
  compose.onNodeWithTag("mini_player_like").performClick()
  compose.onNodeWithTag("mini_player").performClick();compose.onNodeWithTag("full_player_screen").assertExists()
  scenario.onActivity {engine.addToQueue(MusicCatalog.getTrackById("demo-track-1")!!);engine.seekTo(5000);engine.pause()}
  compose.waitUntil(5000){!engine.playbackState.value.isPlaying}
  scenario.onActivity {engine.resume()};compose.waitUntil(5000){engine.playbackState.value.isPlaying}
  val before=engine.playbackState.value.currentPositionMs
  scenario.moveToState(androidx.lifecycle.Lifecycle.State.CREATED)
  Thread.sleep(1800)
  assertTrue(engine.playbackState.value.isPlaying);assertTrue(engine.playbackState.value.currentPositionMs>before)
  scenario.moveToState(androidx.lifecycle.Lifecycle.State.RESUMED)
  scenario.onActivity {engine.playNext()}
  compose.waitUntil(8000){engine.playbackState.value.currentTrack?.id=="demo-track-1" && engine.playbackState.value.isPlaying}
  scenario.onActivity {engine.moveQueue(1,0)}
  assertEquals("demo-track-1",engine.playbackState.value.queue.first().id)
 }
 @Test fun albumOpensTrackListAndThemePersists(){
  skip()
  compose.onNodeWithTag("home_list").performScrollToNode(hasText("Sound In motion"))
  // Move the album above the floating player before a physical tap.
  compose.onNodeWithTag("home_list").performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.ScrollBy) { it(0f,240f) }
  compose.onNodeWithText("Sound In motion",useUnmergedTree=true).performClick()
  compose.waitUntil(15000) { compose.onAllNodesWithTag("album_play_all").fetchSemanticsNodes().isNotEmpty() }
  compose.onNodeWithTag("album_play_all").assertExists()
  compose.onNodeWithTag("track_item_demo-track-0").assertExists()
  compose.onNodeWithTag("album_play_all").performClick()
  compose.waitUntil(15000){AudioPlayerEngine.getInstance(context).playbackState.value.isPlaying}
  scenario.onActivity {AppSettings.get(it).update {p->p.copy(theme="Light",accent="Lavender")}}
  scenario.recreate()
  assertEquals("Light",AppSettings.get(context).state.value.theme);assertEquals("Lavender",AppSettings.get(context).state.value.accent)
 }
 @Test fun downloadThenPlayOfflineFile(){
  skip();val track=MusicCatalog.getTrackById("demo-track-2")!!
  AppSettings.get(context).update {it.copy(wifiDownloads=false)}
  DownloadStore.get(context).enqueue(track)
  compose.waitUntil(30000){DownloadStore.get(context).completedFile(track.id)!=null}
  assertTrue(DownloadStore.get(context).completedFile(track.id)!!.length()>0)
  scenario.onActivity {AudioPlayerEngine.getInstance(it).playTrack(track,listOf(track))}
  compose.waitUntil(10000){AudioPlayerEngine.getInstance(context).playbackState.value.isPlaying}
  scenario.onActivity { assertEquals("file",AudioPlayerEngine.getInstance(it).player.currentMediaItem!!.localConfiguration!!.uri.scheme) }
 }
 @Test fun bundledPythonQuickJsAndFfmpegCanExecute(){
  skip()
  val native=context.applicationInfo.nativeLibraryDir
  listOf("libpython.so","libqjs.so","libffmpeg.so","libc++_shared.so").forEach {assertTrue("Missing runtime $it",java.io.File(native,it).isFile)}
  YtDlpExtractor.get(context).ensureFfmpeg()
  val qjs=ProcessBuilder("$native/libqjs.so","-e","print('runtime-ok')").redirectErrorStream(true).start()
  val qjsOut=qjs.inputStream.bufferedReader().readText();assertEquals(qjsOut,0,qjs.waitFor());assertTrue(qjsOut.contains("runtime-ok"))
  val ff=ProcessBuilder("$native/libffmpeg.so","-version").redirectErrorStream(true)
  ff.environment()["LD_LIBRARY_PATH"]="${context.noBackupFilesDir}/youtubedl-android/packages/python/usr/lib:${context.noBackupFilesDir}/youtubedl-android/packages/ffmpeg/usr/lib:$native"
  val proc=ff.start();val ffOut=proc.inputStream.bufferedReader().readText();assertEquals(ffOut,0,proc.waitFor());assertTrue(ffOut.contains("ffmpeg version"))
  val request=com.yausername.youtubedl_android.YoutubeDLRequest(emptyList<String>()).apply {addOption("--version")}
  val response=com.yausername.youtubedl_android.YoutubeDL.getInstance().execute(request,"runtime-check",null)
  assertTrue(response.out.trim().matches(Regex(".*\\d{4}.*")))
 }
 @Test fun nativeClippingUsesTrackRelativeTime(){
  skip()
  val original=MusicCatalog.getTrackById("demo-track-0")!!
  val clip=original.copy(id="demo-clip",startMs=10000,endMs=20000,durationSec=10)
  scenario.onActivity {AudioPlayerEngine.getInstance(it).playTrack(clip,listOf(clip))}
  compose.waitUntil(10000){AudioPlayerEngine.getInstance(context).playbackState.value.isPlaying}
  val duration=AudioPlayerEngine.getInstance(context).playbackState.value.durationMs
  assertTrue("Expected a 10-second clip, got $duration",duration in 9800..10200)
  scenario.onActivity {AudioPlayerEngine.getInstance(it).seekTo(3000)}
  compose.waitUntil(5000){AudioPlayerEngine.getInstance(context).playbackState.value.currentPositionMs in 2900..6000}
 }
}
