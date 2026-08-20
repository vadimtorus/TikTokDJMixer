package com.tiktokdj.mixer.updater

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.database.Cursor
import android.net.Uri
import android.os.Environment
import android.util.Log
import com.tiktokdj.mixer.model.AppVersion
import com.tiktokdj.mixer.model.UpdateInfo
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

class AppUpdater(private val context: Context) {

    companion object {
        private const val TAG = "AppUpdater"
        private const val GITHUB_API_URL = "https://api.github.com/repos"
        private const val CHECK_INTERVAL_MS = 6 * 60 * 60 * 1000L
    }

    private val _updateState = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val updateState: StateFlow<UpdateState> = _updateState.asStateFlow()

    private var checkJob: Job? = null
    private var downloadId: Long = -1
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var receiverRegistered = false

    sealed class UpdateState {
        data object Idle : UpdateState()
        data object Checking : UpdateState()
        data class UpdateAvailable(val updateInfo: UpdateInfo) : UpdateState()
        data object Downloading : UpdateState()
        data class DownloadProgress(val progress: Int) : UpdateState()
        data object ReadyToInstall : UpdateState()
        data class Error(val message: String) : UpdateState()
        data object UpToDate : UpdateState()
    }

    private val downloadReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context, intent: Intent) {
            if (intent.action == DownloadManager.ACTION_DOWNLOAD_COMPLETE) {
                val id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1)
                if (id == downloadId) {
                    _updateState.value = UpdateState.ReadyToInstall
                    installUpdate()
                }
            }
        }
    }

    fun startPeriodicCheck(owner: String, repo: String) {
        if (!receiverRegistered) {
            val filter = IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE)
            context.registerReceiver(downloadReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
            receiverRegistered = true
        }

        checkJob?.cancel()
        checkJob = scope.launch {
            while (isActive) {
                checkForUpdates(owner, repo)
                delay(CHECK_INTERVAL_MS)
            }
        }
    }

    suspend fun checkForUpdates(owner: String, repo: String): UpdateInfo? {
        return try {
            _updateState.value = UpdateState.Checking

            val url = URL("$GITHUB_API_URL/$owner/$repo/releases/latest")
            val connection = url.openConnection() as HttpURLConnection
            connection.setRequestProperty("Accept", "application/vnd.github.v3+json")
            connection.connectTimeout = 10000
            connection.readTimeout = 10000

            if (connection.responseCode == 200) {
                val response = BufferedReader(InputStreamReader(connection.inputStream)).use { it.readText() }
                parseReleaseResponse(response)
            } else {
                _updateState.value = UpdateState.Error("HTTP ${connection.responseCode}")
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Check for updates error", e)
            _updateState.value = UpdateState.Error("Check failed: ${e.message}")
            null
        }
    }

    private fun parseReleaseResponse(response: String): UpdateInfo? {
        return try {
            val json = JSONObject(response)
            val tagName = json.getString("tag_name").trimStart('v', 'V')
            val version = AppVersion.parse(tagName)
            val currentVersion = getCurrentVersion()

            if (version.isNewerThan(currentVersion)) {
                val body = json.optString("body", "")
                val assets = json.getJSONArray("assets")
                var downloadUrl = ""

                for (i in 0 until assets.length()) {
                    val asset = assets.getJSONObject(i)
                    val name = asset.getString("name")
                    if (name.endsWith(".apk")) {
                        downloadUrl = asset.getString("browser_download_url")
                        break
                    }
                }

                if (downloadUrl.isNotEmpty()) {
                    val updateInfo = UpdateInfo(
                        version = version,
                        downloadUrl = downloadUrl,
                        changelog = body,
                        isRequired = false,
                        publishedAt = System.currentTimeMillis()
                    )
                    _updateState.value = UpdateState.UpdateAvailable(updateInfo)
                    updateInfo
                } else {
                    _updateState.value = UpdateState.UpToDate
                    null
                }
            } else {
                _updateState.value = UpdateState.UpToDate
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Parse release error", e)
            _updateState.value = UpdateState.Error("Parse error: ${e.message}")
            null
        }
    }

    fun downloadUpdate(downloadUrl: String) {
        _updateState.value = UpdateState.Downloading

        val request = DownloadManager.Request(Uri.parse(downloadUrl))
            .setTitle("Downloading update")
            .setDescription("Downloading new app version")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, "TikTokDJMixer-update.apk")
            .setAllowedOverMetered(true)
            .setAllowedOverRoaming(true)

        val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        downloadId = downloadManager.enqueue(request)

        scope.launch {
            var downloading = true
            while (downloading && isActive) {
                val query = DownloadManager.Query().setFilterById(downloadId)
                val cursor: Cursor? = downloadManager.query(query)

                cursor?.use {
                    if (it.moveToFirst()) {
                        val status = it.getInt(it.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
                        val bytesDownloaded = it.getLong(it.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
                        val totalBytes = it.getLong(it.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES))

                        if (totalBytes > 0) {
                            val progress = ((bytesDownloaded * 100) / totalBytes).toInt()
                            _updateState.value = UpdateState.DownloadProgress(progress)
                        }

                        if (status == DownloadManager.STATUS_SUCCESSFUL || status == DownloadManager.STATUS_FAILED) {
                            downloading = false
                        }
                    }
                }
                delay(500)
            }
        }
    }

    private fun installUpdate() {
        try {
            val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            val uri = downloadManager.getUriForDownloadedFile(downloadId)

            val installIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(installIntent)
        } catch (e: Exception) {
            Log.e(TAG, "Install error", e)
            _updateState.value = UpdateState.Error("Installation failed: ${e.message}")
        }
    }

    fun getCurrentVersion(): AppVersion {
        return try {
            val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            AppVersion.parse(packageInfo.versionName ?: "0.0.0")
        } catch (e: Exception) {
            AppVersion(0, 0, 0)
        }
    }

    fun cleanup() {
        checkJob?.cancel()
        if (receiverRegistered) {
            try {
                context.unregisterReceiver(downloadReceiver)
            } catch (e: Exception) {
                // Already unregistered
            }
            receiverRegistered = false
        }
        scope.cancel()
    }
}
