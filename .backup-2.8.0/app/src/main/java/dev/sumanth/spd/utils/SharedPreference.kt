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

    /** Folder chosen by the user, or null when the default folder (Download/Kugo Tunes) is used. */
    fun getDownloadPath(): String? = sharedPref.getString(KEY_DOWNLOAD_PATH, null)

    /** False when the chosen folder lost its permission or was removed: the user must pick another one. */
    fun isCustomFolderAccessible(): Boolean {
        val path = getDownloadPath() ?: return true
        return try {
            val uri = path.toUri()
            context.contentResolver.persistedUriPermissions.any {
                it.uri == uri && it.isWritePermission
            }
        } catch (e: Exception) {
            false
        }
    }

    fun getWifiOnly(): Boolean = sharedPref.getBoolean(KEY_WIFI_ONLY, false)

    fun storeWifiOnly(value: Boolean) {
        sharedPref.edit { putBoolean(KEY_WIFI_ONLY, value) }
    }

    fun clearDownloadPath() {
        sharedPref.edit { remove(KEY_DOWNLOAD_PATH) }
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
        private const val KEY_WIFI_ONLY = "wifi_only"
        private const val KEY_LANGUAGE = "language"
        private const val KEY_SKIPPED_UPDATE = "skipped_update_version"
    }
}