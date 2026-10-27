package com.mousike.app.ui.screens
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.unit.*
import com.mousike.app.ui.components.TrackItem
import com.mousike.app.ui.theme.*
import com.mousike.app.ui.viewmodel.MousikeViewModel

@Composable fun DownloadsScreen(vm: MousikeViewModel) {
    val entries by vm.downloads.collectAsState(); val playing by vm.browsingPlaybackState.collectAsState();val liked by vm.likedTrackIds.collectAsState()
    LazyColumn(Modifier.fillMaxSize().background(ObsidianDark),contentPadding=PaddingValues(16.dp,12.dp,16.dp,170.dp)) {
        item { Row(verticalAlignment=Alignment.CenterVertically) { IconButton(onClick={vm.navigateBack()}) { Icon(Icons.Default.ArrowBack,"Back",tint=TextPrimary) };Text("Downloads",style=MaterialTheme.typography.headlineMedium,color=TextPrimary) };Text("Your offline collection • downloads stay on this device",color=TextSecondary,fontSize=13.sp,modifier=Modifier.padding(12.dp)) }
        if(entries.none { it.state!="Removed" }) item { Text("Download a song from its ••• menu. Wi-Fi only is enabled by default.",color=TextSecondary,modifier=Modifier.padding(20.dp)) }
        items(entries.filter { it.state!="Removed" },key={it.track.id}) { entry ->
            Column(Modifier.fillMaxWidth().padding(vertical=8.dp)) {
                TrackItem(entry.track,playing.currentTrack?.id==entry.track.id,playing.isPlaying,entry.track.id in liked,
                    {if(entry.state=="Complete") vm.playTrack(entry.track,listOf(entry.track))else vm.showSnackbar("Download is not ready")},
                    {vm.toggleLikeTrack(entry.track.id)},{vm.addToQueue(entry.track)},{vm.setTrackToAddToPlaylist(entry.track)},{vm.setFeedbackDialogForTrack(entry.track)})
                Row(Modifier.fillMaxWidth().padding(start=14.dp),verticalAlignment=Alignment.CenterVertically) {
                    Text(if(entry.state=="Downloading")"${entry.progress}%"else entry.state,color=if(entry.state=="Complete")MousikeGreen else TextSecondary,modifier=Modifier.weight(1f),fontSize=12.sp)
                    if(entry.state in setOf("Failed","Cancelled")) TextButton(onClick={vm.download(entry.track)}) { Text("Retry") }
                    if(entry.state in setOf("Queued","Downloading")) IconButton(onClick={vm.cancelDownload(entry.track)}){Icon(Icons.Default.Close,"Cancel download",tint=TextSecondary)}
                    if(entry.state=="Complete") IconButton(onClick={vm.deleteDownload(entry.track)}){Icon(Icons.Default.DeleteOutline,"Delete download",tint=TextSecondary)}
                }
                if(entry.state=="Downloading") LinearProgressIndicator(progress={entry.progress/100f},modifier=Modifier.fillMaxWidth().padding(horizontal=14.dp),color=MousikeGreen)
                if(entry.error.isNotBlank()) Text(entry.error,color=TextMuted,fontSize=12.sp,modifier=Modifier.padding(14.dp))
            }
        }
    }
}
