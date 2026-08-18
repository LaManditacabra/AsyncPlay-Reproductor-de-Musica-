package com.example.musicplayer

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import com.example.musicplayer.ui.songs.SongsScreen
import com.example.musicplayer.ui.theme.MusicPlayerTheme

/**
 * Actividad principal: única Activity de la app, que monta la interfaz Compose.
 */
class MainActivity : ComponentActivity() {

    private val requestNotificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {
            // El usuario puede denegarlo; la reproducción seguirá funcionando
            // pero sin notificación de reproducción en curso.
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // En Android 13+ la notificación de reproducción requiere permiso en runtime.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requestNotificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        setContent {
            MusicPlayerTheme {
                SongsScreen()
            }
        }
    }
}