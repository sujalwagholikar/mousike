package com.mousike.app.playback

import android.os.Build
import com.mousike.app.BuildConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

object PlaybackDiagnostics {
    data class Snapshot(val videoId: String="",val stage: String="Ready",val startedAt: Long=0,val events: List<String> = emptyList())
    private val state=MutableStateFlow(Snapshot())
    val status: StateFlow<Snapshot> = state
    @Synchronized fun begin(id: String){state.value=Snapshot(id,"Connecting",System.currentTimeMillis(),listOf("0 ms • Selection requested"))}
    @Synchronized fun stage(id: String,text: String){
        val s=state.value;if(s.videoId!=id)return
        val safe=text.replace(Regex("https?://\\S+"),"[address]").take(220)
        if(s.stage==safe)return
        state.value=s.copy(stage=safe,events=(s.events+"${System.currentTimeMillis()-s.startedAt} ms • $safe").takeLast(16))
    }
    fun copyText(): String {
        val s=state.value
        return "Mousike ${BuildConfig.VERSION_NAME}\n${Build.MANUFACTURER} ${Build.MODEL} • Android ${Build.VERSION.RELEASE}\nVideo: ${s.videoId}\n"+s.events.joinToString("\n")
    }
}
