package com.pulsevisualizer

import android.content.Context
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

object MediaRepository {

    private val _media = MutableStateFlow(MediaInfo())
    val media: StateFlow<MediaInfo> = _media

    private var manager: MediaSessionManager? = null

    private var listener:
        MediaSessionManager.OnActiveSessionsChangedListener? = null

    private var controllers = emptyList<MediaController>()

    private var selectedPackage: String? = null

    private var appContext: Context? = null

    /*
     * The MediaController currently being observed.
     */
    private var callbackController: MediaController? = null

    /*
     * Callback used to receive live changes from Spotify, YouTube,
     * YouTube Music, etc.
     */
    private var controllerCallback: MediaController.Callback? = null

    fun start(context: Context) {
        if (manager != null) {
            return
        }

        appContext = context.applicationContext

        manager = context.getSystemService(
            MediaSessionManager::class.java
        )

        val newListener =
            MediaSessionManager.OnActiveSessionsChangedListener { sessions ->

                update(
                    sessions ?: emptyList()
                )
            }

        listener = newListener

        try {

            manager?.addOnActiveSessionsChangedListener(
                newListener,
                null
            )

            update(
                manager?.getActiveSessions(null)
                    ?: emptyList()
            )

        } catch (_: SecurityException) {

            controllers = emptyList()

            detachControllerCallback()

            _media.value = MediaInfo()

            updateWidget()
        }
    }

    fun stop() {

        try {

            val currentListener = listener

            if (currentListener != null) {

                manager?.removeOnActiveSessionsChangedListener(
                    currentListener
                )
            }

        } catch (_: Exception) {
        }

        detachControllerCallback()

        listener = null

        manager = null

        controllers = emptyList()

        appContext = null

        _media.value = MediaInfo()
    }

    fun selectPackage(pkg: String?) {

        selectedPackage = pkg

        update(controllers)
    }

    fun availablePackages(): List<String> {

        return controllers
            .map { it.packageName }
            .distinct()
    }

    private fun currentController(): MediaController? {

        return controllers.firstOrNull {

            selectedPackage == null ||
                it.packageName == selectedPackage

        } ?: controllers.firstOrNull()
    }

    fun play() {

        val controller = currentController()
            ?: return

        controller.transportControls.play()

        refresh()
    }

    fun pause() {

        val controller = currentController()
            ?: return

        controller.transportControls.pause()

        refresh()
    }

    fun togglePlayPause() {

        val controller = currentController()
            ?: return

        val state =
            controller.playbackState?.state

        if (state == PlaybackState.STATE_PLAYING) {

            controller.transportControls.pause()

        } else {

            controller.transportControls.play()
        }

        /*
         * The controller callback will normally update this
         * automatically. We also refresh immediately so the
         * UI responds without waiting for the media application.
         */
        refresh()
    }

    fun next() {

        val controller = currentController()
            ?: return

        controller.transportControls.skipToNext()

        refresh()
    }

    fun previous() {

        val controller = currentController()
            ?: return

        controller.transportControls.skipToPrevious()

        refresh()
    }

    fun refresh() {

        update(controllers)
    }

    private fun update(
        list: List<MediaController>
    ) {

        controllers = list

        val controller =
            list.firstOrNull {

                selectedPackage == null ||
                    it.packageName == selectedPackage

            } ?: list.firstOrNull()

        if (controller == null) {

            detachControllerCallback()

            _media.value = MediaInfo()

            updateWidget()

            return
        }

        /*
         * If Android gave us a different MediaController,
         * move the callback to the new controller.
         */
        if (callbackController !== controller) {

            attachControllerCallback(controller)
        }

        updateFromController(controller)
    }

    private fun updateFromController(
        controller: MediaController
    ) {

        /*
         * Ignore callbacks from an old controller that has
         * already been replaced.
         */
        if (
            callbackController != null &&
            callbackController !== controller
        ) {
            return
        }

        val metadata =
            controller.metadata

        val title =
            metadata?.getString(
                MediaMetadata.METADATA_KEY_TITLE
            )
                ?: metadata?.getString(
                    MediaMetadata.METADATA_KEY_DISPLAY_TITLE
                )
                ?: metadata?.getString(
                    MediaMetadata.METADATA_KEY_ALBUM
                )
                ?: "Unknown title"

        val artist =
            metadata?.getString(
                MediaMetadata.METADATA_KEY_ARTIST
            )
                ?: metadata?.getString(
                    MediaMetadata.METADATA_KEY_ALBUM_ARTIST
                )
                ?: metadata?.getString(
                    MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE
                )
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
            playbackState == PlaybackState.STATE_PLAYING

        _media.value = MediaInfo(
            packageName = controller.packageName,
            appName = controller.packageName,
            title = title,
            artist = artist,
            artwork = artwork,
            playing = playing
        )

        updateWidget()
    }

    /*
     * Attach a live MediaController callback.
     *
     * This is the important part that was missing from the
     * previous version.
     */
    private fun attachControllerCallback(
        controller: MediaController
    ) {

        detachControllerCallback()

        val callback =
            object : MediaController.Callback() {

                override fun onMetadataChanged(
                    metadata: MediaMetadata?
                ) {

                    /*
                     * Metadata changes when a new song starts,
                     * when Spotify/YouTube changes artwork, etc.
                     */
                    if (
                        callbackController === controller
                    ) {

                        updateFromController(controller)
                    }
                }

                override fun onPlaybackStateChanged(
                    state: PlaybackState?
                ) {

                    /*
                     * Playback changes when:
                     *
                     * Play is pressed
                     * Pause is pressed
                     * A song finishes
                     * Playback resumes
                     * Playback stops
                     */
                    if (
                        callbackController === controller
                    ) {

                        updateFromController(controller)
                    }
                }

                override fun onSessionDestroyed() {

                    /*
                     * The media application/session has gone away.
                     * Remove the controller and look for another
                     * available media session.
                     */
                    if (
                        callbackController === controller
                    ) {

                        callbackController = null
                        controllerCallback = null

                        controllers =
                            controllers.filter {
                                it !== controller
                            }

                        update(controllers)
                    }
                }
            }

        callbackController = controller
        controllerCallback = callback

        try {

            controller.registerCallback(callback)

        } catch (_: Exception) {

            /*
             * Some media applications can destroy their session
             * while the callback is being registered.
             */
            callbackController = null
            controllerCallback = null
        }
    }

    /*
     * Remove the callback from the currently monitored
     * MediaController.
     */
    private fun detachControllerCallback() {

        val controller =
            callbackController

        val callback =
            controllerCallback

        if (
            controller != null &&
            callback != null
        ) {

            try {

                controller.unregisterCallback(callback)

            } catch (_: Exception) {
            }
        }

        callbackController = null

        controllerCallback = null
    }

    /*
     * Update the home-screen widget whenever media information
     * changes.
     */
    private fun updateWidget() {

        appContext?.let { context ->

            try {

                MusicWidgetProvider.updateAll(
                    context
                )

            } catch (_: Exception) {
                /*
                 * Widget failures must never stop media
                 * detection.
                 */
            }
        }
    }
}
