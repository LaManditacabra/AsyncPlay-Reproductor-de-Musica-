package com.example.musicplayer.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.work.Constraints
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.example.musicplayer.BuildConfig
import java.io.File
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Sistema de actualización por GitHub Releases.
 *
 * Flujo: [checkForUpdates] consulta la última release de GitHub, compara su
 * versión con la instalada (BuildConfig) y expone el resultado como [StateFlow].
 * El usuario puede descargar el APK ([downloadUpdate]) con progreso vía
 * WorkManager e instalarlo ([installUpdate]) usando el instalador del sistema.
 */
class UpdateManager(private val context: Context) {

    /** Estados posibles del ciclo de actualización. */
    sealed interface UpdateState {
        data object Idle : UpdateState
        data object Checking : UpdateState
        data class Available(val release: ReleaseInfo) : UpdateState
        data class Downloading(val progress: Int) : UpdateState
        data class Downloaded(val apkFile: File) : UpdateState
        data class Failed(val message: String) : UpdateState
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val client = GitHubReleasesClient()

    private val _state = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val state: StateFlow<UpdateState> = _state.asStateFlow()

    /**
     * Comprueba si hay una versión más reciente en GitHub. Si la comprobación
     * falla (offline, rate-limit, sin releases) vuelve silenciosamente a [UpdateState.Idle].
     */
    fun checkForUpdates() {
        if (_state.value is UpdateState.Checking) return
        scope.launch {
            _state.value = UpdateState.Checking
            try {
                val release = client.fetchLatestRelease()
                val isNewer = release != null &&
                    release.apkUrl.isNotBlank() &&
                    VersionComparator.isNewer(release.tagName, BuildConfig.VERSION_NAME)
                _state.value = if (isNewer) UpdateState.Available(release) else UpdateState.Idle
            } catch (e: Exception) {
                _state.value = UpdateState.Idle
            }
        }
    }

    /** Descarga el APK de la release disponible en segundo plano. */
    fun downloadUpdate() {
        val release = (_state.value as? UpdateState.Available)?.release ?: return
        if (_state.value is UpdateState.Downloading) return

        val request = OneTimeWorkRequestBuilder<UpdateWorker>()
            .setInputData(
                workDataOf(
                    UpdateWorker.KEY_DOWNLOAD_URL to release.apkUrl,
                    UpdateWorker.KEY_VERSION to release.tagName,
                ),
            )
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build(),
            )
            .build()

        _state.value = UpdateState.Downloading(0)
        WorkManager.getInstance(context).enqueue(request)
        observeWork(request.id)
    }

    /** Abre el instalador del sistema con el APK descargado (vía FileProvider). */
    fun installUpdate() {
        val apkFile = (_state.value as? UpdateState.Downloaded)?.apkFile ?: return
        val uri = FileProvider.getUriForFile(
            context,
            "${BuildConfig.APPLICATION_ID}.fileprovider",
            apkFile,
        )
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching { context.startActivity(intent) }
            .onFailure {
                _state.value = UpdateState.Failed("No se pudo abrir el instalador")
            }
    }

    /** Oculta el diálogo de actualización. */
    fun dismiss() {
        _state.value = UpdateState.Idle
    }

    private fun observeWork(workId: UUID) {
        scope.launch {
            WorkManager.getInstance(context).getWorkInfoByIdFlow(workId).collect { info ->
                if (info == null) return@collect
                when (info.state) {
                    WorkInfo.State.RUNNING -> {
                        val progress = info.progress.getInt(UpdateWorker.KEY_PROGRESS, 0)
                        _state.value = UpdateState.Downloading(progress)
                    }
                    WorkInfo.State.SUCCEEDED -> {
                        val path = info.outputData.getString(UpdateWorker.KEY_APK_PATH)
                        _state.value = if (path != null) {
                            UpdateState.Downloaded(File(path))
                        } else {
                            UpdateState.Failed("No se pudo completar la descarga")
                        }
                    }
                    WorkInfo.State.FAILED, WorkInfo.State.CANCELLED -> {
                        _state.value = UpdateState.Failed("Error al descargar la actualización")
                    }
                    else -> Unit
                }
            }
        }
    }
}