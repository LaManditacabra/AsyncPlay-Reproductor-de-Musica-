package com.example.musicplayer.ui.songs

import android.app.Application
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.example.musicplayer.MusicPlayerApplication
import com.example.musicplayer.R
import com.example.musicplayer.data.model.Song
import com.example.musicplayer.player.PlaybackController
import com.example.musicplayer.scraper.DownloadWorker
import com.example.musicplayer.update.UpdateManager
import java.io.File
import java.io.IOException
import java.util.UUID
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * ViewModel de la pantalla de canciones.
 *
 * Consume el repository (lista de canciones en Room), el [PlaybackController]
 * (estado del reproductor) y el [UpdateManager] (actualizaciones), y los expone
 * como [StateFlow] para la UI. Toda la lógica de negocio queda fuera de los composables.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SongsViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as MusicPlayerApplication
    private val repository = app.repository
    private val playbackController = app.playbackController
    private val updateManager = app.updateManager

    /** Permite filtrar la lista mostrando solo favoritas. */
    private val _favoritesOnly = MutableStateFlow(false)
    val favoritesOnly: StateFlow<Boolean> = _favoritesOnly.asStateFlow()

    /** Lista reactiva de canciones (filtrable por favoritas). */
    val songs: StateFlow<List<Song>> =
        _favoritesOnly.flatMapLatest { repository.songs(it) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Estado observable del reproductor (canción actual, playing, posición...). */
    val playerState: StateFlow<PlaybackController.PlaybackState> = playbackController.state

    /** Estado observable del sistema de actualizaciones. */
    val updateState: StateFlow<UpdateManager.UpdateState> = updateManager.state

    /** Mensajes informativos para mostrar como snackbar (descargas/importaciones). */
    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 16)
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    /** Representa una descarga de YouTube en curso (para mostrar progreso en la UI). */
    data class ActiveDownload(
        val title: String?,
        val status: String,
        val progress: Int,
    )

    /** Descargas de YouTube en curso (RUNNING), con su estado y progreso. */
    val activeDownloads: StateFlow<List<ActiveDownload>> =
        WorkManager.getInstance(getApplication())
            .getWorkInfosByTagFlow(DownloadWorker.TAG)
            .map { infos ->
                infos
                    .filter { it.state == WorkInfo.State.RUNNING }
                    .map { info ->
                        val output = info.progress
                        ActiveDownload(
                            title = output.getString(DownloadWorker.KEY_TITLE),
                            status = output.getString(DownloadWorker.KEY_STATUS).orEmpty(),
                            progress = output.getInt(DownloadWorker.KEY_PROGRESS, 0),
                        )
                    }
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val seenDownloadIds = mutableSetOf<UUID>()

    init {
        // Comprueba actualizaciones al abrir la app.
        viewModelScope.launch { updateManager.checkForUpdates() }
        // Reanuda la última sesión de reproducción si quedó guardada.
        restoreLastSession()
        // Observa el resultado de las descargas en segundo plano.
        observeDownloadResults()
    }

    /** Alterna el filtro de favoritas. */
    fun toggleFavoritesFilter() {
        _favoritesOnly.value = !_favoritesOnly.value
    }

    /**
     * Reproduce la canción tocada y encola el resto como playlist,
     * empezando por la posición de la canción seleccionada.
     */
    fun onSongClick(song: Song) {
        val currentSongs = songs.value
        val index = currentSongs.indexOfFirst { it.id == song.id }
        if (index >= 0) {
            playbackController.play(currentSongs, index)
        }
    }

    /** Pausa o reanuda la reproducción actual. */
    fun togglePlayPause() = playbackController.togglePlayPause()

    /** Busca (seek) a una posición concreta. */
    fun seekTo(positionMs: Long) = playbackController.seekTo(positionMs)

    fun skipToNext() = playbackController.skipToNext()

    fun skipToPrevious() = playbackController.skipToPrevious()

    fun toggleShuffle() = playbackController.toggleShuffle()

    fun cycleRepeatMode() = playbackController.cycleRepeatMode()

    /** Marca/desmarca la canción como favorita. */
    fun toggleFavorite(song: Song) {
        viewModelScope.launch {
            repository.setFavorite(song.id, !song.isFavorite)
        }
    }

    /**
     * Elimina una canción: borra los archivos del disco y la fila de la base de datos.
     */
    fun deleteSong(song: Song) {
        viewModelScope.launch {
            runCatching { File(song.localPath).delete() }
            // La portada se guarda como una URI `file://`.
            runCatching {
                val path = android.net.Uri.parse(song.thumbnailUrl.orEmpty()).path
                if (path != null) {
                    val artwork = File(path)
                    if (artwork.exists()) artwork.delete()
                }
            }
            repository.deleteSong(song)
        }
    }

    /**
     * Lanza la descarga de un vídeo de YouTube en segundo plano (WorkManager).
     * El resultado (éxito/error) se reporta por [messages].
     */
    fun downloadFromUrl(videoUrl: String) {
        DownloadWorker.start(getApplication(), videoUrl)
    }

    /**
     * Importa audios elegidos por el usuario (SAF, sin permisos de almacenamiento):
     * los copia a la carpeta privada de la app y los añade a la biblioteca.
     */
    fun importSongs(uris: List<Uri>) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            var imported = 0
            for (uri in uris) {
                val ok = try {
                    importOne(uri)
                    true
                } catch (e: Exception) {
                    false
                }
                if (ok) imported++
            }
            _messages.tryEmit(
                if (imported > 0) {
                    app.getString(R.string.import_success, imported)
                } else {
                    app.getString(R.string.import_failed)
                },
            )
        }
    }

    private suspend fun importOne(uri: Uri) {
        val resolver = app.contentResolver
        val displayName = resolver.query(uri, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                cursor.getString(cursor.getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME))
            } else {
                null
            }
        } ?: uri.lastPathSegment ?: "cancion"

        // Copia a filesDir/music/imported/<nombre original>.
        val file = File(app.filesDir, "$IMPORT_DIR/$displayName")
        file.parentFile?.mkdirs()
        val input = resolver.openInputStream(uri) ?: throw IOException("No se pudo leer el archivo")
        input.use { source -> file.outputStream().use { target -> source.copyTo(target) } }

        repository.addSong(
            Song(
                title = displayName.substringBeforeLast('.'),
                artist = app.getString(R.string.local_artist),
                durationMs = readDuration(file),
                localPath = file.absolutePath,
                thumbnailUrl = null,
            ),
        )
    }

    /** Lee la duración de un archivo local (best-effort; 0 si no se puede). */
    private fun readDuration(file: File): Long = runCatching {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(file.absolutePath)
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLong() ?: 0L
        } finally {
            retriever.release()
        }
    }.getOrDefault(0L)

    /** Observa el resultado de las descargas y lo traduce a [messages]. */
    private fun observeDownloadResults() {
        viewModelScope.launch {
            WorkManager.getInstance(getApplication())
                .getWorkInfosByTagFlow(DownloadWorker.TAG)
                .collect { infos ->
                    for (info in infos) {
                        when (info.state) {
                            WorkInfo.State.SUCCEEDED -> {
                                if (seenDownloadIds.add(info.id)) {
                                    val title = info.outputData.getString(DownloadWorker.KEY_SONG_TITLE)
                                    _messages.tryEmit(app.getString(R.string.download_added, title.orEmpty()))
                                }
                            }
                            WorkInfo.State.FAILED -> {
                                if (seenDownloadIds.add(info.id)) {
                                    val error = info.outputData.getString(DownloadWorker.KEY_ERROR)
                                        ?: app.getString(R.string.download_failed, "")
                                    _messages.tryEmit(app.getString(R.string.download_failed, error))
                                }
                            }
                            else -> Unit
                        }
                    }
                }
        }
    }

    // ------------------------------------------------------------------
    // Reanudar sesión
    // ------------------------------------------------------------------

    private fun restoreLastSession() {
        viewModelScope.launch {
            val savedSongId = app.playbackPreferences.lastSongId
            if (savedSongId < 0) return@launch
            val savedPosition = app.playbackPreferences.lastPositionMs
            val songs = repository.songs(false).first()
            playbackController.restorePosition(songs, savedSongId, savedPosition)
        }
    }

    // ------------------------------------------------------------------
    // Actualizaciones
    // ------------------------------------------------------------------

    fun downloadUpdate() = updateManager.downloadUpdate()

    fun installUpdate() = updateManager.installUpdate()

    fun dismissUpdate() = updateManager.dismiss()

    private companion object {
        const val IMPORT_DIR = "music/imported"
    }
}