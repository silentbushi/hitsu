package app.hitsu.vault.data.download

import app.hitsu.vault.domain.VaultGateway
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile

class SavedCookies(val domain: String, val createdAtMillis: Long)

/**
 * Session cookies for sites that will not show a post without one.
 *
 * These are live credentials for the user's accounts, so they are kept sealed with the vault key
 * like everything else, and only written out in the clear for as long as yt-dlp needs to read them.
 */
class CookieStore(
    private val directory: File,
    private val vault: VaultGateway,
    private val ioDispatcher: CoroutineDispatcher,
) {

    suspend fun list(): List<SavedCookies> = withContext(ioDispatcher) {
        directory.listFiles().orEmpty()
            .filter { it.isFile && it.name.endsWith(EXTENSION) }
            .map { SavedCookies(it.name.removeSuffix(EXTENSION), it.lastModified()) }
            .sortedBy { it.domain }
    }

    suspend fun save(domain: String, netscapeLines: List<String>): Boolean = withContext(ioDispatcher) {
        val cipher = vault.cipher ?: return@withContext false
        if (netscapeLines.isEmpty()) return@withContext false
        directory.mkdirs()
        val body = (listOf(HEADER) + netscapeLines).joinToString("\n", postfix = "\n")
        file(domain).writeBytes(cipher.encrypt(body.toByteArray()))
        true
    }

    suspend fun delete(domain: String) = withContext(ioDispatcher) {
        file(domain).delete()
        Unit
    }

    /**
     * Writes every saved cookie into one plaintext file, hands it to [block], and shreds it after.
     * yt-dlp can only read cookies from a file, so this is the shortest life that file can have.
     */
    suspend fun <T> withCookies(workDirectory: File, block: suspend (File?) -> T): T {
        val cipher = vault.cipher
        val saved = directory.listFiles().orEmpty().filter { it.isFile && it.name.endsWith(EXTENSION) }
        if (cipher == null || saved.isEmpty()) return block(null)

        val target = withContext(ioDispatcher) {
            workDirectory.mkdirs()
            val plain = File(workDirectory, "cookies.txt")
            val body = buildString {
                appendLine(HEADER)
                saved.forEach { file ->
                    runCatching { cipher.decrypt(file.readBytes()).decodeToString() }
                        .getOrNull()
                        ?.lineSequence()
                        ?.filter { it.isNotBlank() && !it.startsWith("#") }
                        ?.forEach { appendLine(it) }
                }
            }
            plain.writeText(body)
            plain
        }
        return try {
            block(target)
        } finally {
            withContext(ioDispatcher) { shred(target) }
        }
    }

    private fun file(domain: String) = File(directory, domain + EXTENSION)

    private fun shred(file: File) {
        try {
            if (file.exists()) {
                RandomAccessFile(file, "rws").use { raf ->
                    raf.write(ByteArray(raf.length().toInt().coerceAtMost(MAX_SHRED_BYTES)))
                }
            }
        } catch (_: IOException) {
            // Best effort; it is deleted either way.
        } finally {
            file.delete()
        }
    }

    private companion object {
        const val EXTENSION = ".hitsu"
        const val HEADER = "# Netscape HTTP Cookie File"
        const val MAX_SHRED_BYTES = 1 shl 20
    }
}

/**
 * Turns what the WebView knows ("a=1; b=2") into the tab-separated lines yt-dlp expects. The
 * WebView does not hand over each cookie's own domain or expiry, so the host being visited and a
 * year from now are the honest approximations.
 */
fun netscapeLines(host: String, rawCookies: String, expiresAtSeconds: Long): List<String> =
    rawCookies.split(";")
        .mapNotNull { pair ->
            val trimmed = pair.trim()
            val separator = trimmed.indexOf('=')
            if (separator <= 0) return@mapNotNull null
            val name = trimmed.substring(0, separator)
            val value = trimmed.substring(separator + 1)
            val domain = if (host.startsWith(".")) host else ".$host"
            listOf(domain, "TRUE", "/", "TRUE", expiresAtSeconds.toString(), name, value)
                .joinToString("\t")
        }
