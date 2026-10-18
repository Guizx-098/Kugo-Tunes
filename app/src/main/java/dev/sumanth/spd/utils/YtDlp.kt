package dev.sumanth.spd.utils

import android.content.Context
import android.util.Log
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import dev.sumanth.spd.R
import dev.sumanth.spd.model.DownloadStatus
import dev.sumanth.spd.model.Platform
import dev.sumanth.spd.model.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

/**
 * TikTok, Instagram Reels and Facebook through yt-dlp (youtubedl-android): the maintained open-source extractor that
 * follows every change these platforms make. It only reads what a public link offers; private, removed or login-only
 * content is reported to the user. The best quality available is requested; formats the platform marks as
 * watermarked are the last choice. Whether a watermark-free file exists depends on the platform.
 */
object YtDlp {
    private const val TAG = "YtDlp"

    // Best H.264 video + best audio joined into MP4 (no re-encoding), then any best video+audio, then the best single file.
    private const val FORMAT = "bv*[vcodec^=avc]+ba[ext=m4a]/bv*[vcodec^=avc]+ba/bv*+ba/b"

    private val lock = Any()
    private val updateLock = Any()
    private const val UPDATE_EVERY_MS = 12L * 60 * 60 * 1000
    @Volatile private var ready = false
    @Volatile private var failed = false

    /** Starts the bundled yt-dlp (first launch unpacks it). False when it cannot run on this device. */
    fun isReady(context: Context): Boolean {
        if (ready) return true
        if (failed) return false
        synchronized(lock) {
            if (ready) return true
            if (failed) return false
            return try {
                val app = context.applicationContext
                YoutubeDL.getInstance().init(app)
                // ffmpeg only joins separate video and audio files: its failure must not disable yt-dlp
                try {
                    FFmpeg.getInstance().init(app)
                } catch (e: Throwable) {
                    Log.w(TAG, "ffmpeg for yt-dlp unavailable: ${e.javaClass.simpleName}")
                }
                ready = true
                true
            } catch (e: Throwable) {
                Log.e(TAG, "yt-dlp could not start: ${e.javaClass.simpleName}")
                failed = true
                false
            }
        }
    }

    /**
     * yt-dlp fixes TikTok / Instagram almost every week and the copy bundled in the app gets old, so it is updated
     * from the official nightly channel (at most every 12 hours, or right away when [force] is true).
     */
    fun updateIfNeeded(context: Context, force: Boolean = false) {
        if (!isReady(context)) return
        val prefs = context.applicationContext.getSharedPreferences("ytdlp_update", Context.MODE_PRIVATE)
        synchronized(updateLock) {
            val now = System.currentTimeMillis()
            if (!force && now - prefs.getLong("last_update", 0L) < UPDATE_EVERY_MS) return
            try {
                YoutubeDL.getInstance().updateYoutubeDL(context.applicationContext, YoutubeDL.UpdateChannel.NIGHTLY)
                prefs.edit().putLong("last_update", now).apply()
            } catch (e: Throwable) {
                Log.w(TAG, "yt-dlp update failed: ${e.javaClass.simpleName}")
            }
        }
    }

    private fun baseRequest(url: String) = YoutubeDLRequest(url).apply {
        addOption("--no-playlist")
        addOption("--no-warnings")
        addOption("--socket-timeout", "30")
        addOption("--retries", "3")
    }

    /** Validates the link, reads the title and thumbnail, and returns the task to download. */
    fun resolveTrack(context: Context, input: String, platform: Platform): Track {
        val pageUrl = SocialVideoExtractor.prepare(input, platform)
        val name = when (platform) {
            Platform.TIKTOK -> "TikTok"
            Platform.INSTAGRAM -> "Instagram Reel"
            else -> "Facebook video"
        }
        if (platform == Platform.TIKTOK || platform == Platform.INSTAGRAM) updateIfNeeded(context)
        val info = try {
            YoutubeDL.getInstance().getInfo(baseRequest(pageUrl))
        } catch (e: Exception) {
            Log.w(TAG, "info failed for $platform: ${e.message?.take(300)}")
            val mapped = mapError(e)
            // TikTok / Instagram: only definitive answers stop here; otherwise the download tries the other sources
            if ((platform == Platform.TIKTOK || platform == Platform.INSTAGRAM) && !isDefinitive(mapped)) null else throw mapped
        }
        val title = (info?.title ?: "").replace(Regex("\\s+"), " ").trim().take(80).ifBlank { name }
        return Track(
            title = title,
            artist = "",
            status = DownloadStatus.IDLE,
            imageUrl = info?.thumbnail,
            isVideo = true,
            sourceUrl = pageUrl,
            useYtDlp = true
        )
    }

    /**
     * Downloads the video of [url] into a private temp folder and returns the file. The caller copies it to the
     * chosen folder and deletes `file.parentFile`. Cancelling the coroutine stops the yt-dlp process.
     */
    suspend fun download(context: Context, url: String): File {
        try {
            return downloadOnce(context, url)
        } catch (e: LocalizedException) {
            if (e.resId != R.string.err_extraction_failed) throw e
            // "could not read the video" usually means the bundled extractor is outdated: update and try once more
            withContext(Dispatchers.IO) { updateIfNeeded(context, force = true) }
            return downloadOnce(context, url)
        }
    }

    private suspend fun downloadOnce(context: Context, url: String): File = withContext(Dispatchers.IO) {
        if (!isReady(context)) {
            throw LocalizedException(R.string.err_extraction_failed, fallback = "Could not start the download engine.")
        }
        val id = "spd_ytdlp_" + UUID.randomUUID()
        val dir = File(context.cacheDir, id).apply { mkdirs() }
        val request = baseRequest(url).apply {
            addOption("-f", FORMAT)
            addOption("--merge-output-format", "mp4")
            addOption("-N", "4")
            addOption("--no-mtime")
            addOption("-o", File(dir, "video.%(ext)s").absolutePath)
        }
        val handle = currentCoroutineContext()[Job]?.invokeOnCompletion { cause ->
            if (cause != null) runCatching { YoutubeDL.getInstance().destroyProcessById(id) }
        }
        try {
            YoutubeDL.getInstance().execute(request = request, processId = id)
        } catch (e: Exception) {
            dir.deleteRecursively()
            currentCoroutineContext().ensureActive()
            Log.w(TAG, "download failed: ${e.message?.take(300)}")
            throw mapError(e)
        } finally {
            handle?.dispose()
        }
        val file = dir.listFiles { f ->
            f.isFile && f.length() > 0 && !f.name.endsWith(".part") && !f.name.endsWith(".ytdl") && !f.name.endsWith(".temp")
        }?.maxByOrNull { it.length() }
        if (file == null) {
            dir.deleteRecursively()
            throw LocalizedException(R.string.err_post_without_video, fallback = "No video was downloaded.")
        }
        file
    }

    /** Answers that no other source can change (wrong link, removed or private video, post without video). */
    fun isDefinitive(e: LocalizedException): Boolean = e.resId == R.string.err_wrong_platform ||
        e.resId == R.string.err_video_unavailable || e.resId == R.string.err_post_without_video

    /** Turns the yt-dlp error text into a clear message instead of a generic failure. */
    fun mapError(e: Throwable): Exception {
        if (e is LocalizedException) return e
        val text = (e.message ?: "").lowercase()
        fun has(vararg parts: String) = parts.any { text.contains(it) }
        fun err(id: Int, fallback: String) = LocalizedException(id, fallback = fallback)
        return when {
            has("login required", "log in", "sign in", "requires login", "not granting access") ->
                err(R.string.err_login_required, "This content requires a login.")
            has("429", "too many requests", "rate-limit", "rate limit") ->
                err(R.string.err_rate_limited, "The platform is limiting requests right now.")
            has("403", "forbidden", "blocked") ->
                err(R.string.err_access_blocked, "The platform blocked access to this link.")
            has("unsupported url") ->
                err(R.string.err_wrong_platform, "This link does not belong to the selected platform.")
            has("no video formats", "there is no video", "no video in this post", "photo") ->
                err(R.string.err_post_without_video, "This post does not seem to contain a video.")
            has("private", "not available", "unavailable", "removed", "does not exist", "404") ->
                err(R.string.err_video_unavailable, "This video is unavailable or private.")
            has("unable to download webpage", "timed out", "name or service not known", "unreachable", "connection") ->
                err(R.string.err_network, "Could not reach the site.")
            else -> err(R.string.err_extraction_failed, "Could not read the video data.")
        }
    }
}
