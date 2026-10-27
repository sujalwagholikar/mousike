package com.mousike.app.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mousike.app.data.model.LyricLine
import com.mousike.app.ui.theme.GenreIndigo
import com.mousike.app.ui.theme.GenrePurple
import com.mousike.app.ui.theme.MousikeGreen
import com.mousike.app.ui.theme.ObsidianDark
import com.mousike.app.ui.theme.TextMuted
import com.mousike.app.ui.theme.TextPrimary
import com.mousike.app.ui.theme.TextSecondary
import com.mousike.app.ui.viewmodel.MousikeViewModel

@Composable
fun LyricsScreen(
    viewModel: MousikeViewModel,
    modifier: Modifier = Modifier
) {
    BackHandler {
        viewModel.navigateBack()
    }

    val playbackState by viewModel.playbackState.collectAsState()
    val track = playbackState.currentTrack ?: return

    val lyricsList: List<LyricLine> = remember(track.id, track.lyrics) {
        track.parseLrcLyrics()
    }

    val listState = rememberLazyListState()
    val currentPositionMs = playbackState.currentPositionMs

    // Find current active lyric line
    val activeIndex = remember(currentPositionMs, lyricsList) {
        var idx = -1
        for (i in lyricsList.indices) {
            if (currentPositionMs >= lyricsList[i].timeMs) {
                idx = i
            } else {
                break
            }
        }
        idx
    }

    // Auto-scroll to active lyric
    LaunchedEffect(activeIndex) {
        if (activeIndex >= 0 && activeIndex < lyricsList.size) {
            listState.animateScrollToItem(
                index = (activeIndex - 2).coerceAtLeast(0)
            )
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(GenrePurple.copy(alpha = 0.6f), GenreIndigo.copy(alpha = 0.4f), ObsidianDark)
                )
            )
            .testTag("lyrics_screen")
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Top Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { viewModel.navigateBack() }) {
                    Icon(
                        imageVector = Icons.Default.ArrowBack,
                        contentDescription = "Back",
                        tint = TextPrimary
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = track.title,
                        color = TextPrimary,
                        fontSize = 16.sp,
                        fontFamily = com.mousike.app.ui.theme.Manrope, fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Synchronized Lyrics • Tap line to seek",
                        color = MousikeGreen,
                        fontSize = 12.sp
                    )
                }

                IconButton(onClick = { viewModel.togglePlayPause() }) {
                    Icon(
                        imageVector = if (playbackState.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = "Play/Pause",
                        tint = TextPrimary
                    )
                }
            }

            if (lyricsList.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No synchronized lyrics available for this track.",
                        color = TextMuted,
                        fontSize = 15.sp
                    )
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 24.dp, end = 24.dp, top = 40.dp, bottom = if (track.streamUrl.isBlank()) 270.dp else 40.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    itemsIndexed(lyricsList) { index, line ->
                        val isActive = index == activeIndex
                        val isPast = index < activeIndex

                        val textColor by animateColorAsState(
                            targetValue = when {
                                isActive -> Color.White
                                isPast -> TextMuted
                                else -> TextSecondary
                            },
                            animationSpec = tween(300),
                            label = "lyric_color"
                        )

                        Text(
                            text = line.text,
                            color = textColor,
                            fontSize = if (isActive) 24.sp else 19.sp,
                            fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium,
                            lineHeight = if (isActive) 32.sp else 26.sp,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable {
                                    viewModel.seekTo(line.timeMs)
                                }
                                .padding(vertical = 4.dp)
                                .testTag("lyric_line_$index")
                        )
                    }
                }
            }
        }
    }
}
