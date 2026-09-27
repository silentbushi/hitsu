package app.hitsu.vault.data

import android.graphics.Bitmap
import android.net.Uri
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.hitsu.vault.crypto.AesGcm
import app.hitsu.vault.crypto.VaultCipher
import app.hitsu.vault.data.db.HitsuDatabase
import app.hitsu.vault.data.media.ExifSanitizer
import app.hitsu.vault.data.media.ImportSource
import app.hitsu.vault.data.media.ImportSources
import app.hitsu.vault.data.media.MediaImporter
import app.hitsu.vault.data.media.PlaybackCache
import app.hitsu.vault.data.media.SharedIntake
import app.hitsu.vault.data.media.ThumbnailFactory
import app.hitsu.vault.data.media.VideoFrames
import app.hitsu.vault.data.media.VaultFiles
import app.hitsu.vault.domain.Clock
import app.hitsu.vault.domain.UnlockResult
import app.hitsu.vault.domain.VaultGateway
import app.hitsu.vault.domain.VaultState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File

/** Importing copies into the vault and never touches the gallery; the same file lands only once. */
@RunWith(AndroidJUnit4::class)
class ImportFlowTest {

    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var database: HitsuDatabase
    private lateinit var root: File
    private lateinit var files: VaultFiles
    private lateinit var repository: MediaRepository
    private val cipher = VaultCipher(AesGcm.newKey())

    private inner class UnlockedVault : VaultGateway {
        override val state: StateFlow<VaultState> = MutableStateFlow(VaultState.Unlocked)
        override val pinLength = 6
        override val biometricRequested = false
        override val retryAtMillis = 0L
        override val lockTimeoutMillis = 0L
        override val cipher = this@ImportFlowTest.cipher
        override suspend fun create(pin: CharArray, biometricRequested: Boolean) = Unit
        override suspend fun unlock(pin: CharArray): UnlockResult = UnlockResult.Success
        override suspend fun setLockTimeout(millis: Long) = Unit
        override fun lock() = Unit
    }

    /** Distinct pixels per seed, so two photos really are two different files. */
    private fun jpeg(seed: Int): ByteArray {
        val bitmap = Bitmap.createBitmap(600, 400, Bitmap.Config.ARGB_8888)
            .apply { eraseColor(0xFF000000.toInt() or (seed * 0x3B5F2D)) }
        return ByteArrayOutputStream().use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 85, out)
            bitmap.recycle()
            out.toByteArray()
        }
    }

    /** The uri's last segment picks which photo it stands for, so the same uri means the same bytes. */
    private fun photoUri(seed: Int): Uri = Uri.parse("content://media/picker/0/p/media/$seed")

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(context, HitsuDatabase::class.java).build()
        root = File(context.cacheDir, "import-test-${System.nanoTime()}")
        files = VaultFiles(root)
        repository = MediaRepository(
            dao = database.mediaDao(),
            importer = MediaImporter(
                files = files,
                dao = database.mediaDao(),
                thumbnails = ThumbnailFactory(),
                videos = VideoFrames(ThumbnailFactory()),
                exif = ExifSanitizer(File(context.cacheDir, "staging-import")),
                clock = Clock { 0L },
            ),
            files = files,
            sources = ImportSources { uri ->
                val bytes = jpeg(uri.lastPathSegment!!.toInt())
                ImportSource(
                    displayName = "IMG_${uri.lastPathSegment}.jpg",
                    mime = "image/jpeg",
                    sizeBytes = bytes.size.toLong(),
                    open = { ByteArrayInputStream(bytes) },
                )
            },
            playback = PlaybackCache(
                directory = File(context.cacheDir, "playback-test"),
                files = files,
                storage = null,
                ioDispatcher = Dispatchers.IO,
            ),
            intake = SharedIntake(
                sources = { error("not used here") },
                stagingDir = File(context.cacheDir, "shared-import-test"),
                ioDispatcher = Dispatchers.IO,
                appScope = appScope,
            ),
            vault = UnlockedVault(),
            appScope = appScope,
            ioDispatcher = Dispatchers.IO,
        )
    }

    @After
    fun tearDown() {
        appScope.cancel()
        database.close()
        root.deleteRecursively()
    }

    /** The importer touches real files and Room's own executor, so the test waits for the job. */
    private suspend fun importAndWait(uris: List<Uri>) {
        val job = repository.import(uris)
        assertNotNull(job)
        withTimeout(TIMEOUT_MS) { job!!.join() }
    }

    private fun storedObjects(): Int = files.file("objects").listFiles()?.size ?: 0

    /** The fingerprint identifies what the vault stores, so that is what the test recomputes. */
    private fun fingerprintOfStored(objectPath: String): String =
        cipher.fingerprint(cipher.decrypt(files.file(objectPath).readBytes()))

    @Test
    fun differentPhotosAreBothImported() = runBlocking {
        importAndWait(listOf(photoUri(1), photoUri(2)))

        assertEquals(2, database.mediaDao().count())
        assertEquals(2, storedObjects())
        assertEquals(0, repository.importStatus.value.duplicates)
        assertEquals(2, repository.importStatus.value.done)
    }

    @Test
    fun reimportingTheSamePhotoIsSkipped() = runBlocking {
        importAndWait(listOf(photoUri(1)))

        importAndWait(listOf(photoUri(1)))

        assertEquals(1, database.mediaDao().count())
        assertEquals(1, storedObjects())
        assertEquals(1, repository.importStatus.value.duplicates)
        assertEquals(0, repository.importStatus.value.done)
    }

    @Test
    fun aRepeatedPhotoInsideOneBatchCountsOnce() = runBlocking {
        importAndWait(listOf(photoUri(1), photoUri(2), photoUri(1)))

        assertEquals(2, database.mediaDao().count())
        assertEquals(1, repository.importStatus.value.duplicates)
    }

    @Test
    fun everyImportedRowCarriesAFingerprint() = runBlocking {
        importAndWait(listOf(photoUri(3)))

        val row = database.mediaDao().observeAll().first().single()
        assertEquals(fingerprintOfStored(row.objectPath), row.contentFingerprint)
    }

    /** Simulates a row written before fingerprints existed: its own object, no fingerprint. */
    private suspend fun legacyRowFrom(seed: Int, id: String) {
        val source = database.mediaDao().observeAll().first().first { it.originalName == "IMG_$seed.jpg" }
        val legacyPath = "objects/$id.hitsu"
        files.file(legacyPath).writeBytes(files.file(source.objectPath).readBytes())
        database.mediaDao().insert(
            source.copy(id = id, objectPath = legacyPath, contentFingerprint = null),
        )
    }

    @Test
    fun backfillFingerprintsOlderRows() = runBlocking {
        importAndWait(listOf(photoUri(4)))
        val original = database.mediaDao().observeAll().first().single()
        legacyRowFrom(seed = 4, id = "legacy")
        database.mediaDao().delete(original.id)
        assertNull(database.mediaDao().byId("legacy")?.contentFingerprint)

        withTimeout(TIMEOUT_MS) { repository.ensureFingerprints().join() }

        val legacy = database.mediaDao().byId("legacy")!!
        assertEquals(fingerprintOfStored(legacy.objectPath), legacy.contentFingerprint)
    }

    /** What the backfill is for: after it runs, reimporting that same file is caught as a copy. */
    @Test
    fun aBackfilledRowIsRecognisedOnReimport() = runBlocking {
        importAndWait(listOf(photoUri(7)))
        val original = database.mediaDao().observeAll().first().single()
        legacyRowFrom(seed = 7, id = "legacy-7")
        database.mediaDao().delete(original.id)
        withTimeout(TIMEOUT_MS) { repository.ensureFingerprints().join() }

        importAndWait(listOf(photoUri(7)))

        assertEquals(1, database.mediaDao().count())
        assertEquals(1, repository.importStatus.value.duplicates)
    }

    /** A pre-deduplication copy collides on the unique index: it is left alone, never deleted. */
    @Test
    fun backfillKeepsALegacyDuplicate() = runBlocking {
        importAndWait(listOf(photoUri(6)))
        legacyRowFrom(seed = 6, id = "legacy-copy")

        withTimeout(TIMEOUT_MS) { repository.ensureFingerprints().join() }

        assertNull(database.mediaDao().byId("legacy-copy")?.contentFingerprint)
        assertEquals(2, database.mediaDao().count())
    }

    private companion object {
        const val TIMEOUT_MS = 15_000L
    }
}
