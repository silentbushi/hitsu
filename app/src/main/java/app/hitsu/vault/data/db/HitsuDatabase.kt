package app.hitsu.vault.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [MediaEntity::class, AlbumEntity::class, AlbumMediaCrossRef::class],
    version = 3,
    exportSchema = true,
)
abstract class HitsuDatabase : RoomDatabase() {
    abstract fun mediaDao(): MediaDao

    abstract fun albumDao(): AlbumDao

    companion object {
        /** Adds the content fingerprint that keeps the same file from being imported twice. */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE media ADD COLUMN contentFingerprint TEXT")
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS index_media_contentFingerprint " +
                        "ON media(contentFingerprint)",
                )
            }
        }

        /** Spec §9: albums, and the join that says what is in them. */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS albums (" +
                        "id TEXT NOT NULL PRIMARY KEY, " +
                        "name TEXT NOT NULL, " +
                        "createdAt INTEGER NOT NULL)",
                )
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_albums_name ON albums(name)")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS album_media (" +
                        "albumId TEXT NOT NULL, " +
                        "mediaId TEXT NOT NULL, " +
                        "addedAt INTEGER NOT NULL, " +
                        "PRIMARY KEY(albumId, mediaId), " +
                        "FOREIGN KEY(albumId) REFERENCES albums(id) ON DELETE CASCADE, " +
                        "FOREIGN KEY(mediaId) REFERENCES media(id) ON DELETE CASCADE)",
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_album_media_mediaId ON album_media(mediaId)",
                )
            }
        }
    }
}
