package com.mousike.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mousike.app.ui.components.MusicArtwork
import com.mousike.app.data.model.Track
import com.mousike.app.ui.theme.MousikeGreen
import com.mousike.app.ui.theme.SurfaceCard
import com.mousike.app.ui.theme.TextMuted
import com.mousike.app.ui.theme.TextPrimary
import com.mousike.app.ui.theme.TextSecondary

@Composable
fun TrackItem(
    track: Track,
    isCurrentTrack: Boolean,
    isPlaying: Boolean,
    isLiked: Boolean,
    onTrackClick: () -> Unit,
    onToggleLike: () -> Unit,
    onAddToQueue: () -> Unit,
    onAddToPlaylist: () -> Unit,
    onFeedback: () -> Unit,
    modifier: Modifier = Modifier,
    index: Int? = null
) {
    var showMenu by remember { mutableStateOf(false) }
    val context=androidx.compose.ui.platform.LocalContext.current
    val green=MousikeGreen
    val appearance=com.mousike.app.ui.theme.LocalAppearance.current

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(if (isCurrentTrack) SurfaceCard.copy(alpha = 0.7f) else Color.Transparent)
            .clickable(onClick = onTrackClick)
            .padding(horizontal = 12.dp, vertical = when(appearance.trackSpacing){"Compact"->3.dp;"Spacious"->14.dp;else->8.dp})
            .testTag("track_item_${track.id}"),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (index != null) {
            Box(
                modifier = Modifier.width(28.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                if (isCurrentTrack && isPlaying) {
                    VisualizerBars(
                        amplitudes = listOf(0.4f, 0.9f, 0.6f, 0.8f),
                        barWidth = 2.dp,
                        barSpacing = 1.5.dp,
                        maxHeight = 14.dp,
                        activeColor = MousikeGreen
                    )
                } else {
                    Text(
                        text = "${index + 1}",
                        color = if (isCurrentTrack) MousikeGreen else TextMuted,
                        fontSize = 13.sp,
                        fontWeight = if (isCurrentTrack) FontWeight.Bold else FontWeight.Normal
                    )
                }
            }
        }

        if(appearance.showTrackArtwork)MusicArtwork(
            model = track.coverUrl,
            contentDescription = track.title,
            modifier = Modifier
                .size(48.dp)
                .clip(com.mousike.app.ui.theme.ArtworkShape),
            contentScale = ContentScale.Fit
        )

        if(appearance.showTrackArtwork)Spacer(modifier = Modifier.width(12.dp))

        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = track.title,
                color = if (isCurrentTrack) MousikeGreen else TextPrimary,
                fontSize = 15.sp,
                fontWeight = if (isCurrentTrack) FontWeight.Bold else FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "${track.artist} • ${track.album}",
                color = TextSecondary,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        IconButton(
            onClick = onToggleLike,
            modifier = Modifier
                .size(36.dp)
                .testTag("like_btn_${track.id}")
        ) {
            Icon(
                imageVector = if (isLiked) Icons.Default.Favorite else Icons.Outlined.FavoriteBorder,
                contentDescription = if (isLiked) "Unlike" else "Like",
                tint = if (isLiked) MousikeGreen else TextMuted,
                modifier = Modifier.size(20.dp)
            )
        }

        if(appearance.showTrackDuration)Text("%d:%02d".format(track.durationSec/60,track.durationSec%60),color=TextMuted,fontSize=10.sp,modifier=Modifier.padding(horizontal=3.dp))
        Box {
            IconButton(
                onClick = { showMenu = true },
                modifier = Modifier
                    .size(36.dp)
                    .testTag("more_btn_${track.id}")
            ) {
                Icon(
                    imageVector = Icons.Default.MoreVert,
                    contentDescription = "Track options",
                    tint = TextMuted,
                    modifier = Modifier.size(20.dp)
                )
            }

            DropdownMenu(
                expanded = showMenu,
                onDismissRequest = { showMenu = false },
                modifier = Modifier.background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                DropdownMenuItem(text={Text("Play next",color=TextPrimary)},onClick={showMenu=false;com.mousike.app.playback.AudioPlayerEngine.getInstance(context).playNextInQueue(track)})
                DropdownMenuItem(text={Text("Download",color=TextPrimary)},onClick={showMenu=false;com.mousike.app.download.DownloadStore.get(context).enqueue(track)})
                DropdownMenuItem(text={Text("Share song",color=TextPrimary)},onClick={showMenu=false;val url=if(track.videoId.matches(Regex("[A-Za-z0-9_-]{11}"))) "https://www.youtube.com/watch?v=${track.videoId}&t=${track.startMs/1000}" else "${track.title} • ${track.artist}";context.startActivity(android.content.Intent.createChooser(android.content.Intent(android.content.Intent.ACTION_SEND).apply {type="text/plain";putExtra(android.content.Intent.EXTRA_TEXT,url)},"Share song"))})
                DropdownMenuItem(
                    text = { Text("Add to Queue", color = TextPrimary) },
                    onClick = {
                        showMenu = false
                        onAddToQueue()
                    }
                )
                DropdownMenuItem(
                    text = { Text("Add to Playlist", color = TextPrimary) },
                    onClick = {
                        showMenu = false
                        onAddToPlaylist()
                    }
                )
                DropdownMenuItem(
                    text = { Text("Tune Recommendations", color = TextPrimary) },
                    onClick = {
                        showMenu = false
                        onFeedback()
                    }
                )
            }
        }
    }
}
