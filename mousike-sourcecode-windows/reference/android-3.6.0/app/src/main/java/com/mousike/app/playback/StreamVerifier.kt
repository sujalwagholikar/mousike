package com.mousike.app.playback

import com.mousike.app.performance.ResolutionSignal
import okhttp3.*
import java.io.IOException
import java.util.concurrent.*

/** A player JSON result is not proof that its CDN address serves playable bytes. */
open class StreamVerifier(private val http: OkHttpClient=AudioNetwork.http.newBuilder().connectTimeout(3,TimeUnit.SECONDS)
    .readTimeout(4,TimeUnit.SECONDS).callTimeout(6000,TimeUnit.MILLISECONDS).build()) {
    open fun verify(stream: YtDlpExtractor.Stream,signal: ResolutionSignal): YtDlpExtractor.Stream {
        signal.check()
        val request=Request.Builder().url(stream.url).apply {stream.headers.forEach { (k,v)->header(k,v)}}
            .header("Range","bytes=0-1023").build()
        val call=http.newCall(request);signal.onCancel {call.cancel()}
        call.execute().use {r ->
            if(!r.isSuccessful)throw IOException("Audio HTTP ${r.code}")
            val body=r.body ?: throw IOException("Empty audio response")
            // Read a bounded prefix, not the whole track. request() is subject to the call deadline.
            val source=body.source();source.request(64)
            val bytes=source.buffer.clone().readByteArray(minOf(1024L,source.buffer.size))
            validate(stream.isHls,r.header("Content-Type").orEmpty(),bytes)
            signal.check();return stream
        }
    }
    fun select(candidates: List<YtDlpExtractor.Stream>,signal: ResolutionSignal,onRejected: (YtDlpExtractor.Stream)->Unit={}): YtDlpExtractor.Stream {
        require(candidates.isNotEmpty())
        // Preferred AAC gets a short head start; an unusable/slow source cannot hide the HLS route.
        val selected=candidates.distinctBy {it.url}.take(3)
        val completion=ExecutorCompletionService<YtDlpExtractor.Stream>(workers)
        val children=selected.map {ResolutionSignal()}
        val jobs=selected.mapIndexed {i,s ->completion.submit(Callable {
            if(i>0)Thread.sleep(180L*i)
            try {verify(s,children[i])}
            catch(e: Exception){if(!children[i].isCancelled && !signal.isCancelled)onRejected(s);throw e}
        })}
        signal.onCancel {children.forEach {it.cancel()};jobs.forEach {it.cancel(true)}}
        var failure: Throwable?=null
        try {
            repeat(jobs.size) {
                signal.check()
                try {return (completion.poll(7000,TimeUnit.MILLISECONDS) ?: throw IOException("Audio address timed out")).get().also {signal.check()}}
                catch(e: ExecutionException){failure=e.cause}
            }
            throw IOException(failure?.message ?: "No media address responded",failure)
        } catch(_: InterruptedException){Thread.currentThread().interrupt();throw IOException("Audio selection cancelled")}
        finally {children.forEach {it.cancel()};jobs.forEach {it.cancel(true)}}
    }
    companion object {
        private val workers=Executors.newCachedThreadPool {r->Thread(r,"audio-validation").apply {isDaemon=true}}
        fun validate(hls: Boolean,mime: String,bytes: ByteArray) {
            if(bytes.isEmpty())throw IOException("Empty media response")
            val text=bytes.toString(Charsets.UTF_8).trimStart('\uFEFF',' ','\n','\r','\t')
            val lower=text.lowercase()
            if(mime.contains("html",true) || mime.contains("json",true) || lower.startsWith("<") || lower.startsWith("{"))throw IOException("Source returned a webpage instead of audio")
            if(hls) {if(!text.startsWith("#EXTM3U"))throw IOException("Invalid audio playlist");return}
            val mp4=bytes.size>=8 && String(bytes,4,4,Charsets.US_ASCII) in listOf("ftyp","styp","moov","moof")
            val webm=bytes.size>=4 && bytes[0]==0x1a.toByte() && bytes[1]==0x45.toByte() && bytes[2]==0xdf.toByte() && bytes[3]==0xa3.toByte()
            val wave=text.startsWith("RIFF") || text.startsWith("ID3") || text.startsWith("OggS") || text.startsWith("fLaC")
            val sync=bytes.size>=2 && bytes[0]==0xff.toByte() && (bytes[1].toInt() and 0xe0)==0xe0
            if(!mp4 && !webm && !wave && !sync)throw IOException("Unrecognised audio response")
        }
    }
}
