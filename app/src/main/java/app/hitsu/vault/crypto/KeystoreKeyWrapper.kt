package app.hitsu.vault.crypto

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.security.keystore.StrongBoxUnavailableException
import java.security.KeyStore
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

/**
 * The outer wrap of the DEK. The key is generated inside the Keystore and cannot be exported, so a
 * copy of the vault files is useless off the device: the PIN alone never unwraps anything.
 * Uninstalling the app destroys this key, which is why uninstall destroys the vault (spec §2.9).
 */
class KeystoreKeyWrapper(private val alias: String) : KeyWrapper {

    private val keyStore = KeyStore.getInstance(PROVIDER).apply { load(null) }

    override fun wrap(plaintext: ByteArray): ByteArray = AesGcm.encrypt(key(), plaintext)

    override fun unwrap(payload: ByteArray): ByteArray = AesGcm.decrypt(key(), payload)

    private fun key(): SecretKey {
        val existing = (keyStore.getEntry(alias, null) as? KeyStore.SecretKeyEntry)?.secretKey
        return existing ?: generateKey()
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
            .setIsStrongBoxBacked(strongBox)
            .build()
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, PROVIDER)
            .apply { init(spec) }
            .generateKey()
    }

    private companion object {
        const val PROVIDER = "AndroidKeyStore"
    }
}
