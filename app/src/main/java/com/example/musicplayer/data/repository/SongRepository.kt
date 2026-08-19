package com.example.musicplayer.data.repository

import com.example.musicplayer.data.db.PlaylistDao
import com.example.musicplayer.data.db.SongDao
import com.example.musicplayer.data.model.Playlist
import com.example.musicplayer.data.model.PlaylistSong
import com.example.musicplayer.data.model.PlaylistWithCount
import com.example.musicplayer.data.model.Song
import kotlinx.coroutines.flow.Flow

/**
 * Repository de canciones y playlists: única puerta de entrada de la capa de datos.
 *
 * El ViewModel depende de esta abstracción (no de los DAOs directamente), lo que
 * permite, en el futuro, añadir fuentes de datos remotas o cachés sin tocar la UI.
 */
class SongRepository(
    private val songDao: SongDao,
    private val playlistDao: PlaylistDao,
) {

    /** Flujo reactivo con las canciones, filtrable por favoritas. */
    fun songs(favoritesOnly: Boolean = false): Flow<List<Song>> =
        songDao.observeSongs(favoritesOnly)

    /** Guarda una canción en la base de datos. */
    suspend fun addSong(song: Song): Long = songDao.insert(song)

    /** Elimina una canción de la base de datos. */
    suspend fun deleteSong(song: Song) = songDao.delete(song)

    /** Marca o desmarca una canción como favorita. */
    suspend fun setFavorite(songId: Long, favorite: Boolean) =
        songDao.setFavorite(songId, favorite)

    // ------------------------------------------------------------------
    // Playlists
    // ------------------------------------------------------------------

    /** Flujo reactivo de playlists con su número de canciones. */
    fun playlistsWithCount(): Flow<List<PlaylistWithCount>> =
        playlistDao.observePlaylistsWithCount()

    /** Flujo reactivo de playlists (para el selector "agregar a playlist"). */
    fun playlists(): Flow<List<Playlist>> =
        playlistDao.observePlaylists()

    /** Flujo reactivo de una playlist concreta (para el título de la pantalla). */
    fun playlist(playlistId: Long): Flow<Playlist?> =
        playlistDao.observePlaylist(playlistId)

    /** Flujo reactivo de las canciones de una playlist, en orden. */
    fun playlistSongs(playlistId: Long): Flow<List<Song>> =
        playlistDao.observePlaylistSongs(playlistId)

    /** Crea una playlist y devuelve su id. */
    suspend fun createPlaylist(name: String): Long =
        playlistDao.insertPlaylist(Playlist(name = name))

    /** Borra una playlist (y sus relaciones), sin tocar los archivos de audio. */
    suspend fun deletePlaylist(playlist: Playlist) {
        playlistDao.clearPlaylistSongs(playlist.id)
        playlistDao.deletePlaylist(playlist)
    }

    /**
     * Añade una canción al final de la playlist.
     * Devuelve `false` si la canción ya estaba en ella.
     */
    suspend fun addSongToPlaylist(playlistId: Long, songId: Long): Boolean {
        if (playlistDao.isSongInPlaylist(playlistId, songId)) return false
        val position = (playlistDao.lastPosition(playlistId) ?: -1) + 1
        playlistDao.addSong(PlaylistSong(playlistId, songId, position))
        return true
    }

    /** Quita una canción de la playlist (sin borrar el archivo). */
    suspend fun removeSongFromPlaylist(playlistId: Long, songId: Long) =
        playlistDao.removeSong(playlistId, songId)
}