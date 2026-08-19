package com.example.musicplayer.player

import android.content.ComponentName
import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.PlaybackException
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.example.musicplayer.data.model.Song
import com.google.common.util.concurrent.MoreExecutors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Cliente de control de reproducción.
 *
 * Se conecta al [PlayerService] mediante un [MediaController] (protocolo
 * Media3 Session) y expone el estado de reproducción como un [StateFlow]
 * observable para que la UI reaccione de forma reactiva.
 *
 * Centraliza todas las acciones de reproducción: reproducir una lista desde un
 * índice, pausar/reanudar, saltar, buscar, modo aleatorio y repetición. También
 * persiste la posición de la última canción para reanudarla al abrir la app.
 */
class PlaybackController(context: Context) : Player.Listener {

    /** Estado de reproducción que la UI consume para pintarse. */
    data class PlaybackState(
        val isConnected: Boolean = false,
        val isPlaying: Boolean = false,
        val isBuffering: Boolean = false,
        val positionMs: Long = 0L,
        val durationMs: Long = 0L,
        val currentSong: Song? = null,
        val currentIndex: Int = 0,
        val queue: List<Song> = emptyList(),
        val shuffleEnabled: Boolean = false,
        val repeatMode: Int = Player.REPEAT_MODE_OFF,
    )

    private val appContext = context.applicationContext
    private val preferences = PlaybackPreferences(appContext)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val _state = MutableStateFlow(PlaybackState())
    val state: StateFlow<PlaybackState> = _state.asStateFlow()

    private var mediaController: MediaController? = null
    private var tickerJob: Job? = null
    private var lastSavedPositionMs = 0L

    /** Lista de canciones que se está reproduciendo (para conocer la actual). */
    private var currentPlaylist: List<Song> = emptyList()

    /** Reproducción solicitada antes de que la conexión con el servicio estuviera lista. */
    private var pendingPlay: Pair<List<Song>, Int>? = null

    // ------------------------------------------------------------------
    // Conexión con el servicio de reproducción
    // ------------------------------------------------------------------

    private fun connect() {
        val sessionToken =
            SessionToken(appContext, ComponentName(appContext, PlayerService::class.java))

        // buildAsync() devuelve un futuro que se completa al conectarse.
        // (Desde Media3 1.10 ya no existe el callback `onConnected` del listener.)
        val controllerFuture =
            MediaController.Builder(appContext, sessionToken)
                .setListener(controllerListener)
                .buildAsync()

        controllerFuture.addListener(
            {
                try {
                    onControllerConnected(controllerFuture.get())
                } catch (e: Exception) {
                    android.util.Log.e(TAG, "No se pudo conectar con el servicio", e)
                }
            },
            MoreExecutors.directExecutor(),
        )
    }

    private val controllerListener = object : MediaController.Listener {
        override fun onDisconnected(controller: MediaController) {
            mediaController = null
            _state.update { it.copy(isConnected = false) }
            stopTicker()
        }
    }

    init {
        // IMPORTANTE: debe ir después de declarar `controllerListener`, porque
        // connect() lo usa y las propiedades se inicializan en orden.
        connect()
    }

    /** Se invoca cuando la conexión con el servicio está lista. */
    private fun onControllerConnected(controller: MediaController) {
        mediaController = controller
        controller.addListener(this@PlaybackController)
        _state.update {
            it.copy(
                isConnected = true,
                shuffleEnabled = controller.shuffleModeEnabled,
                repeatMode = controller.repeatMode,
            )
        }

        // Ejecuta la reproducción pendiente (si se pidió antes de conectar).
        pendingPlay?.let { (songs, index) ->
            playAt(songs, index)
            pendingPlay = null
        }
        handlePendingRestoreIfNeeded()
        startTicker()
    }

    // ------------------------------------------------------------------
    // Controles de reproducción (API pública para el ViewModel)
    // ------------------------------------------------------------------

    /**
     * Reproduce una lista de canciones empezando por [startIndex].
     * El resto de canciones queda encolado como playlist (next/prev).
     */
    fun play(songs: List<Song>, startIndex: Int) {
        val controller = mediaController
        if (controller == null) {
            pendingPlay = songs to startIndex
        } else {
            playAt(songs, startIndex)
        }
    }

    /**
     * Restaura una canción concreta en una posición dada, sin reproducirla.
     * Se usa al arrancar la app para "reanudar" la última sesión.
     */
    fun restorePosition(songs: List<Song>, songId: Long, positionMs: Long) {
        val index = songs.indexOfFirst { it.id == songId }
        if (index < 0) return
        val controller = mediaController
        if (controller == null) {
            // No queremos autoplay; solo preparar la lista al conectar.
            pendingRestore = songs to index to positionMs
        } else {
            restoreAt(songs, index, positionMs)
        }
    }

    /** Pausa si está sonando o reanuda si está en pausa. */
    fun togglePlayPause() {
        mediaController?.let { controller ->
            if (controller.isPlaying) controller.pause() else controller.play()
        }
    }

    /** Busca (seek) a la posición indicada en milisegundos. */
    fun seekTo(positionMs: Long) {
        mediaController?.seekTo(positionMs)
    }

    fun skipToNext() = mediaController?.seekToNextMediaItem()

    fun skipToPrevious() = mediaController?.seekToPreviousMediaItem()

    /** Salta a un elemento concreto de la cola (p. ej. desde "Up next"). */
    fun skipToIndex(index: Int) {
        mediaController?.seekTo(index, 0L)
    }

    /** Alterna el modo aleatorio (shuffle). */
    fun toggleShuffle() {
        mediaController?.let { controller ->
            controller.shuffleModeEnabled = !controller.shuffleModeEnabled
        }
    }

    /** Cicla el modo de repetición: Off -> All -> One -> Off. */
    fun cycleRepeatMode() {
        mediaController?.let { controller ->
            controller.repeatMode = when (controller.repeatMode) {
                Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
                Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
                else -> Player.REPEAT_MODE_OFF
            }
        }
    }

    // ------------------------------------------------------------------
    // Interno
    // ------------------------------------------------------------------

    private var pendingRestore: Pair<Pair<List<Song>, Int>, Long>? = null

    private fun playAt(songs: List<Song>, startIndex: Int) {
        val controller = mediaController ?: return
        currentPlaylist = songs
        controller.setMediaItems(songs.map { it.toMediaItem() }, startIndex, 0L)
        controller.prepare()
        controller.play()
        _state.update {
            it.copy(currentSong = songs.getOrNull(startIndex), queue = songs, currentIndex = startIndex)
        }
    }

    private fun restoreAt(songs: List<Song>, index: Int, positionMs: Long) {
        val controller = mediaController ?: return
        currentPlaylist = songs
        controller.setMediaItems(songs.map { it.toMediaItem() }, index, positionMs)
        controller.prepare()
        _state.update {
            it.copy(currentSong = songs.getOrNull(index), queue = songs, currentIndex = index)
        }
    }

    private fun handlePendingRestoreIfNeeded() {
        if (mediaController != null) {
            pendingRestore?.let { (indexed, positionMs) ->
                restoreAt(indexed.first, indexed.second, positionMs)
                pendingRestore = null
            }
        }
    }

    /** Convierte una [Song] en un [MediaItem] con metadatos para la notificación. */
    private fun Song.toMediaItem(): MediaItem =
        MediaItem.Builder()
            .setMediaId(id.toString())
            .setUri(toUri())
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(title)
                    .setArtist(artist)
                    .setArtworkUri(thumbnailUrl?.let { android.net.Uri.parse(it) })
                    .build(),
            )
            .build()

    /** Actualiza la posición cada 500 ms y persiste el progreso cada ~5 s. */
    private fun startTicker() {
        stopTicker()
        tickerJob = scope.launch {
            while (isActive) {
                mediaController?.let { controller ->
                    _state.update {
                        it.copy(
                            isPlaying = controller.isPlaying,
                            positionMs = controller.currentPosition,
                            durationMs = controller.duration,
                            shuffleEnabled = controller.shuffleModeEnabled,
                            repeatMode = controller.repeatMode,
                        )
                    }
                    persistProgress(controller)
                }
                delay(TICK_MS)
            }
        }
    }

    /** Guarda la posición actual (como máximo 1 vez cada 5 s). */
    private fun persistProgress(controller: MediaController) {
        val position = controller.currentPosition
        if (position <= 0 || position - lastSavedPositionMs < SAVE_INTERVAL_MS) return
        lastSavedPositionMs = position
        preferences.lastSongId = currentPlaylist.getOrNull(controller.currentMediaItemIndex)?.id ?: -1
        preferences.lastPositionMs = position
    }

    private fun stopTicker() {
        tickerJob?.cancel()
        tickerJob = null
    }

    // ------------------------------------------------------------------
    // Player.Listener: eventos que llegan desde el servicio
    // ------------------------------------------------------------------

    override fun onIsPlayingChanged(isPlaying: Boolean) {
        _state.update { it.copy(isPlaying = isPlaying) }
    }

    override fun onPlaybackStateChanged(playbackState: Int) {
        _state.update { it.copy(isBuffering = playbackState == Player.STATE_BUFFERING) }
    }

    override fun onPlayerError(error: PlaybackException) {
        // Archivo corrupto, formato no soportado, etc.
        // En un caso real se notificaría al usuario; aquí simplemente se loguea.
        android.util.Log.e(TAG, "Error de reproducción", error)
    }

    /** Al cambiar de canción (auto-next incluido) actualizamos la canción actual. */
    override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
        val index = mediaController?.currentMediaItemIndex ?: 0
        val song = currentPlaylist.getOrNull(index)
        _state.update { it.copy(currentSong = song, currentIndex = index) }
        song?.let { preferences.lastSongId = it.id }
    }

    /** Actualiza el estado cuando cambia el modo aleatorio o de repetición. */
    override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
        _state.update { it.copy(shuffleEnabled = shuffleModeEnabled) }
    }

    override fun onRepeatModeChanged(repeatMode: Int) {
        _state.update { it.copy(repeatMode = repeatMode) }
    }

    private companion object {
        const val TAG = "PlaybackController"
        const val TICK_MS = 500L
        const val SAVE_INTERVAL_MS = 5_000L
    }
}