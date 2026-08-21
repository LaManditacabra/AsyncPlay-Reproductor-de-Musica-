package com.example.musicplayer.scraper

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.search.SearchInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem

/**
 * Resultado de búsqueda de YouTube.
 */
data class SearchResult(
    val title: String,
    val url: String,
    val thumbnailUrl: String?,
    val durationMs: Long,
    val uploader: String,
)

/**
 * Busca canciones en YouTube usando NewPipeExtractor (sin API key).
 */
class SearchEngine {

    /**
     * Busca [query] y devuelve la primera página de resultados.
     * Lanza una excepción si el extractor falla.
     */
    suspend fun search(query: String): List<SearchResult> = withContext(Dispatchers.IO) {
        // ServiceList.YouTube directo: getServiceByUrl() rechaza la URL base sin ruta.
        val service = ServiceList.YouTube
        val handler = service.getSearchQHFactory().fromQuery(query)

        // IMPORTANTE: hay que descargar la página antes de obtener la info.
        // SearchInfo.getInfo(extractor) (un solo argumento) NO llama a
        // fetchPage(), así que initialData quedaría null y el extractor lanzaría
        // un NullPointerException al parsear. Usamos la variante de dos
        // argumentos, que sí descarga la página.
        val info = SearchInfo.getInfo(service, handler)

        // El extractor silencia los fallos internos en info.getErrors() y devuelve
        // una página vacía. Si los hay, los propagamos para no mostrar "sin resultados"
        // cuando en realidad falló la extracción.
        info.errors.firstOrNull()?.let { error ->
            val cause = error.cause ?: error
            val diagnostics = NewPipeDownloader.lastYouTubeDiagnostics.joinToString("\n")
            val stack = cause.stackTrace.joinToString("\n") { "  at $it" }
            throw IllegalStateException(
                "Error del extractor: $cause\n" +
                    "Peticiones a YouTube:\n${diagnostics.ifEmpty { "n/a" }}\n" +
                    "Stacktrace:\n$stack",
                cause,
            )
        }

        info.relatedItems.mapNotNull { item ->
            (item as? StreamInfoItem)?.let {
                SearchResult(
                    title = it.name,
                    url = it.url,
                    thumbnailUrl = it.thumbnails.firstOrNull()?.url,
                    durationMs = it.duration * 1_000,
                    uploader = it.uploaderName,
                )
            }
        }
    }
}