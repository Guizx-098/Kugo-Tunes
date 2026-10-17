package dev.sumanth.spd.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.sumanth.spd.service.UpdateError
import dev.sumanth.spd.service.UpdateStage
import dev.sumanth.spd.service.UpdateState
import dev.sumanth.spd.utils.ApkInstaller
import dev.sumanth.spd.utils.NoReleaseException
import dev.sumanth.spd.utils.SharedPref
import dev.sumanth.spd.utils.UpdateChecker
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class UpdaterViewModel(application: Application) : AndroidViewModel(application) {

    private val sharedPref = SharedPref(application)
    private var job: Job? = null

    val installedVersion: String by lazy {
        runCatching {
            application.packageManager.getPackageInfo(application.packageName, 0).versionName
        }.getOrNull() ?: "0"
    }

    init {
        // Old/partial update files are not needed anymore
        viewModelScope.launch(Dispatchers.IO) { ApkInstaller.cleanup(application) }
    }

    /** [manual] = pressed "Check for updates": always reports the result, ignores "Not now". */
    fun checkForUpdate(manual: Boolean) {
        val busy = UpdateState.stage == UpdateStage.DOWNLOADING || UpdateState.stage == UpdateStage.INSTALLING
        if (busy || job?.isActive == true) return

        if (manual) UpdateState.stage = UpdateStage.CHECKING
        job = viewModelScope.launch {
            try {
                val info = withContext(Dispatchers.IO) { UpdateChecker.fetchLatest() }
                if (!UpdateChecker.isNewer(info.version, installedVersion)) {
                    UpdateState.stage = if (manual) UpdateStage.UP_TO_DATE else UpdateStage.IDLE
                } else if (!manual && sharedPref.getSkippedUpdateVersion() == info.version) {
                    UpdateState.stage = UpdateStage.IDLE
                } else if (info.apkUrl == null) {
                    if (manual) setCheckFailed(UpdateError.NO_APK) else UpdateState.stage = UpdateStage.IDLE
                } else {
                    UpdateState.release = info
                    UpdateState.stage = UpdateStage.AVAILABLE
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (manual) {
                    setCheckFailed(if (e is NoReleaseException) UpdateError.NO_RELEASE else UpdateError.NETWORK)
                } else {
                    UpdateState.stage = UpdateStage.IDLE
                }
            }
        }
    }

    private fun setCheckFailed(error: UpdateError) {
        UpdateState.error = error
        UpdateState.stage = UpdateStage.CHECK_FAILED
    }

    fun downloadApk() {
        val info = UpdateState.release ?: return
        if (job?.isActive == true) return
        val app = getApplication<Application>()

        job = viewModelScope.launch {
            UpdateState.progress = 0f
            UpdateState.stage = UpdateStage.DOWNLOADING
            try {
                val ready = withContext(Dispatchers.IO) { ApkInstaller.existing(app, info) }
                val file = ready ?: ApkInstaller.download(app, info) { done, total ->
                    UpdateState.progress = if (total > 0) (done.toFloat() / total).coerceIn(0f, 1f) else -1f
                }
                val valid = withContext(Dispatchers.IO) { ApkInstaller.verify(app, file, info.apkSize) }
                if (!valid) {
                    file.delete()
                    UpdateState.stage = UpdateStage.INVALID_APK
                } else {
                    UpdateState.apkFile = file
                    UpdateState.stage = UpdateStage.DOWNLOADED
                }
            } catch (e: CancellationException) {
                UpdateState.stage = UpdateStage.AVAILABLE
                throw e
            } catch (e: Exception) {
                UpdateState.stage = UpdateStage.DOWNLOAD_FAILED
            }
        }
    }

    fun cancelDownload() {
        job?.cancel()
    }

    fun installApk() {
        val app = getApplication<Application>()
        val file = UpdateState.apkFile
        if (file == null || !file.exists()) {
            UpdateState.stage = UpdateStage.AVAILABLE
            return
        }
        if (!ApkInstaller.canInstall(app)) {
            UpdateState.stage = UpdateStage.NEEDS_PERMISSION
            return
        }
        UpdateState.stage = UpdateStage.INSTALLING
        viewModelScope.launch(Dispatchers.IO) {
            try {
                ApkInstaller.install(app, file)
            } catch (e: Exception) {
                UpdateState.error = UpdateError.GENERIC
                UpdateState.errorDetail = e.message
                UpdateState.stage = UpdateStage.INSTALL_ERROR
            }
        }
    }

    /** "Not now": remembers this version so the same notice is not shown again at every launch. */
    fun notNow() {
        if (UpdateState.stage == UpdateStage.AVAILABLE) {
            UpdateState.release?.let { sharedPref.storeSkippedUpdateVersion(it.version) }
        }
        UpdateState.stage = UpdateStage.IDLE
    }

    fun dismiss() {
        UpdateState.stage = UpdateStage.IDLE
    }

    /** Called when the app comes back to the foreground (e.g. after the install-permission screen). */
    fun onResume() {
        if (UpdateState.stage == UpdateStage.NEEDS_PERMISSION && ApkInstaller.canInstall(getApplication())) {
            UpdateState.stage = UpdateStage.DOWNLOADED
        }
    }

    fun openInstallSettings() {
        getApplication<Application>().startActivity(ApkInstaller.settingsIntent(getApplication()))
    }
}
