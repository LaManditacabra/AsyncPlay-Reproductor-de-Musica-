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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.musicplayer.ui.components.SongCard
import com.example.musicplayer.ui.update.UpdateDialog
import com.example.musicplayer.update.UpdateManager.UpdateState

/**
 * Pantalla principal: lista de canciones guardadas consumida desde el
 * [SongsViewModel], con acción para añadir nuevas canciones desde YouTube.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SongsScreen(viewModel: SongsViewModel = viewModel()) {
    val songs by viewModel.songs.collectAsStateWithLifecycle()
    val playerState by viewModel.playerState.collectAsStateWithLifecycle()
    val updateState by viewModel.updateState.collectAsStateWithLifecycle()
    var showAddDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Mi Música") })
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddDialog = true }) {
                Icon(Icons.Filled.Add, contentDescription = "Añadir desde YouTube")
            }
        },
    ) { innerPadding ->
        if (songs.isEmpty()) {
            // Estado vacío: no hay canciones aún.
            EmptyState(modifier = Modifier.padding(innerPadding))
        } else {
            // Lista reactiva de canciones (LazyColumn para listas grandes).
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
                            // Si esta canción ya suena, alterna play/pausa;
                            // si no, la reproduce.
                            if (playerState.currentSong?.id == song.id) {
                                viewModel.togglePlayPause()
                            } else {
                                viewModel.onSongClick(song)
                            }
                        },
                    )
                }
            }
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
            text = "Aún no hay canciones.\nPulsa el botón + para añadir desde YouTube.",
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
        title = { Text("Añadir desde YouTube") },
        text = {
            Column {
                Text(
                    text = "Pega la URL del vídeo. El audio se descargará en segundo plano.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text("URL de YouTube") },
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
                Text("Descargar")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        },
    )
}