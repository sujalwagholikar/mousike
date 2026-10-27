package com.mousike.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mousike.app.ui.components.MusicArtwork
import com.mousike.app.data.catalog.MusicCatalog
import com.mousike.app.data.model.GenreCategory
import com.mousike.app.ui.components.ArtworkCard
import com.mousike.app.ui.components.TrackItem
import com.mousike.app.ui.theme.MousikeGreen
import com.mousike.app.ui.theme.ObsidianDark
import com.mousike.app.ui.theme.SurfaceCard
import com.mousike.app.ui.theme.SurfaceElevated
import com.mousike.app.ui.theme.TextMuted
import com.mousike.app.ui.theme.TextPrimary
import com.mousike.app.ui.theme.TextSecondary
import com.mousike.app.ui.viewmodel.MousikeViewModel

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SearchScreen(
    viewModel: MousikeViewModel,
    modifier: Modifier = Modifier
) {
    val searchError by viewModel.searchError.collectAsState()
    val isSearching by viewModel.isSearching.collectAsState()
    val query by viewModel.searchQuery.collectAsState()
    val searchResults by viewModel.searchResults.collectAsState()
    val searchHistory by viewModel.searchHistory.collectAsState()
    val playbackState by viewModel.browsingPlaybackState.collectAsState()
    val likedTrackIds by viewModel.likedTrackIds.collectAsState()

    var selectedFilter by remember { mutableStateOf("All") }
    val filterTabs = listOf("All", "Songs", "Artists", "Albums")

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
                    text = "Search",
                    color = TextPrimary,
                    fontSize = 24.sp,
                    fontFamily = com.mousike.app.ui.theme.Manrope, fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = query,
                    onValueChange = {
                        viewModel.updateSearchQuery(it)
                    },
                    placeholder = { Text("What do you want to listen to?", color = TextMuted) },
                    leadingIcon = {
                        Icon(Icons.Default.Search, contentDescription = null, tint = TextMuted)
                    },
                    trailingIcon = {
                        if (query.isNotEmpty()) {
                            IconButton(
                                onClick = { viewModel.updateSearchQuery("") },
                                modifier = Modifier.testTag("clear_search_btn")
                            ) {
                                Icon(Icons.Default.Clear, contentDescription = "Clear", tint = TextMuted)
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        focusedContainerColor = SurfaceCard,
                        unfocusedContainerColor = SurfaceCard,
                        focusedBorderColor = MousikeGreen,
                        unfocusedBorderColor = Color.Transparent
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("search_text_input")
                )

                if (query.isNotBlank()) {
                    Spacer(modifier = Modifier.height(10.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(filterTabs) { tab ->
                            FilterChip(
                                selected = selectedFilter == tab,
                                onClick = { selectedFilter = tab },
                                label = { Text(tab) },
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
            }
        }

        // Active Search Results
        if(query.isNotBlank() && isSearching) {
            item {com.mousike.app.ui.components.SearchLoadingPanel()}
        } else if (query.isNotBlank()) {
            val showSongs = selectedFilter == "All" || selectedFilter == "Songs"
            val showArtists = selectedFilter == "All" || selectedFilter == "Artists"
            val showAlbums = selectedFilter == "All" || selectedFilter == "Albums"

            // Songs Result
            if (showSongs && searchResults.tracks.isNotEmpty()) {
                item {
                    Text(
                        text = "Songs",
                        color = TextPrimary,
                        fontSize = 17.sp,
                        fontFamily = com.mousike.app.ui.theme.Manrope, fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }
                items(searchResults.tracks) { track ->
                    TrackItem(
                        track = track,
                        isCurrentTrack = playbackState.currentTrack?.id == track.id,
                        isPlaying = playbackState.isPlaying,
                        isLiked = likedTrackIds.contains(track.id),
                        onTrackClick = {
                            viewModel.commitSearchQuery(query)
                            viewModel.playTrack(track, searchResults.tracks)
                        },
                        onToggleLike = { viewModel.toggleLikeTrack(track.id) },
                        onAddToQueue = { viewModel.addToQueue(track) },
                        onAddToPlaylist = { viewModel.setTrackToAddToPlaylist(track) },
                        onFeedback = { viewModel.setFeedbackDialogForTrack(track) },
                        modifier = Modifier.padding(horizontal = 8.dp)
                    )
                }
            }

            // Artists Result
            if (showArtists && searchResults.artists.isNotEmpty()) {
                item {
                    Text(
                        text = "Artists",
                        color = TextPrimary,
                        fontSize = 17.sp,
                        fontFamily = com.mousike.app.ui.theme.Manrope, fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(searchResults.artists) { artist ->
                            ArtworkCard(
                                title = artist.name,
                                subtitle = "Artist",
                                imageUrl = artist.avatarUrl,
                                onClick = {
                                    viewModel.commitSearchQuery(query)
                                    viewModel.openArtist(artist)
                                },
                                isCircular = true
                            )
                        }
                    }
                }
            }

            // Albums Result
            if (showAlbums && searchResults.albums.isNotEmpty()) {
                item {
                    Text(
                        text = "Albums",
                        color = TextPrimary,
                        fontSize = 17.sp,
                        fontFamily = com.mousike.app.ui.theme.Manrope, fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(searchResults.albums) { album ->
                            ArtworkCard(
                                title = album.title,
                                subtitle = album.artist,
                                imageUrl = album.coverUrl,
                                onClick = {
                                    viewModel.commitSearchQuery(query)
                                    viewModel.openAlbum(album)
                                }
                            )
                        }
                    }
                }
            }

            val visibleCount=(if(showSongs)searchResults.tracks.size else 0)+(if(showArtists)searchResults.artists.size else 0)+(if(showAlbums)searchResults.albums.size else 0)
            if(visibleCount==0) item {
                com.mousike.app.ui.components.SearchEmptyPanel(query,searchError,onRetry={viewModel.retrySearch()})
            }

        } else {
            // Search History
            if (searchHistory.isNotEmpty()) {
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Recent searches", color = TextPrimary, fontFamily = com.mousike.app.ui.theme.Manrope, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        TextButton(onClick = { viewModel.clearSearchHistory() }) {
                            Text("Clear all", color = TextMuted, fontSize = 12.sp)
                        }
                    }
                    FlowRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        searchHistory.forEach { hist ->
                            AssistChip(
                                onClick = { viewModel.updateSearchQuery(hist) },
                                label = { Text(hist, color = TextPrimary) },
                                leadingIcon = {
                                    Icon(Icons.Default.History, contentDescription = null, tint = TextMuted, modifier = Modifier.size(16.dp))
                                },
                                trailingIcon = {
                                    Icon(
                                        Icons.Default.Close,
                                        contentDescription = "Delete",
                                        tint = TextMuted,
                                        modifier = Modifier
                                            .size(14.dp)
                                            .clickable { viewModel.removeSearchHistory(hist) }
                                    )
                                },
                                colors = AssistChipDefaults.assistChipColors(containerColor = SurfaceCard)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }

            // Browse All Categories Grid
            item {
                Text(
                    text = "Browse all",
                    color = TextPrimary,
                    fontSize = 18.sp,
                    fontFamily = com.mousike.app.ui.theme.Manrope, fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )

                val categories = MusicCatalog.GENRES
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    for (i in 0 until (categories.size / 2)) {
                        val cat1 = categories[i * 2]
                        val cat2 = categories.getOrNull(i * 2 + 1)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            GenreBrowseTile(
                                category = cat1,
                                onClick = { viewModel.openGenre(cat1.name) },
                                modifier = Modifier.weight(1f)
                            )
                            if (cat2 != null) {
                                GenreBrowseTile(
                                    category = cat2,
                                    onClick = { viewModel.openGenre(cat2.name) },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun GenreBrowseTile(
    category: GenreCategory,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .height(96.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(
                Brush.linearGradient(
                    listOf(Color(category.gradientStartHex), Color(category.gradientEndHex))
                )
            )
            .clickable(onClick = onClick)
            .padding(12.dp)
            .testTag("genre_tile_${category.id}")
    ) {
        Text(
            text = category.name,
            color = Color.White,
            fontSize = 16.sp,
            fontFamily = com.mousike.app.ui.theme.Manrope, fontWeight = FontWeight.Bold,
            modifier = Modifier.align(Alignment.TopStart)
        )
    }
}
