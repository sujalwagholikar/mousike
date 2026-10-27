package com.mousike.app
import android.app.Application
import com.mousike.app.playback.VisitorSession
import coil.ImageLoader
import coil.ImageLoaderFactory
class MousikeApplication: Application(),ImageLoaderFactory {
    override fun onCreate(){super.onCreate();VisitorSession.initialize(this)}
    override fun newImageLoader(): ImageLoader {
        val dispatcher=okhttp3.Dispatcher().apply {maxRequests=4;maxRequestsPerHost=2}
        val http=okhttp3.OkHttpClient.Builder().dispatcher(dispatcher).connectTimeout(5,java.util.concurrent.TimeUnit.SECONDS).readTimeout(7,java.util.concurrent.TimeUnit.SECONDS).build()
        return ImageLoader.Builder(this).okHttpClient(http).crossfade(false).memoryCache {coil.memory.MemoryCache.Builder(this).maxSizePercent(.15).build()}.build()
    }
}
