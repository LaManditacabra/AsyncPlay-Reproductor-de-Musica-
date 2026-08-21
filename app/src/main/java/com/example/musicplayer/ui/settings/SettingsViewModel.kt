package com.example.musicplayer.ui.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.example.musicplayer.MusicPlayerApplication
import kotlinx.coroutines.flow.StateFlow

/** ViewModel de la pantalla de ajustes: expone y modifica las preferencias. */
class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as MusicPlayerApplication

    /** Tema oscuro activado o desactivado. */
    val darkTheme: StateFlow<Boolean> = app.settings.darkTheme

    fun setDarkTheme(enabled: Boolean) = app.settings.setDarkTheme(enabled)

    /** Descarga automática de canciones pendientes al importar backup. */
    val autoDownloadPending: StateFlow<Boolean> = app.settings.autoDownloadPending

    fun setAutoDownloadPending(enabled: Boolean) = app.settings.setAutoDownloadPending(enabled)
}