package com.example.musicplayer.update

/**
 * Compara versiones tipo `semver` (`v1.2.3` o `1.2`). Soporta tags con prefijo
 * `v` y versiones con menos de 3 segmentos, normalizando ambos a 3.
 */
object VersionComparator {

    /**
     * Devuelve `true` si [latestTag] es estrictamente más nueva que
     * [currentVersion].
     */
    fun isNewer(latestTag: String, currentVersion: String): Boolean {
        val latest = parse(latestTag) ?: return false
        val current = parse(currentVersion) ?: return false
        for (i in 0 until SEGMENTS) {
            when {
                latest[i] > current[i] -> return true
                latest[i] < current[i] -> return false
            }
        }
        return false
    }

    /** Normaliza "v1.2.3-beta" -> [1, 2, 3]; devuelve null si no es una versión válida. */
    private fun parse(version: String): IntArray? {
        val clean = version.trim().trimStart('v', 'V')
        val parts = clean
            .substringBefore('-')   // ignora sufijos tipo "-beta"
            .split('.')
            .mapNotNull { it.toIntOrNull() }
        if (parts.isEmpty()) return null
        return IntArray(SEGMENTS) { parts.getOrElse(it) { 0 } }
    }

    private const val SEGMENTS = 3
}