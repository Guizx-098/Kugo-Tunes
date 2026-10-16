package dev.sumanth.spd.utils

import android.content.Context
import androidx.documentfile.provider.DocumentFile
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.FFmpegKitConfig
import com.arthenica.ffmpegkit.Level
import com.arthenica.ffmpegkit.ReturnCode
import dev.sumanth.spd.model.DownloadStatus
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import org.schabi.newpipe.extractor.ServiceList.YouTube
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request
import org.schabi.newpipe.extractor.downloader.Response
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException
import org.schabi.newpipe.extractor.services.youtube.extractors.YoutubeMusicSearchExtractor
import org.schabi.newpipe.extractor.services.youtube.linkHandler.YoutubeSearchQueryHandlerFactory
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.UUID
import java.util.concurrent.TimeUnit
import androidx.core.net.toUri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.CancellationException

data class FileMeta(val url: String, val name: String, val extention: String)

object DownloadManager {
    private const val BUFFER_SIZE = 64 * 1024

    val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .readTimeout(30, TimeUnit.SECONDS)
            .connectTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    fun getFileMeta(title: String, artist: String): FileMeta {
        val extra = YouTube.getSearchExtractor(
            title + " " + artist,
            listOf(YoutubeSearchQueryHandlerFactory.MUSIC_SONGS),
            null
        ) as YoutubeMusicSearchExtractor
        extra.fetchPage()

        val mediaLink = extra.initialPage.items.firstOrNull()?.url
            ?: throw IOException("No search results found for $title")

        return getFileMetaFromUrl(mediaLink, false)
    }

    /** Direct YouTube / YouTube Music item. [wantVideo] = best muxed mp4, otherwise best m4a audio. */
    fun getFileMetaFromUrl(mediaLink: String, wantVideo: Boolean): FileMeta {
        val extractor = YouTube.getStreamExtractor(mediaLink)
        extractor.fetchPage()

        if (wantVideo) {
            val bestVideo = extractor.videoStreams
                .filter { it.format?.suffix == "mp4" }
                .maxByOrNull { it.height }
                ?: throw IOException("No video streams found for ${extractor.name}")
            return FileMeta(url = bestVideo.content, name = extractor.name, extention = "mp4")
        }

        val bestStream = extractor.audioStreams
            .filter { it.format?.suffix == "m4a" }
            .maxByOrNull { it.bitrate }
            ?: throw IOException("No audio streams found for ${extractor.name}")

        return FileMeta(url = bestStream.content, name = extractor.name, extention = "m4a")
    }

    /**
     * Full, independent pipeline of ONE task: download -> (convert to MP3 | tag) -> copy to folder.
     * Returns only after every step finished successfully; any failure throws.
     */
    suspend fun downloadFile(
        context: Context,
        url: String,
        folderUriString: String,
        fileName: String,
        extension: String,
        convertToMp3: Boolean,
        artist: String,
        imageUrl: String? = null,
        createSubfolder: Boolean = true,
        playlistName: String? = null,
        isVideo: Boolean = false,
        onStage: (DownloadStatus) -> Unit = {}
    ) {
        // Unique temp names: parallel tasks (or same-title tracks) never share files
        val uid = UUID.randomUUID().toString()
        val tempFile = File(context.cacheDir, "spd_$uid.$extension")
        val thumbTarget = File(context.cacheDir, "spd_$uid.jpg")

        try {
            val thumbFile: File? = coroutineScope {
                val thumbJob = if (imageUrl != null && !isVideo) {
                    async(Dispatchers.IO) { downloadThumbnail(imageUrl, thumbTarget) }
                } else null

                val request = okhttp3.Request.Builder()
                    .url(url)
                    .addHeader("Range", "bytes=0-")
                    .build()

                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) throw IOException("Unexpected code $response")

                    val body = response.body ?: throw IOException("Empty response body")

                    body.byteStream().use { input ->
                        FileOutputStream(tempFile).use { output ->
                            val buffer = ByteArray(BUFFER_SIZE)
                            var bytesRead: Int
                            while (input.read(buffer).also { bytesRead = it } != -1) {
                                if (!currentCoroutineContext().isActive) {
                                    throw CancellationException("Download cancelled")
                                }
                                output.write(buffer, 0, bytesRead)
                            }
                        }
                    }
                }
                thumbJob?.await()
            }
            currentCoroutineContext().ensureActive()

            onStage(if (convertToMp3) DownloadStatus.CONVERTING else DownloadStatus.PROCESSING)

            val processedFile = if (convertToMp3) {
                convertToMp3(tempFile, fileName, artist, thumbFile)
            } else {
                tagFile(tempFile, extension, fileName, artist, thumbFile)
            }
            currentCoroutineContext().ensureActive()

            val finalExtension = if (convertToMp3) "mp3" else extension
            if (!processedFile.exists() || processedFile.length() == 0L) {
                throw IOException("Processed file is missing or empty for $fileName")
            }
            if (convertToMp3 && !processedFile.name.endsWith(".mp3")) {
                throw IOException("MP3 conversion was not applied for $fileName")
            }

            onStage(DownloadStatus.PROCESSING)

            // Copy to SAF
            val folderUri = folderUriString.toUri()
            var pickedDir = DocumentFile.fromTreeUri(context, folderUri)
                ?: throw IOException("Failed to get document from tree URI")

            if (createSubfolder && !playlistName.isNullOrBlank()) {
                val sanitizedPlaylistName = sanitizeFilename(playlistName)
                var subDir = pickedDir.findFile(sanitizedPlaylistName)
                if (subDir == null || !subDir.isDirectory) {
                    subDir = pickedDir.createDirectory(sanitizedPlaylistName)
                }
                if (subDir != null) {
                    pickedDir = subDir
                }
            }

            val mime = if (isVideo) "video/mp4" else "audio/*"
            val newFile = pickedDir.createFile(mime, "$fileName.$finalExtension")
                ?: pickedDir.findFile("$fileName.$finalExtension")
                ?: throw IOException("Failed to create file in selected folder")

            val output = context.contentResolver.openOutputStream(newFile.uri)
                ?: throw IOException("Failed to open output file")
            output.use { out ->
                processedFile.inputStream().use { input ->
                    input.copyTo(out, BUFFER_SIZE)
                }
            }
        } finally {
            // Removes every intermediate file of this task (including after failure/cancel)
            context.cacheDir.listFiles { f -> f.name.startsWith("spd_$uid") }?.forEach { it.delete() }
        }
    }

    fun sanitizeFilename(name: String): String {
        return name.replace(Regex("[\\\\/:*?\"<>|]"), "_")
    }

    private fun tagFile(file: File, ext: String, trackName: String, artist: String, thumbFile: File? = null): File {
        val tempFile = File(file.parent, "${file.nameWithoutExtension}.tmp.$ext")

        val args = mutableListOf("-i", file.absolutePath)
        if (thumbFile != null) {
            args += listOf("-i", thumbFile.absolutePath, "-map", "0", "-map", "1", "-c", "copy", "-disposition:v:0", "attached_pic")
        } else {
            args += listOf("-codec", "copy")
        }
        args += listOf("-metadata", "title=$trackName", "-metadata", "artist=$artist", "-y", tempFile.absolutePath)

        FFmpegKitConfig.setLogLevel(Level.AV_LOG_QUIET)
        val session = FFmpegKit.executeWithArguments(args.toTypedArray())

        return if (ReturnCode.isSuccess(session.returnCode) && tempFile.exists() && tempFile.length() > 0L) {
            file.delete()
            tempFile
        } else {
            tempFile.delete()
            file
        }
    }

    /** Strict: returns an existing, non-empty .mp3 or throws. Never falls back to the original file. */
    private fun convertToMp3(file: File, trackName: String, artist: String, thumbFile: File? = null): File {
        val output = File(file.parent, "${file.nameWithoutExtension}.mp3")

        var ok = runMp3Conversion(file, output, trackName, artist, thumbFile)
        if (!ok && thumbFile != null) {
            // Cover art can break the mux: retry once without cover so the track is still converted
            ok = runMp3Conversion(file, output, trackName, artist, null)
        }
        if (!ok) {
            output.delete()
            throw IOException("MP3 conversion failed for $trackName")
        }
        file.delete()
        return output
    }

    private fun runMp3Conversion(input: File, output: File, trackName: String, artist: String, thumbFile: File?): Boolean {
        val args = mutableListOf("-i", input.absolutePath)
        if (thumbFile != null) {
            args += listOf(
                "-i", thumbFile.absolutePath,
                "-map", "0:a", "-map", "1:0", "-c:a", "libmp3lame", "-ab", "192k", "-ar", "44100",
                "-id3v2_version", "3",
                "-metadata:s:v", "title=Album cover", "-metadata:s:v", "comment=Cover (Front)"
            )
        } else {
            args += listOf("-map", "0:a", "-c:a", "libmp3lame", "-ab", "192k", "-ar", "44100")
        }
        args += listOf("-metadata", "title=$trackName", "-metadata", "artist=$artist", "-y", output.absolutePath)

        FFmpegKitConfig.setLogLevel(Level.AV_LOG_QUIET)
        val session = FFmpegKit.executeWithArguments(args.toTypedArray())
        return ReturnCode.isSuccess(session.returnCode) && output.exists() && output.length() > 0L
    }

    private fun downloadThumbnail(url: String, target: File): File? {
        return try {
            val request = okhttp3.Request.Builder().url(url).build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                response.body.byteStream().use { input ->
                    target.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
                target
            }
        } catch (e: Exception) {
            null
        }
    }
}

class NewPipeDownloader private constructor() : Downloader() {
    
    @Throws(IOException::class, ReCaptchaException::class)
    override fun execute(request: Request): Response {
        val httpMethod = request.httpMethod()
        val url = request.url()
        val headers = request.headers()
        val dataToSend = request.dataToSend()
        
        var requestBody = dataToSend?.toRequestBody()

        if (requestBody == null && (httpMethod == "POST" || httpMethod == "PUT" || httpMethod == "PATCH")) {
            requestBody = "".toRequestBody()
        }

        val requestBuilder = okhttp3.Request.Builder()
            .method(httpMethod, requestBody)
            .url(url)
            .addHeader("User-Agent", USER_AGENT)

        headers.forEach { (name, values) ->
            values.forEach { value ->
                requestBuilder.addHeader(name, value)
            }
        }

        val response = DownloadManager.client.newCall(requestBuilder.build()).execute()
        
        if (response.code == 429) {
            response.close()
            throw ReCaptchaException("reCaptcha Challenge requested", url)
        }

        val bodyString = response.body?.string() ?: ""
        val latestUrl = response.request.url.toString()
        
        return Response(
            response.code,
            response.message,
            response.headers.toMultimap(),
            bodyString,
            latestUrl
        )
    }

    companion object {
        const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36"
        private var instance: NewPipeDownloader? = null

        fun getInstance(): NewPipeDownloader {
            if (instance == null) {
                instance = NewPipeDownloader()
            }
            return instance!!
        }
    }
}
