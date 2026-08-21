package com.example.musicplayer.ui.songs

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.example.musicplayer.MusicPlayerApplication
import com.example.musicplayer.R
import com.example.musicplayer.data.model.Playlist
import com.example.musicplayer.data.model.Song
import com.example.musicplayer.player.PlaybackController
import com.example.musicplayer.scraper.DownloadWorker
import com.example.musicplayer.scraper.PlaylistDownloadWorker
import com.example.musicplayer.update.UpdateManager
import java.io.File
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
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

    /** Criterio de ordenación de la biblioteca. */
    private val _sortMode = MutableStateFlow(SortMode.TITLE)
    val sortMode: StateFlow<SortMode> = _sortMode.asStateFlow()

    /** Texto de búsqueda local (filtra por título/artista). */
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    /** Lista reactiva de canciones (filtrable por orden y búsqueda). */
    val songs: StateFlow<List<Song>> =
        combine(_sortMode, _searchQuery) { sort, q -> Pair(sort, q) }
            .flatMapLatest { (sort, q) ->
                repository.songs().map { list ->
                    val filtered = if (q.isBlank()) {
                        list
                    } else {
                        list.filter {
                            it.title.contains(q, ignoreCase = true) ||
                                it.artist.contains(q, ignoreCase = true)
                        }
                    }
                    filtered.sortedWith(sort.comparator)
                }
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Playlists disponibles (para el selector "agregar a playlist"). */
    val playlists: StateFlow<List<Playlist>> =
        repository.playlists()
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
        val done: Int,
        val total: Int,
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
                            done = output.getInt(DownloadWorker.KEY_DONE, 0),
                            total = output.getInt(DownloadWorker.KEY_TOTAL, 0),
                        )
                    }
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        // Comprueba actualizaciones al abrir la app.
        viewModelScope.launch { updateManager.checkForUpdates() }
        // Reanuda la última sesión de reproducción si quedó guardada.
        restoreLastSession()
        // Observa el resultado de las descargas en segundo plano.
        observeDownloadResults()
    }

    fun setSortMode(mode: SortMode) {
        _sortMode.value = mode
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    /**
     * Reproduce la canción tocada y encola el resto como playlist,
     * empezando por la posición de la canción seleccionada.
     *
     * Si la canción no tiene archivo local (p. ej. restaurada desde un backup),
     * en su lugar lanza la re-descarga desde YouTube.
     */
    fun onSongClick(song: Song) {
        if (isPending(song)) {
            redownloadSong(song)
            return
        }
        // La cola solo incluye canciones con audio disponible, para que un
        // archivo faltante no interrumpa la reproducción al saltar de tema.
        val playable = songs.value.filter { !isPending(it) }
        val index = playable.indexOfFirst { it.id == song.id }
        if (index >= 0) {
            playbackController.play(playable, index)
        }
    }

    /**
     * Indica si la canción está "pendiente": sin archivo de audio local
     * (importada por backup) o con el archivo borrado del disco.
     */
    fun isPending(song: Song): Boolean = song.isPending()

    /** Vuelve a descargar una canción pendiente usando su URL guardada. */
    fun redownloadSong(song: Song) {
        val url = song.youtubeUrl
        if (url.isNullOrBlank()) {
            _messages.tryEmit(app.getString(R.string.redownload_no_url))
            return
        }
        DownloadWorker.start(getApplication(), url)
    }

    /** Pausa o reanuda la reproducción actual. */
    fun togglePlayPause() = playbackController.togglePlayPause()

    /** Busca (seek) a una posición concreta. */
    fun seekTo(positionMs: Long) = playbackController.seekTo(positionMs)

    fun skipToNext() = playbackController.skipToNext()

    fun skipToPrevious() = playbackController.skipToPrevious()

    /** Salta a una canción concreta de la cola (desde "Up next"). */
    fun skipToIndex(index: Int) = playbackController.skipToIndex(index)

    fun toggleShuffle() = playbackController.toggleShuffle()

    fun cycleRepeatMode() = playbackController.cycleRepeatMode()

    /** Marca/desmarca la canción como favorita. */
    fun toggleFavorite(song: Song) {
        viewModelScope.launch {
            repository.setFavorite(song.id, !song.isFavorite)
        }
    }

    /**
     * Añade una canción a una playlist existente. Si ya estaba, se avisa.
     */
    fun addSongToPlaylist(playlistId: Long, songId: Long) {
        viewModelScope.launch {
            val name = playlists.value.firstOrNull { it.id == playlistId }?.name ?: return@launch
            val added = repository.addSongToPlaylist(playlistId, songId)
            _messages.tryEmit(
                app.getString(
                    if (added) R.string.playlist_added else R.string.playlist_already_in,
                    name,
                ),
            )
        }
    }

    /** Crea una playlist nueva y le agrega la canción de una vez. */
    fun createPlaylistAndAdd(name: String, songId: Long) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch {
            val id = repository.createPlaylist(trimmed)
            repository.addSongToPlaylist(id, songId)
            _messages.tryEmit(app.getString(R.string.playlist_added, trimmed))
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
     * Lanza la descarga de un vídeo o una playlist de YouTube en segundo plano
     * (WorkManager). Las URLs con `list=` se tratan como playlist completa; si se
     * indica [playlistName], las canciones descargadas se agregan a una playlist
     * con ese nombre (creada si no existe).
     */
    fun downloadFromUrl(url: String, playlistName: String? = null) {
        val trimmed = url.trim()
        if (trimmed.isEmpty()) return
        if (isPlaylistUrl(trimmed)) {
            PlaylistDownloadWorker.start(getApplication(), trimmed, playlistName)
        } else {
            DownloadWorker.start(getApplication(), trimmed)
        }
    }

    private fun isPlaylistUrl(url: String): Boolean = url.contains("list=")

    /** Observa el resultado de las descargas y lo traduce a [messages]. */
    private fun observeDownloadResults() {
        viewModelScope.launch {
            WorkManager.getInstance(getApplication())
                .getWorkInfosByTagFlow(DownloadWorker.TAG)
                .collect { infos ->
                    for (info in infos) {
                        when (info.state) {
                            WorkInfo.State.SUCCEEDED -> {
                                // Solo notifica una vez por descarga, incluso entre sesiones.
                                if (app.playbackPreferences.isDownloadSeen(info.id)) continue
                                val output = info.outputData
                                if (output.getString(DownloadWorker.KEY_TYPE) == DownloadWorker.TYPE_PLAYLIST) {
                                    val count = output.getInt(DownloadWorker.KEY_DONE, 0)
                                    _messages.tryEmit(app.getString(R.string.playlist_downloaded, count))
                                } else {
                                    val title = output.getString(DownloadWorker.KEY_SONG_TITLE)
                                    _messages.tryEmit(app.getString(R.string.download_added, title.orEmpty()))
                                }
                            }
                            WorkInfo.State.FAILED -> {
                                if (app.playbackPreferences.isDownloadSeen(info.id)) continue
                                val error = info.outputData.getString(DownloadWorker.KEY_ERROR)
                                    ?: app.getString(R.string.download_failed, "")
                                _messages.tryEmit(app.getString(R.string.download_failed, error))
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
}

/** Criterios de ordenación de la biblioteca. */
enum class SortMode {
    TITLE,
    ARTIST,
    DURATION,
    NEWEST,
}

/** Comparador de canciones según el criterio elegido. */
private val SortMode.comparator: Comparator<Song>
    get() = when (this) {
        SortMode.TITLE -> compareBy { it.title.lowercase() }
        SortMode.ARTIST -> compareBy { it.artist.lowercase() }
        SortMode.DURATION -> compareBy { it.durationMs }
        SortMode.NEWEST -> compareByDescending { it.id }
    }