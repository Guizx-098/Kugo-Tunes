package dev.sumanth.spd.model

enum class AppStatus {
    IDLE,
    SCRAPING,
    SCRAPING_COMPLETE,
    DOWNLOADING,
    DOWNLOADING_COMPLETE,
}

enum class DownloadStatus {
    IDLE,
    DOWNLOADING,
    PROCESSING,
    CONVERTING,
    FAILED,
    COMPLETE
}

data class Track(
    val title: String,
    val artist: String,
    val status: DownloadStatus,
    val imageUrl: String? = null,
    val isSelected: Boolean = true,
    val sourceUrl: String? = null,
    val isVideo: Boolean = false,
    // TikTok / Kwai: direct media address found on the public page + the headers the player used
    val directUrl: String? = null,
    val referer: String? = null,
    val cookie: String? = null,
    // YouTube: audio tracks offered by the video when the original one could not be identified reliably
    val audioOptions: List<AudioOption> = emptyList(),
    val audioTrackId: String? = null
)

/** One selectable audio track of a YouTube video (id = YouTube's audio track id). */
data class AudioOption(val id: String, val label: String)

enum class Platform { SPOTIFY, YOUTUBE, TIKTOK, KWAI, INSTAGRAM, FACEBOOK }

enum class ContentType { VIDEO, MUSIC }

enum class AudioFormat { MP3, M4A, MP4 }