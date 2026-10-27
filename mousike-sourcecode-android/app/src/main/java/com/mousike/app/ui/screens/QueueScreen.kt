package com.mousike.app.ui.screens

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mousike.app.ui.components.MusicArtwork
import com.mousike.app.ui.theme.MousikeGreen
import com.mousike.app.ui.theme.ObsidianDark
import com.mousike.app.ui.theme.SurfaceCard
import com.mousike.app.ui.theme.TextMuted
import com.mousike.app.ui.theme.TextPrimary
import com.mousike.app.ui.theme.TextSecondary
import com.mousike.app.ui.viewmodel.MousikeViewModel

@Composable
fun QueueScreen(
    viewModel: MousikeViewModel,
    modifier: Modifier = Modifier
) {
    BackHandler {
        viewModel.navigateBack()
    }

    val playbackState by viewModel.browsingPlaybackState.collectAsState()
    val currentTrack = playbackState.currentTrack
    val queue = playbackState.queue
    val currentIndex = playbackState.queueIndex

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(ObsidianDark),
        contentPadding = PaddingValues(bottom = 24.dp)
    ) {
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { viewModel.navigateBack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = TextPrimary)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Play Queue", color = TextPrimary, fontSize = 20.sp, fontFamily = com.mousike.app.ui.theme.Manrope, fontWeight = FontWeight.Bold)
                }

                if (queue.size > 1) {
                    TextButton(onClick = { viewModel.clearQueue() }) {
                        Text("Clear Queue", color = TextMuted, fontSize = 13.sp)
                    }
                }
            }
        }

        // Now Playing Section
        if (currentTrack != null) {
            item {
                Text(
                    text = "Now Playing",
                    color = MousikeGreen,
                    fontSize = 13.sp,
                    fontFamily = com.mousike.app.ui.theme.Manrope, fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(SurfaceCard)
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    MusicArtwork(
                        model = currentTrack.coverUrl,
                        contentDescription = currentTrack.title,
                        modifier = Modifier
                            .size(50.dp)
                            .clip(com.mousike.app.ui.theme.ArtworkShape),
                        contentScale = ContentScale.Fit
                    )

                    Spacer(modifier = Modifier.width(12.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(currentTrack.title, color = MousikeGreen, fontSize = 15.sp, fontFamily = com.mousike.app.ui.theme.Manrope, fontWeight = FontWeight.Bold)
                        Text(currentTrack.artist, color = TextSecondary, fontSize = 12.sp)
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
            }
        }

        // Up Next Section
        item {
            Text(
                text = "Next In Queue (${queue.size} total)",
                color = TextPrimary,
                fontSize = 15.sp,
                fontFamily = com.mousike.app.ui.theme.Manrope, fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
        }

        itemsIndexed(queue) { index, track ->
            val isCurrent = index == currentIndex
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 3.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (isCurrent) MousikeGreen.copy(alpha = 0.15f) else SurfaceCard.copy(alpha = 0.6f))
                    .clickable { viewModel.playTrack(track, queue, index) }
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${index + 1}",
                    color = if (isCurrent) MousikeGreen else TextMuted,
                    fontSize = 12.sp,
                    modifier = Modifier.width(24.dp)
                )

                MusicArtwork(
                    model = track.coverUrl,
                    contentDescription = track.title,
                    modifier = Modifier
                        .size(40.dp)
                        .clip(com.mousike.app.ui.theme.ArtworkShape),
                    contentScale = ContentScale.Fit
                )

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = track.title,
                        color = if (isCurrent) MousikeGreen else TextPrimary,
                        fontSize = 14.sp,
                        fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = track.artist,
                        color = TextSecondary,
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                IconButton(onClick={viewModel.moveQueue(index,index-1)},enabled=index>0,modifier=Modifier.size(28.dp)) { Icon(Icons.Default.KeyboardArrowUp,"Move up",tint=TextMuted) }
                IconButton(onClick={viewModel.moveQueue(index,index+1)},enabled=index<queue.lastIndex,modifier=Modifier.size(28.dp)) { Icon(Icons.Default.KeyboardArrowDown,"Move down",tint=TextMuted) }
                IconButton(
                    onClick = { viewModel.removeFromQueue(index) },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Remove from Queue",
                        tint = TextMuted,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}
