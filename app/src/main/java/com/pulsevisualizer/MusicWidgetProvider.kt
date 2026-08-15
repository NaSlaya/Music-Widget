package com.pulsevisualizer

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews

class MusicWidgetProvider : AppWidgetProvider() {

    companion object {

        private const val ACTION_PREVIOUS =
            "com.pulsevisualizer.WIDGET_PREVIOUS"

        private const val ACTION_PLAY_PAUSE =
            "com.pulsevisualizer.WIDGET_PLAY_PAUSE"

        private const val ACTION_NEXT =
            "com.pulsevisualizer.WIDGET_NEXT"

        fun updateAll(context: Context) {

            val manager =
                AppWidgetManager.getInstance(context)

            val component =
                ComponentName(
                    context,
                    MusicWidgetProvider::class.java
                )

            val ids =
                manager.getAppWidgetIds(component)

            for (id in ids) {
                try {
                    updateWidget(
                        context,
                        manager,
                        id
                    )
                } catch (_: Exception) {
                    // Never allow a widget update failure
                    // to crash the application.
                }
            }
        }

        private fun updateWidget(
            context: Context,
            manager: AppWidgetManager,
            widgetId: Int
        ) {

            val media =
                MediaRepository.media.value

            val views =
                RemoteViews(
                    context.packageName,
                    R.layout.music_widget
                )

            views.setTextViewText(
                R.id.widget_title,
                media.title
            )

            views.setTextViewText(
                R.id.widget_artist,
                media.artist
            )

            views.setTextViewText(
                R.id.widget_play,
                if (media.playing) {
                    "Ⅱ"
                } else {
                    "▶"
                }
            )

            if (media.artwork != null) {

                views.setImageViewBitmap(
                    R.id.widget_artwork,
                    media.artwork
                )

            } else {

                views.setImageViewResource(
                    R.id.widget_artwork,
                    android.R.drawable.ic_media_play
                )
            }

            views.setOnClickPendingIntent(
                R.id.widget_previous,
                actionPendingIntent(
                    context,
                    ACTION_PREVIOUS
                )
            )

            views.setOnClickPendingIntent(
                R.id.widget_play,
                actionPendingIntent(
                    context,
                    ACTION_PLAY_PAUSE
                )
            )

            views.setOnClickPendingIntent(
                R.id.widget_next,
                actionPendingIntent(
                    context,
                    ACTION_NEXT
                )
            )

            views.setOnClickPendingIntent(
                R.id.widget_artwork,
                launchAppPendingIntent(context)
            )

            manager.updateAppWidget(
                widgetId,
                views
            )
        }

        private fun actionPendingIntent(
            context: Context,
            action: String
        ): PendingIntent {

            val intent =
                Intent(
                    context,
                    MusicWidgetProvider::class.java
                ).apply {
                    this.action = action
                }

            return PendingIntent.getBroadcast(
                context,
                action.hashCode(),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or
                    PendingIntent.FLAG_IMMUTABLE
            )
        }

        private fun launchAppPendingIntent(
            context: Context
        ): PendingIntent {

            val intent =
                Intent(
                    context,
                    MainActivity::class.java
                )

            return PendingIntent.getActivity(
                context,
                9001,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or
                    PendingIntent.FLAG_IMMUTABLE
            )
        }
    }

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {

        /*
         * IMPORTANT:
         *
         * Do NOT start MediaRepository here.
         *
         * Samsung's launcher calls onUpdate while adding the
         * widget. Starting the MediaSession/notification-listener
         * machinery at this exact point can cause the launcher to
         * reject the widget with "Couldn't add widget".
         *
         * The main application starts MediaRepository separately.
         */

        for (widgetId in appWidgetIds) {

            try {

                updateWidget(
                    context,
                    appWidgetManager,
                    widgetId
                )

            } catch (_: Exception) {
                // Prevent widget creation from crashing.
            }
        }
    }

    override fun onReceive(
        context: Context,
        intent: Intent
    ) {

        super.onReceive(
            context,
            intent
        )

        when (intent.action) {

            ACTION_PREVIOUS -> {

                MediaRepository.start(context)
                MediaRepository.previous()

                updateAll(context)
            }

            ACTION_PLAY_PAUSE -> {

                MediaRepository.start(context)
                MediaRepository.togglePlayPause()

                updateAll(context)
            }

            ACTION_NEXT -> {

                MediaRepository.start(context)
                MediaRepository.next()

                updateAll(context)
            }

            AppWidgetManager.ACTION_APPWIDGET_UPDATE -> {

                updateAll(context)
            }
        }
    }
}
