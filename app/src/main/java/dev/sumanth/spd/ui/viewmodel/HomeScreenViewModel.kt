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
import dev.sumanth.spd.model.AudioFormat
import dev.sumanth.spd.model.ContentType
import dev.sumanth.spd.model.Platform
import dev.sumanth.spd.service.DownloadService
import dev.sumanth.spd.service.DownloadSession
import dev.sumanth.spd.utils.SessionStore
import dev.sumanth.spd.service.DownloadState
import dev.sumanth.spd.utils.SharedPref
import dev.sumanth.spd.model.Track
import dev.sumanth.spd.R
import dev.sumanth.spd.utils.LocaleHelper
import dev.sumanth.spd.utils.PlatformFolders
import dev.sumanth.spd.utils.LocalizedException
import dev.sumanth.spd.utils.SocialVideoExtractor
import dev.sumanth.spd.utils.YtDlp
import dev.sumanth.spd.utils.SpotifyScraper
import dev.sumanth.spd.utils.YoutubeScraper
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class HomeScreenViewModel(application: Application) : AndroidViewModel(application) {

    private companion object {
        /** Single-video platforms handled by SocialVideoExtractor. */
        val SOCIAL_PLATFORMS = setOf(Platform.TIKTOK, Platform.KWAI, Platform.INSTAGRAM, Platform.FACEBOOK)
    }

    private val sharedPref = SharedPref(application)
    private val spotifyScraper = SpotifyScraper()
    private var scrapingJob: Job? = null
    
    var appStatus: AppStatus
        get() = DownloadState.appStatus
        set(value) { DownloadState.appStatus = value }
        
    var spotifyLink by mutableStateOf("")

    // New home flow: platform -> (YouTube only) Video / Music -> link (+ format for music)
    var platform by mutableStateOf<Platform?>(null)
    var contentType by mutableStateOf<ContentType?>(null)
    var audioFormat by mutableStateOf(AudioFormat.MP3)
    var totalTracksToScrape by mutableIntStateOf(0)
    
    var currentTrack: Int
        get() = DownloadState.currentTrackIndex
        set(value) { DownloadState.currentTrackIndex = value }
        
    val tracks get() = DownloadState.tracks

    // A supported link found in the clipboard: offered once ("Download this link?")
    var clipboardSuggestion by mutableStateOf<String?>(null)
    private var lastClipboardHandled: String? = null

    fun onClipboardText(text: String?) {
        if (text.isNullOrBlank()) return
        val url = Regex("https?://\\S+").find(text)?.value ?: return
        if (url == lastClipboardHandled || url == clipboardSuggestion) return
        if (detectPlatform(url) == null) return
        clipboardSuggestion = url
    }

    fun acceptClipboard() {
        val url = clipboardSuggestion ?: return
        lastClipboardHandled = url
        clipboardSuggestion = null
        startFromSharedLink(url)
    }

    fun dismissClipboard() {
        lastClipboardHandled = clipboardSuggestion
        clipboardSuggestion = null
    }

    init {
        // Lists left unfinished when the app was closed show up as "paused" in the background list
        viewModelScope.launch(Dispatchers.IO) {
            val restored = SessionStore.loadAll(getApplication())
            withContext(Dispatchers.Main) {
                restored.filter { DownloadState.find(it.id) == null }.forEach { DownloadState.background.add(it) }
            }
        }
    }

    private fun toast(resId: Int) {
        Toast.makeText(getApplication(), LocaleHelper.string(getApplication(), resId), Toast.LENGTH_SHORT).show()
    }

    fun startScraping() {
        if (spotifyLink.isBlank()) return toast(R.string.toast_invalid_link)
        val currentPlatform = platform ?: Platform.SPOTIFY
        DownloadState.platformFolder = PlatformFolders.of(currentPlatform, contentType == ContentType.MUSIC)
        if (currentPlatform == Platform.SPOTIFY && spotifyLink.contains("?")) spotifyLink = spotifyLink.split("?")[0]
        
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
                if (currentPlatform == Platform.YOUTUBE) {
                    val youtubeResult = withContext(Dispatchers.IO) {
                        YoutubeScraper.scrape(spotifyLink, contentType == ContentType.VIDEO, onProgress)
                    }
                    totalTracksToScrape = youtubeResult.tracks.size
                    tracks.clear()
                    tracks.addAll(youtubeResult.tracks)
                    DownloadState.playlistName = youtubeResult.name
                } else if (currentPlatform in SOCIAL_PLATFORMS) {
                    val track = withContext(Dispatchers.IO) {
                        val app = getApplication<Application>()
                        if (currentPlatform != Platform.KWAI && YtDlp.isReady(app)) {
                            // TikTok / Instagram / Facebook: yt-dlp (kept up to date by its community)
                            YtDlp.resolveTrack(app, spotifyLink, currentPlatform)
                        } else {
                            SocialVideoExtractor.extract(spotifyLink, currentPlatform)
                        }
                    }
                    totalTracksToScrape = 1
                    tracks.clear()
                    tracks.add(track)
                    DownloadState.playlistName = ""
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
        // null = default folder (Download/Kugo Tunes); a chosen folder that became inaccessible is never replaced silently
        val downloadPath = sharedPref.getDownloadPath()
        if (downloadPath != null && !sharedPref.isCustomFolderAccessible()) return toast(R.string.toast_folder_lost)

        // The session carries everything it needs to run, and to be resumed later (even after the app was closed)
        val session = DownloadState.foreground
        session.downloadPath = downloadPath.orEmpty()
        session.convertToMp3 = effectiveConvertToMp3()
        session.createSubfolder = sharedPref.getCreateSubfolder()
        session.concurrency = sharedPref.getConcurrentDownloads()
        startSessionService(session)
    }

    private fun startSessionService(session: DownloadSession) {
        val intent = Intent(getApplication(), DownloadService::class.java).apply {
            action = DownloadService.ACTION_START
            putExtra(DownloadService.EXTRA_SESSION_ID, session.id)
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

    /** Pause: stops the list on screen, keeps what was already downloaded; "Resume" continues with the rest. */
    fun cancelDownload() = pauseSession(DownloadState.foreground)

    fun pauseSession(session: DownloadSession) {
        val intent = Intent(getApplication(), DownloadService::class.java).apply {
            action = DownloadService.ACTION_STOP
            putExtra(DownloadService.EXTRA_SESSION_ID, session.id)
        }
        getApplication<Application>().startService(intent)
    }

    fun resumeSession(session: DownloadSession) = startSessionService(session)

    /** Keeps the list downloading in the background and returns to the platform list to start another one. */
    fun moveToBackground() {
        val session = DownloadState.foreground
        if (session.tracks.isNotEmpty()) DownloadState.background.add(session)
        DownloadState.foreground = DownloadSession()
        platform = null
        contentType = null
        spotifyLink = ""
    }

    /** Brings a background list to the screen. What was on screen keeps going in the background when it still has work. */
    fun openBackground(session: DownloadSession) {
        val current = DownloadState.foreground
        DownloadState.background.remove(session)
        val stillHasWork = current.appStatus == AppStatus.DOWNLOADING ||
            (current.appStatus == AppStatus.SCRAPING_COMPLETE && current.doneCount() > 0 && current.pendingCount() > 0)
        if (stillHasWork) DownloadState.background.add(current)
        DownloadState.foreground = session
        platform = null
        contentType = null
        spotifyLink = ""
    }

    /** Removes a background list (stops it first when it is downloading) and forgets it. */
    fun removeSession(session: DownloadSession) {
        session.discarded = true
        if (session.appStatus == AppStatus.DOWNLOADING) pauseSession(session)
        DownloadState.background.remove(session)
        SessionStore.delete(getApplication(), session.id)
    }

    /** Back from a list that is not downloading: a partly downloaded list is kept in the background, any other is closed. */
    fun leaveQueue() {
        val session = DownloadState.foreground
        if (session.appStatus == AppStatus.SCRAPING_COMPLETE && session.doneCount() > 0 && session.pendingCount() > 0) {
            moveToBackground()
        } else {
            reset()
        }
    }

    val waitingFor: Int get() = DownloadState.foreground.waitingFor
    val backgroundSessions get() = DownloadState.background

    fun cancelScraping() {
        scrapingJob?.cancel()
        appStatus = AppStatus.IDLE
        tracks.clear()
    }

    /** Music on YouTube: the chosen format decides (MP3 = convert, M4A = keep). Videos are never converted. */
    private fun effectiveConvertToMp3(): Boolean = when (platform) {
        Platform.YOUTUBE -> contentType == ContentType.MUSIC && audioFormat == AudioFormat.MP3
        Platform.TIKTOK, Platform.KWAI, Platform.INSTAGRAM, Platform.FACEBOOK -> false
        // Spotify uses the same MP3 / M4A choice as YouTube Music
        else -> audioFormat == AudioFormat.MP3
    }

    fun selectPlatform(selected: Platform) {
        platform = selected
        contentType = null
        spotifyLink = ""
    }

    /** One step back in the home flow (link -> type -> platform list). */
    fun back() {
        if (platform == Platform.YOUTUBE && contentType != null) {
            contentType = null
        } else {
            platform = null
            contentType = null
        }
        spotifyLink = ""
    }

    /** Which platform a link belongs to (null = not a link this app downloads). */
    fun detectPlatform(url: String): Platform? {
        val link = url.lowercase()
        return when {
            link.contains("spotify") -> Platform.SPOTIFY
            link.contains("tiktok.com") -> Platform.TIKTOK
            link.contains("kwai") || link.contains("kw.ai") -> Platform.KWAI
            link.contains("instagram.com") || link.contains("instagr.am") -> Platform.INSTAGRAM
            link.contains("facebook.com") || link.contains("fb.watch") || link.contains("fb.com") -> Platform.FACEBOOK
            link.contains("youtube.com") || link.contains("youtu.be") -> Platform.YOUTUBE
            else -> null
        }
    }

    /** A link copied in another app (or shared to this one) already tells which platform to use. */
    fun startFromSharedLink(url: String) {
        val detected = detectPlatform(url)
        if (detected == null || detected == Platform.YOUTUBE) {
            selectPlatform(Platform.YOUTUBE)
            contentType = if (url.lowercase().contains("music.youtube.com")) ContentType.MUSIC else ContentType.VIDEO
        } else {
            selectPlatform(detected)
        }
        spotifyLink = url
        startScraping()
    }

    /** Chosen audio track of a YouTube video that offers several (no original track could be identified). */
    fun selectAudioTrack(index: Int, trackId: String) {
        if (index in tracks.indices) tracks[index] = tracks[index].copy(audioTrackId = trackId)
    }

    /** After a finished download: clears the finished queue and returns to the platform list. */
    fun backToPlatforms() {
        reset()
        platform = null
        contentType = null
    }

    fun reset() {
        // The list is closed: its saved progress is not needed anymore
        SessionStore.delete(getApplication(), DownloadState.foreground.id)
        tracks.clear()
        spotifyLink = ""
        appStatus = AppStatus.IDLE
        currentTrack = -1
    }
    
    fun getFailedDownloadsCount(): Int = tracks.count { it.status == dev.sumanth.spd.model.DownloadStatus.FAILED }
    fun getDownloadedCount(): Int = tracks.count { it.status == dev.sumanth.spd.model.DownloadStatus.COMPLETE }
    fun getSelectedTracksCount(): Int = tracks.count { it.isSelected }
}
