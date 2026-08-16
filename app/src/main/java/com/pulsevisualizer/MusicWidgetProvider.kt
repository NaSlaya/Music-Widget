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
                AppWidgetManager.getInstance(
                    context
                )

            val component =
                ComponentName(
                    context,
                    MusicWidgetProvider::class.java
                )

            val ids =
                manager.getAppWidgetIds(
                    component
                )

            for (
                id in ids
            ) {

                updateWidget(
                    context,
                    manager,
                    id
                )
            }
        }

        private fun id(
            context: Context,
            name: String
        ): Int {

            return context.resources
                .getIdentifier(
                    name,
                    "id",
                    context.packageName
                )
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

            val titleId =
                id(
                    context,
                    "widget_title"
                )

            val artistId =
                id(
                    context,
                    "widget_artist"
                )

            val statusId =
                id(
                    context,
                    "widget_status"
                )

            val playId =
                id(
                    context,
                    "widget_play"
                )

            val artworkId =
                id(
                    context,
                    "widget_artwork"
                )

            val previousId =
                id(
                    context,
                    "widget_previous"
                )

            val nextId =
                id(
                    context,
                    "widget_next"
                )

            if (
                titleId != 0
            ) {

                views.setTextViewText(
                    titleId,
                    media.title.ifBlank {
                        "Nothing playing"
                    }
                )
            }

            if (
                artistId != 0
            ) {

                views.setTextViewText(
                    artistId,
                    media.artist.ifBlank {
                        "Unknown artist"
                    }
                )
            }

            if (
                statusId != 0
            ) {

                views.setTextViewText(
                    statusId,
                    if (
                        media.playing
                    ) {
                        "NOW PLAYING"
                    } else {
                        "PAUSED"
                    }
                )
            }

            if (
                playId != 0
            ) {

                views.setTextViewText(
                    playId,
                    if (
                        media.playing
                    ) {
                        "Ⅱ"
                    } else {
                        "▶"
                    }
                )
            }

            if (
                artworkId != 0
            ) {

                if (
                    media.artwork != null
                ) {

                    views.setImageViewBitmap(
                        artworkId,
                        media.artwork
                    )

                } else {

                    views.setImageViewResource(
                        artworkId,
                        android.R.drawable.ic_media_play
                    )
                }
            }

            if (
                previousId != 0
            ) {

                views.setOnClickPendingIntent(
                    previousId,
                    actionPendingIntent(
                        context,
                        ACTION_PREVIOUS
                    )
                )
            }

            if (
                playId != 0
            ) {

                views.setOnClickPendingIntent(
                    playId,
                    actionPendingIntent(
                        context,
                        ACTION_PLAY_PAUSE
                    )
                )
            }

            if (
                nextId != 0
            ) {

                views.setOnClickPendingIntent(
                    nextId,
                    actionPendingIntent(
                        context,
                        ACTION_NEXT
                    )
                )
            }

            if (
                artworkId != 0
            ) {

                views.setOnClickPendingIntent(
                    artworkId,
                    launchAppPendingIntent(
                        context
                    )
                )
            }

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
                    this.action =
                        action
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
        manager: AppWidgetManager,
        ids: IntArray
    ) {

        for (
            widgetId in ids
        ) {

            updateAll(
                context
            )

            break
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

        when (
            intent.action
        ) {

            ACTION_PREVIOUS -> {

                MediaRepository.start(
                    context
                )

                MediaRepository.previous()

                updateAll(
                    context
                )
            }

            ACTION_PLAY_PAUSE -> {

                MediaRepository.start(
                    context
                )

                MediaRepository.togglePlayPause()

                updateAll(
                    context
                )
            }

            ACTION_NEXT -> {

                MediaRepository.start(
                    context
                )

                MediaRepository.next()

                updateAll(
                    context
                )
            }

            AppWidgetManager
                .ACTION_APPWIDGET_UPDATE -> {

                updateAll(
                    context
                )
            }
        }
    }
}
