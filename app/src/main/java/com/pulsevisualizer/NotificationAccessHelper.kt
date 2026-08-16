package com.pulsevisualizer

import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.appcompat.app.AlertDialog

object NotificationAccessHelper {

    /**
     * Returns true if this app has been granted
     * Notification Listener access.
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
                MusicNotificationListener::class.java
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
             * Android versions before Oreo don't have
             * the NotificationManager API above.
             *
             * Check the enabled notification listeners
             * directly.
             */
            val enabledListeners =
                notificationManager
                    .getEnabledNotificationListenerPackages()

            enabledListeners.contains(
                context.packageName
            )
        }
    }


    /**
     * Shows the permission explanation dialog.
     *
     * If permission is already granted, nothing happens.
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
                "Not now"
            ) {
                dialog, _ ->

                dialog.dismiss()
            }
            .setPositiveButton(
                "Grant access"
            ) {
                dialog, _ ->

                dialog.dismiss()

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
     */
    fun openNotificationAccessSettings(
        context: Context
    ) {

        try {

            /*
             * Android 11+ supports opening the specific
             * listener's detail page.
             */
            if (
                Build.VERSION.SDK_INT >=
                Build.VERSION_CODES.R
            ) {

                val component =
                    ComponentName(
                        context,
                        MusicNotificationListener::class.java
                    )

                val intent =
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
                        intent
                    )

                    return

                } catch (
                    _: Exception
                ) {

                    /*
                     * Some Samsung/Android builds may not
                     * expose the detail page.
                     *
                     * Fall back to the general page.
                     */
                }
            }

            /*
             * Universal fallback.
             */
            val intent =
                Intent(
                    Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS
                )

            context.startActivity(
                intent
            )

        } catch (
            _: Exception
        ) {

            /*
             * Extremely unusual fallback in case the
             * device doesn't expose the settings activity.
             */
            Toast.makeText(
                context,
                "Please enable Notification access for Music Widget in Android Settings.",
                Toast.LENGTH_LONG
            ).show()
        }
    }


    /**
     * Checks permission again after the user returns
     * from Android Settings.
     *
     * Returns true if access is now enabled.
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

            /*
             * Restart the media listener/repository so
             * the app immediately starts receiving music
             * information.
             */
            try {

                MediaRepository.start(
                    context.applicationContext
                )

            } catch (
                _: Exception
            ) {
                /*
                 * Don't crash the app if the repository
                 * isn't ready yet.
                 */
            }
        }

        return granted
    }
}
