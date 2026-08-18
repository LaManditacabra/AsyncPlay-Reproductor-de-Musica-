package com.example.musicplayer.update

import android.content.Context

/**
 * Almacenamiento local (SharedPreferences) del sistema de actualizaciones.
 *
 * Guarda el estado necesario para respetar el rate limit de la API de GitHub:
 *  - cuándo se hizo la última comprobación (cooldown),
 *  - el ETag de la última respuesta (para peticiones condicionales -> 304),
 *  - hasta cuándo esperar si GitHub nos devolvió 403 (rate limit alcanzado).
 */
class UpdatePreferences(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** Instante (epoch ms) de la última comprobación realizada. */
    var lastCheckAt: Long
        get() = prefs.getLong(KEY_LAST_CHECK_AT, 0L)
        set(value) {
            prefs.edit().putLong(KEY_LAST_CHECK_AT, value).apply()
        }

    /** ETag de la última respuesta 200 de GitHub (para `If-None-Match`). */
    var etag: String?
        get() = prefs.getString(KEY_ETAG, null)
        set(value) {
            prefs.edit().putString(KEY_ETAG, value).apply()
        }

    /** Instante (epoch ms) en el que se podrá volver a consultar la API. */
    var rateLimitResetAt: Long
        get() = prefs.getLong(KEY_RATE_LIMIT_RESET, 0L)
        set(value) {
            prefs.edit().putLong(KEY_RATE_LIMIT_RESET, value).apply()
        }

    private companion object {
        const val PREFS_NAME = "update_preferences"
        const val KEY_LAST_CHECK_AT = "last_check_at"
        const val KEY_ETAG = "etag"
        const val KEY_RATE_LIMIT_RESET = "rate_limit_reset_at"
    }
}