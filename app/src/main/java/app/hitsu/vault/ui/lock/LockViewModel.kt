package app.hitsu.vault.ui.lock

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.hitsu.vault.domain.Clock
import app.hitsu.vault.domain.UnlockResult
import app.hitsu.vault.domain.UnlockThrottle
import app.hitsu.vault.domain.VaultGateway
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.crypto.Cipher
import javax.inject.Inject

@HiltViewModel
class LockViewModel @Inject constructor(
    private val vault: VaultGateway,
    private val clock: Clock,
) : ViewModel() {

    private val buffer = CharArray(vault.pinLength)
    private var count = 0
    private var countdown: Job? = null

    private val _state = MutableStateFlow(
        LockUiState(
            pinLength = vault.pinLength,
            biometric = if (vault.biometricEnabled) BiometricState.Available else BiometricState.Unavailable,
        ),
    )
    val state: StateFlow<LockUiState> = _state.asStateFlow()

    init {
        if (vault.retryAtMillis > clock.now()) startCountdown(vault.retryAtMillis)
    }

    /** The cipher the prompt has to authorise, or null when the key no longer opens anything. */
    fun unlockCipher(): Cipher? = vault.biometricUnlockCipher()

    fun onBiometricUnavailable() {
        _state.update { it.copy(biometric = BiometricState.Invalidated) }
    }

    fun onBiometricNotRecognised() {
        _state.update { it.copy(biometric = BiometricState.NotRecognized) }
    }

    fun onBiometricAuthorised(cipher: Cipher) {
        viewModelScope.launch {
            if (!vault.unlockWithBiometric(cipher)) {
                _state.update { it.copy(biometric = BiometricState.Invalidated) }
            }
        }
    }

    fun onDigit(digit: Int) {
        if (!_state.value.acceptsInput || count == buffer.size) return
        buffer[count++] = '0' + digit
        _state.update { it.copy(entered = count, entry = PinEntry.Idle) }
        if (count == buffer.size) submit()
    }

    fun onBackspace() {
        val current = _state.value
        if (!current.acceptsInput) return
        if (current.entry is PinEntry.Wrong) {
            _state.update { it.copy(entered = 0, entry = PinEntry.Idle) }
            return
        }
        if (count == 0) return
        buffer[--count] = '\u0000'
        _state.update { it.copy(entered = count) }
    }

    fun onUseBiometric() {
        if (_state.value.biometric == BiometricState.Available) {
            _state.update { it.copy(biometric = BiometricState.Requested) }
        }
    }

    fun onUsePin() {
        _state.update {
            when (it.biometric) {
                BiometricState.Invalidated -> it.copy(biometric = BiometricState.Unavailable)
                BiometricState.Requested, BiometricState.NotRecognized -> it.copy(biometric = BiometricState.Available)
                else -> it
            }
        }
    }

    private fun submit() {
        val attempt = buffer.copyOf()
        buffer.fill('\u0000')
        count = 0
        _state.update { it.copy(entry = PinEntry.Verifying) }
        viewModelScope.launch {
            val result = try {
                vault.unlock(attempt)
            } finally {
                attempt.fill('\u0000')
            }
            when (result) {
                UnlockResult.Success -> Unit
                is UnlockResult.Rejected ->
                    if (result.retryAtMillis > clock.now()) {
                        startCountdown(result.retryAtMillis)
                    } else {
                        _state.update {
                            it.copy(entry = PinEntry.Wrong(result.failedAttempts, UnlockThrottle.MAX_ATTEMPTS))
                        }
                    }
            }
        }
    }

    private fun startCountdown(retryAtMillis: Long) {
        countdown?.cancel()
        countdown = viewModelScope.launch {
            while (true) {
                val left = retryAtMillis - clock.now()
                if (left <= 0) break
                _state.update { it.copy(entered = 0, entry = PinEntry.Throttled(((left + 999) / 1000).toInt())) }
                delay(minOf(left, 1_000L))
            }
            _state.update { it.copy(entry = PinEntry.Idle) }
        }
    }

    override fun onCleared() = buffer.fill('\u0000')
}
