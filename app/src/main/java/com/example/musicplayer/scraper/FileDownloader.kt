package com.example.musicplayer.scraper

import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * Utilidad para descargar archivos binarios (audio y portadas) directamente a
 * disco, escribiendo en streaming para no cargar archivos grandes en memoria.
 */
object FileDownloader {

    /**
     * Descarga el recurso de [url] y lo guarda en [destination].
     *
     * @throws IOException si la descarga falla o el servidor responde con error.
     */
    fun download(url: String, destination: File) {
        val connection = URL(url).openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "GET"
            connection.connectTimeout = CONNECT_TIMEOUT_MS
            connection.readTimeout = READ_TIMEOUT_MS
            connection.instanceFollowRedirects = true
            connection.setRequestProperty(HEADER_USER_AGENT, USER_AGENT)
            // El audio se guarda tal cual; no queremos que se descomprima como texto.
            connection.setRequestProperty(HEADER_ACCEPT_ENCODING, "identity")

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
     * Si el servidor no indica el tamaño total, no se notifica.
     */
    suspend fun downloadWithProgress(
        url: String,
        destination: File,
        onProgress: suspend (Int) -> Unit,
    ) {
        val connection = URL(url).openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "GET"
            connection.connectTimeout = CONNECT_TIMEOUT_MS
            connection.readTimeout = READ_TIMEOUT_MS
            connection.instanceFollowRedirects = true
            connection.setRequestProperty(HEADER_USER_AGENT, USER_AGENT)
            connection.setRequestProperty(HEADER_ACCEPT_ENCODING, "identity")

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

    private const val CONNECT_TIMEOUT_MS = 20_000
    private const val READ_TIMEOUT_MS = 30_000
    private const val BUFFER_SIZE = 64 * 1024
    private const val HEADER_USER_AGENT = "User-Agent"
    private const val HEADER_ACCEPT_ENCODING = "Accept-Encoding"
    private const val USER_AGENT =
        "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0 Safari/537.36"
}