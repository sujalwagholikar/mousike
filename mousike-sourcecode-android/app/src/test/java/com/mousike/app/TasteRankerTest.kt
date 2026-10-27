package com.mousike.app
import com.mousike.app.data.local.*
import com.mousike.app.data.model.Track
import com.mousike.app.recommendation.TasteRanker
import com.mousike.app.settings.Preferences
import org.junit.Assert.*
import org.junit.Test
class TasteRankerTest {
 private val now=100000000000L
 private fun t(id: String,a: String="Singer $id")=Track(id,"Track $id",a,"Album $id",200,"","","Indie",2026,language="Hindi")
 private fun rank(ts: List<Track>,liked: Set<String> = emptySet(),h: List<HistoryEntity> = emptyList(),f: List<FeedbackEntity> = emptyList(),current: Track?=null)=TasteRanker.rank(ts,ts,liked,h,f,Preferences(),current,now)
 @Test fun empty(){assertTrue(rank(emptyList()).isEmpty())}
 @Test fun excludesCurrent(){val ts=(0..5).map {t("$it")};assertFalse(rank(ts,current=ts[0]).any {it.id=="0"})}
 @Test fun blocksSongs(){val ts=(0..5).map {t("$it")};val f=FeedbackEntity(trackId="0",artistName="Singer 0",genre="Indie",feedbackType="DONT_RECOMMEND");assertFalse(rank(ts,f=listOf(f)).any {it.id=="0"})}
 @Test fun artistFeedback(){val ts=(0..5).map {t("$it")};val f=FeedbackEntity(trackId="0",artistName="Singer 0",genre="Indie",feedbackType="LESS_OF_ARTIST");assertFalse(rank(ts,f=listOf(f)).any {it.artist=="Singer 0"})}
 @Test fun duplicatePublishers(){val a=t("one").copy(title="Blue Sky",artist="The Artist");assertEquals(1,rank(listOf(a,a.copy(id="two",title="Blue Sky (Official Audio)"))).size)}
 @Test fun artistDiversity(){val ts=(0..11).map {t("a$it","Same")}+(0..11).map {t("b$it")};assertTrue(rank(ts,liked=ts.take(12).map {it.id}.toSet()).take(12).count {it.artist=="Same"}<=2)}
 @Test fun repeatPenalty(){val ts=listOf(t("first"),t("second"));assertEquals("second",rank(ts,h=listOf(HistoryEntity(trackId="first",playedAt=now-10000,completionPercent=100))).first().id)}
 @Test fun skipsReduceAffinity(){val ts=listOf(t("skipped","Skipped Singer"),t("fresh","Fresh Singer"));assertEquals("fresh",rank(ts,h=(0..4).map {HistoryEntity(trackId="skipped",playedAt=now-it*1000,completionPercent=5,wasSkipped=true)}).first().id)}
 @Test fun deterministic(){val ts=(0..20).map {t("$it")};assertEquals(rank(ts).map {it.id},rank(ts).map {it.id})}
 @Test fun noFabricatedCandidates(){val ts=(0..20).map {t("$it")};assertEquals(ts.map {it.id}.toSet(),rank(ts).map {it.id}.toSet())}
}
