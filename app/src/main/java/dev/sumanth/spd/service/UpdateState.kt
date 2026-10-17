package dev.sumanth.spd.service

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import dev.sumanth.spd.utils.ReleaseInfo
import java.io.File

enum class UpdateStage {
    IDLE,
    CHECKING,
    UP_TO_DATE,
    CHECK_FAILED,
    AVAILABLE,
    DOWNLOADING,
    DOWNLOAD_FAILED,
    INVALID_APK,
    DOWNLOADED,
    NEEDS_PERMISSION,
    INSTALLING,
    INSTALL_CANCELLED,
    INSTALL_ERROR,
    INSTALLED
}

enum class UpdateError { NONE, NETWORK, NO_RELEASE, NO_APK, CONFLICT, GENERIC }

/** Single source of truth for the update flow (also written by the install result receiver). */
object UpdateState {
    var stage by mutableStateOf(UpdateStage.IDLE)
    var release by mutableStateOf<ReleaseInfo?>(null)
    /** 0..1, or -1 when the size is unknown */
    var progress by mutableFloatStateOf(0f)
    var error by mutableStateOf(UpdateError.NONE)
    var errorDetail by mutableStateOf<String?>(null)
    var apkFile: File? = null
}
