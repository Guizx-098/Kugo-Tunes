package dev.sumanth.spd.utils

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.pm.PackageInfoCompat
import dev.sumanth.spd.service.InstallResultReceiver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

/**
 * APK handling for in-app updates.
 * Storage: the app-private cache folder (cacheDir/updates): needs no permission, is invisible to other
 * apps, and Android can reclaim it. Installation uses the official PackageInstaller session API.
 */
object ApkInstaller {
    const val ACTION_RESULT = "dev.sumanth.spd.INSTALL_RESULT"
    private const val DIR = "updates"

    private fun updatesDir(context: Context) = File(context.cacheDir, DIR).apply { mkdirs() }

    private fun targetFile(context: Context, info: ReleaseInfo) =
        File(updatesDir(context), "SPD-Turbo-${info.version}.apk")

    /** An already downloaded and valid APK for this release, if any (avoids downloading twice). */
    fun existing(context: Context, info: ReleaseInfo): File? {
        val file = targetFile(context, info)
        return if (verify(context, file, info.apkSize)) file else null
    }

    suspend fun download(context: Context, info: ReleaseInfo, onProgress: (Long, Long) -> Unit): File {
        val url = info.apkUrl ?: throw IOException("Release has no APK")
        val target = targetFile(context, info)
        val part = File(target.parentFile, target.name + ".part")
        // Remove leftovers from older/other updates before starting
        updatesDir(context).listFiles()?.forEach { it.delete() }

        try {
            withContext(Dispatchers.IO) {
                val request = okhttp3.Request.Builder().url(url).header("User-Agent", "SPD-Turbo").build()
                DownloadManager.client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) throw IOException("Download failed: ${response.code}")
                    val body = response.body
                    val total = body.contentLength().takeIf { it > 0 } ?: info.apkSize

                    body.byteStream().use { input ->
                        FileOutputStream(part).use { output ->
                            val buffer = ByteArray(64 * 1024)
                            var read: Int
                            var done = 0L
                            var lastReported = 0L
                            while (input.read(buffer).also { read = it } != -1) {
                                ensureActive()
                                output.write(buffer, 0, read)
                                done += read
                                if (done - lastReported >= 128 * 1024) {
                                    onProgress(done, total)
                                    lastReported = done
                                }
                            }
                            onProgress(done, total)
                        }
                    }
                }
            }
            if (!part.renameTo(target)) throw IOException("Could not store the downloaded APK")
            return target
        } catch (e: Throwable) {
            part.delete()
            target.delete()
            throw e
        }
    }

    /** Size matches the Release asset and it really is an APK of this app. */
    fun verify(context: Context, file: File, expectedSize: Long): Boolean {
        if (!file.exists() || file.length() == 0L) return false
        if (expectedSize > 0 && file.length() != expectedSize) return false
        val info = context.packageManager.getPackageArchiveInfo(file.absolutePath, 0) ?: return false
        return info.packageName == context.packageName
    }

    fun canInstall(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.O || context.packageManager.canRequestPackageInstalls()

    fun settingsIntent(context: Context): Intent =
        Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    /** Starts the official install flow; the result arrives in [InstallResultReceiver]. */
    fun install(context: Context, file: File) {
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
        params.setAppPackageName(context.packageName)
        val sessionId = installer.createSession(params)
        try {
            installer.openSession(sessionId).use { session ->
                session.openWrite("base.apk", 0, file.length()).use { out ->
                    file.inputStream().use { it.copyTo(out, 64 * 1024) }
                    session.fsync(out)
                }
                val intent = Intent(context, InstallResultReceiver::class.java).setAction(ACTION_RESULT)
                var flags = PendingIntent.FLAG_UPDATE_CURRENT
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) flags = flags or PendingIntent.FLAG_MUTABLE
                val pendingIntent = PendingIntent.getBroadcast(context, sessionId, intent, flags)
                session.commit(pendingIntent.intentSender)
            }
        } catch (e: Exception) {
            runCatching { installer.abandonSession(sessionId) }
            throw e
        }
    }

    /** Deletes partial downloads and APKs that are not newer than the installed version. */
    fun cleanup(context: Context) {
        val installed = runCatching {
            PackageInfoCompat.getLongVersionCode(context.packageManager.getPackageInfo(context.packageName, 0))
        }.getOrDefault(Long.MAX_VALUE)

        updatesDir(context).listFiles()?.forEach { file ->
            if (!file.name.endsWith(".apk")) {
                file.delete()
                return@forEach
            }
            val info = context.packageManager.getPackageArchiveInfo(file.absolutePath, 0)
            if (info == null || PackageInfoCompat.getLongVersionCode(info) <= installed) file.delete()
        }
    }

    fun deleteAll(context: Context) {
        updatesDir(context).listFiles()?.forEach { it.delete() }
    }
}
