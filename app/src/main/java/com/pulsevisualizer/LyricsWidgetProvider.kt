package com.pulsevisualizer

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews

class LyricsWidgetProvider :
    AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        manager: AppWidgetManager,
        ids: IntArray
    ) {

        MediaRepository.start(context)

        for (id in ids) {
            updateWidget(
                context,
                manager,
                id
            )
        }
    }

    companion object {

        fun updateAll(
            context: Context
        ) {

            val manager =
                AppWidgetManager
                    .getInstance(context)

            val component =
                ComponentName(
                    context,
                    LyricsWidgetProvider::class.java
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
            id: Int
        ) {

            val media =
                MediaRepository.media.value

            val views =
                RemoteViews(
                    context.packageName,
                    R.layout.widget_lyrics
                )

            views.setTextViewText(
                R.id.lyrics_title,
                media.title
            )

            views.setTextViewText(
                R.id.lyrics_artist,
                if (
                    media.artist.isBlank()
                ) {
                    "Tap to open lyrics video"
                } else {
                    media.artist
                }
            )

            media.artwork?.let {

                views.setImageViewBitmap(
                    R.id.lyrics_artwork,
                    it
                )
            }

            val intent =
                Intent(
                    context,
                    LyricsVideoActivity::class.java
                )

            val pending =
                PendingIntent.getActivity(
                    context,
                    id,
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or
                        PendingIntent.FLAG_IMMUTABLE
                )

            views.setOnClickPendingIntent(
                R.id.lyrics_widget_root,
                pending
            )

            manager.updateAppWidget(
                id,
                views
            )
        }
    }
    }
