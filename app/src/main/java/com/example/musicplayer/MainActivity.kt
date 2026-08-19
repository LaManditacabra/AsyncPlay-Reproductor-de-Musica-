package com.example.musicplayer

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.musicplayer.settings.ThemeMode
import com.example.musicplayer.ui.playlists.PlaylistDetailScreen
import com.example.musicplayer.ui.playlists.PlaylistsScreen
import com.example.musicplayer.ui.search.SearchScreen
import com.example.musicplayer.ui.settings.SettingsScreen
import com.example.musicplayer.ui.songs.SongsScreen
import com.example.musicplayer.ui.theme.MusicPlayerTheme

/**
 * Actividad principal: única Activity de la app, que monta la interfaz Compose
 * con navegación entre canciones, búsqueda y playlists.
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
            val app = LocalContext.current.applicationContext as MusicPlayerApplication
            val themeMode by app.settings.themeMode.collectAsStateWithLifecycle()
            val darkTheme = when (themeMode) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }

            MusicPlayerTheme(darkTheme = darkTheme) {
                val navController = rememberNavController()
                NavHost(navController = navController, startDestination = "songs") {
                    composable("songs") {
                        SongsScreen(
                            onNavigateToSearch = { navController.navigate("search") },
                            onNavigateToPlaylists = { navController.navigate("playlists") },
                            onNavigateToSettings = { navController.navigate("settings") },
                        )
                    }
                    composable("search") {
                        SearchScreen(onBack = { navController.popBackStack() })
                    }
                    composable("playlists") {
                        PlaylistsScreen(
                            onBack = { navController.popBackStack() },
                            onOpenPlaylist = { id ->
                                navController.navigate("playlist/$id")
                            },
                        )
                    }
                    composable("playlist/{playlistId}") { backStackEntry ->
                        val playlistId =
                            backStackEntry.arguments?.getString("playlistId")?.toLongOrNull() ?: 0L
                        PlaylistDetailScreen(
                            playlistId = playlistId,
                            onBack = { navController.popBackStack() },
                        )
                    }
                    composable("settings") {
                        SettingsScreen(onBack = { navController.popBackStack() })
                    }
                }
            }
        }
    }
}