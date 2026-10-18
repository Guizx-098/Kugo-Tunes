package dev.sumanth.spd.utils

import android.content.Context
import dev.sumanth.spd.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.URLEncoder
import java.util.UUID

/** Downloads a direct video address into a private temp folder (the caller deletes `file.parentFile`). */
object DirectFile {
    suspend fun download(context: Context, url: String, headers: Map<String, String> = emptyMap()): File =
        withContext(Dispatchers.IO) {
            val dir = File(context.cacheDir, "spd_direct_" + UUID.randomUUID()).apply { mkdirs() }
            val target = File(dir, "video.mp4")
            try {
                val builder = Request.Builder().url(url)
                headers.forEach { (k, v) -> builder.header(k, v) }
                DownloadManager.client.newCall(builder.build()).execute().use { response ->
                    if (!response.isSuccessful) throw DownloadHttpException(response.code)
                    response.body.byteStream().use { input ->
                        target.outputStream().use { output ->
                            val buffer = ByteArray(64 * 1024)
                            var read: Int
                            while (input.read(buffer).also { read = it } != -1) {
                                currentCoroutineContext().ensureActive()
                                output.write(buffer, 0, read)
                            }
                        }
                    }
                }
                if (target.length() == 0L) throw IOException("Empty file")
                target
            } catch (e: Exception) {
                dir.deleteRecursively()
                throw e
            }
        }
}

/**
 * Last-resort source for TikTok, used only when yt-dlp could not read the video: the public TikWM service returns the
 * video address (HD when it exists). The link of the video is sent to that third-party service.
 * Set [ENABLED] to false to turn it off.
 */
object TikWm {
    const val ENABLED = true
    private const val BASE = "https://www.tikwm.com"

    suspend fun download(context: Context, tiktokUrl: String): File {
        val videoUrl = withContext(Dispatchers.IO) { resolve(tiktokUrl) }
        return DirectFile.download(context, videoUrl)
    }

    private fun resolve(tiktokUrl: String): String {
        val api = "$BASE/api/?hd=1&url=" + URLEncoder.encode(tiktokUrl, "UTF-8")
        val request = Request.Builder().url(api).header("User-Agent", SocialVideoExtractor.USER_AGENT).build()
        val body = DownloadManager.client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw DownloadHttpException(response.code)
            response.body.string()
        }
        val json = JSONObject(body)
        if (json.optInt("code", -1) != 0) {
            val msg = json.optString("msg").lowercase()
            throw when {
                msg.contains("limit") -> LocalizedException(R.string.err_rate_limited, fallback = "Too many requests.")
                else -> LocalizedException(R.string.err_video_unavailable, fallback = "This video is unavailable or private.")
            }
        }
        val data = json.optJSONObject("data")
        // Never the "wmplay" address: that one is the watermarked file
        val address = listOf("hdplay", "play").map { data?.optString(it).orEmpty() }.firstOrNull { it.isNotBlank() }
            ?: throw LocalizedException(R.string.err_post_without_video, fallback = "No video was found.")
        return if (address.startsWith("http")) address else BASE + address
    }
}
