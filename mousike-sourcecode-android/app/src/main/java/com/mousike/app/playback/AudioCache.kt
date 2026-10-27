package com.mousike.app.playback

import android.content.Context
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import java.io.File

@androidx.annotation.OptIn(UnstableApi::class)
object AudioCache {
    @Volatile private var instance: SimpleCache?=null
    fun get(context: Context): SimpleCache=instance ?: synchronized(this) {
        instance ?: SimpleCache(File(context.cacheDir,"audio-v2"),LeastRecentlyUsedCacheEvictor(64L*1024*1024),StandaloneDatabaseProvider(context.applicationContext)).also {instance=it}
    }
    fun key(id: String,quality: String)="audio:$quality:$id"
}
