package com.example.musicplayer.data.repository

import com.example.musicplayer.data.db.SongDao
import com.example.musicplayer.data.model.Song
import kotlinx.coroutines.flow.Flow

/**
 * Repository de canciones: única puerta de entrada de la capa de datos.
 *
 * El ViewModel depende de esta abstracción (no del DAO directamente), lo que
 * permite, en el futuro, añadir fuentes de datos remotas o cachés sin tocar la UI.
 */
class SongRepository(private val songDao: SongDao) {

    /** Flujo reactivo con todas las canciones guardadas. */
    val songs: Flow<List<Song>> = songDao.observeSongs()

    /** Guarda una canción en la base de datos. */
    suspend fun addSong(song: Song): Long = songDao.insert(song)

    /** Elimina una canción de la base de datos. */
    suspend fun deleteSong(song: Song) = songDao.delete(song)
}