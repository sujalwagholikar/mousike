package com.mousike.app.settings

import com.mousike.app.data.model.Track
import com.mousike.app.data.model.TrackCodec
import com.mousike.app.data.remote.MusicPreference
import com.mousike.app.data.remote.YouTubeMusicClient
import com.mousike.app.playback.NativeAudioResolver
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class QualityAndMusicPreferenceTest {
    private fun root()=JSONObject("""{"playabilityStatus":{"status":"OK"},"videoDetails":{"videoId":"abcdefghijk"},"streamingData":{"adaptiveFormats":[{"itag":139,"mimeType":"audio/mp4","bitrate":48000,"url":"https://media.test/low"},{"itag":140,"mimeType":"audio/mp4","bitrate":128000,"url":"https://media.test/high"},{"itag":251,"mimeType":"audio/webm","bitrate":256000,"url":"https://media.test/super"}]}}""")
    @Test fun threeChoicesSelectThreeAvailableSources() {
        for((quality,suffix) in listOf("Low" to "low","High" to "high","Super high" to "super"))
            assertTrue(NativeAudioResolver.parse("abcdefghijk",root(),quality)!!.stream.url.endsWith(suffix))
    }
    @Test fun qualityNeverRejectsTheOnlyWorkingAudio() {
        val source=root();val formats=source.getJSONObject("streamingData").getJSONArray("adaptiveFormats")
        source.getJSONObject("streamingData").put("adaptiveFormats",org.json.JSONArray().put(formats.getJSONObject(1)))
        for(q in listOf("Low","High","Super high"))assertTrue(NativeAudioResolver.parse("abcdefghijk",source,q)!!.stream.url.endsWith("high"))
    }
    @Test fun lowCanUseAnEfficientWebmWhenAacExceedsTheTarget() {
        val source=root();source.getJSONObject("streamingData").getJSONArray("adaptiveFormats").getJSONObject(0).put("mimeType","audio/webm")
        assertTrue(NativeAudioResolver.parse("abcdefghijk",source,"Low")!!.stream.url.endsWith("low"))
    }
    @Test fun oldDataSaverRetainsLowPolicy() {
        assertEquals(AudioQualityPolicy.maxBitrate("Low"),AudioQualityPolicy.maxBitrate("Data saver"))
        assertEquals(AudioQualityPolicy.ytDlpFormat("Low"),AudioQualityPolicy.ytDlpFormat("Data saver"))
    }
    @Test fun missingProgressiveAudioPreservesHlsFallback() {
        val source=root();source.getJSONObject("streamingData").remove("adaptiveFormats")
        source.getJSONObject("streamingData").put("hlsManifestUrl","https://media.test/audio.m3u8")
        for(q in listOf("Low","High","Super high"))assertTrue(NativeAudioResolver.parse("abcdefghijk",source,q)!!.stream.isHls)
    }
    private fun track(id: String,type: String)=Track(id,id,"Artist","Album",180,"","","Music",2026,musicVideoType=type)
    @Test fun prefersMusicRecordingsWhileKeepingSongIdentityAndRelativeTasteOrder() {
        val a=track("video","MUSIC_VIDEO_TYPE_UGC");val b=track("song1","MUSIC_VIDEO_TYPE_ATV");val c=track("official","MUSIC_VIDEO_TYPE_OMV");val d=track("song2","MUSIC_VIDEO_TYPE_ATV")
        assertEquals(listOf(b,d,c,a),MusicPreference.order(listOf(a,b,c,d),true))
        assertEquals(listOf(a,b,c,d),MusicPreference.order(listOf(a,b,c,d),false))
    }
    @Test fun musicMetadataSurvivesBackupAndOlderBackupsStillLoad() {
        val original=track("song","MUSIC_VIDEO_TYPE_ATV")
        assertEquals(original.musicVideoType,TrackCodec.decode(TrackCodec.encode(original)).musicVideoType)
        val old=TrackCodec.encode(original);old.remove("musicVideoType")
        assertEquals("",TrackCodec.decode(old).musicVideoType)
    }
    @Test fun albumParserKeepsTheOriginalTrackSequence() {
        fun item(id: String,type: String)=JSONObject("""{"playlistPanelVideoRenderer":{"videoId":"$id","title":{"simpleText":"$id"},"navigationEndpoint":{"watchEndpoint":{"videoId":"$id","watchEndpointMusicSupportedConfigs":{"watchEndpointMusicConfig":{"musicVideoType":"$type"}}}}}}""")
        val source=JSONObject().put("items",org.json.JSONArray().put(item("abcdefghijk","MUSIC_VIDEO_TYPE_OMV")).put(item("lmnopqrstuv","MUSIC_VIDEO_TYPE_ATV")))
        val tracks=YouTubeMusicClient().extract(source).tracks
        assertEquals(listOf("abcdefghijk","lmnopqrstuv"),tracks.map {it.id})
        assertEquals("MUSIC_VIDEO_TYPE_ATV",tracks[1].musicVideoType)
    }
}
