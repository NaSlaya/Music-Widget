package com.pulsevisualizer

import android.content.Context
import android.graphics.Bitmap
import android.media.session.MediaController
import android.media.session.MediaSessionManager
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

    fun start(context: Context) {
        if (manager != null) return

        manager = context.getSystemService(
            MediaSessionManager::class.java
        )

        val newListener =
            MediaSessionManager.OnActiveSessionsChangedListener { sessions ->
                update(sessions ?: emptyList())
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
            // Notification Access must be enabled by the user.
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

    private fun update(
        list: List<MediaController>
    ) {
        controllers = list

        val controller =
            list.firstOrNull {
                selectedPackage == null ||
                    it.packageName == selectedPackage
            }
                ?: list.firstOrNull()

        if (controller == null) {
            _media.value = MediaInfo()
            return
        }

        val metadata = controller.metadata

        val title =
            metadata?.getString(
                android.media.MediaMetadata.METADATA_KEY_TITLE
            )
                ?: metadata?.getString(
                    android.media.MediaMetadata.METADATA_KEY_DISPLAY_TITLE
                )
                ?: "Unknown title"

        val artist =
            metadata?.getString(
                android.media.MediaMetadata.METADATA_KEY_ARTIST
            )
                ?: metadata?.getString(
                    android.media.MediaMetadata.METADATA_KEY_ALBUM_ARTIST
                )
                ?: metadata?.getString(
                    android.media.MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE
                )
                ?: ""

        val artwork =
            metadata?.getBitmap(
                android.media.MediaMetadata.METADATA_KEY_ART
            )
                ?: metadata?.getBitmap(
                    android.media.MediaMetadata.METADATA_KEY_ALBUM_ART
                )

        _media.value = MediaInfo(
            packageName = controller.packageName,
            appName = controller.packageName,
            title = title,
            artist = artist,
            artwork = artwork,
            playing =
                controller.playbackState?.state ==
                    android.media.session.PlaybackState.STATE_PLAYING
        )
    }
}
