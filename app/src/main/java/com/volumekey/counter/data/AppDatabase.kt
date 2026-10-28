package com.volumekey.counter.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        Counter::class, Checkpoint::class, CountEvent::class,
        InventoryList::class, InventoryItem::class, InventoryEvent::class
    ],
    version = 3,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun counterDao(): CounterDao

    companion object {
        @Volatile
        private var instance: AppDatabase? = null

        private val MIGRATION_1_2 = object : androidx.room.migration.Migration(1, 2) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `inventory_lists` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`name` TEXT NOT NULL, " +
                        "`createdAt` INTEGER NOT NULL, " +
                        "`itemCount` INTEGER NOT NULL)"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `inventory_items` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`listId` INTEGER NOT NULL, " +
                        "`sortOrder` INTEGER NOT NULL, " +
                        "`name` TEXT NOT NULL, " +
                        "`customField` TEXT NOT NULL, " +
                        "`expectedQty` INTEGER NOT NULL, " +
                        "`countedQty` INTEGER NOT NULL, " +
                        "`touched` INTEGER NOT NULL, " +
                        "`remark` TEXT NOT NULL, " +
                        "FOREIGN KEY(`listId`) REFERENCES `inventory_lists`(`id`) " +
                        "ON UPDATE NO ACTION ON DELETE CASCADE )"
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_inventory_items_listId` ON `inventory_items` (`listId`)")
            }
        }

        private val MIGRATION_2_3 = object : androidx.room.migration.Migration(2, 3) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `inventory_events` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`itemId` INTEGER NOT NULL, " +
                        "`delta` INTEGER NOT NULL, " +
                        "`valueAfter` INTEGER NOT NULL, " +
                        "`timestamp` INTEGER NOT NULL, " +
                        "`source` TEXT NOT NULL, " +
                        "FOREIGN KEY(`itemId`) REFERENCES `inventory_items`(`id`) " +
                        "ON UPDATE NO ACTION ON DELETE CASCADE )"
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_inventory_events_itemId` ON `inventory_events` (`itemId`)")
            }
        }

        fun getInstance(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "volume_counter.db"
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                    .build()
                    .also { instance = it }
            }
    }
}
