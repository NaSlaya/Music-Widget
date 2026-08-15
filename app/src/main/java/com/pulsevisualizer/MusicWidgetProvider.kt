package com.pulsevisualizer

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews

class MusicWidgetProvider :
    AppWidgetProvider() {

    companion object {

        private const val ACTION_PREVIOUS =
            "com.pulsevisualizer.WIDGET_PREVIOUS"

        private const val ACTION_PLAY_PAUSE =
            "com.pulsevisualizer.WIDGET_PLAY_PAUSE"

        private const val ACTION_NEXT =
            "com.pulsevisualizer.WIDGET_NEXT"

        fun updateAll(
            context: Context
        ) {

            val manager =
                AppWidgetManager
                    .getInstance(context)

            val component =
                ComponentName(
                    context,
                    MusicWidgetProvider::class.java
                )

            val ids =
                manager.getAppWidgetIds(
                    component
                )

            for (id in ids) {

                updateWidget(
                    context,
                    manager,
                    id
                )
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
                media.artist.ifBlank {
                    "Unknown artist"
                }
            )

            views.setTextViewText(
                R.id.widget_status,
                if (media.playing) {
                    "NOW PLAYING"
                } else {
                    "PAUSED"
                }
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
                launchAppPendingIntent(
                    context
                )
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
                ).setAction(action)

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

        MediaRepository.start(context)

        for (widgetId in appWidgetIds) {

            updateWidget(
                context,
                appWidgetManager,
                widgetId
            )
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

        MediaRepository.start(context)

        when (intent.action) {

            ACTION_PREVIOUS ->
                MediaRepository.previous()

            ACTION_PLAY_PAUSE ->
                MediaRepository
                    .togglePlayPause()

            ACTION_NEXT ->
                MediaRepository.next()

            AppWidgetManager
                .ACTION_APPWIDGET_UPDATE ->
                updateAll(context)
        }
    }
    }
