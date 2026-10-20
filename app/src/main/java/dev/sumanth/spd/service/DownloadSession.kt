package dev.sumanth.spd.service

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import dev.sumanth.spd.model.AppStatus
import dev.sumanth.spd.model.DownloadStatus
import dev.sumanth.spd.model.Track
import dev.sumanth.spd.utils.PlatformFolders
import kotlinx.coroutines.Job
import java.util.UUID

/**
 * One list being downloaded (a playlist, a video...). Several sessions can run at the same time: one is on screen
 * (the foreground one) and the others keep downloading in the background. Everything a session needs to run again
 * later (also after the app was closed) is kept in the session itself.
 */
class DownloadSession(val id: String = UUID.randomUUID().toString()) {
    var appStatus by mutableStateOf(AppStatus.IDLE)
    var currentTrackIndex by mutableIntStateOf(-1)
    var playlistName by mutableStateOf("")
    val tracks = mutableStateListOf<Track>()

    // Folder of the source platform inside the download folder (Spotify, YouTube, TikTok...)
    var platformFolder: String = PlatformFolders.OTHER

    // How it runs
    var downloadPath: String = ""
    var convertToMp3: Boolean = false
    var createSubfolder: Boolean = true
    var concurrency: Int = 1

    // 0 = running, 1 = waiting for the internet, 2 = waiting for Wi-Fi
    var waitingFor by mutableIntStateOf(0)
    var lastTitle by mutableStateOf("")

    // Loaded from disk after the app was closed: files already saved are detected in the folder before downloading again
    var restoredFromDisk: Boolean = false

    // The user removed it: nothing about it is saved again
    @Volatile var discarded: Boolean = false
    @Volatile var lastSaved: Long = 0L
    var job: Job? = null

    fun selectedCount(): Int = tracks.count { it.isSelected }
    fun doneCount(): Int = tracks.count { it.isSelected && it.status == DownloadStatus.COMPLETE }
    fun pendingCount(): Int = tracks.count { it.isSelected && it.status != DownloadStatus.COMPLETE }

    fun label(): String =
        if (playlistName.isBlank()) platformFolder else "$platformFolder · $playlistName"
}
