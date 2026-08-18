package com.example.musicplayer.ui.update

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.musicplayer.R
import com.example.musicplayer.update.ReleaseInfo
import com.example.musicplayer.update.UpdateManager.UpdateState

/**
 * Diálogo del sistema de actualizaciones. Muestra un estado por fase:
 * release disponible, descarga con progreso, APK listo para instalar o error.
 */
@Composable
fun UpdateDialog(
    updateState: UpdateState,
    onDownload: () -> Unit,
    onInstall: () -> Unit,
    onDismiss: () -> Unit,
) {
    when (updateState) {
        is UpdateState.Available -> AvailableDialog(updateState.release, onDownload, onDismiss)
        is UpdateState.Downloading -> DownloadingDialog(updateState.progress)
        is UpdateState.Downloaded -> DownloadedDialog(onInstall, onDismiss)
        is UpdateState.Failed -> FailedDialog(updateState.message, onDismiss)
        else -> Unit
    }
}

@Composable
private fun AvailableDialog(
    release: ReleaseInfo,
    onDownload: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.update_available_title)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    text = stringResource(R.string.update_version_label, release.tagName),
                    style = MaterialTheme.typography.titleSmall,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = release.body.ifBlank {
                        stringResource(R.string.update_default_body)
                    },
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDownload) {
                Text(stringResource(R.string.action_download))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.update_later))
            }
        },
    )
}

@Composable
private fun DownloadingDialog(progress: Int) {
    AlertDialog(
        onDismissRequest = {},
        title = { Text(stringResource(R.string.update_downloading_title)) },
        text = {
            Column {
                if (progress > 0) {
                    LinearProgressIndicator(
                        progress = { progress / 100f },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(8.dp))
                    Text("$progress%", style = MaterialTheme.typography.bodySmall)
                } else {
                    // Progreso indeterminado: aún no se conoce el total.
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
            }
        },
        confirmButton = {},
    )
}

@Composable
private fun DownloadedDialog(
    onInstall: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.update_ready_title)) },
        text = { Text(stringResource(R.string.update_ready_message)) },
        confirmButton = {
            TextButton(onClick = onInstall) {
                Text(stringResource(R.string.update_install))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.update_not_now))
            }
        },
    )
}

@Composable
private fun FailedDialog(message: String, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.update_error)) },
        text = { Text(message) },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_close))
            }
        },
    )
}