package dev.sumanth.spd.utils

import android.content.Context
import android.net.Uri
import androidx.core.content.edit
import androidx.core.net.toUri
import java.io.File

class SharedPref(context: Context) {

    private val context = context.applicationContext
    private val sharedPref = this.context.getSharedPreferences(
        PREFS_NAME,
        Context.MODE_PRIVATE
    )

    fun getDownloadPath(): String? {
        val path = sharedPref.getString(KEY_DOWNLOAD_PATH, null) ?: return null
        try {
            val uri = path.toUri()
            val hasPermission = context.contentResolver.persistedUriPermissions.any {
                it.uri == uri && (it.isWritePermission || it.isReadPermission)
            }
            if (!hasPermission) {
                sharedPref.edit { remove(KEY_DOWNLOAD_PATH) }
                return null
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return path
    }

    fun storeDownloadPath(path: String) {
        sharedPref.edit {
            putString(KEY_DOWNLOAD_PATH, path)
        }
    }

    fun getAutoUpdateCheck(): Boolean {
        return sharedPref.getBoolean(KEY_AUTO_UPDATE_CHECK, true)
    }

    fun storeAutoUpdateCheck(enabled: Boolean) {
        sharedPref.edit { 
            putBoolean(KEY_AUTO_UPDATE_CHECK, enabled)
        }
    }

    fun getCreateSubfolder(): Boolean {
        return sharedPref.getBoolean(KEY_CREATE_SUBFOLDER, true)
    }

    fun storeCreateSubfolder(enabled: Boolean) {
        sharedPref.edit {
            putBoolean(KEY_CREATE_SUBFOLDER, enabled)
        }
    }

    fun getConcurrentDownloads(): Int {
        return sharedPref.getInt(KEY_CONCURRENT_DOWNLOADS, 1).coerceIn(1, 4)
    }

    fun storeConcurrentDownloads(count: Int) {
        sharedPref.edit {
            putInt(KEY_CONCURRENT_DOWNLOADS, count.coerceIn(1, 4))
        }
    }

    /** Optional self-hosted Cobalt-compatible API base URL. Empty means use the built-in extractor. */
    fun getCobaltApiUrl(): String = sharedPref.getString(KEY_COBALT_API_URL, "")?.trim().orEmpty()

    fun storeCobaltApiUrl(url: String) {
        sharedPref.edit { putString(KEY_COBALT_API_URL, url.trim().trimEnd('/')) }
    }

    fun getLanguage(): String = readLanguage(context)

    fun storeLanguage(tag: String) {
        sharedPref.edit {
            putString(KEY_LANGUAGE, tag)
        }
    }

    fun getSkippedUpdateVersion(): String? {
        return sharedPref.getString(KEY_SKIPPED_UPDATE, null)
    }

    fun storeSkippedUpdateVersion(version: String) {
        sharedPref.edit {
            putString(KEY_SKIPPED_UPDATE, version)
        }
    }

    companion object {
        const val LANG_EN = "en"
        const val LANG_PT_BR = "pt-BR"

        /** Reads the language straight from the preferences file (usable from attachBaseContext). */
        fun readLanguage(context: Context): String {
            return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getString(KEY_LANGUAGE, LANG_EN) ?: LANG_EN
        }

        private const val PREFS_NAME = "spd_settings"
        private const val KEY_DOWNLOAD_PATH = "download_path"
        private const val KEY_AUTO_UPDATE_CHECK = "auto_update_check"
        private const val KEY_CREATE_SUBFOLDER = "create_subfolder"
        private const val KEY_CONCURRENT_DOWNLOADS = "concurrent_downloads"
        private const val KEY_LANGUAGE = "language"
        private const val KEY_COBALT_API_URL = "cobalt_api_url"
        private const val KEY_SKIPPED_UPDATE = "skipped_update_version"
    }
}