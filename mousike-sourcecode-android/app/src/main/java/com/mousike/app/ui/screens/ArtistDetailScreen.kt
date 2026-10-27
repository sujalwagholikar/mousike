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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import com.mousike.app.ui.components.ArtworkCard
import com.mousike.app.ui.components.TrackItem
import com.mousike.app.ui.theme.GenreIndigo
import com.mousike.app.ui.theme.GenrePurple
import com.mousike.app.ui.theme.MousikeGreen
import com.mousike.app.ui.theme.ObsidianDark
import com.mousike.app.ui.theme.SurfaceCard
import com.mousike.app.ui.theme.SurfaceElevated
import com.mousike.app.ui.theme.TextMuted
import com.mousike.app.ui.theme.TextPrimary
import com.mousike.app.ui.theme.TextSecondary
import com.mousike.app.ui.viewmodel.MousikeViewModel

@Composable
fun ArtistDetailScreen(
    viewModel: MousikeViewModel,
    modifier: Modifier = Modifier
) {
    BackHandler {
        viewModel.navigateBack()
    }

    val artist = viewModel.selectedArtist.collectAsState().value ?: return
    val catalogVersion by viewModel.catalogVersion.collectAsState()
    val playbackState by viewModel.browsingPlaybackState.collectAsState()
    val likedTrackIds by viewModel.likedTrackIds.collectAsState()
    val savedArtistIds by viewModel.savedArtistIds.collectAsState()

    val isFollowing = savedArtistIds.contains(artist.id)
    val artistTracks = artist.topTrackIds.mapNotNull { MusicCatalog.getTrackById(it) }
        .ifEmpty { MusicCatalog.getTracksForArtist(artist.name) }
    val artistAlbums = MusicCatalog.ALBUMS.filter { it.artist.equals(artist.name, ignoreCase = true) }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(ObsidianDark),
        contentPadding = PaddingValues(bottom = 24.dp)
    ) {
        // Hero Artist Header
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(260.dp)
            ) {
                MusicArtwork(
                    model = artist.avatarUrl,
                    contentDescription = artist.name,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit
                )

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                listOf(Color.Transparent, ObsidianDark.copy(alpha = 0.7f), ObsidianDark)
                            )
                        )
                )

                IconButton(
                    onClick = { viewModel.navigateBack() },
                    modifier = Modifier
                        .padding(16.dp)
                        .align(Alignment.TopStart)
                ) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
                }

                Column(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(16.dp)
                ) {
                    Text(
                        text = "VERIFIED ARTIST",
                        color = MousikeGreen,
                        fontSize = 11.sp,
                        fontFamily = com.mousike.app.ui.theme.Manrope, fontWeight = FontWeight.Black,
                        letterSpacing = 1.sp
                    )
                    Text(
                        text = artist.name,
                        color = Color.White,
                        fontSize = 28.sp,
                        fontFamily = com.mousike.app.ui.theme.Manrope, fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "${artist.genres.joinToString(" • ")} • 1.4M monthly listeners",
                        color = Color.White.copy(alpha = 0.85f),
                        fontSize = 13.sp
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Button(
                            onClick = {
                                if (artistTracks.isNotEmpty()) {
                                    viewModel.playTrack(artistTracks.first(), artistTracks, 0)
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = MousikeGreen, contentColor = ObsidianDark),
                            shape = RoundedCornerShape(24.dp),
                            modifier = Modifier.testTag("artist_play_btn")
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Play", fontFamily = com.mousike.app.ui.theme.Manrope, fontWeight = FontWeight.Bold)
                        }

                        OutlinedButton(
                            onClick = { viewModel.toggleSaveArtist(artist.id) },
                            shape = RoundedCornerShape(24.dp)
                        ) {
                            Text(
                                text = if (isFollowing) "Following" else "Follow",
                                color = if (isFollowing) MousikeGreen else TextPrimary,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }
        }

        // About / Bio
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(SurfaceCard)
                    .padding(14.dp)
            ) {
                Text("About the Artist", color = TextPrimary, fontFamily = com.mousike.app.ui.theme.Manrope, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                Spacer(modifier = Modifier.height(4.dp))
                Text(artist.bio, color = TextSecondary, fontSize = 13.sp, lineHeight = 18.sp)
            }
            Spacer(modifier = Modifier.height(12.dp))
        }

        // Popular Tracks
        item {
            Text(
                text = "Popular Tracks",
                color = TextPrimary,
                fontSize = 18.sp,
                fontFamily = com.mousike.app.ui.theme.Manrope, fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
        }
        itemsIndexed(artistTracks) { index, track ->
            TrackItem(
                track = track,
                isCurrentTrack = playbackState.currentTrack?.id == track.id,
                isPlaying = playbackState.isPlaying,
                isLiked = likedTrackIds.contains(track.id),
                onTrackClick = { viewModel.playTrack(track, artistTracks, index) },
                onToggleLike = { viewModel.toggleLikeTrack(track.id) },
                onAddToQueue = { viewModel.addToQueue(track) },
                onAddToPlaylist = { viewModel.setTrackToAddToPlaylist(track) },
                onFeedback = { viewModel.setFeedbackDialogForTrack(track) },
                index = index,
                modifier = Modifier.padding(horizontal = 8.dp)
            )
        }

        // Discography Albums
        if (artistAlbums.isNotEmpty()) {
            item {
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Discography",
                    color = TextPrimary,
                    fontSize = 18.sp,
                    fontFamily = com.mousike.app.ui.theme.Manrope, fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(artistAlbums) { album ->
                        ArtworkCard(
                            title = album.title,
                            subtitle = "${album.year} • Album",
                            imageUrl = album.coverUrl,
                            onClick = { viewModel.openAlbum(album) }
                        )
                    }
                }
            }
        }
    }
}
