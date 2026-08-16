package com.pulsevisualizer

import android.app.AlertDialog
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.widget.Toast

object NotificationAccessHelper {

    /**
     * Checks whether this application has Notification
     * Listener access.
     *
     * The actual listener used by this project is
     * MediaListenerService.
     */
    fun hasNotificationAccess(
        context: Context
    ): Boolean {

        val notificationManager =
            context.getSystemService(
                Context.NOTIFICATION_SERVICE
            ) as NotificationManager

        val component =
            ComponentName(
                context,
                MediaListenerService::class.java
            )

        return if (
            Build.VERSION.SDK_INT >=
            Build.VERSION_CODES.O
        ) {

            notificationManager
                .isNotificationListenerAccessGranted(
                    component
                )

        } else {

            /*
             * Older Android versions do not provide
             * isNotificationListenerAccessGranted().
             *
             * Check Android's enabled notification
             * listener setting directly instead.
             */
            val enabledListeners =
                Settings.Secure.getString(
                    context.contentResolver,
                    "enabled_notification_listeners"
                )

            if (
                enabledListeners.isNullOrBlank()
            ) {

                false

            } else {

                enabledListeners
                    .split(":")
                    .any { value ->

                        try {

                            ComponentName
                                .unflattenFromString(
                                    value
                                )
                                ?.packageName ==
                                context.packageName

                        } catch (
                            _: Exception
                        ) {

                            false
                        }
                    }
            }
        }
    }


    /**
     * Shows the explanation dialog and opens the
     * correct Android Notification Access settings.
     *
     * Nothing happens if access is already granted.
     */
    fun requestNotificationAccess(
        context: Context
    ) {

        if (
            hasNotificationAccess(
                context
            )
        ) {

            return
        }

        AlertDialog.Builder(
            context
        )
            .setTitle(
                "Music Widget needs access"
            )
            .setMessage(
                "Music Widget needs notification access to detect what song is currently playing and keep the lyrics synchronized with your music."
            )
            .setNegativeButton(
                "Not now",
                null
            )
            .setPositiveButton(
                "Grant access"
            ) { _, _ ->

                openNotificationAccessSettings(
                    context
                )
            }
            .setCancelable(
                true
            )
            .show()
    }


    /**
     * Opens Android's Notification Access settings.
     *
     * On Android 11 and newer we first attempt to open
     * the specific MediaListenerService page.
     *
     * If the device does not support that page, we
     * fall back to the general Notification Access page.
     */
    fun openNotificationAccessSettings(
        context: Context
    ) {

        try {

            if (
                Build.VERSION.SDK_INT >=
                Build.VERSION_CODES.R
            ) {

                val component =
                    ComponentName(
                        context,
                        MediaListenerService::class.java
                    )

                val detailIntent =
                    Intent(
                        Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS
                    ).apply {

                        putExtra(
                            Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME,
                            component
                        )
                    }

                try {

                    context.startActivity(
                        detailIntent
                    )

                    return

                } catch (
                    _: Exception
                ) {

                    /*
                     * Some Android/Samsung versions don't
                     * support the individual listener page.
                     *
                     * Continue to the general page.
                     */
                }
            }


            val settingsIntent =
                Intent(
                    Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS
                )

            context.startActivity(
                settingsIntent
            )

        } catch (
            _: Exception
        ) {

            Toast.makeText(
                context,
                "Please enable Notification access for Pulse Visualizer in Android Settings.",
                Toast.LENGTH_LONG
            ).show()
        }
    }


    /**
     * Called after returning from Android Settings.
     *
     * Returns true when access is now enabled.
     */
    fun checkAfterSettingsReturn(
        context: Context
    ): Boolean {

        val granted =
            hasNotificationAccess(
                context
            )

        if (
            granted
        ) {

            try {

                MediaRepository.start(
                    context.applicationContext
                )

            } catch (
                _: Exception
            ) {

                /*
                 * Do not crash the Activity if the media
                 * repository cannot start yet.
                 */
            }
        }

        return granted
    }
}
