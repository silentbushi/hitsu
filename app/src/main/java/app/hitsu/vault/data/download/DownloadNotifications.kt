package app.hitsu.vault.data.download

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import app.hitsu.vault.MainActivity
import app.hitsu.vault.R

/**
 * A quick download runs with no screen of its own, so the notification is the whole interface: it
 * is what keeps the work alive in the foreground and what reports the outcome.
 */
class DownloadNotifications(private val context: Context) {

    private val manager = NotificationManagerCompat.from(context)

    fun ensureChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.notification_channel_downloads),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = context.getString(R.string.notification_channel_downloads_description)
            setShowBadge(false)
        }
        manager.createNotificationChannel(channel)
    }

    fun progress(title: String?, percent: Int?): Notification =
        base(title ?: context.getString(R.string.notification_downloading))
            .setContentText(context.getString(R.string.notification_downloading))
            .setOngoing(true)
            .apply {
                if (percent == null) {
                    setProgress(0, 0, true)
                } else {
                    setProgress(100, percent, false)
                }
            }
            .build()

    fun finished(title: String?, message: String) {
        // Without the permission the download still runs and saves; it just finishes quietly.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        val notification = base(title ?: context.getString(R.string.app_name))
            .setContentText(message)
            .setAutoCancel(true)
            .build()
        try {
            manager.notify(RESULT_ID, notification)
        } catch (_: SecurityException) {
            // Revoked between the check and here.
        }
    }

    /** Updating the ongoing notification, which is not the same as starting the service again. */
    fun updateProgress(title: String?, percent: Int?) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        try {
            manager.notify(PROGRESS_ID, progress(title, percent))
        } catch (_: SecurityException) {
            // Revoked mid-download; the work carries on quietly.
        }
    }

    fun cancelProgress() {
        manager.cancel(PROGRESS_ID)
    }

    private fun base(title: String) = NotificationCompat.Builder(context, CHANNEL_ID)
        .setSmallIcon(R.drawable.ic_notification)
        .setContentTitle(title)
        .setColor(context.getColor(R.color.hitsu_accent))
        .setContentIntent(openApp())
        .setSilent(true)

    private fun openApp(): PendingIntent {
        val intent = Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        return PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    companion object {
        const val CHANNEL_ID = "downloads"
        const val PROGRESS_ID = 1001
        private const val RESULT_ID = 1002
    }
}
