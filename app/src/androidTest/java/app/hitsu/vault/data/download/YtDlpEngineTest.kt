package app.hitsu.vault.data.download

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.hitsu.vault.domain.Clock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The engine is a Python runtime unpacked at first use, so the only way to know it works on this
 * device is to start it there. Nothing here touches the network.
 */
@RunWith(AndroidJUnit4::class)
class YtDlpEngineTest {

    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val engine = YtDlpEngine(context, Clock { System.currentTimeMillis() }, Dispatchers.IO)

    @Test
    fun startsOnThisDeviceAndReportsItsVersion() = runBlocking {
        val ready = withTimeout(STARTUP_TIMEOUT_MS) { engine.ensureReady() }
        assertTrue("yt-dlp could not start: " + engine.lastError, ready)

        val version = withTimeout(STARTUP_TIMEOUT_MS) { engine.version() }
        assertNotNull("no version reported", version)
        assertTrue(version!!.isNotBlank())
    }

    private companion object {
        const val STARTUP_TIMEOUT_MS = 180_000L
    }
}
