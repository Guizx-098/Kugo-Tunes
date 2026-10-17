package dev.sumanth.spd.utils

import dev.sumanth.spd.model.AudioOption
import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.AudioTrackType

/**
 * Chooses the audio track of a YouTube video. Videos with auto-dubbing expose several audio tracks; the old code
 * simply took the highest bitrate, which could be an AI dub. The device language is never used here.
 *
 * Order: user's explicit choice -> track YouTube marks as ORIGINAL -> the only track that is not marked as
 * dubbed/descriptive -> (last resort) best bitrate among the non-dubbed streams.
 */
object AudioTrackSelector {

    private fun trackKey(stream: AudioStream): String = stream.audioTrackId ?: DEFAULT_TRACK

    private fun isDubOrDescriptive(stream: AudioStream): Boolean =
        stream.audioTrackType == AudioTrackType.DUBBED || stream.audioTrackType == AudioTrackType.DESCRIPTIVE

    /** Picks the stream to download from [streams] (already filtered by container). */
    fun pick(streams: List<AudioStream>, preferredTrackId: String?): AudioStream? {
        val usable = streams.filter { it.content.isNotBlank() }
        if (usable.isEmpty()) return null

        if (preferredTrackId != null) {
            usable.filter { trackKey(it) == preferredTrackId }.maxByOrNull { it.bitrate }?.let { return it }
        }
        if (usable.map(::trackKey).distinct().size <= 1) return usable.maxByOrNull { it.bitrate }

        usable.filter { it.audioTrackType == AudioTrackType.ORIGINAL }.maxByOrNull { it.bitrate }?.let { return it }
        val notDubbed = usable.filterNot(::isDubOrDescriptive)
        return (notDubbed.ifEmpty { usable }).maxByOrNull { it.bitrate }
    }

    /**
     * Tracks to offer to the user, or an empty list when the original track is known (or there is only one track).
     * A choice is only needed when several tracks exist, none is marked ORIGINAL and more than one is not a dub.
     */
    fun optionsIfAmbiguous(streams: List<AudioStream>): List<AudioOption> {
        val usable = streams.filter { it.content.isNotBlank() }
        val tracks = usable.groupBy(::trackKey)
        if (tracks.size <= 1) return emptyList()
        if (usable.any { it.audioTrackType == AudioTrackType.ORIGINAL }) return emptyList()
        val notDubbed = usable.filterNot(::isDubOrDescriptive).map(::trackKey).distinct()
        if (notDubbed.size == 1) return emptyList()
        return tracks.map { (id, list) -> AudioOption(id, label(list.first(), id)) }
    }

    private fun label(stream: AudioStream, id: String): String {
        val name = stream.audioTrackName?.takeIf { it.isNotBlank() }
            ?: stream.audioLocale?.displayName?.takeIf { it.isNotBlank() }
            ?: id
        val type = when (stream.audioTrackType) {
            AudioTrackType.DUBBED -> " (dub)"
            AudioTrackType.DESCRIPTIVE -> " (descriptive)"
            else -> ""
        }
        return name + type
    }

    private const val DEFAULT_TRACK = "default"
}
