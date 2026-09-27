package app.hitsu.vault.domain

import app.hitsu.vault.di.ApplicationScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Spec §5.5: the vault closes when the app leaves the screen, after the delay the user picked.
 *
 * The countdown runs in the background rather than on the way back, so the key spends no longer in
 * memory than was asked for, whether or not the app is ever reopened. Rotation is not leaving, and
 * the system photo picker is a hop the user asked for, so neither starts it.
 */
@Singleton
class AutoLock @Inject constructor(
    private val vault: VaultGateway,
    @ApplicationScope private val scope: CoroutineScope,
) {

    private val skipNextBackground = AtomicBoolean(false)
    private var pending: Job? = null

    fun allowNextBackground() {
        skipNextBackground.set(true)
    }

    fun onBackgrounded(changingConfiguration: Boolean) {
        if (changingConfiguration) return
        if (skipNextBackground.getAndSet(false)) return

        pending?.cancel()
        val timeout = vault.lockTimeoutMillis
        if (timeout <= 0L) {
            vault.lock()
            return
        }
        pending = scope.launch {
            delay(timeout)
            vault.lock()
        }
    }

    fun onForegrounded() {
        pending?.cancel()
        pending = null
    }
}
