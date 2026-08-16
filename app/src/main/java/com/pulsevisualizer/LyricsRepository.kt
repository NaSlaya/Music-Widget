package com.pulsevisualizer

import android.content.Context
import org.json.JSONArray
import java.io.File
import java.net.HttpURLConnection
import java.net.URLEncoder
import java.net.URL
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

object LyricsRepository {

    private const val CACHE_VERSION = 2

    private const val CONNECT_TIMEOUT = 8000
    private const val READ_TIMEOUT = 10000

    private val memoryCache =
        ConcurrentHashMap<String, LyricsDocument>()

    fun getLyrics(
        context: Context,
        title: String,
        artist: String
    ): LyricsDocument {

        val cleanTitle = cleanTitle(title)
        val cleanArtist = cleanArtist(artist)

        if (cleanTitle.isBlank()) {
            return LyricsDocument(
                emptyList(),
                source = "none",
                confidence = 0f
            )
        }

        val key = makeKey(
            cleanTitle,
            cleanArtist
        )

        memoryCache[key]?.let {
            return it
        }

        readDiskCache(
            context,
            key
        )?.let {
            memoryCache[key] = it
            return it
        }

        /*
         * First attempt:
         * exact title + artist.
         */
        val withArtist = search(
            cleanTitle,
            cleanArtist
        )

        if (withArtist.lines.isNotEmpty()) {
            save(
                context,
                key,
                withArtist
            )

            memoryCache[key] = withArtist

            return withArtist
        }

        /*
         * Second attempt:
         * title only.
         */
        val titleOnly = search(
            cleanTitle,
            null
        )

        if (titleOnly.lines.isNotEmpty()) {
            save(
                context,
                key,
                titleOnly
            )

            memoryCache[key] = titleOnly

            return titleOnly
        }

        return LyricsDocument(
            emptyList(),
            source = "none",
            confidence = 0f
        )
    }

    private fun search(
        title: String,
        artist: String?
    ): LyricsDocument {

        return try {

            val encodedTitle =
                URLEncoder.encode(
                    title,
                    "UTF-8"
                )

            val urlString =
                if (!artist.isNullOrBlank()) {

                    val encodedArtist =
                        URLEncoder.encode(
                            artist,
                            "UTF-8"
                        )

                    "https://lrclib.net/api/search" +
                        "?track_name=$encodedTitle" +
                        "&artist_name=$encodedArtist"

                } else {

                    "https://lrclib.net/api/search" +
                        "?q=$encodedTitle"
                }

            val connection =
                URL(urlString)
                    .openConnection()
                    as HttpURLConnection

            connection.requestMethod = "GET"

            connection.connectTimeout =
                CONNECT_TIMEOUT

            connection.readTimeout =
                READ_TIMEOUT

            connection.setRequestProperty(
                "Accept",
                "application/json"
            )

            connection.setRequestProperty(
                "User-Agent",
                "PulseVisualizer/2.0"
            )

            if (
                connection.responseCode !=
                HttpURLConnection.HTTP_OK
            ) {

                connection.disconnect()

                return LyricsDocument(
                    emptyList(),
                    source = "none",
                    confidence = 0f
                )
            }

            val response =
                connection
                    .inputStream
                    .bufferedReader()
                    .use {
                        it.readText()
                    }

            connection.disconnect()

            chooseBest(
                JSONArray(response),
                title,
                artist
            )

        } catch (
            _: Exception
        ) {

            LyricsDocument(
                emptyList(),
                source = "none",
                confidence = 0f
            )
        }
    }

    private fun chooseBest(
        results: JSONArray,
        title: String,
        artist: String?
    ): LyricsDocument {

        var best =
            LyricsDocument(
                emptyList(),
                source = "none",
                confidence = 0f
            )

        var bestScore =
            Float.MIN_VALUE

        for (
            i in 0 until results.length()
        ) {

            val item =
                results.optJSONObject(i)
                    ?: continue

            val resultTitle =
                item.optString(
                    "trackName"
                )

            val resultArtist =
                item.optString(
                    "artistName"
                )

            val syncedLyrics =
                item.optString(
                    "syncedLyrics"
                )

            if (syncedLyrics.isBlank()) {
                continue
            }

            val lines =
                parseLrc(
                    syncedLyrics
                )

            if (lines.isEmpty()) {
                continue
            }

            var score = 0f

            score +=
                similarity(
                    title,
                    resultTitle
                ) * 60f

            if (!artist.isNullOrBlank()) {
                score +=
                    similarity(
                        artist,
                        resultArtist
                    ) * 35f
            }

            /*
             * Slight preference for documents
             * with a healthy number of lyric lines.
             */
            score +=
                min(
                    5f,
                    lines.size / 20f
                )

            if (score > bestScore) {

                bestScore = score

                best =
                    LyricsDocument(
                        lines = lines,
                        source = "LRCLIB",
                        confidence =
                            (score / 100f)
                                .coerceIn(
                                    0f,
                                    1f
                                )
                    )
            }
        }

        return best
    }

    private fun parseLrc(
        raw: String
    ): List<LyricLine> {

        val timestampRegex =
            Regex(
                """\[(\d{1,3}):(\d{2})(?:[.:](\d{1,3}))?]"""
            )

        val rawEntries =
            mutableListOf<Pair<Long, String>>()

        for (
            sourceLine in raw.lines()
        ) {

            val matches =
                timestampRegex
                    .findAll(sourceLine)
                    .toList()

            if (matches.isEmpty()) {
                continue
            }

            val text =
                sourceLine
                    .replace(
                        timestampRegex,
                        ""
                    )
                    .trim()

            if (
                text.isBlank() ||
                isMetadata(text)
            ) {
                continue
            }

            for (match in matches) {

                val minutes =
                    match
                        .groupValues[1]
                        .toLongOrNull()
                        ?: continue

                val seconds =
                    match
                        .groupValues[2]
                        .toLongOrNull()
                        ?: continue

                val fraction =
                    match.groupValues[3]

                val fractionMs =
                    when (fraction.length) {

                        1 ->
                            fraction
                                .toLongOrNull()
                                ?.times(100)
                                ?: 0L

                        2 ->
                            fraction
                                .toLongOrNull()
                                ?.times(10)
                                ?: 0L

                        3 ->
                            fraction
                                .toLongOrNull()
                                ?: 0L

                        else ->
                            0L
                    }

                val time =
                    minutes * 60_000L +
                        seconds * 1_000L +
                        fractionMs

                rawEntries.add(
                    time to
                        cleanLyricText(text)
                )
            }
        }

        if (rawEntries.isEmpty()) {
            return emptyList()
        }

        val sorted =
            rawEntries.sortedBy {
                it.first
            }

        val result =
            mutableListOf<LyricLine>()

        for (index in sorted.indices) {

            val start =
                sorted[index].first

            val next =
                sorted
                    .getOrNull(index + 1)
                    ?.first

            val end =
                next?.let {
                    max(
                        start + 250L,
                        it
                    )
                }
                    ?: (
                        start + 5000L
                    )

            val text =
                sorted[index].second

            if (text.isBlank()) {
                continue
            }

            val previous =
                result.lastOrNull()

            /*
             * Prevent duplicate lines caused by
             * repeated LRC timestamps.
             */
            if (
                previous != null &&
                previous.text.equals(
                    text,
                    ignoreCase = true
                ) &&
                abs(
                    previous.timeMs - start
                ) < 150L
            ) {
                continue
            }

            val words =
                LyricsTiming.estimateWords(
                    text,
                    start,
                    end
                )

            result.add(
                LyricLine(
                    timeMs = start,
                    endMs = end,
                    text = text,
                    words = words
                )
            )
        }

        return result.mapIndexed {
                index,
                line ->

            val actualEnd =
                result
                    .getOrNull(index + 1)
                    ?.timeMs
                    ?: line.endMs

            val fixedEnd =
                max(
                    line.timeMs + 250L,
                    actualEnd
                )

            line.copy(
                endMs = fixedEnd,
                words =
                    LyricsTiming.estimateWords(
                        line.text,
                        line.timeMs,
                        fixedEnd
                    )
            )
        }
    }
        private fun similarity(
        a: String,
        b: String
    ): Float {

        val left =
            normalise(a)

        val right =
            normalise(b)

        if (
            left.isBlank() ||
            right.isBlank()
        ) {
            return 0f
        }

        if (left == right) {
            return 1f
        }

        if (
            left.contains(right) ||
            right.contains(left)
        ) {
            return 0.90f
        }

        val aWords =
            left
                .split(" ")
                .filter {
                    it.isNotBlank()
                }
                .toSet()

        val bWords =
            right
                .split(" ")
                .filter {
                    it.isNotBlank()
                }
                .toSet()

        val intersection =
            aWords.intersect(bWords).size

        val union =
            aWords.union(bWords).size

        if (union == 0) {
            return 0f
        }

        return intersection.toFloat() /
            union.toFloat()
    }

    private fun cleanTitle(
        value: String
    ): String {

        var result =
            value.trim()

        val removable =
            listOf(
                "(Official Lyric Video)",
                "[Official Lyric Video]",
                "(Official Lyrics)",
                "[Official Lyrics]",
                "(Lyric Video)",
                "[Lyric Video]",
                "(Lyrics)",
                "[Lyrics]",
                "(Official Audio)",
                "[Official Audio]",
                "(Official Video)",
                "[Official Video]",
                "(Official Music Video)",
                "[Official Music Video]",
                "(Audio)",
                "[Audio]",
                "(Visualizer)",
                "[Visualizer]",
                "(4K)",
                "[4K]",
                "(HD)",
                "[HD]",
                "(Remastered)",
                "[Remastered]"
            )

        for (item in removable) {

            result =
                result.replace(
                    item,
                    "",
                    ignoreCase = true
                )
        }

        /*
         * Remove common YouTube-style suffixes.
         */
        result =
            result.replace(
                Regex(
                    """\s*[-|]\s*(official|lyrics?|lyric video|audio|visualizer).*$"""
                ),
                "",
                ignoreCase = true
            )

        return result
            .replace(
                Regex("\\s{2,}"),
                " "
            )
            .trim()
    }

    private fun cleanArtist(
        value: String
    ): String {

        return value
            .replace(
                Regex(
                    """\s*(feat\.?|ft\.?)\s+.*$"""
                ),
                "",
                ignoreCase = true
            )
            .trim()
    }

    private fun cleanLyricText(
        value: String
    ): String {

        return value
            .replace(
                Regex(
                    """<\d{1,3}:\d{2}(?:\.\d{1,3})?>"""
                ),
                ""
            )
            .replace(
                Regex(
                    """\{\d{1,3}:\d{2}(?:\.\d{1,3})?\}"""
                ),
                ""
            )
            .replace(
                Regex("\\s{2,}"),
                " "
            )
            .trim()
    }

    private fun isMetadata(
        text: String
    ): Boolean {

        val lower =
            text.lowercase()

        return lower.startsWith("ar:") ||
            lower.startsWith("al:") ||
            lower.startsWith("by:") ||
            lower.startsWith("ti:") ||
            lower.startsWith("re:")
    }

    private fun normalise(
        value: String
    ): String {

        return value
            .lowercase()
            .replace(
                "&",
                "and"
            )
            .replace(
                Regex(
                    """[^a-z0-9 ]"""
                ),
                ""
            )
            .replace(
                Regex("\\s+"),
                " "
            )
            .trim()
    }

    private fun makeKey(
        title: String,
        artist: String
    ): String {

        return CACHE_VERSION
            .toString() +
            "_" +
            normalise(title) +
            "_" +
            normalise(artist)
    }

    private fun cacheFile(
        context: Context,
        key: String
    ): File {

        return File(
            context.cacheDir,
            "lyrics_" +
                key.hashCode()
                    .toString() +
                ".txt"
        )
    }

    private fun save(
        context: Context,
        key: String,
        document: LyricsDocument
    ) {

        try {

            val file =
                cacheFile(
                    context,
                    key
                )

            val data =
                buildString {

                    append(
                        document.source
                    )

                    append("\n")

                    append(
                        document.confidence
                    )

                    append("\n")

                    for (
                        line in document.lines
                    ) {

                        append(
                            line.timeMs
                        )

                        append("|")

                        append(
                            line.text.replace(
                                "\n",
                                " "
                            )
                        )

                        append("\n")
                    }
                }

            file.writeText(data)

        } catch (
            _: Exception
        ) {
            // Cache failure should never
            // prevent lyrics from working.
        }
    }
        private fun readDiskCache(
        context: Context,
        key: String
    ): LyricsDocument? {

        return try {

            val file =
                cacheFile(
                    context,
                    key
                )

            if (!file.exists()) {
                return null
            }

            val lines =
                file.readLines()

            if (lines.size < 3) {
                return null
            }

            val source =
                lines[0]

            val confidence =
                lines[1]
                    .toFloatOrNull()
                    ?: 0f

            val entries =
                lines
                    .drop(2)
                    .mapNotNull { cachedLine ->

                        val split =
                            cachedLine.split(
                                "|",
                                limit = 2
                            )

                        if (split.size != 2) {
                            return@mapNotNull null
                        }

                        val time =
                            split[0]
                                .toLongOrNull()
                                ?: return@mapNotNull null

                        time to split[1]
                    }

            if (entries.isEmpty()) {
                return null
            }

            val result =
                entries.mapIndexed {
                        index,
                        entry ->

                    val start =
                        entry.first

                    val end =
                        entries
                            .getOrNull(index + 1)
                            ?.first
                            ?: (
                                start + 5000L
                            )

                    LyricLine(
                        timeMs = start,
                        endMs = end,
                        text = entry.second,
                        words =
                            LyricsTiming
                                .estimateWords(
                                    entry.second,
                                    start,
                                    end
                                )
                    )
                }

            LyricsDocument(
                lines = result,
                source = source,
                confidence = confidence
            )

        } catch (
            _: Exception
        ) {

            null
        }
    }
}
