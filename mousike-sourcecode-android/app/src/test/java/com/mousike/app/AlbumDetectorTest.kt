package com.mousike.app
import com.mousike.app.album.AlbumDetector
import com.mousike.app.data.model.Track
import com.mousike.app.data.model.TrackCodec
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
class AlbumDetectorTest {
 private fun source(s: Int=1200)=Track("abcdefghijk","A Real Album","Artist","A Real Album",s,"","","Music",2026)
 @Test fun threshold(){assertFalse(AlbumDetector.isLong(source(480)));assertTrue(AlbumDetector.isLong(source(481)))}
 @Test fun rejectsImpossibleTime(){assertNull(AlbumDetector.parseTime("03:99"));assertNull(AlbumDetector.parseTime("-1:30"));assertNull(AlbumDetector.parseTime("abc"))}
 @Test fun hoursAndMinutes(){assertEquals(3723000L,AlbumDetector.parseTime("1:02:03"));assertEquals(192000L,AlbumDetector.parseTime("03:12"))}
 @Test fun chapterClips(){val m=JSONObject("""{"duration":1200,"chapters":[{"title":"First","start_time":0,"end_time":180},{"title":"Second","start_time":180,"end_time":420},{"title":"Third","start_time":420,"end_time":1200}]}""");val d=AlbumDetector.fromMetadata(source(),m);assertEquals(3,d.tracks.size);assertEquals(180000L,d.tracks[0].endMs);assertEquals(180000L,d.tracks[1].startMs);assertEquals("abcdefghijk",d.tracks[2].videoId);assertEquals("verified",d.confidence);assertNotEquals(d.tracks[0].id,d.tracks[1].id)}
 @Test fun descriptionWithoutChapters(){val m=JSONObject().put("duration",1200).put("description","Tracklist\n00:00 First song\n03:12 Second song\n07:15 Last song");val t=AlbumDetector.fromMetadata(source(),m).tracks;assertEquals(3,t.size);assertEquals(192000L,t.first().endMs);assertEquals(1200000L,t.last().endMs)}
 @Test fun titleBeforeAndNumberedRows(){val p=AlbumDetector.descriptionBoundaries("1. Sunrise - 00:00\n2. Moon [03:10]\n07:00 — River");assertEquals(listOf("Sunrise","Moon","River"),p.map {it.title})}
 @Test fun neverInventsTimestamps(){val d=AlbumDetector.fromMetadata(source(),JSONObject().put("duration",1200).put("description","All the best songs"));assertTrue(d.tracks.isEmpty());assertEquals("unresolved",d.confidence)}
 @Test fun validatesAndSortsBoundaries(){val raw=listOf(AlbumDetector.Boundary("Second",200000),AlbumDetector.Boundary("First",0),AlbumDetector.Boundary("Duplicate",0),AlbumDetector.Boundary("Negative",-100),AlbumDetector.Boundary("Beyond",1400000));val t=AlbumDetector.build(source(),raw,1200000,"test");assertEquals(2,t.size);assertEquals("First",t.first().title);assertTrue(t.all {it.endMs!!>it.startMs && it.startMs>=0 && it.endMs!!<=1200000})}
 @Test fun overlapsClamped(){val t=AlbumDetector.build(source(),listOf(AlbumDetector.Boundary("A",0,500000),AlbumDetector.Boundary("B",180000)),1200000,"test");assertEquals(180000L,t.first().endMs)}
 @Test fun zeroLengthRejected(){assertTrue(AlbumDetector.build(source(),listOf(AlbumDetector.Boundary("A",0,0),AlbumDetector.Boundary("B",1000,1000)),1200000,"test").isEmpty())}
 @Test fun explicitRanges(){val p=AlbumDetector.descriptionBoundaries("00:00 - 03:00 First\n03:15 - 07:00 Second");assertEquals(180000L,p.first().endMs);assertEquals("First",p.first().title)}
 @Test fun chapterCodecRoundTrip(){val t=source().copy(id="abcdefghijk@192000:400000",startMs=192000,endMs=400000,evidence="chapters",language="Hindi");assertEquals(t,TrackCodec.decode(TrackCodec.encode(t)))}
 @Test fun catalogMatchRequiresAgreement(){val s=source(600);val t=listOf(source(300).copy(id="1"),source(300).copy(id="2"));assertTrue(AlbumDetector.plausibleRelease(s,"A Real Album",t));assertFalse(AlbumDetector.plausibleRelease(s,"Unrelated Recording",t));assertFalse(AlbumDetector.plausibleRelease(s,"A Real Album",t.map {it.copy(durationSec=30)}))}
}
