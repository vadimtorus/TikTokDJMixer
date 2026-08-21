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

// ============================================================
// СЕКЦИЯ: Обновление приложения / SECTION: App updating
// Проверка новых релизов на GitHub, загрузка APK через
// системный DownloadManager и запуск установки.
// Checking for new releases on GitHub, downloading the APK via
// the system DownloadManager and launching installation.
// ============================================================

/**
 * Менеджер обновлений приложения — проверяет наличие новых версий
 * в релизах GitHub-репозитория, скачивает APK и предлагает установку.
 * Application update manager - checks for new versions in GitHub releases,
 * downloads the APK and offers installation.
 *
 * Жизненный цикл: startPeriodicCheck -> checkForUpdates -> downloadUpdate ->
 * (BroadcastReceiver о завершении) -> installUpdate.
 * Lifecycle: startPeriodicCheck -> checkForUpdates -> downloadUpdate ->
 * (broadcast receiver on completion) -> installUpdate.
 *
 * @param context Контекст приложения для доступа к DownloadManager и PackageManager.
 *                Application context for accessing DownloadManager and PackageManager.
 */
class AppUpdater(private val context: Context) {

    companion object {
        private const val TAG = "AppUpdater"
        // Базовый URL GitHub REST API / Base URL of the GitHub REST API
        private const val GITHUB_API_URL = "https://api.github.com/repos"
        // Интервал фоновой проверки обновлений — 6 часов / Background update check interval - 6 hours
        private const val CHECK_INTERVAL_MS = 6 * 60 * 60 * 1000L
    }

    // Внутреннее изменяемое состояние обновления / Internal mutable update state
    private val _updateState = MutableStateFlow<UpdateState>(UpdateState.Idle)
    // Публичное состояние только для чтения / Public read-only state
    val updateState: StateFlow<UpdateState> = _updateState.asStateFlow()

    private var checkJob: Job? = null
    private var downloadId: Long = -1
    // Отдельная корутинная область для сетевых операций / Dedicated coroutine scope for network operations
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var receiverRegistered = false

    /**
     * Конечный автомат состояний процесса обновления.
     * Finite state machine of the update process.
     */
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

    /**
     * Приёмник широковещательного сообщения о завершении загрузки.
     * Broadcast receiver notified when the download completes.
     *
     * При успешном завершении переводит состояние в ReadyToInstall
     * и сразу запускает установку APK.
     * On successful completion it transitions to ReadyToInstall
     * and immediately launches the APK installation.
     */
    private val downloadReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context, intent: Intent) {
            if (intent.action == DownloadManager.ACTION_DOWNLOAD_COMPLETE) {
                // Фильтруем: реагируем только на свою загрузку
                // Filter: react only to our own download
                val id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1)
                if (id == downloadId) {
                    _updateState.value = UpdateState.ReadyToInstall
                    installUpdate()
                }
            }
        }
    }

    /**
     * Запускает периодическую проверку обновлений каждые 6 часов.
     * Starts periodic update checks every 6 hours.
     *
     * Также регистрирует приёмник завершения загрузки (однократно).
     * Also registers the download completion receiver (once).
     *
     * @param owner Владелец GitHub-репозитория. GitHub repository owner.
     * @param repo Имя GitHub-репозитория. GitHub repository name.
     */
    fun startPeriodicCheck(owner: String, repo: String) {
        // Регистрация приёмника выполняется только один раз
        // Receiver registration is performed only once
        if (!receiverRegistered) {
            val filter = IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE)
            context.registerReceiver(downloadReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
            receiverRegistered = true
        }

        // Отменяем предыдущую проверку и запускаем бесконечный цикл с задержкой
        // Cancel any previous check and launch an endless loop with a delay
        checkJob?.cancel()
        checkJob = scope.launch {
            while (isActive) {
                checkForUpdates(owner, repo)
                delay(CHECK_INTERVAL_MS)
            }
        }
    }

    /**
     * Одноразовая проверка последнего релиза на GitHub.
     * One-shot check of the latest release on GitHub.
     *
     * @param owner Владелец репозитория. Repository owner.
     * @param repo Имя репозитория. Repository name.
     * @return Информация об обновлении или null, если обновления нет/произошла ошибка.
     *         Update info or null if there is no update/an error occurred.
     */
    suspend fun checkForUpdates(owner: String, repo: String): UpdateInfo? {
        var connection: HttpURLConnection? = null
        return try {
            _updateState.value = UpdateState.Checking

            // Запрос endpoint'а «последний релиз» с указанием версии API
            // Requesting the "latest release" endpoint with an explicit API version
            val url = URL("$GITHUB_API_URL/$owner/$repo/releases/latest")
            connection = url.openConnection() as HttpURLConnection
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
        } finally {
            connection?.disconnect()
        }
    }

    /**
     * Разбирает JSON-ответ GitHub Releases и сравнивает версии.
     * Parses the GitHub Releases JSON response and compares versions.
     *
     * Ищет первый ассет с расширением .apk и формирует UpdateInfo,
     * если найденная версия новее текущей.
     * Looks for the first asset with the .apk extension and builds UpdateInfo
     * if the found version is newer than the current one.
     *
     * @param response Сырой JSON-ответ от GitHub API. Raw JSON response from the GitHub API.
     * @return UpdateInfo при наличии новой версии, иначе null. UpdateInfo if a new version exists, otherwise null.
     */
    private fun parseReleaseResponse(response: String): UpdateInfo? {
        return try {
            val json = JSONObject(response)
            // Удаляем префикс 'v'/'V' из имени тега перед парсингом версии
            // Strip the 'v'/'V' prefix from the tag name before parsing the version
            val tagName = json.getString("tag_name").trimStart('v', 'V')
            val version = AppVersion.parse(tagName)
            val currentVersion = getCurrentVersion()

            if (version.isNewerThan(currentVersion)) {
                val body = json.optString("body", "")
                val assets = json.getJSONArray("assets")
                var downloadUrl = ""

                // Перебираем ассеты в поисках первого APK-файла
                // Iterate over assets looking for the first APK file
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
                    // Новый тег есть, но APK не опубликован — считаем актуальной версией
                    // A new tag exists but no APK was published - treat as up to date
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

    /**
     * Ставит APK в очередь системного DownloadManager и запускает
     * фоновый опрос прогресса загрузки.
     * Enqueues the APK in the system DownloadManager and starts
     * background polling of the download progress.
     *
     * @param downloadUrl Прямая ссылка на APK-файл. Direct URL to the APK file.
     */
    fun downloadUpdate(downloadUrl: String) {
        _updateState.value = UpdateState.Downloading

        // Настройка запроса: уведомление, папка Downloads, разрешён мобильный интернет и роуминг
        // Request setup: notification, Downloads folder, metered networks and roaming allowed
        val request = DownloadManager.Request(Uri.parse(downloadUrl))
            .setTitle("Downloading update")
            .setDescription("Downloading new app version")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, "TikTokDJMixer-update.apk")
            .setAllowedOverMetered(true)
            .setAllowedOverRoaming(true)

        val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        downloadId = downloadManager.enqueue(request)

        // Цикл опроса статуса каждые 500 мс для публикации процента выполнения
        // Status polling loop every 500 ms to publish the progress percentage
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

                        // Публикуем прогресс только если размер файла известен
                        // Publish progress only when the total size is known
                        if (totalBytes > 0) {
                            val progress = ((bytesDownloaded * 100) / totalBytes).toInt()
                            _updateState.value = UpdateState.DownloadProgress(progress)
                        }

                        // Завершаем опрос при успехе или неудаче
                        // Stop polling on success or failure
                        if (status == DownloadManager.STATUS_SUCCESSFUL || status == DownloadManager.STATUS_FAILED) {
                            downloading = false
                        }
                    }
                }
                delay(500)
            }
        }
    }

    /**
     * Запускает системное окно установки скачанного APK.
     * Launches the system installer dialog for the downloaded APK.
     */
    private fun installUpdate() {
        try {
            val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            val uri = downloadManager.getUriForDownloadedFile(downloadId) ?: run {
                _updateState.value = UpdateState.Error("Download URI is null")
                return
            }

            // Intent ACTION_VIEW с MIME-типом package-archive открывает установщик Android
            // An ACTION_VIEW intent with the package-archive MIME type opens the Android installer
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

    /**
     * Возвращает версию текущей установленной сборки приложения.
     * Returns the version of the currently installed app build.
     *
     * @return Разобранная версия или 0.0.0 при ошибке чтения. Parsed version or 0.0.0 on read failure.
     */
    fun getCurrentVersion(): AppVersion {
        return try {
            val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            AppVersion.parse(packageInfo.versionName ?: "0.0.0")
        } catch (e: Exception) {
            AppVersion(0, 0, 0)
        }
    }

    /**
     * Освобождает ресурсы: отменяет периодическую проверку,
     * разрегистрирует приёмник и отменяет все корутины.
     * Releases resources: cancels periodic checks,
     * unregisters the receiver and cancels all coroutines.
     */
    fun cleanup() {
        checkJob?.cancel()
        if (receiverRegistered) {
            try {
                context.unregisterReceiver(downloadReceiver)
            } catch (e: Exception) {
                // Приёмник уже разрегистрирован / Receiver already unregistered
            }
            receiverRegistered = false
        }
        scope.cancel()
    }
}
