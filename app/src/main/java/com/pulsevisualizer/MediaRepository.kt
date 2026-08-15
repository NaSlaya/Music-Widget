package com.pulsevisualizer

import android.content.ComponentName
import android.content.Context
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.service.notification.NotificationListenerService
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

    private var listenerComponent: ComponentName? = null

    fun start(context: Context) {
        if (manager != null) return

        val applicationContext = context.applicationContext

        appContext = applicationContext

        listenerComponent = ComponentName(
            applicationContext,
            MediaListenerService::class.java
        )

        manager = applicationContext.getSystemService(
            MediaSessionManager::class.java
        )

        val component = listenerComponent ?: return
        val sessionManager = manager ?: return

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

            update(
                sessionManager.getActiveSessions(component)
            )

        } catch (_: SecurityException) {
            controllers = emptyList()
            _media.value = MediaInfo()

            appContext?.let {
                MusicWidgetProvider.updateAll(it)
            }
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

        listener = null
        manager = null
        controllers = emptyList()
        appContext = null
        listenerComponent = null
        selectedPackage = null

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
        val controller = currentController() ?: return

        val state = controller.playbackState?.state

        if (
            state == android.media.session.PlaybackState.STATE_PLAYING
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

        refresh()
    }

    fun previous() {
        currentController()
            ?.transportControls
            ?.skipToPrevious()

        refresh()
    }

    private fun refresh() {
        update(controllers)

        appContext?.let {
            MusicWidgetProvider.updateAll(it)
        }
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
            _media.value = MediaInfo()

            appContext?.let {
                MusicWidgetProvider.updateAll(it)
            }

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
                android.media.session.PlaybackState.STATE_PLAYING

        _media.value = MediaInfo(
            packageName = controller.packageName,
            appName = controller.packageName,
            title = title,
            artist = artist,
            artwork = artwork,
            playing = playing
        )

        appContext?.let {
            MusicWidgetProvider.updateAll(it)
        }
    }
}
