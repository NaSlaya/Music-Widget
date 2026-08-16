package com.pulsevisualizer

import kotlin.math.max
import kotlin.math.min

object LyricsTiming {

    private val punctuation =
        Regex("[,.!?;:…]+$")

    private val whitespace =
        Regex("\\s+")

    fun estimateWords(
        text: String,
        startMs: Long,
        endMs: Long
    ): List<LyricWord> {

        val clean =
            text
                .replace(
                    whitespace,
                    " "
                )
                .trim()

        if (
            clean.isBlank()
        ) {
            return emptyList()
        }

        val tokens =
            clean.split(" ")

        if (
            tokens.isEmpty()
        ) {
            return emptyList()
        }

        val duration =
            max(
                250L,
                endMs - startMs
            )

        /*
         * Give longer words more time.
         *
         * Punctuation receives additional
         * weighting because singers naturally
         * pause around it.
         */

        val weights =
            tokens.map { word ->

                val stripped =
                    word
                        .replace(
                            punctuation,
                            ""
                        )

                var weight =
                    max(
                        1.0,
                        stripped.length
                            .toDouble()
                            .pow(0.72)
                    )

                if (
                    word.endsWith(
                        ","
                    ) ||
                    word.endsWith(
                        ";"
                    )
                ) {
                    weight *= 1.20
                }

                if (
                    word.endsWith(
                        "."
                    ) ||
                    word.endsWith(
                        "!"
                    ) ||
                    word.endsWith(
                        "?"
                    ) ||
                    word.endsWith(
                        "…"
                    )
                ) {
                    weight *= 1.40
                }

                weight
            }

        val totalWeight =
            weights.sum()

        if (
            totalWeight <= 0.0
        ) {
            return tokens.mapIndexed {
                index,
                word ->

                val s =
                    startMs +
                    (
                        duration *
                        index
                    ) /
                    tokens.size

                val e =
                    startMs +
                    (
                        duration *
                        (index + 1)
                    ) /
                    tokens.size

                LyricWord(
                    text =
                        word,
                    startMs =
                        s,
                    endMs =
                        e
                )
            }
        }

        val words =
            mutableListOf<LyricWord>()

        var cursor =
            startMs.toDouble()

        for (
            index in
            tokens.indices
        ) {

            val portion =
                weights[index] /
                totalWeight

            val wordDuration =
                duration *
                portion

            val wordStart =
                cursor.toLong()

            val wordEnd =
                if (
                    index ==
                    tokens.lastIndex
                ) {

                    endMs

                } else {

                    (
                        cursor +
                        wordDuration
                    )
                        .toLong()
                }

            words.add(
                LyricWord(
                    text =
                        tokens[index],
                    startMs =
                        wordStart,
                    endMs =
                        max(
                            wordStart + 50L,
                            wordEnd
                        )
                )
            )

            cursor +=
                wordDuration
        }

        return words
    }


    fun findCurrentWord(
        words: List<LyricWord>,
        positionMs: Long
    ): Int {

        if (
            words.isEmpty()
        ) {
            return -1
        }

        for (
            index in
            words.indices
        ) {

            val word =
                words[index]

            if (
                positionMs >=
                word.startMs &&
                positionMs <
                word.endMs
            ) {

                return index
            }
        }

        if (
            positionMs <
            words.first().startMs
        ) {
            return -1
        }

        return words.lastIndex
    }


    fun wordProgress(
        word: LyricWord,
        positionMs: Long
    ): Float {

        val duration =
            max(
                1L,
                word.endMs -
                    word.startMs
            )

        return (
            positionMs -
            word.startMs
        )
            .toFloat() /
            duration
            .toFloat()
    }


    private fun Double.pow(
        power: Double
    ): Double {

        return kotlin.math.pow(
            power
        )
    }
}
