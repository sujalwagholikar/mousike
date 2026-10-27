package com.mousike.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mousike.app.ui.components.AddToPlaylistDialog
import com.mousike.app.ui.components.CreatePlaylistDialog
import com.mousike.app.ui.components.EqualizerDialog
import com.mousike.app.ui.components.FeedbackDialog
import com.mousike.app.ui.components.MiniPlayer
import com.mousike.app.playback.AudioPlayerEngine
import com.mousike.app.ui.screens.OnboardingScreen
import com.mousike.app.ui.screens.DownloadsScreen

import com.mousike.app.ui.components.MousikeBottomBar
import com.mousike.app.ui.components.SleepTimerDialog
import com.mousike.app.ui.screens.AlbumDetailScreen
import com.mousike.app.ui.screens.ArtistDetailScreen
import com.mousike.app.ui.screens.ExploreScreen
import com.mousike.app.ui.screens.HomeScreen
import com.mousike.app.ui.screens.LibraryScreen
import com.mousike.app.ui.screens.LyricsScreen
import com.mousike.app.ui.screens.PlayerFullScreen
import com.mousike.app.ui.screens.PlaylistDetailScreen
import com.mousike.app.ui.screens.QueueScreen
import com.mousike.app.ui.screens.SearchScreen
import com.mousike.app.ui.screens.SettingsScreen
import com.mousike.app.ui.theme.MousikeTheme
import com.mousike.app.ui.theme.ObsidianDark
import com.mousike.app.ui.viewmodel.MousikeViewModel
import com.mousike.app.ui.viewmodel.NavScreen

class MainActivity : ComponentActivity() {
    private val sharedText=androidx.compose.runtime.mutableStateOf<String?>(null)
    private val playerRequest=androidx.compose.runtime.mutableIntStateOf(0)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        sharedText.value=intent.getStringExtra(android.content.Intent.EXTRA_TEXT)
        if(intent.getBooleanExtra("open_player",false))playerRequest.intValue++
        RuntimeMode.offlineFixture=BuildConfig.DEBUG && intent.getBooleanExtra("offline_fixture",false)
        if(RuntimeMode.offlineFixture) com.mousike.app.data.catalog.DemoCatalog.install(this)
        enableEdgeToEdge()
        if(android.os.Build.VERSION.SDK_INT>=33 && checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)!=android.content.pm.PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS),33)
        }
        setContent {
            MousikeTheme {
                val prefs by com.mousike.app.settings.AppSettings.get(this).state.collectAsState()
                val light=prefs.theme=="Light" || prefs.theme=="System" && !androidx.compose.foundation.isSystemInDarkTheme()
                androidx.compose.runtime.SideEffect {
                    androidx.core.view.WindowInsetsControllerCompat(window,window.decorView).apply {
                        isAppearanceLightStatusBars=light
                        isAppearanceLightNavigationBars=light
                    }
                }
                MousikeApp(incomingText=sharedText.value,openPlayerRequest=playerRequest.intValue)
            }
        }
    }
    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent);setIntent(intent)
        sharedText.value=intent.getStringExtra(android.content.Intent.EXTRA_TEXT)
        if(intent.getBooleanExtra("open_player",false))playerRequest.intValue++
    }
}

@Composable
fun MousikeReadyApp(
    viewModel: MousikeViewModel = viewModel(),
    incomingText: String? = null
) {
    val preferences by viewModel.preferences.collectAsState()
    LaunchedEffect(incomingText) { if(!incomingText.isNullOrBlank()) { viewModel.updateSearchQuery(incomingText);viewModel.navigateTo(NavScreen.SEARCH) } }
    val inspection by viewModel.inspection.collectAsState()
    val currentScreen by viewModel.currentScreen.collectAsState()
    val playbackState by viewModel.browsingPlaybackState.collectAsState()
    val likedTrackIds by viewModel.likedTrackIds.collectAsState()
    val playlists by viewModel.playlists.collectAsState()

    val showCreatePlaylistDialog by viewModel.showCreatePlaylistDialog.collectAsState()
    val trackToAddToPlaylist by viewModel.trackToAddToPlaylist.collectAsState()
    val showSleepTimerDialog by viewModel.showSleepTimerDialog.collectAsState()
    val showEqualizerDialog by viewModel.showEqualizerDialog.collectAsState()
    val feedbackTrack by viewModel.showFeedbackDialogForTrack.collectAsState()

    val snackbarMessage by viewModel.snackbarMessage.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(snackbarMessage) {
        snackbarMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.dismissSnackbar()
        }
    }

    val isCurrentTrackLiked = playbackState.currentTrack?.let { likedTrackIds.contains(it.id) } ?: false
    val isImmersivePlayerScreen = currentScreen == NavScreen.FULL_PLAYER || currentScreen == NavScreen.LYRICS || currentScreen == NavScreen.MIX_BUILD
    // Global Back Handler
    BackHandler(enabled = currentScreen != NavScreen.HOME) {
        viewModel.navigateBack()
    }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .background(ObsidianDark),
        contentWindowInsets = WindowInsets.safeDrawing,
        bottomBar = {
            if(!isImmersivePlayerScreen) {
                Column(Modifier.fillMaxWidth().background(ObsidianDark).navigationBarsPadding()) {
                    ConnectedMiniPlayer(viewModel)
                    MousikeBottomBar(currentScreen=currentScreen,onNavigate={viewModel.navigateTo(it)})
                }
            }
        },
        snackbarHost = {
            SnackbarHost(
                hostState = snackbarHostState
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .consumeWindowInsets(innerPadding)
                .background(ObsidianDark)
        ) {
            // Main Screen View Switcher
            AnimatedContent(
                targetState = currentScreen,
                transitionSpec = { fadeIn(animationSpec=androidx.compose.animation.core.tween(if(preferences.reduceMotion)0 else 120)) togetherWith fadeOut(animationSpec=androidx.compose.animation.core.tween(if(preferences.reduceMotion)0 else 100)) },
                label = "screen_transition"
            ) { screen ->
                when (screen) {
                    NavScreen.HOME -> HomeScreen(viewModel = viewModel)
                    NavScreen.SEARCH -> SearchScreen(viewModel = viewModel)
                    NavScreen.EXPLORE -> ExploreScreen(viewModel = viewModel)
                    NavScreen.LIBRARY -> LibraryScreen(viewModel = viewModel)
                    NavScreen.SETTINGS -> SettingsScreen(viewModel = viewModel)
                    NavScreen.ABOUT -> com.mousike.app.ui.screens.AboutScreen(viewModel)
                    NavScreen.MIX_BUILD -> com.mousike.app.ui.screens.MixBuildScreen(viewModel)
                    NavScreen.PLAYLIST_DETAIL -> PlaylistDetailScreen(viewModel = viewModel)
                    NavScreen.ALBUM_DETAIL -> AlbumDetailScreen(viewModel = viewModel)
                    NavScreen.ARTIST_DETAIL -> ArtistDetailScreen(viewModel = viewModel)
                    NavScreen.FULL_PLAYER -> PlayerFullScreen(viewModel = viewModel)
                    NavScreen.DOWNLOADS -> DownloadsScreen(viewModel)
                    NavScreen.LYRICS -> LyricsScreen(viewModel = viewModel)
                    NavScreen.QUEUE -> QueueScreen(viewModel = viewModel)
                }
            }


        }
    }

    if(inspection!=null) androidx.compose.material3.AlertDialog(
        onDismissRequest={viewModel.cancelInspection()},title={androidx.compose.material3.Text("Finding your music")},
        text={Column { androidx.compose.material3.LinearProgressIndicator(Modifier.fillMaxWidth()); Spacer(Modifier.height(18.dp));androidx.compose.material3.Text(inspection.orEmpty()) }},
        confirmButton={androidx.compose.material3.TextButton(onClick={viewModel.cancelInspection()}) { androidx.compose.material3.Text("Cancel") }})

    // Dialogs
    if (showCreatePlaylistDialog) {
        CreatePlaylistDialog(
            onDismiss = { viewModel.setCreatePlaylistDialogVisible(false) },
            onConfirm = { name, desc -> viewModel.createPlaylist(name, desc) }
        )
    }

    trackToAddToPlaylist?.let { track ->
        AddToPlaylistDialog(
            track = track,
            playlists = playlists,
            onDismiss = { viewModel.setTrackToAddToPlaylist(null) },
            onPlaylistSelected = { playlistId ->
                viewModel.addTrackToPlaylist(playlistId, track.id)
            },
            onCreateNewPlaylistClick = {
                viewModel.setCreatePlaylistDialogVisible(true)
            }
        )
    }

    if (showSleepTimerDialog) {
        val timerPlayback by viewModel.playbackState.collectAsState()
        SleepTimerDialog(
            currentMinutes = playbackState.sleepTimerMinutes,
            remainingSec = timerPlayback.sleepTimerRemainingSec,
            onDismiss = { viewModel.setSleepTimerDialogVisible(false) },
            onSelectMinutes = { viewModel.setSleepTimer(it) }
        )
    }

    if (showEqualizerDialog) {
        EqualizerDialog(
            playbackState = playbackState,
            onDismiss = { viewModel.setEqualizerDialogVisible(false) },
            onSelectPreset = { viewModel.setEqualizerPreset(it) },
            onCustomChange = { b, v, t -> viewModel.setCustomEqualizer(b, v, t) }
        )
    }

    feedbackTrack?.let { track ->
        FeedbackDialog(
            track = track,
            onDismiss = { viewModel.setFeedbackDialogForTrack(null) },
            onFeedbackSelected = { feedbackType ->
                viewModel.sendFeedback(track, feedbackType)
            }
        )
    }
}

/** Progress invalidates only this small surface while browsing. */
@Composable
private fun ConnectedMiniPlayer(viewModel: MousikeViewModel) {
    val live by viewModel.playbackState.collectAsState()
    val liked by viewModel.likedTrackIds.collectAsState()
    MiniPlayer(playbackState=live,isLiked=live.currentTrack?.id in liked,
        onExpandClick={viewModel.openFullPlayer()},onTogglePlayPause={viewModel.togglePlayPause()},
        onNextClick={viewModel.playNext()},onToggleLike={live.currentTrack?.let {viewModel.toggleLikeTrack(it.id)}})
}

@Composable fun MousikeApp(viewModel: MousikeViewModel=viewModel(),incomingText: String?=null,openPlayerRequest: Int=0) {
    LaunchedEffect(openPlayerRequest) {if(openPlayerRequest>0 && viewModel.playbackState.value.currentTrack!=null)viewModel.navigateTo(NavScreen.FULL_PLAYER)}
    val p by viewModel.preferences.collectAsState()
    val stage by viewModel.assemblyStage.collectAsState()
    val context=androidx.compose.ui.platform.LocalContext.current
    androidx.compose.runtime.SideEffect {
        (context as? android.app.Activity)?.let {com.mousike.app.ui.RefreshRateController.apply(it,p.refreshRate)}
    }
    AnimatedContent(targetState=when{stage!=null->"building";!p.onboarded->"onboarding";else->"home"},
        modifier=Modifier.fillMaxSize().background(ObsidianDark),
        transitionSpec={(fadeIn(androidx.compose.animation.core.tween(if(p.reduceMotion)0 else 420))+androidx.compose.animation.scaleIn(androidx.compose.animation.core.tween(if(p.reduceMotion)0 else 420),initialScale=.98f)+androidx.compose.animation.slideInVertically(androidx.compose.animation.core.tween(if(p.reduceMotion)0 else 420)){it/30}) togetherWith fadeOut(androidx.compose.animation.core.tween(if(p.reduceMotion)0 else 160))},label="personal_home_transition") {page ->
        when(page) {
            "building" -> com.mousike.app.ui.components.PreparationScreen("Your music world",listOf("Analysing your preferences","Customising for you","Building your playlist","Here we go"),stage ?: 3)
            "onboarding" -> OnboardingScreen(viewModel)
            else -> MousikeReadyApp(viewModel,incomingText)
        }
    }
}
