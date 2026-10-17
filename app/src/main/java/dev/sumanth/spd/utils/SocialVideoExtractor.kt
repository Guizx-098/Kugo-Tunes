package dev.sumanth.spd.utils

import dev.sumanth.spd.R
import dev.sumanth.spd.model.DownloadStatus
import dev.sumanth.spd.model.Platform
import dev.sumanth.spd.model.Track
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.Request
import org.json.JSONObject

/**
 * TikTok and Kwai: reads the same public page a browser opens for the shared link and uses the video address
 * that the page itself provides. It does not bypass logins, protections or access controls; if the page does not
 * expose a video, the user gets a clear message. These pages are not an official API and may change at any time.
 */
object SocialVideoExtractor {
    const val USER_AGENT =
        "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36"

    fun belongsTo(platform: Platform, input: String): Boolean {
        val link = input.lowercase()
        return when (platform) {
            Platform.TIKTOK -> link.contains("tiktok.com")
            Platform.KWAI -> link.contains("kwai.com") || link.contains("kw.ai") || link.contains("kwai-video.com")
            else -> false
        }
    }

    fun extract(input: String, platform: Platform): Track {
        val url = Regex("https?://\\S+").find(input)?.value
            ?: throw LocalizedException(R.string.err_link_read, fallback = "Unable to read the link.")
        if (!belongsTo(platform, url)) {
            throw LocalizedException(R.string.err_wrong_platform, fallback = "This link does not belong to the selected platform.")
        }

        val store = mutableListOf<Cookie>()
        val jar = object : CookieJar {
            override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
                store.addAll(cookies)
            }

            override fun loadForRequest(url: HttpUrl): List<Cookie> = store.filter { it.matches(url) }
        }
        val client = DownloadManager.client.newBuilder().cookieJar(jar).followRedirects(true).build()

        var html = ""
        var finalUrl = url
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", USER_AGENT)
            .header("Accept-Language", "en-US,en;q=0.9")
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw java.io.IOException("HTTP ${response.code}")
            finalUrl = response.request.url.toString()
            html = response.body.string()
        }

        val found = if (platform == Platform.TIKTOK) parseTikTok(html) else null
        val fallback = if (found?.videoUrl == null) parseGeneric(html) else null
        val resolved: Found = when {
            found != null && found.videoUrl != null -> found
            fallback != null -> fallback.copy(
                title = found?.title?.ifBlank { null } ?: fallback.title,
                author = found?.author.orEmpty(),
                cover = found?.cover ?: fallback.cover
            )
            else -> throw LocalizedException(
                R.string.err_video_not_found,
                fallback = "Could not find a downloadable video at this link."
            )
        }
        val videoUrl = resolved.videoUrl
            ?: throw LocalizedException(
                R.string.err_video_not_found,
                fallback = "Could not find a downloadable video at this link."
            )

        val cookieHeader = store.distinctBy { it.name }.joinToString("; ") { "${it.name}=${it.value}" }
        val name = if (platform == Platform.TIKTOK) "TikTok" else "Kwai"
        val title = resolved.title.replace(Regex("\\s+"), " ").trim().take(80).ifBlank { "$name video" }

        return Track(
            title = title,
            artist = resolved.author,
            status = DownloadStatus.IDLE,
            imageUrl = resolved.cover,
            isVideo = true,
            directUrl = videoUrl,
            referer = finalUrl,
            cookie = cookieHeader.ifBlank { null }
        )
    }

    private data class Found(val videoUrl: String?, val title: String, val author: String, val cover: String?)

    private fun parseTikTok(html: String): Found? {
        val raw = Regex(
            "<script id=\"__UNIVERSAL_DATA_FOR_REHYDRATION__\"[^>]*>(.*?)</script>",
            RegexOption.DOT_MATCHES_ALL
        ).find(html)?.groupValues?.get(1) ?: return null

        return try {
            val detail = JSONObject(raw).optJSONObject("__DEFAULT_SCOPE__")?.optJSONObject("webapp.video-detail")
                ?: return null
            val statusCode = detail.optInt("statusCode", 0)
            if (statusCode != 0) {
                throw LocalizedException(R.string.err_video_unavailable, fallback = "This video is unavailable or private.")
            }
            val item = detail.optJSONObject("itemInfo")?.optJSONObject("itemStruct") ?: return null
            val video = item.optJSONObject("video")
            var play = video?.optString("playAddr").orEmpty()
            if (play.isBlank()) {
                play = video?.optJSONArray("bitrateInfo")?.optJSONObject(0)
                    ?.optJSONObject("PlayAddr")?.optJSONArray("UrlList")?.optString(0).orEmpty()
            }
            Found(
                videoUrl = play.ifBlank { null }?.let(::unescape),
                title = item.optString("desc"),
                author = item.optJSONObject("author")?.optString("nickname").orEmpty(),
                cover = video?.optString("cover")?.ifBlank { null }?.let(::unescape)
            )
        } catch (e: LocalizedException) {
            throw e
        } catch (e: Exception) {
            null
        }
    }

    private fun parseGeneric(html: String): Found? {
        val patterns = listOf(
            "<meta[^>]+property=[\"']og:video(?::secure_url|:url)?[\"'][^>]+content=[\"']([^\"']+)[\"']",
            "<meta[^>]+content=[\"']([^\"']+)[\"'][^>]+property=[\"']og:video(?::secure_url|:url)?[\"']",
            "\"contentUrl\"\\s*:\\s*\"([^\"]+)\"",
            "<video[^>]+src=[\"']([^\"']+)[\"']",
            "\"(?:videoUrl|playUrl|srcNoMark)\"\\s*:\\s*\"([^\"]+)\""
        )
        val video = patterns.firstNotNullOfOrNull { Regex(it, RegexOption.IGNORE_CASE).find(html)?.groupValues?.get(1) }
            ?.let(::unescape)?.takeIf { it.startsWith("http") }
        fun meta(prop: String) = Regex("<meta[^>]+property=[\"']$prop[\"'][^>]+content=[\"']([^\"']*)[\"']", RegexOption.IGNORE_CASE)
            .find(html)?.groupValues?.get(1)?.let(::unescape)
        return Found(video, meta("og:title").orEmpty(), "", meta("og:image"))
    }

    private fun unescape(value: String): String = value
        .replace("\\u002F", "/").replace("\\/", "/").replace("&amp;", "&").replace("\\u0026", "&")
}
