package com.mousike.app

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mousike.app.data.catalog.MusicCatalog
import com.mousike.app.playback.AudioPlayerEngine
import com.mousike.app.settings.*
import com.mousike.app.ui.components.DailySpotlightGate
import com.mousike.app.ui.viewmodel.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.io.File
import java.time.LocalDate

@RunWith(AndroidJUnit4::class)
class Frontend350FlowTest {
    @get:Rule val compose=createEmptyComposeRule()
    private val context get()=ApplicationProvider.getApplicationContext<Context>()
    private lateinit var scenario: ActivityScenario<MainActivity>
    private lateinit var vm: MousikeViewModel
    private val gate get()=context.getSharedPreferences("mousike_feature_spotlight",0)
    @Before fun start() {
        AppSettings.get(context).update {Preferences(onboarded=true,autoplay=false,reduceMotion=true,detectAlbums=false)}
        DailySpotlightGate.shown(gate,LocalDate.now())
        scenario=ActivityScenario.launch(Intent(context,MainActivity::class.java).putExtra("offline_fixture",true))
        scenario.onActivity {activity ->
            vm=ViewModelProvider(activity)[MousikeViewModel::class.java];AudioPlayerEngine.getInstance(activity).reset()
            val t=MusicCatalog.getTrackById("demo-track-0")!!
            MusicCatalog.putTracks(listOf(t.copy(id="frontend-english",artist="English Test Artist",title="English Sound Check",language="English",genre="Pop"),
                t.copy(id="frontend-hindi",artist="Hindi Test Artist",title="Hindi Sound Check",language="Hindi",genre="Bollywood")))
            vm.loadRecommendations()
        }
        compose.waitUntil(15000){vm.recommendations.value.madeForYou.isNotEmpty() && compose.onAllNodesWithTag("nav_home").fetchSemanticsNodes().isNotEmpty()}
    }
    @After fun close(){scenario.onActivity {AudioPlayerEngine.getInstance(it).reset()};scenario.close()}
    private fun snapshot(name: String){(if(name.startsWith("spotlight"))compose.onNodeWithTag("daily_spotlight") else compose.onRoot()).captureToImage().asAndroidBitmap().let {image->File(context.externalCacheDir,name).outputStream().use {image.compress(Bitmap.CompressFormat.PNG,100,it)}}}
    private fun settings(){scenario.onActivity {vm.navigateTo(NavScreen.SETTINGS)}}
    @Test fun settingsSectionsAreNavigableAndAppearanceAppliesAtLargeText() {
        settings();compose.onNodeWithTag("settings_section_appearance").performClick()
        compose.onNodeWithTag("settings_list").performScrollToNode(hasTestTag("appearance_text_size_large"))
        compose.onNodeWithTag("appearance_text_size_large").performClick();assertEquals("Large",vm.preferences.value.textSize)
        compose.onNodeWithTag("settings_list").performScrollToNode(hasTestTag("settings_back"))
        compose.onNodeWithTag("settings_back").performClick()
        compose.onNodeWithTag("settings_list").performScrollToNode(hasTestTag("settings_section_display"))
        compose.onNodeWithTag("settings_section_display").performClick()
        compose.onNodeWithTag("refresh_120").assertIsNotEnabled();compose.onNodeWithTag("refresh_60").assertIsEnabled()
        compose.onNodeWithTag("settings_back").performClick()
        snapshot("settings-350.png")
        compose.onNodeWithTag("settings_section_appearance").performClick()
        compose.onNodeWithText("Light",useUnmergedTree=true).performClick()
        assertEquals("Light",vm.preferences.value.theme);snapshot("settings-light-350.png")
        val page=compose.onNodeWithTag("settings_list").fetchSemanticsNode().boundsInRoot
        val nav=compose.onNodeWithTag("bottom_navigation").fetchSemanticsNode().boundsInRoot
        assertTrue(page.bottom<=nav.top+1)
    }
    @Test fun expandedPlayerKeepsControlsVisibleAndNextWorksWithOneTap() {
        val tracks=MusicCatalog.TRACKS.filter {it.id.startsWith("demo-track")}
        scenario.onActivity {vm.playTrack(tracks[0],tracks);vm.openFullPlayer()}
        compose.waitUntil(15000){vm.playbackState.value.isPlaying}
        scenario.onActivity {AudioPlayerEngine.getInstance(it).pause()}
        compose.onNodeWithTag("full_player_play_pause").assertIsDisplayed();compose.onNodeWithTag("full_player_next").assertIsDisplayed()
        val page=compose.onNodeWithTag("full_player_screen").fetchSemanticsNode().boundsInRoot
        val next=compose.onNodeWithTag("full_player_next").fetchSemanticsNode().boundsInRoot
        assertTrue(next.bottom<=page.bottom && next.top>=page.top)
        compose.onNodeWithTag("full_player_next").performClick()
        compose.waitUntil(15000){vm.playbackState.value.currentTrack?.id==tracks[1].id && vm.playbackState.value.isPlaying}
        scenario.onActivity {AudioPlayerEngine.getInstance(it).pause()}
        compose.onNodeWithTag("player_like").performClick();snapshot("player-350.png")
        compose.onNodeWithTag("full_player_more_btn").performClick();compose.onNodeWithText("Equalizer").assertIsDisplayed();compose.onNodeWithText("Sleep timer").assertIsDisplayed()
    }
    @Test fun themedAndOriginalMixesOpenSongListsWithoutChangingPlayback() {
        val before=vm.playbackState.value
        compose.onNodeWithTag("home_list").performScrollToNode(hasTestTag("mix_shelf"))
        compose.onNodeWithTag("mix_shelf").performScrollToNode(hasTestTag("build_english_mix"))
        snapshot("mix-cards-350.png")
        compose.onNodeWithTag("build_english_mix").performClick()
        compose.waitUntil(15000){vm.currentScreen.value==NavScreen.ALBUM_DETAIL && vm.selectedAlbum.value?.title=="English Mix"}
        val selected=vm.selectedAlbum.value!!
        assertTrue(selected.trackIds.isNotEmpty());assertTrue(selected.trackIds.mapNotNull {MusicCatalog.getTrackById(it)}.all {it.language=="English"})
        assertFalse(vm.playbackState.value.isPlaying);assertEquals(before.queue.map {it.id},vm.playbackState.value.queue.map {it.id});snapshot("mix-album-350.png")
        scenario.onActivity {vm.navigateTo(NavScreen.HOME)}
        compose.onNodeWithTag("home_list").performScrollToNode(hasTestTag("mix_shelf"))
        compose.onNodeWithTag("mix_shelf").performScrollToNode(hasTestTag("build_daily_mix_1"))
        compose.onNodeWithTag("build_daily_mix_1").performClick()
        compose.waitUntil(15000){vm.currentScreen.value==NavScreen.ALBUM_DETAIL && vm.selectedAlbum.value?.title=="Daily Mix 1"}
        assertFalse(vm.playbackState.value.isPlaying);assertTrue(vm.selectedAlbum.value!!.trackIds.isNotEmpty())
    }
    @Test fun dailySpotlightDismissesOnceAndNeverPausesTheMusic() {
        scenario.onActivity {vm.playTrack(MusicCatalog.getTrackById("demo-track-0")!!);AudioPlayerEngine.getInstance(it).player.repeatMode=androidx.media3.common.Player.REPEAT_MODE_ONE}
        compose.waitUntil(15000){vm.playbackState.value.isPlaying}
        gate.edit().clear().commit();scenario.recreate()
        compose.mainClock.advanceTimeBy(1600)
        Thread.sleep(1700)
        compose.onNodeWithTag("daily_spotlight").assertExists().assertIsDisplayed()
        assertTrue(vm.playbackState.value.isPlaying);assertFalse(DailySpotlightGate.due(gate,LocalDate.now()));snapshot("spotlight-350.png")
        compose.onNodeWithTag("spotlight_close").performClick()
        compose.onNodeWithTag("daily_spotlight").assertDoesNotExist();assertTrue(vm.playbackState.value.isPlaying)
        scenario.onActivity {vm.navigateTo(NavScreen.SETTINGS)};compose.onNodeWithTag("settings_list").assertIsDisplayed()
        scenario.onActivity {vm.navigateTo(NavScreen.HOME)}
        Thread.sleep(1600);compose.onNodeWithTag("daily_spotlight").assertDoesNotExist()
    }
}
