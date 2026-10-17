package dev.sumanth.spd.ui.viewmodel

import android.app.Application
import android.content.Intent
import android.widget.Toast
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.sumanth.spd.model.AppStatus
import dev.sumanth.spd.service.DownloadService
import dev.sumanth.spd.service.DownloadState
import dev.sumanth.spd.utils.SharedPref
import dev.sumanth.spd.model.Track
import dev.sumanth.spd.R
import dev.sumanth.spd.utils.LocaleHelper
import dev.sumanth.spd.utils.LocalizedException
import dev.sumanth.spd.utils.SpotifyScraper
import dev.sumanth.spd.utils.YoutubeScraper
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class HomeScreenViewModel(application: Application) : AndroidViewModel(application) {

    private val sharedPref = SharedPref(application)
    private val spotifyScraper = SpotifyScraper()
    private var scrapingJob: Job? = null
    
    var appStatus: AppStatus
        get() = DownloadState.appStatus
        set(value) { DownloadState.appStatus = value }
        
    var spotifyLink by mutableStateOf("")
    var convertToMp3 by mutableStateOf(false)
    var totalTracksToScrape by mutableIntStateOf(0)
    
    var currentTrack: Int
        get() = DownloadState.currentTrackIndex
        set(value) { DownloadState.currentTrackIndex = value }
        
    val tracks get() = DownloadState.tracks

    private fun toast(resId: Int) {
        Toast.makeText(getApplication(), LocaleHelper.string(getApplication(), resId), Toast.LENGTH_SHORT).show()
    }

    fun startScraping() {
        if (spotifyLink.isBlank()) return toast(R.string.toast_invalid_link)
        val isYoutube = YoutubeScraper.isYoutubeLink(spotifyLink)
        if (!isYoutube && spotifyLink.contains("?")) spotifyLink = spotifyLink.split("?")[0]
        
        appStatus = AppStatus.SCRAPING
        tracks.clear()
        totalTracksToScrape = 0
        
        scrapingJob = viewModelScope.launch {
            try {
                val onProgress: suspend (List<Track>, Int) -> Unit = { fetchedTracks, total ->
                    withContext(Dispatchers.Main) {
                        totalTracksToScrape = total
                        if (fetchedTracks.size >= tracks.size) {
                            tracks.addAll(fetchedTracks.subList(tracks.size, fetchedTracks.size))
                        } else {
                            tracks.clear()
                            tracks.addAll(fetchedTracks)
                        }
                    }
                }
                if (isYoutube) {
                    val youtubeResult = withContext(Dispatchers.IO) {
                        YoutubeScraper.scrape(spotifyLink, onProgress)
                    }
                    totalTracksToScrape = youtubeResult.tracks.size
                    tracks.clear()
                    tracks.addAll(youtubeResult.tracks)
                    DownloadState.playlistName = youtubeResult.name
                } else {
                    val scrapeResult = withContext(Dispatchers.IO) {
                        spotifyScraper.scrapePlaylist(spotifyLink, onProgress)
                    }
                    totalTracksToScrape = scrapeResult.totalTracks
                    tracks.clear()
                    tracks.addAll(scrapeResult.tracks)
                    DownloadState.playlistName = scrapeResult.metadata.name
                }
                appStatus = AppStatus.SCRAPING_COMPLETE
            } catch (e: Exception) {
                if (e is CancellationException) return@launch
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    val app = getApplication<Application>()
                    val message = when {
                        e is LocalizedException -> LocaleHelper.string(app, e.resId, *e.args.toTypedArray())
                        e is IllegalArgumentException && e.message != null -> e.message!!
                        else -> LocaleHelper.string(app, R.string.toast_scrape_failed, e.message ?: "")
                    }
                    Toast.makeText(app, message, Toast.LENGTH_LONG).show()
                    appStatus = AppStatus.IDLE
                }
            }
        }
    }

    fun downloadPlaylist() {
        if (tracks.isEmpty()) return toast(R.string.toast_playlist_empty)
        if (tracks.none { it.isSelected }) return toast(R.string.toast_no_selection)
        val downloadPath = sharedPref.getDownloadPath()
        if (downloadPath == null) return toast(R.string.toast_select_folder)

        val createSubfolder = sharedPref.getCreateSubfolder()
        val playlistName = DownloadState.playlistName

        val intent = Intent(getApplication(), DownloadService::class.java).apply {
            action = DownloadService.ACTION_START
            putExtra(DownloadService.EXTRA_DOWNLOAD_PATH, downloadPath)
            putExtra(DownloadService.EXTRA_CONVERT_TO_MP3, convertToMp3)
            putExtra(DownloadService.EXTRA_CREATE_SUBFOLDER, createSubfolder)
            putExtra(DownloadService.EXTRA_PLAYLIST_NAME, playlistName)
            putExtra(DownloadService.EXTRA_CONCURRENT_DOWNLOADS, sharedPref.getConcurrentDownloads())
        }
        
        getApplication<Application>().startService(intent)
    }

    fun toggleTrackSelection(index: Int) {
        if (index in tracks.indices) {
            val track = tracks[index]
            tracks[index] = track.copy(isSelected = !track.isSelected)
        }
    }

    fun selectAllTracks(selected: Boolean) {
        for (i in tracks.indices) {
            tracks[i] = tracks[i].copy(isSelected = selected)
        }
    }

    fun cancelDownload() {
        val intent = Intent(getApplication(), DownloadService::class.java).apply {
            action = DownloadService.ACTION_STOP
        }
        getApplication<Application>().startService(intent)
    }

    fun cancelScraping() {
        scrapingJob?.cancel()
        appStatus = AppStatus.IDLE
        tracks.clear()
    }

    fun reset() {
        tracks.clear()
        spotifyLink = ""
        appStatus = AppStatus.IDLE
        currentTrack = -1
    }
    
    fun getFailedDownloadsCount(): Int = tracks.count { it.status == dev.sumanth.spd.model.DownloadStatus.FAILED }
    fun getDownloadedCount(): Int = tracks.count { it.status == dev.sumanth.spd.model.DownloadStatus.COMPLETE }
    fun getSelectedTracksCount(): Int = tracks.count { it.isSelected }
}
