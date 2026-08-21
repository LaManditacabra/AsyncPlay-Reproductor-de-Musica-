package com.example.musicplayer.ui.favorites

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.musicplayer.R
import com.example.musicplayer.data.model.Song
import com.example.musicplayer.ui.components.AddToPlaylistDialog
import com.example.musicplayer.ui.components.MiniPlayerBar
import com.example.musicplayer.ui.components.SongCard
import com.example.musicplayer.ui.player.PlayerScreen
import kotlinx.coroutines.delay

/**
 * Sección "Favoritos": lista las canciones marcadas con el corazón. Al tocar
 * una se reproduce junto con el resto de favoritas; incluye mini reproductor,
 * reproductor a pantalla completa y opciones de playlist/borrado.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FavoritesScreen(
    onBack: () -> Unit,
    viewModel: FavoritesViewModel = viewModel(),
) {
    val songs by viewModel.songs.collectAsStateWithLifecycle()
    val playerState by viewModel.playerState.collectAsStateWithLifecycle()
    val playlists by viewModel.playlists.collectAsStateWithLifecycle()
    var songToDelete by remember { mutableStateOf<Song?>(null) }
    var songForPlaylist by remember { mutableStateOf<Song?>(null) }
    var showPlayer by remember { mutableStateOf(false) }

    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(viewModel) {
        viewModel.messages.collect { message ->
            snackbarHostState.currentSnackbarData?.dismiss()
            snackbarHostState.showSnackbar(
                message = message,
                duration = SnackbarDuration.Short,
            )
        }
    }

    // Vigilante: cierra el snackbar si se queda en pantalla más de 5 segundos.
    LaunchedEffect(snackbarHostState) {
        snapshotFlow { snackbarHostState.currentSnackbarData }
            .collect { data ->
                if (data != null) {
                    delay(5_000)
                    if (snackbarHostState.currentSnackbarData === data) {
                        data.dismiss()
                    }
                }
            }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            topBar = {
                TopAppBar(
                    title = { Text(stringResource(R.string.favorites_title)) },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(R.string.action_back),
                            )
                        }
                    },
                )
            },
            snackbarHost = { SnackbarHost(snackbarHostState) },
        ) { innerPadding ->
            if (songs.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = stringResource(R.string.favorites_empty),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
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
                                if (playerState.currentSong?.id == song.id) {
                                    viewModel.togglePlayPause()
                                } else {
                                    viewModel.onSongClick(song)
                                }
                            },
                            onToggleFavorite = { viewModel.toggleFavorite(song) },
                            onDelete = { songToDelete = song },
                            onMore = { songForPlaylist = song },
                            isPending = song.isPending(),
                            onRedownload = { viewModel.redownloadSong(song) },
                        )
                    }
                }
            }

            // Mini reproductor mientras haya canción cargada.
            playerState.currentSong?.let { song ->
                MiniPlayerBar(
                    song = song,
                    isPlaying = playerState.isPlaying,
                    progress = if (playerState.durationMs > 0) {
                        playerState.positionMs.toFloat() / playerState.durationMs
                    } else {
                        0f
                    },
                    onClick = { showPlayer = true },
                    onPlayPause = viewModel::togglePlayPause,
                )
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
                onSkipToIndex = viewModel::skipToIndex,
            )
        }
    }

    // Selector de playlist para agregar la canción tocada (menú ⋮).
    songForPlaylist?.let { song ->
        AddToPlaylistDialog(
            playlists = playlists,
            onDismiss = { songForPlaylist = null },
            onCreateAndAdd = { name ->
                viewModel.createPlaylistAndAdd(name, song.id)
                songForPlaylist = null
            },
            onAddToPlaylist = { playlistId ->
                viewModel.addSongToPlaylist(playlistId, song.id)
                songForPlaylist = null
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
}