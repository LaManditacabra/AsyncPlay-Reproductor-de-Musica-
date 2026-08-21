package com.example.musicplayer.ui.songs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.musicplayer.R
import com.example.musicplayer.data.model.Song
import com.example.musicplayer.scraper.DownloadWorker
import com.example.musicplayer.ui.components.AddToPlaylistDialog
import com.example.musicplayer.ui.components.MiniPlayerBar
import com.example.musicplayer.ui.components.SongCard
import com.example.musicplayer.ui.player.PlayerScreen
import com.example.musicplayer.ui.songs.SongsViewModel.ActiveDownload
import com.example.musicplayer.ui.update.UpdateDialog
import com.example.musicplayer.update.UpdateManager.UpdateState
import kotlinx.coroutines.delay

/**
 * Pantalla principal: lista de canciones guardadas consumida desde el
 * [SongsViewModel], con acción para añadir nuevas canciones desde YouTube,
 * búsqueda, filtro de favoritas, borrado y reproductor embebido.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SongsScreen(
    onNavigateToSearch: () -> Unit,
    onNavigateToSettings: () -> Unit,
    viewModel: SongsViewModel = viewModel(),
) {
    val songs by viewModel.songs.collectAsStateWithLifecycle()
    val playerState by viewModel.playerState.collectAsStateWithLifecycle()
    val updateState by viewModel.updateState.collectAsStateWithLifecycle()
    val activeDownloads by viewModel.activeDownloads.collectAsStateWithLifecycle()
    val playlists by viewModel.playlists.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val sortMode by viewModel.sortMode.collectAsStateWithLifecycle()
    var sortMenuOpen by remember { mutableStateOf(false) }
    var showAddDialog by remember { mutableStateOf(false) }
    var songToDelete by remember { mutableStateOf<Song?>(null) }
    var songForPlaylist by remember { mutableStateOf<Song?>(null) }
    var showPlayer by remember { mutableStateOf(false) }

    val snackbarHostState = remember { SnackbarHostState() }

    // Muestra los mensajes de descargas/importaciones como snackbar.
    LaunchedEffect(viewModel) {
        viewModel.messages.collect { message ->
            // Descarta cualquier snackbar previo para evitar que se queden colgados.
            snackbarHostState.currentSnackbarData?.dismiss()
            snackbarHostState.showSnackbar(
                message = message,
                duration = SnackbarDuration.Short,
            )
        }
    }

    // Vigilante: si un snackbar se queda en pantalla más de 5 segundos (p. ej.
    // porque el coroutine que lo mostró fue cancelado), lo cierra a la fuerza.
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
                    title = { Text(stringResource(R.string.songs_title)) },
                    actions = {
                        // Ajustes.
                        IconButton(onClick = onNavigateToSettings) {
                            Icon(
                                imageVector = Icons.Filled.Settings,
                                contentDescription = stringResource(R.string.settings_title),
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
            snackbarHost = { SnackbarHost(snackbarHostState) },
            // Mini reproductor como bottomBar: el FAB flota encima y la lista
            // respeta su espacio vía innerPadding (sin solapes).
            bottomBar = {
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
                // Búsqueda local + criterio de ordenación.
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = viewModel::setSearchQuery,
                        placeholder = { Text(stringResource(R.string.library_search_hint)) },
                        leadingIcon = {
                            Icon(Icons.Filled.Search, contentDescription = null)
                        },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { viewModel.setSearchQuery("") }) {
                                    Icon(
                                        Icons.Filled.Close,
                                        contentDescription = stringResource(R.string.action_clear_search),
                                    )
                                }
                            }
                        },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Box {
                        IconButton(onClick = { sortMenuOpen = true }) {
                            Icon(
                                Icons.Filled.Sort,
                                contentDescription = stringResource(R.string.sort_label),
                            )
                        }
                        DropdownMenu(
                            expanded = sortMenuOpen,
                            onDismissRequest = { sortMenuOpen = false },
                        ) {
                            SortMode.entries.forEach { mode ->
                                DropdownMenuItem(
                                    text = { Text(stringResource(mode.labelRes())) },
                                    onClick = {
                                        viewModel.setSortMode(mode)
                                        sortMenuOpen = false
                                    },
                                    leadingIcon = {
                                        if (mode == sortMode) {
                                            Icon(Icons.Filled.Check, contentDescription = null)
                                        }
                                    },
                                )
                            }
                        }
                    }
                }

                // Descargas en curso: señal de vida con estado y progreso.
                if (activeDownloads.isNotEmpty()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        activeDownloads.forEach { download ->
                            DownloadStatusCard(download = download)
                        }
                    }
                }

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
                                onMore = { songForPlaylist = song },
                                isPending = viewModel.isPending(song),
                                onRedownload = { viewModel.redownloadSong(song) },
                            )
                        }
                    }
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
                onSkipToIndex = viewModel::skipToIndex,
            )
        }
    }

    // Diálogo para pegar una URL de YouTube y lanzar la descarga.
    if (showAddDialog) {
        AddSongDialog(
            onDismiss = { showAddDialog = false },
            onAdd = { url, playlistName ->
                viewModel.downloadFromUrl(url, playlistName)
                showAddDialog = false
            },
        )
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

/** Recurso de texto para el nombre de un criterio de ordenación. */
private fun SortMode.labelRes(): Int = when (this) {
    SortMode.TITLE -> R.string.sort_title
    SortMode.ARTIST -> R.string.sort_artist
    SortMode.DURATION -> R.string.sort_duration
    SortMode.NEWEST -> R.string.sort_newest
}

/** Tarjeta que muestra el estado y progreso de una descarga en curso. */
@Composable
private fun DownloadStatusCard(download: ActiveDownload) {
    val statusText = when (download.status) {
        DownloadWorker.STATUS_DOWNLOADING -> stringResource(R.string.download_status_downloading)
        DownloadWorker.STATUS_PLAYLIST_EXTRACTING -> stringResource(R.string.download_status_playlist_extracting)
        DownloadWorker.STATUS_PLAYLIST_DOWNLOADING -> stringResource(R.string.download_status_playlist_downloading)
        else -> stringResource(R.string.download_status_extracting)
    }

    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = download.title ?: stringResource(R.string.download_pending_title),
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(modifier = Modifier.height(4.dp))
            if (download.total > 0) {
                Text(
                    text = stringResource(
                        R.string.playlist_download_progress,
                        (download.done + 1).coerceAtMost(download.total),
                        download.total,
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Text(
                    text = statusText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            if (download.progress > 0) {
                LinearProgressIndicator(
                    progress = { download.progress / 100f },
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                // Progreso indeterminado: aún no se conoce el total.
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

/** Diálogo que pide la URL de un vídeo/playlist de YouTube. Si es playlist,
 * ofrece crear una playlist con un nombre para agrupar las canciones descargadas. */
@Composable
private fun AddSongDialog(
    onDismiss: () -> Unit,
    onAdd: (url: String, playlistName: String?) -> Unit,
) {
    var url by remember { mutableStateOf("") }
    var createPlaylist by remember { mutableStateOf(false) }
    var playlistName by remember { mutableStateOf("") }
    val isPlaylistUrl = url.trim().contains("list=")

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.add_song_title)) },
        text = {
            Column {
                Text(
                    text = stringResource(
                        if (isPlaylistUrl) R.string.add_playlist_description else R.string.add_song_description,
                    ),
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
                if (isPlaylistUrl) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Switch(
                            checked = createPlaylist,
                            onCheckedChange = { createPlaylist = it },
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.add_playlist_create_option),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                    if (createPlaylist) {
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = playlistName,
                            onValueChange = { playlistName = it },
                            label = { Text(stringResource(R.string.playlist_name_label)) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onAdd(
                        url.trim(),
                        if (createPlaylist) playlistName.trim().ifEmpty { null } else null,
                    )
                },
                enabled = url.isNotBlank() && (!createPlaylist || playlistName.isNotBlank()),
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