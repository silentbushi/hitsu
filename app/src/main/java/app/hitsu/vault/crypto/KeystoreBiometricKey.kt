package app.hitsu.vault.crypto

import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.security.keystore.StrongBoxUnavailableException
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Spec §5.2: the key that lets a fingerprint stand in for the PIN.
 *
 * It holds a second copy of the DEK, sealed with a Keystore key that will not do anything until the
 * user authenticates — the fingerprint does not *derive* anything, it authorises one use of a key
 * that is already in the device. That is why enabling this is only possible with the vault open: the
 * DEK has to be there to be sealed.
 *
 * The key is tied to the fingerprints enrolled at the time. Adding or removing one invalidates it,
 * and the vault falls back to the PIN rather than trusting a face or a finger that was added later;
 * [decryptCipher] reports that as [android.security.keystore.KeyPermanentlyInvalidatedException].
 */
/** Seals a second copy of the DEK behind a key that only a fingerprint authorises. */
interface BiometricKey {
    fun exists(): Boolean

    /** A fresh key each time this is enabled, so an old sealed DEK can never be opened again. */
    fun encryptCipher(): Cipher

    /** @throws android.security.keystore.KeyPermanentlyInvalidatedException if enrolment changed. */
    fun decryptCipher(iv: ByteArray): Cipher

    fun delete()
}

class KeystoreBiometricKey(private val alias: String) : BiometricKey {

    private val keyStore = KeyStore.getInstance(PROVIDER).apply { load(null) }

    override fun exists(): Boolean = keyStore.containsAlias(alias)

    override fun encryptCipher(): Cipher {
        delete()
        return Cipher.getInstance(AesGcm.TRANSFORMATION).apply { init(Cipher.ENCRYPT_MODE, generateKey()) }
    }

    override fun decryptCipher(iv: ByteArray): Cipher {
        val key = (keyStore.getEntry(alias, null) as? KeyStore.SecretKeyEntry)?.secretKey
            ?: throw IllegalStateException("No biometric key")
        return Cipher.getInstance(AesGcm.TRANSFORMATION).apply {
            init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(AesGcm.TAG_BITS, iv))
        }
    }

    override fun delete() {
        if (keyStore.containsAlias(alias)) keyStore.deleteEntry(alias)
    }

    private fun generateKey(): SecretKey = try {
        generateKey(strongBox = true)
    } catch (_: StrongBoxUnavailableException) {
        generateKey(strongBox = false)
    }

    private fun generateKey(strongBox: Boolean): SecretKey {
        val spec = KeyGenParameterSpec.Builder(
            alias,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(AesGcm.KEY_BYTES * 8)
            .setRandomizedEncryptionRequired(true)
            .setUserAuthenticationRequired(true)
            .setInvalidatedByBiometricEnrollment(true)
            .setIsStrongBoxBacked(strongBox)
            .apply {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    // One authentication authorises one use, and only a strong biometric counts.
                    setUserAuthenticationParameters(0, KeyProperties.AUTH_BIOMETRIC_STRONG)
                } else {
                    @Suppress("DEPRECATION") // The API 29 spelling of the same thing.
                    setUserAuthenticationValidityDurationSeconds(-1)
                }
            }
            .build()
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, PROVIDER)
            .apply { init(spec) }
            .generateKey()
    }

    private companion object {
        const val PROVIDER = "AndroidKeyStore"
    }
}
