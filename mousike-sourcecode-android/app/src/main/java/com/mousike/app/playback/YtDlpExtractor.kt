package com.mousike.app.playback

import android.content.Context
import com.mousike.app.settings.AppSettings
import com.mousike.app.performance.FastMetadata
import com.mousike.app.performance.SingleFlightCache
import com.mousike.app.performance.ResolutionSignal
import com.mousike.app.performance.BoundedResolution
import com.mousike.app.performance.ForegroundResolver
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import com.yausername.ffmpeg.FFmpeg
import kotlinx.coroutines.*
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap

class YtDlpExtractor private constructor(private val context: Context) {
    data class Stream(val url: String,val headers: Map<String,String>,val expiresAt: Long,val isHls: Boolean=false,val native: Boolean=false,val format: String="")
    private val streams=ConcurrentHashMap<String,Stream>()
    private val native=NativeAudioResolver()
    private val background=CoroutineScope(SupervisorJob()+Dispatchers.IO.limitedParallelism(1))
    private val connections=com.mousike.app.performance.ConnectionWork<String,NativeAudioResolver.Result>()
    private val verifier=StreamVerifier()
    private val publicationEpoch=java.util.concurrent.atomic.AtomicLong()
    private val foreground=java.util.concurrent.atomic.AtomicInteger()
    private val alternatives=java.util.concurrent.ConcurrentHashMap<String,List<Stream>>()
    private val checkedAt=java.util.concurrent.ConcurrentHashMap<String,Long>()
    private val rejected=java.util.concurrent.ConcurrentHashMap<String,Long>()
    private fun cacheNative(id: String,key: String,result: NativeAudioResolver.Result) {
        if(streams.size>=32)streams.keys.firstOrNull {it!=key}?.let {streams.remove(it);alternatives.remove(it)}
        alternatives[key]=(result.candidates+listOfNotNull(result.alternate)).filter {it.url!=result.stream.url}.distinctBy {it.url}
        if(checkedAt.size>=128)checkedAt.clear()
        streams[key]=result.stream;checkedAt[result.stream.url]=System.currentTimeMillis();remember(id,result.metadata)
    }
    private fun nativeConnection(id: String,quality: String,signal: ResolutionSignal): NativeAudioResolver.Result {
        val result=native.resolve(id,quality,signal) ?: throw IOException("No native audio candidates")
        val candidates=(result.candidates+listOfNotNull(result.stream,result.alternate)).distinctBy {it.url}
            .filter {candidate ->candidate.expiresAt>System.currentTimeMillis()+15000 && !isRejected(candidate.url)}
        if(candidates.isEmpty())throw IOException("No usable audio candidates")
        val selected=verifier.select(candidates,signal){reject(it.url)}
        return result.copy(stream=selected,candidates=candidates.filter {!isRejected(it.url)},alternate=candidates.firstOrNull {it.url!=selected.url && !isRejected(it.url)})
    }
    private fun cancelWarmup(){connections.cancelSpeculationExcept(null)}
    private val nativeFailed=ConcurrentHashMap<String,Long>()
    private fun nativeTemporarilyFailed(id: String)=nativeFailed[id]?.let {System.currentTimeMillis()-it<60000} ?: false
    private val infoWork=SingleFlightCache<String,JSONObject>(128)
    private val metadata=ConcurrentHashMap<String,JSONObject>()
    private val fast=FastMetadata(File(context.cacheDir,"metadata-v1"))
    @Volatile private var initialized=false
    @Volatile private var ffmpegReady=false
    @Volatile var initializationError: String?=null; private set
    @Synchronized fun initialize() {
        if(initialized)return
        try { YoutubeDL.getInstance().init(context);initialized=true;initializationError=null }
        catch(e: Exception) { initializationError="Audio runtime could not start: ${e.message}";throw e }
    }
    /** FFmpeg extraction is needed by downloads, never by normal stream playback. */
    @Synchronized fun ensureFfmpeg() {
        if(ffmpegReady)return
        initialize();FFmpeg.getInstance().init(context)
        val cpp=File(context.applicationInfo.nativeLibraryDir,"libc++_shared.so")
        val ffLib=File(context.noBackupFilesDir,"youtubedl-android/packages/ffmpeg/usr/lib/libc++_shared.so")
        if(cpp.isFile && (!ffLib.exists() || ffLib.length()!=cpp.length()))cpp.copyTo(ffLib,overwrite=true)
        ffmpegReady=true
    }
    private fun request(id: String)=YoutubeDLRequest("https://music.youtube.com/watch?v=$id").apply {
        require(id.matches(Regex("[A-Za-z0-9_-]{11}"))) { "Invalid YouTube video ID" }
        addOption("--no-playlist");addOption("--no-warnings");addOption("--socket-timeout",8)
        addOption("--retries",0);addOption("--extractor-retries",0)
        // The Android wrapper defaults to --no-cache-dir. Enable player JS caching explicitly.
        addOption("--cache-dir",File(context.cacheDir,"yt-dlp-cache").absolutePath)
    }
    private fun remember(id: String,o: JSONObject): JSONObject {
        metadata[id]=o
        if(metadata.size>128)metadata.keys.firstOrNull { it!=id }?.let { metadata.remove(it) }
        background.launch {fast.remember(id,o)};return o
    }
    suspend fun info(id: String): JSONObject=withContext(Dispatchers.IO) { infoBlocking(id) }
    fun infoBlocking(id: String): JSONObject {
        metadata[id]?.let {return it}
        return infoWork.get(id) {
            metadata[id] ?: fast.fetch(id)?.let {remember(id,it)} ?: run {
                val signal=ResolutionSignal()
                BoundedResolution.run(4500,signal) {
                    initialize();signal.check()
                    val process="metadata-$id-${System.nanoTime()}"
                    signal.onCancel {YoutubeDL.getInstance().destroyProcessById(process)}
                    val r=request(id).apply {
                        addOption("--dump-single-json");addOption("--skip-download");addOption("--ignore-no-formats-error")
                        addOption("--extractor-args","youtube:skip=dash,hls,translated_subs;player_skip=js,initial_data")
                    }
                    signal.check()
                    val out=YoutubeDL.getInstance().execute(r,process,null).out
                    signal.check();remember(id,JSONObject(out.trim()))
                }
            }
        }
    }
    fun resolve(id: String,force: Boolean=false,quality: String=AppSettings.get(context).state.value.audioQuality,
        signal: ResolutionSignal=ResolutionSignal()): Stream {
        require(id.matches(Regex("[A-Za-z0-9_-]{11}"))) {"Invalid YouTube video ID"}
        signal.check()
        val key=AudioCache.key(id,quality)
        connections.cancelSpeculationExcept(key)
        if(force)streams.remove(key)
        streams[key]?.takeIf {it.expiresAt>System.currentTimeMillis()+60000}?.let {cached ->
            if((checkedAt[cached.url] ?: 0)>System.currentTimeMillis()-120000)return cached
            try {verifier.verify(cached,signal);checkedAt[cached.url]=System.currentTimeMillis();return cached}
            catch(e: Exception){signal.check();streams.remove(key,cached);reject(cached.url)}
        }
        publicationEpoch.incrementAndGet();foreground.incrementAndGet()
        try {return ForegroundResolver.resolve(signal,delayMs=if(nativeTemporarilyFailed(id))0 else 2500,budgetMs=22000,
            primary=primary@{request ->
                if(nativeTemporarilyFailed(id))null else {
                    val options=alternatives[key].orEmpty().filter {!isRejected(it.url)}
                    if(options.isNotEmpty()) {
                        runCatching {verifier.select(options,request){reject(it.url)}}.getOrNull()?.let {chosen ->
                            return@primary NativeAudioResolver.Result(chosen,metadata[id] ?: JSONObject(),candidates=options)
                        }
                    }
                    connections.get(key,request,18000) {nativeConnection(id,quality,it)}
                }
            },fallback={extraction ->
                PlaybackDiagnostics.stage(id,"Trying another connection")
                val o=BoundedResolution.run(19000,extraction) {
                    initialize();extraction.check()
                    val process="selected-$id-${System.nanoTime()}"
                    extraction.onCancel {YoutubeDL.getInstance().destroyProcessById(process)}
                    val format=com.mousike.app.settings.AudioQualityPolicy.ytDlpFormat(quality)
                    val r=request(id).apply {
                        addOption("--dump-single-json");addOption("--skip-download");addOption("-f",format)
                        addOption("--extractor-args","youtube:skip=dash,translated_subs;player_skip=initial_data")
                    }
                    extraction.check()
                    JSONObject(YoutubeDL.getInstance().execute(r,process,null).out.trim()).also {extraction.check()}
                }
                extraction.check()
                val stream=parseExtracted(id,key,o)
                verifier.verify(stream,extraction)
                NativeAudioResolver.Result(stream,o)
            }).let {result ->
                signal.check();cacheNative(id,key,result);result.stream
            }} finally {foreground.decrementAndGet()}
    }
    private fun parseExtracted(id: String,key: String,o: JSONObject): Stream {
        val url=o.optString("url")
        if(!url.startsWith("https://"))throw IOException("No playable public audio source was returned")
        val h=o.optJSONObject("http_headers") ?: JSONObject()
        val headers=h.keys().asSequence().associateWith {h.optString(it)}
        val expires=Regex("[?&]expire=(\\d+)").find(url)?.groupValues?.get(1)?.toLongOrNull()?.times(1000)
            ?: (System.currentTimeMillis()+10*60*1000)
        val isHls=o.optString("protocol").startsWith("m3u8") || url.contains(".m3u8") || url.contains("/manifest/hls_")
        remember(id,o)
        if(streams.size>=32)streams.keys.firstOrNull()?.let {streams.remove(it)}
        return Stream(url,headers,expires,isHls,format=o.optString("format_id"))
    }
    /** Warm only one validated connection; a tap joins it instead of cancelling/restarting it. */
    @Synchronized fun prefetch(tracks: List<com.mousike.app.data.model.Track>) {
        if(!AppSettings.get(context).state.value.preloadNext || com.mousike.app.RuntimeMode.offlineFixture || foreground.get()>0)return
        val track=tracks.firstOrNull {it.streamUrl.isBlank() && it.videoId.matches(Regex("[A-Za-z0-9_-]{11}")) && !nativeTemporarilyFailed(it.videoId)} ?: return
        val quality=AppSettings.get(context).state.value.audioQuality;val key=AudioCache.key(track.videoId,quality)
        if(hasCachedStream(track.videoId,quality))return
        val epoch=publicationEpoch.get()
        connections.prepare(key,{signal ->Thread.sleep(500);nativeConnection(track.videoId,quality,signal)},{result ->if(publicationEpoch.get()==epoch)cacheNative(track.videoId,key,result)})
    }
    fun useAlternate(id: String): Boolean {
        val key=AudioCache.key(id,AppSettings.get(context).state.value.audioQuality)
        streams.remove(key)?.let {reject(it.url)}
        val options=alternatives[key].orEmpty().filter {it.expiresAt>System.currentTimeMillis()+60000 && !isRejected(it.url)}
        // Health-check alternates on the connection worker, never the UI thread.
        if(options.isEmpty())return false
        alternatives[key]=options.drop(1);streams[key]=options.first();checkedAt.remove(options.first().url)
        PlaybackDiagnostics.stage(id,"Connecting");return true
    }
    private fun isRejected(url: String): Boolean {
        val at=rejected[url] ?: return false
        if(System.currentTimeMillis()-at>=60000){rejected.remove(url,at);return false};return true
    }
    private fun reject(url: String) {
        if(rejected.size>=128)rejected.clear()
        rejected[url]=System.currentTimeMillis();checkedAt.remove(url)
    }
    fun retryFresh(id: String) {
        val quality=AppSettings.get(context).state.value.audioQuality
        val key=AudioCache.key(id,quality)
        streams.remove(key)?.let {reject(it.url)};alternatives.remove(key);connections.discard(key);nativeFailed.remove(id)
    }
    fun hasCachedStream(id: String,quality: String)=streams[AudioCache.key(id,quality)]?.expiresAt?.let {it>System.currentTimeMillis()+60000} ?: false
    fun hasNativeStream(id: String)=streams.entries.any {it.key.endsWith(":$id") && it.value.native}
    fun useFallback(id: String) {cancelWarmup();if(nativeFailed.size>=128)nativeFailed.clear();nativeFailed[id]=System.currentTimeMillis();invalidate(id)}
    fun invalidate(id: String) { listOf("Data saver","High","Normal",AppSettings.get(context).state.value.audioQuality).forEach { streams.remove(AudioCache.key(id,it));alternatives.remove(AudioCache.key(id,it)) } }
    suspend fun update(): String=withContext(Dispatchers.IO) {
        initialize();YoutubeDL.getInstance().updateYoutubeDL(context,YoutubeDL.UpdateChannel.STABLE)
        metadata.clear();infoWork.clear();streams.clear();"Extractor updated. Retry playback."
    }
    companion object {
        @Volatile private var instance: YtDlpExtractor?=null
        fun get(context: Context)=instance ?: synchronized(this) {instance ?: YtDlpExtractor(context.applicationContext).also {instance=it}}
    }
}
