package com.example.musicplayer.player

import android.content.Context

/**
 * Guarda la última canción reproducida y su posición para reanudar la
 * reproducción en el próximo arranque de la app.
 */
class PlaybackPreferences(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** Id de la última canción reproducida (-1 si nunca hubo). */
    var lastSongId: Long
        get() = prefs.getLong(KEY_LAST_SONG_ID, -1L)
        set(value) {
            prefs.edit().putLong(KEY_LAST_SONG_ID, value).apply()
        }

    /** Posición (ms) en la que se quedó la última canción. */
    var lastPositionMs: Long
        get() = prefs.getLong(KEY_LAST_POSITION, 0L)
        set(value) {
            prefs.edit().putLong(KEY_LAST_POSITION, value).apply()
        }

    private companion object {
        const val PREFS_NAME = "playback_preferences"
        const val KEY_LAST_SONG_ID = "last_song_id"
        const val KEY_LAST_POSITION = "last_position_ms"
    }
}