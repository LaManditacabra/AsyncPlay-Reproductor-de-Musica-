package com.example.musicplayer.ui.widget

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.action.actionSendBroadcast
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.example.musicplayer.MainActivity
import com.example.musicplayer.MusicPlayerApplication
import com.example.musicplayer.R
import java.io.File

object PlayerWidget : GlanceAppWidget() {

    override val stateDefinition = PreferencesGlanceStateDefinition

    private val KEY_TITLE = stringPreferencesKey("w_title")
    private val KEY_ARTIST = stringPreferencesKey("w_artist")
    private val KEY_IS_PLAYING = booleanPreferencesKey("w_playing")
    private val KEY_HAS_SONG = booleanPreferencesKey("w_hassong")
    private val KEY_THUMBNAIL = stringPreferencesKey("w_thumb")
    private val KEY_COUNTER = longPreferencesKey("w_counter")

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val app = context.applicationContext as MusicPlayerApplication
        val state = app.playbackController.state.value
        val song = state.currentSong

        val thumbnailPath = song?.thumbnailUrl?.replace("file:", "")?.let { path ->
            try {
                val file = File(path)
                if (file.exists()) path else null
            } catch (_: Exception) { null }
        }

        val title = song?.title ?: ""
        val artist = song?.artist ?: ""
        val isPlaying = state.isPlaying
        val hasSong = song != null
        val thumbPath = thumbnailPath ?: ""

        updateAppWidgetState(context, PreferencesGlanceStateDefinition, id) { prefs ->
            prefs.toMutablePreferences().apply {
                this[KEY_TITLE] = title
                this[KEY_ARTIST] = artist
                this[KEY_IS_PLAYING] = isPlaying
                this[KEY_HAS_SONG] = hasSong
                this[KEY_THUMBNAIL] = thumbPath
                this[KEY_COUNTER] = (this[KEY_COUNTER] ?: 0L) + 1L
            }
        }

        val bitmap = thumbPath.ifBlank { null }?.let { path ->
            try {
                val file = File(path)
                if (file.exists()) BitmapFactory.decodeFile(file.absolutePath) else null
            } catch (_: Exception) { null }
        }

        provideContent {
            WidgetContent(context, title, artist, isPlaying, hasSong, bitmap)
        }
    }

    @Composable
    private fun WidgetContent(
        context: Context,
        title: String,
        artist: String,
        isPlaying: Boolean,
        hasSong: Boolean,
        bitmap: android.graphics.Bitmap?,
    ) {
        val receiver = ComponentName(context, PlayerWidgetReceiver::class.java)

        Row(
            modifier = GlanceModifier
                .fillMaxWidth()
                .height(80.dp)
                .background(ColorProvider(R.color.widget_bg))
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (bitmap != null) {
                Image(
                    provider = ImageProvider(bitmap),
                    contentDescription = null,
                    modifier = GlanceModifier.size(56.dp),
                )
            } else {
                Box(
                    modifier = GlanceModifier
                        .size(56.dp)
                        .background(ImageProvider(R.drawable.ic_music_note)),
                    contentAlignment = Alignment.Center,
                ) {}
            }

            Spacer(modifier = GlanceModifier.width(12.dp))

            Column(
                modifier = GlanceModifier
                    .defaultWeight()
                    .clickable(
                        actionStartActivity(Intent(context, MainActivity::class.java))
                    ),
            ) {
                Text(
                    text = title.ifBlank { "AsyncPlay" },
                    style = TextStyle(
                        color = ColorProvider(android.R.color.white),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                    ),
                    maxLines = 1,
                )
                Spacer(modifier = GlanceModifier.height(2.dp))
                Text(
                    text = artist.ifBlank { " " },
                    style = TextStyle(
                        color = ColorProvider(android.R.color.darker_gray),
                        fontSize = 12.sp,
                    ),
                    maxLines = 1,
                )
            }

            Spacer(modifier = GlanceModifier.width(4.dp))

            WidgetButton(
                drawable = R.drawable.ic_widget_previous,
                desc = "Anterior",
                enabled = hasSong,
                intent = Intent(PlayerWidgetReceiver.ACTION_PREVIOUS).setComponent(receiver),
            )
            Spacer(modifier = GlanceModifier.width(4.dp))
            WidgetButton(
                drawable = if (isPlaying) R.drawable.ic_widget_pause else R.drawable.ic_widget_play,
                desc = if (isPlaying) "Pausar" else "Reproducir",
                enabled = hasSong,
                intent = Intent(PlayerWidgetReceiver.ACTION_TOGGLE_PLAY_PAUSE).setComponent(receiver),
            )
            Spacer(modifier = GlanceModifier.width(4.dp))
            WidgetButton(
                drawable = R.drawable.ic_widget_next,
                desc = "Siguiente",
                enabled = hasSong,
                intent = Intent(PlayerWidgetReceiver.ACTION_NEXT).setComponent(receiver),
            )
        }
    }

    @Composable
    private fun WidgetButton(
        drawable: Int,
        desc: String,
        enabled: Boolean,
        intent: Intent,
    ) {
        if (!enabled) return
        Image(
            provider = ImageProvider(drawable),
            contentDescription = desc,
            modifier = GlanceModifier
                .size(40.dp)
                .clickable(actionSendBroadcast(intent)),
        )
    }
}
