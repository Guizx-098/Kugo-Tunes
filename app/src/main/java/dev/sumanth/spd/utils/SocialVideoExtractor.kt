package dev.sumanth.spd.utils

import dev.sumanth.spd.R
import dev.sumanth.spd.model.DownloadStatus
import dev.sumanth.spd.model.Platform
import dev.sumanth.spd.model.Track
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Request
import org.json.JSONObject
import java.io.IOException

/**
 * TikTok, Kwai, Instagram (Reels only) and Facebook: reads the same public page a logged-out browser opens for the
 * link and uses the video address that the page itself provides. It never logs in, never uses private APIs and does
 * not bypass privacy settings or protections: if the page does not expose a video (private content, login wall,
 * removed post), the user gets a clear message. These pages are not official APIs and may change at any time.
 */
object SocialVideoExtractor {
    const val USER_AGENT =
        "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36"
    private const val DESKTOP_USER_AGENT =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"

    private fun hostOf(url: String): String = url.toHttpUrlOrNull()?.host?.lowercase().orEmpty()

    private fun hostIs(url: String, vararg domains: String): Boolean {
        val host = hostOf(url)
        return domains.any { host == it || host.endsWith(".$it") }
    }

    fun belongsTo(platform: Platform, input: String): Boolean = when (platform) {
        Platform.TIKTOK -> hostIs(input, "tiktok.com")
        Platform.KWAI -> hostIs(input, "kwai.com", "kw.ai", "kwai-video.com")
        Platform.INSTAGRAM -> hostIs(input, "instagram.com", "instagr.am")
        Platform.FACEBOOK -> hostIs(input, "facebook.com", "fb.watch", "fb.com")
        else -> false
    }

    /** First link in [input], without the punctuation that usually trails a pasted/shared link. */
    private fun firstUrl(input: String): String? =
        Regex("https?://[^\\s]+").find(input)?.value?.trimEnd('.', ',', ';', ')', ']', '"', '\'', '>', '!')

    fun extract(input: String, platform: Platform): Track {
        val url = firstUrl(input)
            ?: throw LocalizedException(R.string.err_link_read, fallback = "Unable to read the link.")
        if (!belongsTo(platform, url)) {
            throw LocalizedException(R.string.err_wrong_platform, fallback = "This link does not belong to the selected platform.")
        }
        // Validates the kind of link before any request is made
        val pageUrl = when (platform) {
            Platform.INSTAGRAM -> normalizeInstagramReel(url)
            Platform.FACEBOOK -> validateFacebook(url)
            else -> url
        }

        val store = mutableListOf<Cookie>()
        val jar = object : CookieJar {
            override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
                synchronized(store) { store.addAll(cookies) }
            }

            override fun loadForRequest(url: HttpUrl): List<Cookie> = synchronized(store) { store.filter { it.matches(url) } }
        }
        val client = DownloadManager.client.newBuilder().cookieJar(jar).followRedirects(true).build()

        var finalUrl = pageUrl
        val resolved: Found = try {
            // Mobile page first; Facebook sometimes only exposes the file addresses on the desktop page
            val agents = if (platform == Platform.FACEBOOK) listOf(USER_AGENT, DESKTOP_USER_AGENT) else listOf(USER_AGENT)
            var found: Found? = null
            for (agent in agents) {
                val page = fetchPage(client, pageUrl, agent, platform)
                finalUrl = page.finalUrl
                found = parse(platform, page.html)
                if (found?.videoUrl != null) break
            }
            if (found?.videoUrl == null && platform == Platform.INSTAGRAM) {
                // Instagram's public embed of a Reel (the same page used to embed it on other sites)
                val embed = fetchPage(client, pageUrl.trimEnd('/') + "/embed/", USER_AGENT, platform)
                val fromEmbed = parseInstagramEmbed(embed.html)
                if (fromEmbed?.videoUrl != null) {
                    found = fromEmbed.copy(title = found?.title.orEmpty().ifBlank { fromEmbed.title }, cover = found?.cover ?: fromEmbed.cover)
                }
            }
            found?.takeIf { it.videoUrl != null }
                ?: throw LocalizedException(
                    if (platform == Platform.INSTAGRAM || platform == Platform.FACEBOOK) R.string.err_video_not_public else R.string.err_video_not_found,
                    fallback = "Could not find a downloadable video at this link."
                )
        } catch (e: LocalizedException) {
            throw e
        } catch (e: IOException) {
            throw LocalizedException(R.string.err_network, fallback = "Could not reach the site. Check your internet connection.")
        }
        val videoUrl = resolved.videoUrl!!

        val cookieHeader = synchronized(store) { store.distinctBy { it.name }.joinToString("; ") { "${it.name}=${it.value}" } }
        val name = when (platform) {
            Platform.TIKTOK -> "TikTok"
            Platform.KWAI -> "Kwai"
            Platform.INSTAGRAM -> "Instagram Reel"
            else -> "Facebook video"
        }
        val title = resolved.title.replace(Regex("\\s+"), " ").trim().take(80).ifBlank { "$name" }

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

    private data class Page(val html: String, val finalUrl: String)

    private fun fetchPage(client: okhttp3.OkHttpClient, url: String, agent: String, platform: Platform): Page {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", agent)
            .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
            .header("Accept-Language", "en-US,en;q=0.9")
            .build()
        client.newCall(request).execute().use { response ->
            val finalUrl = response.request.url.toString()
            // A redirect to a login page means the content is not public: report it, never try to get around it
            if (platform == Platform.INSTAGRAM || platform == Platform.FACEBOOK) {
                val path = response.request.url.encodedPath.lowercase()
                if (path.contains("/accounts/login") || path.startsWith("/login")) {
                    throw LocalizedException(R.string.err_login_required, fallback = "This content needs a login.")
                }
            }
            if (!response.isSuccessful) {
                throw when (response.code) {
                    404, 410 -> LocalizedException(R.string.err_video_unavailable, fallback = "This video is unavailable or private.")
                    401, 403 -> if (platform == Platform.INSTAGRAM || platform == Platform.FACEBOOK) {
                        LocalizedException(R.string.err_login_required, fallback = "This content needs a login.")
                    } else {
                        LocalizedException(R.string.err_access_blocked, fallback = "The platform blocked access to this link.")
                    }
                    429 -> LocalizedException(R.string.err_rate_limited, fallback = "The platform is limiting requests right now.")
                    else -> LocalizedException(R.string.err_http, args = listOf(response.code), fallback = "HTTP ${response.code}")
                }
            }
            return Page(response.body.string(), finalUrl)
        }
    }

    // ---------- Instagram: Reels only ----------

    /** Accepts instagram.com/reel/CODE, /reels/CODE and /user/reel/CODE; rejects Stories and other post types. */
    private fun normalizeInstagramReel(url: String): String {
        val segments = url.toHttpUrlOrNull()?.pathSegments?.filter { it.isNotBlank() }.orEmpty()
        if (segments.firstOrNull() == "stories") {
            throw LocalizedException(R.string.err_instagram_story, fallback = "Stories are not supported.")
        }
        val index = segments.indexOfFirst { it == "reel" || it == "reels" }
        val code = if (index >= 0) segments.getOrNull(index + 1) else null
        if (code.isNullOrBlank() || !Regex("[\\w-]{5,}").matches(code)) {
            throw LocalizedException(R.string.err_instagram_not_reel, fallback = "This is not a Reel link.")
        }
        return "https://www.instagram.com/reel/$code/"
    }

    private fun parseInstagramEmbed(html: String): Found? {
        val video = Regex("\"video_url\"\\s*:\\s*\"([^\"]+)\"").find(html)?.groupValues?.get(1)
            ?.let(::unescape)?.takeIf { it.startsWith("http") }
            ?: Regex("<video[^>]+src=[\"']([^\"']+)[\"']", RegexOption.IGNORE_CASE).find(html)?.groupValues?.get(1)
                ?.let(::unescape)?.takeIf { it.startsWith("http") }
        val title = Regex("\"caption\"\\s*:\\s*\"([^\"]{0,200})", RegexOption.IGNORE_CASE).find(html)?.groupValues?.get(1)?.let(::unescape).orEmpty()
        return Found(video, title, "", null)
    }

    // ---------- Facebook ----------

    /** Accepts watch / reel / videos / share links and fb.watch short links. */
    private fun validateFacebook(url: String): String {
        val parsed = url.toHttpUrlOrNull()
        val host = parsed?.host?.lowercase().orEmpty()
        val path = parsed?.encodedPath?.lowercase().orEmpty()
        val ok = host == "fb.watch" || host.endsWith(".fb.watch") ||
            path.startsWith("/watch") || path.startsWith("/reel/") || path.contains("/videos/") ||
            path.startsWith("/share/v/") || path.startsWith("/share/r/") || path.startsWith("/video.php") ||
            path.startsWith("/share/")
        if (!ok) throw LocalizedException(R.string.err_facebook_invalid, fallback = "This does not look like a Facebook video link.")
        return url
    }

    private fun parseFacebook(html: String): Found? {
        // Best quality first (HD before SD); the page exposes the progressive files, with audio, that its player uses
        val keys = listOf(
            "browser_native_hd_url", "playable_url_quality_hd", "hd_src_no_ratelimit", "hd_src",
            "browser_native_sd_url", "playable_url", "sd_src_no_ratelimit", "sd_src"
        )
        val video = keys.firstNotNullOfOrNull { key ->
            Regex("\"$key\"\\s*:\\s*\"(https?:[^\"]+)\"").find(html)?.groupValues?.get(1)?.let(::unescape)
        } ?: parseGeneric(html)?.videoUrl
        fun meta(prop: String) = Regex("<meta[^>]+property=[\"']$prop[\"'][^>]+content=[\"']([^\"']*)[\"']", RegexOption.IGNORE_CASE)
            .find(html)?.groupValues?.get(1)?.let(::unescape)
        return Found(video, meta("og:title").orEmpty(), "", meta("og:image"))
    }

    // ---------- TikTok / generic ----------

    private fun parse(platform: Platform, html: String): Found? = when (platform) {
        Platform.TIKTOK -> {
            val tiktok = parseTikTok(html)
            if (tiktok?.videoUrl != null) tiktok else {
                val generic = parseGeneric(html)
                if (generic?.videoUrl != null) {
                    generic.copy(
                        title = tiktok?.title?.ifBlank { null } ?: generic.title,
                        author = tiktok?.author.orEmpty(),
                        cover = tiktok?.cover ?: generic.cover
                    )
                } else tiktok
            }
        }
        Platform.FACEBOOK -> parseFacebook(html)
        else -> parseGeneric(html)
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
            when (detail.optInt("statusCode", 0)) {
                0 -> Unit
                10222 -> throw LocalizedException(R.string.err_login_required, fallback = "This content is private.")
                else -> throw LocalizedException(R.string.err_video_unavailable, fallback = "This video is unavailable or private.")
            }
            val item = detail.optJSONObject("itemInfo")?.optJSONObject("itemStruct") ?: return null
            // Photo slideshows have no video file
            if (item.optJSONObject("imagePost") != null && item.optJSONObject("video")?.optString("playAddr").isNullOrBlank()) {
                throw LocalizedException(R.string.err_tiktok_photo, fallback = "This TikTok post is a photo slideshow, not a video.")
            }
            val video = item.optJSONObject("video")
            val candidates = buildList {
                add(video?.optString("playAddr").orEmpty())
                add(video?.optString("downloadAddr").orEmpty())
                val infos = video?.optJSONArray("bitrateInfo")
                if (infos != null) {
                    for (i in 0 until infos.length()) {
                        add(infos.optJSONObject(i)?.optJSONObject("PlayAddr")?.optJSONArray("UrlList")?.optString(0).orEmpty())
                    }
                }
            }
            val play = candidates.firstOrNull { it.isNotBlank() }.orEmpty()
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
        .replace(Regex("\\\\u([0-9a-fA-F]{4})")) { it.groupValues[1].toInt(16).toChar().toString() }
        .replace("\\/", "/").replace("&amp;", "&")
}
