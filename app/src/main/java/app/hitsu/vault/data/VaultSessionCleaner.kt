package app.hitsu.vault.data

import android.content.Context
import app.hitsu.vault.domain.VaultGateway
import app.hitsu.vault.domain.VaultState
import coil3.SingletonImageLoader
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch

/**
 * Spec §5.4: everything decrypted goes when the vault closes. The lock can now fire on a timer with
 * the app in the background, so the cleanup follows the vault's own state rather than a screen.
 */
class VaultSessionCleaner(
    private val context: Context,
    private val vault: VaultGateway,
    private val repository: MediaRepository,
    private val scope: CoroutineScope,
) {
    fun start() {
        scope.launch {
            vault.state
                .filter { it == VaultState.Locked }
                .collect {
                    repository.wipePlayback()
                    repository.wipeShared()
                    SingletonImageLoader.get(context).memoryCache?.clear()
                }
        }
    }
}
