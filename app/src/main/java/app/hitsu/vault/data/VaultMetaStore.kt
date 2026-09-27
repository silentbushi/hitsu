package app.hitsu.vault.data

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.StandardCopyOption

interface VaultMetaStore {
    /** True when a vault exists on disk, even if its metadata cannot be parsed. */
    suspend fun exists(): Boolean

    suspend fun read(): VaultMeta?

    suspend fun write(meta: VaultMeta)
}

class FileVaultMetaStore(
    private val file: File,
    private val ioDispatcher: CoroutineDispatcher,
) : VaultMetaStore {

    // encodeDefaults keeps `version` and the attempt counters in the file: a later migration should be
    // able to read what a vault was written with instead of inferring it from a missing field.
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    override suspend fun exists(): Boolean = withContext(ioDispatcher) { file.exists() }

    override suspend fun read(): VaultMeta? = withContext(ioDispatcher) {
        if (!file.exists()) return@withContext null
        try {
            json.decodeFromString<VaultMeta>(file.readText())
        } catch (_: SerializationException) {
            null
        } catch (_: IOException) {
            null
        }
    }

    override suspend fun write(meta: VaultMeta) = withContext(ioDispatcher) {
        file.parentFile?.mkdirs()
        val temp = File(file.parentFile, "${file.name}.tmp")
        temp.writeText(json.encodeToString(meta))
        Files.move(temp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING)
        Unit
    }
}
