package com.example.musicplayer.data.model

import android.net.Uri
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import java.io.File

/**
 * Representa una canción descargada y almacenada localmente.
 *
 * @property id             Identificador único autogenerado por Room.
 * @property title          Título de la canción (o del vídeo de YouTube).
 * @property artist         Artista / canal que subió el contenido.
 * @property durationMs     Duración total en milisegundos.
 * @property localPath      Ruta absoluta del archivo de audio en el almacenamiento interno.
 * @property thumbnailUrl   Ruta local de la portada (URI `file://`), o `null` si no hay.
 */
@Entity(tableName = "songs")
data class Song(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,

    @ColumnInfo(name = "title")
    val title: String,

    @ColumnInfo(name = "artist")
    val artist: String,

    @ColumnInfo(name = "duration_ms")
    val durationMs: Long,

    @ColumnInfo(name = "local_path")
    val localPath: String,

    @ColumnInfo(name = "thumbnail_url")
    val thumbnailUrl: String? = null,
) {

    /** Devuelve la [Uri] local del archivo de audio, lista para ExoPlayer. */
    fun toUri(): Uri = Uri.fromFile(File(localPath))
}