package dev.sumanth.spd.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.arthenica.ffmpegkit.FFmpegKit
import dev.sumanth.spd.MainActivity
import dev.sumanth.spd.R
import dev.sumanth.spd.model.AppStatus
import dev.sumanth.spd.model.DownloadStatus
import dev.sumanth.spd.model.Platform
import dev.sumanth.spd.model.Track
import dev.sumanth.spd.utils.DirectFile
import dev.sumanth.spd.utils.DownloadHttpException
import dev.sumanth.spd.utils.DownloadManager
import dev.sumanth.spd.utils.FileMeta
import dev.sumanth.spd.utils.LocaleHelper
import dev.sumanth.spd.utils.LocalizedException
import dev.sumanth.spd.utils.NetworkGate
import dev.sumanth.spd.utils.SessionStore
import dev.sumanth.spd.utils.SharedPref
import dev.sumanth.spd.utils.SocialVideoExtractor
import dev.sumanth.spd.utils.StorageManager
import dev.sumanth.spd.utils.TikWm
import dev.sumanth.spd.utils.YtDlp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * Runs the download sessions. Several lists can download at the same time (each one with its own tracks, folder and
 * state). A session survives a lost connection (it waits and goes on), a pause, and the app being closed (it is saved
 * and can be resumed: tracks already downloaded are not downloaded again).
 */
class DownloadService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.IO + Job())
    private val statusLock = Any()
    private val activeSessions = ConcurrentHashMap<String, DownloadSession>()
    private val prefs by lazy { SharedPref(applicationContext) }

    companion object {
        private const val CHANNEL_ID = "download_channel"
        private const val NOTIFICATION_ID = 1
        private const val MAX_NETWORK_RETRIES = 5
        const val ACTION_START = "ACTION_START"
        const val ACTION_STOP = "ACTION_STOP"
        const val EXTRA_SESSION_ID = "EXTRA_SESSION_ID"
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onDestroy() {
        super.onDestroy()
        // Whatever was running is paused and saved, so it can be resumed
        activeSessions.values.toList().forEach { pauseState(it) }
        activeSessions.clear()
        serviceScope.cancel()
        FFmpegKit.cancel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val session = intent?.getStringExtra(EXTRA_SESSION_ID)?.let { DownloadState.find(it) } ?: DownloadState.foreground
        when (intent?.action) {
            ACTION_START -> {
                val text = LocaleHelper.string(this, R.string.notif_preparing)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    startForeground(NOTIFICATION_ID, createNotification(text), ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
                } else {
                    startForeground(NOTIFICATION_ID, createNotification(text))
                }
                startSession(session)
            }
            ACTION_STOP -> stopSession(session)
        }
        return START_NOT_STICKY
    }

    private fun startSession(session: DownloadSession) {
        session.job?.cancel()
        activeSessions[session.id] = session
        session.job = serviceScope.launch {
            session.appStatus = AppStatus.DOWNLOADING
            val indices = session.tracks.indices.filter {
                val track = session.tracks[it]
                track.isSelected && track.status != DownloadStatus.COMPLETE
            }
            // Retried (previously failed) tracks go back to "waiting" until their turn
            indices.forEach { setStatus(session, it, DownloadStatus.IDLE) }
            persist(session, force = true)

            val semaphore = Semaphore(session.concurrency.coerceIn(1, 4))

            // Each track is its own independent task; the semaphore only limits how many run at once
            coroutineScope {
                for (i in indices) {
                    launch {
                        semaphore.withPermit {
                            ensureActive()
                            awaitNetwork(session)
                            session.currentTrackIndex = i
                            session.lastTitle = session.tracks[i].title
                            refreshNotification()
                            processTrack(session, i)
                        }
                    }
                }
            }

            session.appStatus = AppStatus.DOWNLOADING_COMPLETE
            session.currentTrackIndex = -1
            session.waitingFor = 0
            // Finished (even with failures): nothing is left to resume later
            SessionStore.delete(this@DownloadService, session.id)
            endSession(session)
        }
    }

    /** Waits (without losing the list) while there is no internet, or no Wi-Fi when "Wi-Fi only" is on. */
    private suspend fun awaitNetwork(session: DownloadSession) {
        while (true) {
            val state = NetworkGate.state(this, prefs.getWifiOnly())
            if (state == NetworkGate.OK) {
                if (session.waitingFor != 0) {
                    session.waitingFor = 0
                    refreshNotification()
                }
                return
            }
            if (session.waitingFor != state) {
                session.waitingFor = state
                refreshNotification()
            }
            delay(3000)
        }
    }

    /**
     * download -> (convert | tag) -> copy -> COMPLETE. COMPLETE is only set after downloadFile returned,
     * i.e. after every step (including the MP3 conversion) finished. A failure only affects this track;
     * a lost connection makes it wait and try again instead of failing.
     */
    private suspend fun processTrack(session: DownloadSession, index: Int) {
        val track = session.tracks[index]
        if (session.restoredFromDisk && alreadySaved(session, track)) {
            // Saved before the app closed, but not marked as done
            setStatus(session, index, DownloadStatus.COMPLETE)
            persist(session)
            return
        }
        var networkRetries = 0
        while (true) {
            setStatus(session, index, DownloadStatus.DOWNLOADING)
            try {
                runDownload(session, index, track)
                setStatus(session, index, DownloadStatus.COMPLETE)
                persist(session)
                return
            } catch (e: Throwable) {
                // Cancelled by the user -> propagate; any other failure stays isolated in this track
                currentCoroutineContext().ensureActive()
                if (e is CancellationException) throw e
                e.printStackTrace()
                if (isNetworkProblem(e) && networkRetries < MAX_NETWORK_RETRIES) {
                    networkRetries++
                    setStatus(session, index, DownloadStatus.IDLE)
                    delay(2000)
                    awaitNetwork(session)
                    continue
                }
                val reason = if (e is LocalizedException) {
                    LocaleHelper.string(this, e.resId, *e.args.toTypedArray())
                } else if (e is DownloadHttpException) {
                    LocaleHelper.string(
                        this,
                        when (e.code) {
                            401, 403 -> R.string.err_access_blocked
                            404, 410 -> R.string.err_video_unavailable
                            429 -> R.string.err_rate_limited
                            else -> R.string.err_download_generic
                        }
                    )
                } else {
                    LocaleHelper.string(this, R.string.err_download_generic)
                }
                setStatus(session, index, DownloadStatus.FAILED, reason)
                persist(session)
                return
            }
        }
    }

    /** One attempt to download and save a track. */
    private suspend fun runDownload(session: DownloadSession, index: Int, track: Track) {
        val onProgress: (Float) -> Unit = { p -> setProgress(session, index, p) }
        if (track.useYtDlp && track.sourceUrl != null) {
            // TikTok / Instagram / Facebook: yt-dlp downloads (and joins video + audio when needed), then the file is saved
            val file = obtainSocialFile(track.sourceUrl, onProgress)
            try {
                currentCoroutineContext().ensureActive()
                setStatus(session, index, DownloadStatus.PROCESSING)
                val extension = file.extension.ifBlank { "mp4" }
                val mime = when (extension) {
                    "webm" -> "video/webm"
                    "mkv" -> "video/x-matroska"
                    else -> "video/mp4"
                }
                DownloadManager.saveToFolder(
                    this, file, session.downloadPath, session.createSubfolder, session.playlistName,
                    DownloadManager.sanitizeFilename(track.title), extension, mime, session.platformFolder
                )
            } finally {
                file.parentFile?.deleteRecursively()
            }
            return
        }
        val wantVideo = track.isVideo && !session.convertToMp3
        val fileMeta = when {
            track.directUrl != null -> FileMeta(
                url = track.directUrl,
                name = track.title,
                extention = "mp4",
                headers = buildMap {
                    put("User-Agent", SocialVideoExtractor.USER_AGENT)
                    track.referer?.let { put("Referer", it) }
                    track.cookie?.let { put("Cookie", it) }
                }
            )
            track.sourceUrl != null -> DownloadManager.getFileMetaFromUrl(track.sourceUrl, wantVideo, track.audioTrackId)
            else -> DownloadManager.getFileMeta(track.title, track.artist)
        }
        DownloadManager.downloadFile(
            this,
            fileMeta.url,
            session.downloadPath,
            DownloadManager.sanitizeFilename(track.title),
            fileMeta.extention,
            session.convertToMp3,
            track.artist,
            track.imageUrl,
            session.createSubfolder,
            session.playlistName,
            wantVideo,
            track.sourceUrl != null,
            audioUrl = fileMeta.audioUrl,
            headers = fileMeta.headers,
            platformFolder = session.platformFolder,
            onProgress = onProgress
        ) { stage -> setStatus(session, index, stage) }
    }

    /** Is the file of this track already in the folder? (sessions restored after the app was closed) */
    private fun alreadySaved(session: DownloadSession, track: Track): Boolean {
        val extensions = when {
            track.isVideo -> listOf("mp4", "webm", "mkv")
            session.convertToMp3 -> listOf("mp3")
            else -> listOf("m4a")
        }
        val playlist = session.playlistName.takeIf { session.createSubfolder && it.isNotBlank() }
        return runCatching {
            StorageManager.exists(
                this, session.downloadPath.ifBlank { null }, session.platformFolder, playlist,
                DownloadManager.sanitizeFilename(track.title), extensions
            )
        }.getOrDefault(false)
    }

    /** Connection lost in the middle of a download (not a problem with the content itself). */
    private fun isNetworkProblem(e: Throwable): Boolean {
        if (e is LocalizedException) return e.resId == R.string.err_network
        var current: Throwable? = e
        var depth = 0
        while (current != null && depth < 8) {
            if (current is java.net.UnknownHostException || current is java.net.SocketTimeoutException ||
                current is java.net.ConnectException || current is java.net.SocketException ||
                current is javax.net.ssl.SSLException
            ) return true
            val message = current.message?.lowercase().orEmpty()
            if (message.contains("unexpected end of stream") || message.contains("connection reset") ||
                message.contains("connection abort") || message.contains("broken pipe") ||
                message.contains("timeout") || message.contains("timed out")
            ) return true
            val next = current.cause
            current = if (next === current) null else next
            depth++
        }
        return false
    }

    /**
     * yt-dlp first. When it cannot get the video, TikTok falls back to TikWM and Instagram to the public embed page.
     * Definitive answers (private, removed, wrong link) are not retried elsewhere.
     */
    private suspend fun obtainSocialFile(url: String, onProgress: (Float) -> Unit): File {
        try {
            return YtDlp.download(this, url, onProgress)
        } catch (e: Exception) {
            currentCoroutineContext().ensureActive()
            if (e is CancellationException) throw e
            if (e is LocalizedException && YtDlp.isDefinitive(e)) throw e
            val host = android.net.Uri.parse(url).host?.lowercase().orEmpty()
            try {
                return when {
                    host.endsWith("tiktok.com") && TikWm.ENABLED -> TikWm.download(this, url, onProgress)
                    host.endsWith("instagram.com") || host == "instagr.am" -> {
                        val alt = withContext(Dispatchers.IO) { SocialVideoExtractor.extract(url, Platform.INSTAGRAM) }
                        DirectFile.download(
                            this, alt.directUrl ?: throw e,
                            buildMap {
                                put("User-Agent", SocialVideoExtractor.USER_AGENT)
                                alt.referer?.let { put("Referer", it) }
                                alt.cookie?.let { put("Cookie", it) }
                            },
                            onProgress
                        )
                    }
                    else -> throw e
                }
            } catch (e2: Exception) {
                currentCoroutineContext().ensureActive()
                if (e2 is CancellationException) throw e2
                throw e
            }
        }
    }

    private fun setStatus(session: DownloadSession, index: Int, status: DownloadStatus, error: String? = null) {
        synchronized(statusLock) {
            if (index in session.tracks.indices) {
                val track = session.tracks[index]
                session.tracks[index] = track.copy(
                    status = status,
                    error = if (status == DownloadStatus.FAILED) error else null,
                    progress = -1f
                )
            }
        }
    }

    private fun setProgress(session: DownloadSession, index: Int, progress: Float) {
        synchronized(statusLock) {
            if (index in session.tracks.indices) {
                val track = session.tracks[index]
                if (track.status == DownloadStatus.DOWNLOADING) {
                    session.tracks[index] = track.copy(progress = progress)
                }
            }
        }
    }

    /** Remembers where this session is (at most every 3 seconds, unless forced). */
    private fun persist(session: DownloadSession, force: Boolean = false) {
        val now = System.currentTimeMillis()
        if (!force && now - session.lastSaved < 3000) return
        session.lastSaved = now
        SessionStore.save(this, session)
    }

    /** Pause: stops this session only, keeps the tracks already downloaded, and remembers it for a later resume. */
    private fun stopSession(session: DownloadSession) {
        session.job?.cancel()
        session.job = null
        activeSessions.remove(session.id)
        if (activeSessions.isEmpty()) FFmpegKit.cancel()
        pauseState(session)
        endSessionIfIdle()
    }

    private fun pauseState(session: DownloadSession) {
        // Tracks that were in flight go back to "waiting" instead of a spinner that never ends
        synchronized(statusLock) {
            for (i in session.tracks.indices) {
                val track = session.tracks[i]
                if (track.status == DownloadStatus.DOWNLOADING || track.status == DownloadStatus.PROCESSING ||
                    track.status == DownloadStatus.CONVERTING
                ) {
                    session.tracks[i] = track.copy(status = DownloadStatus.IDLE, progress = -1f)
                }
            }
        }
        session.waitingFor = 0
        session.currentTrackIndex = -1
        if (session.appStatus == AppStatus.DOWNLOADING) session.appStatus = AppStatus.SCRAPING_COMPLETE
        persist(session, force = true)
    }

    private fun endSession(session: DownloadSession) {
        activeSessions.remove(session.id)
        endSessionIfIdle()
    }

    private fun endSessionIfIdle() {
        if (activeSessions.isEmpty()) {
            stopForeground(true)
            stopSelf()
        } else {
            refreshNotification()
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                LocaleHelper.string(this, R.string.notif_channel),
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(title: String, content: String = "", progress: Int = 0, total: Int = 0): android.app.Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .apply { if (content.isNotEmpty()) setContentText(content) }

        if (total > 0) {
            builder.setProgress(total, progress, false)
        }

        return builder.build()
    }

    /** One notification for everything that is downloading (all sessions together). */
    private fun refreshNotification() {
        val sessions = activeSessions.values.toList()
        if (sessions.isEmpty()) return
        val total = sessions.sumOf { it.selectedCount() }
        val finished = sessions.sumOf { s ->
            s.tracks.count { it.isSelected && (it.status == DownloadStatus.COMPLETE || it.status == DownloadStatus.FAILED) }
        }
        val current = (finished + 1).coerceAtMost(total.coerceAtLeast(1))
        val title = if (sessions.size > 1) {
            LocaleHelper.string(this, R.string.notif_multi, sessions.size, finished, total)
        } else {
            LocaleHelper.string(this, R.string.notif_downloading, current, total)
        }
        val text = when (sessions.firstOrNull { it.waitingFor != 0 }?.waitingFor) {
            NetworkGate.NO_INTERNET -> LocaleHelper.string(this, R.string.waiting_network)
            NetworkGate.NEEDS_WIFI -> LocaleHelper.string(this, R.string.waiting_wifi)
            else -> sessions.last().lastTitle
        }
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, createNotification(title, text, finished, total))
    }
}
