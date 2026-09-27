package app.hitsu.vault.ui.settings

import android.net.Uri
import androidx.lifecycle.ViewModel
import app.hitsu.vault.data.BackupStatus
import app.hitsu.vault.data.MediaRepository
import app.hitsu.vault.domain.AutoLock
import app.hitsu.vault.domain.Clock
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.inject.Inject

@HiltViewModel
class BackupViewModel @Inject constructor(
    private val repository: MediaRepository,
    private val autoLock: AutoLock,
    private val clock: Clock,
) : ViewModel() {

    val state: StateFlow<BackupStatus> = repository.backupStatus

    /**
     * Held between the passphrase dialog and the file picker coming back, and handed over to the
     * repository, which wipes it. Cancelling the picker wipes it here instead.
     */
    private var pending: CharArray? = null

    fun onPassphraseEntered(passphrase: CharArray) {
        pending?.fill('\u0000')
        pending = passphrase
    }

    fun onPickerCancelled() {
        pending?.fill('\u0000')
        pending = null
    }

    /** The file picker backgrounds the app; without this the vault would close behind it. */
    fun onPickerLaunching() = autoLock.allowNextBackground()

    fun onTargetPicked(target: Uri?) {
        val passphrase = pending ?: return
        pending = null
        if (target == null) {
            passphrase.fill('\u0000')
            return
        }
        repository.createBackup(target, passphrase)
    }

    fun onSourcePicked(source: Uri?) {
        val passphrase = pending ?: return
        pending = null
        if (source == null) {
            passphrase.fill('\u0000')
            return
        }
        repository.restoreBackup(source, passphrase)
    }

    fun onStatusSeen() = repository.clearBackupStatus()

    fun suggestedName(): String = NAME_PREFIX +
        FORMAT.format(Instant.ofEpochMilli(clock.now()).atZone(ZoneId.systemDefault())) + EXTENSION

    override fun onCleared() {
        pending?.fill('\u0000')
        pending = null
    }

    private companion object {
        const val NAME_PREFIX = "hitsu-"
        const val EXTENSION = ".hitsubak"
        val FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
    }
}
