package app.hitsu.vault.data

import app.hitsu.vault.data.db.AlbumDao
import app.hitsu.vault.data.db.AlbumEntity
import app.hitsu.vault.data.db.AlbumMediaCrossRef
import app.hitsu.vault.data.db.AlbumWithCount
import app.hitsu.vault.data.db.MediaEntity
import app.hitsu.vault.domain.Clock
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The rules an album has to keep: one name means one album whatever the casing, renaming cannot
 * collide with another, and asking for a destination by name makes it the first time it is needed —
 * which is what puts every download in «Descargas» without the user creating anything.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AlbumRepositoryTest {

    private val dispatcher = StandardTestDispatcher()
    private val dao = FakeAlbumDao()
    private var now = 1_000L
    private val repository = AlbumRepository(dao, Clock { now }, dispatcher)

    @Test
    fun theSameNameInAnotherCasingIsTheSameAlbum() = runTest(dispatcher) {
        val first = repository.create("Viajes") as AlbumCreation.Created

        val second = repository.create("viajes")

        assertTrue(second is AlbumCreation.NameTaken)
        assertEquals(first.id, (second as AlbumCreation.NameTaken).id)
        assertEquals(1, dao.albums.size)
    }

    @Test
    fun aNameIsTrimmedAndCannotBeBlank() = runTest(dispatcher) {
        val created = repository.create("  Recibos  ")

        assertTrue(created is AlbumCreation.Created)
        assertEquals("Recibos", dao.albums.single().name)
        assertEquals(AlbumCreation.Invalid, repository.create("   "))
    }

    @Test
    fun renamingOntoAnotherAlbumIsRefused() = runTest(dispatcher) {
        repository.create("Viajes")
        val recibos = repository.create("Recibos") as AlbumCreation.Created

        assertFalse(repository.rename(recibos.id, "viajes"))
        assertTrue(repository.rename(recibos.id, "Facturas"))
        assertEquals("Facturas", dao.albums.first { it.id == recibos.id }.name)
    }

    @Test
    fun aDestinationIsMadeTheFirstTimeItIsNeededAndReusedAfter() = runTest(dispatcher) {
        val first = repository.destination(AlbumPreferences.DEFAULT_DOWNLOAD_ALBUM)
        val second = repository.destination(AlbumPreferences.DEFAULT_DOWNLOAD_ALBUM)

        assertEquals(first, second)
        assertEquals(1, dao.albums.size)
        assertEquals("Descargas", dao.albums.single().name)
    }

    @Test
    fun noDestinationMeansNoAlbum() = runTest(dispatcher) {
        assertNull(repository.destination(null))
        assertNull(repository.destination(""))
        assertTrue(dao.albums.isEmpty())
    }

    @Test
    fun addingTheSameItemTwiceLeavesOneMembership() = runTest(dispatcher) {
        val album = repository.create("Viajes") as AlbumCreation.Created

        repository.add(album.id, listOf("a", "b"))
        repository.add(album.id, listOf("b", "c"))

        assertEquals(3, dao.entries.size)
    }

    @Test
    fun theSnapshotSaysWhatBelongedTogether() = runTest(dispatcher) {
        val viajes = repository.create("Viajes") as AlbumCreation.Created
        repository.create("Recibos")
        repository.add(viajes.id, listOf("a", "b"))

        val snapshot = repository.snapshot()

        assertEquals(listOf("Recibos", "Viajes"), snapshot.map { it.name })
        assertEquals(listOf("a", "b"), snapshot.first { it.name == "Viajes" }.mediaIds)
        assertTrue(snapshot.first { it.name == "Recibos" }.mediaIds.isEmpty())
    }

    @Test
    fun removingFromAnAlbumTouchesNothingElse() = runTest(dispatcher) {
        val album = repository.create("Viajes") as AlbumCreation.Created
        repository.add(album.id, listOf("a", "b"))

        repository.remove(album.id, listOf("a"))

        assertEquals(listOf("b"), dao.entries.map { it.mediaId })
        assertEquals(1, dao.albums.size)
    }
}

/** Enough of the DAO to hold albums and memberships in memory, with its uniqueness rule. */
private class FakeAlbumDao : AlbumDao {
    val albums = mutableListOf<AlbumEntity>()
    val entries = mutableListOf<AlbumMediaCrossRef>()

    override fun observeByName(): Flow<List<AlbumWithCount>> = flowOf(counted())

    override fun observeByCreated(): Flow<List<AlbumWithCount>> = flowOf(counted())

    override fun observeAlbum(id: String): Flow<AlbumEntity?> =
        flowOf(albums.firstOrNull { it.id == id })

    override suspend fun byName(name: String): AlbumEntity? =
        albums.firstOrNull { it.name.equals(name, ignoreCase = true) }

    override suspend fun byId(id: String): AlbumEntity? = albums.firstOrNull { it.id == id }

    override suspend fun insert(album: AlbumEntity) {
        check(byName(album.name) == null) { "Name already taken" }
        albums += album
    }

    override suspend fun rename(id: String, name: String) {
        val index = albums.indexOfFirst { it.id == id }
        if (index >= 0) albums[index] = albums[index].copy(name = name)
    }

    override suspend fun delete(id: String) {
        albums.removeAll { it.id == id }
        entries.removeAll { it.albumId == id }
    }

    override suspend fun addAll(entries: List<AlbumMediaCrossRef>) {
        entries.forEach { entry ->
            val already = this.entries.any { it.albumId == entry.albumId && it.mediaId == entry.mediaId }
            if (!already) this.entries += entry
        }
    }

    override suspend fun remove(albumId: String, mediaIds: List<String>) {
        entries.removeAll { it.albumId == albumId && it.mediaId in mediaIds }
    }

    override fun observeMedia(albumId: String): Flow<List<MediaEntity>> = flowOf(emptyList())

    override suspend fun albumsOf(mediaId: String): List<String> =
        entries.filter { it.mediaId == mediaId }.map { it.albumId }

    override suspend fun all(): List<AlbumEntity> = albums.sortedBy { it.name.lowercase() }

    override suspend fun allEntries(): List<AlbumMediaCrossRef> = entries.toList()

    private fun counted(): List<AlbumWithCount> = albums.map { album ->
        AlbumWithCount(
            id = album.id,
            name = album.name,
            createdAt = album.createdAt,
            itemCount = entries.count { it.albumId == album.id },
        )
    }
}
