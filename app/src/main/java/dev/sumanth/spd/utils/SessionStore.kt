package dev.sumanth.spd.utils

import android.content.Context
import android.util.Log
import dev.sumanth.spd.model.AppStatus
import dev.sumanth.spd.model.DownloadStatus
import dev.sumanth.spd.model.Track
import dev.sumanth.spd.service.DownloadSession
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Remembers unfinished sessions on disk so a playlist can be resumed after the app was closed or the phone restarted:
 * which tracks were already downloaded and everything needed to download the rest.
 */
object SessionStore {
    private const val TAG = "SessionStore"

    private fun dir(context: Context) = File(context.filesDir, "sessions").apply { mkdirs() }

    @Synchronized
    fun save(context: Context, session: DownloadSession) {
        if (session.discarded) return
        try {
            val tracks = JSONArray()
            for (t in session.tracks.toList()) {
                val o = JSONObject()
                    .put("title", t.title)
                    .put("artist", t.artist)
                    .put("isSelected", t.isSelected)
                    .put("isVideo", t.isVideo)
                    .put("useYtDlp", t.useYtDlp)
                    .put("done", t.status == DownloadStatus.COMPLETE)
                t.imageUrl?.let { o.put("imageUrl", it) }
                t.sourceUrl?.let { o.put("sourceUrl", it) }
                t.directUrl?.let { o.put("directUrl", it) }
                t.referer?.let { o.put("referer", it) }
                t.cookie?.let { o.put("cookie", it) }
                t.audioTrackId?.let { o.put("audioTrackId", it) }
                tracks.put(o)
            }
            val json = JSONObject()
                .put("id", session.id)
                .put("platformFolder", session.platformFolder)
                .put("playlistName", session.playlistName)
                .put("downloadPath", session.downloadPath)
                .put("convertToMp3", session.convertToMp3)
                .put("createSubfolder", session.createSubfolder)
                .put("concurrency", session.concurrency)
                .put("tracks", tracks)
            val target = File(dir(context), "${session.id}.json")
            val temp = File(dir(context), "${session.id}.tmp")
            temp.writeText(json.toString())
            if (!temp.renameTo(target)) {
                target.writeText(json.toString())
                temp.delete()
            }
        } catch (e: Exception) {
            Log.w(TAG, "could not save session: ${e.javaClass.simpleName}")
        }
    }

    @Synchronized
    fun delete(context: Context, id: String) {
        try {
            File(dir(context), "$id.json").delete()
        } catch (_: Exception) {
        }
    }

    /** Sessions left on disk; the ones with nothing left to download are cleaned up. */
    @Synchronized
    fun loadAll(context: Context): List<DownloadSession> {
        val result = mutableListOf<DownloadSession>()
        dir(context).listFiles { f -> f.name.endsWith(".json") }?.forEach { file ->
            try {
                val json = JSONObject(file.readText())
                val session = DownloadSession(json.getString("id"))
                session.platformFolder = json.optString("platformFolder", PlatformFolders.OTHER)
                session.playlistName = json.optString("playlistName", "")
                session.downloadPath = json.optString("downloadPath", "")
                session.convertToMp3 = json.optBoolean("convertToMp3", false)
                session.createSubfolder = json.optBoolean("createSubfolder", true)
                session.concurrency = json.optInt("concurrency", 1)
                val tracks = json.getJSONArray("tracks")
                for (i in 0 until tracks.length()) {
                    val o = tracks.getJSONObject(i)
                    session.tracks.add(
                        Track(
                            title = o.getString("title"),
                            artist = o.optString("artist", ""),
                            status = if (o.optBoolean("done")) DownloadStatus.COMPLETE else DownloadStatus.IDLE,
                            imageUrl = o.optString("imageUrl").ifBlank { null },
                            isSelected = o.optBoolean("isSelected", true),
                            sourceUrl = o.optString("sourceUrl").ifBlank { null },
                            isVideo = o.optBoolean("isVideo", false),
                            directUrl = o.optString("directUrl").ifBlank { null },
                            referer = o.optString("referer").ifBlank { null },
                            cookie = o.optString("cookie").ifBlank { null },
                            audioTrackId = o.optString("audioTrackId").ifBlank { null },
                            useYtDlp = o.optBoolean("useYtDlp", false)
                        )
                    )
                }
                if (session.pendingCount() == 0) {
                    file.delete()
                } else {
                    session.restoredFromDisk = true
                    session.appStatus = AppStatus.SCRAPING_COMPLETE
                    result.add(session)
                }
            } catch (e: Exception) {
                Log.w(TAG, "ignoring unreadable session file ${file.name}")
                file.delete()
            }
        }
        return result
    }
}
