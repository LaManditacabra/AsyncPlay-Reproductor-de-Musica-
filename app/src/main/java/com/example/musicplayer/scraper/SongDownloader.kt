package com.example.musicplayer.scraper

import android.content.Context
import android.net.Uri
import com.example.musicplayer.data.model.Song
import com.example.musicplayer.data.repository.SongRepository
import java.io.File

/**
 * Utilidad compartida para descargar el audio de un vídeo de YouTube y
 * persistirlo como canción. Lo usan tanto el worker de vídeo único como el de
 * playlists completas, para no duplicar la lógica.
 */
object SongDownloader {

    /**
     * Descarga y persiste la canción del vídeo [videoUrl].
     *
     * @param onProgress callback con el título de la canción y el porcentaje (0-100).
     * @return la [Song] guardada (con su id asignado por Room).
     */
    suspend fun downloadOne(
        context: Context,
        videoUrl: String,
        repository: SongRepository,
        onProgress: suspend (title: String, percent: Int) -> Unit,
    ): Song {
        val normalizedUrl = normalizeUrl(videoUrl)
        val extracted = YouTubeExtractor().extractStream(normalizedUrl, context)
        onProgress(extracted.title, 0)
        val audioFile = audioFile(context, extracted.videoId, extracted.audioExtension)
        FileDownloader.downloadWithProgress(extracted.audioUrl, audioFile) { percent ->
            onProgress(extracted.title, percent)
        }
        val artworkPath = extracted.thumbnailUrl?.let { thumbUrl ->
            try {
                val file = artworkFile(context, extracted.videoId)
                FileDownloader.download(thumbUrl, file)
                Uri.fromFile(file).toString()
            } catch (e: Exception) {
                null
            }
        }
        // Si la canción ya existía (misma URL de YouTube), se actualiza esa fila
        // en lugar de insertar un duplicado: conserva id, favorito y playlists.
        val existing = repository.findSongByUrl(normalizedUrl)
        val song = Song(
            id = existing?.id ?: 0L,
            title = extracted.title,
            artist = extracted.artist,
            durationMs = extracted.durationSeconds * 1_000,
            localPath = audioFile.absolutePath,
            thumbnailUrl = artworkPath ?: existing?.thumbnailUrl,
            youtubeUrl = normalizedUrl,
            isFavorite = existing?.isFavorite ?: false,
        )
        val id = if (existing != null) {
            repository.updateSong(song)
            existing.id
        } else {
            repository.addSong(song)
        }
        return song.copy(id = id)
    }

    /** Convierte `youtu.be/<id>` en `https://www.youtube.com/watch?v=<id>`. */
    private fun normalizeUrl(url: String): String {
        val shortLink = Regex("""youtu\.be/([\w-]{6,})""").find(url)
        return if (shortLink != null) {
            "https://www.youtube.com/watch?v=${shortLink.groupValues[1]}"
        } else {
            url
        }
    }

    private fun audioFile(context: Context, videoId: String, extension: String): File =
        File(context.filesDir, "$AUDIO_DIR/$videoId.$extension")

    private fun artworkFile(context: Context, videoId: String): File =
        File(context.filesDir, "$ARTWORK_DIR/$videoId.jpg")

    private const val AUDIO_DIR = "music"
    private const val ARTWORK_DIR = "artwork"
}