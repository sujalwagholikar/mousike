package com.mousike.app.performance

/** One continuous deadline per waiting episode, driven by the playback ticker.
 * Byte progress matters while a progressive extractor is reading its index: bufferedPosition
 * can stay at zero despite a healthy download. A READY/playing snapshot clears the episode.
 */
class BufferingWatchdog {
    enum class Action { NONE, RECOVER, TIMEOUT }
    private var key: String? = null
    private var startedAt = 0L
    private var progressedAt = 0L
    private var position = 0L
    private var buffered = 0L
    private var bytes = 0L
    fun reset() { key = null }
    fun poll(id: String?, requested: Boolean, playing: Boolean, waiting: Boolean,
        resolved: Boolean, positionMs: Long, bufferedMs: Long, transferredBytes: Long,
        now: Long, deadlineMs: Long, stallMs: Long): Action {
        if(id == null || !requested || playing || !waiting) { reset(); return Action.NONE }
        if(key != id) {
            key=id;startedAt=now;progressedAt=now
            position=positionMs;buffered=bufferedMs;bytes=transferredBytes
            return Action.NONE
        }
        if(now-startedAt >= deadlineMs) return Action.TIMEOUT
        if(positionMs>position || bufferedMs>buffered || transferredBytes>bytes) progressedAt=now
        position=positionMs;buffered=bufferedMs;bytes=transferredBytes
        if(!resolved) { progressedAt=now; return Action.NONE }
        if(now-progressedAt < stallMs) return Action.NONE
        progressedAt=now
        return Action.RECOVER
    }
}
