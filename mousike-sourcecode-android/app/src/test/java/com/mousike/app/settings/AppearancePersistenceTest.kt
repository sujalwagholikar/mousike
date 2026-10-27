package com.mousike.app.settings

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.mousike.app.ui.theme.resetAppearance
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35])
class AppearancePersistenceTest {
    private val context=ApplicationProvider.getApplicationContext<Context>()
    private fun settings(): AppSettings=AppSettings::class.java.getDeclaredConstructor(Context::class.java).apply {isAccessible=true}.newInstance(context)
    @Before fun clear(){context.getSharedPreferences("mousike_preferences",0).edit().clear().commit()}
    @Test fun upgradeAppliesCoralAndSquareOnceWithoutClearingProfile() {
        context.getSharedPreferences("mousike_preferences",0).edit().putString("name","Sujal").putString("accent","Green").putString("coverShape","Rounded").putString("audioQuality","Data saver").commit()
        val first=settings();assertEquals("Coral",first.state.value.accent);assertEquals("Square",first.state.value.coverShape);assertEquals("Sujal",first.state.value.name);assertEquals("Low",first.state.value.audioQuality)
        first.update {it.copy(accent="Sky",coverShape="Soft")}
        assertEquals("Sky",settings().state.value.accent);assertEquals("Soft",settings().state.value.coverShape)
    }
    @Test fun customisationSurvivesSettingsRecreation() {
        val store=settings()
        val desired=store.state.value.copy(fontCombo="Editorial",surfaceStyle="Outlined",playerLayout="Immersive",playerArtworkSize="Large",playerBackground="Accent",playerControls="White",showPlayerShortcuts=false,showPlayerContext=false,albumLayout="Gallery",trackSpacing="Compact",showTrackArtwork=false,showTrackDuration=false,showNavLabels=false,miniPlayerStyle="Edge",showMiniProgress=false,musicFirst=false,audioQuality="Super high")
        store.update {desired};assertEquals(desired,settings().state.value)
    }
    @Test fun resettingAppearanceKeepsTasteAndPlaybackChoices() {
        val original=Preferences(name="Sujal",artists=setOf("A"),audioQuality="Low",autoplay=false,preloadNext=false,fontCombo="Modern",accent="Sky")
        val reset=original.resetAppearance()
        assertEquals("Coral",reset.accent);assertEquals("Square",reset.coverShape)
        assertEquals(original.artists,reset.artists);assertEquals("Low",reset.audioQuality);assertFalse(reset.autoplay);assertFalse(reset.preloadNext)
    }
    @Test fun qualityChangeWaitsForTheNextSongAndKeepsQueueAndPlayIntent() {
        val store=settings();val player=androidx.media3.exoplayer.ExoPlayer.Builder(context).build()
        val binding=QualitySelectionBinding(player,store.state)
        try {
            player.setMediaItems(listOf("one","two").map {id->androidx.media3.common.MediaItem.Builder().setMediaId(id).setUri("asset:///demo/$id.wav").build()})
            player.playWhenReady=true;org.robolectric.shadows.ShadowLooper.idleMainLooper()
            assertEquals(160_000,player.trackSelectionParameters.maxAudioBitrate)
            store.update {it.copy(audioQuality="Low")}
            assertEquals(160_000,player.trackSelectionParameters.maxAudioBitrate)
            assertEquals("one",player.currentMediaItem?.mediaId)
            player.seekToNextMediaItem();org.robolectric.shadows.ShadowLooper.idleMainLooper()
            assertEquals(64_000,player.trackSelectionParameters.maxAudioBitrate)
            assertEquals("two",player.currentMediaItem?.mediaId);assertEquals(2,player.mediaItemCount);assertTrue(player.playWhenReady)
        } finally {binding.close();player.release()}
    }
}
