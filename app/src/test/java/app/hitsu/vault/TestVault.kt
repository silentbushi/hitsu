package app.hitsu.vault

import app.hitsu.vault.crypto.AesGcm
import app.hitsu.vault.crypto.KeyWrapper
import app.hitsu.vault.crypto.VaultCrypto
import app.hitsu.vault.data.CryptoVaultGateway
import app.hitsu.vault.data.VaultMeta
import app.hitsu.vault.data.VaultMetaStore
import app.hitsu.vault.domain.Clock
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope

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
): CryptoVaultGateway = CryptoVaultGateway(crypto, store, clock, dispatcher, scope).apply { bootstrap() }
