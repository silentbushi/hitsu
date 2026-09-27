package app.hitsu.vault.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.hitsu.vault.domain.PinPolicy
import app.hitsu.vault.domain.VaultGateway
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class ChangePinStep { Current, New, Confirm }

data class ChangePinUiState(
    val step: ChangePinStep = ChangePinStep.Current,
    val entered: Int = 0,
    val currentLength: Int = 0,
    val newLength: Int = 0,
    val wrongCurrent: Boolean = false,
    val mismatch: Boolean = false,
    val working: Boolean = false,
    val done: Boolean = false,
) {
    val dotSlots: Int
        get() = when (step) {
            ChangePinStep.Current -> currentLength
            ChangePinStep.New -> entered.coerceIn(PinPolicy.MIN_LENGTH, PinPolicy.MAX_LENGTH)
            ChangePinStep.Confirm -> newLength
        }

    val canContinue: Boolean
        get() = !working && when (step) {
            ChangePinStep.Current -> entered == currentLength
            ChangePinStep.New -> PinPolicy.isValid(entered)
            ChangePinStep.Confirm -> entered == newLength
        }
}

/**
 * Three steps, and the PINs live in char arrays that are wiped as soon as they are used or left
 * behind: the current one to prove who is asking, the new one twice so a typo cannot lock the vault
 * against its owner.
 */
@HiltViewModel
class ChangePinViewModel @Inject constructor(
    private val vault: VaultGateway,
) : ViewModel() {

    private val current = CharArray(PinPolicy.MAX_LENGTH)
    private val fresh = CharArray(PinPolicy.MAX_LENGTH)
    private val repeat = CharArray(PinPolicy.MAX_LENGTH)
    private var currentCount = 0
    private var freshCount = 0
    private var repeatCount = 0

    private val _state = MutableStateFlow(ChangePinUiState(currentLength = vault.pinLength))
    val state: StateFlow<ChangePinUiState> = _state.asStateFlow()

    fun onDigit(digit: Int) {
        val now = _state.value
        if (now.working) return
        when (now.step) {
            ChangePinStep.Current -> {
                if (currentCount == now.currentLength) return
                current[currentCount++] = '0' + digit
                _state.update { it.copy(entered = currentCount, wrongCurrent = false) }
            }
            ChangePinStep.New -> {
                if (freshCount == PinPolicy.MAX_LENGTH) return
                fresh[freshCount++] = '0' + digit
                _state.update { it.copy(entered = freshCount, mismatch = false) }
            }
            ChangePinStep.Confirm -> {
                if (repeatCount == freshCount) return
                repeat[repeatCount++] = '0' + digit
                _state.update { it.copy(entered = repeatCount) }
            }
        }
    }

    fun onBackspace() {
        val now = _state.value
        if (now.working) return
        when (now.step) {
            ChangePinStep.Current -> if (currentCount > 0) {
                current[--currentCount] = '\u0000'
                _state.update { it.copy(entered = currentCount) }
            }
            ChangePinStep.New -> if (freshCount > 0) {
                fresh[--freshCount] = '\u0000'
                _state.update { it.copy(entered = freshCount) }
            }
            ChangePinStep.Confirm -> if (repeatCount > 0) {
                repeat[--repeatCount] = '\u0000'
                _state.update { it.copy(entered = repeatCount) }
            }
        }
    }

    fun onContinue() {
        val now = _state.value
        if (!now.canContinue) return
        when (now.step) {
            ChangePinStep.Current -> _state.update {
                it.copy(step = ChangePinStep.New, entered = 0)
            }
            ChangePinStep.New -> _state.update {
                it.copy(step = ChangePinStep.Confirm, entered = 0, newLength = freshCount)
            }
            ChangePinStep.Confirm -> if (pinsMatch()) change() else backToNew(mismatch = true)
        }
    }

    private fun pinsMatch(): Boolean =
        freshCount == repeatCount && (0 until freshCount).all { fresh[it] == repeat[it] }

    private fun change() {
        _state.update { it.copy(working = true) }
        viewModelScope.launch {
            val changed = vault.changePin(current.copyOf(currentCount), fresh.copyOf(freshCount))
            if (changed) {
                wipe()
                _state.update { ChangePinUiState(done = true) }
            } else {
                // Only the current PIN can be at fault here; the new one was just confirmed.
                wipe()
                _state.value = ChangePinUiState(
                    currentLength = vault.pinLength,
                    wrongCurrent = true,
                )
            }
        }
    }

    private fun backToNew(mismatch: Boolean) {
        fresh.fill('\u0000')
        repeat.fill('\u0000')
        freshCount = 0
        repeatCount = 0
        _state.update {
            it.copy(step = ChangePinStep.New, entered = 0, newLength = 0, mismatch = mismatch)
        }
    }

    private fun wipe() {
        current.fill('\u0000')
        fresh.fill('\u0000')
        repeat.fill('\u0000')
        currentCount = 0
        freshCount = 0
        repeatCount = 0
    }

    override fun onCleared() = wipe()
}
