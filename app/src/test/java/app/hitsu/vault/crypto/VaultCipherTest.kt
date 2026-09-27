package app.hitsu.vault.crypto

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.security.GeneralSecurityException

class VaultCipherTest {

    private val photo = ByteArray(8_192) { (it * 13).toByte() }
    private val cipher = VaultCipher(AesGcm.newKey())

    @Test
    fun sameBytesGiveTheSameFingerprint() {
        assertEquals(cipher.fingerprint(photo), cipher.fingerprint(photo.copyOf()))
    }

    @Test
    fun differentBytesGiveDifferentFingerprints() {
        val edited = photo.copyOf().also { it[42] = (it[42] + 1).toByte() }

        assertNotEquals(cipher.fingerprint(photo), cipher.fingerprint(edited))
    }

    /** The point of keying it: the index cannot be used to prove a given file is in the vault. */
    @Test
    fun anotherVaultFingerprintsTheSameFileDifferently() {
        val otherVault = VaultCipher(AesGcm.newKey())

        assertNotEquals(cipher.fingerprint(photo), otherVault.fingerprint(photo))
    }

    /** Videos are streamed in and out; the result has to match the in-memory path byte for byte. */
    @Test
    fun streamingRoundTripsAndReportsProgress() {
        val video = ByteArray(700_000) { (it % 251).toByte() }
        val encrypted = ByteArrayOutputStream()

        val fingerprint = cipher.encryptTo(ByteArrayInputStream(video), encrypted)

        assertEquals(cipher.fingerprint(video), fingerprint)
        val decrypted = ByteArrayOutputStream()
        val progress = mutableListOf<Long>()
        cipher.decryptTo(ByteArrayInputStream(encrypted.toByteArray()), decrypted) { progress += it }

        assertArrayEquals(video, decrypted.toByteArray())
        assertEquals(video.size.toLong(), progress.last())
        assertEquals(progress.sorted(), progress)
    }

    /** Files written before chunking existed must still open. */
    @Test
    fun readsBackTheOlderSingleSealedFormat() {
        val legacy = cipher.encrypt(photo)
        val out = ByteArrayOutputStream()

        cipher.decryptTo(ByteArrayInputStream(legacy), out)

        assertArrayEquals(photo, out.toByteArray())
    }

    /** A file that is an exact multiple of the chunk size still has a final chunk of its own. */
    @Test
    fun streamsAFileThatEndsOnAChunkBoundary() {
        val exact = ByteArray(1 shl 20) { it.toByte() }
        val encrypted = ByteArrayOutputStream()
        cipher.encryptTo(ByteArrayInputStream(exact), encrypted)

        val decrypted = ByteArrayOutputStream()
        cipher.decryptTo(ByteArrayInputStream(encrypted.toByteArray()), decrypted)

        assertArrayEquals(exact, decrypted.toByteArray())
    }

    @Test
    fun streamsAnEmptyFile() {
        val encrypted = ByteArrayOutputStream()
        cipher.encryptTo(ByteArrayInputStream(ByteArray(0)), encrypted)

        val decrypted = ByteArrayOutputStream()
        cipher.decryptTo(ByteArrayInputStream(encrypted.toByteArray()), decrypted)

        assertEquals(0, decrypted.size())
    }

    /** Several chunks, so progress really ticks and memory stays bounded. */
    @Test
    fun streamsFilesLargerThanOneChunk() {
        val video = ByteArray(3_500_000) { (it % 97).toByte() }
        val encrypted = ByteArrayOutputStream()
        cipher.encryptTo(ByteArrayInputStream(video), encrypted)

        val decrypted = ByteArrayOutputStream()
        val progress = mutableListOf<Long>()
        cipher.decryptTo(ByteArrayInputStream(encrypted.toByteArray()), decrypted) { progress += it }

        assertArrayEquals(video, decrypted.toByteArray())
        assertEquals(4, progress.size)
        assertEquals(video.size.toLong(), progress.last())
    }

    /** Dropping the tail must not pass as a complete file: the last chunk is marked as such. */
    @Test
    fun streamingRejectsATruncatedFile() {
        val video = ByteArray(2_200_000) { it.toByte() }
        val encrypted = ByteArrayOutputStream()
        cipher.encryptTo(ByteArrayInputStream(video), encrypted)
        val chunkFrame = 1 + 12 + (1 shl 20) + 16
        val truncated = encrypted.toByteArray().copyOf(5 + chunkFrame)

        assertThrows(Exception::class.java) {
            cipher.decryptTo(ByteArrayInputStream(truncated), ByteArrayOutputStream())
        }
    }

    @Test
    fun streamingRejectsTamperedFiles() {
        val encrypted = ByteArrayOutputStream()
        cipher.encryptTo(ByteArrayInputStream(photo), encrypted)
        val payload = encrypted.toByteArray().also { it[it.size - 1] = (it[it.size - 1] + 1).toByte() }

        assertThrows(GeneralSecurityException::class.java) {
            cipher.decryptTo(ByteArrayInputStream(payload), ByteArrayOutputStream())
        }
    }

    @Test
    fun fingerprintDoesNotContainTheContent() {
        val marker = "cofre".toByteArray()
        val fingerprint = VaultCipher(AesGcm.newKey()).fingerprint(marker)

        assertNotEquals(marker.toList(), fingerprint.toByteArray().toList())
        assertEquals(44, fingerprint.length) // base64 of 32 bytes
    }
}
