package com.example.musicplayer.ui.profile

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.musicplayer.BuildConfig
import com.example.musicplayer.R
import com.example.musicplayer.ui.update.UpdateDialog

/**
 * Sección "Perfil": cabecera con la identidad de la app, estadísticas de la
 * biblioteca (canciones, playlists, favoritas) y accesos a ajustes y al
 * sistema de actualizaciones.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    onNavigateToSettings: (() -> Unit)? = null,
    viewModel: ProfileViewModel = viewModel(),
) {
    val stats by viewModel.stats.collectAsStateWithLifecycle()
    val updateState by viewModel.updateState.collectAsStateWithLifecycle()
    val backupState by viewModel.backupState.collectAsStateWithLifecycle()

    // SAF: crear archivo para exportar / elegir archivo para importar.
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri -> uri?.let(viewModel::exportBackup) }
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri -> uri?.let(viewModel::importBackup) }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(title = { Text(stringResource(R.string.profile_title)) })
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        ) {
            ProfileHeader()

            Spacer(modifier = Modifier.height(24.dp))

            StatsRow(stats = stats)

            Spacer(modifier = Modifier.height(24.dp))

            SectionTitle(text = stringResource(R.string.profile_section_general))
            SettingsItem(
                icon = Icons.Filled.Settings,
                title = stringResource(R.string.settings_title),
                subtitle = stringResource(R.string.profile_settings_subtitle),
                onClick = onNavigateToSettings,
            )
            UpdateItem(viewModel = viewModel)

            Spacer(modifier = Modifier.height(16.dp))

            SectionTitle(text = stringResource(R.string.profile_section_backup))
            BackupItem(
                icon = Icons.Filled.Upload,
                title = stringResource(R.string.profile_export_backup),
                subtitle = stringResource(R.string.profile_export_backup_subtitle),
                enabled = backupState !is BackupUiState.Working,
                onClick = { exportLauncher.launch(DEFAULT_BACKUP_FILE_NAME) },
            )
            BackupItem(
                icon = Icons.Filled.Download,
                title = stringResource(R.string.profile_import_backup),
                subtitle = stringResource(R.string.profile_import_backup_subtitle),
                enabled = backupState !is BackupUiState.Working,
                onClick = { importLauncher.launch(arrayOf("application/json")) },
            )
        }
    }

    UpdateDialog(
        updateState = updateState,
        onDownload = viewModel::downloadUpdate,
        onInstall = viewModel::installUpdate,
        onDismiss = viewModel::dismissUpdate,
    )

    if (backupState is BackupUiState.Working) {
        BackupWorkingDialog()
    } else if (backupState is BackupUiState.ImportDone ||
        backupState is BackupUiState.ExportDone ||
        backupState is BackupUiState.Error
    ) {
        BackupResultDialog(
            state = backupState,
            onDismiss = viewModel::dismissBackupState,
        )
    }
}

/** Cabecera: avatar circular, nombre de la app y versión instalada. */
@Composable
private fun ProfileHeader() {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer,
        ) {
            Icon(
                imageVector = Icons.Filled.Person,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier
                    .size(72.dp)
                    .padding(18.dp),
            )
        }
        Spacer(modifier = Modifier.size(16.dp))
        Column {
            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = stringResource(R.string.profile_version, BuildConfig.VERSION_NAME),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Fila de tres tarjetas con las estadísticas de la biblioteca. */
@Composable
private fun StatsRow(stats: ProfileStats) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        StatCard(
            modifier = Modifier.weight(1f),
            icon = Icons.Filled.MusicNote,
            value = stats.songCount.toString(),
            label = stringResource(R.string.nav_songs),
        )
        StatCard(
            modifier = Modifier.weight(1f),
            icon = Icons.Filled.LibraryMusic,
            value = stats.playlistCount.toString(),
            label = stringResource(R.string.nav_library),
        )
        StatCard(
            modifier = Modifier.weight(1f),
            icon = Icons.Filled.Favorite,
            value = stats.favoriteCount.toString(),
            label = stringResource(R.string.favorites_title),
        )
    }
}

/** Tarjeta individual de estadística. */
@Composable
private fun StatCard(
    modifier: Modifier = Modifier,
    icon: ImageVector,
    value: String,
    label: String,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column(
            modifier = Modifier.padding(vertical = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(22.dp),
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Título de sección. */
@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
    )
    Spacer(modifier = Modifier.height(8.dp))
}

/** Ítem de lista genérico (icono + título + subtítulo + chevron). */
@Composable
private fun SettingsItem(
    icon: ImageVector,
    title: String,
    subtitle: String?,
    onClick: (() -> Unit)?,
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick ?: {},
        enabled = onClick != null,
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(modifier = Modifier.size(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(text = title, style = MaterialTheme.typography.bodyLarge)
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Icon(
                imageVector = Icons.Filled.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Ítem para lanzar el chequeo manual de actualizaciones. */
@Composable
private fun UpdateItem(viewModel: ProfileViewModel) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth(),
        onClick = viewModel::checkForUpdates,
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Filled.SystemUpdate,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(modifier = Modifier.size(14.dp))
            Column {
                Text(
                    text = stringResource(R.string.profile_check_updates),
                    style = MaterialTheme.typography.bodyLarge,
                )
                Text(
                    text = stringResource(R.string.profile_check_updates_subtitle),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** Ítem de la sección de backup (exportar / importar). */
@Composable
private fun BackupItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick,
        enabled = enabled,
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(modifier = Modifier.size(14.dp))
            Column {
                Text(text = title, style = MaterialTheme.typography.bodyLarge)
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** Diálogo de progreso mientras se procesa el backup. */
@Composable
private fun BackupWorkingDialog() {
    AlertDialog(
        onDismissRequest = {},
        title = { Text(stringResource(R.string.backup_working_title)) },
        text = { Text(stringResource(R.string.backup_working_body)) },
        confirmButton = {},
    )
}

/** Diálogo con el resultado de exportar/importar (o el error). */
@Composable
private fun BackupResultDialog(
    state: BackupUiState,
    onDismiss: () -> Unit,
) {
    val message = when (state) {
        is BackupUiState.ExportDone -> stringResource(R.string.backup_export_done)
        is BackupUiState.ImportDone -> stringResource(
            R.string.backup_import_done,
            state.result.songsRestored + state.result.songsPending,
            state.result.playlistsCreated,
        ) + if (state.result.songsPending > 0) {
            "\n" + stringResource(R.string.backup_import_pending, state.result.songsPending)
        } else {
            ""
        }
        is BackupUiState.Error -> stringResource(R.string.backup_error, state.message)
        else -> return
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.backup_result_title)) },
        text = { Text(message) },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_ok))
            }
        },
    )
}

/** Nombre por defecto del archivo de backup. */
private const val DEFAULT_BACKUP_FILE_NAME = "asyncplay_backup.json"
