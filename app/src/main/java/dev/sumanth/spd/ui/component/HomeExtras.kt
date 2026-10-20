package dev.sumanth.spd.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.sumanth.spd.R
import dev.sumanth.spd.model.AppStatus
import dev.sumanth.spd.model.Platform
import dev.sumanth.spd.service.DownloadSession
import dev.sumanth.spd.ui.viewmodel.HomeScreenViewModel

/** "Link copied: TikTok - Download?" shown when a supported link was copied in another app. */
@Composable
fun ClipboardBanner(viewModel: HomeScreenViewModel) {
    val url = viewModel.clipboardSuggestion ?: return
    val name = when (viewModel.detectPlatform(url)) {
        Platform.SPOTIFY -> stringResource(R.string.platform_spotify)
        Platform.YOUTUBE -> stringResource(R.string.platform_youtube)
        Platform.TIKTOK -> stringResource(R.string.platform_tiktok)
        Platform.KWAI -> stringResource(R.string.platform_kwai)
        Platform.INSTAGRAM -> stringResource(R.string.platform_instagram)
        Platform.FACEBOOK -> stringResource(R.string.platform_facebook)
        else -> ""
    }
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.clip_detected, name),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f)
            )
            TextButton(onClick = { viewModel.acceptClipboard() }) { Text(stringResource(R.string.clip_download)) }
            TextButton(onClick = { viewModel.dismissClipboard() }) { Text(stringResource(R.string.clip_ignore)) }
        }
    }
}

/** Lists that keep downloading (or are paused) while the user starts another one. */
@Composable
fun BackgroundBar(viewModel: HomeScreenViewModel) {
    val sessions = viewModel.backgroundSessions
    if (sessions.isEmpty()) return
    var open by remember { mutableStateOf(false) }
    val done = sessions.sumOf { it.doneCount() }
    val total = sessions.sumOf { it.selectedCount() }

    Card(onClick = { open = true }, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.bg_bar, sessions.size),
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f)
                )
                Text("$done/$total", style = MaterialTheme.typography.labelMedium)
            }
            LinearProgressIndicator(
                progress = { if (total > 0) done / total.toFloat() else 0f },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }

    if (open) {
        AlertDialog(
            onDismissRequest = { open = false },
            title = { Text(stringResource(R.string.bg_title)) },
            text = {
                Column(
                    modifier = Modifier.heightIn(max = 380.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    sessions.toList().forEach { SessionRow(it, viewModel) { open = false } }
                }
            },
            confirmButton = { TextButton(onClick = { open = false }) { Text(stringResource(R.string.bg_close)) } }
        )
    }
}

@Composable
private fun SessionRow(session: DownloadSession, viewModel: HomeScreenViewModel, onClose: () -> Unit) {
    val done = session.doneCount()
    val total = session.selectedCount()
    val status = when (session.appStatus) {
        AppStatus.DOWNLOADING -> when (session.waitingFor) {
            1 -> stringResource(R.string.waiting_network)
            2 -> stringResource(R.string.waiting_wifi)
            else -> stringResource(R.string.bg_status_downloading)
        }
        AppStatus.DOWNLOADING_COMPLETE -> stringResource(R.string.bg_status_done)
        else -> stringResource(R.string.bg_status_paused)
    }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = session.label(),
            style = MaterialTheme.typography.titleSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = "$done/$total \u00b7 $status",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        LinearProgressIndicator(
            progress = { if (total > 0) done / total.toFloat() else 0f },
            modifier = Modifier.fillMaxWidth()
        )
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            TextButton(onClick = { viewModel.openBackground(session); onClose() }) {
                Text(stringResource(R.string.bg_open))
            }
            when (session.appStatus) {
                AppStatus.DOWNLOADING ->
                    TextButton(onClick = { viewModel.pauseSession(session) }) { Text(stringResource(R.string.pause_download)) }
                AppStatus.DOWNLOADING_COMPLETE -> Unit
                else ->
                    TextButton(onClick = { viewModel.resumeSession(session) }) { Text(stringResource(R.string.bg_resume)) }
            }
            TextButton(onClick = { viewModel.removeSession(session) }) { Text(stringResource(R.string.bg_remove)) }
        }
    }
}
