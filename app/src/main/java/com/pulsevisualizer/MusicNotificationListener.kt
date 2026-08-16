package com.pulsevisualizer

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

class MusicNotificationListener :
    NotificationListenerService() {

    override fun onListenerConnected() {
        super.onListenerConnected()

        /*
         * Notification access is now active.
         *
         * Start/reconnect the media repository.
         */
        try {

            MediaRepository.start(
                applicationContext
            )

        } catch (
            _: Exception
        ) {
            // Don't crash the listener.
        }
    }


    override fun onNotificationPosted(
        sbn: StatusBarNotification
    ) {

        /*
         * MediaRepository is responsible for reading
         * the actual playback information.
         *
         * We don't do lyric fetching here.
         */
        try {

            MediaRepository.refresh(
                applicationContext
            )

        } catch (
            _: Exception
        ) {
            // Ignore notification parsing failures.
        }
    }


    override fun onNotificationRemoved(
        sbn: StatusBarNotification
    ) {

        /*
         * Let the repository refresh its current state.
         */
        try {

            MediaRepository.refresh(
                applicationContext
            )

        } catch (
            _: Exception
        ) {
            // Ignore errors.
        }
    }


    override fun onListenerDisconnected() {
        super.onListenerDisconnected()

        /*
         * Android may reconnect the listener later.
         *
         * Don't try to force MediaRepository here.
         */
    }
  }
