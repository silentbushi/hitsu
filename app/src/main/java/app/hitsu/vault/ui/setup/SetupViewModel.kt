package app.hitsu.vault.ui.setup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.hitsu.vault.domain.BiometricAvailability
import app.hitsu.vault.domain.PinPolicy
import app.hitsu.vault.domain.VaultGateway
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SetupViewModel @Inject constructor(
    private val vault: VaultGateway,
    biometrics: BiometricAvailability,
) : ViewModel() {

    private val pin = CharArray(PinPolicy.MAX_LENGTH)
    private val confirm = CharArray(PinPolicy.MAX_LENGTH)
    private var pinLength = 0
    private var confirmLength = 0

    private val _state = MutableStateFlow(
        biometrics.canEnroll().let { SetupUiState(biometricAvailable = it, biometricRequested = it) },
    )
    val state: StateFlow<SetupUiState> = _state.asStateFlow()

    fun onDigit(digit: Int) {
        val current = _state.value
        if (current.creating) return
        when (current.step) {
            SetupStep.Create -> {
                if (pinLength == PinPolicy.MAX_LENGTH) return
                pin[pinLength++] = '0' + digit
                _state.update { it.copy(entered = pinLength, mismatch = false) }
            }
            SetupStep.Confirm -> {
                if (confirmLength == pinLength) return
                confirm[confirmLength++] = '0' + digit
                _state.update { it.copy(entered = confirmLength) }
            }
        }
    }

    fun onBackspace() {
        val current = _state.value
        if (current.creating) return
        when (current.step) {
            SetupStep.Create -> {
                if (pinLength == 0) return
                pin[--pinLength] = '\u0000'
                _state.update { it.copy(entered = pinLength) }
            }
            SetupStep.Confirm -> {
                if (confirmLength == 0) return
                confirm[--confirmLength] = '\u0000'
                _state.update { it.copy(entered = confirmLength) }
            }
        }
    }

    fun onBiometricToggled(enabled: Boolean) {
        _state.update { if (it.biometricAvailable) it.copy(biometricRequested = enabled) else it }
    }

    fun onContinue() {
        val current = _state.value
        if (!current.canContinue) return
        when (current.step) {
            SetupStep.Create -> _state.update {
                it.copy(step = SetupStep.Confirm, entered = 0, confirmLength = pinLength, mismatch = false)
            }
            SetupStep.Confirm -> if (pinsMatch()) createVault() else restart(mismatch = true)
        }
    }

    fun onBack() = restart(mismatch = false)

    private fun pinsMatch(): Boolean =
        pinLength == confirmLength && (0 until pinLength).all { pin[it] == confirm[it] }

    private fun createVault() {
        val chosen = pin.copyOf(pinLength)
        val biometricRequested = _state.value.biometricRequested
        wipe()
        _state.update { it.copy(creating = true) }
        viewModelScope.launch {
            try {
                vault.create(chosen, biometricRequested)
            } finally {
                chosen.fill('\u0000')
            }
        }
    }

    private fun restart(mismatch: Boolean) {
        wipe()
        _state.update { it.copy(step = SetupStep.Create, entered = 0, confirmLength = 0, mismatch = mismatch) }
    }

    private fun wipe() {
        pin.fill('\u0000')
        confirm.fill('\u0000')
        pinLength = 0
        confirmLength = 0
    }

    override fun onCleared() = wipe()
}
