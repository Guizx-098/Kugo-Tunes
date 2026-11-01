package dev.sumanth.spd.utils

import android.content.Context
import dev.sumanth.spd.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.URLEncoder
import java.util.UUID

/** Downloads a direct video address into a private temp folder (the caller deletes `file.parentFile`). */
object DirectFile {
    suspend fun download(
        context: Context,
        url: String,
        headers: Map<String, String> = emptyMap(),
        onProgress: (Float) -> Unit = {}
    ): File = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, "spd_direct_" + UUID.randomUUID()).apply { mkdirs() }
        val target = File(dir, "video.mp4")
        try {
            DownloadManager.fetchToFile(url, target, headers, onProgress)
            if (target.length() == 0L) throw IOException("Empty file")
            target
        } catch (e: Exception) {
            dir.deleteRecursively()
            throw e
        }
    }
}

/**
 * TikTok through the public TikWM service: it returns the video address (HD when it exists) in about a second, which is
 * much faster than starting yt-dlp. The link of the video is sent to that third-party service.
 * Set [ENABLED] to false to turn it off completely.
 */
object TikWm {
    const val ENABLED = true
    private const val BASE = "https://www.tikwm.com"

    class Result(val file: File, val title: String?)
    private class Found(val url: String, val title: String?)

    suspend fun download(context: Context, tiktokUrl: String, onProgress: (Float) -> Unit = {}): Result {
        val found = resolve(tiktokUrl)
        val file = DirectFile.download(context, found.url, emptyMap(), onProgress)
        return Result(file, found.title)
    }

    private suspend fun resolve(tiktokUrl: String): Found {
        var attempt = 0
        while (true) {
            try {
                return withContext(Dispatchers.IO) { resolveOnce(tiktokUrl) }
            } catch (e: LocalizedException) {
                // The free service allows about one request per second: wait a moment and ask again
                if (e.resId == R.string.err_rate_limited && attempt < 3) {
                    attempt++
                    delay(1100L)
                    continue
                }
                throw e
            }
        }
    }

    private fun resolveOnce(tiktokUrl: String): Found {
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
        val url = if (address.startsWith("http")) address else BASE + address
        return Found(url, data?.optString("title")?.ifBlank { null })
    }
}
