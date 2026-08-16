package com.pulsevisualizer

import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

object MediaRepository {

    private val _media = MutableStateFlow(MediaInfo())

    val media: StateFlow<MediaInfo> = _media

    private var manager: MediaSessionManager? = null

    private var listener:
        MediaSessionManager.OnActiveSessionsChangedListener? = null

    private var controllers: List<MediaController> = emptyList()

    private var selectedPackage: String? = null

    private var appContext: Context? = null

    private var updateJob: Job? = null

    private var notificationListenerComponent: ComponentName? = null

    private var lastPackageName = ""
    private var lastTitle = ""
    private var lastArtist = ""
    private var lastPlaying = false
    private var lastArtwork: Bitmap? = null

    private var started = false


    fun start(context: Context) {

        appContext = context.applicationContext

        val component = ComponentName(
            context,
            MediaListenerService::class.java
        )

        notificationListenerComponent = component

        if (manager == null) {

            manager = context.getSystemService(
                MediaSessionManager::class.java
            )
        }

        val sessionManager = manager ?: return

        if (started) {

            refresh()

            return
        }

        started = true

        val activeListener =
            MediaSessionManager.OnActiveSessionsChangedListener { sessions ->

                update(
                    sessions ?: emptyList()
                )
            }

        listener = activeListener

        try {

            /*
             * Android only allows getActiveSessions()
             * when the notification listener component
             * is enabled.
             */

            sessionManager.addOnActiveSessionsChangedListener(
                activeListener,
                notificationListenerComponent
            )

        } catch (_: SecurityException) {

            started = false

            controllers = emptyList()

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

                    delay(750)
                }
            }
    }


    fun stop() {

        updateJob?.cancel()

        updateJob = null

        try {

            listener?.let { currentListener ->

                manager?.removeOnActiveSessionsChangedListener(
                    currentListener
                )
            }

        } catch (_: Exception) {
        }

        listener = null

        manager = null

        controllers = emptyList()

        selectedPackage = null

        appContext = null

        notificationListenerComponent = null

        started = false

        lastPackageName = ""
        lastTitle = ""
        lastArtist = ""
        lastPlaying = false
        lastArtwork = null

        _media.value = MediaInfo()
    }


    fun selectPackage(packageName: String?) {

        selectedPackage = packageName

        update(
            controllers
        )
    }


    fun availablePackages(): List<String> {

        return controllers
            .map { it.packageName }
            .distinct()
    }


    private fun currentController(): MediaController? {

        /*
         * Prefer the selected application.
         */

        selectedPackage?.let { packageName ->

            controllers.firstOrNull {
                it.packageName == packageName
            }?.let {
                return it
            }
        }

        /*
         * Otherwise prefer a currently playing session.
         */

        controllers.firstOrNull {

            it.playbackState?.state ==
                PlaybackState.STATE_PLAYING

        }?.let {

            return it
        }

        /*
         * Finally use the first available controller.
         */

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
            controller.playbackState?.state

        if (
            state ==
            PlaybackState.STATE_PLAYING
        ) {

            controller.transportControls.pause()

        } else {

            controller.transportControls.play()
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
            manager ?: return

        val component =
            notificationListenerComponent
                ?: return

        try {

            val sessions =
                sessionManager.getActiveSessions(
                    component
                )

            update(
                sessions
            )

        } catch (_: SecurityException) {

            /*
             * Android can temporarily revoke access
             * while the notification listener reconnects.
             *
             * Keep the existing media state.
             */

        } catch (_: Exception) {
        }
    }


    private fun update(
        list: List<MediaController>
    ) {

        controllers = list

        if (list.isEmpty()) {

            /*
             * Don't immediately erase a valid song.
             *
             * Android can briefly return an empty list
             * while Spotify/YouTube changes sessions.
             */

            return
        }

        val controller =
            chooseController(
                list
            ) ?: return


        val metadata =
            controller.metadata


        val title =
            metadata?.getString(
                MediaMetadata.METADATA_KEY_TITLE
            )
                ?.takeIf {
                    it.isNotBlank()
                }
                ?: metadata?.getString(
                    MediaMetadata.METADATA_KEY_DISPLAY_TITLE
                )
                    ?.takeIf {
                        it.isNotBlank()
                    }
                ?: "Unknown title"


        val artist =
            metadata?.getString(
                MediaMetadata.METADATA_KEY_ARTIST
            )
                ?.takeIf {
                    it.isNotBlank()
                }
                ?: metadata?.getString(
                    MediaMetadata.METADATA_KEY_ALBUM_ARTIST
                )
                    ?.takeIf {
                        it.isNotBlank()
                    }
                ?: metadata?.getString(
                    MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE
                )
                    ?.takeIf {
                        it.isNotBlank()
                    }
                ?: ""


        val artwork =
            metadata?.getBitmap(
                MediaMetadata.METADATA_KEY_ART
            )
                ?: metadata?.getBitmap(
                    MediaMetadata.METADATA_KEY_ALBUM_ART
                )


        val playbackState =
            controller.playbackState?.state


        val playing =
            playbackState ==
                PlaybackState.STATE_PLAYING


        val packageName =
            controller.packageName


        val changed =
            packageName != lastPackageName ||
            title != lastTitle ||
            artist != lastArtist ||
            playing != lastPlaying ||
            artworkChanged(
                artwork
            )


        if (!changed) {
            return
        }


        lastPackageName =
            packageName

        lastTitle =
            title

        lastArtist =
            artist

        lastPlaying =
            playing

        lastArtwork =
            artwork


        _media.value =
            MediaInfo(
                packageName = packageName,
                appName = packageName,
                title = title,
                artist = artist,
                artwork = artwork,
                playing = playing
            )


        /*
         * Update BOTH widgets whenever the media changes.
         */

        updateWidget()
    }


    private fun chooseController(
        list: List<MediaController>
    ): MediaController? {

        /*
         * 1. Explicitly selected application.
         */

        selectedPackage?.let { packageName ->

            list.firstOrNull {
                it.packageName == packageName
            }?.let {

                return it
            }
        }


        /*
         * 2. Currently playing application.
         */

        list.firstOrNull {

            it.playbackState?.state ==
                PlaybackState.STATE_PLAYING

        }?.let {

            return it
        }


        /*
         * 3. A session that has actual metadata.
         */

        list.firstOrNull {

            val metadata =
                it.metadata

            !metadata?.getString(
                MediaMetadata.METADATA_KEY_TITLE
            ).isNullOrBlank()

        }?.let {

            return it
        }


        /*
         * 4. Last resort.
         */

        return list.firstOrNull()
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


        /*
         * Bitmap references can change even when
         * the actual artwork is identical, so don't
         * constantly refresh the widget.
         */

        return false
    }


    private fun updateWidget() {

        appContext?.let { context ->

            /*
             * Update the normal music widget.
             */

            try {

                MusicWidgetProvider.updateAll(
                    context
                )

            } catch (_: Exception) {

                /*
                 * Widget errors must never break
                 * media detection.
                 */
            }


            /*
             * Update the lyrics widget.
             */

            try {

                LyricsWidgetProvider.updateAll(
                    context
                )

            } catch (_: Exception) {

                /*
                 * Lyrics widget errors must never
                 * break media detection.
                 */
            }
        }
    }
}
