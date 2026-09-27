package app.hitsu.vault.domain

import app.hitsu.vault.crypto.VaultCipher
import kotlinx.coroutines.flow.StateFlow

enum class VaultState { Unknown, Absent, Locked, Unlocked }

enum class PinKind { Numeric, Passphrase }

sealed interface UnlockResult {
    data object Success : UnlockResult

    /** [retryAtMillis] is 0 when no throttle applies. */
    data class Rejected(val failedAttempts: Int, val retryAtMillis: Long) : UnlockResult
}

interface VaultGateway {
    val state: StateFlow<VaultState>
    val pinLength: Int
    val biometricRequested: Boolean
    val retryAtMillis: Long

    /** 0 means the vault closes the moment the app leaves the screen (spec §5.5). */
    val lockTimeoutMillis: Long

    /** Non-null only while the vault is unlocked. */
    val cipher: VaultCipher?

    suspend fun create(pin: CharArray, biometricRequested: Boolean)

    suspend fun unlock(pin: CharArray): UnlockResult

    suspend fun setLockTimeout(millis: Long)

    fun lock()
}
