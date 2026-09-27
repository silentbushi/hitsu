package app.hitsu.vault.data

import app.hitsu.vault.data.db.AlbumDao
import app.hitsu.vault.data.db.AlbumEntity
import app.hitsu.vault.data.db.AlbumMediaCrossRef
import app.hitsu.vault.data.db.AlbumWithCount
import app.hitsu.vault.data.db.toItem
import app.hitsu.vault.domain.Clock
import app.hitsu.vault.domain.MediaItem
import android.database.sqlite.SQLiteConstraintException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.util.UUID

/** The order the album list is shown in (spec §9, mockup `05-albumes.png`). */
enum class AlbumOrder { Alphabetical, Newest }

sealed interface AlbumCreation {
    class Created(val id: String) : AlbumCreation

    /** A name already taken, ignoring case. The existing album is offered instead of a duplicate. */
    class NameTaken(val id: String) : AlbumCreation

    data object Invalid : AlbumCreation
}

/**
 * Spec §9: albums are a way of looking at the vault, not a place where things live. Nothing here
 * touches an encrypted object or a media row — the most an album can do is forget about something.
 */
class AlbumRepository(
    private val dao: AlbumDao,
    private val clock: Clock,
    private val ioDispatcher: CoroutineDispatcher,
) {

    fun albums(order: AlbumOrder): Flow<List<AlbumWithCount>> = when (order) {
        AlbumOrder.Alphabetical -> dao.observeByName()
        AlbumOrder.Newest -> dao.observeByCreated()
    }

    fun album(id: String): Flow<AlbumEntity?> = dao.observeAlbum(id)

    fun media(albumId: String): Flow<List<MediaItem>> =
        dao.observeMedia(albumId).map { rows -> rows.map { it.toItem() } }

    suspend fun create(name: String): AlbumCreation = withContext(ioDispatcher) {
        val clean = name.trim()
        if (clean.isEmpty() || clean.length > MAX_NAME) return@withContext AlbumCreation.Invalid
        val existing = dao.byName(clean)
        if (existing != null) return@withContext AlbumCreation.NameTaken(existing.id)
        val album = AlbumEntity(id = UUID.randomUUID().toString(), name = clean, createdAt = clock.now())
        try {
            dao.insert(album)
            AlbumCreation.Created(album.id)
        } catch (_: SQLiteConstraintException) {
            // Two creations of the same name at once; the one that lost takes the winner's album.
            AlbumCreation.NameTaken(dao.byName(clean)?.id ?: return@withContext AlbumCreation.Invalid)
        }
    }

    suspend fun rename(id: String, name: String): Boolean = withContext(ioDispatcher) {
        val clean = name.trim()
        if (clean.isEmpty() || clean.length > MAX_NAME) return@withContext false
        val existing = dao.byName(clean)
        if (existing != null && existing.id != id) return@withContext false
        dao.rename(id, clean)
        true
    }

    /** Spec §9: this forgets the album, not what was in it. */
    suspend fun delete(id: String) = withContext(ioDispatcher) { dao.delete(id) }

    suspend fun add(albumId: String, mediaIds: List<String>) = withContext(ioDispatcher) {
        val now = clock.now()
        dao.addAll(mediaIds.map { AlbumMediaCrossRef(albumId, it, now) })
    }

    suspend fun remove(albumId: String, mediaIds: List<String>) =
        withContext(ioDispatcher) { dao.remove(albumId, mediaIds) }

    /**
     * The album that something new should land in, creating it if it has never been needed. Returns
     * null when nothing is configured, which is what "Ninguno" means in settings.
     */
    suspend fun destination(name: String?): String? = withContext(ioDispatcher) {
        val clean = name?.trim().orEmpty()
        if (clean.isEmpty()) return@withContext null
        dao.byName(clean)?.id ?: when (val created = create(clean)) {
            is AlbumCreation.Created -> created.id
            is AlbumCreation.NameTaken -> created.id
            AlbumCreation.Invalid -> null
        }
    }

    private companion object {
        const val MAX_NAME = 60
    }
}
