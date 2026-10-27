package com.mousike.app.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import com.mousike.app.data.model.PlaybackState
import com.mousike.app.data.model.RepeatMode
import com.mousike.app.ui.components.*
import com.mousike.app.ui.theme.*
import com.mousike.app.ui.viewmodel.MousikeViewModel

@OptIn(ExperimentalFoundationApi::class)
@Composable fun PlayerFullScreen(viewModel: MousikeViewModel,modifier: Modifier=Modifier) {
    BackHandler {viewModel.navigateBack()}
    val state by viewModel.playbackState.collectAsState()
    val liked by viewModel.likedTrackIds.collectAsState()
    val preferences by viewModel.preferences.collectAsState()
    val track=state.currentTrack ?: return
    val ambient=when(preferences.playerBackground){"Solid"->ObsidianDark;"Accent"->MousikeGreen;else->coverAmbient(track.coverUrl)}
    val artworkShape=ArtworkShape
    val immersive=preferences.playerLayout=="Immersive"
    val compact=preferences.playerLayout=="Compact"
    val buttonColor=if(preferences.playerControls=="Accent")MousikeGreen else TextPrimary
    val reduce=preferences.reduceMotion
    val tint by animateColorAsState(ambient,tween(if(reduce)0 else 650),label="player_ambience")
    var options by remember {mutableStateOf(false)}
    val artScale=animateFloatAsState(if(state.isPlaying)1f else .97f,tween(if(reduce)0 else 450),label="artwork_breath")
    val likeScale=animateFloatAsState(if(track.id in liked)1.08f else 1f,spring(stiffness=500f),label="like_response")
    val fontScale=LocalDensity.current.fontScale
    BoxWithConstraints(modifier.fillMaxSize().background(Brush.verticalGradient(listOf(tint.copy(alpha=.34f),ObsidianDark,ObsidianDark))).testTag("full_player_screen")) {
        val artRatio=(if(fontScale>1.15f).32f else if(compact).29f else if(immersive).46f else .40f)*when(preferences.playerArtworkSize){"Small"->.82f;"Large"->1.12f;else->1f}
        val artSize=minOf(maxWidth-48.dp,maxHeight*artRatio,if(immersive)390.dp else 350.dp).coerceAtLeast(112.dp)
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal=24.dp,vertical=8.dp),horizontalAlignment=Alignment.CenterHorizontally) {
            Row(Modifier.fillMaxWidth().height(44.dp),verticalAlignment=Alignment.CenterVertically) {
                IconButton({viewModel.navigateBack()},Modifier.testTag("full_player_collapse_btn")){Icon(Icons.Default.KeyboardArrowDown,"Collapse Player",tint=TextPrimary,modifier=Modifier.size(28.dp))}
                Column(Modifier.weight(1f),horizontalAlignment=Alignment.CenterHorizontally) {
                    Text("NOW PLAYING",color=TextPrimary,fontSize=10.sp,fontWeight=FontWeight.Bold,letterSpacing=2.sp)
                    if(preferences.showPlayerContext)Text(track.album.ifBlank {"Your personal soundtrack"},color=TextSecondary,fontSize=11.sp,maxLines=1,overflow=TextOverflow.Ellipsis,modifier=Modifier.padding(top=3.dp))
                }
                Box {
                    IconButton({options=true},Modifier.testTag("full_player_more_btn")){Icon(Icons.Default.MoreHoriz,"Player options",tint=TextPrimary)}
                    DropdownMenu(options,{options=false},Modifier.background(SurfaceElevated)) {
                        DropdownMenuItem(text={Text("Add to playlist")},leadingIcon={Icon(Icons.Default.PlaylistAdd,null)},onClick={options=false;viewModel.setTrackToAddToPlaylist(track)})
                        DropdownMenuItem(text={Text("Equalizer")},leadingIcon={Icon(Icons.Default.Equalizer,null)},onClick={options=false;viewModel.setEqualizerDialogVisible(true)})
                        DropdownMenuItem(text={Text("Sleep timer")},leadingIcon={Icon(Icons.Default.Schedule,null)},onClick={options=false;viewModel.setSleepTimerDialogVisible(true)})
                        DropdownMenuItem(text={Text("Tune recommendations")},leadingIcon={Icon(Icons.Default.Tune,null)},onClick={options=false;viewModel.setFeedbackDialogForTrack(track)})
                        DropdownMenuItem(text={Text("Lyrics")},leadingIcon={Icon(Icons.Default.Lyrics,null)},onClick={options=false;viewModel.openLyrics()})
                        DropdownMenuItem(text={Text("Queue")},leadingIcon={Icon(Icons.AutoMirrored.Filled.QueueMusic,null)},onClick={options=false;viewModel.openQueue()})
                        DropdownMenuItem(text={Text("Download")},leadingIcon={Icon(Icons.Default.Download,null)},onClick={options=false;viewModel.download(track)})
                        DropdownMenuItem(text={Text("Customise player")},leadingIcon={Icon(Icons.Default.Palette,null)},onClick={options=false;viewModel.navigateTo(com.mousike.app.ui.viewmodel.NavScreen.SETTINGS)})
                    }
                }
            }
            Spacer(Modifier.height(14.dp))
            AnimatedContent(targetState=track.id,transitionSpec={fadeIn(tween(if(reduce)0 else 320))+scaleIn(tween(if(reduce)0 else 360),initialScale=.96f) togetherWith fadeOut(tween(if(reduce)0 else 160))},label="player_cover_change") {id ->
                // Capture artwork by identity so an outgoing frame never adopts the next cover.
                val art=remember(id){track.coverUrl to track.title}
                Box(Modifier.size(artSize).graphicsLayer {scaleX=artScale.value;scaleY=artScale.value}
                    .shadow(if(compact)8.dp else 28.dp,artworkShape,ambientColor=tint.copy(alpha=.3f),spotColor=Color.Black.copy(alpha=.35f))
                    .clip(artworkShape).background(SurfaceCard).border(1.dp,TextPrimary.copy(alpha=.06f),artworkShape),contentAlignment=Alignment.Center) {
                    MusicArtwork(art.first,art.second,Modifier.fillMaxSize(),ContentScale.Fit)
                }
            }
            Spacer(Modifier.height(if(compact)14.dp else 22.dp))
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(track.title,color=TextPrimary,fontFamily=Manrope,fontWeight=FontWeight.ExtraBold,fontSize=if(immersive)27.sp else 23.sp,lineHeight=32.sp,maxLines=1,overflow=TextOverflow.Ellipsis,modifier=Modifier.basicMarquee(iterations=if(reduce)0 else Int.MAX_VALUE,initialDelayMillis=1800))
                    Text(track.artist,color=TextSecondary,fontSize=14.sp,maxLines=1,overflow=TextOverflow.Ellipsis,modifier=Modifier.padding(top=4.dp))
                }
                Spacer(Modifier.width(8.dp))
                IconButton({viewModel.toggleLikeTrack(track.id)},Modifier.graphicsLayer {scaleX=if(reduce)1f else likeScale.value;scaleY=if(reduce)1f else likeScale.value}.testTag("player_like")) {
                    Icon(if(track.id in liked)Icons.Default.Favorite else Icons.Outlined.FavoriteBorder,if(track.id in liked)"Unlike" else "Like",tint=if(track.id in liked)MousikeGreen else TextSecondary,modifier=Modifier.size(26.dp))
                }
            }
            Spacer(Modifier.height(6.dp))
            Box(Modifier.fillMaxWidth().heightIn(min=24.dp),contentAlignment=Alignment.CenterStart) {
                when {
                    state.error!=null -> Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                        Text("Couldn’t connect to this source",color=TextSecondary,fontSize=11.sp,modifier=Modifier.weight(1f))
                        TextButton({viewModel.togglePlayPause()},contentPadding=PaddingValues(horizontal=8.dp,vertical=0.dp)){Text("Retry",fontSize=11.sp,color=MousikeGreen)}
                    }
                    state.isBuffering -> Row(verticalAlignment=Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(12.dp),color=MousikeGreen,strokeWidth=1.5.dp);Spacer(Modifier.width(8.dp));Text(state.loadingLabel.ifBlank {"Connecting"},color=TextMuted,fontSize=11.sp,maxLines=1,overflow=TextOverflow.Ellipsis)
                    }
                    else -> Text(if(state.isPlaying)"IN YOUR MOMENT" else "READY WHEN YOU ARE",color=MousikeGreen.copy(alpha=.85f),fontSize=9.sp,fontWeight=FontWeight.Bold,letterSpacing=1.3.sp)
                }
            }
            PlayerTimeline(state,reduce,viewModel::seekTo)
            Row(Modifier.fillMaxWidth().height(80.dp),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically) {
                IconButton({viewModel.toggleShuffle()},Modifier.size(48.dp)){Icon(Icons.Default.Shuffle,if(state.isShuffle)"Turn shuffle off" else "Turn shuffle on",tint=if(state.isShuffle)MousikeGreen else TextMuted,modifier=Modifier.size(22.dp))}
                IconButton({viewModel.playPrevious()},Modifier.size(48.dp)){Icon(Icons.Default.SkipPrevious,"Previous",tint=TextPrimary,modifier=Modifier.size(34.dp))}
                FilledIconButton({viewModel.togglePlayPause()},modifier=Modifier.size(68.dp).testTag("full_player_play_pause"),
                    colors=IconButtonDefaults.filledIconButtonColors(containerColor=buttonColor,contentColor=ObsidianDark,disabledContainerColor=buttonColor.copy(alpha=.65f))) {
                    if(state.isBuffering) Box(contentAlignment=Alignment.Center) {
                        CircularProgressIndicator(Modifier.size(42.dp),color=ObsidianDark.copy(alpha=.3f),strokeWidth=2.dp)
                        Icon(Icons.Default.Pause,"Pause loading",modifier=Modifier.size(28.dp))
                    }
                    else AnimatedContent(state.isPlaying,transitionSpec={fadeIn(tween(if(reduce)0 else 130))+scaleIn(tween(if(reduce)0 else 150),initialScale=.8f) togetherWith fadeOut(tween(if(reduce)0 else 90))},label="play_button") {playing ->Icon(if(playing)Icons.Default.Pause else Icons.Default.PlayArrow,if(playing)"Pause" else if(state.error!=null)"Retry" else "Play",modifier=Modifier.size(36.dp))}
                }
                IconButton({viewModel.playNext()},Modifier.size(48.dp).testTag("full_player_next")){Icon(Icons.Default.SkipNext,"Next",tint=TextPrimary,modifier=Modifier.size(34.dp))}
                IconButton({viewModel.cycleRepeatMode()},Modifier.size(48.dp)){Icon(if(state.repeatMode==RepeatMode.ONE)Icons.Default.RepeatOne else Icons.Default.Repeat,"Change repeat mode",tint=if(state.repeatMode==RepeatMode.OFF)TextMuted else MousikeGreen,modifier=Modifier.size(22.dp))}
            }
            Spacer(Modifier.height(8.dp))
            if(preferences.showPlayerShortcuts)Row(Modifier.fillMaxWidth().background(TextPrimary.copy(alpha=.035f),ComponentShape).padding(horizontal=4.dp),horizontalArrangement=Arrangement.SpaceEvenly) {
                PlayerShortcut(Icons.Default.Lyrics,"Lyrics",viewModel::openLyrics,Modifier.weight(1f))
                PlayerShortcut(Icons.AutoMirrored.Filled.QueueMusic,"Queue",viewModel::openQueue,Modifier.weight(1f))
                PlayerShortcut(Icons.Default.Download,"Download",{viewModel.download(track)},Modifier.weight(1f))
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable private fun PlayerTimeline(state: PlaybackState,reduce: Boolean,onSeek: (Long)->Unit) {
    var dragging by remember(state.currentTrack?.id){mutableStateOf(false)}
    var drag by remember(state.currentTrack?.id){mutableFloatStateOf(0f)}
    val duration=state.durationMs.coerceAtLeast(1)
    val rawProgress=(state.currentPositionMs.toFloat()/duration).coerceIn(0f,1f)
    val progress=if(reduce)rememberUpdatedState(rawProgress) else animateFloatAsState(rawProgress,tween(230,easing=LinearEasing),label="playback_progress")
    Slider(value=if(dragging)drag else progress.value,onValueChange={dragging=true;drag=it},onValueChangeFinished={onSeek((drag*duration).toLong());dragging=false},enabled=state.durationMs>0 && !state.isBuffering,
        colors=SliderDefaults.colors(thumbColor=TextPrimary,activeTrackColor=TextPrimary,inactiveTrackColor=TextPrimary.copy(alpha=.12f)),modifier=Modifier.fillMaxWidth().height(42.dp).testTag("player_seek_slider"))
    fun time(ms: Long)="${ms/60000}:${((ms/1000)%60).toString().padStart(2,'0')}"
    Row(Modifier.fillMaxWidth().padding(horizontal=3.dp),horizontalArrangement=Arrangement.SpaceBetween) {
        Text(time(if(dragging)(drag*duration).toLong() else state.currentPositionMs),fontSize=10.sp,color=TextMuted)
        Text(if(state.durationMs>0)time(state.durationMs) else "—:—",fontSize=10.sp,color=TextMuted)
    }
}
@Composable private fun PlayerShortcut(icon: ImageVector,label: String,onClick: ()->Unit,modifier: Modifier) {
    TextButton(onClick,modifier.heightIn(min=52.dp),contentPadding=PaddingValues(horizontal=4.dp)) {
        Icon(icon,null,Modifier.size(18.dp),tint=TextSecondary);Spacer(Modifier.width(6.dp));Text(label,color=TextSecondary,fontSize=11.sp)
    }
}
