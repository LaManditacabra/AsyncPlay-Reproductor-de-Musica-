package com.example.musicplayer.player

import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService

/**
 * Servicio de reproducción basado en Media3 ([MediaSessionService]).
 *
 * Al ser un [MediaSessionService], Media3 se encarga por nosotros de:
 *  - Mantener la reproducción con la pantalla apagada o la app en segundo plano
 *    (arranca como foreground service cuando empieza la reproducción).
 *  - Publicar la notificación de reproducción en curso.
 *  - Responder a los controles de medios del sistema (bluetooth, pantalla de
 *    bloqueo, auriculares, etc.) a través de la [MediaSession].
 *
 * El `ExoPlayer` solo se crea cuando un cliente (nuestro [PlaybackController])
 * solicita la sesión vía [onGetSession], siguiendo el patrón recomendado.
 */
class PlayerService : MediaSessionService() {

    private var mediaSession: MediaSession? = null

    /**
     * Devuelve la [MediaSession] activa. Si aún no existe (primera conexión),
     * crea el [ExoPlayer] y la sesión y los guarda.
     */
    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession =
        mediaSession ?: run {
            val player = ExoPlayer.Builder(this).build()
            val session = MediaSession.Builder(this, player).build()
            mediaSession = session
            session
        }

    override fun onDestroy() {
        // Libera el reproductor y la sesión para evitar fugas de recursos.
        mediaSession?.player?.release()
        mediaSession?.release()
        mediaSession = null
        super.onDestroy()
    }
}