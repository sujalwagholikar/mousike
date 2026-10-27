package com.mousike.app.performance

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.datasource.DataSpec
import com.mousike.app.playback.ResumingAudioDataSource
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import okio.BufferedSource
import okio.Source
import okio.Timeout
import okio.buffer
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.SocketTimeoutException

@org.junit.runner.RunWith(org.robolectric.RobolectricTestRunner::class)
@org.robolectric.annotation.Config(sdk=[28],manifest=org.robolectric.annotation.Config.NONE)
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class ResumingAudioTest {
    private val bytes=ByteArray(10003){(it%251).toByte()}
    private fun spec(position: Long=0,length: Long=C.LENGTH_UNSET.toLong())=DataSpec.Builder().setUri(Uri.parse("https://media.test/audio"))
        .setPosition(position).setLength(length).build()
    private fun response(request: Request,start: Int,end: Int,body: ResponseBody=bytes.copyOfRange(start,end+1).toResponseBody("audio/mp4".toMediaType()))=
        Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(206).message("Partial Content")
            .header("Content-Range","bytes $start-$end/${bytes.size}").body(body).build()
    private fun readAll(source: ResumingAudioDataSource): ByteArray {
        val out=ByteArrayOutputStream();val buffer=ByteArray(137)
        while(true){val n=source.read(buffer,0,buffer.size);if(n==C.RESULT_END_OF_INPUT)break;out.write(buffer,0,n)}
        return out.toByteArray()
    }
    private fun range(request: Request): Pair<Int,Int> {
        val parts=request.header("Range")!!.removePrefix("bytes=").split('-').map {it.toInt()}
        return parts[0] to minOf(parts[1],bytes.lastIndex)
    }
    @Test fun consecutiveRangesExposeTheWholeResourceNotTheFirstChunk() {
        val requests=mutableListOf<Pair<Int,Int>>()
        val http=OkHttpClient.Builder().addInterceptor {chain->val (a,b)=range(chain.request());requests.add(a to b);response(chain.request(),a,b)}.build()
        val source=ResumingAudioDataSource(http,emptyMap(),1024)
        try {assertEquals(bytes.size.toLong(),source.open(spec()));assertArrayEquals(bytes,readAll(source));assertEquals(10,requests.size);assertEquals(0 to 1023,requests.first());assertEquals(9216 to 10002,requests.last())}
        finally {source.close()}
    }
    @Test fun timeoutAfterSomeAudioResumesAtTheLastDeliveredByteWithoutDuplication() {
        val requests=mutableListOf<Pair<Int,Int>>();var first=true
        val http=OkHttpClient.Builder().addInterceptor {chain->
            val (a,b)=range(chain.request());requests.add(a to b)
            if(first) {
                first=false
                val body=object: ResponseBody() {
                    private val data=object: Source {
                        var delivered=false
                        override fun read(sink: Buffer,byteCount: Long): Long {
                            if(delivered)throw SocketTimeoutException("Server stopped after initial audio")
                            delivered=true;sink.write(bytes,0,300);return 300
                        }
                        override fun timeout()=Timeout.NONE
                        override fun close(){}
                    }.buffer()
                    override fun contentType()="audio/mp4".toMediaType()
                    override fun contentLength()=(b-a+1).toLong()
                    override fun source(): BufferedSource=data
                }
                response(chain.request(),a,b,body)
            } else response(chain.request(),a,b)
        }.build()
        val source=ResumingAudioDataSource(http,emptyMap(),1024)
        try {source.open(spec());assertArrayEquals(bytes,readAll(source));assertEquals(300,requests[1].first)}finally {source.close()}
    }
    @Test fun prematureEofAlsoResumesRatherThanTruncatingTheSong() {
        var first=true;val starts=mutableListOf<Int>()
        val http=OkHttpClient.Builder().addInterceptor {chain->
            val (a,b)=range(chain.request());starts.add(a)
            if(first){first=false;response(chain.request(),a,b,bytes.copyOfRange(a,a+233).toResponseBody("audio/mp4".toMediaType()))}
            else response(chain.request(),a,b)
        }.build()
        val source=ResumingAudioDataSource(http,emptyMap(),1024)
        try {source.open(spec());assertArrayEquals(bytes,readAll(source));assertEquals(233,starts[1])}finally {source.close()}
    }
    @Test fun seekAndExplicitLengthDoNotReadOrCacheTheWrongBytes() {
        val http=OkHttpClient.Builder().addInterceptor {chain->val (a,b)=range(chain.request());response(chain.request(),a,b)}.build()
        val source=ResumingAudioDataSource(http,emptyMap(),1024)
        try {assertEquals(2345L,source.open(spec(1234,2345)));assertArrayEquals(bytes.copyOfRange(1234,3579),readAll(source))}finally {source.close()}
    }
    @Test fun incorrectContentRangeFailsBeforeWritingCorruptBytes() {
        var calls=0
        val http=OkHttpClient.Builder().addInterceptor {chain->calls++;response(chain.request(),0,1023)}.build()
        val source=ResumingAudioDataSource(http,emptyMap(),1024)
        try {source.open(spec(2000));fail("Offset mismatch must be rejected")}catch(_: IOException){assertEquals(1,calls)}finally {source.close()}
    }
    @Test fun unavailableSourceHasBoundedRetries() {
        var calls=0
        val http=OkHttpClient.Builder().addInterceptor {calls++;throw SocketTimeoutException("No bytes")}.build()
        val source=ResumingAudioDataSource(http,emptyMap(),1024)
        try {source.open(spec());fail("Unresponsive source must fail")}catch(_: IOException){assertEquals(3,calls)}finally {source.close()}
    }
    @Test fun forbiddenAddressIsNotRepeated() {
        var calls=0
        val http=OkHttpClient.Builder().addInterceptor {chain->calls++;Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(403).message("Forbidden").body(ByteArray(0).toResponseBody()).build()}.build()
        val source=ResumingAudioDataSource(http,emptyMap(),1024)
        try {source.open(spec());fail("Forbidden must fail")}catch(_: IOException){assertEquals(1,calls)}finally {source.close()}
    }
    @Test fun serverIgnoringRangePlaysWithoutDuplicatingItsPrefix() {
        var calls=0
        val http=OkHttpClient.Builder().addInterceptor {chain->calls++;Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("OK").body(bytes.toResponseBody("audio/mp4".toMediaType())).build()}.build()
        val source=ResumingAudioDataSource(http,emptyMap(),1024)
        try {assertEquals(bytes.size.toLong(),source.open(spec()));assertArrayEquals(bytes,readAll(source));assertEquals(1,calls)}finally {source.close()}
    }
}
