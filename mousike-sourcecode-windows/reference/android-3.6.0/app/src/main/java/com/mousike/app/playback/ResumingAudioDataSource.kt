package com.mousike.app.playback

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.BaseDataSource
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.EOFException
import java.io.IOException
import java.io.InputStream
import java.io.InterruptedIOException

/** Bounded progressive requests, with byte-exact continuation after a dropped connection.
 * The extractor and cache see the complete resource, never the end of an HTTP chunk.
 * HLS keeps its own segment loader and does not use this source.
 */
@androidx.annotation.OptIn(UnstableApi::class)
class ResumingAudioDataSource(private val http: OkHttpClient,private val headers: Map<String,String>,
    private val chunkBytes: Long=256*1024L): BaseDataSource(true) {
    class Factory(private val http: OkHttpClient,private val headers: Map<String,String> = emptyMap(),
        private val chunkBytes: Long=256*1024L): DataSource.Factory {
        override fun createDataSource()=ResumingAudioDataSource(http,headers,chunkBytes)
    }
    private var spec: DataSpec?=null
    private var cursor=0L
    private var end=C.LENGTH_UNSET.toLong()
    private var chunkEnd=0L
    private var response: Response?=null
    private var input: InputStream?=null
    @Volatile private var call: Call?=null
    private var started=false
    private var retries=0
    private var ranges=true
    override fun getUri(): Uri?=spec?.uri
    override fun getResponseHeaders(): Map<String,List<String>> = response?.headers?.toMultimap() ?: emptyMap()
    override fun open(dataSpec: DataSpec): Long {
        require(chunkBytes>0)
        spec=dataSpec;cursor=dataSpec.position
        end=if(dataSpec.length==C.LENGTH_UNSET.toLong())C.LENGTH_UNSET.toLong() else cursor+dataSpec.length
        retries=0;ranges=true
        transferInitializing(dataSpec)
        try {connectWithRetry();started=true;transferStarted(dataSpec)}
        catch(e: IOException){disconnect();throw e}
        return if(end==C.LENGTH_UNSET.toLong())C.LENGTH_UNSET.toLong() else end-cursor
    }
    private fun checkCancelled() {if(Thread.currentThread().isInterrupted)throw InterruptedIOException("Audio read cancelled")}
    private fun disconnect() {call?.cancel();call=null;input=null;response?.close();response=null}
    private fun retry(error: IOException) {
        disconnect();checkCancelled()
        // Auth/range failures need a different representation, not another identical request.
        if(error is InvalidAudioResponse || !ranges || retries++>=2)throw error
    }
    private fun connectWithRetry() {
        while(true) {try {connect();return}catch(e: IOException){retry(e)}}
    }
    private fun connect() {
        checkCancelled()
        if(end!=C.LENGTH_UNSET.toLong() && cursor>=end)return
        val s=checkNotNull(spec)
        val requestedEnd=minOf(cursor+chunkBytes,end.takeIf {it!=C.LENGTH_UNSET.toLong()} ?: Long.MAX_VALUE)
        val request=Request.Builder().url(s.uri.toString()).apply {
            headers.forEach { (k,v)->header(k,v) };s.httpRequestHeaders.forEach { (k,v)->header(k,v) }
            header("Accept-Encoding","identity");header("Range","bytes=$cursor-${requestedEnd-1}")
        }.build()
        val active=http.newCall(request);call=active
        val r=active.execute();response=r
        if(r.code==416) {
            val total=Regex("bytes \\*/(\\d+)").matchEntire(r.header("Content-Range").orEmpty())?.groupValues?.get(1)?.toLongOrNull()
            if(total==cursor){end=cursor;disconnect();return}
        }
        if(!r.isSuccessful) {
            if(r.code in 500..599)throw IOException("Audio HTTP ${r.code}")
            throw InvalidAudioResponse("Audio HTTP ${r.code}")
        }
        val body=r.body ?: throw IOException("Empty audio response")
        val mime=r.header("Content-Type").orEmpty()
        if(mime.contains("html",true) || mime.contains("json",true))throw InvalidAudioResponse("Source returned a webpage instead of audio")
        if(r.code==206) {
            val match=Regex("bytes (\\d+)-(\\d+)/(\\d+|\\*)").matchEntire(r.header("Content-Range").orEmpty())
                ?: throw InvalidAudioResponse("Missing media byte range")
            val first=match.groupValues[1].toLong();val last=match.groupValues[2].toLong()
            if(first!=cursor || last<first || last>=requestedEnd)throw InvalidAudioResponse("Incorrect media byte range")
            match.groupValues[3].toLongOrNull()?.let {total ->
                if(total<=last)throw InvalidAudioResponse("Incorrect media length")
                end=if(end==C.LENGTH_UNSET.toLong())total else minOf(end,total)
            }
            chunkEnd=last+1
        } else {
            // A server that ignores Range can still play once from zero; never duplicate bytes on resume.
            if(cursor!=0L)throw InvalidAudioResponse("Source cannot resume audio")
            ranges=false
            if(body.contentLength()>=0)end=if(end==C.LENGTH_UNSET.toLong())body.contentLength() else minOf(end,body.contentLength())
            chunkEnd=if(end==C.LENGTH_UNSET.toLong())Long.MAX_VALUE else end
        }
        input=body.byteStream()
    }
    override fun read(buffer: ByteArray,offset: Int,length: Int): Int {
        if(length==0)return 0
        while(true) {
            checkCancelled()
            if(end!=C.LENGTH_UNSET.toLong() && cursor>=end)return C.RESULT_END_OF_INPUT
            if(input==null)connectWithRetry()
            if(end!=C.LENGTH_UNSET.toLong() && cursor>=end)return C.RESULT_END_OF_INPUT
            try {
                val amount=minOf(length.toLong(),chunkEnd-cursor).toInt()
                val read=checkNotNull(input).read(buffer,offset,amount)
                if(read<0) {
                    if(!ranges && end==C.LENGTH_UNSET.toLong()){end=cursor;return C.RESULT_END_OF_INPUT}
                    throw EOFException("Audio connection ended before its declared range")
                }
                if(read==0)continue
                cursor+=read;bytesTransferred(read)
                if(cursor==chunkEnd){disconnect();retries=0}
                return read
            } catch(e: IOException){retry(e)}
        }
    }
    override fun close() {
        disconnect();spec=null
        if(started){started=false;transferEnded()}
    }
    private class InvalidAudioResponse(message: String): IOException(message)
}
