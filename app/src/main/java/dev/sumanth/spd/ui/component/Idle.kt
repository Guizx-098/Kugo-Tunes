package dev.sumanth.spd.ui.component

import androidx.compose.ui.res.stringResource
import dev.sumanth.spd.R
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.activity.compose.BackHandler
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.sumanth.spd.model.AudioFormat
import dev.sumanth.spd.model.ContentType
import dev.sumanth.spd.model.Platform
import dev.sumanth.spd.ui.viewmodel.HomeScreenViewModel


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Idle(viewModel: HomeScreenViewModel) {
    val platform = viewModel.platform ?: Platform.SPOTIFY
    val isMusic = platform == Platform.YOUTUBE && viewModel.contentType == ContentType.MUSIC

    BackHandler(enabled = viewModel.platform != null) { viewModel.back() }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        if (viewModel.platform != null) {
            IconButton(onClick = { viewModel.back() }) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {

                val title = when {
                    platform == Platform.SPOTIFY -> stringResource(R.string.playlist_details)
                    platform == Platform.TIKTOK -> stringResource(R.string.platform_tiktok)
                    platform == Platform.KWAI -> stringResource(R.string.platform_kwai)
                    platform == Platform.INSTAGRAM -> stringResource(R.string.link_title_reel)
                    platform == Platform.FACEBOOK -> stringResource(R.string.platform_facebook)
                    isMusic -> stringResource(R.string.link_title_music)
                    else -> stringResource(R.string.link_title_video)
                }
                Text(title, style = MaterialTheme.typography.titleMedium)

                OutlinedTextField(
                    value = viewModel.spotifyLink,
                    onValueChange = { viewModel.spotifyLink = it },
                    label = {
                        Text(
                            stringResource(
                                if (platform == Platform.SPOTIFY) R.string.link_label else R.string.paste_link
                            )
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3
                )

                when {
                    platform == Platform.SPOTIFY -> {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(stringResource(R.string.convert_to_mp3), style = MaterialTheme.typography.bodyLarge)
                            Switch(checked = viewModel.convertToMp3, onCheckedChange = { viewModel.convertToMp3 = it })
                        }
                    }

                    isMusic -> {
                        Text(stringResource(R.string.choose_format), style = MaterialTheme.typography.bodyLarge)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            AudioFormat.entries.forEach { format ->
                                FilterChip(
                                    selected = viewModel.audioFormat == format,
                                    onClick = { viewModel.audioFormat = format },
                                    label = { Text(format.name) },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }

                    platform == Platform.TIKTOK || platform == Platform.KWAI ||
                        platform == Platform.INSTAGRAM || platform == Platform.FACEBOOK -> {
                        Text(
                            text = stringResource(
                                when (platform) {
                                    Platform.INSTAGRAM -> R.string.instagram_note
                                    Platform.FACEBOOK -> R.string.facebook_note
                                    else -> R.string.watermark_note
                                }
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        Button(
            onClick = { viewModel.startScraping() },
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
        ) {
            Text(stringResource(if (platform == Platform.SPOTIFY) R.string.scrape_playlist else R.string.btn_continue))
        }

    }
}
