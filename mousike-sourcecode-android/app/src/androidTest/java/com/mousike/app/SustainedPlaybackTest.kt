package com.mousike.app

import android.content.Context
import android.os.SystemClock
import androidx.media3.common.Player
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mousike.app.data.model.Track
import com.mousike.app.playback.*
import com.mousike.app.settings.AppSettings
import okhttp3.OkHttpClient
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.Closeable
import java.net.InetAddress
import java.net.Socket
import java.security.KeyStore
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import javax.net.ssl.*

/** Actual HTTPS + ExoPlayer audio, including a server that stops after the first sound. */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@RunWith(AndroidJUnit4::class)
class SustainedPlaybackTest {
    private val context get()=ApplicationProvider.getApplicationContext<Context>()
    private fun main(action: ()->Unit)=InstrumentationRegistry.getInstrumentation().runOnMainSync(action)
    private fun until(ms: Long=12000,predicate: ()->Boolean) {
        val deadline=SystemClock.elapsedRealtime()+ms
        while(!predicate()){assertTrue("Playback did not recover before deadline",SystemClock.elapsedRealtime()<deadline);Thread.sleep(20)}
    }
    private class MediaServer(context: Context,m4a: Boolean=false): Closeable {
        val requests=CopyOnWriteArrayList<Pair<String,Long>>()
        val stalled=CountDownLatch(1)
        private val sentBurst=AtomicBoolean()
        private val stop=CountDownLatch(1)
        private val sockets=CopyOnWriteArrayList<Socket>()
        private val workers=Executors.newCachedThreadPool()
        private val audio=if(m4a)InstrumentationRegistry.getInstrumentation().context.assets.open("range-check.m4a").use {it.readBytes()}
            else context.assets.open("demo/soundcheck-1.wav").use {it.readBytes()}
        private val mime=if(m4a)"audio/mp4" else "audio/wav"
        private val tls: SSLContext
        val client: OkHttpClient
        private val server: SSLServerSocket
        val base: String
        init {
            val keys=KeyStore.getInstance("PKCS12")
            InstrumentationRegistry.getInstrumentation().context.assets.open("loopback-test.p12").use {keys.load(it,"localtest".toCharArray())}
            val km=KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm()).apply {init(keys,"localtest".toCharArray())}
            val tm=TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm()).apply {init(keys)}
            val trust=tm.trustManagers.filterIsInstance<X509TrustManager>().single()
            tls=SSLContext.getInstance("TLS").apply {init(km.keyManagers,arrayOf(trust),null)}
            server=tls.serverSocketFactory.createServerSocket(0,20,InetAddress.getByName("127.0.0.1")) as SSLServerSocket
            base="https://127.0.0.1:${server.localPort}"
            client=OkHttpClient.Builder().sslSocketFactory(tls.socketFactory,trust)
                .connectTimeout(2,TimeUnit.SECONDS).readTimeout(1500,TimeUnit.MILLISECONDS).build()
            workers.submit {
                while(stop.count>0)try {
                    val socket=server.accept();sockets.add(socket)
                    workers.submit {socket.use {runCatching {serve(it)}};sockets.remove(socket)}
                }catch(_: Exception){break}
            }
        }
        private fun serve(socket: Socket) {
            socket.soTimeout=8000
            val reader=socket.getInputStream().bufferedReader()
            val path=reader.readLine().split(' ')[1];var range=""
            while(true){val line=reader.readLine() ?: return;if(line.isEmpty())break;if(line.startsWith("Range:",true))range=line.substringAfter(':').trim()}
            assertTrue("Media requests must have a finite end",Regex("bytes=\\d+-\\d+").matches(range))
            val parts=range.removePrefix("bytes=").split('-').map {it.toLong()}
            val start=parts[0];val end=minOf(parts[1],audio.lastIndex.toLong())
            requests.add(path to start)
            val output=socket.getOutputStream()
            if(start>=audio.size){output.write("HTTP/1.1 416 Range Not Satisfiable\r\nContent-Range: bytes */${audio.size}\r\nContent-Length: 0\r\nConnection: close\r\n\r\n".toByteArray());output.flush();return}
            output.write("HTTP/1.1 206 Partial Content\r\nContent-Type: $mime\r\nContent-Range: bytes $start-$end/${audio.size}\r\nContent-Length: ${end-start+1}\r\nConnection: close\r\n\r\n".toByteArray())
            if(path=="/resume" && sentBurst.compareAndSet(false,true) || path=="/dead" && start==0L && sentBurst.compareAndSet(false,true)) {
                val count=minOf(end-start+1,48044L).toInt() // 1.5 s of real audio, then silence on the wire.
                output.write(audio,start.toInt(),count);output.flush();stalled.countDown();stop.await(8,TimeUnit.SECONDS)
            } else if(path=="/dead") {output.flush();stalled.countDown();stop.await(8,TimeUnit.SECONDS)}
            else {output.write(audio,start.toInt(),(end-start+1).toInt());output.flush()}
        }
        override fun close(){stop.countDown();server.close();sockets.forEach {runCatching {it.close()}};workers.shutdownNow();client.dispatcher.executorService.shutdownNow();client.connectionPool.evictAll()}
    }
    private fun field(target: Any,name: String)=target.javaClass.getDeclaredField(name).apply {isAccessible=true}
    @Suppress("UNCHECKED_CAST")
    private fun seed(extractor: YtDlpExtractor,id: String,url: String,alternate: String?=null) {
        val stream=YtDlpExtractor.Stream(url,emptyMap(),Long.MAX_VALUE,native=true,format="https-test")
        (field(extractor,"streams").get(extractor) as MutableMap<String,YtDlpExtractor.Stream>)[AudioCache.key(id,"High")]=stream
        (field(extractor,"checkedAt").get(extractor) as MutableMap<String,Long>)[url]=System.currentTimeMillis()
        if(alternate!=null)(field(extractor,"alternatives").get(extractor) as MutableMap<String,List<YtDlpExtractor.Stream>>)[AudioCache.key(id,"High")]=listOf(stream.copy(url=alternate))
    }
    private fun fixture(m4a: Boolean=false,block: (AudioPlayerEngine,MediaServer,YtDlpExtractor)->Unit) {
        val oldFixture=RuntimeMode.offlineFixture;RuntimeMode.offlineFixture=true
        AppSettings.get(context).update {it.copy(autoplay=false,audioQuality="High")}
        val cache=AudioCache.get(context)
        cache.keys.filter {it.contains("rangeflow")}.forEach {cache.removeResource(it)}
        val extractor=YtDlpExtractor.get(context)
        val verifierField=field(extractor,"verifier");val oldVerifier=verifierField.get(extractor)
        MediaServer(context,m4a).use {server ->
            verifierField.set(extractor,StreamVerifier(server.client))
            lateinit var engine: AudioPlayerEngine
            main {engine=AudioPlayerEngine(context,server.client);engine.stallTimeoutMs=700;engine.startupTimeoutMs=10000}
            try {block(engine,server,extractor)}catch(e: Throwable) {
                var state=""
                main {state="state=${engine.player.playbackState} playing=${engine.player.isPlaying} playWhenReady=${engine.player.playWhenReady} pos=${engine.player.currentPosition} buffer=${engine.player.bufferedPosition} recoveries=${field(engine,"recoveries").get(engine)} wants=${field(engine,"wantsPlayback").get(engine)}"}
                throw AssertionError("$state requests=${server.requests} app=${engine.playbackState.value} diagnostics=${PlaybackDiagnostics.copyText()}",e)
            }finally {
                main {engine.reset();engine.release()}
                listOf("rangeflow01","rangeflow02","rangeflow03","rangeflow04","rangeflow05").forEach {extractor.invalidate(it)}
                verifierField.set(extractor,oldVerifier);RuntimeMode.offlineFixture=oldFixture
            }
        }
    }
    private fun track(id: String)=Track(id,"HTTPS stability check","Mousike","",30,"","","Music",2026)
    @Test fun audioStartsThenStalledHttpReadResumesFromLastByteAndKeepsPlaying()=fixture {engine,server,extractor ->
        val id="rangeflow01";seed(extractor,id,server.base+"/resume")
        val rebuffered=AtomicBoolean();var hadSound=false
        main {engine.stallTimeoutMs=6000;engine.player.addListener(object: Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean){if(playing)hadSound=true}
            override fun onPlaybackStateChanged(state: Int){if(hadSound && state==Player.STATE_BUFFERING)rebuffered.set(true)}
        });engine.playTrack(track(id))}
        until {engine.playbackState.value.isPlaying}
        assertTrue(server.stalled.await(2,TimeUnit.SECONDS))
        until {server.requests.any {it.first=="/resume" && it.second>0} && engine.playbackState.value.isPlaying && engine.playbackState.value.currentPositionMs>4000}
        assertNull(engine.playbackState.value.error)
        assertEquals(id,engine.playbackState.value.currentTrack?.id)
        android.util.Log.i("MousikeSustained","MID_READ_RESUMED requests=${server.requests.size} position=${engine.playbackState.value.currentPositionMs} rebuffer=${rebuffered.get()}")
    }
    @Test fun startedSongThatBuffersForeverAutomaticallySwitchesToHealthyRoute()=fixture {engine,server,extractor ->
        val id="rangeflow02";seed(extractor,id,server.base+"/dead",server.base+"/good")
        val rebuffered=AtomicBoolean();var hadSound=false
        main {engine.player.addListener(object: Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean){if(playing)hadSound=true}
            override fun onPlaybackStateChanged(state: Int){if(hadSound && state==Player.STATE_BUFFERING){rebuffered.set(true);PlaybackDiagnostics.stage(id,"Connecting")}}
        });engine.playTrack(track(id))}
        until {engine.playbackState.value.isPlaying}
        until {server.requests.any {it.first=="/good"} && engine.playbackState.value.isPlaying && engine.playbackState.value.currentPositionMs>3500}
        assertTrue("Fixture must reproduce playback followed by buffering",rebuffered.get())
        assertNull(engine.playbackState.value.error);assertEquals(id,engine.playbackState.value.currentTrack?.id)
    }
    @Test fun nextDuringStalledMediaStartsTheNextSongWithOneTap()=fixture {engine,server,extractor ->
        val first=track("rangeflow03");val next=track("rangeflow04")
        seed(extractor,first.id,server.base+"/dead");seed(extractor,next.id,server.base+"/good")
        main {engine.stallTimeoutMs=6000;engine.playTrack(first,listOf(first,next))}
        until {engine.playbackState.value.isPlaying}
        until {engine.playbackState.value.isBuffering}
        val clicked=SystemClock.elapsedRealtime()
        main {engine.playNext()}
        until(6000){engine.playbackState.value.currentTrack?.id==next.id && engine.playbackState.value.isPlaying}
        assertNull(engine.playbackState.value.error)
        android.util.Log.i("MousikeSustained","NEXT_DURING_STALL_MS=${SystemClock.elapsedRealtime()-clicked}")
    }
    @Test fun aacWithTailIndexPlaysAcrossRangesAndSeeksBeyondTheFirstChunk()=fixture(m4a=true) {engine,server,extractor ->
        val id="rangeflow05";seed(extractor,id,server.base+"/m4a")
        main {engine.stallTimeoutMs=4500;engine.startupTimeoutMs=30000;engine.playTrack(track(id).copy(durationSec=60))}
        until {engine.playbackState.value.isPlaying}
        main {assertTrue(engine.player.duration>59000);engine.seekTo(45000)}
        until {engine.playbackState.value.isPlaying && engine.playbackState.value.currentPositionMs>45500}
        assertTrue(server.requests.size>=2);assertTrue(server.requests.any {it.second>256*1024})
        assertNull(engine.playbackState.value.error)
    }
}
