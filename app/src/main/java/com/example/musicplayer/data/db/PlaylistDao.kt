package com.example.musicplayer.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.musicplayer.data.model.Playlist
import com.example.musicplayer.data.model.PlaylistSong
import com.example.musicplayer.data.model.PlaylistWithCount
import com.example.musicplayer.data.model.Song
import kotlinx.coroutines.flow.Flow

/**
 * DAO de playlists: operaciones contra las tablas `playlists` y `playlist_songs`.
 *
 * Las lecturas exponen [Flow] reactivos (la UI se actualiza sola) y las
 * escrituras son `suspend`.
 */
@Dao
interface PlaylistDao {

    @Query("SELECT * FROM playlists ORDER BY created_at ASC")
    fun observePlaylists(): Flow<List<Playlist>>

    /** Playlists con su número de canciones (para la tarjeta de la lista). */
    @Query(
        """
        SELECT p.*,
               (SELECT COUNT(*) FROM playlist_songs ps WHERE ps.playlist_id = p.id) AS song_count
        FROM playlists p
        ORDER BY p.created_at ASC
        """,
    )
    fun observePlaylistsWithCount(): Flow<List<PlaylistWithCount>>

    @Query("SELECT * FROM playlists WHERE id = :playlistId")
    fun observePlaylist(playlistId: Long): Flow<Playlist?>

    /** Canciones de una playlist, en el orden de [PlaylistSong.position]. */
    @Query(
        """
        SELECT s.* FROM songs s
        INNER JOIN playlist_songs ps ON ps.song_id = s.id
        WHERE ps.playlist_id = :playlistId
        ORDER BY ps.position ASC
        """,
    )
    fun observePlaylistSongs(playlistId: Long): Flow<List<Song>>

    @Insert
    suspend fun insertPlaylist(playlist: Playlist): Long

    @Delete
    suspend fun deletePlaylist(playlist: Playlist)

    @Query("DELETE FROM playlist_songs WHERE playlist_id = :playlistId")
    suspend fun clearPlaylistSongs(playlistId: Long)

    @Query("SELECT MAX(position) FROM playlist_songs WHERE playlist_id = :playlistId")
    suspend fun lastPosition(playlistId: Long): Int?

    @Query(
        "SELECT EXISTS(SELECT 1 FROM playlist_songs WHERE playlist_id = :playlistId AND song_id = :songId)",
    )
    suspend fun isSongInPlaylist(playlistId: Long, songId: Long): Boolean

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun addSong(playlistSong: PlaylistSong): Long

    @Query(
        "DELETE FROM playlist_songs WHERE playlist_id = :playlistId AND song_id = :songId",
    )
    suspend fun removeSong(playlistId: Long, songId: Long)
}