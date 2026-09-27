package app.hitsu.vault.data.backup

import app.hitsu.vault.crypto.Pbkdf2
import app.hitsu.vault.crypto.VaultCipher
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.EOFException
import java.io.InputStream
import java.io.OutputStream

/**
 * Spec §7.10: the outer layer of a backup file, the part that has to stay readable without any key.
 *
 * ```
 * HTSUBAK | version | salt (16) | iterations (4) | <everything else, sealed>
 * ```
 *
 * The salt and the iteration count travel in the clear because they are what a reader needs to derive
 * the key again, and neither is a secret. What follows is the same chunked AES-256-GCM format the
 * vault uses for its own objects (§5.3), only keyed from the passphrase instead of from the DEK —
 * which is why the file opens on a phone that never knew this vault, and why a wrong passphrase is
 * caught at the first chunk rather than after writing anything.
 */
object BackupCrypto {

    class Header(val salt: ByteArray, val iterations: Int)

    fun writeHeader(sink: OutputStream): Header {
        val header = Header(Pbkdf2.newSalt(), Pbkdf2.DEFAULT_ITERATIONS)
        DataOutputStream(sink).apply {
            write(MAGIC)
            write(header.salt)
            writeInt(header.iterations)
            flush()
        }
        return header
    }

    fun readHeader(source: InputStream): Header {
        val input = DataInputStream(source)
        val magic = ByteArray(MAGIC.size)
        try {
            input.readFully(magic)
        } catch (_: EOFException) {
            throw BackupFormatException("File is too short to be a backup")
        }
        if (!magic.contentEquals(MAGIC)) throw BackupFormatException("Not a Hitsu backup")

        val salt = ByteArray(Pbkdf2.SALT_BYTES).also(input::readFully)
        val iterations = input.readInt()
        if (iterations !in MIN_ITERATIONS..MAX_ITERATIONS) {
            throw BackupFormatException("Backup asks for $iterations iterations")
        }
        return Header(salt, iterations)
    }

    /**
     * The passphrase is cleared by the caller. The derived key then lives inside the returned cipher
     * for as long as the backup is being written or read, exactly as the DEK lives inside the vault's
     * own cipher, and a [VaultCipher] is what seals the rest: a backup is encrypted by the same code
     * as everything else.
     */
    fun cipherFor(passphrase: CharArray, header: Header): VaultCipher =
        VaultCipher(Pbkdf2.derive(passphrase, header.salt, header.iterations))

    private const val MIN_ITERATIONS = 100_000
    private const val MAX_ITERATIONS = 5_000_000
    private val MAGIC = byteArrayOf('H'.code.toByte(), 'T'.code.toByte(), 'S'.code.toByte(), 'U'.code.toByte(), 'B'.code.toByte(), 'A'.code.toByte(), 'K'.code.toByte(), 1)
}
