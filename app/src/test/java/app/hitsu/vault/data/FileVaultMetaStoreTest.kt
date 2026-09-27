package app.hitsu.vault.data

import app.hitsu.vault.domain.PinKind
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class FileVaultMetaStoreTest {

    @get:Rule
    val folder = TemporaryFolder()

    private val file: File get() = File(folder.root, "vault/meta.json")
    private val store: FileVaultMetaStore get() = FileVaultMetaStore(file, Dispatchers.Unconfined)

    private val meta = VaultMeta(
        wrappedDek = "d3JhcHBlZA==",
        kdfSalt = "c2FsdA==",
        kdfAlgorithm = "PBKDF2WithHmacSHA256",
        kdfIterations = 310_000,
        pinKind = PinKind.Numeric,
        pinLength = 6,
    )

    @Test
    fun readsBackWhatItWrote() = runTest {
        assertFalse(store.exists())
        assertNull(store.read())

        store.write(meta)

        assertTrue(store.exists())
        assertEquals(meta, store.read())
        assertTrue(file.readText().contains("\"version\":${VaultMeta.CURRENT_VERSION}"))
    }

    @Test
    fun overwritesPreviousMetaAndLeavesNoTempFile() = runTest {
        store.write(meta)
        store.write(meta.copy(failedUnlocks = 3, retryAtMillis = 1_000L))

        assertEquals(3, store.read()?.failedUnlocks)
        assertEquals(listOf("meta.json"), file.parentFile!!.list()!!.sorted())
    }

    @Test
    fun corruptMetaReadsAsNullButStillCountsAsExisting() = runTest {
        file.parentFile!!.mkdirs()
        file.writeText("{ not json")

        assertTrue(store.exists())
        assertNull(store.read())
    }
}
