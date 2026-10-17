package dev.sumanth.spd.utils

import android.content.Context
import android.util.Log
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import dev.sumanth.spd.model.DownloadStatus
import dev.sumanth.spd.model.Platform
import dev.sumanth.spd.model.Track
import java.io.IOException

/**
 * Uses the yt-dlp/Python runtime bundled in the Android app to resolve public social video pages.
 * It does not use a third-party extraction API and does not load browser cookies or bypass login walls.
 */
object YtDlpSocialExtractor {
    private const val TAG = "YtDlpSocialExtractor"

    private fun firstUrl(input: String): String? =
        Regex("https?://[^\\s]+")
            .find(input.trim())?.value?.trimEnd('.', ',', ';', ')', ']', '"', '\'', '>', '!')

    fun extract(context: Context, input: String, platform: Platform): Track {
        val url = firstUrl(input)
            ?: throw IllegalArgumentException("Cole um link válido da plataforma.")
        if (!SocialVideoExtractor.belongsTo(platform, url)) {
            throw IllegalArgumentException("Esse link não pertence à plataforma selecionada.")
        }

        try {
            YoutubeDL.getInstance().init(context.applicationContext)
            val request = YoutubeDLRequest(url).apply {
                addOption("--no-playlist")
                addOption("--no-warnings")
                addOption("-f", "best[ext=mp4]/best")
            }
            val info = YoutubeDL.getInstance().getInfo(request)
            val mediaUrl = info.url?.takeIf { it.startsWith("https://") || it.startsWith("http://") }
                ?: throw IOException("yt-dlp não retornou um endereço direto de mídia para esta publicação.")
            val title = info.title?.trim().orEmpty().ifBlank {
                when (platform) {
                    Platform.TIKTOK -> "TikTok video"
                    Platform.KWAI -> "Kwai video"
                    Platform.INSTAGRAM -> "Instagram Reel"
                    else -> "Facebook video"
                }
            }.take(100)
            return Track(
                title = title,
                artist = info.uploader?.trim().orEmpty(),
                status = DownloadStatus.IDLE,
                imageUrl = info.thumbnail,
                isVideo = true,
                directUrl = mediaUrl,
                referer = url
            )
        } catch (e: Exception) {
            Log.w(TAG, "yt-dlp extraction failed for ${platform.name}: ${e.javaClass.simpleName}")
            throw IOException("yt-dlp não conseguiu extrair esta publicação pública: ${e.message ?: e.javaClass.simpleName}", e)
        }
    }
}
