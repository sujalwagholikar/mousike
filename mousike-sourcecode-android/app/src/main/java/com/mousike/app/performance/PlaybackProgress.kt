package com.mousike.app.performance
/** Detect a frozen playing clock, independently of ExoPlayer's isPlaying flag. */
class PlaybackProgress {
    private var previous=Long.MIN_VALUE
    private var changedAt=0L
    fun stalled(position: Long,playing: Boolean,now: Long,limit: Long): Boolean {
        if(!playing || position!=previous){previous=position;changedAt=now;return false}
        if(now-changedAt<limit)return false
        changedAt=now;return true
    }
}
