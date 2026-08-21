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

    /** URL de YouTube de origen (para backups y re-descargas), o `null` si se
     * importó sin ella. */
    @ColumnInfo(name = "youtube_url")
    val youtubeUrl: String? = null,

    @ColumnInfo(name = "is_favorite")
    val isFavorite: Boolean = false,
) {

    /** Devuelve la [Uri] local del archivo de audio, lista para ExoPlayer. */
    fun toUri(): Uri = Uri.fromFile(File(localPath))

    /**
     * Indica si el audio no está disponible localmente (canción importada por
     * backup o con el archivo borrado): no se puede reproducir hasta re-descargar.
     */
    fun isPending(): Boolean = localPath.isBlank() || !File(localPath).exists()

    /** Copia con el estado de favorito actualizado. */
    fun withFavorite(favorite: Boolean): Song = copy(isFavorite = favorite)
}