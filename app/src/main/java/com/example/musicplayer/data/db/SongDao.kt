package com.example.musicplayer.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.musicplayer.data.model.Song
import kotlinx.coroutines.flow.Flow

/**
 * DAO de [Song]: operaciones de lectura/escritura contra la tabla `songs`.
 *
 * Todas las operaciones de escritura son `suspend` (se ejecutan fuera del hilo
 * principal) y las lecturas exponen un [Flow] reactivo: la UI se actualiza
 * automáticamente cada vez que cambia el contenido de la tabla.
 */
@Dao
interface SongDao {

    /** Inserta una canción; si ya existe con el mismo id, la reemplaza. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(song: Song): Long

    /** Elimina una canción de la base de datos. */
    @Delete
    suspend fun delete(song: Song)

    /** Observa todas las canciones ordenadas por orden de inserción. */
    @Query("SELECT * FROM songs ORDER BY id ASC")
    fun observeSongs(): Flow<List<Song>>
}