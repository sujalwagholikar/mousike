package com.mousike.app

import android.content.Context
import androidx.activity.compose.setContent
import android.content.Intent
import android.graphics.Bitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mousike.app.data.catalog.MusicCatalog
import com.mousike.app.playback.AudioPlayerEngine
import com.mousike.app.settings.AppSettings
import com.mousike.app.settings.Preferences
import com.mousike.app.ui.viewmodel.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class Version33FlowTest {
    @get:Rule val compose=createEmptyComposeRule()
    private val context get()=ApplicationProvider.getApplicationContext<Context>()
    private lateinit var scenario: ActivityScenario<MainActivity>
    private lateinit var vm: MousikeViewModel
    @Before fun start() {
        com.mousike.app.ui.components.DailySpotlightGate.shown(context.getSharedPreferences("mousike_feature_spotlight",0),java.time.LocalDate.now())
        AppSettings.get(context).update {Preferences(onboarded=true,autoplay=false,reduceMotion=true,detectAlbums=false)}
        scenario=ActivityScenario.launch(Intent(context,MainActivity::class.java).putExtra("offline_fixture",true))
        scenario.onActivity {vm=ViewModelProvider(it)[MousikeViewModel::class.java];AudioPlayerEngine.getInstance(it).pause()}
        compose.waitUntil(15000){compose.onAllNodesWithTag("nav_home").fetchSemanticsNodes().isNotEmpty()}
    }
    @After fun close(){scenario.onActivity {AudioPlayerEngine.getInstance(it).pause()};scenario.close()}
    private fun snapshot(name: String){compose.onRoot().captureToImage().asAndroidBitmap().let {image->File(context.externalCacheDir,name).outputStream().use {image.compress(Bitmap.CompressFormat.PNG,100,it)}}}
    @Test fun dailyMixBuildsTrackListWithoutStartingOrReplacingPlayback() {
        val before=AudioPlayerEngine.getInstance(context).playbackState.value
        compose.onNodeWithTag("home_list").performScrollToNode(hasTestTag("build_daily_mix_1"))
        compose.onNodeWithTag("build_daily_mix_1").performClick()
        compose.waitUntil(15000){vm.currentScreen.value==NavScreen.ALBUM_DETAIL && vm.selectedAlbum.value?.trackIds?.isNotEmpty()==true}
        val album=vm.selectedAlbum.value!!;assertEquals("Daily Mix 1",album.title)
        assertTrue(album.trackIds.all {MusicCatalog.getTrackById(it)!=null})
        val after=AudioPlayerEngine.getInstance(context).playbackState.value
        assertFalse(after.isPlaying);assertEquals(before.currentTrack?.id,after.currentTrack?.id);assertEquals(before.queue.map {it.id},after.queue.map {it.id})
        compose.onNodeWithText("Daily Mix 1").assertIsDisplayed();snapshot("daily-mix-33.png")
    }
    @Test fun onboardingPreparationCompletesIntoHome() {
        scenario.onActivity {
            AppSettings.get(context).update {it.copy(onboarded=false,reduceMotion=false)}
            vm.completeOnboarding("Sujal",setOf("Instrumental"),setOf("Ambient"),setOf("Mousike Studio"))
            assertNotNull(vm.assemblyStage.value)
        }
        compose.waitUntil(15000){AppSettings.get(context).state.value.onboarded && vm.assemblyStage.value==null}
        compose.onNodeWithTag("nav_home").assertIsDisplayed()
        assertEquals("Sujal",AppSettings.get(context).state.value.name)
    }
    @Test fun typingIsImmediatelyLoadingAndClearingStopsEmptyFlash() {
        scenario.onActivity {
            vm.navigateTo(NavScreen.SEARCH);vm.updateSearchQuery("An artist")
            assertTrue(vm.isSearching.value)
            vm.updateSearchQuery("");assertFalse(vm.isSearching.value)
        }
        compose.onNodeWithTag("search_empty").assertDoesNotExist()
    }
    @Test fun developerPortraitAndRealRefreshRateOptionsArePresent() {
        scenario.onActivity {vm.navigateTo(NavScreen.ABOUT)}
        compose.onNodeWithTag("about_screen").performScrollToNode(hasContentDescription("Sujal Wagholikar, developer of Mousike"))
        compose.onNodeWithContentDescription("Sujal Wagholikar, developer of Mousike").assertIsDisplayed();snapshot("developer-33.png")
        scenario.onActivity {vm.navigateTo(NavScreen.SETTINGS)}
        compose.onNodeWithTag("settings_list").performScrollToNode(hasTestTag("settings_section_display"))
        compose.onNodeWithTag("settings_section_display").performClick()
        compose.onNodeWithTag("settings_list").performScrollToNode(hasTestTag("refresh_120"))
        compose.onNodeWithTag("refresh_0").assertIsDisplayed();compose.onNodeWithTag("refresh_60").assertIsEnabled()
        // The emulator has a 60 Hz panel; unsupported 120 Hz is shown but cannot be selected.
        compose.onNodeWithTag("refresh_120").assertIsNotEnabled();snapshot("refresh-33.png")
    }
    @Test fun preparationAnimationHasReadableStagesAndNoWhiteIconFrame() {
        scenario.onActivity {activity ->activity.setContent {
            com.mousike.app.ui.theme.MousikeTheme {
                com.mousike.app.ui.components.PreparationScreen("Your music world",listOf("Analysing your preferences","Customising for you","Building your playlist","Here we go"),1)
            }
        }}
        compose.onNodeWithTag("preparation_screen").assertIsDisplayed()
        compose.onAllNodesWithText("Customising for you").onFirst().assertIsDisplayed()
        snapshot("preparation-34.png")
    }
}
