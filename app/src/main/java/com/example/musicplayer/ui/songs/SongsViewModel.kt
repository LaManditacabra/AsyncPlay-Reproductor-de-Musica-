package com.example.musicplayer.ui.songs

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.musicplayer.MusicPlayerApplication
import com.example.musicplayer.data.model.Song
import com.example.musicplayer.player.PlaybackController
import com.example.musicplayer.scraper.DownloadWorker
import com.example.musicplayer.update.UpdateManager
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * ViewModel de la pantalla de canciones.
 *
 * Consume el repository (lista de canciones en Room), el [PlaybackController]
 * (estado del reproductor) y el [UpdateManager] (actualizaciones), y los expone
 * como [StateFlow] para la UI. Toda la lógica de negocio queda fuera de los composables.
 */
class SongsViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as MusicPlayerApplication
    private val repository = app.repository
    private val playbackController = app.playbackController
    private val updateManager = app.updateManager

    /** Lista reactiva de canciones guardadas en la base de datos. */
    val songs: StateFlow<List<Song>> = repository.songs
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Estado observable del reproductor (canción actual, playing, posición...). */
    val playerState: StateFlow<PlaybackController.PlaybackState> = playbackController.state

    /** Estado observable del sistema de actualizaciones. */
    val updateState: StateFlow<UpdateManager.UpdateState> = updateManager.state

    init {
        // Comprueba actualizaciones al abrir la app.
        viewModelScope.launch { updateManager.checkForUpdates() }
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

    /** Lanza la descarga de un vídeo de YouTube en segundo plano (WorkManager). */
    fun downloadFromUrl(videoUrl: String) {
        DownloadWorker.start(getApplication(), videoUrl)
    }

    // ------------------------------------------------------------------
    // Actualizaciones
    // ------------------------------------------------------------------

    fun downloadUpdate() = updateManager.downloadUpdate()

    fun installUpdate() = updateManager.installUpdate()

    fun dismissUpdate() = updateManager.dismiss()
}