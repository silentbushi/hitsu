package app.hitsu.vault.data

import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.hitsu.vault.data.media.ImportSource
import app.hitsu.vault.data.media.ImportSources
import app.hitsu.vault.data.media.SharedIntake
import app.hitsu.vault.data.media.asImportSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException

/**
 * Spec §7.4: whatever another app hands over is copied before the PIN is asked for, because the
 * grant that lets us read it dies with the sharing app.
 */
@RunWith(AndroidJUnit4::class)
class SharedIntakeTest {

    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var stagingDir: File
    private lateinit var intake: SharedIntake

    /** Stands in for the sharing app: readable now, gone once [revoked] is set. */
    private var revoked = false

    private fun bytesFor(seed: Int) = ByteArray(2_048) { ((it + seed) % 251).toByte() }

    @Before
    fun setUp() {
        stagingDir = File(context.cacheDir, "shared-test-${System.nanoTime()}")
        intake = SharedIntake(
            sources = ImportSources { uri ->
                val seed = uri.lastPathSegment!!.toInt()
                ImportSource(
                    displayName = "shared_$seed.jpg",
                    mime = "image/jpeg",
                    sizeBytes = bytesFor(seed).size.toLong(),
                    open = {
                        if (revoked) throw IOException("permission gone")
                        ByteArrayInputStream(bytesFor(seed))
                    },
                )
            },
            stagingDir = stagingDir,
            ioDispatcher = Dispatchers.IO,
            appScope = appScope,
        )
    }

    @After
    fun tearDown() {
        appScope.cancel()
        stagingDir.deleteRecursively()
    }

    private fun sharedUri(seed: Int) = Uri.parse("content://com.other.app/media/$seed")

    private suspend fun stageAndWait(vararg seeds: Int) {
        val job = intake.stage(seeds.map(::sharedUri))
        assertNotNull(job)
        withTimeout(TIMEOUT_MS) { job!!.join() }
    }

    @Test
    fun copiesTheBytesBeforeTheyCanBeTakenAway() = runBlocking {
        stageAndWait(1)

        // The sharing app is gone now, which is exactly the case staging exists for.
        revoked = true

        val staged = intake.staged.value.single()
        assertArrayEquals(bytesFor(1), staged.asImportSource().open().use { it.readBytes() })
        assertEquals("shared_1.jpg", staged.displayName)
        assertEquals("image/jpeg", staged.mime)
    }

    @Test
    fun takeHandsOverEverythingOnce() = runBlocking {
        stageAndWait(1, 2)

        val first = intake.take()
        val second = intake.take()

        assertEquals(2, first.size)
        assertTrue(second.isEmpty())
        assertTrue(intake.staged.value.isEmpty())
    }

    @Test
    fun discardLeavesNothingOnDisk() = runBlocking {
        stageAndWait(1, 2)
        val staged = intake.take()

        intake.discard(staged)

        staged.forEach { assertFalse(it.file.exists()) }
    }

    @Test
    fun wipeClearsEvenWhatWasNeverTaken() = runBlocking {
        stageAndWait(1, 2, 3)

        intake.wipe()

        assertTrue(intake.staged.value.isEmpty())
        assertEquals(0, stagingDir.listFiles()?.size ?: 0)
    }

    @Test
    fun oneUnreadableShareDoesNotSinkTheRest() = runBlocking {
        val job = intake.stage(listOf(sharedUri(1), Uri.parse("content://com.other.app/media/nope")))
        withTimeout(TIMEOUT_MS) { job!!.join() }

        assertEquals(1, intake.staged.value.size)
    }

    private companion object {
        const val TIMEOUT_MS = 15_000L
    }
}
