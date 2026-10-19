package dev.sumanth.spd.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.Icon
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import dev.sumanth.spd.utils.NoteBlock
import dev.sumanth.spd.utils.ReleaseNotes
import dev.sumanth.spd.utils.SharedPref
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.sumanth.spd.R
import dev.sumanth.spd.service.UpdateError
import dev.sumanth.spd.service.UpdateStage
import dev.sumanth.spd.service.UpdateState
import dev.sumanth.spd.ui.viewmodel.UpdaterViewModel

@Composable
fun UpdateDialog(updaterViewModel: UpdaterViewModel) {
    when (UpdateState.stage) {
        UpdateStage.AVAILABLE -> {
            val release = UpdateState.release
            AlertDialog(
                onDismissRequest = { updaterViewModel.notNow() },
                title = { Text(stringResource(R.string.upd_title_available)) },
                text = {
                    val language = SharedPref.readLanguage(LocalContext.current)
                    val blocks = remember(release?.notes, language) { ReleaseNotes.parse(release?.notes.orEmpty(), language) }
                    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            VersionBox(
                                label = stringResource(R.string.upd_current_version),
                                value = updaterViewModel.installedVersion,
                                highlighted = false,
                                modifier = Modifier.weight(1f)
                            )
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowForward,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            VersionBox(
                                label = stringResource(R.string.upd_new_version),
                                value = release?.version ?: "",
                                highlighted = true,
                                modifier = Modifier.weight(1f)
                            )
                        }
                        if (blocks.isNotEmpty()) {
                            Text(
                                stringResource(R.string.upd_whats_new),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            ReleaseNotesView(blocks)
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { updaterViewModel.downloadApk() }) {
                        Text(stringResource(R.string.upd_btn_download))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { updaterViewModel.notNow() }) {
                        Text(stringResource(R.string.upd_btn_not_now))
                    }
                }
            )
        }

        UpdateStage.DOWNLOADING -> AlertDialog(
            onDismissRequest = {},
            title = { Text(stringResource(R.string.upd_downloading)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    val progress = UpdateState.progress
                    if (progress >= 0f) {
                        LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
                        Text("${(progress * 100).toInt()}%", style = MaterialTheme.typography.bodyMedium)
                    } else {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { updaterViewModel.cancelDownload() }) {
                    Text(stringResource(R.string.upd_cancel))
                }
            }
        )

        UpdateStage.DOWNLOADED -> AlertDialog(
            onDismissRequest = { updaterViewModel.dismiss() },
            title = { Text(stringResource(R.string.upd_download_complete)) },
            confirmButton = {
                TextButton(onClick = { updaterViewModel.installApk() }) {
                    Text(stringResource(R.string.upd_btn_install))
                }
            },
            dismissButton = {
                TextButton(onClick = { updaterViewModel.dismiss() }) {
                    Text(stringResource(R.string.upd_close))
                }
            }
        )

        UpdateStage.NEEDS_PERMISSION -> AlertDialog(
            onDismissRequest = { updaterViewModel.dismiss() },
            title = { Text(stringResource(R.string.upd_permission_title)) },
            text = { Text(stringResource(R.string.upd_permission_msg)) },
            confirmButton = {
                TextButton(onClick = { updaterViewModel.openInstallSettings() }) {
                    Text(stringResource(R.string.upd_open_settings))
                }
            },
            dismissButton = {
                TextButton(onClick = { updaterViewModel.dismiss() }) {
                    Text(stringResource(R.string.upd_cancel))
                }
            }
        )

        UpdateStage.INSTALLING -> AlertDialog(
            // Close = back to "download complete" so the user is never stuck if the system dialog is dismissed
            onDismissRequest = {},
            title = { Text(stringResource(R.string.upd_installing)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    Text(stringResource(R.string.upd_installing_msg), style = MaterialTheme.typography.bodyMedium)
                }
            },
            confirmButton = {
                TextButton(onClick = { UpdateState.stage = UpdateStage.DOWNLOADED }) {
                    Text(stringResource(R.string.upd_close))
                }
            }
        )

        UpdateStage.INSTALLED -> MessageDialog(
            title = stringResource(R.string.upd_installed),
            message = null,
            onClose = { updaterViewModel.dismiss() }
        )

        UpdateStage.INSTALL_CANCELLED -> AlertDialog(
            onDismissRequest = { updaterViewModel.dismiss() },
            title = { Text(stringResource(R.string.upd_cancelled_title)) },
            text = { Text(stringResource(R.string.upd_cancelled_msg)) },
            confirmButton = {
                TextButton(onClick = { updaterViewModel.installApk() }) {
                    Text(stringResource(R.string.upd_btn_install))
                }
            },
            dismissButton = {
                TextButton(onClick = { updaterViewModel.dismiss() }) {
                    Text(stringResource(R.string.upd_close))
                }
            }
        )

        UpdateStage.INSTALL_ERROR -> {
            val message = if (UpdateState.error == UpdateError.CONFLICT) {
                stringResource(R.string.upd_error_conflict)
            } else {
                stringResource(R.string.upd_error_generic, UpdateState.errorDetail ?: "")
            }
            MessageDialog(
                title = stringResource(R.string.upd_error_title),
                message = message,
                onClose = { updaterViewModel.dismiss() }
            )
        }

        UpdateStage.INVALID_APK -> AlertDialog(
            onDismissRequest = { updaterViewModel.dismiss() },
            title = { Text(stringResource(R.string.upd_invalid_title)) },
            text = { Text(stringResource(R.string.upd_invalid_msg)) },
            confirmButton = {
                TextButton(onClick = { updaterViewModel.downloadApk() }) {
                    Text(stringResource(R.string.upd_retry))
                }
            },
            dismissButton = {
                TextButton(onClick = { updaterViewModel.dismiss() }) {
                    Text(stringResource(R.string.upd_close))
                }
            }
        )

        UpdateStage.DOWNLOAD_FAILED -> AlertDialog(
            onDismissRequest = { updaterViewModel.dismiss() },
            title = { Text(stringResource(R.string.upd_download_failed_title)) },
            text = { Text(stringResource(R.string.upd_download_failed_msg)) },
            confirmButton = {
                TextButton(onClick = { updaterViewModel.downloadApk() }) {
                    Text(stringResource(R.string.upd_retry))
                }
            },
            dismissButton = {
                TextButton(onClick = { updaterViewModel.dismiss() }) {
                    Text(stringResource(R.string.upd_close))
                }
            }
        )

        UpdateStage.UP_TO_DATE -> MessageDialog(
            title = stringResource(R.string.upd_up_to_date),
            message = null,
            onClose = { updaterViewModel.dismiss() }
        )

        UpdateStage.CHECK_FAILED -> {
            val message = when (UpdateState.error) {
                UpdateError.NO_RELEASE -> stringResource(R.string.upd_no_release)
                UpdateError.NO_APK -> stringResource(R.string.upd_no_apk)
                else -> stringResource(R.string.upd_check_failed_msg)
            }
            MessageDialog(
                title = stringResource(R.string.upd_check_failed_title),
                message = message,
                onClose = { updaterViewModel.dismiss() }
            )
        }

        UpdateStage.IDLE, UpdateStage.CHECKING -> Unit
    }
}

@Composable
private fun MessageDialog(title: String, message: String?, onClose: () -> Unit) {
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(title) },
        text = message?.let { { Text(it) } },
        confirmButton = {
            TextButton(onClick = onClose) { Text(stringResource(R.string.upd_ok)) }
        }
    )
}


/** Current / new version as two boxes; the new one is highlighted. */
@Composable
private fun VersionBox(label: String, value: String, highlighted: Boolean, modifier: Modifier = Modifier) {
    val container = if (highlighted) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
    val content = if (highlighted) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
    Column(
        modifier = modifier.background(container, RoundedCornerShape(12.dp)).padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(label.removeSuffix(":"), style = MaterialTheme.typography.labelMedium, color = content)
        Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = content)
    }
}

/** The changelog: group titles, bullets and notes instead of raw Markdown. Scrolls when it is long. */
@Composable
private fun ReleaseNotesView(blocks: List<NoteBlock>) {
    val codeBackground = MaterialTheme.colorScheme.surfaceVariant
    Column(
        modifier = Modifier.fillMaxWidth().heightIn(max = 260.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        blocks.forEach { block ->
            when (block) {
                is NoteBlock.Group -> Text(
                    text = block.text,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 4.dp)
                )

                is NoteBlock.Bullet -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("\u2022", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodyMedium)
                    Text(
                        text = styled(block.text, codeBackground),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f)
                    )
                }

                is NoteBlock.Quote -> Text(
                    text = styled(block.text, codeBackground),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))
                        .padding(10.dp)
                )

                is NoteBlock.Paragraph -> Text(
                    text = styled(block.text, codeBackground),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}

private val inlineMarkdown = Regex("\\*\\*(.+?)\\*\\*|`(.+?)`")

/** **bold** and `code` inside a line. */
private fun styled(text: String, codeBackground: Color): AnnotatedString = buildAnnotatedString {
    var last = 0
    for (match in inlineMarkdown.findAll(text)) {
        append(text.substring(last, match.range.first))
        val bold = match.groups[1]?.value
        val code = match.groups[2]?.value
        if (bold != null) {
            withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(bold) }
        } else if (code != null) {
            withStyle(SpanStyle(fontFamily = FontFamily.Monospace, background = codeBackground)) { append(code) }
        }
        last = match.range.last + 1
    }
    append(text.substring(last))
}
