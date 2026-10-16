package dev.sumanth.spd.utils

import dev.sumanth.spd.model.DownloadStatus
import dev.sumanth.spd.model.Track
import org.schabi.newpipe.extractor.ServiceList.YouTube
import org.schabi.newpipe.extractor.StreamingService
import org.schabi.newpipe.extractor.stream.StreamInfoItem

object YoutubeScraper {

    data class ScrapeResult(val name: String, val tracks: List<Track>)

    fun isYoutubeLink(input: String): Boolean {
        val link = input.lowercase()
        return link.contains("youtube.com/") || link.contains("youtu.be/")
    }

    suspend fun scrape(input: String, onProgress: suspend (List<Track>, Int) -> Unit): ScrapeResult {
        val url = Regex("https?://\\S+").find(input)?.value
            ?: throw IllegalArgumentException("Unable to read the YouTube link.")
        // YouTube Music (music.youtube.com) -> audio. YouTube -> video.
        val isVideo = !url.contains("music.youtube.com")

        val listId = Regex("[?&]list=([\\w-]+)").find(url)?.groupValues?.get(1)
        if (listId != null && !listId.startsWith("RD")) {
            return scrapePlaylist("https://www.youtube.com/playlist?list=$listId", isVideo, onProgress)
        }

        if (YouTube.getLinkTypeByUrl(url) == StreamingService.LinkType.STREAM) {
            return scrapeSingle(url, isVideo)
        }
        throw IllegalArgumentException("Unsupported YouTube link. Use a video, song or playlist link.")
    }

    private fun scrapeSingle(url: String, isVideo: Boolean): ScrapeResult {
        val extractor = YouTube.getStreamExtractor(url)
        extractor.fetchPage()
        val track = Track(
            title = extractor.name,
            artist = cleanArtist(extractor.uploaderName),
            status = DownloadStatus.IDLE,
            imageUrl = extractor.thumbnails.lastOrNull()?.url,
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
        imageUrl = item.thumbnails.lastOrNull()?.url,
        sourceUrl = item.url,
        isVideo = isVideo
    )

    private fun cleanArtist(name: String?): String = (name ?: "").removeSuffix(" - Topic")
}
