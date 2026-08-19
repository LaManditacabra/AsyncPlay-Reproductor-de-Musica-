package com.example.musicplayer.scraper

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.MediaFormat
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.StreamInfo

/**
 * Resultado de la extracción de un vídeo de YouTube: los metadatos y las URLs
 * directas que necesitamos para descargar el audio y la portada.
 */
data class ExtractedStream(
    val videoId: String,
    val title: String,
    val artist: String,
    val durationSeconds: Long,
    val audioUrl: String,
    val audioExtension: String,
    val thumbnailUrl: String?,
)

/**
 * Capa de scraper: usa NewPipeExtractor para obtener, sin la API oficial de
 * YouTube, los metadatos de un vídeo y la URL directa de su mejor stream de
 * audio.
 *
 * Para no re-extraer cada vez, las URLs de los streams se cachean en disco
 * (~6 h de validez, que es la vida útil real de una URL de googlevideo.com).
 */
class YouTubeExtractor {

    /**
     * Extrae la información de un vídeo a partir de su URL, reutilizando la
     * caché si esta canción ya se había extraído antes.
     *
     * @throws org.schabi.newpipe.extractor.exceptions.ExtractionException
     * @throws java.io.IOException
     */
    suspend fun extractStream(url: String, context: Context): ExtractedStream =
        withContext(Dispatchers.IO) {
            val cache = StreamCache(context)
            videoIdOf(url)?.let { id ->
                cache.get(id)?.let { return@withContext it }
            }

            // NewPipe.getServiceByUrl() resuelve el servicio (YouTube) por la URL.
            val info = StreamInfo.getInfo(NewPipe.getServiceByUrl(url), url)
            val audio = pickBestAudio(info.audioStreams)

            val stream = ExtractedStream(
                videoId = info.id,
                title = info.name,
                artist = info.uploaderName,
                durationSeconds = info.duration,
                audioUrl = checkNotNull(audio.content) { "El stream de audio no tiene URL" },
                audioExtension = audio.format?.suffix ?: "m4a",
                thumbnailUrl = info.thumbnails.maxByOrNull { it.height }?.url,
            )
            cache.put(stream.videoId, stream)
            stream
        }

    /** Extrae el id del vídeo de una URL `watch?v=` o `youtu.be/`. */
    private fun videoIdOf(url: String): String? {
        val watch = Regex("""[?&]v=([\w-]{11})""").find(url)
        if (watch != null) return watch.groupValues[1]
        val short = Regex("""youtu\.be/([\w-]{11})""").find(url)
        return short?.groupValues?.get(1)
    }

    /**
     * Selecciona el mejor audio disponible: prioriza los formatos más
     * compatibles con Media3 (M4A/AAC y MP3) y, dentro de ellos, el de mayor
     * bitrate. Si no hay ninguno de esos, usa el de mayor bitrate general.
     */
    private fun pickBestAudio(streams: List<AudioStream>): AudioStream {
        val preferred = streams.filter { it.format in PREFERRED_FORMATS }
        val pool = if (preferred.isNotEmpty()) preferred else streams
        return pool.maxByOrNull { it.averageBitrate }
            ?: error("No hay streams de audio disponibles para este vídeo")
    }

    /** Caché persistente (SharedPreferences) de streams extraídos, por vídeo. */
    private class StreamCache(context: Context) {
        private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        fun get(videoId: String): ExtractedStream? {
            val stored = prefs.getString(key(videoId), null) ?: return null
            val savedAt = prefs.getLong(keyTime(videoId), 0L)
            if (savedAt == 0L || System.currentTimeMillis() - savedAt > TTL_MS) return null
            val parts = stored.split(DELIMITER)
            if (parts.size < 6) return null
            val duration = parts[2].toLongOrNull() ?: return null
            return ExtractedStream(
                videoId = videoId,
                title = parts[0],
                artist = parts[1],
                durationSeconds = duration,
                audioUrl = parts[3],
                audioExtension = parts[4],
                thumbnailUrl = parts[5].takeIf { it.isNotEmpty() },
            )
        }

        fun put(videoId: String, stream: ExtractedStream) {
            val value = listOf(
                stream.title,
                stream.artist,
                stream.durationSeconds.toString(),
                stream.audioUrl,
                stream.audioExtension,
                stream.thumbnailUrl.orEmpty(),
            ).joinToString(DELIMITER)
            prefs.edit()
                .putString(key(videoId), value)
                .putLong(keyTime(videoId), System.currentTimeMillis())
                .apply()
        }

        private fun key(videoId: String) = "stream_$videoId"
        private fun keyTime(videoId: String) = "stream_${videoId}_time"

        private companion object {
            const val PREFS_NAME = "stream_cache"
            const val DELIMITER = "\u0001" // separador que no aparece en textos normales
            const val TTL_MS = 6 * 60 * 60 * 1_000L // 6 horas
        }
    }

    private companion object {
        val PREFERRED_FORMATS = setOf(MediaFormat.M4A, MediaFormat.MP3)
    }
}