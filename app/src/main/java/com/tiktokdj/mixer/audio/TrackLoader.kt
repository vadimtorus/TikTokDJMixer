package com.tiktokdj.mixer.audio

import android.content.ContentResolver
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import com.tiktokdj.mixer.model.Track
import java.util.UUID

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
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.DATA,
            MediaStore.Audio.Media.DISPLAY_NAME,
            MediaStore.Audio.Media.MIME_TYPE
        )

        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0"
        val sortOrder = "${MediaStore.Audio.Media.TITLE} ASC"

        var cursor: Cursor? = null

        try {
            cursor = context.contentResolver.query(
                collection,
                projection,
                selection,
                null,
                sortOrder
            )

            cursor?.let {
                val idColumn = it.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                val titleColumn = it.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                val artistColumn = it.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                val durationColumn = it.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
                val dataColumn = it.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)

                while (it.moveToNext()) {
                    val id = it.getLong(idColumn)
                    val title = it.getString(titleColumn) ?: "Unknown"
                    val artist = it.getString(artistColumn) ?: "Unknown"
                    val duration = it.getLong(durationColumn)
                    val path = it.getString(dataColumn) ?: ""

                    if (duration > 3000) { // Skip very short files
                        val contentUri = Uri.withAppendedPath(
                            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                            id.toString()
                        )

                        tracks.add(
                            Track(
                                id = UUID.randomUUID().toString(),
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
        return loadAllTracks().filter { track ->
            track.title.contains(query, ignoreCase = true) ||
                    track.artist.contains(query, ignoreCase = true)
        }
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
            cursor?..moveToFirst()

            val id = cursor?.getLong(0) ?: return null
            val title = cursor?.getString(1) ?: "Unknown"
            val artist = cursor?.getString(2) ?: "Unknown"
            val duration = cursor?.getLong(3) ?: 0

            Track(
                id = UUID.randomUUID().toString(),
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
