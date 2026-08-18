package com.example.musicplayer.update

import com.example.musicplayer.BuildConfig
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/** Resultado de la consulta de la última release. */
sealed interface LatestReleaseResult {
    /** Hay una release nueva; [etag] se guardará para futuras peticiones condicionales. */
    data class Found(val release: ReleaseInfo, val etag: String?) : LatestReleaseResult

    /** Nada cambió desde la última consulta (304). No consume rate limit. */
    data object NotModified : LatestReleaseResult

    /** El repo aún no tiene releases (404). */
    data object NoReleases : LatestReleaseResult

    /** Rate limit alcanzado (403); [resetAtEpochSeconds] indica cuándo se podrá reintentar. */
    data class RateLimited(val resetAtEpochSeconds: Long) : LatestReleaseResult
}

/**
 * Cliente de la API pública de GitHub para consultar la última release del repo.
 *
 * Endpoint: `GET /repos/{owner}/{repo}/releases/latest`
 *
 * Protección contra el rate limit (60 peticiones/hora sin autenticación):
 *  - peticiones condicionales con `If-None-Match`: si nada cambió, GitHub
 *    responde 304 y ESA petición NO consume cuota;
 *  - expone el estado 403/rate-limit con el instante de reset.
 */
class GitHubReleasesClient {

    /**
     * Obtiene la última release publicada.
     *
     * @param etag ETag de la última respuesta 200 (o `null` en la primera consulta).
     */
    suspend fun fetchLatestRelease(etag: String? = null): LatestReleaseResult =
        withContext(Dispatchers.IO) {
            val url =
                "https://api.github.com/repos/${BuildConfig.REPO_OWNER}/${BuildConfig.REPO_NAME}/releases/latest"
            val connection = URL(url).openConnection() as HttpURLConnection
            try {
                connection.requestMethod = "GET"
                connection.connectTimeout = CONNECT_TIMEOUT_MS
                connection.readTimeout = READ_TIMEOUT_MS
                connection.setRequestProperty("Accept", "application/vnd.github+json")
                connection.setRequestProperty("User-Agent", "AsyncPlay")
                // Petición condicional: si el ETag coincide, GitHub responde 304 sin coste.
                etag?.let { connection.setRequestProperty(HEADER_IF_NONE_MATCH, it) }

                when (connection.responseCode) {
                    HttpURLConnection.HTTP_OK -> {
                        val body = connection.inputStream.bufferedReader().use { it.readText() }
                        LatestReleaseResult.Found(
                            release = parseRelease(JSONObject(body)),
                            etag = connection.getHeaderField(HEADER_ETAG),
                        )
                    }
                    HttpURLConnection.HTTP_NOT_MODIFIED -> LatestReleaseResult.NotModified
                    HttpURLConnection.HTTP_NOT_FOUND -> LatestReleaseResult.NoReleases
                    HttpURLConnection.HTTP_FORBIDDEN -> LatestReleaseResult.RateLimited(
                        resetAtEpochSeconds = connection
                            .getHeaderField(HEADER_RATE_LIMIT_RESET)
                            ?.toLongOrNull()
                            ?: 0L,
                    )
                    else -> throw IOException("GitHub API error: ${connection.responseCode}")
                }
            } finally {
                connection.disconnect()
            }
        }

    private fun parseRelease(json: JSONObject): ReleaseInfo {
        // Busca el primer asset con extensión .apk (puede haber varios por release).
        val apkAsset = json.optJSONArray("assets")
            ?.let { assets ->
                (0 until assets.length())
                    .mapNotNull { assets.optJSONObject(it) }
                    .firstOrNull { it.optString("name").endsWith(".apk", ignoreCase = true) }
            }

        return ReleaseInfo(
            tagName = json.optString("tag_name"),
            name = json.optString("name", json.optString("tag_name")),
            body = json.optString("body"),
            publishedAt = json.optString("published_at"),
            apkUrl = apkAsset?.optString("browser_download_url").orEmpty(),
            apkName = apkAsset?.optString("name").orEmpty(),
            apkSizeBytes = apkAsset?.optLong("size") ?: 0L,
        )
    }

    private companion object {
        const val CONNECT_TIMEOUT_MS = 15_000
        const val READ_TIMEOUT_MS = 15_000
        const val HEADER_IF_NONE_MATCH = "If-None-Match"
        const val HEADER_ETAG = "ETag"
        const val HEADER_RATE_LIMIT_RESET = "X-RateLimit-Reset"
    }
}