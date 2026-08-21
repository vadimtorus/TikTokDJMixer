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

/**
 * Корневой Compose-экран приложения: верхняя панель, нижняя навигация
 * и переключение между четырьмя вкладками (микшер, эффекты, стрим, библиотека).
 *
 * The app's root Compose screen: top bar, bottom navigation and switching
 * between the four tabs (mixer, effects, stream, library).
 *
 * @param mixerEngine Аудио-ядро / audio core
 * @param streamManager Менеджер стриминга / streaming manager
 * @param appUpdater Менеджер автообновления / auto-update manager
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DJMixerApp(
    mixerEngine: MixerEngine,
    streamManager: StreamManager,
    appUpdater: AppUpdater
) {
    // Индекс активной вкладки нижней навигации.
    // Index of the active bottom-navigation tab.
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("Mixer", "Effects", "Stream", "Library")

    // Подписка на реактивное состояние микшера: recomposition при каждом изменении.
    // Subscribe to the reactive mixer state: recomposition on every change.
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
            // Отображаем экран выбранной вкладки.
            // Show the screen of the selected tab.
            when (selectedTab) {
                0 -> MixerScreen(mixerEngine, mixerState)
                1 -> EffectsScreen(mixerEngine)
                2 -> StreamScreen(streamManager)
                3 -> LibraryScreen(mixerEngine)
            }
        }
    }
}

/**
 * Экран микшера: две деки бок о бок, спектр-анализатор, эквалайзер и кроссфейдер.
 *
 * The mixer screen: two side-by-side decks, a spectrum analyzer, EQ and crossfader.
 */
@Composable
fun MixerScreen(mixerEngine: MixerEngine, mixerState: MixerState) {
    // Опрос состояния каждые 100 мс: позиции воспроизведения меняются
    // внутри DeckPlayer, поэтому их нужно подтягивать в общий MixerState.
    //
    // Poll state every 100 ms: playback positions change inside DeckPlayer,
    // so they must be pulled into the shared MixerState.
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
            // Дека A: все колбэки пробрасываются напрямую в DeckPlayer движка.
            // Deck A: every callback is forwarded straight to the engine's DeckPlayer.
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

            // Дека B: аналогична деке A.
            // Deck B: mirrors deck A.
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

        // Спектр-анализатор: опрашивает движок с частотой ~20 FPS.
        // Spectrum analyzer: polls the engine at ~20 FPS.
        SpectrumAnalyzer(
            getSpectrumData = { mixerEngine.getSpectralData() },
            modifier = Modifier
                .fillMaxWidth()
                .height(80.dp)
        )

        // Эквалайзер обоих деков (LOW/MID/HI).
        // Both decks' equalizer (LOW/MID/HI).
        EQPanel(
            deckAState = mixerState.deckA.eq,
            deckBState = mixerState.deckB.eq,
            onEQChangeDeckA = { low, mid, high -> mixerEngine.deckA.setEQ(low, mid, high) },
            onEQChangeDeckB = { low, mid, high -> mixerEngine.deckB.setEQ(low, mid, high) }
        )

        // Кроссфейдер + кнопка синхронизации BPM.
        // Crossfader + BPM sync toggle.
        CrossfaderBar(
            position = mixerState.crossfader,
            onPositionChange = { mixerEngine.setCrossfader(it) },
            isSyncEnabled = mixerState.isSyncEnabled,
            onSyncToggle = { mixerEngine.toggleSync() }
        )
    }
}

/** Экран эффектов / The effects screen. */
@Composable
fun EffectsScreen(mixerEngine: MixerEngine) {
    EffectsPanel(mixerEngine = mixerEngine)
}

/** Экран стриминга / The streaming screen. */
@Composable
fun StreamScreen(streamManager: StreamManager) {
    StreamPanel(streamManager = streamManager)
}

/**
 * Экран библиотеки: поиск треков по медиатеке устройства с дебаунсом 300 мс
 * и загрузка найденного трека на свободную деку (сначала A, затем B).
 *
 * The library screen: device-media-library search with a 300 ms debounce,
 * loading a found track onto the free deck (A first, then B).
 */
@Composable
fun LibraryScreen(mixerEngine: MixerEngine) {
    // Локальное состояние экрана: запрос, результаты, индикатор загрузки.
    // Screen-local state: query, results, loading indicator.
    var searchQuery by remember { mutableStateOf("") }
    var tracks by remember { mutableStateOf(emptyList<Track>()) }
    var isLoading by remember { mutableStateOf(false) }
    // ИСПРАВЛЕНО: контекст получаем до remember, потому что LocalContext.current
    // — composable-свойство; его чтение внутри remember запрещено компилятором.
    // FIXED: obtain context before remember because LocalContext.current is a
    // composable read — using it inside remember is a compiler error.
    val context = LocalContext.current.applicationContext
    val trackLoader = remember { TrackLoader(context) }
    val coroutineScope = rememberCoroutineScope()

    // Дебаунс поиска: ждём 300 мс после последнего ввода, затем ищем на IO-потоке.
    // Search debounce: wait 300 ms after the last keystroke, then search on IO.
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
                            // Загружаем на первую свободную деку: A, иначе B.
                            // Load onto the first free deck: A, otherwise B.
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
