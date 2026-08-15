package com.pulsevisualizer

import android.content.ComponentName
import android.content.Context
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
    private var listener: MediaSessionManager.OnActiveSessionsChangedListener? = null

    private var controllers: List<MediaController> = emptyList()
    private var selectedPackage: String? = null
    private var appContext: Context? = null
    private var updateJob: Job? = null

    private var notificationListenerComponent: ComponentName? = null

    private var lastPackageName = ""
    private var lastTitle = ""
    private var lastArtist = ""
    private var lastPlaying = false
    private var lastArtwork: android.graphics.Bitmap? = null

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

        if (listener != null) {
            refresh()
            return
        }

        val newListener =
            MediaSessionManager.OnActiveSessionsChangedListener { sessions ->
                update(sessions ?: emptyList())
            }

        listener = newListener

        try {

            sessionManager.addOnActiveSessionsChangedListener(
                newListener,
                component
            )

            refresh()

            updateJob?.cancel()

            updateJob = CoroutineScope(
                Dispatchers.Main.immediate
            ).launch {

                while (isActive) {

                    try {

                        val activeSessions =
                            sessionManager.getActiveSessions(
                                notificationListenerComponent
                            )

                        update(activeSessions)

                    } catch (_: SecurityException) {

                        /*
                         * Notification listener access may temporarily
                         * disappear while Android is reconnecting it.
                         *
                         * Do not erase the current media information.
                         */

                    } catch (_: Exception) {
                    }

                    delay(500)
                }
            }

        } catch (_: SecurityException) {

            controllers = emptyList()

            _media.value = MediaInfo()

            updateWidget()
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

        lastPackageName = ""
        lastTitle = ""
        lastArtist = ""
        lastPlaying = false
        lastArtwork = null

        _media.value = MediaInfo()
    }

    fun selectPackage(packageName: String?) {

        selectedPackage = packageName

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

        val controller = currentController() ?: return

        val state = controller.playbackState?.state

        if (state == PlaybackState.STATE_PLAYING) {
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

            delay(400)
            refresh()

            delay(800)
            refresh()
        }
    }

    private fun refresh() {

        val sessionManager = manager ?: return

        try {

            val activeSessions =
                sessionManager.getActiveSessions(
                    notificationListenerComponent
                )

            update(activeSessions)

        } catch (_: SecurityException) {

            /*
             * Keep the existing state if Android temporarily
             * refuses access.
             */

        } catch (_: Exception) {
        }

        updateWidget()
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

            if (_media.value.title != "Nothing playing") {

                _media.value = MediaInfo()
            }

            updateWidget()
            return
        }

        val metadata = controller.metadata

        val title =
            metadata?.getString(
                MediaMetadata.METADATA_KEY_TITLE
            )
                ?: metadata?.getString(
                    MediaMetadata.METADATA_KEY_DISPLAY_TITLE
                )
                ?: metadata?.getString(
                    MediaMetadata.METADATA_KEY_MEDIA_URI
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

        val changed =
            controller.packageName != lastPackageName ||
                title != lastTitle ||
                artist != lastArtist ||
                playing != lastPlaying ||
                artworkChanged(artwork)

        if (changed) {

            lastPackageName = controller.packageName
            lastTitle = title
            lastArtist = artist
            lastPlaying = playing
            lastArtwork = artwork

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
    }

    private fun artworkChanged(
        artwork: android.graphics.Bitmap?
    ): Boolean {

        if (artwork == null && lastArtwork == null) {
            return false
        }

        if (artwork == null || lastArtwork == null) {
            return true
        }

        return artwork !== lastArtwork
    }

    private fun updateWidget() {

        appContext?.let { context ->

            try {

                MusicWidgetProvider.updateAll(context)

            } catch (_: Exception) {
                /*
                 * Widget failures must never break media detection.
                 */
            }
        }
    }
}
