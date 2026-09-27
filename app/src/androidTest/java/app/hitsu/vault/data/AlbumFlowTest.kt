package app.hitsu.vault.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.hitsu.vault.data.db.AlbumMediaCrossRef
import app.hitsu.vault.data.db.HitsuDatabase
import app.hitsu.vault.data.db.MediaEntity
import app.hitsu.vault.domain.Clock
import app.hitsu.vault.domain.MediaType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * What albums promise, against a real database rather than a fake DAO: an item can be in two at once
 * and still be in Todos, deleting media forgets its memberships, and deleting an album forgets only
 * itself. The cascades are declared in Room, so only SQLite can really answer for them.
 */
@RunWith(AndroidJUnit4::class)
class AlbumFlowTest {

    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private lateinit var database: HitsuDatabase
    private lateinit var albums: AlbumRepository

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(context, HitsuDatabase::class.java).build()
        albums = AlbumRepository(database.albumDao(), Clock { NOW }, Dispatchers.IO)
    }

    @After
    fun tearDown() = database.close()

    private suspend fun media(id: String, takenAt: Long = NOW): MediaEntity {
        val row = MediaEntity(
            id = id,
            type = MediaType.Photo,
            mime = "image/jpeg",
            objectPath = "objects/$id.hitsu",
            thumbPath = "thumbs/$id.hitsu",
            width = 100,
            height = 100,
            durationMs = null,
            sizeBytes = 1_000,
            takenAt = takenAt,
            importedAt = NOW,
            originalName = "$id.jpg",
            contentFingerprint = id,
        )
        database.mediaDao().insert(row)
        return row
    }

    @Test
    fun oneItemCanLiveInTwoAlbumsAtOnce() = runBlocking {
        val photo = media("a")
        val viajes = albums.create("Viajes") as AlbumCreation.Created
        val familia = albums.create("Familia") as AlbumCreation.Created

        albums.add(viajes.id, listOf(photo.id))
        albums.add(familia.id, listOf(photo.id))

        assertEquals(1, albums.media(viajes.id).first().size)
        assertEquals(1, albums.media(familia.id).first().size)
        // And it is still in the vault at large, which is what Todos shows.
        assertEquals(1, database.mediaDao().count())
    }

    @Test
    fun deletingAnAlbumKeepsItsContents() = runBlocking {
        val photo = media("b")
        val album = albums.create("Recibos") as AlbumCreation.Created
        albums.add(album.id, listOf(photo.id))

        albums.delete(album.id)

        assertNotNull(database.mediaDao().byId(photo.id))
        assertTrue(database.albumDao().allEntries().isEmpty())
    }

    @Test
    fun deletingMediaTakesItsMembershipsWithIt() = runBlocking {
        val photo = media("c")
        val album = albums.create("Mascotas") as AlbumCreation.Created
        albums.add(album.id, listOf(photo.id))

        database.mediaDao().delete(photo.id)

        assertTrue(database.albumDao().allEntries().isEmpty())
        assertNotNull(database.albumDao().byId(album.id))
    }

    @Test
    fun theCoverIsTheNewestThingInTheAlbum() = runBlocking {
        val older = media("old", takenAt = NOW - 10_000)
        val newer = media("new", takenAt = NOW)
        val album = albums.create("Capturas") as AlbumCreation.Created
        albums.add(album.id, listOf(older.id, newer.id))

        val listed = albums.albums(AlbumOrder.Alphabetical).first().single()

        assertEquals(2, listed.itemCount)
        assertEquals(newer.id, listed.coverId)
    }

    @Test
    fun anEmptyAlbumHasNoCover() = runBlocking {
        albums.create("Pendientes")

        val listed = albums.albums(AlbumOrder.Alphabetical).first().single()

        assertEquals(0, listed.itemCount)
        assertNull(listed.coverId)
    }

    @Test
    fun aMembershipCannotPointAtSomethingThatIsNotThere() = runBlocking {
        val album = albums.create("Viajes") as AlbumCreation.Created

        val refused = runCatching {
            database.albumDao().addAll(listOf(AlbumMediaCrossRef(album.id, "ghost", NOW)))
        }

        assertTrue(refused.isFailure)
    }

    private companion object {
        const val NOW = 1_700_000_000_000L
    }
}

