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

class MainActivity : ComponentActivity() {

    lateinit var mixerEngine: MixerEngine
    lateinit var streamManager: StreamManager
    lateinit var appUpdater: AppUpdater

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

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val perms = requiredPermissions
        if (perms.isNotEmpty()) {
            permissionLauncher.launch(perms)
        }

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
