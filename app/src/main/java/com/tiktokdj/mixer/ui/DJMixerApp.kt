package com.tiktokdj.mixer.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.tiktokdj.mixer.audio.TrackLoader
import com.tiktokdj.mixer.engine.MixerEngine
import com.tiktokdj.mixer.model.MixerState
import com.tiktokdj.mixer.model.Track
import com.tiktokdj.mixer.streaming.StreamManager
import com.tiktokdj.mixer.ui.deck.DeckPanel
import com.tiktokdj.mixer.ui.effects.EffectsPanel
import com.tiktokdj.mixer.ui.mixer.CrossfaderBar
import com.tiktokdj.mixer.ui.mixer.EQPanel
import com.tiktokdj.mixer.ui.mixer.SpectrumAnalyzer
import com.tiktokdj.mixer.ui.stream.StreamPanel
import com.tiktokdj.mixer.updater.AppUpdater
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DJMixerApp(
    mixerEngine: MixerEngine,
    streamManager: StreamManager,
    appUpdater: AppUpdater
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("Mixer", "Effects", "Stream", "Library")

    val mixerState by mixerEngine.mixerState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("TikTok DJ Mixer") },
                actions = {
                    IconButton(onClick = { /* Settings */ }) {
                        Icon(Icons.Default.Settings, "Settings")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            )
        },
        bottomBar = {
            NavigationBar {
                tabs.forEachIndexed { index, title ->
                    NavigationBarItem(
                        icon = {
                            when (index) {
                                0 -> Icon(Icons.Default.Equalizer, "Mixer")
                                1 -> Icon(Icons.Default.AutoAwesome, "Effects")
                                2 -> Icon(Icons.Default.LiveTv, "Stream")
                                3 -> Icon(Icons.Default.LibraryMusic, "Library")
                            }
                        },
                        label = { Text(title) },
                        selected = selectedTab == index,
                        onClick = { selectedTab = index }
                    )
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(8.dp)
        ) {
            when (selectedTab) {
                0 -> MixerScreen(mixerEngine, mixerState)
                1 -> EffectsScreen(mixerEngine)
                2 -> StreamScreen(streamManager)
                3 -> LibraryScreen(mixerEngine)
            }
        }
    }
}

@Composable
fun MixerScreen(mixerEngine: MixerEngine, mixerState: MixerState) {
    LaunchedEffect(Unit) {
        while (true) {
            mixerEngine.updateMixerState()
            delay(100)
        }
    }

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            DeckPanel(
                deckId = "A",
                state = mixerState.deckA,
                modifier = Modifier.weight(1f),
                onPlayPause = { mixerEngine.togglePlayPauseDeckA() },
                onSeek = { mixerEngine.deckA.seekTo(it) },
                onVolumeChange = { mixerEngine.deckA.setVolume(it) },
                onPitchChange = { mixerEngine.deckA.setSpeed(it) },
                onCue = { mixerEngine.deckA.setCuePoint() },
                onHotCue = { mixerEngine.deckA.jumpToHotCue(it) }
            )

            DeckPanel(
                deckId = "B",
                state = mixerState.deckB,
                modifier = Modifier.weight(1f),
                onPlayPause = { mixerEngine.togglePlayPauseDeckB() },
                onSeek = { mixerEngine.deckB.seekTo(it) },
                onVolumeChange = { mixerEngine.deckB.setVolume(it) },
                onPitchChange = { mixerEngine.deckB.setSpeed(it) },
                onCue = { mixerEngine.deckB.setCuePoint() },
                onHotCue = { mixerEngine.deckB.jumpToHotCue(it) }
            )
        }

        SpectrumAnalyzer(
            getSpectrumData = { mixerEngine.getSpectralData() },
            modifier = Modifier
                .fillMaxWidth()
                .height(80.dp)
        )

        EQPanel(
            deckAState = mixerState.deckA.eq,
            deckBState = mixerState.deckB.eq,
            onEQChangeDeckA = { low, mid, high -> mixerEngine.deckA.setEQ(low, mid, high) },
            onEQChangeDeckB = { low, mid, high -> mixerEngine.deckB.setEQ(low, mid, high) }
        )

        CrossfaderBar(
            position = mixerState.crossfader,
            onPositionChange = { mixerEngine.setCrossfader(it) },
            isSyncEnabled = mixerState.isSyncEnabled,
            onSyncToggle = { mixerEngine.toggleSync() }
        )
    }
}

@Composable
fun EffectsScreen(mixerEngine: MixerEngine) {
    EffectsPanel(mixerEngine = mixerEngine)
}

@Composable
fun StreamScreen(streamManager: StreamManager) {
    StreamPanel(streamManager = streamManager)
}

@Composable
fun LibraryScreen(mixerEngine: MixerEngine) {
    var searchQuery by remember { mutableStateOf("") }
    var tracks by remember { mutableStateOf(emptyList<Track>()) }
    var isLoading by remember { mutableStateOf(false) }
    val trackLoader = remember { TrackLoader(LocalContext.current.applicationContext) }
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(searchQuery) {
        if (searchQuery.isBlank()) {
            tracks = emptyList()
            isLoading = false
            return@LaunchedEffect
        }
        isLoading = true
        delay(300)
        tracks = withContext(Dispatchers.IO) {
            trackLoader.searchTracks(searchQuery)
        }
        isLoading = false
    }

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            label = { Text("Search tracks...") },
            modifier = Modifier.fillMaxWidth(),
            leadingIcon = { Icon(Icons.Default.Search, "Search") }
        )

        if (isLoading) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }

        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            items(tracks, key = { it.id }) { track ->
                ListItem(
                    headlineContent = { Text(track.title) },
                    supportingContent = { Text(track.artist) },
                    trailingContent = { Text(track.durationFormatted) },
                    leadingContent = {
                        Icon(Icons.Default.MusicNote, "Track")
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            if (mixerEngine.deckA.hasTrack().not()) {
                                mixerEngine.deckA.loadTrack(track)
                            } else {
                                mixerEngine.deckB.loadTrack(track)
                            }
                        }
                )
            }
        }
    }
}
