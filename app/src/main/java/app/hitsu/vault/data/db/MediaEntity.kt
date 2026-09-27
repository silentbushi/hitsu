package app.hitsu.vault.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import app.hitsu.vault.domain.MediaItem
import app.hitsu.vault.domain.MediaType

/**
 * Spec §14. Paths are relative to the vault directory; the encrypted bytes never live in Room.
 * [contentFingerprint] is nullable because SQLite treats NULLs as distinct, which lets rows written
 * before fingerprinting existed sit under the unique index until the backfill fills them in.
 */
@Entity(
    tableName = "media",
    indices = [Index(value = ["contentFingerprint"], unique = true)],
)
data class MediaEntity(
    @PrimaryKey val id: String,
    val type: MediaType,
    val mime: String,
    val objectPath: String,
    val thumbPath: String,
    val width: Int,
    val height: Int,
    val durationMs: Long?,
    val sizeBytes: Long,
    val takenAt: Long?,
    val importedAt: Long,
    val originalName: String?,
    val contentFingerprint: String? = null,
    val favorite: Boolean = false,
)

fun MediaEntity.toItem(): MediaItem = MediaItem(
    id = id,
    type = type,
    mime = mime,
    width = width,
    height = height,
    durationMs = durationMs,
    sizeBytes = sizeBytes,
    takenAt = takenAt,
    importedAt = importedAt,
    originalName = originalName,
)
