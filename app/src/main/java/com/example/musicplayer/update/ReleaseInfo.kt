package com.example.musicplayer.update

/**
 * Información de una release de GitHub con el APK de la app.
 */
data class ReleaseInfo(
    /** Etiqueta de la release, p. ej. `v1.0.1`. Se usa para comparar versiones. */
    val tagName: String,
    /** Título de la release. */
    val name: String,
    /** Notas de la release (changelog). */
    val body: String,
    /** Fecha de publicación en formato ISO 8601. */
    val publishedAt: String,
    /** URL directa de descarga del APK. */
    val apkUrl: String,
    /** Nombre del archivo APK adjunto. */
    val apkName: String,
    /** Tamaño del APK en bytes (0 si no se conoce). */
    val apkSizeBytes: Long,
)