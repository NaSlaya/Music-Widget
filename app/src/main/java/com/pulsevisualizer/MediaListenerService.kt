package com.pulsevisualizer

import android.content.ComponentName
import android.service.notification.NotificationListenerService

class MediaListenerService : NotificationListenerService() {

    override fun onListenerConnected() {
        super.onListenerConnected()

        val listenerComponent = ComponentName(
            this,
            MediaListenerService::class.java
        )

        MediaRepository.start(
            context = this,
            notificationListenerComponent = listenerComponent
        )
    }

    override fun onListenerDisconnected() {
        MediaRepository.stop()

        super.onListenerDisconnected()
    }
}
