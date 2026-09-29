package app.hitsu.vault

import android.graphics.Color
import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.fragment.app.FragmentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.hitsu.vault.data.MediaRepository
import app.hitsu.vault.data.download.PendingLinks
import app.hitsu.vault.domain.AutoLock
import app.hitsu.vault.domain.VaultGateway
import app.hitsu.vault.domain.VaultState
import app.hitsu.vault.ui.nav.HitsuNavHost
import app.hitsu.vault.ui.player.LocalPipHost
import app.hitsu.vault.ui.player.PipHost
import app.hitsu.vault.ui.theme.HitsuTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : FragmentActivity() {

    @Inject
    lateinit var vault: VaultGateway

    @Inject
    lateinit var autoLock: AutoLock

    @Inject
    lateinit var mediaRepository: MediaRepository

    @Inject
    lateinit var pendingLinks: PendingLinks

    private val pipHost by lazy { PipHost(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        stageSharedMedia(intent)
        setContent {
            HitsuTheme {
                AskForNotificationsOnce()
                val vaultState by vault.state.collectAsStateWithLifecycle()
                // Spec §8: a locked vault cannot keep playing in a floating window.
                LaunchedEffect(vaultState) {
                    if (vaultState != VaultState.Unlocked) pipHost.close()
                }
                val pendingLink by pendingLinks.link.collectAsStateWithLifecycle()
                CompositionLocalProvider(LocalPipHost provides pipHost) {
                    HitsuNavHost(
                        vaultState = vaultState,
                        pendingLink = pendingLink,
                        onLinkHandled = { pendingLinks.take() },
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        stageSharedMedia(intent)
    }

    /**
     * Spec §7.4: copy what was shared before anything else. The app that shared it may be gone by
     * the time the PIN is entered, and its permission grant goes with it.
     */
    private fun stageSharedMedia(intent: Intent?) {
        if (intent?.action == Intent.ACTION_SEND && intent.type?.startsWith("text/") == true) {
            intent.getStringExtra(Intent.EXTRA_TEXT)?.let(pendingLinks::offer)
            return
        }
        val uris = when (intent?.action) {
            Intent.ACTION_SEND -> listOfNotNull(intent.mediaUri(Intent.EXTRA_STREAM))
            Intent.ACTION_SEND_MULTIPLE ->
                intent.mediaUris(Intent.EXTRA_STREAM) ?: emptyList()
            else -> return
        }.ifEmpty { intent.clipUris() }
        if (uris.isNotEmpty()) mediaRepository.stageShared(uris)
    }

    /**
     * Not every app fills EXTRA_STREAM: the share sheet carries the same URIs in the clip data, and
     * some galleries send only those. They are the same grant, so they are staged the same way.
     */
    private fun Intent?.clipUris(): List<Uri> {
        val clip = this?.clipData ?: return emptyList()
        return (0 until clip.itemCount).mapNotNull { clip.getItemAt(it).uri }
    }

    @Suppress("DEPRECATION")
    private fun Intent.mediaUri(key: String): Uri? =
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            getParcelableExtra(key, Uri::class.java)
        } else {
            getParcelableExtra(key)
        }

    @Suppress("DEPRECATION")
    private fun Intent.mediaUris(key: String): List<Uri>? =
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            getParcelableArrayListExtra(key, Uri::class.java)
        } else {
            getParcelableArrayListExtra(key)
        }

    /** Spec §8: leaving the app without asking for PiP pauses instead of playing on unseen. */
    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (!isInPictureInPictureMode) pipHost.handler?.onUserLeaving()
    }

    override fun onPictureInPictureModeChanged(
        isInPictureInPictureMode: Boolean,
        newConfig: Configuration,
    ) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        pipHost.handler?.onPictureInPictureChanged(isInPictureInPictureMode)
    }

    override fun onDestroy() {
        super.onDestroy()
        pipHost.release()
    }

    /** The quick download reports through a notification, so the permission is asked for here. */
    @androidx.compose.runtime.Composable
    private fun AskForNotificationsOnce() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val launcher = rememberLauncherForActivityResult(
            ActivityResultContracts.RequestPermission(),
        ) { }
        LaunchedEffect(Unit) {
            val granted = ContextCompat.checkSelfPermission(
                this@MainActivity,
                Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    override fun onStart() {
        super.onStart()
        // Back on screen within the grace period: the pending lock is called off.
        autoLock.onForegrounded()
    }

    override fun onStop() {
        super.onStop()
        /*
         * Entering PiP only pauses the activity, so this runs when the app really goes away: either
         * backgrounded outright, or the floating window was dismissed. Whether that locks now or
         * after the configured delay is AutoLock's call; the cleanup follows the vault state.
         */
        autoLock.onBackgrounded(isChangingConfigurations)
    }
}
