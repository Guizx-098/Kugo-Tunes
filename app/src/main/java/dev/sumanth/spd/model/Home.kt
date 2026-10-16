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
    val isVideo: Boolean = false
)