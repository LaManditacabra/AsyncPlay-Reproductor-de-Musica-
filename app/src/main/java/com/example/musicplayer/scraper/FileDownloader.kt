package com.example.musicplayer.scraper

import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

/**
 * Utilidad para descargar archivos binarios (audio y portadas) directamente a
 * disco, escribiendo en streaming para no cargar archivos grandes en memoria.
 *
 * El audio se descarga por fragmentos en paralelo (multi-hilo) cuando el
 * servidor soporta rangos HTTP (Accept-Ranges). Eso multiplica la velocidad en
 * redes con latencia o límites por conexión. Si no soporta rangos, se usa una
 * descarga secuencial normal.
 */
object FileDownloader {

    /** Descarga el recurso de [url] y lo guarda en [destination]. */
    fun download(url: String, destination: File) {
        val connection = URL(url).openConnection() as HttpURLConnection
        try {
            configure(connection)
            if (connection.responseCode >= 400) {
                throw IOException("Error HTTP ${connection.responseCode} descargando $url")
            }
            destination.parentFile?.mkdirs()
            connection.inputStream.use { input ->
                destination.outputStream().use { output -> input.copyTo(output) }
            }
        } finally {
            connection.disconnect()
        }
    }

    /**
     * Igual que [download] pero reporta el progreso (0-100) a través de
     * [onProgress] (callback suspendible para poder notificar desde corutinas).
     */
    suspend fun downloadWithProgress(
        url: String,
        destination: File,
        onProgress: suspend (Int) -> Unit,
    ) {
        val size = probeSize(url)
        val chunks = decideChunks(size)
        if (chunks <= 1 || size <= 0) {
            sequentialDownload(url, destination, onProgress)
            return
        }

        val downloaded = AtomicLong(0L)
        val lastReportedPercent = AtomicInteger(-1)
        val chunkSize = size / chunks

        // Descarga cada fragmento en paralelo (una conexión por hilo).
        coroutineScope {
            val jobs = (0 until chunks).map { i ->
                async(Dispatchers.IO) {
                    val start = i * chunkSize
                    val end = if (i == chunks - 1) size - 1 else (i + 1) * chunkSize - 1
                    downloadRange(url, start, end, destination.partFile(i)) { bytes ->
                        val total = downloaded.addAndGet(bytes)
                        val percent = ((total * 100) / size).toInt()
                        val last = lastReportedPercent.get()
                        if (percent != last && lastReportedPercent.compareAndSet(last, percent)) {
                            onProgress(percent)
                        }
                    }
                }
            }
            jobs.awaitAll()
        }

        // Une los fragmentos en el archivo final y limpia los temporales.
        destination.parentFile?.mkdirs()
        destination.outputStream().use { output ->
            for (i in 0 until chunks) {
                val part = destination.partFile(i)
                part.inputStream().use { input -> input.copyTo(output) }
                part.delete()
            }
        }
    }

    /** Pide solo el primer byte para conocer el tamaño total y si soporta rangos. */
    private fun probeSize(url: String): Long {
        val connection = URL(url).openConnection() as HttpURLConnection
        return try {
            configure(connection)
            connection.setRequestProperty(HEADER_RANGE, "bytes=0-0")
            if (connection.responseCode != HttpURLConnection.HTTP_PARTIAL) return -1L
            // El total viene en Content-Range: bytes 0-0/TOTAL.
            val contentRange = connection.getHeaderField(HEADER_CONTENT_RANGE)
            contentRange?.substringAfter('/')?.toLongOrNull() ?: -1L
        } catch (e: Exception) {
            -1L
        } finally {
            connection.disconnect()
        }
    }

    /** Cuántos fragmentos usar: 1 por cada [MIN_CHUNK_SIZE], tope [MAX_CHUNKS]. */
    private fun decideChunks(size: Long): Int {
        if (size <= 0) return 1
        return ((size / MIN_CHUNK_SIZE).toInt()).coerceIn(1, MAX_CHUNKS)
    }

    /** Descarga un fragmento concreto del archivo (bytes [start]-[end]). */
    private suspend fun downloadRange(
        url: String,
        start: Long,
        end: Long,
        part: File,
        onBytes: suspend (Long) -> Unit,
    ) {
        val connection = URL(url).openConnection() as HttpURLConnection
        try {
            configure(connection)
            connection.setRequestProperty(HEADER_RANGE, "bytes=$start-$end")
            if (connection.responseCode != HttpURLConnection.HTTP_PARTIAL) {
                throw IOException("El servidor no respondió con el rango solicitado")
            }
            part.parentFile?.mkdirs()
            val buffer = ByteArray(BUFFER_SIZE)
            connection.inputStream.use { input ->
                part.outputStream().use { output ->
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                        onBytes(read.toLong())
                    }
                }
            }
        } finally {
            connection.disconnect()
        }
    }

    /** Descarga secuencial de un solo flujo (cuando no hay soporte de rangos). */
    private suspend fun sequentialDownload(
        url: String,
        destination: File,
        onProgress: suspend (Int) -> Unit,
    ) {
        val connection = URL(url).openConnection() as HttpURLConnection
        try {
            configure(connection)
            if (connection.responseCode >= 400) {
                throw IOException("Error HTTP ${connection.responseCode} descargando $url")
            }
            val total = connection.contentLengthLong
            destination.parentFile?.mkdirs()

            val buffer = ByteArray(BUFFER_SIZE)
            var downloaded = 0L
            var lastReported = -1

            connection.inputStream.use { input ->
                destination.outputStream().use { output ->
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                        downloaded += read
                        if (total > 0) {
                            val percent = ((downloaded * 100) / total).toInt()
                            if (percent != lastReported) {
                                lastReported = percent
                                onProgress(percent)
                            }
                        }
                    }
                }
            }
        } finally {
            connection.disconnect()
        }
    }

    /** Nombre del archivo temporal para el fragmento [index]. */
    private fun File.partFile(index: Int): File =
        File(parentFile, "${name}.part$index")

    /** Cabeceras comunes para todas las peticiones de descarga. */
    private fun configure(connection: HttpURLConnection) {
        connection.requestMethod = "GET"
        connection.connectTimeout = CONNECT_TIMEOUT_MS
        connection.readTimeout = READ_TIMEOUT_MS
        connection.instanceFollowRedirects = true
        connection.setRequestProperty(HEADER_USER_AGENT, USER_AGENT)
        connection.setRequestProperty(HEADER_ACCEPT_ENCODING, "identity")
    }

    private const val CONNECT_TIMEOUT_MS = 20_000
    private const val READ_TIMEOUT_MS = 30_000
    private const val BUFFER_SIZE = 64 * 1024
    private const val MAX_CHUNKS = 4
    private const val MIN_CHUNK_SIZE = 1L * 1024 * 1024 // 1 MB mínimo por fragmento
    private const val HEADER_RANGE = "Range"
    private const val HEADER_CONTENT_RANGE = "Content-Range"
    private const val HEADER_USER_AGENT = "User-Agent"
    private const val HEADER_ACCEPT_ENCODING = "Accept-Encoding"
    private const val USER_AGENT =
        "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0 Safari/537.36"
}