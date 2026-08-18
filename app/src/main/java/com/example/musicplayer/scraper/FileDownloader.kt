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

    private const val CONNECT_TIMEOUT_MS = 20_000
    private const val READ_TIMEOUT_MS = 30_000
    private const val HEADER_USER_AGENT = "User-Agent"
    private const val HEADER_ACCEPT_ENCODING = "Accept-Encoding"
    private const val USER_AGENT =
        "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0 Safari/537.36"
}