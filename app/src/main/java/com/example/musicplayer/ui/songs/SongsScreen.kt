package com.example.musicplayer.ui.songs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.musicplayer.R
import com.example.musicplayer.data.model.Song
import com.example.musicplayer.ui.components.MiniPlayerBar
import com.example.musicplayer.ui.components.SongCard
import com.example.musicplayer.ui.player.PlayerScreen
import com.example.musicplayer.ui.update.UpdateDialog
import com.example.musicplayer.update.UpdateManager.UpdateState

/**
 * Pantalla principal: lista de canciones guardadas consumida desde el
 * [SongsViewModel], con acción para añadir nuevas canciones desde YouTube,
 * búsqueda, filtro de favoritas, borrado y reproductor embebido.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SongsScreen(
    onNavigateToSearch: () -> Unit,
    viewModel: SongsViewModel = viewModel(),
) {
    val songs by viewModel.songs.collectAsStateWithLifecycle()
    val favoritesOnly by viewModel.favoritesOnly.collectAsStateWithLifecycle()
    val playerState by viewModel.playerState.collectAsStateWithLifecycle()
    val updateState by viewModel.updateState.collectAsStateWithLifecycle()
    var showAddDialog by remember { mutableStateOf(false) }
    var songToDelete by remember { mutableStateOf<Song?>(null) }
    var showPlayer by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(stringResource(R.string.songs_title)) },
                    actions = {
                        // Filtro de favoritas.
                        IconButton(onClick = viewModel::toggleFavoritesFilter) {
                            Icon(
                                imageVector = if (favoritesOnly) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                                contentDescription = stringResource(
                                    if (favoritesOnly) R.string.songs_show_favorites else R.string.songs_show_all,
                                ),
                                tint = if (favoritesOnly) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                            )
                        }
                        // Búsqueda en YouTube.
                        IconButton(onClick = onNavigateToSearch) {
                            Icon(
                                Icons.Filled.Search,
                                contentDescription = stringResource(R.string.search_title),
                            )
                        }
                    },
                )
            },
            floatingActionButton = {
                FloatingActionButton(onClick = { showAddDialog = true }) {
                    Icon(
                        Icons.Filled.Add,
                        contentDescription = stringResource(R.string.songs_add),
                    )
                }
            },
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
            ) {
                if (songs.isEmpty()) {
                    // Estado vacío: no hay canciones aún.
                    EmptyState(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                    )
                } else {
                    // Lista reactiva de canciones (LazyColumn para listas grandes).
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(songs, key = { it.id }) { song ->
                            SongCard(
                                song = song,
                                isCurrentSong = playerState.currentSong?.id == song.id,
                                isPlaying = playerState.isPlaying,
                                onClick = { viewModel.onSongClick(song) },
                                onPlayPause = {
                                    // Si esta canción ya suena, alterna play/pausa;
                                    // si no, la reproduce.
                                    if (playerState.currentSong?.id == song.id) {
                                        viewModel.togglePlayPause()
                                    } else {
                                        viewModel.onSongClick(song)
                                    }
                                },
                                onToggleFavorite = { viewModel.toggleFavorite(song) },
                                onDelete = { songToDelete = song },
                            )
                        }
                    }
                }

                // Barra de reproducción en miniatura mientras haya canción cargada.
                playerState.currentSong?.let { song ->
                    MiniPlayerBar(
                        song = song,
                        isPlaying = playerState.isPlaying,
                        onClick = { showPlayer = true },
                        onPlayPause = viewModel::togglePlayPause,
                    )
                }
            }
        }

        // Reproductor a pantalla completa (overlay).
        if (showPlayer) {
            PlayerScreen(
                state = playerState,
                onClose = { showPlayer = false },
                onTogglePlayPause = viewModel::togglePlayPause,
                onSeekTo = viewModel::seekTo,
                onSkipNext = viewModel::skipToNext,
                onSkipPrevious = viewModel::skipToPrevious,
                onToggleShuffle = viewModel::toggleShuffle,
                onCycleRepeat = viewModel::cycleRepeatMode,
            )
        }
    }

    // Diálogo para pegar una URL de YouTube y lanzar la descarga.
    if (showAddDialog) {
        AddSongDialog(
            onDismiss = { showAddDialog = false },
            onAdd = { url ->
                viewModel.downloadFromUrl(url)
                showAddDialog = false
            },
        )
    }

    // Confirmación de borrado.
    songToDelete?.let { song ->
        AlertDialog(
            onDismissRequest = { songToDelete = null },
            title = { Text(stringResource(R.string.delete_song_title)) },
            text = { Text(stringResource(R.string.delete_song_message, song.title)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteSong(song)
                    songToDelete = null
                }) {
                    Text(stringResource(R.string.action_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { songToDelete = null }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }

    // Diálogo del sistema de actualizaciones (si hay una fase activa).
    when (updateState) {
        is UpdateState.Available,
        is UpdateState.Downloading,
        is UpdateState.Downloaded,
        is UpdateState.Failed,
        -> {
            UpdateDialog(
                updateState = updateState,
                onDownload = viewModel::downloadUpdate,
                onInstall = viewModel::installUpdate,
                onDismiss = viewModel::dismissUpdate,
            )
        }
        else -> Unit
    }
}

/** Estado vacío: mensaje indicando cómo añadir la primera canción. */
@Composable
private fun EmptyState(modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text = stringResource(R.string.songs_empty),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

/** Diálogo que pide la URL de un vídeo de YouTube para descargarlo. */
@Composable
private fun AddSongDialog(
    onDismiss: () -> Unit,
    onAdd: (String) -> Unit,
) {
    var url by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.add_song_title)) },
        text = {
            Column {
                Text(
                    text = stringResource(R.string.add_song_description),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text(stringResource(R.string.add_song_url_label)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onAdd(url.trim()) },
                enabled = url.isNotBlank(),
            ) {
                Text(stringResource(R.string.action_download))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        },
    )
}