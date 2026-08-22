package com.example.musicplayer.ui.widget

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import com.example.musicplayer.MusicPlayerApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class PlayerWidgetReceiver : GlanceAppWidgetReceiver() {

    override val glanceAppWidget: GlanceAppWidget = PlayerWidget

    override fun onReceive(context: Context, intent: Intent) {
        Log.d("WidgetReceiver", "onReceive action=${intent.action}")
        when (intent.action) {
            ACTION_TOGGLE_PLAY_PAUSE, ACTION_NEXT, ACTION_PREVIOUS, ACTION_UPDATE_WIDGET -> {
                val app = context.applicationContext as MusicPlayerApplication
                val controller = app.playbackController

                when (intent.action) {
                    ACTION_TOGGLE_PLAY_PAUSE -> {
                        if (controller.state.value.currentSong != null) {
                            controller.togglePlayPause()
                        }
                    }
                    ACTION_NEXT -> controller.skipToNext()
                    ACTION_PREVIOUS -> controller.skipToPrevious()
                }

                val pendingResult = goAsync()
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        val manager = GlanceAppWidgetManager(context)
                        val ids = manager.getGlanceIds(PlayerWidget::class.java)
                        Log.d("WidgetReceiver", "Updating ${ids.size} widget(s)")
                        ids.forEach { id -> PlayerWidget.update(context, id) }
                    } catch (e: Exception) {
                        Log.e("WidgetReceiver", "Error updating widget", e)
                    } finally {
                        pendingResult.finish()
                    }
                }
                return
            }
        }
        super.onReceive(context, intent)
    }

    companion object {
        const val ACTION_TOGGLE_PLAY_PAUSE = "com.example.musicplayer.ACTION_TOGGLE_PLAY_PAUSE"
        const val ACTION_NEXT = "com.example.musicplayer.ACTION_NEXT"
        const val ACTION_PREVIOUS = "com.example.musicplayer.ACTION_PREVIOUS"
        const val ACTION_UPDATE_WIDGET = "com.example.musicplayer.ACTION_UPDATE_WIDGET"
    }
}
