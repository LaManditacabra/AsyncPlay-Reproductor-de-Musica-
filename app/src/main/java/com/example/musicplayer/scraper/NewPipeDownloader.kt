package com.example.musicplayer.scraper

import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.GZIPInputStream
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request
import org.schabi.newpipe.extractor.downloader.Response

/**
 * Implementación de [Downloader] para NewPipeExtractor.
 *
 * NewPipeExtractor no trae una implementación de red propia: exige que la app
 * le proporcione un [Downloader] capaz de ejecutar [execute]. Esta clase lo
 * resuelve con `HttpURLConnection` (sin dependencias extra).
 */
class NewPipeDownloader : Downloader() {

    /**
     * Ejecuta una petición HTTP genérica descrita por [request] y devuelve la
     * respuesta como un [Response]. Se usa internamente por el extractor para
     * pedir páginas y respuestas de la API (InnerTube) de YouTube.
     */
    override fun execute(request: Request): Response {
        val connection = URL(request.url()).openConnection() as HttpURLConnection

        try {
            connection.requestMethod = request.httpMethod()
            connection.connectTimeout = CONNECT_TIMEOUT_MS
            connection.readTimeout = READ_TIMEOUT_MS
            connection.instanceFollowRedirects = true

            // Cabeceras proporcionadas por el extractor.
            request.headers().forEach { (name, values) ->
                values.forEach { connection.addRequestProperty(name, it) }
            }
            connection.setRequestProperty(HEADER_USER_AGENT, USER_AGENT)
            connection.setRequestProperty(HEADER_ACCEPT_ENCODING, "gzip")

            // Cuerpo de las peticiones POST (el InnerTube de YouTube usa JSON).
            request.dataToSend()?.let { body ->
                connection.doOutput = true
                connection.setRequestProperty("Content-Length", body.size.toString())
                connection.outputStream.use { it.write(body) }
            }

            val responseCode = connection.responseCode
            val responseMessage = connection.responseMessage ?: ""
            val latestUrl = connection.url.toString()
            val headers = connection.headerFields.filterKeys { it != null }

            // Cuerpo de la respuesta (puede venir comprimida en gzip).
            val bodyStream =
                if (responseCode >= 400) connection.errorStream else connection.inputStream
            val responseBody = bodyStream?.let { stream ->
                if ("gzip" == connection.contentEncoding) {
                    GZIPInputStream(stream).readBytes().toString(Charsets.UTF_8)
                } else {
                    stream.readBytes().toString(Charsets.UTF_8)
                }
            } ?: ""

            return Response(responseCode, responseMessage, headers, responseBody, latestUrl)
        } catch (e: IOException) {
            throw e
        } finally {
            connection.disconnect()
        }
    }

    private companion object {
        const val CONNECT_TIMEOUT_MS = 20_000
        const val READ_TIMEOUT_MS = 20_000
        const val HEADER_USER_AGENT = "User-Agent"
        const val HEADER_ACCEPT_ENCODING = "Accept-Encoding"
        const val USER_AGENT =
            "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0 Safari/537.36"
    }
}