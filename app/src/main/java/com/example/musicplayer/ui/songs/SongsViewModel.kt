package com.example.musicplayer.ui.songs

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.musicplayer.MusicPlayerApplication
import com.example.musicplayer.data.model.Song
import com.example.musicplayer.player.PlaybackController
import com.example.musicplayer.scraper.DownloadWorker
import com.example.musicplayer.update.UpdateManager
import java.io.File
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
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

    init {
        // Comprueba actualizaciones al abrir la app.
        viewModelScope.launch { updateManager.checkForUpdates() }
        // Reanuda la última sesión de reproducción si quedó guardada.
        restoreLastSession()
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

    /** Lanza la descarga de un vídeo de YouTube en segundo plano (WorkManager). */
    fun downloadFromUrl(videoUrl: String) {
        DownloadWorker.start(getApplication(), videoUrl)
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