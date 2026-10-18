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
import dev.sumanth.spd.MainActivity
import dev.sumanth.spd.model.AppStatus
import dev.sumanth.spd.model.DownloadStatus
import dev.sumanth.spd.R
import dev.sumanth.spd.utils.DownloadManager
import dev.sumanth.spd.utils.FileMeta
import dev.sumanth.spd.utils.SocialVideoExtractor
import dev.sumanth.spd.utils.LocaleHelper
import dev.sumanth.spd.utils.LocalizedException
import dev.sumanth.spd.utils.YtDlp
import dev.sumanth.spd.utils.TikWm
import dev.sumanth.spd.utils.DirectFile
import dev.sumanth.spd.utils.SocialVideoExtractor
import dev.sumanth.spd.model.Platform
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.CancellationException
import com.arthenica.ffmpegkit.FFmpegKit

class DownloadService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.IO + Job())
    private var downloadJob: Job? = null
    private val statusLock = Any()

    companion object {
        private const val CHANNEL_ID = "download_channel"
        private const val NOTIFICATION_ID = 1
        const val ACTION_START = "ACTION_START"
        const val ACTION_STOP = "ACTION_STOP"
        const val EXTRA_DOWNLOAD_PATH = "EXTRA_DOWNLOAD_PATH"
        const val EXTRA_CONVERT_TO_MP3 = "EXTRA_CONVERT_TO_MP3"
        const val EXTRA_CREATE_SUBFOLDER = "EXTRA_CREATE_SUBFOLDER"
        const val EXTRA_PLAYLIST_NAME = "EXTRA_PLAYLIST_NAME"
        const val EXTRA_CONCURRENT_DOWNLOADS = "EXTRA_CONCURRENT_DOWNLOADS"
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
        FFmpegKit.cancel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val path = intent.getStringExtra(EXTRA_DOWNLOAD_PATH).orEmpty()
                val convert = intent.getBooleanExtra(EXTRA_CONVERT_TO_MP3, false)
                val createSubfolder = intent.getBooleanExtra(EXTRA_CREATE_SUBFOLDER, true)
                val playlistName = intent.getStringExtra(EXTRA_PLAYLIST_NAME)
                val concurrency = intent.getIntExtra(EXTRA_CONCURRENT_DOWNLOADS, 1)
                
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    startForeground(NOTIFICATION_ID, createNotification(LocaleHelper.string(this, R.string.notif_preparing)), ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
                } else {
                    startForeground(NOTIFICATION_ID, createNotification(LocaleHelper.string(this, R.string.notif_preparing)))
                }
                
                startDownloads(path, convert, createSubfolder, playlistName, concurrency)
            }
            ACTION_STOP -> {
                stopDownloads()
            }
        }
        return START_NOT_STICKY
    }

    private fun startDownloads(downloadPath: String, convertToMp3: Boolean, createSubfolder: Boolean, playlistName: String?, concurrency: Int) {
        downloadJob?.cancel()
        downloadJob = serviceScope.launch {
            DownloadState.appStatus = AppStatus.DOWNLOADING
            val indices = DownloadState.tracks.indices.filter {
                val track = DownloadState.tracks[it]
                track.isSelected && track.status != DownloadStatus.COMPLETE
            }
            // Retried (previously failed) tracks go back to "waiting" until their turn
            indices.forEach { setStatus(it, DownloadStatus.IDLE) }

            val totalToDownload = indices.size
            val startedCount = AtomicInteger(0)
            val semaphore = Semaphore(concurrency.coerceIn(1, 4))

            // Each track is its own independent task; the semaphore only limits how many run at once
            coroutineScope {
                for (i in indices) {
                    launch {
                        semaphore.withPermit {
                            ensureActive()
                            val started = startedCount.incrementAndGet()
                            DownloadState.currentTrackIndex = i
                            updateNotification(started, totalToDownload, DownloadState.tracks[i].title)
                            processTrack(i, downloadPath, convertToMp3, createSubfolder, playlistName)
                        }
                    }
                }
            }

            DownloadState.appStatus = AppStatus.DOWNLOADING_COMPLETE
            DownloadState.currentTrackIndex = -1
            stopForeground(true)
            stopSelf()
        }
    }

    /**
     * download -> (convert | tag) -> copy -> COMPLETE. COMPLETE is only set after downloadFile returned,
     * i.e. after every step (including the MP3 conversion) finished. A failure only affects this track.
     */
    private suspend fun processTrack(index: Int, downloadPath: String, convertToMp3: Boolean, createSubfolder: Boolean, playlistName: String?) {
        val track = DownloadState.tracks[index]
        setStatus(index, DownloadStatus.DOWNLOADING)
        try {
            if (track.useYtDlp && track.sourceUrl != null) {
                // TikTok / Instagram / Facebook: yt-dlp downloads (and joins video + audio when needed), then the file is saved
                val file = obtainSocialFile(track.sourceUrl)
                try {
                    currentCoroutineContext().ensureActive()
                    setStatus(index, DownloadStatus.PROCESSING)
                    val extension = file.extension.ifBlank { "mp4" }
                    val mime = when (extension) {
                        "webm" -> "video/webm"
                        "mkv" -> "video/x-matroska"
                        else -> "video/mp4"
                    }
                    DownloadManager.saveToFolder(
                        this@DownloadService, file, downloadPath, createSubfolder, playlistName,
                        DownloadManager.sanitizeFilename(track.title), extension, mime
                    )
                } finally {
                    file.parentFile?.deleteRecursively()
                }
                setStatus(index, DownloadStatus.COMPLETE)
                return
            }
            val wantVideo = track.isVideo && !convertToMp3
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
                this@DownloadService,
                fileMeta.url,
                downloadPath,
                DownloadManager.sanitizeFilename(track.title),
                fileMeta.extention,
                convertToMp3,
                track.artist,
                track.imageUrl,
                createSubfolder,
                playlistName,
                wantVideo,
                track.sourceUrl != null,
                audioUrl = fileMeta.audioUrl,
                headers = fileMeta.headers
            ) { stage -> setStatus(index, stage) }
            setStatus(index, DownloadStatus.COMPLETE)
        } catch (e: Throwable) {
            // Cancelled by the user -> propagate; any other failure stays isolated in this track
            currentCoroutineContext().ensureActive()
            if (e is CancellationException) throw e
            e.printStackTrace()
            val reason = if (e is LocalizedException) {
                LocaleHelper.string(this, e.resId, *e.args.toTypedArray())
            } else {
                LocaleHelper.string(this, R.string.err_download_generic)
            }
            setStatus(index, DownloadStatus.FAILED, reason)
        }
    }

    /**
     * yt-dlp first. When it cannot get the video, TikTok falls back to TikWM and Instagram to the public embed page.
     * Definitive answers (private, removed, wrong link) are not retried elsewhere.
     */
    private suspend fun obtainSocialFile(url: String): File {
        try {
            return YtDlp.download(this, url)
        } catch (e: Exception) {
            currentCoroutineContext().ensureActive()
            if (e is CancellationException) throw e
            if (e is LocalizedException && YtDlp.isDefinitive(e)) throw e
            val host = android.net.Uri.parse(url).host?.lowercase().orEmpty()
            try {
                return when {
                    host.endsWith("tiktok.com") && TikWm.ENABLED -> TikWm.download(this, url)
                    host.endsWith("instagram.com") || host == "instagr.am" -> {
                        val alt = withContext(Dispatchers.IO) { SocialVideoExtractor.extract(url, Platform.INSTAGRAM) }
                        DirectFile.download(
                            this, alt.directUrl ?: throw e,
                            buildMap {
                                put("User-Agent", alt.userAgent ?: SocialVideoExtractor.USER_AGENT)
                                alt.referer?.let { put("Referer", it) }
                                alt.cookie?.let { put("Cookie", it) }
                            }
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

    private fun setStatus(index: Int, status: DownloadStatus, error: String? = null) {
        synchronized(statusLock) {
            if (index in DownloadState.tracks.indices) {
                DownloadState.tracks[index] = DownloadState.tracks[index].copy(
                    status = status,
                    error = if (status == DownloadStatus.FAILED) error else null
                )
            }
        }
    }

    private fun stopDownloads() {
        downloadJob?.cancel()
        FFmpegKit.cancel()
        // Tracks that were in flight go back to "waiting" instead of a spinner that never ends
        synchronized(statusLock) {
            for (i in DownloadState.tracks.indices) {
                val status = DownloadState.tracks[i].status
                if (status == DownloadStatus.DOWNLOADING || status == DownloadStatus.PROCESSING || status == DownloadStatus.CONVERTING) {
                    DownloadState.tracks[i] = DownloadState.tracks[i].copy(status = DownloadStatus.IDLE)
                }
            }
        }
        DownloadState.appStatus = AppStatus.SCRAPING_COMPLETE
        stopForeground(true)
        stopSelf()
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

    private fun updateNotification(current: Int, total: Int, trackName: String) {
        val title = LocaleHelper.string(this, R.string.notif_downloading, current, total)
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, createNotification(title, trackName, current, total))
    }
}
