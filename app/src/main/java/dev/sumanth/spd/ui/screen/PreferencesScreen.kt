package dev.sumanth.spd.ui.screen

import android.app.Activity
import android.content.Intent
import android.os.Environment
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Launch
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Launch
import androidx.compose.material3.Button
import androidx.compose.material3.TextButton
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.sumanth.spd.R
import dev.sumanth.spd.service.UpdateStage
import dev.sumanth.spd.service.UpdateState
import dev.sumanth.spd.ui.viewmodel.UpdaterViewModel
import dev.sumanth.spd.ui.viewmodel.PreferencesScreenViewModel
import dev.sumanth.spd.utils.SharedPref

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PreferencesScreen(
    viewModel: PreferencesScreenViewModel = viewModel(),
    updaterViewModel: UpdaterViewModel
) {

    val uriHandler = LocalUriHandler.current
    val context = LocalContext.current
    val displayPath = remember(viewModel.downloadPath) { viewModel.getDisplayPath() }
    val folderLost = remember(viewModel.downloadPath) {
        viewModel.downloadPath != null && !viewModel.sharedPref.isCustomFolderAccessible()
    }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        viewModel.handleFolderSelection(uri)
    }


    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(stringResource(R.string.general_settings), style = MaterialTheme.typography.titleMedium)

        Card(
            onClick = { launcher.launch(null) },
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(stringResource(R.string.download_location), style = MaterialTheme.typography.labelLarge)
                Text(
                    text = displayPath ?: stringResource(R.string.default_folder),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = stringResource(R.string.tap_to_change),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
                if (viewModel.downloadPath != null) {
                    if (folderLost) {
                        Text(
                            text = stringResource(R.string.folder_lost_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                    TextButton(onClick = { viewModel.useDefaultFolder() }) {
                        Text(stringResource(R.string.use_default_folder))
                    }
                }
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.subfolder_title), style = MaterialTheme.typography.bodyLarge)
                    Text(
                        text = stringResource(R.string.subfolder_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = viewModel.createSubfolder,
                    onCheckedChange = {
                        viewModel.createSubfolder = it
                        viewModel.sharedPref.storeCreateSubfolder(it)
                    }
                )
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(stringResource(R.string.concurrent_title), style = MaterialTheme.typography.bodyLarge)
                Text(
                    text = stringResource(R.string.concurrent_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    (1..4).forEach { count ->
                        FilterChip(
                            selected = viewModel.concurrentDownloads == count,
                            onClick = {
                                viewModel.concurrentDownloads = count
                                viewModel.sharedPref.storeConcurrentDownloads(count)
                            },
                            label = { Text(count.toString()) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                    Text(stringResource(R.string.wifi_only_title), style = MaterialTheme.typography.bodyLarge)
                    Text(
                        text = stringResource(R.string.wifi_only_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = viewModel.wifiOnly,
                    onCheckedChange = {
                        viewModel.wifiOnly = it
                        viewModel.sharedPref.storeWifiOnly(it)
                    }
                )
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(stringResource(R.string.language_title), style = MaterialTheme.typography.bodyLarge)
                Text(
                    text = stringResource(R.string.language_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(
                        SharedPref.LANG_EN to "English",
                        SharedPref.LANG_PT_BR to "Português (Brasil)"
                    ).forEach { (tag, label) ->
                        FilterChip(
                            selected = viewModel.language == tag,
                            onClick = {
                                if (viewModel.language != tag) {
                                    viewModel.language = tag
                                    viewModel.sharedPref.storeLanguage(tag)
                                    // Re-creates the screen so every string is re-read in the new language
                                    (context as? Activity)?.recreate()
                                }
                            },
                            label = { Text(label) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }

        Text(stringResource(R.string.updates), style = MaterialTheme.typography.titleMedium)
        Card(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = stringResource(R.string.auto_update),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f).padding(end = 12.dp)
                )
                Switch(
                    checked = viewModel.autoUpdateCheck,
                    onCheckedChange = {
                        viewModel.autoUpdateCheck = it
                        viewModel.sharedPref.storeAutoUpdateCheck(it)
                    }
                )
            }
        }

        Button(
            onClick = { updaterViewModel.checkForUpdate(manual = true) },
            enabled = UpdateState.stage != UpdateStage.CHECKING,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                stringResource(
                    if (UpdateState.stage == UpdateStage.CHECKING) R.string.checking else R.string.check_updates
                )
            )
        }

        Text(stringResource(R.string.about), style = MaterialTheme.typography.titleMedium)
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().clickable { viewModel.openProfile(uriHandler) },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Info, contentDescription = stringResource(R.string.cd_developer), modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    // One Text so a long translation wraps as a normal sentence instead of overflowing the card
                    Text(
                        text = buildAnnotatedString {
                            append(stringResource(R.string.developed_by).trim())
                            append(" ")
                            withStyle(
                                SpanStyle(
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            ) { append(viewModel.developerName) }
                        },
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(
                        Icons.AutoMirrored.Filled.Launch,
                        contentDescription = stringResource(R.string.cd_open_profile),
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth().clickable { viewModel.openGitHub(uriHandler) },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.source_code),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(
                        Icons.AutoMirrored.Filled.Launch,
                        contentDescription = stringResource(R.string.cd_open_link),
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                }
                Text(
                    text = stringResource(R.string.licenses),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.clickable {
                        uriHandler.openUri("https://github.com/Guizx-098/spd-turbo/blob/main/LICENSE")
                    }
                )
                Text(
                    text = stringResource(R.string.version_label, viewModel.packageInfo.versionName ?: ""),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}