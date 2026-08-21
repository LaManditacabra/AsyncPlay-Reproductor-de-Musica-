package com.example.musicplayer.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.musicplayer.R
import com.example.musicplayer.data.model.Song
import com.example.musicplayer.util.formatDuration

/**
 * Tarjeta de una canción: portada (Coil), título, artista, duración y acciones
 * de reproducción, favorito y eliminación.
 *
 * @param song            Canción a mostrar.
 * @param isCurrentSong   Indica si esta canción es la que está sonando.
 * @param isPlaying       Indica si el reproductor está reproduciendo (para el icono).
 * @param onClick         Al pulsar la tarjeta (reproducir esta canción).
 * @param onPlayPause     Al pulsar el botón central (reproducir/pausar).
 * @param onToggleFavorite Al pulsar el corazón (marcar/desmarcar favorita).
 * @param onDelete        Al pulsar el botón de eliminar.
 * @param onMore          Si se pasa, muestra el menú ⋮ (p. ej. "Agregar a playlist").
 * @param isPending       Indica si el archivo de audio no está disponible (p. ej.
 *                        canción restaurada desde un backup). Se muestra atenuada
 *                        y con botón de descarga en lugar de reproducción.
 * @param onRedownload    Al pulsar el botón de descarga de una canción pendiente.
 */
@Composable
fun SongCard(
    song: Song,
    isCurrentSong: Boolean,
    isPlaying: Boolean,
    onClick: () -> Unit,
    onPlayPause: () -> Unit,
    onToggleFavorite: () -> Unit,
    onDelete: () -> Unit,
    onMore: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    isPending: Boolean = false,
    onRedownload: (() -> Unit)? = null,
) {
    ElevatedCard(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Portada de la canción cargada con Coil. Si no hay imagen, se
            // muestra un degradado de color en su lugar.
            AsyncImage(
                model = song.thumbnailUrl,
                contentDescription = song.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(
                        Brush.linearGradient(
                            listOf(
                                MaterialTheme.colorScheme.primary,
                                MaterialTheme.colorScheme.tertiary,
                            ),
                        ),
                    ),
            )

            Spacer(modifier = Modifier.width(12.dp))

            // Información textual de la canción. Las pendientes se muestran
            // atenuadas para indicar que aún no se pueden reproducir.
            Column(
                modifier = Modifier
                    .weight(1f)
                    .alpha(if (isPending) 0.55f else 1f),
            ) {
                Text(
                    text = song.title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = song.artist,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = if (isPending) {
                        stringResource(R.string.song_pending)
                    } else {
                        formatDuration(song.durationMs)
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isPending) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            if (isPending && onRedownload != null) {
                // Sin archivo local: el botón lanza la re-descarga desde YouTube.
                FilledIconButton(onClick = onRedownload) {
                    Icon(
                        imageVector = Icons.Filled.Download,
                        contentDescription = stringResource(R.string.action_redownload),
                    )
                }
            } else {
                // Botón de reproducción: play si no es la actual, play/pausa si lo es.
                FilledIconButton(onClick = onPlayPause) {
                    val playingThis = isCurrentSong && isPlaying
                    Icon(
                        imageVector = if (playingThis) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = if (playingThis) {
                            stringResource(R.string.action_pause)
                        } else {
                            stringResource(R.string.action_play)
                        },
                    )
                }
            }

            // Corazón de favorito.
            IconButton(onClick = onToggleFavorite) {
                Icon(
                    imageVector = if (song.isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                    contentDescription = stringResource(
                        if (song.isFavorite) R.string.action_unfavorite else R.string.action_favorite,
                    ),
                    tint = if (song.isFavorite) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }

            // Menú de acciones extra (agregar a playlist, etc.).
            if (onMore != null) {
                IconButton(onClick = onMore) {
                    Icon(
                        imageVector = Icons.Filled.MoreVert,
                        contentDescription = stringResource(R.string.playlist_add),
                    )
                }
            }

            // Eliminar canción.
            IconButton(onClick = onDelete) {
                Icon(
                    imageVector = Icons.Filled.Delete,
                    contentDescription = stringResource(R.string.action_delete),
                    tint = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}