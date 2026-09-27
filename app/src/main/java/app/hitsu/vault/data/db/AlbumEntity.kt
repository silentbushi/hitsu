package app.hitsu.vault.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Spec §9 and §14. An album is a name and a list of what belongs to it — nothing else, and nothing
 * nested. The name is unique ignoring case, so "Viajes" and "viajes" cannot both exist and confuse
 * whoever comes back to the list a month later.
 */
@Entity(
    tableName = "albums",
    indices = [Index(value = ["name"], unique = true)],
)
data class AlbumEntity(
    @PrimaryKey val id: String,
    val name: String,
    val createdAt: Long,
)

/**
 * The join between an album and what it holds. Both sides cascade: deleting media takes its
 * memberships with it, and deleting an album takes the memberships but never the media — an album is
 * a way of looking at the vault, not a place where things live.
 */
@Entity(
    tableName = "album_media",
    primaryKeys = ["albumId", "mediaId"],
    foreignKeys = [
        ForeignKey(
            entity = AlbumEntity::class,
            parentColumns = ["id"],
            childColumns = ["albumId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = MediaEntity::class,
            parentColumns = ["id"],
            childColumns = ["mediaId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("mediaId")],
)
data class AlbumMediaCrossRef(
    val albumId: String,
    val mediaId: String,
    val addedAt: Long,
)

/** An album plus how many things are in it, which is what the list shows. */
data class AlbumWithCount(
    val id: String,
    val name: String,
    val createdAt: Long,
    val itemCount: Int,
)
