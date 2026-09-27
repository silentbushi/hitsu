package app.hitsu.vault.ui.lock

import app.hitsu.vault.domain.PinPolicy

sealed interface PinEntry {
    data object Idle : PinEntry
    data object Verifying : PinEntry
    data class Wrong(val attempt: Int, val maxAttempts: Int) : PinEntry
    data class Throttled(val secondsLeft: Int) : PinEntry
}

enum class BiometricState {
    Unavailable,
    Available,
    Requested,
    NotRecognized,
    Invalidated,
}

data class LockUiState(
    val pinLength: Int = PinPolicy.MIN_LENGTH,
    val entered: Int = 0,
    val entry: PinEntry = PinEntry.Idle,
    val biometric: BiometricState = BiometricState.Unavailable,
) {
    val biometricSheetVisible: Boolean
        get() = biometric == BiometricState.Requested ||
            biometric == BiometricState.NotRecognized ||
            biometric == BiometricState.Invalidated

    val acceptsInput: Boolean
        get() = !biometricSheetVisible && entry != PinEntry.Verifying && entry !is PinEntry.Throttled
}
