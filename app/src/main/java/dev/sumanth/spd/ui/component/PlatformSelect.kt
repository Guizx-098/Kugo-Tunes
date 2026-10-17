package dev.sumanth.spd.ui.component

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.sumanth.spd.R
import dev.sumanth.spd.model.ContentType
import dev.sumanth.spd.model.Platform
import dev.sumanth.spd.ui.viewmodel.HomeScreenViewModel

/** First home screen: "What would you like to download?" */
@Composable
fun PlatformSelect(viewModel: HomeScreenViewModel) {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = stringResource(R.string.choose_platform_title),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            ChoiceCard(
                icon = Icons.Filled.MusicNote,
                title = stringResource(R.string.platform_spotify),
                description = stringResource(R.string.platform_desc_music),
                onClick = { viewModel.selectPlatform(Platform.SPOTIFY) },
                modifier = Modifier.weight(1f)
            )
            ChoiceCard(
                icon = Icons.Filled.PlayCircle,
                title = stringResource(R.string.platform_youtube),
                description = stringResource(R.string.platform_desc_video_music),
                onClick = { viewModel.selectPlatform(Platform.YOUTUBE) },
                modifier = Modifier.weight(1f)
            )
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            ChoiceCard(
                icon = Icons.Filled.Movie,
                title = stringResource(R.string.platform_tiktok),
                description = stringResource(R.string.platform_desc_video),
                onClick = { viewModel.selectPlatform(Platform.TIKTOK) },
                modifier = Modifier.weight(1f)
            )
            ChoiceCard(
                icon = Icons.Filled.Videocam,
                title = stringResource(R.string.platform_kwai),
                description = stringResource(R.string.platform_desc_video),
                onClick = { viewModel.selectPlatform(Platform.KWAI) },
                modifier = Modifier.weight(1f)
            )
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            ChoiceCard(
                icon = Icons.Filled.PhotoCamera,
                title = stringResource(R.string.platform_instagram),
                description = stringResource(R.string.platform_desc_reels),
                onClick = { viewModel.selectPlatform(Platform.INSTAGRAM) },
                modifier = Modifier.weight(1f)
            )
            ChoiceCard(
                icon = Icons.Filled.ThumbUp,
                title = stringResource(R.string.platform_facebook),
                description = stringResource(R.string.platform_desc_video),
                onClick = { viewModel.selectPlatform(Platform.FACEBOOK) },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

/** YouTube / YouTube Music: Video or Music? */
@Composable
fun YoutubeTypeSelect(viewModel: HomeScreenViewModel) {
    BackHandler { viewModel.back() }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { viewModel.back() }) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
            }
            Text(
                text = stringResource(R.string.platform_youtube),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        }
        Text(
            text = stringResource(R.string.youtube_choose_title),
            style = MaterialTheme.typography.titleMedium
        )
        ChoiceCard(
            icon = Icons.Filled.Videocam,
            title = stringResource(R.string.type_video),
            description = stringResource(R.string.type_video_desc),
            onClick = { viewModel.contentType = ContentType.VIDEO },
            modifier = Modifier.fillMaxWidth()
        )
        ChoiceCard(
            icon = Icons.Filled.MusicNote,
            title = stringResource(R.string.type_music),
            description = stringResource(R.string.type_music_desc),
            onClick = { viewModel.contentType = ContentType.MUSIC },
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun ChoiceCard(
    icon: ImageVector,
    title: String,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(onClick = onClick, modifier = modifier.heightIn(min = 140.dp)) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(40.dp)
            )
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}
