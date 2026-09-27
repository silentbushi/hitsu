package app.hitsu.vault.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface AlbumDao {

    @Query(
        "SELECT a.id, a.name, a.createdAt, COUNT(m.mediaId) AS itemCount FROM albums a " +
            "LEFT JOIN album_media m ON m.albumId = a.id " +
            "GROUP BY a.id ORDER BY a.name COLLATE NOCASE ASC",
    )
    fun observeByName(): Flow<List<AlbumWithCount>>

    @Query(
        "SELECT a.id, a.name, a.createdAt, COUNT(m.mediaId) AS itemCount FROM albums a " +
            "LEFT JOIN album_media m ON m.albumId = a.id " +
            "GROUP BY a.id ORDER BY a.createdAt DESC",
    )
    fun observeByCreated(): Flow<List<AlbumWithCount>>

    @Query("SELECT * FROM albums WHERE id = :id")
    fun observeAlbum(id: String): Flow<AlbumEntity?>

    @Query("SELECT * FROM albums WHERE name = :name COLLATE NOCASE LIMIT 1")
    suspend fun byName(name: String): AlbumEntity?

    @Query("SELECT * FROM albums WHERE id = :id")
    suspend fun byId(id: String): AlbumEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(album: AlbumEntity)

    @Query("UPDATE albums SET name = :name WHERE id = :id")
    suspend fun rename(id: String, name: String)

    /** Takes the memberships with it, never the media (spec §9). */
    @Query("DELETE FROM albums WHERE id = :id")
    suspend fun delete(id: String)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun addAll(entries: List<AlbumMediaCrossRef>)

    @Query("DELETE FROM album_media WHERE albumId = :albumId AND mediaId IN (:mediaIds)")
    suspend fun remove(albumId: String, mediaIds: List<String>)

    @Query(
        "SELECT m.* FROM media m JOIN album_media am ON am.mediaId = m.id " +
            "WHERE am.albumId = :albumId " +
            "ORDER BY COALESCE(m.takenAt, m.importedAt) DESC, m.id DESC",
    )
    fun observeMedia(albumId: String): Flow<List<MediaEntity>>

    @Query("SELECT albumId FROM album_media WHERE mediaId = :mediaId")
    suspend fun albumsOf(mediaId: String): List<String>
}
