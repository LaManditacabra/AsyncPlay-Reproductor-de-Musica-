package com.example.musicplayer

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.musicplayer.ui.favorites.FavoritesScreen
import com.example.musicplayer.ui.playlists.PlaylistDetailScreen
import com.example.musicplayer.ui.playlists.PlaylistsScreen
import com.example.musicplayer.ui.profile.ProfileScreen
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
            val darkTheme by app.settings.darkTheme.collectAsStateWithLifecycle()

            MusicPlayerTheme(darkTheme = darkTheme) {
                val navController = rememberNavController()
                Scaffold(
                    bottomBar = { BottomNavBar(navController) },
                    contentWindowInsets = WindowInsets(0, 0, 0, 0),
                ) { innerPadding ->
                    NavHost(
                        navController = navController,
                        startDestination = "songs",
                        modifier = Modifier.padding(innerPadding),
                    ) {
                        composable("songs") {
                            SongsScreen(
                                onNavigateToSearch = { navController.navigate("search") },
                                onNavigateToSettings = { navController.navigate("settings") },
                            )
                        }
                        composable("library") {
                            PlaylistsScreen(
                                onBack = null,
                                onOpenPlaylist = { id ->
                                    navController.navigate("playlist/$id")
                                },
                                onOpenFavorites = {
                                    navController.navigate("favorites")
                                },
                            )
                        }
                        composable("profile") {
                            ProfileScreen(
                                onNavigateToSettings = { navController.navigate("settings") },
                            )
                        }
                        composable("search") {
                            SearchScreen(onBack = { navController.popBackStack() })
                        }
                        composable("favorites") {
                            FavoritesScreen(onBack = { navController.popBackStack() })
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
}

/** Barra de navegación inferior estilo Spotify: píldora activa con etiqueta. */
@Composable
private fun BottomNavBar(navController: NavHostController) {
    val items = listOf(
        NavItem("songs", R.string.nav_songs, Icons.Filled.MusicNote),
        NavItem("library", R.string.nav_library, Icons.Filled.LibraryMusic),
        NavItem("profile", R.string.nav_profile, Icons.Filled.Person),
    )
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        tonalElevation = 8.dp,
        shadowElevation = 12.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            items.forEach { item ->
                val selected = currentRoute == item.route
                val label = stringResource(item.labelRes)

                Column(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .clickable {
                            navController.navigate(item.route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                        .padding(horizontal = 18.dp, vertical = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    // Píldora circular tras el icono activo.
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(
                                if (selected) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    Color.Transparent
                                },
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = item.icon,
                            contentDescription = null,
                            tint = if (selected) {
                                MaterialTheme.colorScheme.onPrimary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            modifier = Modifier.size(22.dp),
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (selected) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                    )
                }
            }
        }
    }
}

/** Descripción de un elemento de la barra de navegación inferior. */
private data class NavItem(
    val route: String,
    val labelRes: Int,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
)