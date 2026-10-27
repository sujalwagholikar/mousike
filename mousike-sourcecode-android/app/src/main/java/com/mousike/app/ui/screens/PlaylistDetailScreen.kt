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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mousike.app.ui.components.MusicArtwork
import com.mousike.app.data.catalog.MusicCatalog
import com.mousike.app.data.model.Track
import com.mousike.app.ui.components.TrackItem
import com.mousike.app.ui.theme.GenreIndigo
import com.mousike.app.ui.theme.GenrePurple
import com.mousike.app.ui.theme.MousikeGreen
import com.mousike.app.ui.theme.ObsidianDark
import com.mousike.app.ui.theme.SurfaceCard
import com.mousike.app.ui.theme.TextMuted
import com.mousike.app.ui.theme.TextPrimary
import com.mousike.app.ui.theme.TextSecondary
import com.mousike.app.ui.viewmodel.MousikeViewModel

@Composable
fun PlaylistDetailScreen(
    viewModel: MousikeViewModel,
    modifier: Modifier = Modifier
) {
    BackHandler {
        viewModel.navigateBack()
    }

    val playlist = viewModel.selectedPlaylist.collectAsState().value ?: return
    val catalogVersion by viewModel.catalogVersion.collectAsState()
    val playbackState by viewModel.browsingPlaybackState.collectAsState()
    val likedTrackIds by viewModel.likedTrackIds.collectAsState()

    val tracks: List<Track> = playlist.trackIds.mapNotNull { MusicCatalog.getTrackById(it) }

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
                            listOf(GenrePurple.copy(alpha = 0.7f), GenreIndigo.copy(alpha = 0.4f), ObsidianDark)
                        )
                    )
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Column {
                    IconButton(onClick = { viewModel.navigateBack() }) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Back",
                            tint = TextPrimary
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        MusicArtwork(
                            model = playlist.coverUrl.ifBlank { "https://images.unsplash.com/photo-1514525253161-7a46d19cd819?auto=format&fit=crop&w=400&q=80" },
                            contentDescription = playlist.name,
                            modifier = Modifier
                                .size(130.dp)
                                .shadow(12.dp, com.mousike.app.ui.theme.ArtworkShape)
                                .clip(com.mousike.app.ui.theme.ArtworkShape),
                            contentScale = ContentScale.Fit
                        )

                        Spacer(modifier = Modifier.width(16.dp))

                        Column {
                            Text(
                                text = "PLAYLIST",
                                color = TextMuted,
                                fontSize = 11.sp,
                                fontFamily = com.mousike.app.ui.theme.Manrope, fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp
                            )
                            Text(
                                text = playlist.name,
                                color = TextPrimary,
                                fontSize = 22.sp,
                                fontFamily = com.mousike.app.ui.theme.Manrope, fontWeight = FontWeight.Bold
                            )
                            if (playlist.description.isNotBlank()) {
                                Text(
                                    text = playlist.description,
                                    color = TextSecondary,
                                    fontSize = 12.sp,
                                    modifier = Modifier.padding(vertical = 2.dp)
                                )
                            }
                            Text(
                                text = "${tracks.size} songs • Mousiké Library",
                                color = TextMuted,
                                fontSize = 12.sp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    // Play & Shuffle Buttons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Button(
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
                            modifier = Modifier.testTag("playlist_play_all")
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Play All", fontFamily = com.mousike.app.ui.theme.Manrope, fontWeight = FontWeight.Bold)
                        }

                        Button(
                            onClick = {
                                if (tracks.isNotEmpty()) {
                                    viewModel.toggleShuffle()
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

        if (tracks.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(40.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("This playlist has no tracks yet.", color = TextMuted)
                }
            }
        } else {
            itemsIndexed(tracks) { index, track ->
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
            }
        }
    }
}
