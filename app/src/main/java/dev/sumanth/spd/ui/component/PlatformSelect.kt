package dev.sumanth.spd.ui.component

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.IntrinsicSize
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.sumanth.spd.R
import dev.sumanth.spd.model.ContentType
import dev.sumanth.spd.model.Platform
import dev.sumanth.spd.ui.viewmodel.HomeScreenViewModel

private data class PlatformItem(
    val icon: ImageVector,
    val titleRes: Int,
    val descRes: Int,
    val platform: Platform
)

/** First home screen: "What would you like to download?" */
@Composable
fun PlatformSelect(viewModel: HomeScreenViewModel) {
    val items = listOf(
        PlatformItem(Icons.Filled.MusicNote, R.string.platform_spotify, R.string.platform_desc_music, Platform.SPOTIFY),
        PlatformItem(Icons.Filled.PlayCircle, R.string.platform_youtube, R.string.platform_desc_video_music, Platform.YOUTUBE),
        PlatformItem(Icons.Filled.Movie, R.string.platform_tiktok, R.string.platform_desc_video, Platform.TIKTOK),
        PlatformItem(Icons.Filled.Videocam, R.string.platform_kwai, R.string.platform_desc_video, Platform.KWAI),
        PlatformItem(Icons.Filled.PhotoCamera, R.string.platform_instagram, R.string.platform_desc_reels, Platform.INSTAGRAM),
        PlatformItem(Icons.Filled.ThumbUp, R.string.platform_facebook, R.string.platform_desc_video, Platform.FACEBOOK)
    )
    val rows = items.chunked(2)

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        // Enough room (normal phone, normal text size): the three rows share the free height, so the six cards
        // all have the same size and are fully visible with no scrolling. Otherwise (small screen, landscape,
        // very large text) the cards keep a fixed equal height and the screen scrolls.
        val fits = maxHeight >= 540.dp && LocalDensity.current.fontScale <= 1.15f
        if (fits) {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                PlatformTitle()
                Column(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    rows.forEach { PlatformRow(it, viewModel, Modifier.weight(1f)) }
                }
            }
        } else {
            Column(
                modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                PlatformTitle()
                rows.forEach { PlatformRow(it, viewModel, Modifier.heightIn(min = 168.dp).height(IntrinsicSize.Max)) }
            }
        }
    }
}

@Composable
private fun PlatformTitle() {
    Text(
        text = stringResource(R.string.choose_platform_title),
        style = MaterialTheme.typography.headlineSmall,
        fontWeight = FontWeight.Bold
    )
}

@Composable
private fun PlatformRow(row: List<PlatformItem>, viewModel: HomeScreenViewModel, modifier: Modifier) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        row.forEach { item ->
            ChoiceCard(
                icon = item.icon,
                title = stringResource(item.titleRes),
                description = stringResource(item.descRes),
                onClick = { viewModel.selectPlatform(item.platform) },
                modifier = Modifier.weight(1f).fillMaxHeight()
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
            modifier = Modifier.fillMaxWidth().heightIn(min = 184.dp)
        )
        ChoiceCard(
            icon = Icons.Filled.MusicNote,
            title = stringResource(R.string.type_music),
            description = stringResource(R.string.type_music_desc),
            onClick = { viewModel.contentType = ContentType.MUSIC },
            modifier = Modifier.fillMaxWidth().heightIn(min = 184.dp)
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
    Card(onClick = onClick, modifier = modifier) {
        Column(
            modifier = Modifier.fillMaxSize().padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterVertically)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(36.dp)
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
