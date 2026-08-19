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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.ServiceList

/**
 * Worker de descarga de playlists de YouTube por URL.
 *
 * Extrae la lista de vídeos con NewPipeExtractor ([PlaylistExtractor]) y baja el
 * audio de cada uno reutilizando [SongDownloader]. Reporta progreso: canción
 * actual, total y porcentaje. Comparte el tag con [DownloadWorker] para que la
 * UI muestre ambas descargas en la misma sección de progreso.
 */
class PlaylistDownloadWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    private val repository
        get() = (applicationContext as MusicPlayerApplication).repository

    override suspend fun doWork(): Result {
        val playlistUrl = inputData.getString(KEY_PLAYLIST_URL) ?: return Result.failure()
        return try {
            setProgress(
                workDataOf(DownloadWorker.KEY_STATUS to DownloadWorker.STATUS_PLAYLIST_EXTRACTING),
            )
            val videos = extractVideos(playlistUrl)
            if (videos.isEmpty()) {
                return Result.failure(
                    workDataOf(DownloadWorker.KEY_ERROR to "La playlist no tiene vídeos"),
                )
            }

            val total = videos.size
            var done = 0
            for (videoUrl in videos) {
                setProgress(
                    workDataOf(
                        DownloadWorker.KEY_STATUS to DownloadWorker.STATUS_PLAYLIST_DOWNLOADING,
                        DownloadWorker.KEY_DONE to done,
                        DownloadWorker.KEY_TOTAL to total,
                    ),
                )
                SongDownloader.downloadOne(
                    context = applicationContext,
                    videoUrl = videoUrl,
                    repository = repository,
                ) { songTitle, percent ->
                    setProgress(
                        workDataOf(
                            DownloadWorker.KEY_STATUS to DownloadWorker.STATUS_PLAYLIST_DOWNLOADING,
                            DownloadWorker.KEY_TITLE to songTitle,
                            DownloadWorker.KEY_DONE to done,
                            DownloadWorker.KEY_TOTAL to total,
                            DownloadWorker.KEY_PROGRESS to percent,
                        ),
                    )
                }
                done++
            }

            Result.success(
                workDataOf(
                    DownloadWorker.KEY_TYPE to DownloadWorker.TYPE_PLAYLIST,
                    DownloadWorker.KEY_DONE to done,
                ),
            )
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Fallo descargando playlist $playlistUrl", e)
            Result.failure(
                workDataOf(DownloadWorker.KEY_ERROR to (e.message ?: e.javaClass.simpleName)),
            )
        }
    }

    /** Extrae las URLs de los vídeos de la playlist (máximo [MAX_VIDEOS]). */
    private suspend fun extractVideos(playlistUrl: String): List<String> =
        withContext(Dispatchers.IO) {
            val extractor = ServiceList.YouTube.getPlaylistExtractor(playlistUrl)
            extractor.fetchPage()
            extractor.getInitialPage()
                ?.items
                .orEmpty()
                .take(MAX_VIDEOS)
                .map { it.url }
        }

    companion object {
        private const val TAG = "youtube_playlist_download"
        const val KEY_PLAYLIST_URL = "playlist_url"
        const val MAX_VIDEOS = 100

        /**
         * Encola la descarga de una playlist completa. Usa el mismo tag que
         * [DownloadWorker] para que la UI las muestre juntas.
         */
        fun start(context: Context, playlistUrl: String) {
            val request = OneTimeWorkRequestBuilder<PlaylistDownloadWorker>()
                .addTag(DownloadWorker.TAG)
                .setInputData(
                    workDataOf(
                        KEY_PLAYLIST_URL to playlistUrl,
                        DownloadWorker.KEY_TYPE to DownloadWorker.TYPE_PLAYLIST,
                    ),
                )
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