package com.example.musicplayer.ui.playlists

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.musicplayer.MusicPlayerApplication
import com.example.musicplayer.data.model.Playlist
import com.example.musicplayer.data.model.PlaylistWithCount
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * ViewModel de la pantalla de playlists: lista todas las playlists del usuario
 * y permite crear o eliminar.
 */
class PlaylistsViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as MusicPlayerApplication

    /** Playlists con su número de canciones. */
    val playlists: StateFlow<List<PlaylistWithCount>> =
        app.repository.playlistsWithCount()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

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
}