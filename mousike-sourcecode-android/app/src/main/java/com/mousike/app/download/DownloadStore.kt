package com.mousike.app.download

import android.content.Context
import androidx.work.*
import com.mousike.app.data.model.Track
import com.mousike.app.data.model.TrackCodec
import com.mousike.app.settings.AppSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest

data class DownloadEntry(val track: Track,val state: String,val progress: Int=0,val path: String="",val error: String="")
class DownloadStore private constructor(private val context: Context) {
    private val prefs=context.getSharedPreferences("mousike_downloads",Context.MODE_PRIVATE)
    private val mutable=MutableStateFlow(load())
    val state: StateFlow<List<DownloadEntry>> = mutable
    private fun load(): List<DownloadEntry> = runCatching {
        val a=JSONArray(prefs.getString("entries","[]")); (0 until a.length()).map { i ->
            val o=a.getJSONObject(i); DownloadEntry(TrackCodec.decode(o.getJSONObject("track")),o.optString("state"),o.optInt("progress"),o.optString("path"),o.optString("error"))
        }
    }.getOrDefault(emptyList())
    @Synchronized fun update(entry: DownloadEntry) {
        val list=mutable.value.filter { it.track.id!=entry.track.id }+entry
        val a=JSONArray(); list.forEach { e -> a.put(JSONObject().put("track",TrackCodec.encode(e.track)).put("state",e.state).put("progress",e.progress).put("path",e.path).put("error",e.error)) }
        prefs.edit().putString("entries",a.toString()).apply(); mutable.value=list
    }
    fun completedFile(id: String): File? = mutable.value.firstOrNull { it.track.id==id && it.state=="Complete" }?.path
        ?.takeIf { it.isNotBlank() }?.let { File(it) }?.takeIf { it.isFile && it.length()>0 }
    fun enqueue(track: Track) {
        if(completedFile(track.id)!=null) return
        update(DownloadEntry(track,"Queued"))
        val network=when {
            track.streamUrl.startsWith("asset:") -> NetworkType.NOT_REQUIRED
            AppSettings.get(context).state.value.wifiDownloads -> NetworkType.UNMETERED
            else -> NetworkType.CONNECTED
        }
        val request=OneTimeWorkRequestBuilder<MusicDownloadWorker>().setInputData(workDataOf("track" to TrackCodec.encode(track).toString()))
            .setConstraints(Constraints.Builder().setRequiredNetworkType(network).build()).addTag("music-download").build()
        WorkManager.getInstance(context).enqueueUniqueWork(workName(track.id),ExistingWorkPolicy.KEEP,request)
    }
    fun cancel(track: Track) {
        WorkManager.getInstance(context).cancelUniqueWork(workName(track.id))
        com.yausername.youtubedl_android.YoutubeDL.getInstance().destroyProcessById(workName(track.id))
        update(DownloadEntry(track,"Cancelled"))
    }
    @Synchronized fun delete(track: Track) {
        val oldFile=completedFile(track.id)
        cancel(track); oldFile?.delete()
        update(DownloadEntry(track,"Removed"))
    }
    fun clear() { mutable.value.forEach { e -> cancel(e.track); if(e.path.isNotBlank()) File(e.path).delete() }; prefs.edit().clear().apply(); mutable.value=emptyList() }
    fun outputDirectory(): File = File(context.filesDir,"offline_music").apply { mkdirs() }
    companion object {
        @Volatile private var instance: DownloadStore?=null
        fun get(context: Context)=instance ?: synchronized(this) { instance ?: DownloadStore(context.applicationContext).also { instance=it } }
        fun workName(id: String)="download-"+MessageDigest.getInstance("SHA-256").digest(id.toByteArray()).take(12).joinToString("") { "%02x".format(it) }
    }
}
