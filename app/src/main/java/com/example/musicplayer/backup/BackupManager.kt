package com.example.musicplayer.backup

import com.example.musicplayer.data.model.Song
import com.example.musicplayer.data.repository.SongRepository
import java.io.IOException
import kotlinx.coroutines.flow.first
import org.json.JSONArray
import org.json.JSONObject

/**
 * Resultado de una importación de backup.
 *
 * @property songsRestored Canciones que ya estaban descargadas y se vincularon.
 * @property songsPending  Canciones insertadas sin archivo local (pendientes de
 *                         volver a descargarse desde su URL de YouTube).
 * @property playlistsCreated Playlists creadas durante la importación.
 */
data class ImportResult(
    val songsRestored: Int,
    val songsPending: Int,
    val playlistsCreated: Int,
)

/**
 * Exporta e importa un backup en JSON con las canciones (su URL de YouTube,
 * metadatos y favorito) y las playlists armadas.
 *
 * Formato:
 * ```json
 * {
 *   "version": 1,
 *   "exportedAt": 1690000000000,
 *   "songs": [{"url": "...", "title": "...", "artist": "...",
 *              "durationMs": 123, "favorite": true}],
 *   "playlists": [{"name": "...", "songs": ["url1", "url2"]}]
 * }
 * ```
 *
 * En la importación, cada canción se vincula por URL (o por título+artista si
 * el backup no tiene URL). Si no existe localmente se inserta como entrada
 * pendiente: al volver a descargarla desde la búsqueda, [SongDownloader] la
 * detecta por URL y completa el archivo sin duplicarla.
 */
class BackupManager(private val repository: SongRepository) {

    /** Genera el JSON del backup a partir del estado actual de la base. */
    suspend fun exportToJson(): String {
        val songs = repository.songs().first()
        val playlists = repository.playlistsWithCount().first()

        val songsArray = JSONArray()
        songs.forEach { song ->
            songsArray.put(
                JSONObject()
                    .putOpt(KEY_URL, song.youtubeUrl)
                    .put(KEY_TITLE, song.title)
                    .put(KEY_ARTIST, song.artist)
                    .put(KEY_DURATION, song.durationMs)
                    .put(KEY_FAVORITE, song.isFavorite),
            )
        }

        val playlistsArray = JSONArray()
        playlists.forEach { playlistWithCount ->
            val playlist = playlistWithCount.playlist
            val songUrls = JSONArray()
            repository.playlistSongs(playlist.id).first().forEach { song ->
                // Identificador estable: la URL; si no hay, título+artista.
                songUrls.put(song.youtubeUrl ?: songKey(song.title, song.artist))
            }
            playlistsArray.put(
                JSONObject()
                    .put(KEY_NAME, playlist.name)
                    .put(KEY_SONGS, songUrls),
            )
        }

        return JSONObject()
            .put(KEY_VERSION, BACKUP_VERSION)
            .put(KEY_EXPORTED_AT, System.currentTimeMillis())
            .put(KEY_SONGS, songsArray)
            .put(KEY_PLAYLISTS, playlistsArray)
            .toString(2)
    }

    /**
     * Restaura canciones y playlists desde el JSON de backup.
     *
     * @throws IOException si el JSON es inválido o no tiene el formato esperado.
     */
    @Throws(IOException::class)
    suspend fun importFromJson(json: String): ImportResult {
        val root = try {
            JSONObject(json)
        } catch (e: Exception) {
            throw IOException("JSON inválido", e)
        }
        val songsArray = root.optJSONArray(KEY_SONGS)
            ?: throw IOException("Falta la sección de canciones")
        val playlistsArray = root.optJSONArray(KEY_PLAYLISTS) ?: JSONArray()

        var restored = 0
        var pending = 0

        // Mapa clave de canción -> id en la base, para reconstruir playlists.
        val idByKey = HashMap<String, Long>()

        for (i in 0 until songsArray.length()) {
            val item = songsArray.optJSONObject(i) ?: continue
            val url = item.optString(KEY_URL).ifEmpty { null }
            val title = item.optString(KEY_TITLE)
            val artist = item.optString(KEY_ARTIST)
            val durationMs = item.optLong(KEY_DURATION, 0L)
            val favorite = item.optBoolean(KEY_FAVORITE, false)
            val key = url ?: songKey(title, artist)

            val existingId = repository.findSongByUrl(url ?: "")?.id
                ?: repository.findSongByTitleAndArtist(title, artist)?.id
            if (existingId != null) {
                idByKey[key] = existingId
                if (favorite) repository.setFavorite(existingId, true)
                restored++
            } else {
                val newSong = Song(
                    title = title,
                    artist = artist,
                    durationMs = durationMs,
                    localPath = "", // Sin archivo local hasta re-descargar.
                    youtubeUrl = url,
                    isFavorite = favorite,
                )
                idByKey[key] = repository.addSong(newSong)
                pending++
            }
        }

        var createdPlaylists = 0
        for (i in 0 until playlistsArray.length()) {
            val item = playlistsArray.optJSONObject(i) ?: continue
            val name = item.optString(KEY_NAME)
            if (name.isEmpty()) continue
            val songKeys = item.optJSONArray(KEY_SONGS) ?: continue

            // Reutiliza una playlist existente con el mismo nombre; si no, crea una.
            val playlistId = repository.findPlaylistByName(name)?.id
                ?: repository.createPlaylist(name).also { createdPlaylists++ }

            var position = repository.playlistSongs(playlistId).first().size
            for (j in 0 until songKeys.length()) {
                val songId = idByKey[songKeys.optString(j)] ?: continue
                if (repository.addSongToPlaylist(playlistId, songId)) position++
            }
        }

        return ImportResult(
            songsRestored = restored,
            songsPending = pending,
            playlistsCreated = createdPlaylists,
        )
    }

    private fun songKey(title: String, artist: String): String = "title:$title|artist:$artist"

    private companion object {
        const val BACKUP_VERSION = 1
        const val KEY_VERSION = "version"
        const val KEY_EXPORTED_AT = "exportedAt"
        const val KEY_SONGS = "songs"
        const val KEY_PLAYLISTS = "playlists"
        const val KEY_URL = "url"
        const val KEY_TITLE = "title"
        const val KEY_ARTIST = "artist"
        const val KEY_DURATION = "durationMs"
        const val KEY_FAVORITE = "favorite"
        const val KEY_NAME = "name"
    }
}