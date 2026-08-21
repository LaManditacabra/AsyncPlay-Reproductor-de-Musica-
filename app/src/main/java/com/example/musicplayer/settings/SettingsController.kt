package com.example.musicplayer.settings

import android.content.Context
import android.content.res.Configuration
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Temas almacenables (solo para persistencia; la UI usa un switch). */
enum class ThemeMode {
    LIGHT,
    DARK,
}

/**
 * Preferencias de la app (tema, descargas...) expuestas como [StateFlow]
 * reactivo y persistidas en SharedPreferences. La UI reacciona al instante.
 */
class SettingsController(private val context: Context) {

    private val prefs =
        context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    /**
     * Tema oscuro activado o desactivado. Si había un valor "sistema" guardado
     * de versiones anteriores, se resuelve con la configuración actual del
     * dispositivo la primera vez.
     */
    private val _darkTheme = MutableStateFlow(resolveInitialDarkTheme())
    val darkTheme: StateFlow<Boolean> = _darkTheme.asStateFlow()

    fun setDarkTheme(enabled: Boolean) {
        _darkTheme.value = enabled
        val mode = if (enabled) ThemeMode.DARK else ThemeMode.LIGHT
        prefs.edit().putString(KEY_THEME_MODE, mode.name).apply()
    }

    private fun resolveInitialDarkTheme(): Boolean =
        when (
            runCatching {
                ThemeMode.valueOf(prefs.getString(KEY_THEME_MODE, null) ?: "")
            }.getOrNull()
        ) {
            ThemeMode.DARK -> true
            ThemeMode.LIGHT -> false
            // Sin preferencia previa (o modo sistema heredado): sigue al dispositivo.
            null -> isSystemInDarkTheme()
        }

    private fun isSystemInDarkTheme(): Boolean {
        val uiMode = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        return uiMode == Configuration.UI_MODE_NIGHT_YES
    }

    private val _autoDownloadPending = MutableStateFlow(
        prefs.getBoolean(KEY_AUTO_DOWNLOAD_PENDING, false),
    )

    /**
     * Si está activo, al importar un backup las canciones pendientes (sin audio
     * local) se encolan para descarga automática; si no, se descargan a mano
     * con el botón ⬇ de cada tarjeta.
     */
    val autoDownloadPending: StateFlow<Boolean> = _autoDownloadPending.asStateFlow()

    fun setAutoDownloadPending(enabled: Boolean) {
        _autoDownloadPending.value = enabled
        prefs.edit().putBoolean(KEY_AUTO_DOWNLOAD_PENDING, enabled).apply()
    }

    private companion object {
        const val KEY_THEME_MODE = "theme_mode"
        const val KEY_AUTO_DOWNLOAD_PENDING = "auto_download_pending"
    }
}