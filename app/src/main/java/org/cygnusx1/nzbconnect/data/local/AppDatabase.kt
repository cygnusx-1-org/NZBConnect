package org.cygnusx1.nzbconnect.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [IndexerEntity::class, CategoryEntity::class, SearchHistoryEntity::class],
    version = 2,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun indexerDao(): IndexerDao
    abstract fun categoryDao(): CategoryDao
    abstract fun searchHistoryDao(): SearchHistoryDao
}
