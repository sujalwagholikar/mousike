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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mousike.app.ui.components.MusicArtwork
import com.mousike.app.data.catalog.MusicCatalog
import com.mousike.app.data.model.UserPlaylist
import com.mousike.app.ui.theme.GenreCyan
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
fun LibraryScreen(
    viewModel: MousikeViewModel,
    modifier: Modifier = Modifier
) {
    val catalogVersion by viewModel.catalogVersion.collectAsState()
    val playbackState by viewModel.browsingPlaybackState.collectAsState()
    val playlists by viewModel.playlists.collectAsState()
    val likedTracks by viewModel.likedTracks.collectAsState()
    val recentlyPlayed by viewModel.recentlyPlayed.collectAsState()
    val savedAlbumIds by viewModel.savedAlbumIds.collectAsState()
    val savedArtistIds by viewModel.savedArtistIds.collectAsState()

    var selectedFilter by remember { mutableStateOf("All") }
    val filters = listOf("All", "Playlists", "Liked Songs", "Saved Albums", "Artists", "History")

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
                Text(
                    text = "Your Library",
                    color = TextPrimary,
                    fontSize = 24.sp,
                    fontFamily = com.mousike.app.ui.theme.Manrope, fontWeight = FontWeight.Bold
                )

                IconButton(
                    onClick = { viewModel.setCreatePlaylistDialogVisible(true) },
                    modifier = Modifier
                        .size(38.dp)
                        .background(SurfaceElevated, CircleShape)
                        .testTag("create_playlist_fab")
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Create Playlist",
                        tint = MousikeGreen
                    )
                }
            }
        }

        item {
            androidx.compose.material3.TextButton(onClick={viewModel.navigateTo(com.mousike.app.ui.viewmodel.NavScreen.DOWNLOADS)},modifier=Modifier.padding(horizontal=16.dp)) {
                Text("↓  Downloads & offline music",color=MousikeGreen)
            }
        }
        // Filter Chips Row
        item {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(bottom = 16.dp)
            ) {
                items(filters) { filter ->
                    FilterChip(
                        selected = selectedFilter == filter,
                        onClick = { selectedFilter = filter },
                        label = { Text(filter) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MousikeGreen,
                            selectedLabelColor = ObsidianDark,
                            containerColor = SurfaceCard,
                            labelColor = TextPrimary
                        )
                    )
                }
            }
        }

        // Pinned Liked Songs Card
        if (selectedFilter == "All" || selectedFilter == "Liked Songs") {
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            Brush.linearGradient(
                                listOf(GenrePurple, GenreIndigo)
                            )
                        )
                        .clickable {
                            val likedPl = UserPlaylist(
                                id = "pl_liked_songs",
                                name = "Liked Songs",
                                description = "Your favorite saved tracks",
                                trackIds = likedTracks.map { it.id }
                            )
                            viewModel.openPlaylist(likedPl)
                        }
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(54.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color.White.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Favorite,
                            contentDescription = "Liked Songs",
                            tint = Color.White,
                            modifier = Modifier.size(28.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(14.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Liked Songs",
                            color = Color.White,
                            fontSize = 17.sp,
                            fontFamily = com.mousike.app.ui.theme.Manrope, fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "${likedTracks.size} saved tracks • Auto playlist",
                            color = Color.White.copy(alpha = 0.85f),
                            fontSize = 13.sp
                        )
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))
            }
        }

        // Smart On Repeat / History Card
        if (selectedFilter == "All" || selectedFilter == "History") {
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(SurfaceCard)
                        .clickable {
                            val histPl = UserPlaylist(
                                id = "pl_recently_played",
                                name = "Listening History",
                                description = "Recently streamed sessions",
                                trackIds = recentlyPlayed.map { it.id }
                            )
                            viewModel.openPlaylist(histPl)
                        }
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(54.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(MousikeGreen.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Repeat,
                            contentDescription = "On Repeat",
                            tint = MousikeGreen,
                            modifier = Modifier.size(28.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(14.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Recently Played & On Repeat",
                            color = TextPrimary,
                            fontSize = 16.sp,
                            fontFamily = com.mousike.app.ui.theme.Manrope, fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "${recentlyPlayed.size} tracks in recent activity",
                            color = TextMuted,
                            fontSize = 13.sp
                        )
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))
            }
        }

        // Custom Playlists Section
        if (selectedFilter == "All" || selectedFilter == "Playlists") {
            if (playlists.isNotEmpty()) {
                item {
                    Text(
                        text = "Your Playlists",
                        color = TextPrimary,
                        fontSize = 17.sp,
                        fontFamily = com.mousike.app.ui.theme.Manrope, fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }
                items(playlists) { playlist ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(SurfaceCard)
                            .clickable { viewModel.openPlaylist(playlist) }
                            .padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        MusicArtwork(
                            model = playlist.coverUrl.ifBlank { "https://images.unsplash.com/photo-1514525253161-7a46d19cd819?auto=format&fit=crop&w=400&q=80" },
                            contentDescription = playlist.name,
                            modifier = Modifier
                                .size(50.dp)
                                .clip(com.mousike.app.ui.theme.ArtworkShape),
                            contentScale = ContentScale.Fit
                        )

                        Spacer(modifier = Modifier.width(12.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = playlist.name,
                                color = TextPrimary,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "Playlist • ${playlist.trackIds.size} songs",
                                color = TextMuted,
                                fontSize = 12.sp
                            )
                        }

                        IconButton(
                            onClick = { viewModel.deletePlaylist(playlist.id) },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "Delete Playlist",
                                tint = TextMuted,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            } else if (selectedFilter == "Playlists") {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("No custom playlists yet", color = TextPrimary, fontFamily = com.mousike.app.ui.theme.Manrope, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(6.dp))
                        Text("Tap + above to create your first playlist!", color = TextMuted, fontSize = 13.sp)
                    }
                }
            }
        }

        // Saved Albums Section
        if (selectedFilter == "All" || selectedFilter == "Saved Albums") {
            val savedAlbums = MusicCatalog.ALBUMS.filter { savedAlbumIds.contains(it.id) }
            if (savedAlbums.isNotEmpty()) {
                item {
                    Text(
                        text = "Saved Albums",
                        color = TextPrimary,
                        fontSize = 17.sp,
                        fontFamily = com.mousike.app.ui.theme.Manrope, fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }
                items(savedAlbums) { album ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(SurfaceCard)
                            .clickable { viewModel.openAlbum(album) }
                            .padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        MusicArtwork(
                            model = album.coverUrl,
                            contentDescription = album.title,
                            modifier = Modifier
                                .size(50.dp)
                                .clip(com.mousike.app.ui.theme.ArtworkShape),
                            contentScale = ContentScale.Fit
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(album.title, color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                            Text("Album • ${album.artist} • ${album.year}", color = TextMuted, fontSize = 12.sp)
                        }
                    }
                }
            }
        }

        // Followed Artists Section
        if (selectedFilter == "All" || selectedFilter == "Artists") {
            val followedArtists = MusicCatalog.ARTISTS.filter { savedArtistIds.contains(it.id) }
            if (followedArtists.isNotEmpty()) {
                item {
                    Text(
                        text = "Followed Artists",
                        color = TextPrimary,
                        fontSize = 17.sp,
                        fontFamily = com.mousike.app.ui.theme.Manrope, fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }
                items(followedArtists) { artist ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(SurfaceCard)
                            .clickable { viewModel.openArtist(artist) }
                            .padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        MusicArtwork(
                            model = artist.avatarUrl,
                            contentDescription = artist.name,
                            modifier = Modifier
                                .size(50.dp)
                                .clip(CircleShape),
                            contentScale = ContentScale.Fit
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(artist.name, color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                            Text("Artist • ${artist.genres.joinToString()}", color = TextMuted, fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }
}
