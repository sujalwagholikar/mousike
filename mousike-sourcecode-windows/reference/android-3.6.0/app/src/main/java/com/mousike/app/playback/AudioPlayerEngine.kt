package com.mousike.app.playback

import android.content.Context
import android.content.Intent
import android.media.audiofx.Equalizer
import androidx.core.content.ContextCompat
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.exoplayer.upstream.DefaultLoadErrorHandlingPolicy
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import com.mousike.app.data.model.PlaybackState
import com.mousike.app.data.model.RepeatMode
import com.mousike.app.data.model.Track
import com.mousike.app.data.model.TrackCodec
import com.mousike.app.download.DownloadStore
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import okhttp3.OkHttpClient
import org.json.JSONArray
import kotlin.math.PI
import kotlin.math.sin

@androidx.annotation.OptIn(UnstableApi::class)
class AudioPlayerEngine internal constructor(private val context: Context,private val http: OkHttpClient=AudioNetwork.http) {
    private val scope=CoroutineScope(SupervisorJob()+Dispatchers.Main.immediate)
    private val mutable=MutableStateFlow(PlaybackState())
    val playbackState: StateFlow<PlaybackState> = mutable.asStateFlow()
    private val amplitudes=MutableStateFlow(List(16){.08f})
    val visualizerAmplitudes: StateFlow<List<Float>> = amplitudes
    var onTrackStarted: ((Track)->Unit)?=null
    var onTrackCompleted: ((Track)->Unit)?=null
    var onTrackSkipped: ((Track,Int)->Unit)?=null
    var onQueueEnding: ((Track)->Unit)?=null
    private val repository by lazy { com.mousike.app.data.repository.MusicRepository(context) }
    private var radioJob: Job?=null
    private var radioFor=""
    private val extractor=YtDlpExtractor.get(context)
    private var eq: Equalizer?=null
    private var timer: Job?=null
    private var tracked: Track?=null
    private var listenedMs=0L
    private var lastPosition=0L
    private var completedLogged=false
    private var retryId=""
    private var startedId=""
    private var tick=0
    private var persistJob: Job?=null
    private var warmJob: Job?=null
    private var wantsPlayback=false
    internal var startupTimeoutMs=30000L
    internal var stallTimeoutMs=3500L
    private val resolvedItems=java.util.concurrent.ConcurrentHashMap.newKeySet<String>()
    private val bufferingGuard=com.mousike.app.performance.BufferingWatchdog()
    private val streamUris=java.util.concurrent.ConcurrentHashMap<String,String>()
    private val transportBytes=java.util.concurrent.ConcurrentHashMap<String,java.util.concurrent.atomic.AtomicLong>()
    private var sameSourceRetried=false
    private val progressGuard=com.mousike.app.performance.PlaybackProgress()
    private var recoveries=0
    private var freshRetried=false
    private var compatibilityRetried=false
    private val cache by lazy {AudioCache.get(context)}
    private val queuePrefs=context.getSharedPreferences("mousike_queue",Context.MODE_PRIVATE)
    val player: ExoPlayer
    init {
        val localFactory=DefaultMediaSourceFactory(DefaultDataSource.Factory(context))
        val factory=StreamingMediaSourceFactory(localFactory,
            {id,quality,signal -> extractor.resolve(id,quality=quality,signal=signal)},
            {stream ->
                val segmented=OkHttpDataSource.Factory(http).setDefaultRequestProperties(stream.headers)
                    .setUserAgent(stream.headers["User-Agent"] ?: NativeAudioResolver.USER_AGENT)
                val network=if(stream.isHls)segmented else ResumingAudioDataSource.Factory(
                    http.newBuilder().readTimeout(minOf(3000,http.readTimeoutMillis).toLong(),java.util.concurrent.TimeUnit.MILLISECONDS).build(),stream.headers)
                val cached=CacheDataSource.Factory().setCache(cache).setUpstreamDataSourceFactory(network)
                    .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
                // Files/assets bypass the network cache entirely.
                DefaultMediaSourceFactory(DefaultDataSource.Factory(context,cached).setTransferListener(object: androidx.media3.datasource.TransferListener {
                    override fun onTransferInitializing(source: androidx.media3.datasource.DataSource,spec: DataSpec,isNetwork: Boolean) {}
                    override fun onTransferStart(source: androidx.media3.datasource.DataSource,spec: DataSpec,isNetwork: Boolean) {}
                    override fun onTransferEnd(source: androidx.media3.datasource.DataSource,spec: DataSpec,isNetwork: Boolean) {}
                    override fun onBytesTransferred(source: androidx.media3.datasource.DataSource,spec: DataSpec,isNetwork: Boolean,bytes: Int) {
                        transportBytes.computeIfAbsent(stream.url){java.util.concurrent.atomic.AtomicLong()}.addAndGet(bytes.toLong())
                    }
                }))
                    .setLoadErrorHandlingPolicy(DefaultLoadErrorHandlingPolicy(0))
            },{id ->resolvedItems.add(id)},{id ->resolvedItems.remove(id)},
            {id,url ->streamUris[id]=url})
        // A 300 ms teaser was too easy to drain before the second network read arrived.
        val loadControl=DefaultLoadControl.Builder().setBufferDurationsMs(15000,60000,1000,2500)
            .setPrioritizeTimeOverSizeThresholds(true).build()
        val selector=DefaultTrackSelector(context).apply {
            setParameters(buildUponParameters().setTrackTypeDisabled(C.TRACK_TYPE_VIDEO,true))
        }
        player=ExoPlayer.Builder(context).setLoadControl(loadControl).setTrackSelector(selector)
            .setMediaSourceFactory(factory).build().apply {
            setAudioAttributes(AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MUSIC).build(),true)
            setHandleAudioBecomingNoisy(true); setWakeMode(C.WAKE_MODE_LOCAL)
            addListener(object: Player.Listener {
                override fun onMediaItemTransition(mediaItem: MediaItem?,reason: Int) {
                    val new=mutable.value.queue.firstOrNull { it.id==mediaItem?.mediaId }
                    if(tracked?.id!=new?.id) {
                        bufferingGuard.reset()
                        mutable.update {it.copy(error=null,loadingLabel="Connecting")}
                        new?.let {PlaybackDiagnostics.begin(it.videoId)}
                        recordExit(reason==Player.MEDIA_ITEM_TRANSITION_REASON_AUTO)
                        recoveries=0;sameSourceRetried=false;freshRetried=false;compatibilityRetried=false
                        tracked=new; listenedMs=0; lastPosition=0; completedLogged=false; startedId=""; retryId=""
                    }
                    refresh(); persist()
                }
                override fun onPlaybackStateChanged(playbackState: Int) {
                    refresh()
                    if(playbackState==Player.STATE_BUFFERING) {
                        if(startedId==mutable.value.currentTrack?.id)mutable.value.currentTrack?.let {PlaybackDiagnostics.stage(it.videoId,"Buffering")}
                        
                    }
                    if(playbackState==Player.STATE_READY) {
                        mutable.value.queue.getOrNull(mutable.value.queueIndex+1)?.let {extractor.prefetch(listOf(it))}
                        val t=mutable.value.currentTrack
                        if(t!=null && startedId!=t.id) {
                            startedId=t.id; onTrackStarted?.invoke(t)
                            scope.launch { repository.recordTrackPlayed(t.id,0,false) }
                            scope.launch { runCatching { repository.lyricsFor(t) }.onSuccess { if(it.isNotBlank()) updateCurrentLyrics(t.id,it) } }
                        }
                        if(mutable.value.queueIndex>=mutable.value.queue.lastIndex-2) t?.let { requestRadio(it); onQueueEnding?.invoke(it) }
                    }
                    if(playbackState==Player.STATE_ENDED) recordExit(true)
                }
                override fun onIsPlayingChanged(isPlaying: Boolean) {if(isPlaying){bufferingGuard.reset();mutable.value.currentTrack?.let {PlaybackDiagnostics.stage(it.videoId,"Playing")}};refresh()}
                override fun onPlayWhenReadyChanged(playWhenReady: Boolean,reason: Int) {
                    wantsPlayback=playWhenReady
                    if(!playWhenReady)bufferingGuard.reset()
                    refresh()
                }
                override fun onEvents(player: Player,events: Player.Events) {
                    // Combined state is authoritative; diagnostics are never a playback state.
                    refresh()
                }
                override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) { refresh() }
                override fun onRepeatModeChanged(repeatMode: Int) { refresh() }
                override fun onAudioSessionIdChanged(audioSessionId: Int) { createEqualizer(audioSessionId) }
                override fun onPositionDiscontinuity(oldPosition: Player.PositionInfo,newPosition: Player.PositionInfo,reason: Int) {
                    lastPosition=newPosition.positionMs
                    if(reason==Player.DISCONTINUITY_REASON_SEEK && oldPosition.mediaItemIndex!=newPosition.mediaItemIndex) refresh()
                }
                override fun onPlayerError(error: PlaybackException) {
                    val t=mutable.value.currentTrack
                    if(recoverPlayback())return
                    bufferingGuard.reset();wantsPlayback=false
                    mutable.value.currentTrack?.let {extractor.invalidate(it.videoId)}
                    wantsPlayback=false;player.playWhenReady=false
                    val detail=generateSequence(error as Throwable) {it.cause}.take(12).last().message.orEmpty()
                    t?.let {PlaybackDiagnostics.stage(it.videoId,detail)}
                    mutable.update {it.copy(isPlaying=false,isBuffering=false,error="Couldn't connect to this source. Use Retry or choose another song.")}
                }
            })
        }
        restore()
        scope.launch {PlaybackDiagnostics.status.collect {status ->
            if(mutable.value.currentTrack?.videoId==status.videoId && mutable.value.isBuffering)
                mutable.update {it.copy(loadingLabel=status.stage)}
        }}
        scope.launch {
            while(isActive) {
                val pos=player.currentPosition.coerceAtLeast(0)
                if(player.isPlaying) {
                    val delta=pos-lastPosition
                    if(delta in 1..2000) listenedMs+=delta
                }
                if(progressGuard.stalled(pos,player.isPlaying,android.os.SystemClock.elapsedRealtime(),stallTimeoutMs)) {
                    if(!recoverPlayback(allowSameSource=true))failWaiting("Audio stopped responding. Use Retry or choose another song.")
                }
                monitorBuffering();lastPosition=pos; refresh()
                // Decorative motion, not an audio spectrum measurement. No microphone permission.
                tick++
                if(player.isPlaying) amplitudes.value=List(16){ i -> (.18+.5*kotlin.math.abs(sin(tick*.22+i*.8))).toFloat() }
                else if(amplitudes.value.firstOrNull()!=.08f)amplitudes.value=List(16){.08f}
                if(tick%40==0 && player.isPlaying)persist()
                delay(if(player.isPlaying || wantsPlayback)250 else 700)
            }
        }
    }
    private fun recordExit(natural: Boolean) {
        val t=tracked ?: return
        if(completedLogged) return
        val duration=(t.durationSec*1000L).takeIf {it>0} ?: mutable.value.durationMs.coerceAtLeast(listenedMs+1)
        val completion=(listenedMs*100/duration).coerceIn(0,100).toInt()
        if(natural || listenedMs>3000) {
            scope.launch { repository.recordTrackPlayed(t.id,completion,completion<75) }
            if(completion>=75) onTrackCompleted?.invoke(t) else onTrackSkipped?.invoke(t,completion)
        }
        completedLogged=true
    }
    private fun requestRadio(t: Track) {
        if(!com.mousike.app.settings.AppSettings.get(context).state.value.autoplay || t.streamUrl.startsWith("asset:") || radioFor==t.id) return
        radioFor=t.id
        radioJob?.cancel(); radioJob=scope.launch {
            runCatching { repository.relatedTracks(t.id) }.onSuccess { related ->
                if(mutable.value.currentTrack?.id==t.id) related.filter { it.endMs!=null || it.durationSec in 1..480 }.take(10).forEach { addToQueue(it) }
            }
        }
    }
    private fun item(t: Track): MediaItem {
        val local=DownloadStore.get(context).completedFile(t.id)
        val uri=when {
            local!=null -> android.net.Uri.fromFile(local)
            t.streamUrl.isNotBlank() -> android.net.Uri.parse(t.streamUrl)
            else -> android.net.Uri.parse("mousike://video/${t.videoId}")
        }
        val b=MediaItem.Builder().setMediaId(t.id).setUri(uri)
        if(uri.scheme=="mousike")b.setCustomCacheKey(AudioCache.key(t.videoId,com.mousike.app.settings.AppSettings.get(context).state.value.audioQuality))
        b
            .setMediaMetadata(MediaMetadata.Builder().setTitle(t.title).setArtist(t.artist).setAlbumTitle(t.album).setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC).setIsPlayable(true).setIsBrowsable(false)
                .setArtworkUri(t.coverUrl.takeIf { it.isNotBlank() }?.let { android.net.Uri.parse(it) }).build())
        if(local==null && (t.startMs>0 || t.endMs!=null)) {
            val clip=MediaItem.ClippingConfiguration.Builder().setStartPositionMs(t.startMs)
            t.endMs?.let { clip.setEndPositionMs(it) }; b.setClippingConfiguration(clip.build())
        }
        return b.build()
    }
    fun playTrack(track: Track,newQueue: List<Track> = emptyList(),startIndex: Int = -1) {
        val q=(if(newQueue.isNotEmpty()) newQueue else listOf(track)).distinctBy {it.id}
        wantsPlayback=true
        if(mutable.value.currentTrack?.id==track.id && mutable.value.isBuffering && mutable.value.queue.map {it.id}==q.map {it.id}) {
            player.play();return
        }
        if(mutable.value.currentTrack?.id==track.id && mutable.value.queue.map {it.id}==q.map {it.id} && player.playbackState!=Player.STATE_IDLE && mutable.value.error==null) {
            player.seekTo(0);player.play();refresh();return
        }
        bufferingGuard.reset()
        resolvedItems.clear()
        PlaybackDiagnostics.begin(track.videoId);wantsPlayback=true
        recordExit(false)
        val index=q.indexOfFirst { it.id==track.id }.coerceAtLeast(0)
        mutable.update { it.copy(currentTrack=track,queue=q,queueIndex=index,isBuffering=true,isPlaying=false,error=null,loadingLabel="Connecting",currentPositionMs=0,durationMs=track.durationSec*1000L) }
        recoveries=0;sameSourceRetried=false;freshRetried=false;compatibilityRetried=false
        tracked=track; listenedMs=0; lastPosition=0; completedLogged=false; startedId=""
        streamUris.clear();transportBytes.clear()
        player.setMediaItems(q.map { item(it) },index,0); player.prepare(); player.play();ensureService()
        persist()
    }
    fun updateTrackMetadata(track: Track) {
        mutable.update {s -> s.copy(currentTrack=s.currentTrack?.let {if(it.id==track.id)track else it},queue=s.queue.map {if(it.id==track.id)track else it})}
        if(tracked?.id==track.id)tracked=track
    }
    /** A failed/stalled representation must not hold the same queue behind repeated Play taps. */
    private fun recoverPlayback(allowSameSource: Boolean=false): Boolean {
        val state=mutable.value;val t=state.currentTrack ?: return false
        if(!wantsPlayback || state.queue.isEmpty())return false
        // Rebuild once with the already resolved URI/cache, as next-and-back used to do.
        // Do not reject a healthy signed address merely because its parser/loader stalled.
        val sameSource=allowSameSource && !sameSourceRetried
        if(sameSource)sameSourceRetried=true
        else {
            if(t.streamUrl.isNotBlank() || recoveries>=3)return false
            when {
            extractor.useAlternate(t.videoId) -> Unit
            !freshRetried -> {freshRetried=true;extractor.retryFresh(t.videoId)}
            !compatibilityRetried -> {compatibilityRetried=true;extractor.useFallback(t.videoId)}
                else -> return false
            }
            recoveries++
        }
        resolvedItems.clear()
        PlaybackDiagnostics.stage(t.videoId,"Connecting")
        val position=player.currentPosition.coerceAtLeast(0)
        mutable.update {it.copy(error=null,isBuffering=true,loadingLabel="Connecting")}
        player.setMediaItems(state.queue.map {item(it)},state.queueIndex.coerceIn(state.queue.indices),position)
        player.prepare();player.play();return true
    }
    /** Sample every tick, including after the first sound and rapid READY -> BUFFERING events. */
    private fun monitorBuffering() {
        val state=mutable.value;val track=state.currentTrack
        val waiting=player.playbackState==Player.STATE_BUFFERING || player.playbackState==Player.STATE_IDLE && state.error==null
        val action=bufferingGuard.poll(track?.id,wantsPlayback,player.isPlaying,waiting,
            track!=null && (track.id in resolvedItems || track.streamUrl.isNotBlank()),
            player.currentPosition,player.bufferedPosition,
            track?.id?.let {streamUris[it]}?.let {transportBytes[it]?.get()} ?: 0L,
            android.os.SystemClock.elapsedRealtime(),startupTimeoutMs,stallTimeoutMs)
        when(action) {
            com.mousike.app.performance.BufferingWatchdog.Action.NONE -> Unit
            com.mousike.app.performance.BufferingWatchdog.Action.RECOVER -> {
                if(!recoverPlayback(allowSameSource=true))failWaiting("Audio stopped responding. Use Retry or choose another song.")
            }
            com.mousike.app.performance.BufferingWatchdog.Action.TIMEOUT -> failWaiting("Couldn't connect to this source. Use Retry or choose another song.")
        }
    }
    private fun failWaiting(message: String) {
        wantsPlayback=false;player.pause();player.stop();bufferingGuard.reset()
        mutable.update {it.copy(isPlaying=false,isBuffering=false,playWhenReady=false,error=message)}
    }
    private fun ensureService() {
        runCatching { ContextCompat.startForegroundService(context,Intent(context,PlaybackService::class.java)) }
            .onFailure { mutable.update { it.copy(error="Open Mousike to start background playback") } }
    }
    private fun refresh() {
        val q=mutable.value.queue
        val t=q.firstOrNull { it.id==player.currentMediaItem?.mediaId } ?: mutable.value.currentTrack
        mutable.update { it.copy(currentTrack=t,isPlaying=player.isPlaying,
            error=if(player.isPlaying)null else it.error,
            loadingLabel=if(player.isPlaying)"" else it.loadingLabel,
            isBuffering=!player.isPlaying && wantsPlayback && it.error==null && (player.playbackState==Player.STATE_BUFFERING || player.playbackState==Player.STATE_IDLE),
            playWhenReady=player.playWhenReady && wantsPlayback,
            currentPositionMs=player.currentPosition.coerceAtLeast(0),
            durationMs=player.duration.takeIf { d -> d>0 } ?: ((t?.durationSec ?: 0)*1000L),
            queueIndex=player.currentMediaItemIndex.coerceAtLeast(0),isShuffle=player.shuffleModeEnabled,
            repeatMode=when(player.repeatMode){Player.REPEAT_MODE_ONE->RepeatMode.ONE;Player.REPEAT_MODE_ALL->RepeatMode.ALL;else->RepeatMode.OFF},
            playbackSpeed=player.playbackParameters.speed) }
    }
    fun togglePlayPause() {
        if(wantsPlayback || player.playWhenReady)pause() else resume()
    }
    fun pause() {
        wantsPlayback=false;bufferingGuard.reset();player.pause()
        // Keep the prepared source and buffered audio, so resume does not resolve it again.
        refresh();persist()
    }
    fun resume() {
        wantsPlayback=true
        if(mutable.value.error!=null || player.playbackState==Player.STATE_IDLE) {
            val state=mutable.value;state.currentTrack?.let {playTrack(it,state.queue)};return
        }
        ensureService();if(player.playbackState==Player.STATE_ENDED){player.seekTo(0);player.prepare()};player.play()
    }
    fun seekTo(positionMs: Long) { player.seekTo(positionMs.coerceIn(0,mutable.value.durationMs.coerceAtLeast(0))); refresh() }
    fun playNext() {
        wantsPlayback=true
        if(player.hasNextMediaItem()) {
            recordExit(false);player.seekToNextMediaItem();if(player.playbackState==Player.STATE_IDLE || player.playbackState==Player.STATE_ENDED)player.prepare();player.play();refresh();return
        }
        val current=mutable.value.currentTrack ?: return
        radioJob?.cancel()
        radioJob=scope.launch {
            val local=repository.rankCandidates(repository.getAllTracks().filter {it.id!=current.id && it.durationSec in 1..480},current).firstOrNull()
            val next=local ?: runCatching {repository.relatedTracks(current.id).firstOrNull {it.id!=current.id}}.getOrNull()
            if(mutable.value.currentTrack?.id==current.id && next!=null)playTrack(next,listOf(next))
            else if(next==null)mutable.update {it.copy(error="No next song is available yet. Choose a song or try again.")}
        }
    }
    fun playPrevious() { if(player.currentPosition>3000) player.seekTo(0) else if(player.hasPreviousMediaItem()) player.seekToPreviousMediaItem(); refresh() }
    fun toggleShuffle() { player.shuffleModeEnabled=!player.shuffleModeEnabled }
    fun cycleRepeatMode() { player.repeatMode=(player.repeatMode+1)%3 }
    fun setPlaybackSpeed(speed: Float) { player.setPlaybackSpeed(speed.coerceIn(.5f,2f)); refresh() }
    fun addToQueue(t: Track) { if(mutable.value.queue.none { it.id==t.id }) { mutable.update { it.copy(queue=it.queue+t) }; player.addMediaItem(item(t)); persist() } }
    fun playNextInQueue(t: Track) {
        val index=(player.currentMediaItemIndex+1).coerceIn(0,mutable.value.queue.size)
        val q=mutable.value.queue.toMutableList().apply { add(index,t) }
        mutable.update { it.copy(queue=q) }; player.addMediaItem(index,item(t)); persist()
    }
    fun moveQueue(from: Int,to: Int) {
        if(from !in mutable.value.queue.indices || to !in mutable.value.queue.indices) return
        val q=mutable.value.queue.toMutableList(); q.add(to,q.removeAt(from)); mutable.update { it.copy(queue=q) }
        player.moveMediaItem(from,to); refresh(); persist()
    }
    fun removeFromQueue(index: Int) {
        if(index !in mutable.value.queue.indices) return
        mutable.update { it.copy(queue=it.queue.toMutableList().apply { removeAt(index) }) }
        player.removeMediaItem(index); refresh(); persist()
    }
    fun clearQueue() {
        val t=mutable.value.currentTrack ?: return
        val pos=player.currentPosition; val playing=player.playWhenReady
        resolvedItems.clear()
        mutable.update { it.copy(queue=listOf(t),queueIndex=0) }; player.setMediaItems(listOf(item(t)),0,pos)
        player.prepare(); player.playWhenReady=playing; persist()
    }
    fun updateCurrentLyrics(trackId: String,lrc: String) {
        mutable.update { s -> s.copy(currentTrack=s.currentTrack?.let { if(it.id==trackId) it.copy(lyrics=lrc) else it },queue=s.queue.map { if(it.id==trackId) it.copy(lyrics=lrc) else it }) }
    }
    fun setSleepTimer(minutes: Int?) {
        timer?.cancel(); mutable.update { it.copy(sleepTimerMinutes=minutes,sleepTimerRemainingSec=(minutes ?: 0)*60) }
        if(minutes!=null) timer=scope.launch { var seconds=minutes*60; while(seconds>0) { delay(1000); seconds--; mutable.update { it.copy(sleepTimerRemainingSec=seconds) } }; pause(); mutable.update { it.copy(sleepTimerMinutes=null) } }
    }
    private fun createEqualizer(id: Int) {
        runCatching { eq?.release(); eq=Equalizer(0,id).apply { enabled=true }; applyEq() }
    }
    private fun applyEq() {
        runCatching {
            eq?.let { e ->
                val range=e.bandLevelRange; val state=mutable.value
                for(i in 0 until e.numberOfBands.toInt()) {
                    val x=when { i<e.numberOfBands/3 -> state.bassBoost; i>=e.numberOfBands*2/3 -> state.trebleBoost; else->state.vocalClarity }
                    val level=((x-.5f)*2*range[1]).toInt().coerceIn(range[0].toInt(),range[1].toInt()).toShort()
                    e.setBandLevel(i.toShort(),level)
                }
            }
        }
    }
    fun setEqualizerPreset(preset: String) {
        val levels=when(preset){"Bass Boost"->Triple(.85f,.5f,.55f);"Vocal","Vocal Clarity"->Triple(.4f,.85f,.6f);"Treble"->Triple(.4f,.5f,.85f);"Acoustic Studio"->Triple(.45f,.65f,.65f);"Electronic Beats"->Triple(.8f,.4f,.7f);"Rock & Alt"->Triple(.7f,.6f,.75f);else->Triple(.5f,.5f,.5f)}
        mutable.update { it.copy(equalizerPreset=preset,bassBoost=levels.first,vocalClarity=levels.second,trebleBoost=levels.third) }; applyEq()
    }
    fun setCustomEqualizer(bass: Float,vocal: Float,treble: Float) { mutable.update { it.copy(equalizerPreset="Custom",bassBoost=bass,vocalClarity=vocal,trebleBoost=treble) }; applyEq() }
    fun reset() {wantsPlayback=false;bufferingGuard.reset();persistJob?.cancel();warmJob?.cancel();player.stop(); player.clearMediaItems(); mutable.value=PlaybackState();persistJob?.cancel();warmJob?.cancel(); queuePrefs.edit().clear().apply(); tracked=null; setSleepTimer(null) }
    fun release() {bufferingGuard.reset();persistJob?.cancel();warmJob?.cancel();timer?.cancel(); eq?.release(); player.release(); scope.cancel() }
    private fun persist() {
        val tracks=mutable.value.queue
        val index=player.currentMediaItemIndex.coerceAtLeast(0)
        val position=player.currentPosition.coerceAtLeast(0)
        persistJob?.cancel()
        persistJob=scope.launch(Dispatchers.IO) {
            delay(200)
            val a=JSONArray();tracks.forEach {a.put(TrackCodec.encode(it))}
            queuePrefs.edit().putString("tracks",a.toString()).putInt("index",index).putLong("position",position).apply()
        }
    }
    private fun restore() { runCatching {
        val a=JSONArray(queuePrefs.getString("tracks","[]")); val tracks=(0 until a.length()).map { TrackCodec.decode(a.getJSONObject(it)) }
        if(tracks.isNotEmpty()) {
            val index=queuePrefs.getInt("index",0).coerceIn(tracks.indices)
            mutable.update { it.copy(queue=tracks,currentTrack=tracks[index],queueIndex=index) }
            player.setMediaItems(tracks.map { item(it) },index,queuePrefs.getLong("position",0)); player.playWhenReady=false
        }
    } }
    companion object {
        @Volatile private var instance: AudioPlayerEngine?=null
        fun getInstance(context: Context): AudioPlayerEngine = instance ?: synchronized(this) { instance ?: AudioPlayerEngine(context.applicationContext).also { instance=it } }
    }
}
