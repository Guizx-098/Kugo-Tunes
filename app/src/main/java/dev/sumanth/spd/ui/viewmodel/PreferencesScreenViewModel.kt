package dev.sumanth.spd.ui.viewmodel

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.UriHandler
import androidx.lifecycle.AndroidViewModel
import dev.sumanth.spd.utils.SharedPref
import java.io.File

class PreferencesScreenViewModel(application: Application) : AndroidViewModel(application) {
    val sharedPref = SharedPref(application)
    var downloadPath by mutableStateOf<String?>(sharedPref.getDownloadPath())
    var autoUpdateCheck by mutableStateOf(sharedPref.getAutoUpdateCheck())
    var createSubfolder by mutableStateOf(sharedPref.getCreateSubfolder())
    var concurrentDownloads by mutableIntStateOf(sharedPref.getConcurrentDownloads())
    var language by mutableStateOf(sharedPref.getLanguage())

    val developerName = "Guizx-098"
    private val githubUrl = "https://github.com/Guizx-098/spd-turbo"
    private val profileUrl = "https://github.com/Guizx-098"
    val packageInfo = application.packageManager.getPackageInfo(application.packageName, 0)

    fun openGitHub(uriHandler: UriHandler) {
        uriHandler.openUri(githubUrl)
    }

    fun openProfile(uriHandler: UriHandler) {
        uriHandler.openUri(profileUrl)
    }

    fun handleFolderSelection(uri: Uri?) {
        uri?.let { folderUri ->
            val context = getApplication<Application>()
            val takeFlags: Int = Intent.FLAG_GRANT_READ_URI_PERMISSION or
                    Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            context.contentResolver.takePersistableUriPermission(folderUri, takeFlags)

            val uriString = folderUri.toString()
            downloadPath = uriString
            sharedPref.storeDownloadPath(uriString)
        }
    }

    /** Readable folder path, or null when no folder was chosen yet. */
    fun getDisplayPath(): String? {
        val current = downloadPath ?: return null
        val path = Uri.parse(current).path ?: return current
        if (path.contains(":")) {
            return path.split(":").last()
        }
        return path
    }
}
