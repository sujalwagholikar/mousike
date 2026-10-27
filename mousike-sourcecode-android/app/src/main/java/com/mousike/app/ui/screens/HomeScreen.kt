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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.platform.LocalContext
import com.mousike.app.ui.components.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mousike.app.ui.components.MusicArtwork
import com.mousike.app.data.catalog.MusicCatalog
import com.mousike.app.data.model.Album
import com.mousike.app.data.model.Artist
import com.mousike.app.data.model.Track
import com.mousike.app.ui.components.ArtworkCard
import com.mousike.app.ui.components.TrackItem
import com.mousike.app.ui.theme.GenreCyan
import com.mousike.app.ui.theme.GenrePurple
import com.mousike.app.ui.theme.MousikeGreen
import com.mousike.app.ui.theme.ObsidianDark
import com.mousike.app.ui.theme.SurfaceCard
import com.mousike.app.ui.theme.SurfaceElevated
import com.mousike.app.ui.theme.TextMuted
import com.mousike.app.ui.theme.TextPrimary
import com.mousike.app.ui.theme.TextSecondary
import com.mousike.app.ui.viewmodel.MousikeViewModel
import com.mousike.app.ui.viewmodel.NavScreen

@Composable
fun HomeScreen(
    viewModel: MousikeViewModel,
    modifier: Modifier = Modifier
) {
    val catalogVersion by viewModel.catalogVersion.collectAsState()
    val playbackState by viewModel.browsingPlaybackState.collectAsState()
    val recommendations by viewModel.recommendations.collectAsState()
    val homeFeed by viewModel.homeFeed.collectAsState()
    val homeSongs=(recommendations.madeForYou+homeFeed.songs).distinctBy {it.id}.take(60)
    val homeDiscoveries=homeFeed.discoveries.ifEmpty {recommendations.discoverWeekly}
    val homeAlbums=homeFeed.albums.ifEmpty {MusicCatalog.ALBUMS.take(96)}
    LaunchedEffect(Unit){viewModel.expandHome()}
    val likedTrackIds by viewModel.likedTrackIds.collectAsState()
    val appearance = com.mousike.app.ui.theme.LocalAppearance.current
    val greeting = viewModel.getDynamicGreeting()
    val context=LocalContext.current
    var buildingMix by remember {mutableStateOf<PersonalMix?>(null)}
    var mixStage by remember {mutableIntStateOf(0)}
    var mixError by remember {mutableStateOf<String?>(null)}
    var retryMix by remember {mutableIntStateOf(0)}
    val inspecting by viewModel.inspection.collectAsState()
    DailyFeatureSpotlight(enabled=buildingMix==null && inspecting==null,onCustomise={viewModel.navigateTo(NavScreen.SETTINGS)})
    val mixTracks=remember(recommendations,catalogVersion) {PersonalMixes.associate {it.id to personalMixTracks(it,recommendations,MusicCatalog.TRACKS)}}
    LaunchedEffect(buildingMix,retryMix) {
        val mix=buildingMix ?: return@LaunchedEffect
        mixStage=0;mixError=null
        suspend fun beat(){if(!appearance.reduceMotion)delay(260)}
        beat();mixStage=1
        val selected=withContext(Dispatchers.Default){personalMixTracks(mix,recommendations,MusicCatalog.TRACKS)}
        if(selected.isEmpty()) {
            mixError="Add more ${when(mix.id){"bollywood"->"Hindi or Bollywood";"english"->"English-language";"indie"->"indie or alternative";"chill"->"acoustic, chill or lo-fi";else->"favourite"}} songs through Search to grow this mix."
            return@LaunchedEffect
        }
        beat();mixStage=2
        val album=Album("personal-collection-${mix.id}",mix.title,"Made for ${appearance.name}",selected.first().coverUrl,java.time.LocalDate.now().year,"Personal mix",selected.map {it.id},resolutionNote="A fresh collection shaped by your listening. Pick a song to begin.")
        beat();mixStage=3;beat()
        MusicCatalog.putAlbums(listOf(album));com.mousike.app.data.catalog.CatalogCache.saveSoon(context)
        buildingMix=null;viewModel.openAlbum(album)
    }
    buildingMix?.let {mix ->
        Dialog(onDismissRequest={buildingMix=null},properties=DialogProperties(usePlatformDefaultWidth=false,decorFitsSystemWindows=false)) {
            com.mousike.app.ui.components.PreparationScreen(mix.title,listOf("Reading your listening taste","Finding your sound","Shaping your collection","Your mix is ready"),mixStage,mixError,onBack={buildingMix=null},onRetry={retryMix++})
        }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(ObsidianDark).testTag("home_list"),
        contentPadding = PaddingValues(bottom = 24.dp)
    ) {
        // Dynamic Header
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    com.mousike.app.ui.components.MousikeLogo(Modifier.size(36.dp))
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "Mousiké",
                            color = MousikeGreen,
                            fontSize = 11.sp,
                            fontFamily = com.mousike.app.ui.theme.Manrope, fontWeight = FontWeight.Black,
                            letterSpacing = 1.5.sp
                        )
                        Text(
                            text = greeting,
                            color = TextPrimary,
                            fontSize = 20.sp,
                            fontFamily = com.mousike.app.ui.theme.Manrope, fontWeight = FontWeight.Bold
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = { viewModel.refreshYouTubeMusic() },
                        modifier = Modifier.testTag("refresh_home_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Refresh recommendations",
                            tint = TextSecondary
                        )
                    }
                    IconButton(
                        onClick = { viewModel.navigateTo(NavScreen.SETTINGS) },
                        modifier = Modifier.testTag("home_settings_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Settings",
                            tint = TextSecondary
                        )
                    }
                }
            }
        }

        item {
            val status by viewModel.catalogStatus.collectAsState()
            Text(text = status, color = TextMuted, fontSize = 12.sp,
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 4.dp))
        }

        // Quick Resumption Tiles (2-column grid of 6 items)
        item {
            val quickPicks = recommendations.madeForYou.take(6)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                for (i in 0 until (quickPicks.size / 2)) {
                    val track1 = quickPicks[i * 2]
                    val track2 = quickPicks.getOrNull(i * 2 + 1)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        QuickPickTile(
                            track = track1,
                            isPlaying = playbackState.currentTrack?.id == track1.id && playbackState.isPlaying,
                            onClick = { viewModel.playTrack(track1, quickPicks) },
                            modifier = Modifier.weight(1f)
                        )
                        if (track2 != null) {
                            QuickPickTile(
                                track = track2,
                                isPlaying = playbackState.currentTrack?.id == track2.id && playbackState.isPlaying,
                                onClick = { viewModel.playTrack(track2, quickPicks) },
                                modifier = Modifier.weight(1f)
                            )
                        } else {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
        }

        // Made For You Shelf
        item {
            SectionHeader(title = "Made For You", subtitle = "Tailored to your mood & taste")
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(homeSongs,key={it.id}) { track ->
                    ArtworkCard(
                        title = track.title,
                        subtitle = track.artist,
                        imageUrl = track.coverUrl,
                        onClick = { viewModel.playTrack(track, homeSongs) },
                        onPlayClick = { viewModel.playTrack(track, homeSongs) }
                    )
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
        }

        // Discover Weekly Shelf
        item {
            SectionHeader(title = "Discover Weekly", subtitle = "Fresh tracks outside your usual rotation")
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(homeDiscoveries,key={it.id}) { track ->
                    ArtworkCard(
                        title = track.title,
                        subtitle = track.genre,
                        imageUrl = track.coverUrl,
                        onClick = { viewModel.playTrack(track, homeDiscoveries) },
                        onPlayClick = { viewModel.playTrack(track, homeDiscoveries) }
                    )
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
        }

        // Existing two builders remain intact; themed collections reuse their ranked candidates.
        if(appearance.showMixes) item {
            SectionHeader(title="Your daily soundtrack",subtitle="Eight collections. A little more you.")
            LazyRow(modifier=Modifier.testTag("mix_shelf"),contentPadding=PaddingValues(horizontal=16.dp),horizontalArrangement=Arrangement.spacedBy(14.dp)) {
                items(PersonalMixes,key={it.id}) {mix ->
                    PersonalMixCard(mix,mixTracks[mix.id].orEmpty()) {
                        when(mix.id) {"daily-1"->viewModel.buildDailyMix(1);"daily-2"->viewModel.buildDailyMix(2);else->buildingMix=mix}
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }

        // Featured Artists
        if(appearance.showArtists) item {
            SectionHeader(title = "Artists You'll Love", subtitle = "Popular performers")
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                items(MusicCatalog.ARTISTS) { artist ->
                    ArtworkCard(
                        title = artist.name,
                        subtitle = artist.genres.joinToString(" • "),
                        imageUrl = artist.avatarUrl.ifBlank {MusicCatalog.getTracksForArtist(artist.name).firstOrNull {it.coverUrl.isNotBlank()}?.coverUrl.orEmpty()},
                        onClick = { viewModel.openArtist(artist) },
                        isCircular = true
                    )
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
        }

        // Popular Albums
        if(appearance.showAlbums) { homeAlbums.chunked(32).forEachIndexed {shelf,albums -> item {
            SectionHeader(title = listOf("Albums For You","More From Your World","Explore More Albums")[shelf], subtitle = "More releases, shaped by your taste")
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(albums,key={it.id}) { album ->
                    ArtworkCard(
                        title = album.title,
                        subtitle = listOf(album.artist,album.year.takeIf {it>0}?.toString()).filterNotNull().filter {it.isNotBlank()}.joinToString(" • "),
                        imageUrl = album.coverUrl.ifBlank {MusicCatalog.getTracksForAlbum(album.title).firstOrNull {it.coverUrl.isNotBlank()}?.coverUrl.orEmpty()},
                        onClick = { viewModel.openAlbum(album) }
                    )
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
        }
        }}

        // Heavy Rotation List
        item {
            SectionHeader(title = "Heavy Rotation", subtitle = "Your top played anthems")
        }
        items(recommendations.heavyRotation.take(4)) { track ->
            TrackItem(
                track = track,
                isCurrentTrack = playbackState.currentTrack?.id == track.id,
                isPlaying = playbackState.isPlaying,
                isLiked = likedTrackIds.contains(track.id),
                onTrackClick = { viewModel.playTrack(track, recommendations.heavyRotation) },
                onToggleLike = { viewModel.toggleLikeTrack(track.id) },
                onAddToQueue = { viewModel.addToQueue(track) },
                onAddToPlaylist = { viewModel.setTrackToAddToPlaylist(track) },
                onFeedback = { viewModel.setFeedbackDialogForTrack(track) },
                modifier = Modifier.padding(horizontal = 8.dp)
            )
        }
    }
}

@Composable
fun QuickPickTile(
    track: Track,
    isPlaying: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .height(52.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(SurfaceCard)
            .clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically
    ) {
        MusicArtwork(
            model = track.coverUrl,
            contentDescription = track.title,
            modifier = Modifier.size(52.dp),
            contentScale = ContentScale.Fit
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = track.title,
            color = if (isPlaying) MousikeGreen else TextPrimary,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .weight(1f)
                .padding(end = 6.dp)
        )
        if (isPlaying) {
            Box(
                modifier = Modifier
                    .padding(end = 8.dp)
                    .size(24.dp)
                    .background(MousikeGreen, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.GraphicEq,
                    contentDescription = null,
                    tint = ObsidianDark,
                    modifier = Modifier.size(14.dp)
                )
            }
        }
    }
}

@Composable
fun SectionHeader(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Text(
            text = title,
            color = TextPrimary,
            fontSize = 18.sp,
            fontFamily = com.mousike.app.ui.theme.Manrope, fontWeight = FontWeight.Bold
        )
        Text(
            text = subtitle,
            color = TextMuted,
            fontSize = 12.sp
        )
    }
}
