package app.hitsu.vault.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [MediaEntity::class], version = 2, exportSchema = true)
abstract class HitsuDatabase : RoomDatabase() {
    abstract fun mediaDao(): MediaDao

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
    }
}
