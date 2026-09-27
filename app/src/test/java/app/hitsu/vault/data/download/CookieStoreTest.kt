package app.hitsu.vault.data.download

import app.hitsu.vault.crypto.AesGcm
import app.hitsu.vault.crypto.VaultCipher
import app.hitsu.vault.domain.UnlockResult
import app.hitsu.vault.domain.VaultGateway
import app.hitsu.vault.domain.VaultState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import javax.crypto.Cipher

/**
 * Cookies are live credentials for the user's accounts, so what matters here is not only that yt-dlp
 * can read them but that the readable copy exists for no longer than that.
 */
class CookieStoreTest {

    @get:Rule
    val folder = TemporaryFolder()

    private val cipher = VaultCipher(AesGcm.newKey())

    /** One dispatcher for the store and the test, or they end up on different schedulers. */
    private val dispatcher = StandardTestDispatcher()

    private val vault = object : VaultGateway {
        override val state: StateFlow<VaultState> = MutableStateFlow(VaultState.Unlocked)
        override val pinLength = 6
        override val biometricRequested = false
        override val retryAtMillis = 0L
        override val lockTimeoutMillis = 0L
        override val cipher: VaultCipher? = this@CookieStoreTest.cipher
        override suspend fun create(pin: CharArray, biometricRequested: Boolean) = Unit
        override suspend fun unlock(pin: CharArray) = UnlockResult.Success
        override suspend fun setLockTimeout(millis: Long) = Unit
        override suspend fun changePin(currentPin: CharArray, newPin: CharArray) = false
        override val biometricEnabled = false
        override fun biometricEnrollCipher(): Cipher? = null
        override fun biometricUnlockCipher(): Cipher? = null
        override suspend fun enableBiometric(cipher: Cipher) = false
        override suspend fun unlockWithBiometric(cipher: Cipher) = false
        override suspend fun forgetBiometric() = Unit
        override fun lock() = Unit
    }

    private fun store(directory: File) =
        CookieStore(directory, vault, dispatcher)

    @Test
    fun whatIsStoredOnDiskIsNotTheCookieItself() = runTest(dispatcher) {
        val directory = folder.newFolder("cookies")
        val store = store(directory)

        store.save("x.com", netscapeLines("x.com", "auth_token=secret", EXPIRY))

        val stored = directory.listFiles().orEmpty().single().readBytes()
        assertFalse(stored.decodeToString().contains("auth_token"))
        assertTrue(cipher.decrypt(stored).decodeToString().contains("auth_token"))
    }

    @Test
    fun theReadableCopyIsHandedOverAndThenTakenAway() = runTest(dispatcher) {
        val store = store(folder.newFolder("cookies"))
        store.save("x.com", netscapeLines("x.com", "auth_token=secret", EXPIRY))
        store.save("tiktok.com", netscapeLines("tiktok.com", "sessionid=other", EXPIRY))
        val jarDirectory = folder.newFolder("jar")

        val lent = store.withCookies(jarDirectory) { jar ->
            val body = jar!!.readText()
            assertTrue(body.contains("auth_token"))
            assertTrue(body.contains("sessionid"))
            jar
        }

        assertFalse(lent.exists())
    }

    @Test
    fun withoutSavedCookiesThereIsNothingToLend() = runTest(dispatcher) {
        val store = store(folder.newFolder("cookies"))
        val jarDirectory = folder.newFolder("jar")

        val lent = store.withCookies(jarDirectory) { it }

        assertEquals(null, lent)
        assertEquals(0, jarDirectory.listFiles().orEmpty().size)
    }

    private companion object {
        const val EXPIRY = 1_800_000_000L
    }
}
