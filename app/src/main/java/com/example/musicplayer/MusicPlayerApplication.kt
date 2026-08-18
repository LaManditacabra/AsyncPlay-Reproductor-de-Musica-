package com.example.musicplayer

import android.app.Application
import com.example.musicplayer.data.db.AppDatabase
import com.example.musicplayer.data.repository.SongRepository
import com.example.musicplayer.player.PlaybackController
import com.example.musicplayer.scraper.NewPipeDownloader
import com.example.musicplayer.update.UpdateManager
import java.util.Locale
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.localization.Localization

/**
 * Application de la app. Actúa como contenedor de dependencias (DI manual):
 * inicializa una única vez la base de datos, el repository y el controlador de
 * reproducción, y los expone a las capas superiores.
 */
class MusicPlayerApplication : Application() {

    lateinit var repository: SongRepository
        private set

    lateinit var playbackController: PlaybackController
        private set

    lateinit var updateManager: UpdateManager
        private set

    override fun onCreate() {
        super.onCreate()

        // NewPipeExtractor exige un Downloader antes de cualquier extracción.
        NewPipe.init(NewPipeDownloader())
        NewPipe.setupLocalization(Localization.fromLocale(Locale.getDefault()))

        val database = AppDatabase.getInstance(this)
        repository = SongRepository(database.songDao())
        playbackController = PlaybackController(this)
        updateManager = UpdateManager(this)
    }
}