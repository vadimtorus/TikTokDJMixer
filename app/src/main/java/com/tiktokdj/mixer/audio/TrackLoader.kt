package com.tiktokdj.mixer.audio

import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import com.tiktokdj.mixer.model.Track

class TrackLoader(private val context: Context) {

    fun loadAllTracks(): List<Track> {
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

        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0 AND " +
                "(${MediaStore.Audio.Media.TITLE} LIKE ? OR ${MediaStore.Audio.Media.ARTIST} LIKE ?)"
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
