package com.example.musicplayer.scraper

import android.content.Context
import android.net.Uri
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.example.musicplayer.MusicPlayerApplication
import com.example.musicplayer.data.model.Song
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Worker de descarga en segundo plano (WorkManager).
 *
 * Flujo completo: extrae los datos con NewPipeExtractor, descarga el audio y la
 * portada al almacenamiento interno y persiste la [Song] en Room. Al terminar,
 * el [Flow] del repository notifica el cambio y la UI se actualiza sola.
 *
 * El resultado se reporta al WorkManager: `Result.success()` con el título de
 * la canción o `Result.failure()` con el mensaje de error (para que la UI lo
 * muestre y el usuario sepa por qué falló).
 */
class DownloadWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    private val repository
        get() = (applicationContext as MusicPlayerApplication).repository

    override suspend fun doWork(): Result {
        val videoUrl = inputData.getString(KEY_VIDEO_URL) ?: return Result.failure()
        return try {
            // 1) Normaliza URLs cortas (youtu.be/...) a URLs que NewPipe entienda.
            setProgress(workDataOf(KEY_STATUS to STATUS_EXTRACTING))
            val extracted = YouTubeExtractor().extractStream(normalizeUrl(videoUrl))

            // 2) Descarga del audio en el directorio privado de la app, con progreso.
            setProgress(
                workDataOf(
                    KEY_STATUS to STATUS_DOWNLOADING,
                    KEY_TITLE to extracted.title,
                ),
            )
            val audioFile = audioFile(extracted.videoId, extracted.audioExtension)
            FileDownloader.downloadWithProgress(extracted.audioUrl, audioFile) { percent ->
                setProgress(
                    workDataOf(
                        KEY_STATUS to STATUS_DOWNLOADING,
                        KEY_TITLE to extracted.title,
                        KEY_PROGRESS to percent,
                    ),
                )
            }

            // 3) Descarga de la portada (si el vídeo tiene una). Si falla, seguimos.
            val artworkPath = extracted.thumbnailUrl?.let { thumbUrl ->
                try {
                    val file = artworkFile(extracted.videoId)
                    FileDownloader.download(thumbUrl, file)
                    Uri.fromFile(file).toString()
                } catch (e: Exception) {
                    null
                }
            }

            // 4) Persistencia en la base de datos local.
            repository.addSong(
                Song(
                    title = extracted.title,
                    artist = extracted.artist,
                    durationMs = extracted.durationSeconds * 1_000,
                    localPath = audioFile.absolutePath,
                    thumbnailUrl = artworkPath,
                ),
            )
            Result.success(workDataOf(KEY_SONG_TITLE to extracted.title))
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Fallo descargando $videoUrl", e)
            Result.failure(workDataOf(KEY_ERROR to (e.message ?: e.javaClass.simpleName)))
        }
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

    /** Ruta del archivo de audio: filesDir/music/<videoId>.<ext>. */
    private fun audioFile(videoId: String, extension: String): File =
        File(applicationContext.filesDir, "$AUDIO_DIR/$videoId.$extension")

    /** Ruta del archivo de portada: filesDir/artwork/<videoId>.jpg. */
    private fun artworkFile(videoId: String): File =
        File(applicationContext.filesDir, "$ARTWORK_DIR/$videoId.jpg")

    companion object {
        const val TAG = "youtube_download"
        const val KEY_VIDEO_URL = "video_url"
        const val KEY_SONG_TITLE = "song_title"
        const val KEY_ERROR = "error"
        const val KEY_STATUS = "status"
        const val KEY_PROGRESS = "progress"
        const val KEY_TITLE = "title"

        const val STATUS_EXTRACTING = "extracting"
        const val STATUS_DOWNLOADING = "downloading"

        private const val AUDIO_DIR = "music"
        private const val ARTWORK_DIR = "artwork"

        /**
         * Encola la descarga de un vídeo de YouTube. Requiere conexión a internet
         * y reintenta con backoff exponencial si falla temporalmente.
         */
        fun start(context: Context, videoUrl: String) {
            val request = OneTimeWorkRequestBuilder<DownloadWorker>()
                .addTag(TAG)
                .setInputData(workDataOf(KEY_VIDEO_URL to videoUrl))
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build(),
                )
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 10, TimeUnit.SECONDS)
                .build()

            WorkManager.getInstance(context).enqueue(request)
        }
    }
}