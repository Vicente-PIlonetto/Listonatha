package dev.viko.listaanimes.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import dev.viko.listaanimes.domain.ListEntry
import dev.viko.listaanimes.domain.Media

@Database(
    entities = [
        Media::class, ListEntry::class, SpecialScore::class, PendingChange::class, ReviewDraft::class,
        ReviewEntity::class, AiringEntity::class, CacheBlob::class, NotifiedEpisode::class,
    ],
    version = 2,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun media(): MediaDao
    abstract fun entries(): EntryDao
    abstract fun specials(): SpecialDao
    abstract fun pending(): PendingDao
    abstract fun reviews(): ReviewDao
    abstract fun drafts(): DraftDao
    abstract fun airing(): AiringDao
    abstract fun cache(): CacheDao
    abstract fun notified(): NotifiedDao

    companion object {
        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "lista-animes.db").addMigrations(MIGRATION_1_2).build()

        /** v2: coluna isAdult (obras 18+). */
        val MIGRATION_1_2 = object : androidx.room.migration.Migration(1, 2) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE media ADD COLUMN isAdult INTEGER NOT NULL DEFAULT 0")
            }
        }
    }
}
