package app.hitsu.vault.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import app.hitsu.vault.domain.MediaType
import kotlinx.coroutines.flow.Flow

@Dao
interface MediaDao {

    @Query("SELECT * FROM media ORDER BY COALESCE(takenAt, importedAt) DESC, id DESC")
    fun observeAll(): Flow<List<MediaEntity>>

    @Query(
        "SELECT * FROM media WHERE type = :type ORDER BY COALESCE(takenAt, importedAt) DESC, id DESC",
    )
    fun observeByType(type: MediaType): Flow<List<MediaEntity>>

    @Query("SELECT * FROM media WHERE id = :id")
    suspend fun byId(id: String): MediaEntity?

    @Query("SELECT id FROM media WHERE contentFingerprint = :fingerprint LIMIT 1")
    suspend fun findByFingerprint(fingerprint: String): String?

    @Query("SELECT * FROM media WHERE contentFingerprint IS NULL")
    suspend fun withoutFingerprint(): List<MediaEntity>

    @Query("UPDATE media SET contentFingerprint = :fingerprint WHERE id = :id")
    suspend fun setFingerprint(id: String, fingerprint: String)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(entity: MediaEntity)

    @Query("DELETE FROM media WHERE id = :id")
    suspend fun delete(id: String)

    @Query("SELECT COUNT(*) FROM media")
    suspend fun count(): Int
}
