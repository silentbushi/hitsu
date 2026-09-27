package app.hitsu.vault

import app.hitsu.vault.crypto.AesGcm
import app.hitsu.vault.crypto.BiometricKey
import app.hitsu.vault.crypto.KeyWrapper
import app.hitsu.vault.crypto.VaultCrypto
import app.hitsu.vault.data.CryptoVaultGateway
import app.hitsu.vault.data.VaultMeta
import app.hitsu.vault.data.VaultMetaStore
import app.hitsu.vault.domain.Clock
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Stands in for the Keystore: same sealing, but with a key the test owns. */
class FakeKeyWrapper(private val key: ByteArray = AesGcm.newKey()) : KeyWrapper {
    override fun wrap(plaintext: ByteArray): ByteArray = AesGcm.encrypt(key, plaintext)
    override fun unwrap(payload: ByteArray): ByteArray = AesGcm.decrypt(key, payload)
}

class InMemoryVaultMetaStore : VaultMetaStore {
    var meta: VaultMeta? = null
        private set

    override suspend fun exists(): Boolean = meta != null
    override suspend fun read(): VaultMeta? = meta
    override suspend fun write(meta: VaultMeta) {
        this.meta = meta
    }
}

/** The real KDF cost would add seconds to the suite; correctness does not depend on it. */
const val TEST_KDF_ITERATIONS = 1_000

fun testCrypto(keyWrapper: KeyWrapper = FakeKeyWrapper()): VaultCrypto =
    VaultCrypto(keyWrapper, TEST_KDF_ITERATIONS)

suspend fun testGateway(
    scope: CoroutineScope,
    dispatcher: CoroutineDispatcher,
    clock: Clock,
    store: VaultMetaStore = InMemoryVaultMetaStore(),
    crypto: VaultCrypto = testCrypto(),
    biometricKey: BiometricKey = FakeBiometricKey(),
): CryptoVaultGateway =
    CryptoVaultGateway(crypto, biometricKey, store, clock, dispatcher, scope).apply { bootstrap() }

/**
 * What the Keystore key does, minus the part that needs a fingerprint: a plain AES-GCM key, so the
 * sealing and unsealing of the DEK can be checked without a device to authenticate against.
 */
class FakeBiometricKey : BiometricKey {
    private var key: SecretKey? = null

    override fun exists(): Boolean = key != null

    override fun encryptCipher(): Cipher {
        val fresh = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
        key = fresh
        return Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, fresh) }
    }

    override fun decryptCipher(iv: ByteArray): Cipher {
        val existing = checkNotNull(key) { "No biometric key" }
        return Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(Cipher.DECRYPT_MODE, existing, GCMParameterSpec(128, iv))
        }
    }

    override fun delete() {
        key = null
    }
}
