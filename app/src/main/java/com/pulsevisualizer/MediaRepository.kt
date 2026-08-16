package com.pulsevisualizer

import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.SystemClock

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

object MediaRepository {

    private val _media =
        MutableStateFlow(
            MediaInfo()
        )

    val media: StateFlow<MediaInfo> =
        _media

    private var manager:
        MediaSessionManager? =
        null

    private var listener:
        MediaSessionManager.OnActiveSessionsChangedListener? =
        null

    private var controllers:
        List<MediaController> =
        emptyList()

    private var selectedPackage:
        String? =
        null

    private var appContext:
        Context? =
        null

    private var updateJob:
        Job? =
        null

    private var notificationListenerComponent:
        ComponentName? =
        null

    private var lastPackageName =
        ""

    private var lastTitle =
        ""

    private var lastArtist =
        ""

    private var lastPlaying =
        false

    private var lastArtwork:
        Bitmap? =
        null

    private var lastPosition =
        0L

    private var lastPlaybackSpeed =
        0f

    private var lastPositionUpdateTime =
        0L

    private var started =
        false


    fun start(
        context: Context
    ) {

        appContext =
            context.applicationContext

        val component =
            ComponentName(
                context,
                MediaListenerService::class.java
            )

        notificationListenerComponent =
            component

        if (manager == null) {

            manager =
                context.getSystemService(
                    MediaSessionManager::class.java
                )
        }

        val sessionManager =
            manager ?: return

        if (started) {

            refresh()

            return
        }

        started =
            true

        val activeListener =
            MediaSessionManager
                .OnActiveSessionsChangedListener { sessions ->

                    update(
                        sessions ?: emptyList()
                    )
                }

        listener =
            activeListener

        try {

            sessionManager
                .addOnActiveSessionsChangedListener(
                    activeListener,
                    notificationListenerComponent
                )

        } catch (
            _: SecurityException
        ) {

            started =
                false

            controllers =
                emptyList()

            return
        }

        refresh()

        updateJob?.cancel()

        updateJob =
            CoroutineScope(
                Dispatchers.Main.immediate
            ).launch {

                while (isActive) {

                    refresh()

                    delay(250)
                }
            }
    }


    fun stop() {

        updateJob?.cancel()

        updateJob =
            null

        try {

            listener?.let { currentListener ->

                manager
                    ?.removeOnActiveSessionsChangedListener(
                        currentListener
                    )
            }

        } catch (
            _: Exception
        ) {
        }

        listener =
            null

        manager =
            null

        controllers =
            emptyList()

        selectedPackage =
            null

        appContext =
            null

        notificationListenerComponent =
            null

        started =
            false

        lastPackageName =
            ""

        lastTitle =
            ""

        lastArtist =
            ""

        lastPlaying =
            false

        lastArtwork =
            null

        lastPosition =
            0L

        lastPlaybackSpeed =
            0f

        lastPositionUpdateTime =
            0L

        _media.value =
            MediaInfo()
    }


    fun selectPackage(
        packageName: String?
    ) {

        selectedPackage =
            packageName

        update(
            controllers
        )
    }


    fun availablePackages():
        List<String> {

        return controllers
            .map {
                it.packageName
            }
            .distinct()
    }


    private fun currentController():
        MediaController? {

        selectedPackage?.let {
            packageName ->

            controllers
                .firstOrNull {
                    it.packageName ==
                        packageName
                }
                ?.let {

                    return it
                }
        }

        controllers
            .firstOrNull {

                it.playbackState?.state ==
                    PlaybackState.STATE_PLAYING

            }
            ?.let {

                return it
            }

        controllers
            .firstOrNull {

                val metadata =
                    it.metadata

                !metadata
                    ?.getString(
                        MediaMetadata.METADATA_KEY_TITLE
                    )
                    .isNullOrBlank()
            }
            ?.let {

                return it
            }

        return controllers.firstOrNull()
    }


    fun play() {

        currentController()
            ?.transportControls
            ?.play()

        refreshDelayed()
    }


    fun pause() {

        currentController()
            ?.transportControls
            ?.pause()

        refreshDelayed()
    }


    fun togglePlayPause() {

        val controller =
            currentController()
                ?: return

        val state =
            controller
                .playbackState
                ?.state

        if (
            state ==
            PlaybackState.STATE_PLAYING
        ) {

            controller
                .transportControls
                .pause()

        } else {

            controller
                .transportControls
                .play()
        }

        refreshDelayed()
    }


    fun next() {

        currentController()
            ?.transportControls
            ?.skipToNext()

        refreshDelayed()
    }


    fun previous() {

        currentController()
            ?.transportControls
            ?.skipToPrevious()

        refreshDelayed()
    }


    private fun refreshDelayed() {

        CoroutineScope(
            Dispatchers.Main.immediate
        ).launch {

            delay(100)

            refresh()

            delay(300)

            refresh()

            delay(600)

            refresh()
        }
    }
        private fun refresh() {

        val sessionManager =
            manager
                ?: return

        val component =
            notificationListenerComponent
                ?: return

        try {

            val sessions =
                sessionManager
                    .getActiveSessions(
                        component
                    )

            update(
                sessions
            )

        } catch (
            _: SecurityException
        ) {

        } catch (
            _: Exception
        ) {
        }
    }


    private fun update(
        list: List<MediaController>
    ) {

        controllers =
            list

        if (
            list.isEmpty()
        ) {

            return
        }

        val controller =
            chooseController(
                list
            )
                ?: return

        val metadata =
            controller.metadata

        val title =
            metadata
                ?.getString(
                    MediaMetadata.METADATA_KEY_TITLE
                )
                ?.takeIf {
                    it.isNotBlank()
                }
                ?: metadata
                    ?.getString(
                        MediaMetadata
                            .METADATA_KEY_DISPLAY_TITLE
                    )
                    ?.takeIf {
                        it.isNotBlank()
                    }
                ?: "Unknown title"

        val artist =
            metadata
                ?.getString(
                    MediaMetadata.METADATA_KEY_ARTIST
                )
                ?.takeIf {
                    it.isNotBlank()
                }
                ?: metadata
                    ?.getString(
                        MediaMetadata
                            .METADATA_KEY_ALBUM_ARTIST
                    )
                    ?.takeIf {
                        it.isNotBlank()
                    }
                ?: metadata
                    ?.getString(
                        MediaMetadata
                            .METADATA_KEY_DISPLAY_SUBTITLE
                    )
                    ?.takeIf {
                        it.isNotBlank()
                    }
                ?: ""

        val artwork =
            metadata
                ?.getBitmap(
                    MediaMetadata.METADATA_KEY_ART
                )
                ?: metadata
                    ?.getBitmap(
                        MediaMetadata
                            .METADATA_KEY_ALBUM_ART
                    )

        val playbackState =
            controller.playbackState

        val state =
            playbackState?.state

        val playing =
            state ==
                PlaybackState.STATE_PLAYING

        val position =
            playbackState
                ?.position
                ?.coerceAtLeast(0L)
                ?: 0L

        val speed =
            playbackState
                ?.playbackSpeed
                ?: 0f

        val positionUpdateTime =
            playbackState
                ?.lastPositionUpdateTime
                ?: 0L

        val changed =
            packageNameChanged(
                controller.packageName
            ) ||
            title != lastTitle ||
            artist != lastArtist ||
            playing != lastPlaying ||
            positionChanged(
                position
            ) ||
            speed != lastPlaybackSpeed ||
            positionUpdateTime !=
                lastPositionUpdateTime ||
            artworkChanged(
                artwork
            )

        if (!changed) {
            return
        }

        lastPackageName =
            controller.packageName

        lastTitle =
            title

        lastArtist =
            artist

        lastPlaying =
            playing

        lastPosition =
            position

        lastPlaybackSpeed =
            speed

        lastPositionUpdateTime =
            positionUpdateTime

        lastArtwork =
            artwork

        _media.value =
            MediaInfo(
                packageName =
                    controller.packageName,

                appName =
                    controller.packageName,

                title =
                    title,

                artist =
                    artist,

                artwork =
                    artwork,

                playing =
                    playing,

                positionMs =
                    position,

                playbackSpeed =
                    speed,

                positionUpdateTimeMs =
                    positionUpdateTime
            )

        updateWidget()
    }


    private fun chooseController(
        list: List<MediaController>
    ): MediaController? {

        selectedPackage?.let {
            packageName ->

            list
                .firstOrNull {
                    it.packageName ==
                        packageName
                }
                ?.let {

                    return it
                }
        }

        list
            .firstOrNull {

                it.playbackState
                    ?.state ==
                    PlaybackState.STATE_PLAYING

            }
            ?.let {

                return it
            }

        list
            .firstOrNull {

                val metadata =
                    it.metadata

                !metadata
                    ?.getString(
                        MediaMetadata
                            .METADATA_KEY_TITLE
                    )
                    .isNullOrBlank()
            }
            ?.let {

                return it
            }

        return list.firstOrNull()
    }


    fun getCurrentPositionMs():
        Long {

        val media =
            _media.value

        if (
            media.positionUpdateTimeMs <= 0L
        ) {

            return media.positionMs
                .coerceAtLeast(0L)
        }

        if (
            !media.playing
        ) {

            return media.positionMs
                .coerceAtLeast(0L)
        }

        val elapsed =
            (
                SystemClock.elapsedRealtime() -
                media.positionUpdateTimeMs
            )
                .coerceAtLeast(0L)

        val additional =
            (
                elapsed.toDouble() *
                media.playbackSpeed
                    .toDouble()
            )
                .toLong()

        return (
            media.positionMs +
            additional
        )
            .coerceAtLeast(0L)
        }
            private fun packageNameChanged(
        packageName: String
    ): Boolean {

        return packageName !=
            lastPackageName
    }


    private fun positionChanged(
        position: Long
    ): Boolean {

        return kotlin.math.abs(
            position -
            lastPosition
        ) > 500L
    }


    private fun artworkChanged(
        artwork: Bitmap?
    ): Boolean {

        if (
            artwork == null &&
            lastArtwork == null
        ) {

            return false
        }

        if (
            artwork == null ||
            lastArtwork == null
        ) {

            return true
        }

        if (
            artwork.width !=
            lastArtwork!!.width
        ) {

            return true
        }

        if (
            artwork.height !=
            lastArtwork!!.height
        ) {

            return true
        }

        return false
    }


    private fun updateWidget() {

        appContext?.let {
            context ->

            try {

                MusicWidgetProvider
                    .updateAll(
                        context
                    )

            } catch (
                _: Exception
            ) {
            }

            try {

                LyricsWidgetProvider
                    .updateAll(
                        context
                    )

            } catch (
                _: Exception
            ) {
            }
        }
    }
}
