package app.hitsu.vault.data

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.exifinterface.media.ExifInterface
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.hitsu.vault.crypto.AesGcm
import app.hitsu.vault.crypto.VaultCipher
import app.hitsu.vault.data.db.HitsuDatabase
import app.hitsu.vault.data.media.ExifSanitizer
import app.hitsu.vault.data.media.ImportOutcome
import app.hitsu.vault.data.media.ImportSource
import app.hitsu.vault.data.media.MediaImporter
import app.hitsu.vault.data.media.PlaybackCache
import app.hitsu.vault.data.media.ThumbnailFactory
import app.hitsu.vault.data.media.VideoFrames
import app.hitsu.vault.data.media.VaultFiles
import app.hitsu.vault.domain.Clock
import app.hitsu.vault.domain.MediaType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File

@RunWith(AndroidJUnit4::class)
class MediaImportTest {

    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private lateinit var database: HitsuDatabase
    private lateinit var root: File
    private lateinit var files: VaultFiles
    private lateinit var importer: MediaImporter
    private val cipher = VaultCipher(AesGcm.newKey())

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(context, HitsuDatabase::class.java).build()
        root = File(context.cacheDir, "vault-test-${System.nanoTime()}")
        files = VaultFiles(root)
        importer = MediaImporter(
            files = files,
            dao = database.mediaDao(),
            thumbnails = ThumbnailFactory(),
            videos = VideoFrames(ThumbnailFactory()),
            exif = ExifSanitizer(File(context.cacheDir, "staging-test")),
            clock = Clock { IMPORTED_AT },
        )
    }

    @After
    fun tearDown() {
        database.close()
        root.deleteRecursively()
    }

    /** A landscape JPEG carrying GPS and a capture date, like something out of a camera roll. */
    private fun cameraPhoto(): ByteArray {
        val bitmap = Bitmap.createBitmap(1200, 800, Bitmap.Config.ARGB_8888).apply { eraseColor(0x4488AACC) }
        val jpeg = ByteArrayOutputStream().use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
            out.toByteArray()
        }
        bitmap.recycle()

        val staged = File(context.cacheDir, "camera-${System.nanoTime()}.jpg")
        staged.writeBytes(jpeg)
        ExifInterface(staged).apply {
            setAttribute(ExifInterface.TAG_GPS_LATITUDE, "19/1,25/1,0/1")
            setAttribute(ExifInterface.TAG_GPS_LATITUDE_REF, "N")
            setAttribute(ExifInterface.TAG_GPS_LONGITUDE, "99/1,7/1,0/1")
            setAttribute(ExifInterface.TAG_GPS_LONGITUDE_REF, "W")
            setAttribute(ExifInterface.TAG_DATETIME_ORIGINAL, "2026:09:21 12:30:00")
            saveAttributes()
        }
        val bytes = staged.readBytes()
        staged.delete()
        return bytes
    }

    /** Every photo here is new, so the import always yields a fresh row. */
    private suspend fun importPhoto(bytes: ByteArray) =
        (importer.import(source(bytes), cipher) as ImportOutcome.Imported).entity

    private fun source(bytes: ByteArray) = ImportSource(
        displayName = "IMG_0421.jpg",
        mime = "image/jpeg",
        sizeBytes = bytes.size.toLong(),
        open = { ByteArrayInputStream(bytes) },
    )

    @Test
    fun importEncryptsTheImageAndItsThumbnail() = runTest {
        val original = cameraPhoto()

        val entity = importPhoto(original)

        val objectFile = files.file(entity.objectPath)
        val thumbFile = files.file(entity.thumbPath)
        assertTrue(objectFile.exists() && thumbFile.exists())

        // On disk it is ciphertext: a JPEG would start with FF D8.
        val storedBytes = objectFile.readBytes()
        assertNotEquals(0xFF.toByte(), storedBytes[0])
        assertNotEquals(0xD8.toByte(), storedBytes[1])

        val decrypted = cipher.decrypt(storedBytes)
        assertEquals(0xFF.toByte(), decrypted[0])
        assertEquals(0xD8.toByte(), decrypted[1])

        val thumbBytes = cipher.decrypt(thumbFile.readBytes())
        val thumbnail = BitmapFactory.decodeByteArray(thumbBytes, 0, thumbBytes.size)
        assertEquals(480, maxOf(thumbnail.width, thumbnail.height))
        thumbnail.recycle()
    }

    @Test
    fun importDropsLocationFromTheStoredBytes() = runTest {
        val original = cameraPhoto()
        assertNotNull(ExifInterface(ByteArrayInputStream(original)).getAttribute(ExifInterface.TAG_GPS_LATITUDE))

        val entity = importPhoto(original)

        val stored = ExifInterface(ByteArrayInputStream(cipher.decrypt(files.file(entity.objectPath).readBytes())))
        assertNull(stored.getAttribute(ExifInterface.TAG_GPS_LATITUDE))
        assertNull(stored.getAttribute(ExifInterface.TAG_GPS_LONGITUDE))
        assertFalse(stored.getLatLong() != null)
    }

    @Test
    fun indexRowDescribesTheOriginal() = runTest {
        val entity = importPhoto(cameraPhoto())

        assertEquals(MediaType.Photo, entity.type)
        assertEquals("image/jpeg", entity.mime)
        assertEquals(1200, entity.width)
        assertEquals(800, entity.height)
        assertEquals("IMG_0421.jpg", entity.originalName)
        assertEquals(IMPORTED_AT, entity.importedAt)
        assertNull(entity.durationMs)
        assertTrue((entity.takenAt ?: 0L) > 0L)

        val rows = database.mediaDao().observeAll().first()
        assertEquals(listOf(entity.id), rows.map { it.id })
        assertEquals(1, database.mediaDao().count())
    }

    @Test
    fun newestCapturesComeFirst() = runTest {
        val older = importPhoto(cameraPhoto())
        database.mediaDao().insert(
            older.copy(
                id = "newer",
                takenAt = (older.takenAt ?: 0L) + 86_400_000,
                contentFingerprint = "another-file",
            ),
        )

        val rows = database.mediaDao().observeAll().first()

        assertEquals("newer", rows.first().id)
    }

    private fun assertNotNull(value: Any?) = assertTrue(value != null)

    private companion object {
        const val IMPORTED_AT = 1_759_000_000_000L
    }
}
