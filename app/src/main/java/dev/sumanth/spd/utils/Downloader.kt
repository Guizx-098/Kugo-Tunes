package dev.sumanth.spd.utils

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.FFmpegKitConfig
import com.arthenica.ffmpegkit.Level
import com.arthenica.ffmpegkit.ReturnCode
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import org.schabi.newpipe.extractor.ServiceList.YouTube
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request
import org.schabi.newpipe.extractor.downloader.Response
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException
import org.schabi.newpipe.extractor.services.youtube.extractors.YoutubeMusicSearchExtractor
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import org.schabi.newpipe.extractor.services.youtube.linkHandler.YoutubeSearchQueryHandlerFactory
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.concurrent.TimeUnit
import androidx.core.net.toUri
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import android.util.Log
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.withLock

data class FileMeta(val url: String, val name: String, val extention: String)

object DownloadManager {
    private const val BUFFER_SIZE = 128 * 1024
    private val safLock = Mutex()
    private const val TAG = "SPD"
    private const val PARALLEL_PARTS = 4
    private const val MIN_PART_SIZE = 512L * 1024L
    private const val MAX_RETRIES = 3
    val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .readTimeout(30, TimeUnit.SECONDS)
            .connectTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    fun getFileMeta(title: String, artist: String): FileMeta {
        return try {
            getFileMetaFromMusicSearch(title, artist)
        } catch (e: Exception) {
            Log.d(TAG, "Busca no YouTube Music falhou para '$title' (${e.message}). Tentando busca geral...")
            getFileMetaFromGeneralSearch(title, artist)
        }
    }

    private fun getFileMetaFromMusicSearch(title: String, artist: String): FileMeta {
        val extra = YouTube.getSearchExtractor(
            title + " " + artist,
            listOf(YoutubeSearchQueryHandlerFactory.MUSIC_SONGS),
            null
        ) as YoutubeMusicSearchExtractor
        extra.fetchPage()

        val mediaLink = extra.initialPage.items.firstOrNull()?.url
            ?: throw IOException("No search results found for $title")

        return getFileMetaFromLink(mediaLink, title)
    }

    private fun getFileMetaFromLink(mediaLink: String, title: String): FileMeta {
        val extractor = YouTube.getStreamExtractor(mediaLink)
        extractor.fetchPage()

        val bestStream = extractor.audioStreams
            .filter { it.format?.suffix == "m4a" }
            .maxByOrNull { it.bitrate }
            ?: throw IOException("No audio streams found for $title")

        return FileMeta(url = bestStream.content, name = extractor.name, extention = "m4a")
    }

    /** Plano B: busca geral no YouTube e escolhe o resultado que parece mais oficial. */
    private fun getFileMetaFromGeneralSearch(title: String, artist: String): FileMeta {
        val queries = listOf("$title $artist", title).distinct()
        var lastError: Exception? = null

        for (query in queries) {
            val candidates = try {
                val search = YouTube.getSearchExtractor(
                    query,
                    listOf(YoutubeSearchQueryHandlerFactory.VIDEOS),
                    null
                )
                search.fetchPage()
                search.initialPage.items
                    .filterIsInstance<StreamInfoItem>()
                    .filter { it.duration in 30..900 }
                    .map { it to officialScore(it, title, artist) }
                    .filter { it.second > Int.MIN_VALUE / 2 }
                    .sortedByDescending { it.second }
                    .take(3)
            } catch (e: Exception) {
                lastError = e
                continue
            }

            for ((item, score) in candidates) {
                try {
                    Log.d(TAG, "Plano B: '${item.name}' (${item.uploaderName}) pontos=$score")
                    return getFileMetaFromLink(item.url, title)
                } catch (e: Exception) {
                    lastError = e
                }
            }
        }
        throw IOException("No official match found for $title", lastError)
    }

    private fun normalizeText(text: String): String {
        return java.text.Normalizer.normalize(text.lowercase(), java.text.Normalizer.Form.NFD)
            .replace(Regex("\\p{M}+"), "")
            .replace(Regex("[^a-z0-9]+"), " ")
            .trim()
    }

    /** Pontua o quanto o video parece ser a versao oficial. Retorna um valor muito baixo se nem tem o titulo. */
    private fun officialScore(item: StreamInfoItem, title: String, artist: String): Int {
        val baseTitle = normalizeText(title.substringBefore(" (").substringBefore(" - ").substringBefore(" ["))
        val mainArtist = normalizeText(artist.split(",").first())
        val name = normalizeText(item.name ?: "")
        val uploaderRaw = (item.uploaderName ?: "").lowercase()
        val uploader = normalizeText(uploaderRaw)

        if (baseTitle.isNotBlank() && !name.contains(baseTitle)) return Int.MIN_VALUE

        var score = 0
        if (item.isUploaderVerified) score += 40
        if (uploaderRaw.trim().endsWith("- topic")) score += 60
        if (uploader.contains("vevo")) score += 30
        if (mainArtist.isNotBlank() && uploader.contains(mainArtist)) score += 35
        if (mainArtist.isNotBlank() && name.contains(mainArtist)) score += 10
        if (name.contains("official")) score += 15
        if (name.contains("audio")) score += 10

        val padded = " $name "
        val wanted = " ${normalizeText(title)} "
        val unwanted = listOf(
            "cover", "karaoke", "remix", "live", "ao vivo", "reaction", "slowed",
            "sped up", "nightcore", "8d", "instrumental", "tutorial", "lesson", "tribute"
        )
        for (word in unwanted) {
            if (padded.contains(" $word ") && !wanted.contains(" $word ")) score -= 50
        }
        return score
    }

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
        partsPerFile: Int = PARALLEL_PARTS
    ) {
        val uniqueId = System.nanoTime()
        val tempFile = File(context.cacheDir, "$uniqueId.$extension")
        
        fetchToFile(url, tempFile, partsPerFile)

        val thumbFile = imageUrl?.let { downloadThumbnail(it) }

        val processedFile = if (convertToMp3) {
            convertToMp3(tempFile, fileName, artist, thumbFile)
        } else {
            tagFile(tempFile, extension, fileName, artist, thumbFile)
        }

        // Copy to SAF
        val folderUri = folderUriString.toUri()
        val newFile = safLock.withLock {
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

        val finalExtension = if (convertToMp3) "mp3" else extension
        val created = pickedDir.createFile("audio/*", "$fileName.$finalExtension")
            ?: pickedDir.findFile("$fileName.$finalExtension")
            ?: throw IOException("Failed to create file in selected folder")
        created
        }

        context.contentResolver.openOutputStream(newFile.uri)?.use { output ->
            processedFile.inputStream().use { input ->
                input.copyTo(output, BUFFER_SIZE)
            }
        }
        
        processedFile.delete()
        if (processedFile != tempFile) tempFile.delete()
        thumbFile?.delete()
    }

    private suspend fun fetchToFile(url: String, target: File, partsPerFile: Int) {
        val t0 = System.currentTimeMillis()
        val total = probeLength(url)
        if (total > 0L) {
            parallelRangeDownload(url, target, total, partsPerFile)
        } else {
            singleStreamDownload(url, target)
        }
        val ms = System.currentTimeMillis() - t0
        Log.d(TAG, "audio ${target.length() / 1024} KB em $ms ms (partes=${if (total > 0L) "paralelo" else "unico"})")
    }

    /** Pergunta ao servidor o tamanho total e se aceita Range. Retorna -1 se nao der. */
    private fun probeLength(url: String): Long {
        return try {
            val req = okhttp3.Request.Builder().url(url).addHeader("Range", "bytes=0-0").build()
            client.newCall(req).execute().use { response ->
                if (response.code != 206) return -1L
                val range = response.header("Content-Range") ?: return -1L
                range.substringAfter('/', "").toLongOrNull() ?: -1L
            }
        } catch (e: IOException) {
            -1L
        }
    }

    private suspend fun parallelRangeDownload(url: String, target: File, total: Long, partsPerFile: Int) = coroutineScope {
        RandomAccessFile(target, "rw").use { raf ->
            raf.setLength(total)
            val channel = raf.channel
            val parts = maxOf(1L, minOf(partsPerFile.toLong(), total / MIN_PART_SIZE)).toInt()
            val partSize = (total + parts - 1) / parts
            (0 until parts).map { idx ->
                async(Dispatchers.IO) {
                    val start = idx * partSize
                    val end = minOf(start + partSize, total) - 1
                    downloadRange(url, channel, start, end)
                }
            }.awaitAll()
        }
    }

    private suspend fun downloadRange(url: String, channel: FileChannel, start: Long, end: Long) {
        var attempt = 0
        while (true) {
            try {
                val req = okhttp3.Request.Builder()
                    .url(url)
                    .addHeader("Range", "bytes=$start-$end")
                    .build()
                client.newCall(req).execute().use { response ->
                    if (response.code != 206) throw IOException("Range nao aceito: ${response.code}")
                    val body = response.body ?: throw IOException("Empty response body")
                    var pos = start
                    val buf = ByteArray(BUFFER_SIZE)
                    body.byteStream().use { input ->
                        while (true) {
                            val n = input.read(buf)
                            if (n == -1) break
                            currentCoroutineContext().ensureActive()
                            channel.write(ByteBuffer.wrap(buf, 0, n), pos)
                            pos += n
                        }
                    }
                    if (pos != end + 1) throw IOException("Parte incompleta: $pos de ${end + 1}")
                }
                return
            } catch (e: CancellationException) {
                throw e
            } catch (e: IOException) {
                attempt++
                if (attempt >= MAX_RETRIES) throw e
                delay(500L * attempt)
            }
        }
    }

    /** Caminho antigo (uma conexao so), usado se o servidor nao aceitar Range. */
    private suspend fun singleStreamDownload(url: String, target: File) {
        val request = okhttp3.Request.Builder()
            .url(url)
            .addHeader("Range", "bytes=0-")
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("Unexpected code $response")
            val body = response.body ?: throw IOException("Empty response body")
            body.byteStream().use { input ->
                FileOutputStream(target).use { output ->
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
    }

    fun sanitizeFilename(name: String): String {
        return name.replace(Regex("[\\\\/:*?\"<>|]"), "_")
    }

    private fun tagFile(file: File, ext: String, trackName: String, artist: String, thumbFile: File? = null): File {
        val inputPath = file.absolutePath
        val tempFile = File(file.parent, "${file.nameWithoutExtension}.tmp.$ext")
        val tempPath = tempFile.absolutePath

        val command = StringBuilder("-i \"$inputPath\" ")
        if (thumbFile != null) {
            command.append("-i \"${thumbFile.absolutePath}\" ")
            command.append("-map 0 -map 1 -c copy -disposition:v:0 attached_pic ")
        } else {
            command.append("-codec copy ")
        }
        command.append("-metadata title=\"$trackName\" ")
        command.append("-metadata artist=\"$artist\" ")
        command.append("-y \"$tempPath\"")

        FFmpegKitConfig.setLogLevel(Level.AV_LOG_QUIET)
        val session = FFmpegKit.execute(command.toString())

        return if (ReturnCode.isSuccess(session.returnCode)) {
            file.delete()
            tempFile
        } else {
            file
        }
    }

    private fun convertToMp3(file: File, trackName: String, artist: String, thumbFile: File? = null): File {
        val inputPath = file.absolutePath
        val outputPath = File(file.parent, "${file.nameWithoutExtension}.mp3").absolutePath

        val command = StringBuilder("-i \"$inputPath\" ")
        if (thumbFile != null) {
            command.append("-i \"${thumbFile.absolutePath}\" ")
            command.append("-map 0:a -map 1:0 -c:a libmp3lame -ab 192k -ar 44100 -id3v2_version 3 -metadata:s:v title=\"Album cover\" -metadata:s:v comment=\"Cover (Front)\" ")
        } else {
            command.append("-map 0:a -c:a libmp3lame -ab 192k -ar 44100 ")
        }
        command.append("-metadata title=\"$trackName\" ")
        command.append("-metadata artist=\"$artist\" ")
        command.append("-y \"$outputPath\"")

        FFmpegKitConfig.setLogLevel(Level.AV_LOG_QUIET)
        val session = FFmpegKit.execute(command.toString())

        return if (ReturnCode.isSuccess(session.returnCode)) {
            file.delete()
            File(outputPath)
        } else {
            file
        }
    }

    private fun downloadThumbnail(url: String): File? {
        return try {
            val request = okhttp3.Request.Builder().url(url).build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                val tempFile = File.createTempFile("thumb", ".jpg")
                response.body.byteStream().use { input ->
                    tempFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
                tempFile
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
