package app.hitsu.vault.crypto

import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * AES-256-GCM (spec §2.1). Payload layout is `iv || ciphertext || tag`; the IV is kept in the clear
 * because GCM only needs it to be unique, not secret. The provider generates it: Keystore keys are
 * created with randomized encryption required and reject a caller-supplied IV outright.
 */
object AesGcm {
    const val KEY_BYTES = 32
    internal const val IV_BYTES = 12
    internal const val TAG_BITS = 128
    internal const val TRANSFORMATION = "AES/GCM/NoPadding"

    private val random = SecureRandom()

    fun encrypt(key: ByteArray, plaintext: ByteArray): ByteArray = encrypt(key.toSecretKey(), plaintext)

    fun decrypt(key: ByteArray, payload: ByteArray): ByteArray = decrypt(key.toSecretKey(), payload)

    fun encrypt(key: SecretKey, plaintext: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.ENCRYPT_MODE, key) }
        val iv = cipher.iv
        check(iv.size == IV_BYTES) { "Unexpected IV size" }
        return iv + cipher.doFinal(plaintext)
    }

    fun decrypt(key: SecretKey, payload: ByteArray): ByteArray {
        require(payload.size > IV_BYTES) { "Payload too short" }
        val cipher = Cipher.getInstance(TRANSFORMATION).apply {
            init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_BITS, payload, 0, IV_BYTES))
        }
        return cipher.doFinal(payload, IV_BYTES, payload.size - IV_BYTES)
    }

    fun newKey(): ByteArray = ByteArray(KEY_BYTES).also(random::nextBytes)

    private fun ByteArray.toSecretKey(): SecretKey {
        require(size == KEY_BYTES) { "Key must be 256 bit" }
        return SecretKeySpec(this, "AES")
    }
}
