package app.hitsu.vault.data.backup

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.security.GeneralSecurityException
import javax.crypto.AEADBadTagException

/**
 * A backup is only worth having if it can be read back, and the two ways it could quietly fail are a
 * file that decrypts into nonsense and a file that looks complete when it is not. Both are checked
 * here, end to end but without a phone: the archive, the chunked sealing and the passphrase are all
 * plain JVM code.
 */
class BackupFormatTest {

    private val passphrase = "una contraseña larga".toCharArray()

    @Test
    fun aBackupComesBackByteForByte() {
        val photo = ByteArray(300_000) { (it * 31 % 251).toByte() }
        val video = ByteArray(3_500_000) { (it * 17 % 253).toByte() }

        val file = write { archive ->
            archive.writeBytes(BackupArchive.MANIFEST, MANIFEST.toByteArray())
            archive.write(BackupArchive.mediaEntry("a")) { it.write(photo) }
            archive.write(BackupArchive.mediaEntry("b")) { it.write(video) }
        }

        read(file) { reader ->
            assertEquals(BackupArchive.MANIFEST, reader.next()?.name)
            assertEquals(MANIFEST, reader.readBytes().decodeToString())
            assertEquals("media/a", reader.next()?.name)
            assertArrayEquals(photo, reader.readBytes())
            assertEquals("media/b", reader.next()?.name)
            assertArrayEquals(video, reader.readBytes())
            assertNull(reader.next())
        }
    }

    @Test
    fun anEmptyVaultStillProducesAReadableBackup() {
        val file = write { it.writeBytes(BackupArchive.MANIFEST, MANIFEST.toByteArray()) }

        read(file) { reader ->
            assertEquals(BackupArchive.MANIFEST, reader.next()?.name)
            assertEquals(MANIFEST, reader.readBytes().decodeToString())
            assertNull(reader.next())
        }
    }

    @Test
    fun anotherPassphraseOpensNothing() {
        val file = write { it.writeBytes(BackupArchive.MANIFEST, MANIFEST.toByteArray()) }

        val header = BackupCrypto.readHeader(ByteArrayInputStream(file))
        val wrong = BackupCrypto.cipherFor("otra cosa".toCharArray(), header)
        val body = ByteArrayInputStream(file, HEADER_BYTES, file.size - HEADER_BYTES)

        assertThrows(AEADBadTagException::class.java) {
            BackupArchive.Reader(wrong.decryptingSource(body))
        }
    }

    @Test
    fun aBackupCutShortIsRejectedRatherThanReadHalfway() {
        val photo = ByteArray(2_500_000) { it.toByte() }
        val file = write { archive ->
            archive.writeBytes(BackupArchive.MANIFEST, MANIFEST.toByteArray())
            archive.write(BackupArchive.mediaEntry("a")) { it.write(photo) }
        }

        // Losing the tail costs the last chunk, which is the one that says the file was finished.
        val truncated = file.copyOf(file.size - 1_200_000)
        val failure = assertThrows(Exception::class.java) {
            read(truncated) { reader ->
                while (reader.next() != null) reader.readBytes()
            }
        }
        /*
         * Any of three, depending on where the cut fell: the frame is incomplete (end of file), the
         * ciphertext of a chunk is cut and fails its tag (GCM), or the archive framing no longer adds
         * up. What matters is that none of them is silence.
         */
        assertTrue(
            failure.toString(),
            failure is IOException || failure is GeneralSecurityException ||
                failure is BackupFormatException,
        )
    }

    @Test
    fun aFileThatIsNotABackupSaysSo() {
        val nonsense = ByteArrayInputStream("una foto cualquiera".toByteArray())

        val failure = assertThrows(BackupFormatException::class.java) {
            BackupCrypto.readHeader(nonsense)
        }
        assertEquals("Not a Hitsu backup", failure.message)
    }

    private fun write(body: (BackupArchive.Writer) -> Unit): ByteArray {
        val file = ByteArrayOutputStream()
        val header = BackupCrypto.writeHeader(file)
        val cipher = BackupCrypto.cipherFor(passphrase, header)
        cipher.encryptingSink(file).use { sealed ->
            BackupArchive.Writer(sealed).use(body)
        }
        return file.toByteArray()
    }

    private fun read(file: ByteArray, body: (BackupArchive.Reader) -> Unit) {
        val source = ByteArrayInputStream(file)
        val header = BackupCrypto.readHeader(source)
        val cipher = BackupCrypto.cipherFor(passphrase, header)
        body(BackupArchive.Reader(cipher.decryptingSource(source)))
    }

    private companion object {
        const val MANIFEST = """{"version":1,"createdAt":0,"items":[]}"""

        /** magic + version + salt + iterations, the part that stays in the clear. */
        const val HEADER_BYTES = 8 + 16 + 4
    }
}

