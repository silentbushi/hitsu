package app.hitsu.vault.data.backup

import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.EOFException
import java.io.InputStream
import java.io.OutputStream

/**
 * Spec §7.10: what lives inside the encrypted layer of a backup file. A name, then the bytes in
 * length-prefixed blocks, then a zero-length block; entry after entry, ending with a name of length
 * zero.
 *
 * The body is split into blocks rather than announced with a total length because neither end knows
 * that length in time: the file is written straight into whatever the user picked through SAF, which
 * cannot be rewound to fill a size in afterwards, and what a photo weighs inside the vault is not what
 * the index says it weighed when it was imported, since its location was stripped on the way in.
 *
 * Deliberately not a ZIP: media is already compressed, so deflating it buys nothing, and a format
 * written here in fifty lines can be read back without depending on how a library behaves in five
 * years. Everything streams, so a backup of gigabytes is never held in memory.
 */
object BackupArchive {

    const val MANIFEST = "manifest.json"

    /** Every media entry is named this way, so a reader can tell them from anything added later. */
    fun mediaEntry(id: String): String = "media/$id"

    class Writer(sink: OutputStream) : AutoCloseable {
        private val out = DataOutputStream(sink)

        init {
            out.write(MAGIC)
        }

        /** [body] writes the whole entry; the stream it is handed must not be used afterwards. */
        fun write(name: String, body: (OutputStream) -> Unit) {
            val encoded = name.toByteArray()
            out.writeInt(encoded.size)
            out.write(encoded)
            BlockOutputStream(out).use(body)
        }

        fun writeBytes(name: String, bytes: ByteArray) = write(name) { it.write(bytes) }

        override fun close() {
            out.writeInt(END)
            out.flush()
        }
    }

    class Entry(val name: String)

    class Reader(source: InputStream) {
        private val input = DataInputStream(source)

        init {
            val magic = ByteArray(MAGIC.size)
            try {
                input.readFully(magic)
            } catch (_: EOFException) {
                throw BackupFormatException("File is too short to be a backup")
            }
            if (!magic.contentEquals(MAGIC)) throw BackupFormatException("Not a Hitsu backup")
        }

        /** @return the next entry, or null at the end. Its body must be read before moving on. */
        fun next(): Entry? {
            val nameLength = try {
                input.readInt()
            } catch (_: EOFException) {
                throw BackupFormatException("Backup ends in the middle of an entry")
            }
            if (nameLength == END) return null
            if (nameLength < 0 || nameLength > MAX_NAME_BYTES) {
                throw BackupFormatException("Entry name of $nameLength bytes")
            }
            return Entry(ByteArray(nameLength).also(input::readFully).decodeToString())
        }

        fun read(sink: OutputStream): Long {
            var total = 0L
            val buffer = ByteArray(BLOCK_BYTES)
            while (true) {
                val length = readBlockLength()
                if (length == END) return total
                var left = length
                while (left > 0) {
                    val read = input.read(buffer, 0, minOf(left, buffer.size))
                    if (read <= 0) throw BackupFormatException("Backup is cut short inside an entry")
                    sink.write(buffer, 0, read)
                    left -= read
                    total += read
                }
            }
        }

        fun readBytes(): ByteArray {
            val collected = java.io.ByteArrayOutputStream()
            read(collected)
            return collected.toByteArray()
        }

        private fun readBlockLength(): Int {
            val length = try {
                input.readInt()
            } catch (_: EOFException) {
                throw BackupFormatException("Backup is cut short inside an entry")
            }
            if (length < 0 || length > BLOCK_BYTES) throw BackupFormatException("Block of $length bytes")
            return length
        }
    }

    /** Buffers so the blocks are worth their four-byte header, and closes the entry with a zero. */
    private class BlockOutputStream(private val sink: DataOutputStream) : OutputStream() {
        private val buffer = ByteArray(BLOCK_BYTES)
        private var filled = 0
        private var closed = false

        override fun write(b: Int) {
            if (filled == buffer.size) flushBlock()
            buffer[filled++] = b.toByte()
        }

        override fun write(b: ByteArray, off: Int, len: Int) {
            var written = 0
            while (written < len) {
                if (filled == buffer.size) flushBlock()
                val step = minOf(len - written, buffer.size - filled)
                b.copyInto(buffer, filled, off + written, off + written + step)
                filled += step
                written += step
            }
        }

        override fun close() {
            if (closed) return
            closed = true
            flushBlock()
            sink.writeInt(END)
        }

        private fun flushBlock() {
            if (filled == 0) return
            sink.writeInt(filled)
            sink.write(buffer, 0, filled)
            filled = 0
        }
    }

    private const val END = 0
    private const val BLOCK_BYTES = 64 * 1024
    private const val MAX_NAME_BYTES = 4096
    private val MAGIC = "HTSUARC".toByteArray() + 1
}

/** The file is not a backup, or not one this version can read. */
class BackupFormatException(message: String) : Exception(message)
