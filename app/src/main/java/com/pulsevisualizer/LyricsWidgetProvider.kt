package com.pulsevisualizer

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.RemoteViews
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URLEncoder
import java.net.URL
import java.util.concurrent.Executors

class LyricsWidgetProvider : AppWidgetProvider() {

    companion object {

        private val executor = Executors.newSingleThreadExecutor()

        private var cachedKey: String = ""
        private var cachedLyrics: String = ""

        fun updateAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context)

            val component = ComponentName(
                context,
                LyricsWidgetProvider::class.java
            )

            val ids = manager.getAppWidgetIds(component)

            if (ids.isEmpty()) {
                return
            }

            val media = MediaRepository.media.value

            val title = media.title.trim()
            val artist = media.artist.trim()

            if (title.isBlank() || title.equals("Nothing playing", true)) {
                updateViews(
                    context,
                    manager,
                    ids,
                    "Nothing playing",
                    "",
                    "Play a song to see lyrics"
                )
                return
            }

            val key = "$title|$artist"

            if (key == cachedKey && cachedLyrics.isNotBlank()) {
                updateViews(
                    context,
                    manager,
                    ids,
                    title,
                    artist,
                    cachedLyrics
                )
                return
            }

            updateViews(
                context,
                manager,
                ids,
                title,
                artist,
                "Finding lyrics..."
            )

            executor.execute {
                val lyrics = fetchLyrics(title, artist)

                cachedKey = key
                cachedLyrics = lyrics

                updateViews(
                    context,
                    manager,
                    ids,
                    title,
                    artist,
                    lyrics
                )
            }
        }

        private fun fetchLyrics(
            title: String,
            artist: String
        ): String {

            return try {

                val encodedTitle =
                    URLEncoder.encode(title, "UTF-8")

                val encodedArtist =
                    URLEncoder.encode(artist, "UTF-8")

                val url = URL(
                    "https://lrclib.net/api/get" +
                        "?track_name=$encodedTitle" +
                        "&artist_name=$encodedArtist"
                )

                val connection =
                    url.openConnection() as HttpURLConnection

                connection.requestMethod = "GET"
                connection.connectTimeout = 8000
                connection.readTimeout = 8000
                connection.setRequestProperty(
                    "User-Agent",
                    "PulseVisualizer/1.0"
                )

                if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                    connection.disconnect()
                    return "Lyrics not found"
                }

                val response =
                    connection.inputStream
                        .bufferedReader()
                        .use { it.readText() }

                connection.disconnect()

                val json = JSONObject(response)

                val synced =
                    json.optString("syncedLyrics")

                val plain =
                    json.optString("plainLyrics")

                val result =
                    if (synced.isNotBlank()) {
                        cleanSyncedLyrics(synced)
                    } else {
                        plain
                    }

                result
                    .trim()
                    .ifBlank {
                        "Lyrics not available"
                    }

            } catch (_: Exception) {
                "Unable to load lyrics"
            }
        }

        private fun cleanSyncedLyrics(
            lyrics: String
        ): String {

            return lyrics
                .lines()
                .map { line ->
                    line.replace(
                        Regex("""^\[\d{1,2}:\d{2}(?:\.\d{1,3})?]"""),
                        ""
                    ).trim()
                }
                .filter { it.isNotBlank() }
                .joinToString("\n")
        }

        private fun updateViews(
            context: Context,
            manager: AppWidgetManager,
            ids: IntArray,
            title: String,
            artist: String,
            lyrics: String
        ) {

            for (id in ids) {

                val views = RemoteViews(
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
                    lyrics
                )

                views.setOnClickPendingIntent(
                    R.id.lyrics_root,
                    launchAppPendingIntent(context)
                )

                manager.updateAppWidget(
                    id,
                    views
                )
            }
        }

        private fun launchAppPendingIntent(
            context: Context
        ): PendingIntent {

            val intent = Intent(
                context,
                MainActivity::class.java
            )

            return PendingIntent.getActivity(
                context,
                9100,
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
        updateAll(context)
    }
}
