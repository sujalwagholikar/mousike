package com.mousike.app.settings

/** Source selection only. No retry, networking, buffering or playback lifecycle logic. */
object AudioQualityPolicy {
    fun maxBitrate(quality: String)=when(quality){"Low","Data saver"->64_000;"Super high"->Int.MAX_VALUE;else->160_000}
    fun <T> select(formats: List<T>,quality: String,bitrate: (T)->Int): T? {
        val sorted=formats.sortedBy(bitrate)
        if(quality=="Super high")return sorted.lastOrNull()
        return sorted.lastOrNull {bitrate(it) in 1..maxBitrate(quality)} ?: sorted.firstOrNull()
    }
    fun ytDlpFormat(quality: String)=when(quality) {
        "Low","Data saver"->"bestaudio[abr<=64]/worstaudio/worst[height<=240]/worst"
        "Super high"->"bestaudio/best[height<=240]/worst"
        else->"bestaudio[abr<=160]/bestaudio/best[height<=240]/worst"
    }
}
