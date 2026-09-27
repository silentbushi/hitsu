package app.hitsu.vault.data

import android.graphics.Bitmap
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.hitsu.vault.crypto.AesGcm
import app.hitsu.vault.crypto.VaultCipher
import app.hitsu.vault.data.backup.BackupPassphraseException
import app.hitsu.vault.data.backup.BackupStore
import app.hitsu.vault.data.db.HitsuDatabase
import app.hitsu.vault.data.media.ExifSanitizer
import app.hitsu.vault.data.media.ImportOutcome
import app.hitsu.vault.data.media.ImportSource
import app.hitsu.vault.data.media.MediaImporter
import app.hitsu.vault.data.media.ThumbnailFactory
import app.hitsu.vault.data.media.VaultFiles
import app.hitsu.vault.data.media.VideoFrames
import app.hitsu.vault.domain.Clock
import app.hitsu.vault.domain.UnlockResult
import app.hitsu.vault.domain.VaultGateway
import app.hitsu.vault.domain.VaultState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import javax.crypto.Cipher

/**
 * Spec §7.10, the part no unit test can reach: a backup made in one vault, opened in another one
 * with a different key — which is what moving to another phone is. What has to survive is the bytes
 * and the albums, and what must not survive is anything at all when the passphrase is wrong.
 */
@RunWith(AndroidJUnit4::class)
class BackupRoundTripTest {

    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private lateinit var source: Vault
    private lateinit var target: Vault

    /** Everything one vault needs: its own key, its own database and its own files. */
    private inner class Vault(name: String) {
        val cipher = VaultCipher(AesGcm.newKey())
        val database: HitsuDatabase =
            Room.inMemoryDatabaseBuilder(context, HitsuDatabase::class.java).build()
        val root = File(context.cacheDir, "$name-${System.nanoTime()}")
        val files = VaultFiles(root)
        val albums = AlbumRepository(database.albumDao(), Clock { NOW }, Dispatchers.IO)
        val importer = MediaImporter(
            files = files,
            dao = database.mediaDao(),
            thumbnails = ThumbnailFactory(),
            videos = VideoFrames(ThumbnailFactory()),
            exif = ExifSanitizer(File(root, "staging")),
            clock = Clock { NOW },
        )
        val gateway = object : VaultGateway {
            override val state: StateFlow<VaultState> = MutableStateFlow(VaultState.Unlocked)
            override val pinLength = 6
            override val biometricRequested = false
            override val retryAtMillis = 0L
            override val lockTimeoutMillis = 0L
            override val cipher = this@Vault.cipher
            override suspend fun create(pin: CharArray, biometricRequested: Boolean) = Unit
            override suspend fun unlock(pin: CharArray): UnlockResult = UnlockResult.Success
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
        val backups = BackupStore(
            dao = database.mediaDao(),
            albums = albums,
            files = files,
            vault = gateway,
            clock = Clock { NOW },
            stagingDir = File(root, "backup"),
            ioDispatcher = Dispatchers.IO,
        )

        fun close() {
            database.close()
            root.deleteRecursively()
        }
    }

    @Before
    fun setUp() {
        source = Vault("backup-source")
        target = Vault("backup-target")
    }

    @After
    fun tearDown() {
        source.close()
        target.close()
    }

    private fun jpeg(seed: Int): ByteArray {
        val bitmap = Bitmap.createBitmap(320, 240, Bitmap.Config.ARGB_8888)
            .apply { eraseColor(0xFF000000.toInt() or (seed * 0x2F7B11)) }
        return ByteArrayOutputStream().use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
            bitmap.recycle()
            out.toByteArray()
        }
    }

    private suspend fun Vault.importPhoto(seed: Int): String {
        val bytes = jpeg(seed)
        val outcome = importer.import(
            ImportSource(
                displayName = "IMG_$seed.jpg",
                mime = "image/jpeg",
                sizeBytes = bytes.size.toLong(),
                open = { ByteArrayInputStream(bytes) },
            ),
            cipher,
        )
        return (outcome as ImportOutcome.Imported).entity.id
    }

    /** What the vault actually stores, which is what has to come out the other end unchanged. */
    private fun Vault.plaintextOf(id: String): ByteArray = runBlocking {
        val row = database.mediaDao().byId(id)!!
        cipher.decrypt(files.file(row.objectPath).readBytes())
    }

    private suspend fun restoreInto(vault: Vault, backup: ByteArray, passphrase: String) {
        vault.backups.restore(
            source = ByteArrayInputStream(backup),
            passphrase = passphrase.toCharArray(),
            onProgress = { _, _ -> },
        ) { file, item ->
            val bytes = file.readBytes()
            val outcome = vault.importer.import(
                ImportSource(
                    displayName = item.originalName ?: item.id,
                    mime = item.mime,
                    sizeBytes = bytes.size.toLong(),
                    open = { ByteArrayInputStream(bytes) },
                ),
                vault.cipher,
            )
            when (outcome) {
                is ImportOutcome.Imported -> outcome.entity.id
                is ImportOutcome.Duplicate -> outcome.existingId
            }
        }
    }

    private fun backupOf(vault: Vault, passphrase: String): ByteArray {
        val sink = ByteArrayOutputStream()
        runBlocking { vault.backups.create(sink, passphrase.toCharArray()) { _, _ -> } }
        return sink.toByteArray()
    }

    @Test
    fun aBackupOpensInAVaultThatNeverKnewItsKey() = runBlocking {
        val first = source.importPhoto(1)
        val second = source.importPhoto(2)
        val original = source.plaintextOf(first)

        restoreInto(target, backupOf(source, PASSPHRASE), PASSPHRASE)

        assertEquals(2, target.database.mediaDao().count())
        val restored = target.database.mediaDao().observeAll().first()
            .first { it.originalName == "IMG_1.jpg" }
        assertArrayEquals(original, target.plaintextOf(restored.id))
        // The two vaults seal the same bytes with different keys, so the ciphertext must differ.
        assertTrue(
            !source.files.file(source.database.mediaDao().byId(first)!!.objectPath).readBytes()
                .contentEquals(target.files.file(restored.objectPath).readBytes()),
        )
        assertArrayEquals(
            source.plaintextOf(second),
            target.plaintextOf(
                target.database.mediaDao().observeAll().first()
                    .first { it.originalName == "IMG_2.jpg" }.id,
            ),
        )
    }

    @Test
    fun theAlbumsComeBackToo() = runBlocking {
        val photo = source.importPhoto(3)
        val album = source.albums.create("Viajes") as AlbumCreation.Created
        source.albums.add(album.id, listOf(photo))

        restoreInto(target, backupOf(source, PASSPHRASE), PASSPHRASE)

        val restoredAlbum = target.albums.albums(AlbumOrder.Alphabetical).first().single()
        assertEquals("Viajes", restoredAlbum.name)
        assertEquals(1, restoredAlbum.itemCount)
    }

    @Test
    fun restoringTwiceDoesNotDuplicateAnything() = runBlocking {
        val photo = source.importPhoto(4)
        val album = source.albums.create("Recibos") as AlbumCreation.Created
        source.albums.add(album.id, listOf(photo))
        val backup = backupOf(source, PASSPHRASE)

        restoreInto(target, backup, PASSPHRASE)
        restoreInto(target, backup, PASSPHRASE)

        assertEquals(1, target.database.mediaDao().count())
        assertEquals(1, target.albums.albums(AlbumOrder.Alphabetical).first().single().itemCount)
    }

    @Test
    fun theWrongPassphraseRestoresNothing() = runBlocking {
        source.importPhoto(5)
        val backup = backupOf(source, PASSPHRASE)

        assertThrows(BackupPassphraseException::class.java) {
            runBlocking { restoreInto(target, backup, "otra cosa distinta") }
        }

        assertEquals(0, target.database.mediaDao().count())
    }

    @Test
    fun aFileThatIsNotABackupIsRefused() {
        val nonsense = jpeg(6)

        assertThrows(Exception::class.java) {
            runBlocking { restoreInto(target, nonsense, PASSPHRASE) }
        }
    }

    private companion object {
        const val PASSPHRASE = "una contraseña larga"
        const val NOW = 1_700_000_000_000L
    }
}
