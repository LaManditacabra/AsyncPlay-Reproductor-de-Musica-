package com.example.musicplayer.scraper

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.NewPipe
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

    companion object {
        private const val YOUTUBE_URL = "https://www.youtube.com"
    }

    /**
     * Busca [query] y devuelve la primera página de resultados.
     * Lanza una excepción si el extractor falla.
     */
    suspend fun search(query: String): List<SearchResult> = withContext(Dispatchers.IO) {
        val service = NewPipe.getServiceByUrl(YOUTUBE_URL)
        val handler = service.getSearchQHFactory().fromQuery(query)
        val extractor = service.getSearchExtractor(handler)
        val info = SearchInfo.getInfo(extractor)

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