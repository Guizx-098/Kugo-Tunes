package dev.sumanth.spd.ui.component

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilterChip
import androidx.compose.ui.platform.LocalContext
import android.app.Activity
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.res.stringResource
import dev.sumanth.spd.R
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TriStateCheckbox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.sumanth.spd.model.AppStatus
import dev.sumanth.spd.model.DownloadStatus
import dev.sumanth.spd.model.Track
import dev.sumanth.spd.ui.viewmodel.HomeScreenViewModel

@Composable
fun Queue(viewModel: HomeScreenViewModel) {
    val listState = rememberLazyListState()
    val isScraping = viewModel.appStatus == AppStatus.SCRAPING
    val isDownloading = viewModel.appStatus == AppStatus.DOWNLOADING
    val isDownloadComplete = viewModel.appStatus == AppStatus.DOWNLOADING_COMPLETE
    val isScrapingComplete = viewModel.appStatus == AppStatus.SCRAPING_COMPLETE
    val failedCount by remember { derivedStateOf { viewModel.getFailedDownloadsCount() } }
    val downloadedCount by remember { derivedStateOf { viewModel.getDownloadedCount() } }
    val selectedCount by remember { derivedStateOf { viewModel.getSelectedTracksCount() } }
    val pendingCount by remember { derivedStateOf { viewModel.tracks.count { it.isSelected && it.status != DownloadStatus.COMPLETE } } }

    // Back (system button and arrow behave the same): never leaves the user stuck on a result screen.
    // While downloading, back only sends the app to the background; the download service keeps running.
    val activity = LocalContext.current as? Activity
    BackHandler {
        when {
            isScraping -> viewModel.cancelScraping()
            // Downloading: the list keeps going in the background and the user can start another one
            isDownloading -> viewModel.moveToBackground()
            isDownloadComplete -> viewModel.backToPlatforms()
            else -> viewModel.leaveQueue()
        }
    }

    LaunchedEffect(viewModel.currentTrack) {
        // Do not fight the user's own scrolling
        if (viewModel.currentTrack >= 0 && !listState.isScrollInProgress) {
            listState.animateScrollToItem(viewModel.currentTrack)
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val title = if (isScraping) {
                    if (viewModel.totalTracksToScrape > 0) stringResource(R.string.scraping_progress, viewModel.tracks.size, viewModel.totalTracksToScrape)
                    else stringResource(R.string.scraping)
                } else {
                    stringResource(R.string.queue_title, viewModel.tracks.size)
                }
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge
                )
                
                if (!isScraping) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = stringResource(R.string.downloaded_count, downloadedCount),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = stringResource(R.string.failed_count, failedCount),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            }

            if (isDownloading && viewModel.waitingFor != 0) {
                Text(
                    text = stringResource(if (viewModel.waitingFor == 2) R.string.waiting_wifi else R.string.waiting_network),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }

            if (isScraping) {
                Spacer(modifier = Modifier.height(8.dp))
                if (viewModel.totalTracksToScrape > 0) {
                    LinearProgressIndicator(
                        progress = { viewModel.tracks.size.toFloat() / viewModel.totalTracksToScrape },
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant,
                    )
                } else {
                    LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant,
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            if (!isScraping && viewModel.tracks.isNotEmpty()) {
                val allSelected = viewModel.tracks.all { it.isSelected }
                val totalCount = viewModel.tracks.size
                val toggleState = when {
                    allSelected -> ToggleableState.On
                    viewModel.tracks.none { it.isSelected } -> ToggleableState.Off
                    else -> ToggleableState.Indeterminate
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(enabled = !isDownloading) {
                            viewModel.selectAllTracks(toggleState != ToggleableState.On)
                        }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TriStateCheckbox(
                        state = toggleState,
                        onClick = {
                            viewModel.selectAllTracks(toggleState != ToggleableState.On)
                        },
                        enabled = !isDownloading
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.select_all, selectedCount, totalCount),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                HorizontalDivider(modifier = Modifier.padding(bottom = 4.dp), thickness = 0.5.dp)
            }

            LazyColumn(
                modifier = Modifier.weight(1f),
                state = listState,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                itemsIndexed(viewModel.tracks) { index, track ->
                    TrackItem(
                        index = index + 1,
                        track = track,
                        onSelectionChange = {
                            viewModel.toggleTrackSelection(index)
                        },
                        onAudioTrackChange = { viewModel.selectAudioTrack(index, it) },
                        enabled = !isDownloading
                    )
                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp), thickness = 0.5.dp)
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (isScrapingComplete || isDownloadComplete || isDownloading) {
                    OutlinedButton(
                        // After a download the arrow goes back to the platform list; while downloading the list goes to the background
                        onClick = {
                            when {
                                isDownloadComplete -> viewModel.backToPlatforms()
                                isDownloading -> viewModel.moveToBackground()
                                else -> viewModel.leaveQueue()
                            }
                        },
                        modifier = Modifier.heightIn(min = 56.dp)
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back)
                        )
                    }
                }

                Button(
                    onClick = {
                        when {
                            isScraping -> viewModel.cancelScraping()
                            isDownloading -> viewModel.cancelDownload()
                            isDownloadComplete && failedCount > 0 -> viewModel.downloadPlaylist()
                            isDownloadComplete -> viewModel.reset()
                            else -> viewModel.downloadPlaylist()
                        }
                    },
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 56.dp),
                    enabled = (isScraping || (viewModel.tracks.isNotEmpty() && selectedCount > 0)),
                    colors = if (isScraping) {
                        ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                            contentColor = MaterialTheme.colorScheme.onErrorContainer
                        )
                    } else {
                        ButtonDefaults.buttonColors()
                    }
                ) {
                    val icon = when {
                        isScraping -> Icons.Default.Close
                        isDownloading -> Icons.Default.Pause
                        else -> Icons.Default.Download
                    }
                    val text = when {
                        isScraping -> stringResource(R.string.cancel_scraping)
                        isDownloading -> stringResource(R.string.pause_download)
                        isDownloadComplete && failedCount > 0 -> stringResource(R.string.retry_failed, failedCount)
                        isDownloadComplete -> stringResource(R.string.download_another)
                        isScrapingComplete && downloadedCount > 0 -> stringResource(R.string.resume_remaining, pendingCount)
                        selectedCount == viewModel.tracks.size -> stringResource(R.string.download_all)
                        else -> stringResource(R.string.download_selected, selectedCount)
                    }
                    Icon(icon, contentDescription = null)
                    Text(text, modifier = Modifier.padding(start = 8.dp))
                }
            }
        }
    }
}

@Composable
fun TrackItem(
    index: Int,
    track: Track,
    onSelectionChange: (Boolean) -> Unit,
    onAudioTrackChange: (String) -> Unit = {},
    enabled: Boolean = true
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled) {
                onSelectionChange(!track.isSelected)
            }
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Checkbox(
            checked = track.isSelected,
            onCheckedChange = { onSelectionChange(it) },
            enabled = enabled
        )

        Text(
            text = "$index.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(28.dp)
        )

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = track.title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = track.artist,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (track.status == DownloadStatus.FAILED && !track.error.isNullOrBlank()) {
                Text(
                    text = track.error,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.error,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (track.status == DownloadStatus.DOWNLOADING && track.progress in 0f..1f) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(top = 4.dp)
                ) {
                    LinearProgressIndicator(progress = { track.progress }, modifier = Modifier.weight(1f))
                    Text(
                        text = "${(track.progress * 100).toInt()}%",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            if (track.audioOptions.size > 1) {
                Text(
                    text = stringResource(R.string.choose_audio_track),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp)
                )
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    track.audioOptions.forEach { option ->
                        FilterChip(
                            selected = track.audioTrackId == option.id,
                            onClick = { onAudioTrackChange(option.id) },
                            enabled = enabled,
                            label = { Text(option.label, maxLines = 1) }
                        )
                    }
                }
            }
        }

        StatusIcon(status = track.status)
    }
}

@Composable
fun StatusIcon(status: DownloadStatus) {
    when (status) {
        DownloadStatus.IDLE -> Icon(
            imageVector = Icons.Default.Schedule,
            contentDescription = stringResource(R.string.status_idle),
            tint = MaterialTheme.colorScheme.outline
        )
        DownloadStatus.DOWNLOADING, DownloadStatus.PROCESSING, DownloadStatus.CONVERTING -> {
            val description = stringResource(
                when (status) {
                    DownloadStatus.DOWNLOADING -> R.string.status_downloading
                    DownloadStatus.PROCESSING -> R.string.status_processing
                    else -> R.string.status_converting
                }
            )
            CircularProgressIndicator(
                modifier = Modifier.size(24.dp).semantics { contentDescription = description },
                strokeWidth = 2.dp
            )
        }
        DownloadStatus.FAILED -> Icon(
            imageVector = Icons.Default.Error,
            contentDescription = stringResource(R.string.status_failed),
            tint = MaterialTheme.colorScheme.error
        )
        DownloadStatus.COMPLETE -> Icon(
            imageVector = Icons.Default.CheckCircle,
            contentDescription = stringResource(R.string.status_complete),
            tint = MaterialTheme.colorScheme.primary
        )
    }
}
