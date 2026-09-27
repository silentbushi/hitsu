package app.hitsu.vault.crypto

import java.io.EOFException
import java.io.InputStream
import java.io.OutputStream
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Encrypts vault objects with the session DEK. Handed out only while the vault is unlocked so the
 * DEK itself never leaves the gateway.
 */
class VaultCipher internal constructor(private val dek: ByteArray) {

    fun encrypt(plaintext: ByteArray): ByteArray = AesGcm.encrypt(dek, plaintext)

    fun decrypt(payload: ByteArray): ByteArray = AesGcm.decrypt(dek, payload)

    /**
     * Identifies a file so the same one is not imported twice. It is keyed, not a plain digest:
     * the index is stored in the clear, and a bare SHA-256 would let anyone holding a photo prove
     * it is in the vault by comparing hashes. Derived from the DEK, this says nothing without it.
     */
    fun fingerprint(bytes: ByteArray): String {
        val subKey = fingerprintKey()
        return try {
            hmac(subKey, bytes).encodeBase64()
        } finally {
            subKey.fill(0)
        }
    }

    /**
     * Streams a file into the vault in independently sealed chunks, returning the fingerprint of
     * the plaintext from the same pass.
     *
     * Chunking is what makes video workable: GCM only releases plaintext once it has checked the
     * tag, so a single-sealed file would have to be held whole in memory to be decrypted, and could
     * report no progress along the way.
     *
     * Each chunk is framed as `flag || iv || ciphertext+tag`, and the flag says whether it is the
     * last one. The flag is also associated data, together with the chunk index, so chunks cannot
     * be reordered or swapped, and a file whose tail was cut off fails instead of looking complete:
     * reading stops only when a chunk that claims to be the last one has been verified.
     */
    fun encryptTo(source: InputStream, sink: OutputStream): String {
        val mac = Mac.getInstance(HMAC).apply { init(SecretKeySpec(fingerprintKey(), HMAC)) }
        sink.write(MAGIC)

        val buffer = ByteArray(CHUNK_BYTES)
        val lookahead = ByteArray(CHUNK_BYTES)
        var index = 0L
        var pending = source.readFully(buffer)
        while (true) {
            val following = if (pending == CHUNK_BYTES) source.readFully(lookahead) else 0
            val last = following == 0
            mac.update(buffer, 0, pending)
            val cipher = newCipher(Cipher.ENCRYPT_MODE, iv = null, index = index, last = last)
            sink.write(if (last) LAST_CHUNK else MORE_CHUNKS)
            sink.write(cipher.iv)
            sink.write(cipher.doFinal(buffer, 0, pending))
            if (last) break
            lookahead.copyInto(buffer, endIndex = following)
            pending = following
            index++
        }

        return mac.doFinal().encodeBase64()
    }

    /** [onProgress] reports plaintext bytes written so far, for the "Descifrando" indicator. */
    fun decryptTo(source: InputStream, sink: OutputStream, onProgress: (Long) -> Unit = {}) {
        val header = ByteArray(MAGIC.size)
        val headerRead = source.readFully(header)
        if (headerRead != MAGIC.size || !header.contentEquals(MAGIC)) {
            // A file written before chunking existed: small enough to unseal in one go.
            val legacy = header.copyOf(headerRead) + source.readBytes()
            val plaintext = decrypt(legacy)
            sink.write(plaintext)
            onProgress(plaintext.size.toLong())
            return
        }

        val frame = ByteArray(1 + AesGcm.IV_BYTES)
        val sealed = ByteArray(CHUNK_BYTES + TAG_BYTES)
        var index = 0L
        var written = 0L
        while (true) {
            if (source.readFully(frame) != frame.size) throw EOFException("Truncated file")
            val last = frame[0] == LAST_CHUNK[0]
            val read = source.readFully(sealed)
            if (read < TAG_BYTES) throw EOFException("Truncated chunk")
            val cipher = newCipher(
                mode = Cipher.DECRYPT_MODE,
                iv = frame.copyOfRange(1, frame.size),
                index = index,
                last = last,
            )
            val plaintext = cipher.doFinal(sealed, 0, read)
            sink.write(plaintext)
            written += plaintext.size
            onProgress(written)
            if (last) break
            index++
        }
    }

    /**
     * The same chunked format as [encryptTo], for callers that produce their own bytes instead of
     * copying a stream — the backup writes a whole archive through here (§7.10). Sealing a chunk only
     * happens once more data has arrived, so the chunk that closes the stream is the one marked last,
     * and closing is what finishes the file: a sink that is never closed produces no last chunk, which
     * is exactly the truncation the format is meant to catch.
     */
    fun encryptingSink(sink: OutputStream): OutputStream = ChunkSink(sink)

    /** The reading half of [encryptingSink]. */
    fun decryptingSource(source: InputStream): InputStream = ChunkSource(source)

    private inner class ChunkSink(private val sink: OutputStream) : OutputStream() {
        private val buffer = ByteArray(CHUNK_BYTES)
        private var filled = 0
        private var index = 0L
        private var closed = false

        init {
            sink.write(MAGIC)
        }

        override fun write(b: Int) {
            if (filled == buffer.size) seal(last = false)
            buffer[filled++] = b.toByte()
        }

        override fun write(b: ByteArray, off: Int, len: Int) {
            var written = 0
            while (written < len) {
                if (filled == buffer.size) seal(last = false)
                val step = minOf(len - written, buffer.size - filled)
                b.copyInto(buffer, filled, off + written, off + written + step)
                filled += step
                written += step
            }
        }

        override fun close() {
            if (closed) return
            closed = true
            seal(last = true)
            sink.flush()
        }

        private fun seal(last: Boolean) {
            val cipher = newCipher(Cipher.ENCRYPT_MODE, iv = null, index = index, last = last)
            sink.write(if (last) LAST_CHUNK else MORE_CHUNKS)
            sink.write(cipher.iv)
            sink.write(cipher.doFinal(buffer, 0, filled))
            buffer.fill(0, 0, filled)
            filled = 0
            index++
        }
    }

    private inner class ChunkSource(private val source: InputStream) : InputStream() {
        private val frame = ByteArray(1 + AesGcm.IV_BYTES)
        private val sealed = ByteArray(CHUNK_BYTES + TAG_BYTES)
        private var plain = ByteArray(0)
        private var offset = 0
        private var index = 0L
        private var lastSeen = false
        private var started = false

        override fun read(): Int {
            val one = ByteArray(1)
            return if (read(one, 0, 1) < 0) -1 else one[0].toInt() and 0xFF
        }

        override fun read(b: ByteArray, off: Int, len: Int): Int {
            if (len == 0) return 0
            if (!fill()) return -1
            val step = minOf(len, plain.size - offset)
            plain.copyInto(b, off, offset, offset + step)
            offset += step
            return step
        }

        private fun fill(): Boolean {
            if (offset < plain.size) return true
            if (!started) {
                val header = ByteArray(MAGIC.size)
                if (source.readFully(header) != MAGIC.size || !header.contentEquals(MAGIC)) {
                    throw EOFException("Not a chunked stream")
                }
                started = true
            }
            while (!lastSeen) {
                readChunk()
                if (offset < plain.size) return true
            }
            return false
        }

        private fun readChunk() {
            if (source.readFully(frame) != frame.size) throw EOFException("Truncated file")
            val last = frame[0] == LAST_CHUNK[0]
            val read = source.readFully(sealed)
            if (read < TAG_BYTES) throw EOFException("Truncated chunk")
            val cipher = newCipher(
                mode = Cipher.DECRYPT_MODE,
                iv = frame.copyOfRange(1, frame.size),
                index = index,
                last = last,
            )
            plain = cipher.doFinal(sealed, 0, read)
            offset = 0
            index++
            lastSeen = last
        }
    }

    private fun newCipher(mode: Int, iv: ByteArray?, index: Long, last: Boolean): Cipher =
        Cipher.getInstance(AesGcm.TRANSFORMATION).apply {
            val key = SecretKeySpec(dek, "AES")
            if (iv == null) init(mode, key) else init(mode, key, GCMParameterSpec(AesGcm.TAG_BITS, iv))
            updateAAD(associatedData(index, last))
        }

    private fun associatedData(index: Long, last: Boolean): ByteArray {
        val aad = ByteArray(9)
        for (i in 0 until 8) {
            aad[i] = (index shr (8 * (7 - i))).toByte()
        }
        aad[8] = if (last) 1 else 0
        return aad
    }

    private fun fingerprintKey(): ByteArray = hmac(dek, FINGERPRINT_LABEL.toByteArray())

    private fun hmac(key: ByteArray, message: ByteArray): ByteArray =
        Mac.getInstance(HMAC).apply { init(SecretKeySpec(key, HMAC)) }.doFinal(message)

    /** Reads until the buffer is full or the stream ends, so a short read is not mistaken for EOF. */
    private fun InputStream.readFully(buffer: ByteArray): Int {
        var filled = 0
        while (filled < buffer.size) {
            val read = read(buffer, filled, buffer.size - filled)
            if (read <= 0) break
            filled += read
        }
        return filled
    }

    private companion object {
        const val HMAC = "HmacSHA256"
        const val FINGERPRINT_LABEL = "hitsu.fingerprint.v1"
        const val CHUNK_BYTES = 1 shl 20
        val MORE_CHUNKS = byteArrayOf(0)
        val LAST_CHUNK = byteArrayOf(1)
        const val TAG_BYTES = AesGcm.TAG_BITS / 8
        val MAGIC = byteArrayOf('H'.code.toByte(), 'T'.code.toByte(), 'S'.code.toByte(), 'U'.code.toByte(), 1)
    }
}
