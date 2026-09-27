package app.hitsu.vault.ui.setup

import app.hitsu.vault.domain.PinPolicy

enum class SetupStep { Create, Confirm }

data class SetupUiState(
    val step: SetupStep = SetupStep.Create,
    val entered: Int = 0,
    val confirmLength: Int = 0,
    val mismatch: Boolean = false,
    val biometricAvailable: Boolean = false,
    val biometricRequested: Boolean = false,
    val creating: Boolean = false,
) {
    val dotSlots: Int
        get() = when (step) {
            SetupStep.Create -> entered.coerceIn(PinPolicy.MIN_LENGTH, PinPolicy.MAX_LENGTH)
            SetupStep.Confirm -> confirmLength
        }

    val canContinue: Boolean
        get() = !creating && when (step) {
            SetupStep.Create -> PinPolicy.isValid(entered)
            SetupStep.Confirm -> entered == confirmLength
        }
}
