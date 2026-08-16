package com.pulsevisualizer

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews

class LyricsWidgetProvider : AppWidgetProvider() {

    companion object {

        fun updateAll(context: Context) {

            val manager =
                AppWidgetManager.getInstance(context)

            val component =
                ComponentName(
                    context,
                    LyricsWidgetProvider::class.java
                )

            val widgetIds =
                manager.getAppWidgetIds(component)

            if (widgetIds.isEmpty()) {
                return
            }

            val media =
                MediaRepository.media.value

            val title =
                media.title
                    .takeIf { it.isNotBlank() }
                    ?: "Nothing playing"

            val artist =
                media.artist
                    .takeIf { it.isNotBlank() }
                    ?: ""

            for (widgetId in widgetIds) {

                val views =
                    RemoteViews(
                        context.packageName,
                        R.layout.widget_lyrics
                    )

                views.setTextViewText(
                    R.id.lyrics_title,
                    title
                )

                views.setTextViewText(
                    R.id.lyrics_artist,
                    artist
                )

                views.setTextViewText(
                    R.id.lyrics_text,
                    "Loading lyrics..."
                )

                val launchIntent =
                    Intent(
                        context,
                        MainActivity::class.java
                    ).apply {
                        flags =
                            Intent.FLAG_ACTIVITY_NEW_TASK or
                            Intent.FLAG_ACTIVITY_CLEAR_TOP
                    }

                val pendingIntent =
                    PendingIntent.getActivity(
                        context,
                        widgetId,
                        launchIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT or
                        PendingIntent.FLAG_IMMUTABLE
                    )

                views.setOnClickPendingIntent(
                    R.id.lyrics_root,
                    pendingIntent
                )

                manager.updateAppWidget(
                    widgetId,
                    views
                )
            }
        }
    }

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {

        updateAll(context)
    }

    override fun onEnabled(
        context: Context
    ) {

        updateAll(context)
    }

    override fun onDeleted(
        context: Context,
        appWidgetIds: IntArray
    ) {

        // Nothing to clean up.
    }

    override fun onDisabled(
        context: Context
    ) {

        // Nothing to clean up.
    }
}
