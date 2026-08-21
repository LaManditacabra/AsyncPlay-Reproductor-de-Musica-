package com.example.musicplayer.scraper

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.example.musicplayer.MusicPlayerApplication
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
            // 1) Normaliza URLs cortas (youtu.be/...) y extrae el vídeo.
            setProgress(workDataOf(KEY_STATUS to STATUS_EXTRACTING))

            // 2) Descarga del audio, portada y persistencia (lógica compartida).
            val song = SongDownloader.downloadOne(
                context = applicationContext,
                videoUrl = videoUrl,
                repository = repository,
            ) { songTitle, percent ->
                setProgress(
                    workDataOf(
                        KEY_STATUS to STATUS_DOWNLOADING,
                        KEY_TITLE to songTitle,
                        KEY_PROGRESS to percent,
                    ),
                )
            }

            // 3) Éxito: reporta el título para que la UI lo muestre.
            Result.success(workDataOf(KEY_SONG_TITLE to song.title, KEY_TYPE to TYPE_SINGLE))
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Fallo descargando $videoUrl", e)
            Result.failure(workDataOf(KEY_ERROR to (e.message ?: e.javaClass.simpleName)))
        }
    }

    companion object {
        const val TAG = "youtube_download"
        const val KEY_VIDEO_URL = "video_url"
        const val KEY_SONG_TITLE = "song_title"
        const val KEY_ERROR = "error"
        const val KEY_STATUS = "status"
        const val KEY_PROGRESS = "progress"
        const val KEY_TITLE = "title"
        const val KEY_TYPE = "type"
        const val KEY_DONE = "done"
        const val KEY_TOTAL = "total"

        const val TYPE_SINGLE = "single"
        const val TYPE_PLAYLIST = "playlist"

        const val STATUS_EXTRACTING = "extracting"
        const val STATUS_DOWNLOADING = "downloading"
        const val STATUS_PLAYLIST_EXTRACTING = "playlist_extracting"
        const val STATUS_PLAYLIST_DOWNLOADING = "playlist_downloading"

        /**
         * Encola la descarga de un vídeo de YouTube. Requiere conexión a internet
         * y reintenta con backoff exponencial si falla temporalmente.
         */
        fun start(context: Context, videoUrl: String) {
            val request = OneTimeWorkRequestBuilder<DownloadWorker>()
                .addTag(TAG)
                .setInputData(workDataOf(KEY_VIDEO_URL to videoUrl, KEY_TYPE to TYPE_SINGLE))
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