package com.mousike.app

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mousike.app.data.catalog.MusicCatalog
import com.mousike.app.playback.AudioPlayerEngine
import com.mousike.app.settings.AppSettings
import com.mousike.app.settings.Preferences
import com.mousike.app.ui.viewmodel.MousikeViewModel
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class NavigationLayoutTest {
    @get:Rule val compose=createEmptyComposeRule()
    private val context get()=ApplicationProvider.getApplicationContext<Context>()
    private lateinit var scenario: ActivityScenario<MainActivity>
    @Before fun start() {
        com.mousike.app.ui.components.DailySpotlightGate.shown(context.getSharedPreferences("mousike_feature_spotlight",0),java.time.LocalDate.now())
        AppSettings.get(context).update {Preferences(onboarded=true,autoplay=false,reduceMotion=true,detectAlbums=false)}
        scenario=ActivityScenario.launch(Intent(context,MainActivity::class.java).putExtra("offline_fixture",true))
        compose.waitUntil(15000){compose.onAllNodesWithTag("nav_settings").fetchSemanticsNodes().isNotEmpty()}
        scenario.onActivity {AudioPlayerEngine.getInstance(it).pause()}
    }
    @After fun close() {scenario.onActivity {AudioPlayerEngine.getInstance(it).pause()};scenario.close()}
    @Test fun aboutCreditAndBottomBarHaveSeparateLayoutSpace() {
        compose.onNodeWithTag("nav_settings").performClick()
        compose.onNodeWithTag("settings_list").performScrollToNode(hasTestTag("settings_section_support"))
        compose.onNodeWithTag("settings_section_support").performClick()
        compose.onNodeWithTag("settings_list").performScrollToNode(hasText("About us"))
        compose.onNodeWithText("About us").performClick()
        compose.waitUntil(10000){compose.onAllNodesWithTag("about_screen").fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithTag("about_screen").performScrollToNode(hasTestTag("developer_credit"))
        compose.onNodeWithTag("developer_credit").assertTextEquals("Developed by Sujal Wagholikar").assertIsDisplayed()
        val page=compose.onNodeWithTag("about_screen").fetchSemanticsNode().boundsInRoot
        val nav=compose.onNodeWithTag("bottom_navigation").fetchSemanticsNode().boundsInRoot
        assertTrue("Navigation must reserve space below the page",page.bottom<=nav.top+1f)
        val image=InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
        File(context.externalCacheDir,"about-layout.png").outputStream().use {image.compress(Bitmap.CompressFormat.PNG,100,it)}
    }
    private fun openPlayer(error: Boolean=false) {
        scenario.onActivity {activity ->
            val vm=ViewModelProvider(activity)[MousikeViewModel::class.java]
            val track=MusicCatalog.getTrackById("demo-track-0")!!
            vm.playTrack(if(error)track.copy(id="missing-check",streamUrl="asset:///missing-check.wav") else track)
            vm.openFullPlayer()
        }
        compose.waitUntil(15000){val s=AudioPlayerEngine.getInstance(context).playbackState.value;if(error)s.error!=null else s.isPlaying}
        scenario.onActivity {AudioPlayerEngine.getInstance(it).pause()}
    }
    @Test fun fullPlayerControlsRemainInsideViewport() {
        openPlayer()
        compose.onNodeWithTag("full_player_play_pause").assertIsDisplayed()
        compose.onNodeWithTag("full_player_next").assertIsDisplayed()
        val page=compose.onNodeWithTag("full_player_screen").fetchSemanticsNode().boundsInRoot
        val button=compose.onNodeWithTag("full_player_next").fetchSemanticsNode().boundsInRoot
        assertTrue(button.bottom<=page.bottom && button.top>=page.top)
    }
    @Test fun connectionErrorIsInlineAndKeepsPlayControlsVisible() {
        openPlayer(error=true)
        compose.onNodeWithText("Couldn’t connect to this source").assertIsDisplayed()
        compose.onNodeWithTag("full_player_play_pause").assertIsDisplayed().assertIsEnabled()
        compose.onNodeWithTag("full_player_next").assertIsDisplayed()
    }
    @Test fun appearanceControlsApplyWithoutCoveringNavigation() {
        compose.onNodeWithTag("nav_settings").performClick()
        compose.onNodeWithTag("settings_section_appearance").performClick()
        compose.onNodeWithTag("settings_list").performScrollToNode(hasTestTag("appearance_text_size_large"))
        compose.onNodeWithTag("appearance_text_size_large").performClick()
        compose.onNodeWithTag("settings_list").performScrollToNode(hasTestTag("appearance_card_density_compact"))
        compose.onNodeWithTag("appearance_card_density_compact").performClick()
        compose.onNodeWithTag("settings_list").performScrollToNode(hasTestTag("appearance_cover_shape_soft"))
        compose.onNodeWithTag("appearance_cover_shape_soft").performClick()
        assertEquals("Large",AppSettings.get(context).state.value.textSize)
        assertEquals("Compact",AppSettings.get(context).state.value.cardDensity)
        assertEquals("Soft",AppSettings.get(context).state.value.coverShape)
        val page=compose.onNodeWithTag("settings_list").fetchSemanticsNode().boundsInRoot
        val nav=compose.onNodeWithTag("bottom_navigation").fetchSemanticsNode().boundsInRoot
        assertTrue(page.bottom<=nav.top+1)
        val screenshot=InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
        File(context.externalCacheDir,"settings-appearance.png").outputStream().use {screenshot.compress(Bitmap.CompressFormat.PNG,100,it)}
    }
}
