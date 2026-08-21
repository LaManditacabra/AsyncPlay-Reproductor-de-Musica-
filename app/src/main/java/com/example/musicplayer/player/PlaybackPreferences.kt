package com.example.musicplayer.player

import android.content.Context
import java.util.UUID

/**
 * Guarda la última canción reproducida y su posición para reanudar la
 * reproducción en el próximo arranque de la app. También recuerda los ids de
 * descargas ya notificadas, para no volver a mostrar el aviso en otra sesión.
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

    /** Ids de descargas ya notificadas al usuario (para no repetir avisos). */
    private val seenDownloadIds: MutableSet<String>
        get() = prefs.getStringSet(KEY_SEEN_DOWNLOADS, emptySet())!!.toMutableSet()

    /** Registra una descarga como ya notificada y persiste el conjunto. */
    fun markDownloadSeen(id: UUID) {
        val ids = seenDownloadIds
        ids.add(id.toString())
        prefs.edit().putStringSet(KEY_SEEN_DOWNLOADS, ids).apply()
    }

    /** Comprueba si una descarga ya fue notificada y la marca como vista. */
    fun isDownloadSeen(id: UUID): Boolean {
        if (seenDownloadIds.contains(id.toString())) return true
        markDownloadSeen(id)
        return false
    }

    private companion object {
        const val PREFS_NAME = "playback_preferences"
        const val KEY_LAST_SONG_ID = "last_song_id"
        const val KEY_LAST_POSITION = "last_position_ms"
        const val KEY_SEEN_DOWNLOADS = "seen_downloads"
    }
}