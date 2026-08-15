package com.pulsevisualizer

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

    private var listener:
        MediaSessionManager.OnActiveSessionsChangedListener? = null

    private var controllers = emptyList<MediaController>()

    private var selectedPackage: String? = null

    private var appContext: Context? = null

    private var updateJob: Job? = null

    /*
     * These values let us detect changes even when the media
     * application does not trigger an active-session change.
     */
    private var lastPackageName: String = ""
    private var lastTitle: String = ""
    private var lastArtist: String = ""
    private var lastPlaying: Boolean = false

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

            /*
             * Keep checking the currently active controller.
             *
             * Spotify, YouTube and other media applications do not
             * always cause onActiveSessionsChanged() when a track
             * changes, so this catches:
             *
             * - next song
             * - previous song
             * - pause
             * - play
             * - song finishing
             * - artwork changes
             */
            updateJob?.cancel()

            updateJob = CoroutineScope(
                Dispatchers.Main.immediate
            ).launch {

                while (isActive) {

                    try {

                        val activeSessions =
                            manager?.getActiveSessions(null)
                                ?: emptyList()

                        if (activeSessions.isNotEmpty()) {

                            update(activeSessions)

                        }

                    } catch (_: SecurityException) {

                        /*
                         * Do not destroy the existing media state
                         * just because access temporarily failed.
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

            val currentListener = listener

            if (currentListener != null) {

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

        lastPackageName = ""
        lastTitle = ""
        lastArtist = ""
        lastPlaying = false

        _media.value = MediaInfo()
    }

    fun selectPackage(pkg: String?) {

        selectedPackage = pkg

        update(controllers)
    }

    fun availablePackages(): List<String> {

        return controllers
            .map {
                it.packageName
            }
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

        refresh()
    }

    fun pause() {

        currentController()
            ?.transportControls
            ?.pause()

        refresh()
    }

    fun togglePlayPause() {

        val controller =
            currentController()
                ?: return

        val state =
            controller.playbackState?.state

        if (
            state == PlaybackState.STATE_PLAYING
        ) {

            controller.transportControls.pause()

        } else {

            controller.transportControls.play()
        }

        refresh()
    }

    fun next() {

        currentController()
            ?.transportControls
            ?.skipToNext()

        /*
         * Give the media application a moment to update its
         * metadata, then immediately refresh again.
         */
        CoroutineScope(
            Dispatchers.Main.immediate
        ).launch {

            delay(100)

            refresh()

            delay(400)

            refresh()
        }
    }

    fun previous() {

        currentController()
            ?.transportControls
            ?.skipToPrevious()

        CoroutineScope(
            Dispatchers.Main.immediate
        ).launch {

            delay(100)

            refresh()

            delay(400)

            refresh()
        }
    }

    private fun refresh() {

        update(controllers)

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

            if (
                _media.value.title != "Nothing playing"
            ) {

                _media.value = MediaInfo()
            }

            updateWidget()

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

        val playing =
            controller.playbackState?.state ==
                PlaybackState.STATE_PLAYING

        /*
         * Only publish a new MediaInfo when something actually
         * changed.
         *
         * This prevents unnecessary Compose/widget redraws
         * every 500 ms.
         */
        val changed =
            controller.packageName != lastPackageName ||
                title != lastTitle ||
                artist != lastArtist ||
                playing != lastPlaying

        if (changed) {

            lastPackageName =
                controller.packageName

            lastTitle =
                title

            lastArtist =
                artist

            lastPlaying =
                playing

            _media.value = MediaInfo(
                packageName = controller.packageName,
                appName = controller.packageName,
                title = title,
                artist = artist,
                artwork = artwork,
                playing = playing
            )

            updateWidget()

        } else if (
            artwork != null &&
            _media.value.artwork !== artwork
        ) {

            /*
             * Artwork can change independently of title/artist.
             */
            _media.value =
                _media.value.copy(
                    artwork = artwork
                )

            updateWidget()
        }
    }

    private fun updateWidget() {

        appContext?.let { context ->

            try {

                MusicWidgetProvider.updateAll(
                    context
                )

            } catch (_: Exception) {
                /*
                 * Widget errors must never prevent media
                 * detection.
                 */
            }
        }
    }
}
