package com.example.musicplayer.scraper

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
 */
class YouTubeExtractor {

    /**
     * Extrae la información de un vídeo a partir de su URL.
     *
     * @throws org.schabi.newpipe.extractor.exceptions.ExtractionException
     * @throws java.io.IOException
     */
    suspend fun extractStream(url: String): ExtractedStream = withContext(Dispatchers.IO) {
        // NewPipe.getServiceByUrl() resuelve el servicio (YouTube) por la URL.
        val info = StreamInfo.getInfo(NewPipe.getServiceByUrl(url), url)

        // Elegimos el mejor stream de audio (ver pickBestAudio).
        val audio = pickBestAudio(info.audioStreams)

        ExtractedStream(
            videoId = info.id,
            title = info.name,
            artist = info.uploaderName,
            durationSeconds = info.duration,
            audioUrl = checkNotNull(audio.content) { "El stream de audio no tiene URL" },
            audioExtension = audio.format?.suffix ?: "m4a",
            thumbnailUrl = info.thumbnails.maxByOrNull { it.height }?.url,
        )
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

    private companion object {
        val PREFERRED_FORMATS = setOf(MediaFormat.M4A, MediaFormat.MP3)
    }
}