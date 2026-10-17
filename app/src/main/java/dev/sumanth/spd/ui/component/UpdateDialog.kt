package dev.sumanth.spd.ui.component

import androidx.compose.foundation.layout.Arrangement
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
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Column {
                            Text(stringResource(R.string.upd_current_version), style = MaterialTheme.typography.labelMedium)
                            Text(updaterViewModel.installedVersion, style = MaterialTheme.typography.bodyLarge)
                        }
                        Column {
                            Text(stringResource(R.string.upd_new_version), style = MaterialTheme.typography.labelMedium)
                            Text(release?.version ?: "", style = MaterialTheme.typography.bodyLarge)
                        }
                        val notes = release?.notes.orEmpty()
                        if (notes.isNotBlank()) {
                            Text(stringResource(R.string.upd_release_notes), style = MaterialTheme.typography.labelMedium)
                            Text(notes.take(500), style = MaterialTheme.typography.bodySmall)
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
