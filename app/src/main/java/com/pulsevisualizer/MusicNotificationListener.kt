package com.pulsevisualizer

import android.service.notification.NotificationListenerService

/**
 * Compatibility listener.
 *
 * The actual notification listener used by the application
 * is MediaListenerService.
 *
 * This class intentionally does not call MediaRepository.refresh()
 * because refresh() is private to MediaRepository.
 */
class MusicNotificationListener :
    NotificationListenerService() {

    override fun onListenerConnected() {
        super.onListenerConnected()

        /*
         * MediaRepository is started by the actual
         * MediaListenerService / MainActivity.
         *
         * Nothing needs to be done here.
         */
    }


    override fun onListenerDisconnected() {
        super.onListenerDisconnected()

        /*
         * Android will reconnect the registered
         * MediaListenerService when appropriate.
         */
    }
    }
