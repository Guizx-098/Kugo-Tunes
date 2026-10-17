package dev.sumanth.spd.utils

import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException

data class ReleaseInfo(
    val tag: String,
    val version: String,
    val apkUrl: String?,
    val apkName: String?,
    val apkSize: Long,
    val notes: String
)

class NoReleaseException : IOException("No release has been published")

/** Reads the latest published GitHub Release of this project. */
object UpdateChecker {
    private const val OWNER = "Guizx-098"
    private const val REPO = "spd-turbo"
    private const val LATEST_URL = "https://api.github.com/repos/$OWNER/$REPO/releases/latest"

    fun fetchLatest(): ReleaseInfo {
        val request = Request.Builder()
            .url(LATEST_URL)
            .header("Accept", "application/vnd.github+json")
            .header("User-Agent", "SPD-Turbo")
            .build()

        DownloadManager.client.newCall(request).execute().use { response ->
            if (response.code == 404) throw NoReleaseException()
            if (!response.isSuccessful) throw IOException("GitHub responded with ${response.code}")

            val json = JSONObject(response.body.string())
            val tag = json.optString("tag_name")
            if (tag.isBlank()) throw NoReleaseException()

            val assets = json.optJSONArray("assets") ?: JSONArray()
            var apk: JSONObject? = null
            for (i in 0 until assets.length()) {
                val asset = assets.getJSONObject(i)
                if (asset.optString("name").endsWith(".apk", ignoreCase = true)) {
                    apk = asset
                    break
                }
            }

            return ReleaseInfo(
                tag = tag,
                version = normalize(tag),
                apkUrl = apk?.optString("browser_download_url")?.takeIf { it.isNotBlank() },
                apkName = apk?.optString("name"),
                apkSize = apk?.optLong("size", 0L) ?: 0L,
                notes = json.optString("body", "").trim()
            )
        }
    }

    /** "v2.1.0" -> "2.1.0" */
    fun normalize(version: String): String = numbers(version).joinToString(".")

    fun isNewer(remote: String, local: String): Boolean {
        val a = numbers(remote)
        val b = numbers(local)
        for (i in 0 until maxOf(a.size, b.size)) {
            val x = a.getOrElse(i) { 0 }
            val y = b.getOrElse(i) { 0 }
            if (x != y) return x > y
        }
        return false
    }

    private fun numbers(version: String): List<Int> =
        Regex("\\d+").findAll(version).map { it.value.toIntOrNull() ?: 0 }.toList()
}
