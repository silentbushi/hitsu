package app.hitsu.vault.ui.settings

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.hitsu.vault.R
import app.hitsu.vault.data.MediaRepository
import app.hitsu.vault.data.SlideshowPreferences
import app.hitsu.vault.data.download.YtDlpEngine
import app.hitsu.vault.domain.BiometricAvailability
import app.hitsu.vault.domain.LOCK_TIMEOUT_NEVER
import app.hitsu.vault.domain.VaultGateway
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.crypto.Cipher
import javax.inject.Inject

/** Spec §5.5 plus the fifteen minutes the mockups added. */
enum class AutoLockChoice(val millis: Long, @StringRes val label: Int) {
    Immediate(0L, R.string.auto_lock_immediate),
    TenSeconds(10_000L, R.string.auto_lock_10s),
    ThirtySeconds(30_000L, R.string.auto_lock_30s),
    OneMinute(60_000L, R.string.auto_lock_1m),
    FiveMinutes(5 * 60_000L, R.string.auto_lock_5m),
    FifteenMinutes(15 * 60_000L, R.string.auto_lock_15m),

    /** The vault stays open until it is closed by hand or the process dies (spec §5.5). */
    Never(LOCK_TIMEOUT_NEVER, R.string.auto_lock_never),
    ;

    /** Long enough that someone holding the phone could walk into an open vault. */
    val needsConfirmation: Boolean get() = this == Never || millis >= FiveMinutes.millis

    companion object {
        fun of(millis: Long): AutoLockChoice =
            entries.firstOrNull { it.millis == millis } ?: Immediate
    }
}

data class SettingsUiState(
    val autoLock: AutoLockChoice = AutoLockChoice.Immediate,
    val vaultBytes: Long = 0L,
    val ytDlpVersion: String? = null,
    val pendingConfirmation: AutoLockChoice? = null,
    val biometricEnabled: Boolean = false,
    val biometricAvailable: Boolean = false,
    val slideshowInterval: SlideshowInterval = SlideshowInterval.FiveSeconds,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val vault: VaultGateway,
    private val repository: MediaRepository,
    private val ytDlp: YtDlpEngine,
    private val biometrics: BiometricAvailability,
    private val slideshow: SlideshowPreferences,
) : ViewModel() {

    private val _state = MutableStateFlow(
        SettingsUiState(
            autoLock = AutoLockChoice.of(vault.lockTimeoutMillis),
            biometricEnabled = vault.biometricEnabled,
            biometricAvailable = biometrics.canEnroll(),
            slideshowInterval = SlideshowInterval.of(slideshow.secondsPerPhoto),
        ),
    )
    val state: StateFlow<SettingsUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            _state.update { it.copy(vaultBytes = repository.vaultSizeBytes()) }
        }
        viewModelScope.launch {
            _state.update { it.copy(ytDlpVersion = ytDlp.version()) }
        }
    }

    fun onAutoLockPicked(choice: AutoLockChoice) {
        if (choice == _state.value.autoLock) return
        if (choice.needsConfirmation) {
            _state.update { it.copy(pendingConfirmation = choice) }
        } else {
            apply(choice)
        }
    }

    fun onAutoLockConfirmed() {
        val pending = _state.value.pendingConfirmation ?: return
        apply(pending)
    }

    fun onAutoLockDismissed() {
        _state.update { it.copy(pendingConfirmation = null) }
    }

    private fun apply(choice: AutoLockChoice) {
        _state.update { it.copy(autoLock = choice, pendingConfirmation = null) }
        viewModelScope.launch { vault.setLockTimeout(choice.millis) }
    }
    /** The slideshow screen owns that preference, so the row catches up on the way back. */
    fun refreshSlideshow() {
        _state.update { it.copy(slideshowInterval = SlideshowInterval.of(slideshow.secondsPerPhoto)) }
    }

    /** Spec §5.5: the way out of «Nunca», and of handing the unlocked phone to someone. */
    fun onLockNow() = vault.lock()

    /** The cipher the prompt has to authorise before the DEK can be sealed for a fingerprint. */
    fun enrollCipher(): Cipher? = vault.biometricEnrollCipher()

    fun onBiometricAuthorised(cipher: Cipher) {
        viewModelScope.launch {
            val enabled = vault.enableBiometric(cipher)
            _state.update { it.copy(biometricEnabled = enabled) }
        }
    }

    fun onBiometricDisabled() {
        viewModelScope.launch {
            vault.forgetBiometric()
            _state.update { it.copy(biometricEnabled = false) }
        }
    }

}
