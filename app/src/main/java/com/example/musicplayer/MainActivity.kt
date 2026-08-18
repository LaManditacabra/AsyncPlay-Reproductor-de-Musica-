package com.example.musicplayer

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.musicplayer.ui.search.SearchScreen
import com.example.musicplayer.ui.songs.SongsScreen
import com.example.musicplayer.ui.theme.MusicPlayerTheme

/**
 * Actividad principal: única Activity de la app, que monta la interfaz Compose
 * con navegación entre la pantalla de canciones y la búsqueda.
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
                val navController = rememberNavController()
                NavHost(navController = navController, startDestination = "songs") {
                    composable("songs") {
                        SongsScreen(
                            onNavigateToSearch = { navController.navigate("search") },
                        )
                    }
                    composable("search") {
                        SearchScreen(onBack = { navController.popBackStack() })
                    }
                }
            }
        }
    }
}