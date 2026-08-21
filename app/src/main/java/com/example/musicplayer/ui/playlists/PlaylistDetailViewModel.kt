package com.example.musicplayer.ui.playlists

import android.app.Application
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.musicplayer.MusicPlayerApplication
import com.example.musicplayer.R
import com.example.musicplayer.data.model.Song
import com.example.musicplayer.player.PlaybackController
import com.example.musicplayer.scraper.DownloadWorker
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * ViewModel del detalle de una playlist: muestra sus canciones y permite
 * reproducirlas en orden, quitar canciones o marcar favoritas.
 */
class PlaylistDetailViewModel(
    application: Application,
    private val playlistId: Long,
) : AndroidViewModel(application) {

    private val app = application as MusicPlayerApplication

    /** Nombre de la playlist (para el título de la pantalla). */
    val playlistName: StateFlow<String?> =
        app.repository.playlist(playlistId)
            .map { it?.name }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Canciones de la playlist, en su orden. */
    val songs: StateFlow<List<Song>> =
        app.repository.playlistSongs(playlistId)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Estado del reproductor (canción actual, playing...). */
    val playerState: StateFlow<PlaybackController.PlaybackState> = app.playbackController.state

    /** Reproduce la playlist empezando por la canción [index].
     * Si la canción está pendiente (sin audio local), lanza su re-descarga. */
    fun play(songs: List<Song>, index: Int) {
        val song = songs.getOrNull(index) ?: return
        if (song.isPending()) {
            redownloadSong(song)
            return
        }
        val playable = songs.filter { !it.isPending() }
        val playableIndex = playable.indexOfFirst { it.id == song.id }
        if (playableIndex >= 0) {
            app.playbackController.play(playable, playableIndex)
        }
    }

    /** Vuelve a descargar una canción pendiente usando su URL guardada. */
    fun redownloadSong(song: Song) {
        val url = song.youtubeUrl
        if (url.isNullOrBlank()) {
            Toast.makeText(app, R.string.redownload_no_url, Toast.LENGTH_SHORT).show()
            return
        }
        DownloadWorker.start(app, url)
    }

    fun togglePlayPause() = app.playbackController.togglePlayPause()

    /** Quita la canción de la playlist (no borra el archivo). */
    fun removeSong(song: Song) {
        viewModelScope.launch { app.repository.removeSongFromPlaylist(playlistId, song.id) }
    }

    /** Marca/desmarca la canción como favorita. */
    fun toggleFavorite(song: Song) {
        viewModelScope.launch {
            app.repository.setFavorite(song.id, !song.isFavorite)
        }
    }
}