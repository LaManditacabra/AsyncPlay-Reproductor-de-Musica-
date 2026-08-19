package com.example.musicplayer.ui.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.example.musicplayer.MusicPlayerApplication
import com.example.musicplayer.settings.ThemeMode
import kotlinx.coroutines.flow.StateFlow

/** ViewModel de la pantalla de ajustes: expone y modifica las preferencias. */
class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as MusicPlayerApplication

    val themeMode: StateFlow<ThemeMode> = app.settings.themeMode

    fun setThemeMode(mode: ThemeMode) = app.settings.setThemeMode(mode)
}