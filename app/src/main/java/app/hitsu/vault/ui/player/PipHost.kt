package app.hitsu.vault.ui.player

import android.app.PendingIntent
import android.app.PictureInPictureParams
import android.app.RemoteAction
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.Rect
import android.graphics.drawable.Icon
import android.util.Rational
import androidx.activity.ComponentActivity
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.core.content.ContextCompat
import app.hitsu.vault.R

/** What the player exposes to the floating window's buttons. */
interface PipHandler {
    fun onPlayPause()
    fun onSkip(forward: Boolean)
    fun onUserLeaving()
    fun onPictureInPictureChanged(inPictureInPicture: Boolean)
}

/**
 * Bridges the Compose player to the activity-level PiP APIs, which is where they live.
 *
 * Spec §8 allows three actions, so they are −10 s, play/pause and +10 s, and the params are pushed
 * again whenever playback starts or stops so the middle icon matches what the video is doing.
 */
class PipHost(private val activity: ComponentActivity) {

    var handler: PipHandler? = null

    private var aspectRatio: Rational? = null
    private var sourceRect: Rect? = null
    private var receiverRegistered = false

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.getIntExtra(EXTRA_ACTION, -1)) {
                ACTION_PLAY_PAUSE -> handler?.onPlayPause()
                ACTION_REWIND -> handler?.onSkip(forward = false)
                ACTION_FORWARD -> handler?.onSkip(forward = true)
            }
        }
    }

    val supported: Boolean
        get() = activity.packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)

    val inPictureInPicture: Boolean
        get() = activity.isInPictureInPictureMode

    fun describeVideo(width: Int, height: Int, bounds: Rect?) {
        aspectRatio = aspectRatioOf(width, height)
        sourceRect = bounds
    }

    fun enter(playing: Boolean) {
        if (!supported) return
        registerReceiver()
        activity.enterPictureInPictureMode(params(playing))
    }

    fun updateActions(playing: Boolean) {
        if (!supported || !inPictureInPicture) return
        activity.setPictureInPictureParams(params(playing))
    }

    /**
     * Spec §8: the vault locking means the window has to go. There is no API to step out of PiP
     * without coming to the front, so the window is closed outright rather than dragging the user
     * out of whatever they were doing; the lock screen is waiting when they come back.
     */
    fun close() {
        if (inPictureInPicture) activity.finishAndRemoveTask()
    }

    fun release() {
        handler = null
        if (receiverRegistered) {
            activity.unregisterReceiver(receiver)
            receiverRegistered = false
        }
    }

    private fun registerReceiver() {
        if (receiverRegistered) return
        ContextCompat.registerReceiver(
            activity,
            receiver,
            IntentFilter(ACTION_NAME),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        receiverRegistered = true
    }

    private fun params(playing: Boolean): PictureInPictureParams =
        PictureInPictureParams.Builder()
            .apply {
                aspectRatio?.let(::setAspectRatio)
                sourceRect?.let(::setSourceRectHint)
            }
            .setActions(
                listOf(
                    remoteAction(ACTION_REWIND, R.drawable.ic_pip_rewind, R.string.player_skip_back),
                    if (playing) {
                        remoteAction(ACTION_PLAY_PAUSE, R.drawable.ic_pip_pause, R.string.cd_pause)
                    } else {
                        remoteAction(ACTION_PLAY_PAUSE, R.drawable.ic_pip_play, R.string.cd_play)
                    },
                    remoteAction(ACTION_FORWARD, R.drawable.ic_pip_forward, R.string.player_skip_forward),
                ),
            )
            .build()

    private fun remoteAction(action: Int, iconRes: Int, titleRes: Int): RemoteAction {
        val title = activity.getString(titleRes)
        val intent = Intent(ACTION_NAME)
            .setPackage(activity.packageName)
            .putExtra(EXTRA_ACTION, action)
        val pending = PendingIntent.getBroadcast(
            activity,
            action,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return RemoteAction(Icon.createWithResource(activity, iconRes), title, title, pending)
    }

    /** Android refuses anything narrower than 1:2.39 or wider than 2.39:1. */
    private fun aspectRatioOf(width: Int, height: Int): Rational? {
        if (width <= 0 || height <= 0) return null
        val ratio = width.toFloat() / height
        val clamped = ratio.coerceIn(MIN_RATIO, MAX_RATIO)
        return Rational((clamped * RATIO_PRECISION).toInt(), RATIO_PRECISION)
    }

    private companion object {
        const val ACTION_NAME = "app.hitsu.vault.PIP_ACTION"
        const val EXTRA_ACTION = "action"
        const val ACTION_PLAY_PAUSE = 1
        const val ACTION_REWIND = 2
        const val ACTION_FORWARD = 3
        const val MIN_RATIO = 0.42f
        const val MAX_RATIO = 2.39f
        const val RATIO_PRECISION = 1000
    }
}

val LocalPipHost = staticCompositionLocalOf<PipHost?> { null }
