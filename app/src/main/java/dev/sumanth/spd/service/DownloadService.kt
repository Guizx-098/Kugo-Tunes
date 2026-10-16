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
import dev.sumanth.spd.utils.DownloadManager
import dev.sumanth.spd.utils.SharedPref
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
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

    companion object {
        private const val CHANNEL_ID = "download_channel"
        private const val NOTIFICATION_ID = 1
        const val ACTION_START = "ACTION_START"
        const val ACTION_STOP = "ACTION_STOP"
        const val EXTRA_DOWNLOAD_PATH = "EXTRA_DOWNLOAD_PATH"
        const val EXTRA_CONVERT_TO_MP3 = "EXTRA_CONVERT_TO_MP3"
        const val EXTRA_CREATE_SUBFOLDER = "EXTRA_CREATE_SUBFOLDER"
        const val EXTRA_PLAYLIST_NAME = "EXTRA_PLAYLIST_NAME"
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
                val path = intent.getStringExtra(EXTRA_DOWNLOAD_PATH) ?: return START_NOT_STICKY
                val convert = intent.getBooleanExtra(EXTRA_CONVERT_TO_MP3, false)
                val createSubfolder = intent.getBooleanExtra(EXTRA_CREATE_SUBFOLDER, true)
                val playlistName = intent.getStringExtra(EXTRA_PLAYLIST_NAME)
                
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    startForeground(NOTIFICATION_ID, createNotification("Preparing download..."), ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
                } else {
                    startForeground(NOTIFICATION_ID, createNotification("Preparing download..."))
                }
                
                startDownloads(path, convert, createSubfolder, playlistName)
            }
            ACTION_STOP -> {
                stopDownloads()
            }
        }
        return START_NOT_STICKY
    }

    private fun startDownloads(downloadPath: String, convertToMp3: Boolean, createSubfolder: Boolean, playlistName: String?) {
        downloadJob?.cancel()
        downloadJob = serviceScope.launch {
            DownloadState.appStatus = AppStatus.DOWNLOADING
            val parallel = SharedPref(this@DownloadService).getParallelDownloads()
            val partsPerFile = (12 / parallel).coerceIn(1, 4)
            val gate = Semaphore(parallel)
            val done = AtomicInteger(0)
            val pending = DownloadState.tracks.withIndex()
                .filter { it.value.isSelected && it.value.status != DownloadStatus.COMPLETE }
            val total = pending.size

            pending.map { (i, track) ->
                async {
                    gate.withPermit {
                        ensureActive()
                        DownloadState.currentTrackIndex = i
                        DownloadState.tracks[i] = track.copy(status = DownloadStatus.DOWNLOADING)

                        try {
                            val fileMeta = DownloadManager.getFileMeta(track.title, track.artist)
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
                                partsPerFile
                            )
                            DownloadState.tracks[i] = track.copy(status = DownloadStatus.COMPLETE)
                        } catch (e: Exception) {
                            if (e is CancellationException) throw e
                            e.printStackTrace()
                            DownloadState.tracks[i] = track.copy(status = DownloadStatus.FAILED)
                        }
                        updateNotification(done.incrementAndGet(), total, track.title)
                    }
                }
            }.awaitAll()

            DownloadState.appStatus = AppStatus.DOWNLOADING_COMPLETE
            DownloadState.currentTrackIndex = -1
            stopForeground(true)
            stopSelf()
        }
    }

    private fun stopDownloads() {
        downloadJob?.cancel()
        FFmpegKit.cancel()
        DownloadState.appStatus = AppStatus.SCRAPING_COMPLETE
        stopForeground(true)
        stopSelf()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Downloads",
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
        val title = "Downloading ($current/$total)"
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, createNotification(title, trackName, current, total))
    }
}
