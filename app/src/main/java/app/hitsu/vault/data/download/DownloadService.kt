package app.hitsu.vault.data.download

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import androidx.core.app.ServiceCompat
import app.hitsu.vault.R
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Runs a quick download with no screen behind it. Android will not keep plain background work alive
 * for a process the user cannot see, so this asks to stay in the foreground; the notification that
 * requires is also the only progress the user gets.
 */
@AndroidEntryPoint
class DownloadService : Service() {

    @Inject
    lateinit var coordinator: DownloadCoordinator

    @Inject
    lateinit var notifications: DownloadNotifications

    private val scope = CoroutineScope(SupervisorJob())
    private var work: Job? = null
    private var lastUpdate = 0L

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val url = intent?.getStringExtra(EXTRA_URL)
        if (url.isNullOrBlank()) {
            stopSelf(startId)
            return START_NOT_STICKY
        }

        notifications.ensureChannel()
        startInForeground(notifications.progress(title = null, percent = null))

        work?.cancel()
        work = scope.launch {
            coordinator.runQuickDownload(url) { title, percent -> publish(title, percent) }
            stopSelf(startId)
        }
        return START_NOT_STICKY
    }

    /**
     * Progress arrives many times a second from yt-dlp. Re-entering the foreground on each one is
     * both wasteful and refusable by the system, and an exception thrown here would travel back
     * into the thread reading yt-dlp's output and close it mid-download.
     */
    private fun publish(title: String?, percent: Int?) {
        val now = SystemClock.elapsedRealtime()
        if (now - lastUpdate < UPDATE_INTERVAL_MS) return
        lastUpdate = now
        runCatching { notifications.updateProgress(title, percent) }
    }

    private fun startInForeground(notification: android.app.Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ServiceCompat.startForeground(
                this,
                DownloadNotifications.PROGRESS_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
            )
        } else {
            startForeground(DownloadNotifications.PROGRESS_ID, notification)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        notifications.cancelProgress()
        scope.cancel()
    }

    companion object {
        private const val EXTRA_URL = "url"
        private const val UPDATE_INTERVAL_MS = 700L

        fun start(context: Context, url: String) {
            val intent = Intent(context, DownloadService::class.java).putExtra(EXTRA_URL, url)
            context.startForegroundService(intent)
        }
    }
}
