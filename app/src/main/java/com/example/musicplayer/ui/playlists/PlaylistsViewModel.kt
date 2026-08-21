package com.example.musicplayer.ui.playlists

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.musicplayer.MusicPlayerApplication
import com.example.musicplayer.data.model.Playlist
import com.example.musicplayer.data.model.PlaylistWithCount
import com.example.musicplayer.player.PlaybackController
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Playlist lista para la tarjeta del grid: datos + miniaturas para el collage. */
data class PlaylistItem(
    val playlist: Playlist,
    val songCount: Int,
    val thumbs: List<String>,
)

/** Filtro de la biblioteca: todas (incluye la tarjeta Favoritos) o solo playlists. */
enum class LibraryFilter { ALL, PLAYLISTS }

/** Criterio de ordenación de las playlists. */
enum class PlaylistSortMode { RECENT, NAME }

/**
 * ViewModel de la pantalla de playlists: muestra las playlists del usuario en
 * forma de grid (con miniaturas para el collage 2x2), el número de favoritas y
 * permite crear o eliminar.
 */
class PlaylistsViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as MusicPlayerApplication

    /** Filtro activo de la biblioteca. */
    private val _filter = MutableStateFlow(LibraryFilter.ALL)
    val filter: StateFlow<LibraryFilter> = _filter.asStateFlow()

    /** Criterio de ordenación de las playlists. */
    private val _sortMode = MutableStateFlow(PlaylistSortMode.RECENT)
    val sortMode: StateFlow<PlaylistSortMode> = _sortMode.asStateFlow()

    /** Playlists con sus canciones, miniaturas para el collage y número de canciones. */
    val playlists: StateFlow<List<PlaylistItem>> =
        combine(
            app.repository.playlistsWithCount(),
            app.repository.playlistThumbs(),
            _sortMode,
        ) { counts: List<PlaylistWithCount>, thumbs, sort ->
            val thumbsByPlaylist = thumbs.groupBy { it.playlistId }
            counts
                .map { item ->
                    PlaylistItem(
                        playlist = item.playlist,
                        songCount = item.songCount,
                        thumbs = thumbsByPlaylist[item.playlist.id].orEmpty()
                            .mapNotNull { it.thumbnailUrl }
                            .take(4),
                    )
                }
                .sortedWith(
                    when (sort) {
                        PlaylistSortMode.NAME -> compareBy { it.playlist.name.lowercase() }
                        PlaylistSortMode.RECENT -> compareByDescending { it.playlist.createdAt }
                    },
                )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Número de canciones favoritas (para la tarjeta "Favoritos"). */
    val favoriteCount: StateFlow<Int> =
        app.repository.songs(favoritesOnly = true)
            .map { it.size }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    /** Estado del reproductor (para mostrar el mini reproductor al reproducir). */
    val playerState: StateFlow<PlaybackController.PlaybackState> = app.playbackController.state

    /** Aplica el filtro de la biblioteca. */
    fun setFilter(filter: LibraryFilter) {
        _filter.value = filter
    }

    /** Aplica el criterio de ordenación. */
    fun setSortMode(mode: PlaylistSortMode) {
        _sortMode.value = mode
    }

    /** Crea una playlist con el nombre indicado (ignora nombres vacíos). */
    fun createPlaylist(name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch { app.repository.createPlaylist(trimmed) }
    }

    /** Elimina la playlist (no borra las canciones ni sus archivos). */
    fun deletePlaylist(playlist: Playlist) {
        viewModelScope.launch { app.repository.deletePlaylist(playlist) }
    }

    /** Reproduce la playlist empezando por la primera canción. */
    fun playPlaylist(playlistId: Long) {
        viewModelScope.launch {
            val songs = app.repository.playlistSongs(playlistId).first()
            if (songs.isNotEmpty()) {
                app.playbackController.play(songs, 0)
            }
        }
    }

    fun togglePlayPause() = app.playbackController.togglePlayPause()

    fun seekTo(positionMs: Long) = app.playbackController.seekTo(positionMs)

    fun skipToNext() = app.playbackController.skipToNext()

    fun skipToPrevious() = app.playbackController.skipToPrevious()

    fun toggleShuffle() = app.playbackController.toggleShuffle()

    fun cycleRepeatMode() = app.playbackController.cycleRepeatMode()

    fun skipToIndex(index: Int) = app.playbackController.skipToIndex(index)
}