package com.example.musicplayer.ui.favorites

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.musicplayer.MusicPlayerApplication
import com.example.musicplayer.R
import com.example.musicplayer.data.model.Playlist
import com.example.musicplayer.data.model.Song
import com.example.musicplayer.player.PlaybackController
import com.example.musicplayer.scraper.DownloadWorker
import java.io.File
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * ViewModel de la sección "Favoritos": muestra las canciones marcadas como
 * favoritas y permite reproducirlas, desmarcarlas, añadirlas a playlists o
 * borrarlas.
 */
class FavoritesViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as MusicPlayerApplication
    private val repository = app.repository
    private val playbackController = app.playbackController

    /** Canciones favoritas. */
    val songs: StateFlow<List<Song>> =
        repository.songs(favoritesOnly = true)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Playlists disponibles (para el selector "agregar a playlist"). */
    val playlists: StateFlow<List<Playlist>> =
        repository.playlists()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Estado observable del reproductor. */
    val playerState: StateFlow<PlaybackController.PlaybackState> = playbackController.state

    /** Mensajes informativos para mostrar como snackbar. */
    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 16)
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    /** Reproduce la favorita tocada encolando el resto de favoritas.
     * Si está pendiente (sin audio local), lanza su re-descarga. */
    fun onSongClick(song: Song) {
        if (song.isPending()) {
            redownloadSong(song)
            return
        }
        val playable = songs.value.filter { !it.isPending() }
        val index = playable.indexOfFirst { it.id == song.id }
        if (index >= 0) {
            playbackController.play(playable, index)
        }
    }

    /** Vuelve a descargar una canción pendiente usando su URL guardada. */
    fun redownloadSong(song: Song) {
        val url = song.youtubeUrl
        if (url.isNullOrBlank()) {
            _messages.tryEmit(app.getString(R.string.redownload_no_url))
            return
        }
        DownloadWorker.start(getApplication(), url)
    }

    fun togglePlayPause() = playbackController.togglePlayPause()

    fun seekTo(positionMs: Long) = playbackController.seekTo(positionMs)

    fun skipToNext() = playbackController.skipToNext()

    fun skipToPrevious() = playbackController.skipToPrevious()

    fun skipToIndex(index: Int) = playbackController.skipToIndex(index)

    fun toggleShuffle() = playbackController.toggleShuffle()

    fun cycleRepeatMode() = playbackController.cycleRepeatMode()

    /** Desmarca la canción como favorita (la quita de esta lista). */
    fun toggleFavorite(song: Song) {
        viewModelScope.launch {
            repository.setFavorite(song.id, !song.isFavorite)
        }
    }

    /** Añade la canción a una playlist existente. */
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

    /** Elimina la canción: borra los archivos del disco y la fila de la base de datos. */
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
}