package com.mousike.app.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.material3.*
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.ListAlt
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mousike.app.ui.components.MusicArtwork
import com.mousike.app.data.catalog.MusicCatalog
import com.mousike.app.data.model.Track
import com.mousike.app.ui.components.TrackItem
import com.mousike.app.ui.theme.GenreCyan
import com.mousike.app.ui.theme.GenreIndigo
import com.mousike.app.ui.theme.MousikeGreen
import com.mousike.app.ui.theme.ObsidianDark
import com.mousike.app.ui.theme.SurfaceCard
import com.mousike.app.ui.theme.TextMuted
import com.mousike.app.ui.theme.TextPrimary
import com.mousike.app.ui.theme.TextSecondary
import com.mousike.app.ui.viewmodel.MousikeViewModel

@Composable
fun AlbumDetailScreen(
    viewModel: MousikeViewModel,
    modifier: Modifier = Modifier
) {
    BackHandler {
        viewModel.navigateBack()
    }

    val album = viewModel.selectedAlbum.collectAsState().value ?: return
    val loading by viewModel.albumLoading.collectAsState()
    var editTracklist by remember { mutableStateOf(false) }
    var tracklist by remember { mutableStateOf("") }
    val catalogVersion by viewModel.catalogVersion.collectAsState()
    val playbackState by viewModel.browsingPlaybackState.collectAsState()
    val likedTrackIds by viewModel.likedTrackIds.collectAsState()
    val savedAlbumIds by viewModel.savedAlbumIds.collectAsState()

    val appearance=com.mousike.app.ui.theme.LocalAppearance.current
    val ambient=com.mousike.app.ui.components.coverAmbient(album.coverUrl)
    val isSaved = savedAlbumIds.contains(album.id)
    val tracks: List<Track> = album.trackIds.mapNotNull { MusicCatalog.getTrackById(it) }
    val personalMix=com.mousike.app.ui.components.personalMixForAlbum(album.id)

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(ObsidianDark),
        contentPadding = PaddingValues(bottom = 24.dp)
    ) {
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            listOf(ambient.copy(alpha = 0.6f), GenreIndigo.copy(alpha = 0.35f), ObsidianDark)
                        )
                    )
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = { viewModel.navigateBack() }) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = TextPrimary)
                        }

                        IconButton(onClick = { viewModel.toggleSaveAlbum(album.id) }) {
                            Icon(
                                imageVector = if (isSaved) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                                contentDescription = if (isSaved) "Unsave Album" else "Save Album",
                                tint = if (isSaved) MousikeGreen else TextMuted
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    AlbumHero(album,tracks,personalMix,appearance.albumLayout)

                    Spacer(modifier = Modifier.height(20.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Button(
                            enabled=tracks.isNotEmpty() && !loading,
                            onClick = {
                                if (tracks.isNotEmpty()) {
                                    viewModel.playTrack(tracks.first(), tracks, 0)
                                }
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MousikeGreen,
                                contentColor = ObsidianDark
                            ),
                            shape = RoundedCornerShape(24.dp),
                            modifier = Modifier.testTag("album_play_all")
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(if(personalMix!=null)"Play mix" else "Play Album", fontFamily = com.mousike.app.ui.theme.Manrope, fontWeight = FontWeight.Bold)
                        }

                        Button(
                            enabled=tracks.isNotEmpty() && !loading,
                            onClick = {
                                if (tracks.isNotEmpty()) {
                                    val shuffled = tracks.shuffled()
                                    viewModel.playTrack(shuffled.first(), shuffled, 0)
                                }
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = SurfaceCard,
                                contentColor = TextPrimary
                            ),
                            shape = RoundedCornerShape(24.dp)
                        ) {
                            Icon(Icons.Default.Shuffle, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Shuffle", fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }

        item {
            Column(Modifier.fillMaxWidth().padding(horizontal=20.dp,vertical=12.dp)) {
                if(loading) { LinearProgressIndicator(Modifier.fillMaxWidth(),color=MousikeGreen); Spacer(Modifier.height(12.dp));Text("Reading track metadata…",color=TextSecondary) }
                if(album.resolutionNote.isNotBlank()) Text(album.resolutionNote,color=TextSecondary,fontSize=12.sp,lineHeight=18.sp)
                if(album.sourceVideoId.isNotBlank()) {
                    Text("Detection: ${album.confidence}",color=MousikeGreen,fontSize=11.sp,modifier=Modifier.padding(top=8.dp))
                    Row {
                        TextButton(onClick={viewModel.playFullRecording()}){Text("Play full recording")}
                        TextButton(onClick={editTracklist=true}){Text("Edit track list")}
                    }
                }
                if(tracks.isNotEmpty()) TextButton(onClick={tracks.forEach {viewModel.download(it)}}){Icon(Icons.Default.Download,null);Spacer(Modifier.width(8.dp));Text("Download album")}
            }
        }

        itemsIndexed(tracks,key={_,t->t.id}) { index, track ->
            TrackItem(
                track = track,
                isCurrentTrack = playbackState.currentTrack?.id == track.id,
                isPlaying = playbackState.isPlaying,
                isLiked = likedTrackIds.contains(track.id),
                onTrackClick = { viewModel.playTrack(track, tracks, index) },
                onToggleLike = { viewModel.toggleLikeTrack(track.id) },
                onAddToQueue = { viewModel.addToQueue(track) },
                onAddToPlaylist = { viewModel.setTrackToAddToPlaylist(track) },
                onFeedback = { viewModel.setFeedbackDialogForTrack(track) },
                index = index,
                modifier = Modifier.padding(horizontal = 8.dp)
            )
            if(track.endMs!=null) Text("${track.evidence} • ${formatBoundary(track.startMs)} — ${formatBoundary(track.endMs)}",color=TextMuted,fontSize=10.sp,modifier=Modifier.padding(start=48.dp,bottom=8.dp))
        }
    }
    if(editTracklist) AlertDialog(onDismissRequest={editTracklist=false},title={Text("Timestamped track list")},text={Column {Text("Use one song per line. Example: 00:00 Song One\n03:12 Song Two",fontSize=12.sp);OutlinedTextField(tracklist,{tracklist=it},modifier=Modifier.fillMaxWidth().height(200.dp),placeholder={Text("00:00 Song One\n03:12 Song Two")}) }},confirmButton={TextButton(onClick={viewModel.applyAlbumTracklist(tracklist);editTracklist=false}){Text("Apply")}},dismissButton={TextButton(onClick={editTracklist=false}){Text("Cancel")}})

}

private fun formatBoundary(ms: Long): String = "%d:%02d".format(ms/60000,ms/1000%60)

@Composable private fun AlbumHero(album: com.mousike.app.data.model.Album,tracks: List<Track>,mix: com.mousike.app.ui.components.PersonalMix?,layout: String) {
    val shape=com.mousike.app.ui.theme.ArtworkShape
    val size=if(layout=="Gallery")248.dp else if(layout=="Compact")104.dp else 200.dp
    val cover: @Composable ()->Unit = {
        Box(Modifier.size(size).shadow(12.dp,shape).clip(shape)) {
            if(mix!=null)com.mousike.app.ui.components.PersonalMixCover(mix,tracks,Modifier.fillMaxSize())
            else MusicArtwork(album.coverUrl,album.title,Modifier.fillMaxSize(),ContentScale.Fit)
        }
    }
    val info: @Composable ()->Unit = {
        Text(if(mix!=null)"MADE AROUND YOU" else "ALBUM",color=MousikeGreen,fontSize=10.sp,letterSpacing=1.8.sp,fontWeight=FontWeight.Bold)
        Text(album.title,color=TextPrimary,fontFamily=com.mousike.app.ui.theme.Manrope,fontSize=if(layout=="Compact")21.sp else 29.sp,lineHeight=34.sp,fontWeight=FontWeight.ExtraBold,modifier=Modifier.padding(top=7.dp))
        Text(album.artist,color=TextSecondary,fontSize=14.sp,modifier=Modifier.padding(top=6.dp))
        Text("${tracks.size} songs${if(album.year>0) " • ${album.year}" else ""}",color=TextMuted,fontSize=12.sp,modifier=Modifier.padding(top=8.dp))
    }
    if(layout=="Compact")Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {cover();Spacer(Modifier.width(16.dp));Column(Modifier.weight(1f)){info()}}
    else Column(Modifier.fillMaxWidth(),horizontalAlignment=if(layout=="Gallery")Alignment.CenterHorizontally else Alignment.Start) {
        Box(Modifier.fillMaxWidth(),contentAlignment=Alignment.Center){cover()}
        Spacer(Modifier.height(24.dp));info()
    }
}
