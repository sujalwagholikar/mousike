package com.mousike.app.playback

import android.os.Handler
import android.os.Looper
import androidx.media3.common.*
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.*
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.drm.DrmSessionManagerProvider
import androidx.media3.exoplayer.source.*
import androidx.media3.exoplayer.upstream.*
import com.mousike.app.performance.ResolutionSignal
import okhttp3.OkHttpClient
import java.io.IOException
import java.util.concurrent.Executors
import java.util.concurrent.Future

/** Resolve before selecting the media parser. A virtual URI must not force HLS into an MP4 parser. */
@androidx.annotation.OptIn(UnstableApi::class)
class StreamingMediaSource(private val original: MediaItem,
    private val resolver: (String,String,ResolutionSignal)->YtDlpExtractor.Stream,
    private val sourceFactory: (YtDlpExtractor.Stream)->MediaSource.Factory,
    private val onResolved: (String)->Unit={},private val onPreparing: (String)->Unit={},
    private val onStreamResolved: (String,String)->Unit={_,_->}): CompositeMediaSource<Int>() {
    private var child: MediaSource?=null
    private var generation=0
    private var failure: IOException?=null
    private var signal=ResolutionSignal()
    private var work: Future<*>?=null
    override fun getMediaItem(): MediaItem=original
    override fun prepareSourceInternal(listener: TransferListener?) {
        super.prepareSourceInternal(listener)
        failure=null;signal=ResolutionSignal();val current=++generation
        val active=signal
        val handler=Handler(checkNotNull(Looper.myLooper()))
        val config=checkNotNull(original.localConfiguration)
        val id=checkNotNull(config.uri.lastPathSegment)
        onPreparing(original.mediaId)
        val quality=config.customCacheKey?.removePrefix("audio:")?.substringBeforeLast(':') ?: "High"
        work=workers.submit {
            try {
                val stream=resolver(id,quality,active);active.check()
                handler.post {
                    if(current==generation && !active.isCancelled) {
                        try {
                            PlaybackDiagnostics.stage(id,"Buffering")
                            val resolved=resolvedItem(original,stream)
                            child=sourceFactory(stream).createMediaSource(resolved)
                            onStreamResolved(original.mediaId,stream.url)
                            onResolved(original.mediaId)
                            prepareChildSource(0,checkNotNull(child))
                        } catch(e: Exception) {failure=IOException("Could not prepare audio: ${e.message}",e)}
                    }
                }
            } catch(e: Exception) {handler.post {if(current==generation)failure=IOException(e.message ?: "Source unavailable",e)}}
        }
    }
    override fun maybeThrowSourceInfoRefreshError() {failure?.let {throw it};super.maybeThrowSourceInfoRefreshError()}
    override fun onChildSourceInfoRefreshed(id: Int,mediaSource: MediaSource,timeline: Timeline) {refreshSourceInfo(timeline)}
    override fun createPeriod(id: MediaSource.MediaPeriodId,allocator: Allocator,startPositionUs: Long): MediaPeriod = checkNotNull(child).createPeriod(id,allocator,startPositionUs)
    override fun releasePeriod(period: MediaPeriod) {child?.releasePeriod(period)}
    override fun releaseSourceInternal() {generation++;signal.cancel();work?.cancel(true);super.releaseSourceInternal();child=null;failure=null}
    companion object {
        private val workers=Executors.newFixedThreadPool(2) {r ->Thread(r,"selected-audio").apply {isDaemon=true;priority=Thread.NORM_PRIORITY}}
        fun resolvedItem(item: MediaItem,stream: YtDlpExtractor.Stream): MediaItem=item.buildUpon().setUri(stream.url)
            .setMimeType(if(stream.isHls)MimeTypes.APPLICATION_M3U8 else null)
            // HLS assigns cache keys per segment; a shared MP4 key would corrupt segment caching.
            .setCustomCacheKey(if(stream.isHls)null else item.localConfiguration?.customCacheKey?.let {
                // Older unbounded responses could leave a misleading cached resource length.
                val key=if(stream.format.isBlank())it else "$it:format:${stream.format}"
                "$key:transport:ranges-v1"
            }).build()
    }
}

@androidx.annotation.OptIn(UnstableApi::class)
class StreamingMediaSourceFactory(private val local: MediaSource.Factory,
    private val resolver: (String,String,ResolutionSignal)->YtDlpExtractor.Stream,
    private val remote: (YtDlpExtractor.Stream)->MediaSource.Factory,
    private val onResolved: (String)->Unit={},private val onPreparing: (String)->Unit={},
    private val onStreamResolved: (String,String)->Unit={_,_->}): MediaSource.Factory {
    override fun createMediaSource(item: MediaItem): MediaSource =
        if(item.localConfiguration?.uri?.scheme=="mousike")StreamingMediaSource(item,resolver,remote,onResolved,onPreparing,onStreamResolved) else local.createMediaSource(item)
    override fun getSupportedTypes()=intArrayOf(C.CONTENT_TYPE_OTHER,C.CONTENT_TYPE_HLS)
    override fun setDrmSessionManagerProvider(provider: DrmSessionManagerProvider)=apply {local.setDrmSessionManagerProvider(provider)}
    override fun setLoadErrorHandlingPolicy(policy: LoadErrorHandlingPolicy)=apply {local.setLoadErrorHandlingPolicy(policy)}
}
