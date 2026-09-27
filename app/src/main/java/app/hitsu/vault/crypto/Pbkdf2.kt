package app.hitsu.vault.crypto

import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * KDF choice (spec §4): PBKDF2-HmacSHA256 with 310k iterations, not Argon2id. Argon2 would pull in
 * a native library, and against a 6-digit PIN no KDF cost makes offline guessing hard anyway — what
 * actually stops it is that the wrapped DEK is sealed a second time with a non-exportable Keystore
 * key (see [app.hitsu.vault.crypto.VaultCrypto]), so an attacker with the files but not the device
 * has nothing to guess against.
 */
object Pbkdf2 {
    const val ALGORITHM = "PBKDF2WithHmacSHA256"
    const val DEFAULT_ITERATIONS = 310_000
    const val SALT_BYTES = 16

    private val random = SecureRandom()

    fun newSalt(): ByteArray = ByteArray(SALT_BYTES).also(random::nextBytes)

    fun derive(pin: CharArray, salt: ByteArray, iterations: Int): ByteArray {
        val spec = PBEKeySpec(pin, salt, iterations, AesGcm.KEY_BYTES * 8)
        return try {
            SecretKeyFactory.getInstance(ALGORITHM).generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }
}
