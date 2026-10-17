package dev.sumanth.spd.utils

import android.content.Context
import dev.sumanth.spd.model.DownloadStatus
import dev.sumanth.spd.model.Platform
import dev.sumanth.spd.model.Track
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException

/**
 * Optional adapter for a Cobalt-compatible instance operated by the app owner/user.
 * It intentionally does not rely on an unapproved public relay. The endpoint is configured in Preferences.
 */
object CobaltApiExtractor {
    private val jsonType = "application/json; charset=utf-8".toMediaType()

    fun isConfigured(context: Context): Boolean = SharedPref(context).getCobaltApiUrl().isNotBlank()

    fun extract(context: Context, input: String, platform: Platform): Track {
        val base = SharedPref(context).getCobaltApiUrl().trimEnd('/')
        if (base.isBlank()) throw IllegalStateException("Cobalt API is not configured")
        val url = Regex("https?://[^\\s]+", RegexOption.IGNORE_CASE).find(input)?.value
            ?.trimEnd('.', ',', ';', ')', ']', '"', '\'', '>', '!')
            ?: throw IllegalArgumentException("Cole um link válido.")
        val payload = JSONObject().put("url", url).put("videoQuality", "1080").put("filenameStyle", "basic")
        val request = Request.Builder()
            .url("$base/")
            .header("Accept", "application/json")
            .post(payload.toString().toRequestBody(jsonType))
            .build()
        DownloadManager.client.newCall(request).execute().use { response ->
            val body = response.body.string()
            if (!response.isSuccessful) throw IOException("Cobalt API respondeu HTTP ${response.code}")
            val result = try { JSONObject(body) } catch (_: Exception) { throw IOException("Resposta inválida do Cobalt API") }
            when (result.optString("status")) {
                "tunnel", "redirect" -> {
                    val mediaUrl = result.optString("url")
                    if (!mediaUrl.startsWith("https://") && !mediaUrl.startsWith("http://")) {
                        throw IOException("A API não retornou um endereço de mídia válido")
                    }
                    val service = when (platform) {
                        Platform.INSTAGRAM -> "Instagram"
                        Platform.FACEBOOK -> "Facebook"
                        Platform.TIKTOK -> "TikTok"
                        Platform.KWAI -> "Kwai"
                        else -> "Vídeo"
                    }
                    return Track(
                        title = result.optJSONObject("metadata")?.optString("title")?.takeIf { it.isNotBlank() } ?: "$service download",
                        artist = service,
                        status = DownloadStatus.IDLE,
                        imageUrl = result.optJSONObject("metadata")?.optString("thumbnail")?.takeIf { it.startsWith("http") },
                        isVideo = true,
                        directUrl = mediaUrl,
                        referer = url
                    )
                }
                "error" -> {
                    val code = result.optJSONObject("error")?.optString("code") ?: "unknown"
                    throw IOException("A plataforma/API recusou o link ($code). Verifique se o conteúdo é público e acessível.")
                }
                "picker" -> throw IOException("Esta publicação contém várias mídias; a seleção múltipla ainda não é compatível.")
                else -> throw IOException("Resposta inesperada do Cobalt API.")
            }
        }
    }
}
