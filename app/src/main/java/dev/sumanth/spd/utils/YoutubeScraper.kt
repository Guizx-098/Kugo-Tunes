package dev.sumanth.spd.utils

import dev.sumanth.spd.R

import dev.sumanth.spd.model.DownloadStatus
import dev.sumanth.spd.model.Track
import org.schabi.newpipe.extractor.ServiceList.YouTube
import org.schabi.newpipe.extractor.Image
import org.schabi.newpipe.extractor.StreamingService
import org.schabi.newpipe.extractor.stream.StreamInfoItem

object YoutubeScraper {

    data class ScrapeResult(val name: String, val tracks: List<Track>)

    fun isYoutubeLink(input: String): Boolean {
        val link = input.lowercase()
        return link.contains("youtube.com/") || link.contains("youtu.be/")
    }

    suspend fun scrape(input: String, isVideo: Boolean, onProgress: suspend (List<Track>, Int) -> Unit): ScrapeResult {
        val url = Regex("https?://\\S+").find(input)?.value
            ?: throw LocalizedException(R.string.err_youtube_read, fallback = "Unable to read the YouTube link.")
        // [isVideo] comes from the user's choice (Video or Music) on the YouTube / YouTube Music screen

        val listId = Regex("[?&]list=([\\w-]+)").find(url)?.groupValues?.get(1)
        if (listId != null && !listId.startsWith("RD")) {
            return scrapePlaylist("https://www.youtube.com/playlist?list=$listId", isVideo, onProgress)
        }

        if (YouTube.getLinkTypeByUrl(url) == StreamingService.LinkType.STREAM) {
            return scrapeSingle(url, isVideo)
        }
        throw LocalizedException(R.string.err_youtube_unsupported, fallback = "Unsupported YouTube link. Use a video, song or playlist link.")
    }

    private fun scrapeSingle(url: String, isVideo: Boolean): ScrapeResult {
        val extractor = YouTube.getStreamExtractor(url)
        extractor.fetchPage()
        val track = Track(
            title = extractor.name,
            artist = cleanArtist(extractor.uploaderName),
            status = DownloadStatus.IDLE,
            imageUrl = bestThumbnail(extractor.thumbnails, url),
            sourceUrl = url,
            isVideo = isVideo
        )
        // Blank name -> no playlist subfolder for a single item
        return ScrapeResult("", listOf(track))
    }

    private suspend fun scrapePlaylist(
        url: String,
        isVideo: Boolean,
        onProgress: suspend (List<Track>, Int) -> Unit
    ): ScrapeResult {
        val extractor = YouTube.getPlaylistExtractor(url)
        extractor.fetchPage()
        val total = extractor.streamCount.toInt().coerceAtLeast(0)
        val tracks = mutableListOf<Track>()

        var page = extractor.initialPage
        while (true) {
            page.items.forEach { tracks.add(toTrack(it, isVideo)) }
            onProgress(tracks.toList(), total)
            if (!page.hasNextPage()) break
            page = extractor.getPage(page.nextPage)
        }
        return ScrapeResult(extractor.name, tracks)
    }

    private fun toTrack(item: StreamInfoItem, isVideo: Boolean) = Track(
        title = item.name,
        artist = cleanArtist(item.uploaderName),
        status = DownloadStatus.IDLE,
        imageUrl = bestThumbnail(item.thumbnails, item.url),
        sourceUrl = item.url,
        isVideo = isVideo
    )

    /** Largest thumbnail, preferring JPEG over WebP; falls back to the standard YouTube thumbnail. */
    private fun bestThumbnail(images: List<Image>, videoUrl: String): String? {
        val usable = images.filter { it.url.isNotBlank() }
        val jpegs = usable.filter { !it.url.contains(".webp") && !it.url.contains("vi_webp") }
        val best = (jpegs.ifEmpty { usable })
            .withIndex()
            .maxWithOrNull(compareBy({ maxOf(it.value.height, 0) }, { it.index }))
            ?.value?.url
        if (best != null) return best.replace("/vi_webp/", "/vi/").replace(".webp", ".jpg")
        val id = Regex("(?:v=|youtu\\.be/)([\\w-]{11})").find(videoUrl)?.groupValues?.get(1)
        return id?.let { "https://i.ytimg.com/vi/$it/hqdefault.jpg" }
    }

    private fun cleanArtist(name: String?): String = (name ?: "").removeSuffix(" - Topic")
}
