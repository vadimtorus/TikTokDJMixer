package com.tiktokdj.mixer.audio

import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import com.tiktokdj.mixer.model.Track

/**
 * Загрузчик аудиотреков из MediaStore.
 * Audio track loader from MediaStore.
 *
 * Отвечает за чтение всей музыкальной библиотеки устройства, поиск по названию/исполнителю
 * и получение метаданных одного трека по URI.
 *
 * Responsible for reading the whole device music library, searching by title/artist,
 * and fetching metadata of a single track by URI.
 */
class TrackLoader(private val context: Context) {

    /**
     * Загружает все музыкальные треки длительностью более 3 секунд.
     * Loads all music tracks longer than 3 seconds.
     */
    fun loadAllTracks(): List<Track> {
        val tracks = mutableListOf<Track>()

        // На Android 10+ используем общий том внешнего хранилища, иначе — старый URI.
        // On Android 10+ use the shared external volume, otherwise the legacy URI.
        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        }

        // Запрашиваем только нужные колонки: id, название, исполнитель, длительность.
        // Query only the needed columns: id, title, artist, duration.
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.DURATION
        )

        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0"
        val sortOrder = "${MediaStore.Audio.Media.TITLE} ASC"

        var cursor: Cursor? = null

        try {
            cursor = context.contentResolver.query(
                collection, projection, selection, null, sortOrder
            )

            cursor?.let {
                val idColumn = it.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                val titleColumn = it.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                val artistColumn = it.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                val durationColumn = it.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)

                while (it.moveToNext()) {
                    val id = it.getLong(idColumn)
                    val title = it.getString(titleColumn) ?: "Unknown"
                    val artist = it.getString(artistColumn) ?: "Unknown"
                    val duration = it.getLong(durationColumn)

                    // Фильтруем слишком короткие файлы (джинглы, звуки < 3 c).
                    // Filter out very short files (jingles, sounds < 3 s).
                    if (duration > 3000) {
                        val contentUri = Uri.withAppendedPath(
                            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id.toString()
                        )

                        tracks.add(
                            Track(
                                id = id.toString(),
                                title = title,
                                artist = artist,
                                uri = contentUri.toString(),
                                durationMs = duration
                            )
                        )
                    }
                }
            }
        } finally {
            // Курсор обязательно закрываем, чтобы не утечь память.
            // Always close the cursor to avoid memory leaks.
            cursor?.close()
        }

        return tracks
    }

    /**
     * Ищет треки по подстроке в названии или имени исполнителя.
     * Searches tracks by substring in title or artist name.
     *
     * Пустой запрос возвращает всю библиотеку / A blank query returns the whole library.
     */
    fun searchTracks(query: String): List<Track> {
        if (query.isBlank()) return loadAllTracks()

        val tracks = mutableListOf<Track>()

        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        }

        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.DURATION
        )

        // ИСПРАВЛЕНО: к каждому LIKE добавлено `ESCAPE '\'`. Без этого предложения
        // экранирование `%` и `_` через обратный слэш не работало: SQL воспринимал `\%`
        // как обычные символы «\» и «%», и спецсимволы в запросе срабатывали как шаблоны.
        //
        // FIXED: an `ESCAPE '\'` clause has been appended to each LIKE. Without it the
        // backslash escaping of `%` and `_` had no effect: SQL treated `\%` as literal
        // characters "\" and "%", so wildcards in user input were still interpreted.
        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0 AND " +
                "(${MediaStore.Audio.Media.TITLE} LIKE ? ESCAPE '\\' OR " +
                "${MediaStore.Audio.Media.ARTIST} LIKE ? ESCAPE '\\')"

        // Экранируем спецсимволы LIKE в пользовательском вводе.
        // Escape LIKE special characters in user input.
        val escapedQuery = query.replace("%", "\\%").replace("_", "\\_")
        val selectionArgs = arrayOf("%$escapedQuery%", "%$escapedQuery%")
        val sortOrder = "${MediaStore.Audio.Media.TITLE} ASC"

        var cursor: Cursor? = null

        try {
            cursor = context.contentResolver.query(
                collection, projection, selection, selectionArgs, sortOrder
            )

            cursor?.let {
                val idColumn = it.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                val titleColumn = it.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                val artistColumn = it.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                val durationColumn = it.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)

                while (it.moveToNext()) {
                    val id = it.getLong(idColumn)
                    val title = it.getString(titleColumn) ?: "Unknown"
                    val artist = it.getString(artistColumn) ?: "Unknown"
                    val duration = it.getLong(durationColumn)

                    if (duration > 3000) {
                        val contentUri = Uri.withAppendedPath(
                            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id.toString()
                        )

                        tracks.add(
                            Track(
                                id = id.toString(),
                                title = title,
                                artist = artist,
                                uri = contentUri.toString(),
                                durationMs = duration
                            )
                        )
                    }
                }
            }
        } finally {
            cursor?.close()
        }

        return tracks
    }

    /**
     * Возвращает трек по его content-URI или null, если запись не найдена.
     * Returns a track by its content URI, or null when no row is found.
     */
    fun getTrackByUri(uri: Uri): Track? {
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.DURATION
        )

        var cursor: Cursor? = null
        return try {
            cursor = context.contentResolver.query(uri, projection, null, null, null)
            if (cursor?.moveToFirst() != true) return null

            val id = cursor?.getLong(0) ?: return null
            val title = cursor?.getString(1) ?: "Unknown"
            val artist = cursor?.getString(2) ?: "Unknown"
            val duration = cursor?.getLong(3) ?: 0

            Track(
                id = id.toString(),
                title = title,
                artist = artist,
                uri = uri.toString(),
                durationMs = duration
            )
        } finally {
            cursor?.close()
        }
    }
}
