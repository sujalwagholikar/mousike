package com.mousike.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mousike.app.ui.components.MusicArtwork
import com.mousike.app.data.catalog.MusicCatalog
import com.mousike.app.data.model.Track
import com.mousike.app.ui.components.ArtworkCard
import com.mousike.app.ui.components.TrackItem
import com.mousike.app.ui.theme.GenreAmber
import com.mousike.app.ui.theme.GenreCyan
import com.mousike.app.ui.theme.GenreIndigo
import com.mousike.app.ui.theme.GenreOrange
import com.mousike.app.ui.theme.GenrePink
import com.mousike.app.ui.theme.GenrePurple
import com.mousike.app.ui.theme.GenreRose
import com.mousike.app.ui.theme.MousikeGreen
import com.mousike.app.ui.theme.ObsidianDark
import com.mousike.app.ui.theme.SurfaceCard
import com.mousike.app.ui.theme.TextMuted
import com.mousike.app.ui.theme.TextPrimary
import com.mousike.app.ui.theme.TextSecondary
import com.mousike.app.ui.viewmodel.MousikeViewModel

@Composable
fun ExploreScreen(
    viewModel: MousikeViewModel,
    modifier: Modifier = Modifier
) {
    val catalogVersion by viewModel.catalogVersion.collectAsState()
    LaunchedEffect(Unit) { viewModel.refreshExploreMusic() }
    val playbackState by viewModel.browsingPlaybackState.collectAsState()
    val likedTrackIds by viewModel.likedTrackIds.collectAsState()

    val topCharts = MusicCatalog.TRACKS.take(8)

    val decades = listOf(
        "80s music" to Brush.linearGradient(listOf(GenrePink, GenrePurple)),
        "90s hits" to Brush.linearGradient(listOf(GenreOrange, GenreAmber)),
        "2000s pop" to Brush.linearGradient(listOf(GenreCyan, GenreIndigo)),
        "2010s EDM" to Brush.linearGradient(listOf(GenrePurple, GenreCyan)),
        "2020s hits" to Brush.linearGradient(listOf(MousikeGreen, GenreCyan))
    )

    val moods = listOf(
        "Deep Focus" to "Ambient & Lo-Fi for coding and reading",
        "Night Relax" to "Gentle acoustics & calm piano",
        "Workout Hype" to "High tempo electronic & heavy rock",
        "Creative Flow" to "Ethereal synthwave & chillhop"
    )

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(ObsidianDark),
        contentPadding = PaddingValues(bottom = 24.dp)
    ) {
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Text(
                    text = "Explore",
                    color = TextPrimary,
                    fontSize = 24.sp,
                    fontFamily = com.mousike.app.ui.theme.Manrope, fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Discover global charts, time capsules & curated vibes",
                    color = TextMuted,
                    fontSize = 13.sp
                )
            }
        }

        // Hero Spotlight Banner
        item {
            val featuredTrack = MusicCatalog.TRACKS.firstOrNull()
            if (featuredTrack == null) {
                Text("Browse real music from YouTube Music. Search for your favorite artist to begin.",
                    color = TextSecondary, modifier = Modifier.padding(horizontal = 18.dp, vertical = 22.dp))
            } else {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .height(180.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(
                        Brush.linearGradient(
                            listOf(GenrePurple.copy(alpha = 0.8f), GenreRose.copy(alpha = 0.8f))
                        )
                    )
                    .clickable { viewModel.playTrack(featuredTrack, MusicCatalog.TRACKS) }
                    .padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxSize(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "FEATURED SPOTLIGHT",
                            color = MousikeGreen,
                            fontSize = 11.sp,
                            fontFamily = com.mousike.app.ui.theme.Manrope, fontWeight = FontWeight.Black,
                            letterSpacing = 1.sp
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = featuredTrack.title,
                            color = Color.White,
                            fontSize = 22.sp,
                            fontFamily = com.mousike.app.ui.theme.Manrope, fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "${featuredTrack.artist} • ${featuredTrack.genre}",
                            color = Color.White.copy(alpha = 0.85f),
                            fontSize = 13.sp
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color.White)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Play Track", color = Color.White, fontFamily = com.mousike.app.ui.theme.Manrope, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                    }

                    MusicArtwork(
                        model = featuredTrack.coverUrl,
                        contentDescription = featuredTrack.title,
                        modifier = Modifier
                            .size(110.dp)
                            .clip(com.mousike.app.ui.theme.ArtworkShape),
                        contentScale = ContentScale.Fit
                    )
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
            }
        }

        // Global Charts
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.TrendingUp, contentDescription = null, tint = MousikeGreen)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Music discoveries",
                    color = TextPrimary,
                    fontSize = 18.sp,
                    fontFamily = com.mousike.app.ui.theme.Manrope, fontWeight = FontWeight.Bold
                )
            }
        }
        itemsIndexed(topCharts) { index, track ->
            TrackItem(
                track = track,
                isCurrentTrack = playbackState.currentTrack?.id == track.id,
                isPlaying = playbackState.isPlaying,
                isLiked = likedTrackIds.contains(track.id),
                onTrackClick = { viewModel.playTrack(track, topCharts, index) },
                onToggleLike = { viewModel.toggleLikeTrack(track.id) },
                onAddToQueue = { viewModel.addToQueue(track) },
                onAddToPlaylist = { viewModel.setTrackToAddToPlaylist(track) },
                onFeedback = { viewModel.setFeedbackDialogForTrack(track) },
                index = index,
                modifier = Modifier.padding(horizontal = 8.dp)
            )
        }

        // Decade Time Machine
        item {
            Spacer(modifier = Modifier.height(20.dp))
            Text(
                text = "Decade Time Machine",
                color = TextPrimary,
                fontSize = 18.sp,
                fontFamily = com.mousike.app.ui.theme.Manrope, fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(decades) { (title, brush) ->
                    Box(
                        modifier = Modifier
                            .width(150.dp)
                            .height(110.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(brush)
                            .clickable {
                                viewModel.openGenre(title)
                            }
                            .padding(12.dp)
                    ) {
                        Text(
                            text = title,
                            color = Color.White,
                            fontSize = 15.sp,
                            fontFamily = com.mousike.app.ui.theme.Manrope, fontWeight = FontWeight.Bold,
                            modifier = Modifier.align(Alignment.BottomStart)
                        )
                    }
                }
            }
        }

        // Mood & Atmosphere Stations
        item {
            Spacer(modifier = Modifier.height(20.dp))
            Text(
                text = "Mood & Activity Stations",
                color = TextPrimary,
                fontSize = 18.sp,
                fontFamily = com.mousike.app.ui.theme.Manrope, fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                moods.forEach { (title, desc) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(SurfaceCard)
                            .clickable {
                                viewModel.openGenre(title)
                            }
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(title, color = TextPrimary, fontSize = 15.sp, fontFamily = com.mousike.app.ui.theme.Manrope, fontWeight = FontWeight.Bold)
                            Text(desc, color = TextMuted, fontSize = 12.sp)
                        }
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = "Play station",
                            tint = MousikeGreen
                        )
                    }
                }
            }
        }
    }
}
