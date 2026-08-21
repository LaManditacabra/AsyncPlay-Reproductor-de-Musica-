package com.example.musicplayer.ui.profile

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.musicplayer.MusicPlayerApplication
import com.example.musicplayer.backup.BackupManager
import com.example.musicplayer.backup.ImportResult
import com.example.musicplayer.data.repository.SongRepository
import com.example.musicplayer.scraper.DownloadWorker
import com.example.musicplayer.update.UpdateManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Estado de la pantalla de perfil: contadores de la biblioteca.
 */
data class ProfileStats(
    val songCount: Int = 0,
    val playlistCount: Int = 0,
    val favoriteCount: Int = 0,
)

/**
 * Estado del flujo de backup (exportar/importar JSON).
 */
sealed interface BackupUiState {
    data object Idle : BackupUiState
    data object Working : BackupUiState
    data class ImportDone(val result: ImportResult) : BackupUiState
    data class ExportDone(val uri: Uri) : BackupUiState
    data class Error(val message: String) : BackupUiState
}

/**
 * ViewModel del perfil: expone las estadísticas de la biblioteca (canciones,
 * playlists y favoritas), el estado del sistema de actualizaciones y el
 * export/import del backup en JSON.
 */
class ProfileViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as MusicPlayerApplication
    private val repository: SongRepository = app.repository
    private val updateManager: UpdateManager = app.updateManager
    private val backupManager = BackupManager(repository)

    /** Estadísticas agregadas de la biblioteca, reactivas a los cambios. */
    val stats: StateFlow<ProfileStats> = combine(
        repository.songs(),
        repository.playlistsWithCount(),
    ) { songs, playlists ->
        ProfileStats(
            songCount = songs.size,
            playlistCount = playlists.size,
            favoriteCount = songs.count { it.isFavorite },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ProfileStats())

    /** Estado del ciclo de actualización (compartido con el resto de la app). */
    val updateState: StateFlow<UpdateManager.UpdateState> = updateManager.state

    private val _backupState = MutableStateFlow<BackupUiState>(BackupUiState.Idle)
    val backupState: StateFlow<BackupUiState> = _backupState.asStateFlow()

    /** Lanza el chequeo manual de actualizaciones. */
    fun checkForUpdates() {
        viewModelScope.launch { updateManager.checkForUpdates() }
    }

    fun downloadUpdate() = updateManager.downloadUpdate()

    fun installUpdate() = updateManager.installUpdate()

    fun dismissUpdate() = updateManager.dismiss()

    /**
     * Exporta el backup al [uri] elegido por el usuario (SAF).
     */
    fun exportBackup(uri: Uri) {
        if (_backupState.value is BackupUiState.Working) return
        _backupState.value = BackupUiState.Working
        viewModelScope.launch {
            try {
                val json = backupManager.exportToJson()
                withContext(Dispatchers.IO) {
                    app.contentResolver.openOutputStream(uri)?.use { stream ->
                        stream.write(json.toByteArray())
                    } ?: throw IllegalStateException("No se pudo abrir el archivo")
                }
                _backupState.value = BackupUiState.ExportDone(uri)
            } catch (e: Exception) {
                _backupState.value = BackupUiState.Error(e.message ?: "Error al exportar")
            }
        }
    }

    /**
     * Importa el backup desde el [uri] elegido por el usuario (SAF).
     */
    fun importBackup(uri: Uri) {
        if (_backupState.value is BackupUiState.Working) return
        _backupState.value = BackupUiState.Working
        viewModelScope.launch {
            try {
                val json = withContext(Dispatchers.IO) {
                    app.contentResolver.openInputStream(uri)?.use { stream ->
                        stream.bufferedReader().readText()
                    } ?: throw IllegalStateException("No se pudo abrir el archivo")
                }
                val result = backupManager.importFromJson(json)
                _backupState.value = BackupUiState.ImportDone(result)
                // Con la descarga automática activada, encola todas las
                // canciones pendientes que tengan URL guardada.
                if (app.settings.autoDownloadPending.value) {
                    repository.songs().first()
                        .filter { it.isPending() && !it.youtubeUrl.isNullOrBlank() }
                        .forEach { DownloadWorker.start(app, it.youtubeUrl!!) }
                }
            } catch (e: Exception) {
                _backupState.value = BackupUiState.Error(e.message ?: "Error al importar")
            }
        }
    }

    fun dismissBackupState() {
        _backupState.value = BackupUiState.Idle
    }
}
