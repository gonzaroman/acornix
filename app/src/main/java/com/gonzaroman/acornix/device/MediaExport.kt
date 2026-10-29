package com.gonzaroman.acornix.device

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.annotation.ChecksSdkIntAtLeast
import androidx.annotation.RequiresApi
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** A file an app put in the phone's shared storage, where the gallery and file managers see it. */
data class ExportedFile(val name: String, val uri: Uri, val folder: String)

/**
 * Lets apps hand their creations to the rest of the phone: photos and videos go to the gallery,
 * everything else to Downloads, always inside an "Acornix" folder. It uses MediaStore, so it needs
 * no storage permission (Android 10+); on older versions the share sheet is used instead.
 */
class MediaExport(private val context: Context) {

    @get:ChecksSdkIntAtLeast(api = Build.VERSION_CODES.Q)
    val canSave: Boolean get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q

    @RequiresApi(Build.VERSION_CODES.Q)
    suspend fun save(name: String, bytes: ByteArray): ExportedFile = withContext(Dispatchers.IO) {
        val mime = mimeFor(name)
        val folder = folderFor(mime)
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, mime)
            put(MediaStore.MediaColumns.RELATIVE_PATH, "$folder/$FOLDER")
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val resolver = context.contentResolver
        val uri = resolver.insert(collectionFor(folder), values) ?: error("Could not save the file")
        try {
            resolver.openOutputStream(uri)?.use { it.write(bytes) } ?: error("Could not save the file")
            resolver.update(uri, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)
        } catch (e: Exception) {
            resolver.delete(uri, null, null)
            throw e
        }
        // MediaStore renames the file when the name is taken ("photo (1).jpg").
        val saved = resolver.query(uri, arrayOf(MediaStore.MediaColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        } ?: name
        ExportedFile(saved, uri, "$folder/$FOLDER")
    }

    /** Opens the share sheet with [file] (it must live under filesDir or cacheDir). */
    fun share(file: File): Boolean {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
        val send = Intent(Intent.ACTION_SEND)
            .setType(mimeFor(file.name))
            .putExtra(Intent.EXTRA_STREAM, uri)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        return runCatching {
            context.startActivity(Intent.createChooser(send, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION))
        }.isSuccess
    }

    /** Writes bytes to a fresh file in the cache so they can be shared. */
    suspend fun shareBytes(name: String, bytes: ByteArray): Boolean {
        val file = withContext(Dispatchers.IO) {
            val dir = File(context.cacheDir, "shared").apply { deleteRecursively(); mkdirs() }
            File(dir, name).apply { writeBytes(bytes) }
        }
        return share(file)
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun collectionFor(folder: String): Uri = when (folder) {
        PICTURES -> MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        MOVIES -> MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        MUSIC -> MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        else -> MediaStore.Downloads.EXTERNAL_CONTENT_URI
    }

    companion object {
        const val FOLDER = "Acornix"

        // The values of Environment.DIRECTORY_*, which are not constants in the SDK stubs.
        const val PICTURES = "Pictures"
        const val MOVIES = "Movies"
        const val MUSIC = "Music"
        const val DOWNLOADS = "Download"

        private val TYPES = mapOf(
            "jpg" to "image/jpeg", "jpeg" to "image/jpeg", "png" to "image/png", "webp" to "image/webp",
            "gif" to "image/gif", "svg" to "image/svg+xml", "bmp" to "image/bmp",
            "mp4" to "video/mp4", "webm" to "video/webm", "mov" to "video/quicktime", "3gp" to "video/3gpp",
            "mp3" to "audio/mpeg", "wav" to "audio/wav", "ogg" to "audio/ogg", "m4a" to "audio/mp4", "aac" to "audio/aac",
            "pdf" to "application/pdf", "txt" to "text/plain", "csv" to "text/csv", "json" to "application/json",
            "html" to "text/html", "md" to "text/markdown", "zip" to "application/zip",
        )

        fun mimeFor(name: String): String = TYPES[name.substringAfterLast('.', "").lowercase()] ?: "application/octet-stream"

        /**
         * The shared folder for a type. SVG goes to Downloads: the gallery can't show it.
         */
        fun folderFor(mime: String): String = when {
            mime == "image/svg+xml" -> DOWNLOADS
            mime.startsWith("image/") -> PICTURES
            mime.startsWith("video/") -> MOVIES
            mime.startsWith("audio/") -> MUSIC
            else -> DOWNLOADS
        }
    }
}
