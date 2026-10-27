package com.mousike.app.performance

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.mousike.app.data.model.Track
import com.mousike.app.data.repository.RecommendationResult
import com.mousike.app.ui.components.*
import org.junit.Assert.*
import org.junit.Test

@org.junit.runner.RunWith(org.robolectric.RobolectricTestRunner::class)
@org.robolectric.annotation.Config(sdk=[28],manifest=org.robolectric.annotation.Config.NONE)
class Frontend350Test {
    @Test fun spotlightPersistsOneAutomaticImpressionPerCalendarDay() {
        val context=ApplicationProvider.getApplicationContext<Context>();val prefs=context.getSharedPreferences("spotlight_frequency_test",0)
        prefs.edit().clear().commit()
        val day=java.time.LocalDate.of(2026,10,26)
        assertTrue(DailySpotlightGate.due(prefs,day));DailySpotlightGate.shown(prefs,day)
        assertFalse(DailySpotlightGate.due(context.getSharedPreferences("spotlight_frequency_test",0),day))
        assertTrue(DailySpotlightGate.due(prefs,day.plusDays(1)))
    }
    private fun song(id: String,artist: String="Artist",language: String="English",genre: String="Pop")=Track(id,id,artist,"",200,"","",genre,2026,language=language)
    @Test fun themedMixesKeepRankedOrderAndDoNotSubstituteAnotherLanguage() {
        val a=song("first","A");val b=song("second","B");val hindi=song("hindi","C","Hindi","Bollywood")
        val result=RecommendationResult(madeForYou=listOf(b,hindi,a))
        assertEquals(listOf("second","first"),personalMixTracks(PersonalMixes.first {it.id=="english"},result,listOf(a,b,hindi)).map {it.id})
        assertEquals(listOf("hindi"),personalMixTracks(PersonalMixes.first {it.id=="bollywood"},result,emptyList()).map {it.id})
        assertTrue(personalMixTracks(PersonalMixes.first {it.id=="chill"},result,emptyList()).isEmpty())
    }
    @Test fun mixPresentationLimitsArtistDominanceAndExcludesUnsplitLongVideos() {
        val songs=(1..7).map {song("a$it")};val other=song("other","Another artist")
        val long=other.copy(id="long",durationSec=1800)
        val chapter=long.copy(id="chapter",endMs=200000)
        val result=RecommendationResult(madeForYou=songs+other+long+chapter)
        val selected=personalMixTracks(PersonalMixes.first {it.id=="english"},result,emptyList())
        assertEquals(3,selected.count {it.artist=="Artist"});assertFalse(selected.any {it.id=="long"});assertTrue(selected.any {it.id=="chapter"})
    }
}
