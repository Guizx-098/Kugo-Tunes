package dev.sumanth.spd.service

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import dev.sumanth.spd.model.AppStatus

/**
 * The foreground session is the one the screen shows (and edits); the background ones keep downloading.
 * The properties below keep working on the foreground session, so the screens did not have to change.
 */
object DownloadState {
    var foreground by mutableStateOf(DownloadSession())
    val background = mutableStateListOf<DownloadSession>()

    var appStatus: AppStatus
        get() = foreground.appStatus
        set(value) { foreground.appStatus = value }
    var currentTrackIndex: Int
        get() = foreground.currentTrackIndex
        set(value) { foreground.currentTrackIndex = value }
    var playlistName: String
        get() = foreground.playlistName
        set(value) { foreground.playlistName = value }
    var platformFolder: String
        get() = foreground.platformFolder
        set(value) { foreground.platformFolder = value }
    val tracks get() = foreground.tracks

    fun all(): List<DownloadSession> = listOf(foreground) + background
    fun find(id: String): DownloadSession? = all().firstOrNull { it.id == id }
}
