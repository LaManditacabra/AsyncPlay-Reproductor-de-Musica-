package com.example.musicplayer.update

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.example.musicplayer.scraper.FileDownloader
import java.io.File

/**
 * Worker que descarga el APK de la nueva versión en segundo plano (WorkManager),
 * notificando el progreso (0-100) a través de [setProgress].
 */
class UpdateWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val url = inputData.getString(KEY_DOWNLOAD_URL) ?: return Result.failure()
        val version = inputData.getString(KEY_VERSION) ?: return Result.failure()
        return try {
            val destination = apkFile(version)

            FileDownloader.downloadWithProgress(url, destination) { percent ->
                setProgress(workDataOf(KEY_PROGRESS to percent))
            }

            if (destination.exists() && destination.length() > 0) {
                setProgress(workDataOf(KEY_PROGRESS to 100))
                Result.success(workDataOf(KEY_APK_PATH to destination.absolutePath))
            } else {
                Result.retry()
            }
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Fallo descargando la actualización", e)
            Result.retry()
        }
    }

    private fun apkFile(version: String): File =
        File(applicationContext.filesDir, "updates/AsyncPlay-$version.apk")

    companion object {
        const val KEY_DOWNLOAD_URL = "download_url"
        const val KEY_VERSION = "version"
        const val KEY_PROGRESS = "progress"
        const val KEY_APK_PATH = "apk_path"
        private const val TAG = "UpdateWorker"
    }
}