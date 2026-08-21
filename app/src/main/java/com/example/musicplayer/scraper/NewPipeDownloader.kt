package com.example.musicplayer.scraper

import android.util.Log
import java.io.IOException
import java.util.concurrent.TimeUnit
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Request as OkHttpRequest
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request
import org.schabi.newpipe.extractor.downloader.Response

/**
 * Implementación de [Downloader] para NewPipeExtractor basada en OkHttp.
 *
 * NewPipeExtractor no trae una implementación de red propia: exige que la app
 * le proporcione un [Downloader] capaz de ejecutar [execute]. OkHttp es el
 * mismo motor que usa la app oficial de NewPipe: maneja gzip automáticamente,
 * mantiene cookies (clave para la cookie CONSENT que YouTube exige en la UE)
 * y sigue redirecciones sin romper el POST del InnerTube.
 */
class NewPipeDownloader : Downloader() {

    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(CONNECT_TIMEOUT_MS, TimeUnit.MILLISECONDS)
        .readTimeout(READ_TIMEOUT_MS, TimeUnit.MILLISECONDS)
        .followRedirects(true)
        .cookieJar(PersistentCookieJar)
        .build()

    /**
     * Ejecuta una petición HTTP genérica descrita por [request] y devuelve la
     * respuesta como un [Response]. Se usa internamente por el extractor para
     * pedir páginas y respuestas de la API (InnerTube) de YouTube.
     */
    companion object {
        const val TAG = "NewPipeDownloader"
        const val CONNECT_TIMEOUT_MS = 20_000L
        const val READ_TIMEOUT_MS = 20_000L
        const val HEADER_USER_AGENT = "User-Agent"
        val JSON_MEDIA_TYPE = "application/json".toMediaType()
        val EMPTY_BODY = ByteArray(0).toRequestBody()
        const val USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:140.0) Gecko/20100101 Firefox/140.0"

        /**
         * Últimas respuestas de YouTube (código + fragmento del cuerpo) para
         * diagnóstico. Se rellena en [execute] para poder mostrar la causa real de
         * un fallo de búsqueda directamente en la interfaz.
         */
        @Volatile
        var lastYouTubeDiagnostics: MutableList<String> = ArrayList()

        fun recordDiagnostic(entry: String) {
            lastYouTubeDiagnostics.add(entry)
            if (lastYouTubeDiagnostics.size > 8) {
                lastYouTubeDiagnostics.removeAt(0)
            }
        }
    }

    override fun execute(request: Request): Response {
        try {
            val builder = OkHttpRequest.Builder()
                .url(request.url())

            val body = request.dataToSend()?.toRequestBody(JSON_MEDIA_TYPE)
            when (request.httpMethod().uppercase()) {
                "GET" -> builder.get()
                "HEAD" -> builder.head()
                "DELETE" -> builder.delete(body)
                "PATCH" -> builder.patch(body ?: EMPTY_BODY)
                "PUT" -> builder.put(body ?: EMPTY_BODY)
                else -> builder.post(body ?: EMPTY_BODY)
            }

            // Cabeceras proporcionadas por el extractor + User-Agent.
            request.headers().forEach { (name, values) ->
                values.forEach { builder.header(name, it) }
            }
            builder.header(HEADER_USER_AGENT, USER_AGENT)

            val response = client.newCall(builder.build()).execute()
            val bodyString = response.body?.string().orEmpty()

            // Diagnóstico: registramos el código y un fragmento del cuerpo para las
            // peticiones a YouTube (youtubei, sw.js, results...). Sirve para ver si
            // YouTube bloquea la búsqueda y en qué punto.
            val url = request.url()
            val isYouTube = url.contains("youtube.com")
            if (isYouTube || response.code >= 400) {
                val entry = "${request.httpMethod()} $url -> ${response.code}: " +
                    bodyString.take(400)
                Log.i(TAG, entry)
                if (isYouTube) {
                    recordDiagnostic(entry)
                }
            }

            val headers = response.headers.toMultimap()
            return Response(
                response.code,
                response.message,
                headers,
                bodyString,
                response.request.url.toString(),
            )
        } catch (e: IOException) {
            throw e
        }
    }
}

/**
 * Almacén de cookies en memoria. YouTube (sobre todo en la UE) exige la cookie
 * CONSENT para devolver resultados de búsqueda; si no se reenvían, las
 * respuestas llegan vacías o bloqueadas. Se resuelven por dominio (no por host
 * exacto) para que las cookies de youtube.com se envíen también a
 * www.youtube.com, consent.youtube.com, etc.
 */
private object PersistentCookieJar : CookieJar {
    private val cache = ArrayList<Cookie>()

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        for (cookie in cookies) {
            val index = cache.indexOfFirst { it.name == cookie.name && it.domain == cookie.domain }
            if (index >= 0) cache[index] = cookie else cache.add(cookie)
        }
    }

    override fun loadForRequest(url: HttpUrl): List<Cookie> =
        cache.filter { it.matches(url) }
}