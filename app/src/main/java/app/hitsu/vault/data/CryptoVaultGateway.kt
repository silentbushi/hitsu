package app.hitsu.vault.data

import app.hitsu.vault.crypto.BiometricKey
import app.hitsu.vault.crypto.decodeBase64
import app.hitsu.vault.crypto.encodeBase64
import app.hitsu.vault.crypto.VaultCipher
import app.hitsu.vault.crypto.VaultCrypto
import app.hitsu.vault.crypto.VaultUnlock
import app.hitsu.vault.domain.Clock
import app.hitsu.vault.domain.PinKind
import app.hitsu.vault.domain.PinPolicy
import app.hitsu.vault.domain.UnlockResult
import app.hitsu.vault.domain.UnlockThrottle
import app.hitsu.vault.domain.VaultGateway
import app.hitsu.vault.domain.VaultState
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import android.security.keystore.KeyPermanentlyInvalidatedException
import java.security.GeneralSecurityException
import javax.crypto.Cipher

class CryptoVaultGateway(
    private val crypto: VaultCrypto,
    private val biometricKey: BiometricKey,
    private val store: VaultMetaStore,
    private val clock: Clock,
    private val cryptoDispatcher: CoroutineDispatcher,
    private val scope: CoroutineScope,
) : VaultGateway {

    private val mutex = Mutex()
    private var meta: VaultMeta? = null
    private var dek: ByteArray? = null

    private val _state = MutableStateFlow(VaultState.Unknown)
    override val state: StateFlow<VaultState> = _state.asStateFlow()

    override var cipher: VaultCipher? = null
        private set

    override val pinLength: Int get() = meta?.pinLength ?: 0
    override val biometricRequested: Boolean get() = meta?.biometricRequested ?: false
    override val retryAtMillis: Long get() = meta?.retryAtMillis ?: 0L
    override val lockTimeoutMillis: Long get() = meta?.lockTimeoutMillis ?: 0L

    override val biometricEnabled: Boolean
        get() = meta?.biometricEnabled == true && meta?.biometricDek != null && biometricKey.exists()

    override fun biometricEnrollCipher(): Cipher? =
        runCatching { biometricKey.encryptCipher() }.getOrNull()

    override fun biometricUnlockCipher(): Cipher? {
        val iv = meta?.biometricIv?.decodeBase64() ?: return null
        return try {
            biometricKey.decryptCipher(iv)
        } catch (_: KeyPermanentlyInvalidatedException) {
            // A fingerprint was added or removed: this key will never open again, so it is dropped.
            scope.launch { forgetBiometric() }
            null
        } catch (_: GeneralSecurityException) {
            null
        } catch (_: IllegalStateException) {
            null
        }
    }

    init {
        scope.launch { bootstrap() }
    }

    /** Reads the vault metadata once at startup; until it lands the UI shows the splash. */
    suspend fun bootstrap() = mutex.withLock {
        if (_state.value != VaultState.Unknown) return@withLock
        meta = store.read()
        _state.value = if (meta == null && !store.exists()) VaultState.Absent else VaultState.Locked
    }

    override suspend fun create(pin: CharArray, biometricRequested: Boolean) = mutex.withLock {
        check(_state.value == VaultState.Absent) { "Vault already exists" }
        require(PinPolicy.isValid(pin.size)) { "Invalid PIN length" }
        val created = withContext(cryptoDispatcher) {
            crypto.createVault(pin, PinKind.Numeric, biometricRequested)
        }
        store.write(created.meta)
        meta = created.meta
        openSession(created.dek)
    }

    override suspend fun unlock(pin: CharArray): UnlockResult = mutex.withLock {
        val current = meta ?: return@withLock UnlockResult.Rejected(0, 0L)
        if (clock.now() < current.retryAtMillis) {
            return@withLock UnlockResult.Rejected(current.failedUnlocks, current.retryAtMillis)
        }

        when (val result = withContext(cryptoDispatcher) { crypto.unlock(pin, current) }) {
            is VaultUnlock.Success -> {
                persist(current.copy(failedUnlocks = 0, retryAtMillis = 0L))
                openSession(result.dek)
                UnlockResult.Success
            }
            VaultUnlock.WrongPin -> {
                val failedUnlocks = current.failedUnlocks + 1
                val delay = UnlockThrottle.delayAfter(failedUnlocks)
                val retryAtMillis = if (delay > 0) clock.now() + delay else 0L
                persist(current.copy(failedUnlocks = failedUnlocks, retryAtMillis = retryAtMillis))
                UnlockResult.Rejected(failedUnlocks, retryAtMillis)
            }
            // A lost Keystore key is not a wrong guess, so it must not burn an attempt.
            VaultUnlock.Unrecoverable -> UnlockResult.Rejected(current.failedUnlocks, current.retryAtMillis)
        }
    }

    /**
     * Only with the vault open, because what gets sealed is the DEK that is in memory right now. The
     * cipher comes from the prompt the user just answered, so the sealing is authorised by the same
     * fingerprint that will later authorise the opening.
     */
    override suspend fun enableBiometric(cipher: Cipher): Boolean = mutex.withLock {
        val current = meta ?: return@withLock false
        val key = dek ?: return@withLock false
        val sealed = try {
            withContext(cryptoDispatcher) { cipher.doFinal(key) }
        } catch (_: GeneralSecurityException) {
            return@withLock false
        }
        persist(
            current.copy(
                biometricEnabled = true,
                biometricDek = sealed.encodeBase64(),
                biometricIv = cipher.iv.encodeBase64(),
            ),
        )
        true
    }

    override suspend fun unlockWithBiometric(cipher: Cipher): Boolean = mutex.withLock {
        val current = meta ?: return@withLock false
        val sealed = current.biometricDek?.decodeBase64() ?: return@withLock false
        val key = try {
            withContext(cryptoDispatcher) { cipher.doFinal(sealed) }
        } catch (_: GeneralSecurityException) {
            return@withLock false
        }
        // A fingerprint is not a PIN guess: an unlock through it clears no throttle it did not earn.
        if (current.failedUnlocks != 0 || current.retryAtMillis != 0L) {
            persist(current.copy(failedUnlocks = 0, retryAtMillis = 0L))
        }
        openSession(key)
        true
    }

    /** After the key is gone — turned off, or invalidated by a new fingerprint — the PIN is the way in. */
    override suspend fun forgetBiometric() = mutex.withLock {
        biometricKey.delete()
        val current = meta ?: return@withLock
        persist(current.copy(biometricEnabled = false, biometricDek = null, biometricIv = null))
    }

    override suspend fun setLockTimeout(millis: Long) = mutex.withLock {
        val current = meta ?: return@withLock
        persist(current.copy(lockTimeoutMillis = millis))
    }

    override fun lock() {
        if (_state.value != VaultState.Unlocked) return
        dek?.fill(0)
        dek = null
        cipher = null
        _state.value = VaultState.Locked
    }

    private fun openSession(key: ByteArray) {
        dek?.fill(0)
        dek = key
        cipher = VaultCipher(key)
        _state.value = VaultState.Unlocked
    }

    private suspend fun persist(updated: VaultMeta) {
        store.write(updated)
        meta = updated
    }
}
