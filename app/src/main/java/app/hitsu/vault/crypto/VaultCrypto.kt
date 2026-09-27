package app.hitsu.vault.crypto

import app.hitsu.vault.data.VaultMeta
import app.hitsu.vault.domain.PinKind
import java.security.GeneralSecurityException
import java.security.SecureRandom

class CreatedVault(val meta: VaultMeta, val dek: ByteArray)

sealed interface PinChange {
    class Changed(val meta: VaultMeta) : PinChange

    data object WrongPin : PinChange

    data object Unrecoverable : PinChange
}

sealed interface VaultUnlock {
    class Success(val dek: ByteArray) : VaultUnlock

    data object WrongPin : VaultUnlock

    /** The Keystore key is gone (app data cleared, device restored): no PIN can open this vault. */
    data object Unrecoverable : VaultUnlock
}

/**
 * Key hierarchy (spec §5.1): a random 256-bit DEK is sealed with a KEK derived from the PIN, and
 * that result is sealed again with the Keystore key. Unlocking therefore needs both the PIN and
 * this device; changing the PIN only rewraps the inner layer, never the media.
 */
class VaultCrypto(
    private val keyWrapper: KeyWrapper,
    private val iterations: Int = Pbkdf2.DEFAULT_ITERATIONS,
    private val random: SecureRandom = SecureRandom(),
) {

    fun createVault(pin: CharArray, pinKind: PinKind, biometricRequested: Boolean): CreatedVault {
        val dek = ByteArray(AesGcm.KEY_BYTES).also(random::nextBytes)
        val salt = ByteArray(Pbkdf2.SALT_BYTES).also(random::nextBytes)
        val kek = Pbkdf2.derive(pin, salt, iterations)
        val wrappedDek = try {
            keyWrapper.wrap(AesGcm.encrypt(kek, dek))
        } finally {
            kek.fill(0)
        }
        val meta = VaultMeta(
            wrappedDek = wrappedDek.encodeBase64(),
            kdfSalt = salt.encodeBase64(),
            kdfAlgorithm = Pbkdf2.ALGORITHM,
            kdfIterations = iterations,
            pinKind = pinKind,
            pinLength = pin.size,
            biometricRequested = biometricRequested,
        )
        return CreatedVault(meta, dek)
    }

    /**
     * Spec §5.1: changing the PIN rewraps the inner layer and nothing else. The DEK does not change,
     * so no object is re-encrypted and the copy a fingerprint unseals (§5.2) keeps working — what was
     * encrypted before the change opens after it. A fresh salt comes with the new PIN, because reusing
     * the old one would leave the two derivations related for no reason.
     */
    fun changePin(currentPin: CharArray, newPin: CharArray, meta: VaultMeta): PinChange {
        val dek = when (val unlocked = unlock(currentPin, meta)) {
            is VaultUnlock.Success -> unlocked.dek
            VaultUnlock.WrongPin -> return PinChange.WrongPin
            VaultUnlock.Unrecoverable -> return PinChange.Unrecoverable
        }
        val salt = ByteArray(Pbkdf2.SALT_BYTES).also(random::nextBytes)
        val kek = Pbkdf2.derive(newPin, salt, iterations)
        val wrapped = try {
            keyWrapper.wrap(AesGcm.encrypt(kek, dek))
        } finally {
            kek.fill(0)
            dek.fill(0)
        }
        return PinChange.Changed(
            meta.copy(
                wrappedDek = wrapped.encodeBase64(),
                kdfSalt = salt.encodeBase64(),
                kdfIterations = iterations,
                pinLength = newPin.size,
                failedUnlocks = 0,
                retryAtMillis = 0L,
            ),
        )
    }

    fun unlock(pin: CharArray, meta: VaultMeta): VaultUnlock {
        val sealed = try {
            keyWrapper.unwrap(meta.wrappedDek.decodeBase64())
        } catch (_: GeneralSecurityException) {
            return VaultUnlock.Unrecoverable
        }
        val kek = Pbkdf2.derive(pin, meta.kdfSalt.decodeBase64(), meta.kdfIterations)
        return try {
            VaultUnlock.Success(AesGcm.decrypt(kek, sealed))
        } catch (_: GeneralSecurityException) {
            VaultUnlock.WrongPin
        } finally {
            kek.fill(0)
            sealed.fill(0)
        }
    }
}
