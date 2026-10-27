package com.mousike.app

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mousike.app.settings.AppSettings
import com.mousike.app.settings.Preferences
import com.mousike.app.ui.components.*
import com.mousike.app.ui.theme.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class ArtworkAppearanceTest {
    @get:Rule val compose=createComposeRule()
    private val context get()=ApplicationProvider.getApplicationContext<Context>()
    @Before fun defaults(){AppSettings.get(context).update {Preferences(onboarded=true)}}
    @Test fun actualImageReplacesPlaceholderAndMissingArtworkStaysVisible() {
        compose.setContent {MousikeTheme {
            Row(Modifier.background(ObsidianDark).padding(16.dp)) {
                MusicArtwork(R.drawable.mousike_logo,"Loaded logo",Modifier.size(100.dp).testTag("loaded_art"))
                Spacer(Modifier.width(12.dp))
                MusicArtwork("file:///android_asset/does-not-exist.png","Arctic Monkeys",Modifier.size(100.dp).testTag("missing_art"))
            }
        }}
        compose.waitUntil(15000){compose.onAllNodesWithTag("artwork_placeholder").fetchSemanticsNodes().size==1}
        compose.onNodeWithContentDescription("Loaded logo").assertIsDisplayed()
        compose.onNodeWithText("AM").assertIsDisplayed()
        // Invalid artwork still has a coloured, accessible identity, never a black hole.
        val bitmap=compose.onNodeWithTag("missing_art").captureToImage().toPixelMap()
        val pixel=bitmap[8,8]
        assertTrue(pixel.red>.1f || pixel.green>.1f || pixel.blue>.1f)
    }
    @Test fun densityShapeAndTextSizeReactWhileKeepingCardsReadable() {
        compose.setContent {MousikeTheme {
            Row(Modifier.background(ObsidianDark)) {
                ArtworkCard("Arctic Monkeys","Artist","",{},modifier=Modifier.testTag("sample_card"),isCircular=true)
                ArtworkCard("Popular Album","Album","",{})
            }
        }}
        val initial=compose.onNodeWithTag("sample_card").fetchSemanticsNode().boundsInRoot.width
        compose.runOnIdle {AppSettings.get(context).update {it.copy(cardDensity="Compact",textSize="Large",coverShape="Soft")}}
        compose.waitForIdle()
        val compact=compose.onNodeWithTag("sample_card").fetchSemanticsNode().boundsInRoot.width
        assertTrue(compact<initial)
        compose.onNodeWithText("Arctic Monkeys").assertIsDisplayed()
        compose.onNodeWithText("Popular Album").assertIsDisplayed()
        assertEquals("Large",context.getSharedPreferences("mousike_preferences",0).getString("textSize",""))
        val screenshot=InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
        File(context.externalCacheDir,"artwork-appearance.png").outputStream().use {screenshot.compress(Bitmap.CompressFormat.PNG,100,it)}
    }
}
