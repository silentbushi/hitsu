package app.hitsu.vault.data

import app.hitsu.vault.domain.VaultGateway
import app.hitsu.vault.domain.VaultState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/**
 * Spec §7.4: seals whatever another app shared in as soon as the vault is open, no matter which
 * screen Hitsu is on.
 *
 * Home used to start this from its ViewModel's init, which only runs the first time home is built.
 * A share that arrived with Hitsu already open reaches [android.app.Activity.onNewIntent] without
 * creating anything, so the bytes were staged and then sat in the cache until the vault locked and
 * wiped them. Following the vault's state instead also covers the cold start where the copy is still
 * running when home appears.
 */
class SharedImportWatcher(
    private val vault: VaultGateway,
    private val repository: MediaRepository,
    private val scope: CoroutineScope,
) {
    fun start() {
        scope.launch {
            combine(
                vault.state,
                repository.sharedWaiting,
                repository.importStatus,
            ) { state, waiting, status ->
                // An import already running owns the pipeline; its end re-emits here and picks this up.
                state == VaultState.Unlocked && waiting && !status.running
            }.collect { ready -> if (ready) repository.importShared() }
        }
    }
}
