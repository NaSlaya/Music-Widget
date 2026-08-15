package com.pulsevisualizer

import android.service.notification.NotificationListenerService

class MediaListenerService : NotificationListenerService() {

    override fun onListenerConnected() {
        super.onListenerConnected()

        MediaRepository.start(
            context = this,
            notificationListenerComponent = componentName
        )
    }

    override fun onListenerDisconnected() {
        MediaRepository.stop()

        super.onListenerDisconnected()
    }
}
