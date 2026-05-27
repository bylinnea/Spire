package no.bylinnea.spire.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Room database for Spire. Single source of truth for all plant data.
 *
 * Use [getDatabase] everywhere in the app. Use [getWidgetDatabase] only from
 * the home screen widget, which runs on the main thread.
 */
@Database(entities = [Plant::class, PlantLog::class, PlantPhoto::class], version = 15, exportSchema = false)
abstract class PlantDatabase : RoomDatabase() {

    abstract fun plantDao(): PlantDao
    abstract fun plantLogDao(): PlantLogDao
    abstract fun plantPhotoDao(): PlantPhotoDao

    companion object {
        @Volatile private var INSTANCE: PlantDatabase? = null

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE plants ADD COLUMN lastWateredDate INTEGER DEFAULT NULL")
            }
        }
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE plants ADD COLUMN photoUri TEXT DEFAULT NULL")
            }
        }
        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE plants ADD COLUMN species TEXT DEFAULT NULL")
                db.execSQL("ALTER TABLE plants ADD COLUMN location TEXT DEFAULT NULL")
                db.execSQL("ALTER TABLE plants ADD COLUMN dateAcquired INTEGER DEFAULT NULL")
                db.execSQL("ALTER TABLE plants ADD COLUMN notes TEXT DEFAULT NULL")
            }
        }
        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE plants ADD COLUMN fertilizerIntervalDays INTEGER DEFAULT NULL")
                db.execSQL("ALTER TABLE plants ADD COLUMN lastFertilizedDate INTEGER DEFAULT NULL")
                db.execSQL("ALTER TABLE plants ADD COLUMN fertilizerType TEXT DEFAULT NULL")
                db.execSQL("ALTER TABLE plants ADD COLUMN repottingIntervalDays INTEGER DEFAULT NULL")
                db.execSQL("ALTER TABLE plants ADD COLUMN lastRepottedDate INTEGER DEFAULT NULL")
                db.execSQL("ALTER TABLE plants ADD COLUMN mistingIntervalDays INTEGER DEFAULT NULL")
                db.execSQL("ALTER TABLE plants ADD COLUMN lastMistedDate INTEGER DEFAULT NULL")
                db.execSQL("ALTER TABLE plants ADD COLUMN rotatingIntervalDays INTEGER DEFAULT NULL")
                db.execSQL("ALTER TABLE plants ADD COLUMN lastRotatedDate INTEGER DEFAULT NULL")
            }
        }
        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS plant_log (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        plantId INTEGER NOT NULL,
                        note TEXT NOT NULL,
                        timestamp INTEGER NOT NULL,
                        FOREIGN KEY (plantId) REFERENCES plants(id) ON DELETE CASCADE
                    )
                """.trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS index_plant_log_plantId ON plant_log(plantId)")
            }
        }
        private val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE plants ADD COLUMN isPetSafe INTEGER DEFAULT NULL")
            }
        }
        private val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE plants ADD COLUMN wateringTip TEXT DEFAULT NULL")
                db.execSQL("ALTER TABLE plants ADD COLUMN fertilizingTip TEXT DEFAULT NULL")
                db.execSQL("ALTER TABLE plants ADD COLUMN repottingTip TEXT DEFAULT NULL")
                db.execSQL("ALTER TABLE plants ADD COLUMN mistingTip TEXT DEFAULT NULL")
                db.execSQL("ALTER TABLE plants ADD COLUMN rotatingTip TEXT DEFAULT NULL")
            }
        }
        private val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE plants ADD COLUMN winterWateringIntervalDays INTEGER DEFAULT NULL")
                db.execSQL("ALTER TABLE plants ADD COLUMN winterFertilizerIntervalDays INTEGER DEFAULT NULL")
                db.execSQL("ALTER TABLE plants ADD COLUMN winterMistingIntervalDays INTEGER DEFAULT NULL")
                db.execSQL("ALTER TABLE plants ADD COLUMN winterScheduleDisabled INTEGER DEFAULT 0")
            }
        }
        private val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE plants ADD COLUMN lastRepotSkippedDate INTEGER DEFAULT NULL")
            }
        }
        private val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE plants ADD COLUMN lightPreference TEXT DEFAULT NULL")
            }
        }
        private val MIGRATION_11_12 = object : Migration(11, 12) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE plants ADD COLUMN cleaningIntervalDays INTEGER DEFAULT NULL")
                db.execSQL("ALTER TABLE plants ADD COLUMN lastCleanedDate INTEGER DEFAULT NULL")
                db.execSQL("ALTER TABLE plants ADD COLUMN temperaturePreference TEXT DEFAULT NULL")
            }
        }
        private val MIGRATION_12_13 = object : Migration(12, 13) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE plants ADD COLUMN cleaningTip TEXT DEFAULT NULL")
            }
        }
        private val MIGRATION_13_14 = object : Migration(13, 14) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS plant_photo (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        plantId INTEGER NOT NULL,
                        photoUri TEXT NOT NULL,
                        timestamp INTEGER NOT NULL,
                        notes TEXT,
                        FOREIGN KEY (plantId) REFERENCES plants(id) ON DELETE CASCADE
                    )
                """.trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS index_plant_photo_plantId ON plant_photo(plantId)")
            }
        }
        private val MIGRATION_14_15 = object : Migration(14, 15) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE plants ADD COLUMN diedDate INTEGER DEFAULT NULL")
            }
        }

        /** Returns the singleton database instance for normal app use. */
        fun getDatabase(context: Context): PlantDatabase {
            return INSTANCE ?: synchronized(this) {
                Room.databaseBuilder(
                    context.applicationContext,
                    PlantDatabase::class.java,
                    "plant_database"
                )
                    .addMigrations(
                        MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4,
                        MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7,
                        MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10,
                        MIGRATION_10_11, MIGRATION_11_12, MIGRATION_12_13,
                        MIGRATION_13_14, MIGRATION_14_15
                    )
                    .build().also { INSTANCE = it }
            }
        }

        /**
         * Returns a non-singleton database instance that allows main thread queries.
         * Only use this from the home screen widget.
         */
        fun getWidgetDatabase(context: Context): PlantDatabase {
            return Room.databaseBuilder(
                context.applicationContext,
                PlantDatabase::class.java,
                "plant_database"
            )
                .allowMainThreadQueries()
                .addMigrations(
                    MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4,
                    MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7,
                    MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10,
                    MIGRATION_10_11, MIGRATION_11_12, MIGRATION_12_13,
                    MIGRATION_13_14, MIGRATION_14_15
                )
                .build()
        }
    }
}