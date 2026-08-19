package com.example.musicplayer.data.model

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Lista de reproducción creada por el usuario.
 *
 * @property id        Identificador único autogenerado por Room.
 * @property name      Nombre visible de la playlist.
 * @property createdAt Marca de tiempo de creación (orden de la lista).
 */
@Entity(tableName = "playlists")
data class Playlist(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,

    @ColumnInfo(name = "name")
    val name: String,

    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis(),
)

/**
 * Relación muchos-a-muchos entre playlists y canciones.
 *
 * [position] ordena las canciones dentro de la playlist.
 */
@Entity(
    tableName = "playlist_songs",
    primaryKeys = ["playlist_id", "song_id"],
)
data class PlaylistSong(
    @ColumnInfo(name = "playlist_id")
    val playlistId: Long,

    @ColumnInfo(name = "song_id")
    val songId: Long,

    @ColumnInfo(name = "position")
    val position: Int,
)

/** Playlist con el número de canciones que contiene (para la tarjeta de lista). */
data class PlaylistWithCount(
    @Embedded
    val playlist: Playlist,

    @ColumnInfo(name = "song_count")
    val songCount: Int,
)