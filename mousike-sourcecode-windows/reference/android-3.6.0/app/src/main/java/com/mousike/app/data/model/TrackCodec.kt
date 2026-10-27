package com.mousike.app.data.model
import org.json.JSONObject

object TrackCodec {
    fun encode(t: Track): JSONObject = JSONObject().put("id",t.id).put("title",t.title).put("artist",t.artist)
        .put("album",t.album).put("duration",t.durationSec).put("cover",t.coverUrl).put("genre",t.genre)
        .put("year",t.year).put("language",t.language).put("videoId",t.videoId).put("startMs",t.startMs)
        .put("endMs",t.endMs ?: JSONObject.NULL).put("evidence",t.evidence).put("mood",t.mood).put("musicVideoType",t.musicVideoType)
        .put("streamUrl",if(t.streamUrl.startsWith("asset://")) t.streamUrl else "")
    fun decode(o: JSONObject) = Track(o.getString("id"),o.optString("title"),o.optString("artist"),
        o.optString("album"),o.optInt("duration"),o.optString("streamUrl"),o.optString("cover"),
        o.optString("genre","Music"),o.optInt("year"),language=o.optString("language"),
        videoId=o.optString("videoId",o.getString("id")),startMs=o.optLong("startMs"),
        endMs=if(o.isNull("endMs")) null else o.optLong("endMs"),evidence=o.optString("evidence"),mood=o.optString("mood"),musicVideoType=o.optString("musicVideoType"))
}
