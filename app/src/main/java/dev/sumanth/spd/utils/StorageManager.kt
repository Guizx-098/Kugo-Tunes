package dev.sumanth.spd.utils

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.media.MediaScannerConnection
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.documentfile.provider.DocumentFile
import dev.sumanth.spd.R
import dev.sumanth.spd.model.Platform
import java.io.File
import java.io.IOException

/** Name of the folder of each source inside the download folder. Adding a platform = one more entry here. */
object PlatformFolders {
    const val SPOTIFY = "Spotify"
    const val YOUTUBE = "YouTube"
    const val YOUTUBE_MUSIC = "YouTube Music"
    const val FACEBOOK = "Facebook"
    const val INSTAGRAM = "Instagram"
    const val TIKTOK = "TikTok"
    const val KWAI = "Kwai"
    const val OTHER = "Outras plataformas"

    fun of(platform: Platform?, youtubeMusic: Boolean): String = when (platform) {
        Platform.SPOTIFY -> SPOTIFY
        Platform.YOUTUBE -> if (youtubeMusic) YOUTUBE_MUSIC else YOUTUBE
        Platform.FACEBOOK -> FACEBOOK
        Platform.INSTAGRAM -> INSTAGRAM
        Platform.TIKTOK -> TIKTOK
        Platform.KWAI -> KWAI
        else -> OTHER
    }
}

/**
 * Single place that saves finished files.
 *  - Folder chosen by the user (Storage Access Framework): <chosen folder>/<platform>/[playlist]/file
 *  - No folder chosen: Download/Kugo Tunes/<platform>/[playlist]/file
 *      Android 10+: MediaStore.Downloads (no permission needed); Android 7-9: public Downloads folder.
 * Existing files are never replaced: a name that is taken gets a " (1)" suffix.
 */
object StorageManager {
    const val ROOT = "Kugo Tunes"
    private const val BUFFER = 64 * 1024

    /** Best effort at launch. On Android 10+ the system creates Download/Kugo Tunes when the first file is saved. */
    fun prepareDefaultFolder() {
        try {
            val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), ROOT)
            if (!dir.exists()) dir.mkdirs()
        } catch (_: Exception) {
        }
    }

    fun mimeFor(extension: String): String = when (extension.lowercase()) {
        "mp3" -> "audio/mpeg"
        "m4a" -> "audio/mp4"
        "mp4" -> "video/mp4"
        "webm" -> "video/webm"
        "mkv" -> "video/x-matroska"
        else -> "application/octet-stream"
    }

    fun save(
        context: Context,
        file: File,
        customTreeUri: String?,
        platformFolder: String,
        playlistName: String?,
        playlistFolder: Boolean,
        fileName: String,
        extension: String
    ) {
        val playlist = playlistName?.takeIf { playlistFolder && it.isNotBlank() }?.let { DownloadManager.sanitizeFilename(it) }
        val folder = DownloadManager.sanitizeFilename(platformFolder)
        try {
            when {
                !customTreeUri.isNullOrBlank() -> saveToTree(context, file, customTreeUri, folder, playlist, fileName, extension)
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q -> saveToMediaStore(context, file, folder, playlist, fileName, extension)
                else -> saveLegacy(context, file, folder, playlist, fileName, extension)
            }
        } catch (e: LocalizedException) {
            throw e
        } catch (e: SecurityException) {
            throw LocalizedException(R.string.err_storage_permission, fallback = "No permission to write in this folder.")
        } catch (e: IOException) {
            val text = (e.message ?: "").lowercase()
            if (text.contains("enospc") || text.contains("no space")) {
                throw LocalizedException(R.string.err_no_space, fallback = "Not enough storage space.")
            }
            throw e
        }
    }

    /** Is this file already in its folder? Used when a playlist is resumed after the app was closed. */
    fun exists(
        context: Context,
        customTreeUri: String?,
        platformFolder: String,
        playlistName: String?,
        fileName: String,
        extensions: List<String>
    ): Boolean {
        val folder = DownloadManager.sanitizeFilename(platformFolder)
        val playlist = playlistName?.takeIf { it.isNotBlank() }?.let { DownloadManager.sanitizeFilename(it) }
        return when {
            !customTreeUri.isNullOrBlank() -> {
                var dir = DocumentFile.fromTreeUri(context, customTreeUri.toUri()) ?: return false
                dir = dir.findFile(folder)?.takeIf { it.isDirectory } ?: return false
                if (playlist != null) dir = dir.findFile(playlist)?.takeIf { it.isDirectory } ?: return false
                extensions.any { dir.findFile("$fileName.$it") != null }
            }
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q -> existsInMediaStore(context, folder, playlist, fileName, extensions)
            else -> {
                var dir = File(File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), ROOT), folder)
                if (playlist != null) dir = File(dir, playlist)
                extensions.any { File(dir, "$fileName.$it").exists() }
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun existsInMediaStore(context: Context, folder: String, playlist: String?, fileName: String, extensions: List<String>): Boolean {
        val relative = buildString {
            append(Environment.DIRECTORY_DOWNLOADS).append('/').append(ROOT).append('/').append(folder)
            if (playlist != null) append('/').append(playlist)
            append('/')
        }
        val names = extensions.map { "$fileName.$it" }
        val selection = MediaStore.MediaColumns.RELATIVE_PATH + " = ? AND " + MediaStore.MediaColumns.DISPLAY_NAME +
            " IN (" + names.joinToString(",") { "?" } + ")"
        val args = arrayOf(relative) + names.toTypedArray()
        context.contentResolver.query(
            MediaStore.Downloads.EXTERNAL_CONTENT_URI, arrayOf(MediaStore.MediaColumns._ID), selection, args, null
        )?.use { return it.count > 0 }
        return false
    }

    private fun saveToTree(
        context: Context, file: File, treeUri: String, folder: String, playlist: String?, fileName: String, extension: String
    ) {
        var dir = DocumentFile.fromTreeUri(context, treeUri.toUri())
        if (dir == null || !dir.exists() || !dir.canWrite()) {
            throw LocalizedException(R.string.err_folder_inaccessible, fallback = "The chosen folder is not accessible.")
        }
        dir = child(dir, folder)
        if (playlist != null) dir = child(dir, playlist)

        // createFile gives a new name ("name (1).ext") when the name is taken: nothing is replaced
        val mime = if (extension.lowercase() in listOf("mp3", "m4a")) "audio/*" else mimeFor(extension)
        val target = dir.createFile(mime, "$fileName.$extension")
            ?: throw LocalizedException(R.string.err_folder_inaccessible, fallback = "Could not create the file in the chosen folder.")
        val output = context.contentResolver.openOutputStream(target.uri)
            ?: throw IOException("Failed to open output file")
        try {
            output.use { out -> file.inputStream().use { it.copyTo(out, BUFFER) } }
        } catch (e: IOException) {
            target.delete()
            throw e
        }
    }

    private fun child(parent: DocumentFile, name: String): DocumentFile {
        parent.findFile(name)?.let { if (it.isDirectory) return it }
        return parent.createDirectory(name)
            ?: throw LocalizedException(
                R.string.err_cannot_create_folder, listOf(name),
                "Android does not allow creating the folder $name inside the chosen folder."
            )
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun saveToMediaStore(context: Context, file: File, folder: String, playlist: String?, fileName: String, extension: String) {
        val relative = buildString {
            append(Environment.DIRECTORY_DOWNLOADS).append('/').append(ROOT).append('/').append(folder)
            if (playlist != null) append('/').append(playlist)
        }
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, "$fileName.$extension")
            put(MediaStore.MediaColumns.MIME_TYPE, mimeFor(extension))
            put(MediaStore.MediaColumns.RELATIVE_PATH, relative)
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val resolver = context.contentResolver
        // The system picks a free name when "<name>.<ext>" already exists in that folder
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            ?: throw IOException("Could not create the file in Download/$ROOT")
        try {
            val output = resolver.openOutputStream(uri) ?: throw IOException("Failed to open output file")
            output.use { out -> file.inputStream().use { it.copyTo(out, BUFFER) } }
            val done = ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }
            resolver.update(uri, done, null, null)
        } catch (e: Exception) {
            runCatching { resolver.delete(uri, null, null) }
            throw e
        }
    }

    @Suppress("DEPRECATION")
    private fun saveLegacy(context: Context, file: File, folder: String, playlist: String?, fileName: String, extension: String) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            throw LocalizedException(R.string.err_storage_permission, fallback = "No permission to write in the Downloads folder.")
        }
        var dir = File(File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), ROOT), folder)
        if (playlist != null) dir = File(dir, playlist)
        if (!dir.exists() && !dir.mkdirs()) {
            throw LocalizedException(
                R.string.err_cannot_create_folder, listOf(folder), "Could not create the folder $folder."
            )
        }
        var target = File(dir, "$fileName.$extension")
        var n = 1
        while (target.exists()) {
            target = File(dir, "$fileName ($n).$extension")
            n++
        }
        try {
            file.inputStream().use { input -> target.outputStream().use { input.copyTo(it, BUFFER) } }
        } catch (e: IOException) {
            target.delete()
            throw e
        }
        MediaScannerConnection.scanFile(context, arrayOf(target.absolutePath), null, null)
    }
}
