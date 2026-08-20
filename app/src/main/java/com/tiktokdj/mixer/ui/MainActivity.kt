package com.tiktokdj.mixer.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.tiktokdj.mixer.engine.MixerEngine
import com.tiktokdj.mixer.streaming.StreamManager
import com.tiktokdj.mixer.ui.theme.TikTokDJTheme
import com.tiktokdj.mixer.updater.AppUpdater

class MainActivity : ComponentActivity() {

    lateinit var mixerEngine: MixerEngine
    lateinit var streamManager: StreamManager
    lateinit var appUpdater: AppUpdater

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        mixerEngine = MixerEngine(applicationContext)
        mixerEngine.initialize()

        streamManager = StreamManager(applicationContext)
        appUpdater = AppUpdater(applicationContext)

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

    override fun onDestroy() {
        mixerEngine.release()
        streamManager.cleanup()
        appUpdater.cleanup()
        super.onDestroy()
    }
}
