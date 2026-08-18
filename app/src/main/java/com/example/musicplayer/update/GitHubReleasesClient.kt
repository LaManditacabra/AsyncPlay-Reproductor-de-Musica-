package com.example.musicplayer.update

import com.example.musicplayer.BuildConfig
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * Cliente de la API pública de GitHub para consultar la última release del repo.
 *
 * Endpoint: `GET /repos/{owner}/{repo}/releases/latest`
 * (sin autenticación: 60 peticiones/hora por IP, suficiente para un chequeo ocasional).
 */
class GitHubReleasesClient {

    /**
     * Obtiene la última release publicada.
     *
     * @return la [ReleaseInfo] de la última release, o `null` si el repo aún no
     *         tiene releases (HTTP 404).
     * @throws IOException ante errores de red o HTTP distintos de 404.
     */
    suspend fun fetchLatestRelease(): ReleaseInfo? = withContext(Dispatchers.IO) {
        val url =
            "https://api.github.com/repos/${BuildConfig.REPO_OWNER}/${BuildConfig.REPO_NAME}/releases/latest"
        val connection = URL(url).openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "GET"
            connection.connectTimeout = CONNECT_TIMEOUT_MS
            connection.readTimeout = READ_TIMEOUT_MS
            connection.setRequestProperty("Accept", "application/vnd.github+json")
            connection.setRequestProperty("User-Agent", "AsyncPlay")

            when (connection.responseCode) {
                HttpURLConnection.HTTP_OK -> {
                    val body = connection.inputStream.bufferedReader().use { it.readText() }
                    parseRelease(JSONObject(body))
                }
                HttpURLConnection.HTTP_NOT_FOUND -> null
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
    }
}