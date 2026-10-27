package com.mousike.app.performance

import com.mousike.app.performance.BufferingWatchdog.Action.*
import org.junit.Assert.assertEquals
import org.junit.Test

class BufferingWatchdogTest {
    private val guard=BufferingWatchdog()
    private fun sample(now: Long,id: String="song",playing: Boolean=false,waiting: Boolean=true,
        requested: Boolean=true,resolved: Boolean=true,bytes: Long=0,buffer: Long=0)=
        guard.poll(id,requested,playing,waiting,resolved,0,buffer,bytes,now,12000,3000)
    @Test fun growingMp4IndexDoesNotTriggerFalseStallAtZeroBufferedPosition() {
        assertEquals(NONE,sample(0))
        assertEquals(NONE,sample(3100,bytes=65536))
        assertEquals(NONE,sample(6200,bytes=131072))
        assertEquals(NONE,sample(9200,bytes=262144))
    }
    @Test fun stuckAfterFirstSoundRearmsWithoutAnotherButtonTap() {
        sample(0);assertEquals(NONE,sample(1000,playing=true,waiting=false))
        assertEquals(NONE,sample(1200));assertEquals(RECOVER,sample(4300))
    }
    @Test fun briefReadyToBufferingEventStillHasContinuousRecovery() {
        sample(0);sample(100,playing=true,waiting=false);sample(101)
        assertEquals(RECOVER,sample(3101))
    }
    @Test fun sourceRetryDoesNotRestartTheOverallDeadline() {
        sample(0);assertEquals(RECOVER,sample(3000))
        assertEquals(NONE,sample(4000,resolved=false))
        assertEquals(RECOVER,sample(7100))
        assertEquals(TIMEOUT,sample(12000))
    }
    @Test fun pauseAndAudioFocusSuppressionAreNeverMislabelledAsBufferStalls() {
        sample(0);assertEquals(NONE,sample(10000,requested=false))
        assertEquals(NONE,sample(20000,waiting=false));assertEquals(NONE,sample(30000))
    }
    @Test fun nextSongGetsItsOwnDeadlineAndPlaybackClearsIt() {
        sample(0);assertEquals(NONE,sample(11000,id="next"))
        assertEquals(NONE,sample(12500,id="next",playing=true,waiting=false))
        assertEquals(NONE,sample(14000,id="next"))
        assertEquals(RECOVER,sample(17100,id="next"))
    }
    @Test fun endlessTrickleCannotKeepTheSpinnerAliveForever() {
        sample(0);sample(5000,bytes=1);sample(10000,bytes=2)
        assertEquals(TIMEOUT,sample(12000,bytes=3))
    }
}
