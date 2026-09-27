package app.hitsu.vault.data

import app.hitsu.vault.domain.PinKind
import kotlinx.serialization.Serializable

/**
 * Spec §14. Everything here is either ciphertext or non-secret KDF bookkeeping: the salt and the
 * iteration count are public by design, and the wrapped DEK is useless without the Keystore key.
 */
@Serializable
data class VaultMeta(
    val version: Int = CURRENT_VERSION,
    val wrappedDek: String,
    val kdfSalt: String,
    val kdfAlgorithm: String,
    val kdfIterations: Int,
    val pinKind: PinKind,
    val pinLength: Int,
    val biometricRequested: Boolean = false,
    val biometricEnabled: Boolean = false,
    val failedUnlocks: Int = 0,
    val retryAtMillis: Long = 0L,
    val lockTimeoutMillis: Long = 0L,
) {
    companion object {
        const val CURRENT_VERSION = 1
    }
}
