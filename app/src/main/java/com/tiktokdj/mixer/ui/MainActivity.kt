package com.tiktokdj.mixer.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import com.tiktokdj.mixer.engine.MixerEngine
import com.tiktokdj.mixer.streaming.StreamManager
import com.tiktokdj.mixer.ui.theme.TikTokDJTheme
import com.tiktokdj.mixer.updater.AppUpdater

/**
 * Главная активность приложения: точка сборки всех подсистем.
 *
 * Создаёт и связывает:
 * - [MixerEngine] — аудио-ядро (деки, кроссфейдер, эффекты, спектр);
 * - [StreamManager] — захват микрофона и стриминг в TikTok/RTMP;
 * - [AppUpdater] — периодическая проверка обновлений на GitHub;
 * - Compose UI ([DJMixerApp]) поверх всего этого.
 *
 * The app's main activity: the assembly point of all subsystems.
 *
 * Creates and wires together:
 * - [MixerEngine] — the audio core (decks, crossfader, effects, spectrum);
 * - [StreamManager] — microphone capture and TikTok/RTMP streaming;
 * - [AppUpdater] — periodic GitHub update checks;
 * - the Compose UI ([DJMixerApp]) on top of it all.
 */
class MainActivity : ComponentActivity() {

    /** Аудио-ядро микшера / Mixer audio core. Инициализируется в [onCreate]. */
    lateinit var mixerEngine: MixerEngine

    /** Менеджер стриминга / Streaming manager. Инициализируется в [onCreate]. */
    lateinit var streamManager: StreamManager

    /** Менеджер автообновления / Auto-update manager. Инициализируется в [onCreate]. */
    lateinit var appUpdater: AppUpdater

    /**
     * Список ещё не выданных разрешений, необходимых приложению:
     * микрофон (захват звука) и, начиная с Android 13, уведомления
     * (для foreground-сервиса стрима и уведомлений об обновлениях).
     *
     * List of not-yet-granted permissions the app needs:
     * microphone (audio capture) and, from Android 13 on, notifications
     * (for the streaming foreground service and update notifications).
     */
    private val requiredPermissions: Array<String>
        get() {
            val perms = mutableListOf<String>()
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED
            ) {
                perms.add(Manifest.permission.RECORD_AUDIO)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED
                ) {
                    perms.add(Manifest.permission.POST_NOTIFICATIONS)
                }
            }
            return perms.toTypedArray()
        }

    /**
     * Лончер запроса разрешений; результат не обрабатываем —
     * функции деградируют мягко, если доступ не выдан.
     *
     * Permission-request launcher; the result is ignored —
     * features degrade gracefully when access is denied.
     */
    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Запрашиваем недостающие разрешения до создания подсистем.
        // Request missing permissions before creating the subsystems.
        val perms = requiredPermissions
        if (perms.isNotEmpty()) {
            permissionLauncher.launch(perms)
        }

        // Ядро микшера: деки A/B, кроссфейдер, эффекты, спектральный анализ.
        // Mixer core: decks A/B, crossfader, effects, spectral analysis.
        mixerEngine = MixerEngine(applicationContext)
        mixerEngine.initialize()

        // Менеджер стриминга: захват микрофона и доставка в TikTok Live / RTMP.
        // Streaming manager: mic capture and delivery to TikTok Live / RTMP.
        streamManager = StreamManager(applicationContext)

        // СВЯЗЫВАНИЕ ПОДСИСТЕМ: передаём стримингу процессор эффектов движка,
        // чтобы активные эффекты применялись к захваченному звуку перед отправкой
        // в эфир, и его же спектральный анализатор — для визуализации спектра.
        //
        // SUBSYSTEM WIRING: hand the engine's effects processor to the streamer so
        // active effects are applied to captured audio before going on air, plus
        // its spectral analyzer for spectrum visualization.
        streamManager.effectsProcessor = mixerEngine.effectsProcessor
        streamManager.spectralAnalyzer = mixerEngine.spectralAnalyzer

        // Менеджер автообновления + запуск периодической проверки релизов
        // GitHub-репозитория (каждые 6 часов, см. AppUpdater.CHECK_INTERVAL_MS).
        //
        // Auto-update manager + start of periodic checks of the GitHub repo's
        // releases (every 6 hours, see AppUpdater.CHECK_INTERVAL_MS).
        appUpdater = AppUpdater(applicationContext)
        appUpdater.startPeriodicCheck("vadimtorus", "TikTokDJMixer")

        setContent {
            TikTokDJTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    DJMixerApp(
                        mixerEngine = mixerEngine,
                        streamManager = streamManager,
                        appUpdater = appUpdater
                    )
                }
            }
        }
    }

    /**
     * Освобождает все подсистемы в обратном порядке создания.
     * Releases all subsystems in reverse creation order.
     */
    override fun onDestroy() {
        mixerEngine.release()
        streamManager.cleanup()
        appUpdater.cleanup()
        super.onDestroy()
    }
}
