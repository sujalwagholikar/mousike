package com.mousike.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlaylistAdd
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mousike.app.data.model.PlaybackState
import com.mousike.app.data.model.Track
import com.mousike.app.data.model.UserPlaylist
import com.mousike.app.ui.theme.MousikeGreen
import com.mousike.app.ui.theme.ObsidianDark
import com.mousike.app.ui.theme.SurfaceCard
import com.mousike.app.ui.theme.SurfaceElevated
import com.mousike.app.ui.theme.TextMuted
import com.mousike.app.ui.theme.TextPrimary
import com.mousike.app.ui.theme.TextSecondary

@Composable
fun CreatePlaylistDialog(
    onDismiss: () -> Unit,
    onConfirm: (name: String, desc: String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var desc by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Create Playlist", color = TextPrimary, fontFamily = com.mousike.app.ui.theme.Manrope, fontWeight = FontWeight.Bold) },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Playlist Name") },
                    placeholder = { Text("My Summer Mix") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        focusedBorderColor = MousikeGreen,
                        unfocusedBorderColor = SurfaceElevated
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("playlist_name_input")
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = desc,
                    onValueChange = { desc = it },
                    label = { Text("Description (Optional)") },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        focusedBorderColor = MousikeGreen,
                        unfocusedBorderColor = SurfaceElevated
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { if (name.isNotBlank()) onConfirm(name.trim(), desc.trim()) },
                colors = ButtonDefaults.buttonColors(containerColor = MousikeGreen, contentColor = ObsidianDark),
                modifier = Modifier.testTag("create_playlist_confirm")
            ) {
                Text("Create")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = TextMuted)
            }
        },
        containerColor = SurfaceCard
    )
}

@Composable
fun AddToPlaylistDialog(
    track: Track,
    playlists: List<UserPlaylist>,
    onDismiss: () -> Unit,
    onPlaylistSelected: (playlistId: String) -> Unit,
    onCreateNewPlaylistClick: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.PlaylistAdd, contentDescription = null, tint = MousikeGreen)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Add to Playlist", color = TextPrimary, fontFamily = com.mousike.app.ui.theme.Manrope, fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "${track.title} • ${track.artist}",
                    color = TextSecondary,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                Button(
                    onClick = {
                        onDismiss()
                        onCreateNewPlaylistClick()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = SurfaceElevated, contentColor = TextPrimary),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("+ New Playlist")
                }

                Spacer(modifier = Modifier.height(10.dp))

                if (playlists.isEmpty()) {
                    Text(
                        "No custom playlists yet. Create one above!",
                        color = TextMuted,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(vertical = 16.dp)
                    )
                } else {
                    LazyColumn(modifier = Modifier.height(200.dp)) {
                        items(playlists) { pl ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable { onPlaylistSelected(pl.id) }
                                    .padding(vertical = 10.dp, horizontal = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(pl.name, color = TextPrimary, fontWeight = FontWeight.Medium)
                                    Text("${pl.trackIds.size} songs", color = TextMuted, fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close", color = TextMuted)
            }
        },
        containerColor = SurfaceCard
    )
}

@Composable
fun SleepTimerDialog(
    currentMinutes: Int?,
    remainingSec: Int,
    onDismiss: () -> Unit,
    onSelectMinutes: (Int?) -> Unit
) {
    val options = listOf(
        15 to "15 minutes",
        30 to "30 minutes",
        45 to "45 minutes",
        60 to "60 minutes"
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Schedule, contentDescription = null, tint = MousikeGreen)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Sleep Timer", color = TextPrimary, fontFamily = com.mousike.app.ui.theme.Manrope, fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column {
                if (currentMinutes != null && remainingSec > 0) {
                    val m = remainingSec / 60
                    val s = remainingSec % 60
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(MousikeGreen.copy(alpha = 0.15f))
                            .padding(12.dp)
                    ) {
                        Text(
                            text = "Active: ${String.format("%02d:%02d", m, s)} remaining",
                            color = MousikeGreen,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                }

                options.forEach { (mins, label) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable {
                                onSelectMinutes(mins)
                                onDismiss()
                            }
                            .padding(vertical = 12.dp, horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(label, color = TextPrimary, fontSize = 15.sp)
                        if (currentMinutes == mins) {
                            Icon(Icons.Default.Check, contentDescription = "Selected", tint = MousikeGreen)
                        }
                    }
                }

                if (currentMinutes != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = {
                            onSelectMinutes(null)
                            onDismiss()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color.Red.copy(alpha = 0.8f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Turn Off Sleep Timer", color = Color.White)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = TextMuted)
            }
        },
        containerColor = SurfaceCard
    )
}

@Composable
fun EqualizerDialog(
    playbackState: PlaybackState,
    onDismiss: () -> Unit,
    onSelectPreset: (String) -> Unit,
    onCustomChange: (bass: Float, vocal: Float, treble: Float) -> Unit
) {
    val presets = listOf("Balanced", "Bass Boost", "Acoustic Studio", "Electronic Beats", "Rock & Alt", "Vocal Clarity")
    var bass by remember { mutableFloatStateOf(playbackState.bassBoost) }
    var vocal by remember { mutableFloatStateOf(playbackState.vocalClarity) }
    var treble by remember { mutableFloatStateOf(playbackState.trebleBoost) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Tune, contentDescription = null, tint = MousikeGreen)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Sound Equalizer", color = TextPrimary, fontFamily = com.mousike.app.ui.theme.Manrope, fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column {
                Text("PRESETS", color = TextMuted, fontSize = 11.sp, fontFamily = com.mousike.app.ui.theme.Manrope, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(6.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    presets.take(3).forEach { p ->
                        val isSel = playbackState.equalizerPreset == p
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isSel) MousikeGreen else SurfaceElevated)
                                .clickable { onSelectPreset(p) }
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = p.split(" ").first(),
                                color = if (isSel) ObsidianDark else TextPrimary,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(6.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    presets.drop(3).forEach { p ->
                        val isSel = playbackState.equalizerPreset == p
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isSel) MousikeGreen else SurfaceElevated)
                                .clickable { onSelectPreset(p) }
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = p.split(" ").first(),
                                color = if (isSel) ObsidianDark else TextPrimary,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                Text("FREQUENCY BANDS", color = TextMuted, fontSize = 11.sp, fontFamily = com.mousike.app.ui.theme.Manrope, fontWeight = FontWeight.Bold)

                Text("Bass (${(bass * 100).toInt()}%)", color = TextSecondary, fontSize = 12.sp)
                Slider(
                    value = bass,
                    onValueChange = {
                        bass = it
                        onCustomChange(bass, vocal, treble)
                    },
                    colors = SliderDefaults.colors(thumbColor = MousikeGreen, activeTrackColor = MousikeGreen)
                )

                Text("Mid / Vocals (${(vocal * 100).toInt()}%)", color = TextSecondary, fontSize = 12.sp)
                Slider(
                    value = vocal,
                    onValueChange = {
                        vocal = it
                        onCustomChange(bass, vocal, treble)
                    },
                    colors = SliderDefaults.colors(thumbColor = MousikeGreen, activeTrackColor = MousikeGreen)
                )

                Text("Treble (${(treble * 100).toInt()}%)", color = TextSecondary, fontSize = 12.sp)
                Slider(
                    value = treble,
                    onValueChange = {
                        treble = it
                        onCustomChange(bass, vocal, treble)
                    },
                    colors = SliderDefaults.colors(thumbColor = MousikeGreen, activeTrackColor = MousikeGreen)
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(containerColor = MousikeGreen, contentColor = ObsidianDark)
            ) {
                Text("Done")
            }
        },
        containerColor = SurfaceCard
    )
}

@Composable
fun FeedbackDialog(
    track: Track,
    onDismiss: () -> Unit,
    onFeedbackSelected: (String) -> Unit
) {
    val options = listOf(
        "MORE_LIKE_THIS" to "More songs like this",
        "NOT_INTERESTED" to "Not interested in this track",
        "LESS_OF_ARTIST" to "Less of ${track.artist}",
        "DONT_RECOMMEND" to "Don't recommend this song"
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("Tune Recommendations", color = TextPrimary, fontFamily = com.mousike.app.ui.theme.Manrope, fontWeight = FontWeight.Bold)
        },
        text = {
            Column {
                Text(
                    text = "${track.title} • ${track.artist}",
                    color = TextSecondary,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                options.forEach { (type, label) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable {
                                onFeedbackSelected(type)
                                onDismiss()
                            }
                            .padding(vertical = 12.dp, horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(label, color = TextPrimary, fontSize = 14.sp)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close", color = TextMuted)
            }
        },
        containerColor = SurfaceCard
    )
}
