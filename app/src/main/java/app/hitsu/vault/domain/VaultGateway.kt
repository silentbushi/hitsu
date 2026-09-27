package app.hitsu.vault.domain

import app.hitsu.vault.crypto.VaultCipher
import kotlinx.coroutines.flow.StateFlow
import javax.crypto.Cipher

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

    /** True when a fingerprint can open this vault, which needs a key that is still valid. */
    val biometricEnabled: Boolean

    /** A cipher for sealing the DEK, for the prompt to authorise. Null if no key can be made. */
    fun biometricEnrollCipher(): Cipher?

    /**
     * A cipher for unsealing it. Null when the key is gone or was invalidated by a change in the
     * enrolled fingerprints, which is the vault saying: use the PIN.
     */
    fun biometricUnlockCipher(): Cipher?

    /** Seals the open vault's DEK with [cipher], which a fingerprint has just authorised. */
    suspend fun enableBiometric(cipher: Cipher): Boolean

    /** Opens the vault with the DEK that [cipher] unseals. */
    suspend fun unlockWithBiometric(cipher: Cipher): Boolean

    suspend fun forgetBiometric()

    fun lock()
}
