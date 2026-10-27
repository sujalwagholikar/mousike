package com.mousike.app.download

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.mousike.app.R
import com.mousike.app.data.model.TrackCodec
import com.mousike.app.playback.YtDlpExtractor
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File

class MusicDownloadWorker(context: Context,params: WorkerParameters): CoroutineWorker(context,params) {
    private fun foreground(title: String,percent: Int): ForegroundInfo {
        applicationContext.getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel("downloads","Music downloads",NotificationManager.IMPORTANCE_LOW))
        val n=NotificationCompat.Builder(applicationContext,"downloads").setSmallIcon(R.drawable.ic_music_note)
            .setContentTitle(title).setContentText("Downloading for offline listening").setProgress(100,percent,percent==0).setOngoing(true).setSilent(true).build()
        return if(Build.VERSION.SDK_INT>=29) ForegroundInfo(id.hashCode().and(Int.MAX_VALUE),n,ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        else ForegroundInfo(id.hashCode().and(Int.MAX_VALUE),n)
    }
    override suspend fun doWork(): Result {
        val raw=inputData.getString("track") ?: return Result.failure()
        val track=runCatching { TrackCodec.decode(JSONObject(raw)) }.getOrNull() ?: return Result.failure()
        val store=DownloadStore.get(applicationContext)
        return try {
            setForeground(foreground(track.title,0)); store.update(DownloadEntry(track,"Downloading"))
            val base=DownloadStore.workName(track.id)
            val output=withContext(Dispatchers.IO) {
                if(track.streamUrl.startsWith("asset:///")) {
                    File(store.outputDirectory(),"$base.wav").also { target -> applicationContext.assets.open(track.streamUrl.removePrefix("asset:///" )).use { src -> target.outputStream().use { src.copyTo(it) } } }
                } else {
                    YtDlpExtractor.get(applicationContext).ensureFfmpeg()
                    require(track.videoId.matches(Regex("[A-Za-z0-9_-]{11}")))
                    val r=YoutubeDLRequest("https://www.youtube.com/watch?v=${track.videoId}").apply {
                        addOption("--no-playlist"); addOption("--no-mtime"); addOption("--newline")
                        addOption("--socket-timeout",20); addOption("--retries",3)
                        addOption("-f","bestaudio[ext=m4a]/bestaudio")
                        addOption("-o",File(store.outputDirectory(),"$base.%(ext)s").absolutePath)
                        if(track.endMs!=null) {
                            addOption("--download-sections","*${track.startMs/1000.0}-${track.endMs/1000.0}")
                            addOption("--force-keyframes-at-cuts")
                        }
                    }
                    var lastProgress=-1
                    runInterruptible {
                        YoutubeDL.getInstance().execute(r,base) { p,_,_ ->
                            val percent=p.toInt().coerceIn(0,99)
                            if(isStopped) YoutubeDL.getInstance().destroyProcessById(base)
                            if(percent!=lastProgress) { lastProgress=percent; store.update(DownloadEntry(track,"Downloading",percent)); setProgressAsync(workDataOf("progress" to percent)) }
                        }
                    }
                    store.outputDirectory().listFiles()?.firstOrNull { it.name.startsWith("$base.") && it.extension in setOf("m4a","webm","opus","mp3","mp4","ogg") }
                        ?: error("The source did not return an audio file")
                }
            }
            check(output.length()>0) { "The audio file is empty" }
            store.update(DownloadEntry(track,"Complete",100,output.absolutePath)); Result.success()
        } catch(e: kotlinx.coroutines.CancellationException) {
            YoutubeDL.getInstance().destroyProcessById(DownloadStore.workName(track.id))
            store.update(DownloadEntry(track,"Cancelled")); throw e
        } catch(e: Exception) {
            store.update(DownloadEntry(track,"Failed",error="Download failed. The source may be unavailable; check connection and extractor updates."))
            Result.failure()
        }
    }
}
