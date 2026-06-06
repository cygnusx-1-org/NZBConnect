package org.cygnusx1.nzbconnect.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface IndexerDao {
    @Query("SELECT * FROM indexers ORDER BY id ASC")
    fun observeAll(): Flow<List<IndexerEntity>>

    @Query("SELECT * FROM indexers ORDER BY id ASC")
    suspend fun getAll(): List<IndexerEntity>

    @Query("SELECT * FROM indexers WHERE id = :id")
    suspend fun getById(id: Long): IndexerEntity?

    @Insert
    suspend fun insert(indexer: IndexerEntity): Long

    @Update
    suspend fun update(indexer: IndexerEntity)

    @Delete
    suspend fun delete(indexer: IndexerEntity)
}

@Dao
interface CategoryDao {
    @Query("SELECT * FROM categories WHERE indexerId = :indexerId ORDER BY catId")
    fun observeForIndexer(indexerId: Long): Flow<List<CategoryEntity>>

    @Query("SELECT * FROM categories WHERE indexerId = :indexerId ORDER BY catId")
    suspend fun getForIndexer(indexerId: Long): List<CategoryEntity>

    @Query("DELETE FROM categories WHERE indexerId = :indexerId")
    suspend fun clearForIndexer(indexerId: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(categories: List<CategoryEntity>)
}

@Dao
interface SearchHistoryDao {
    @Query("SELECT * FROM search_history ORDER BY timestamp DESC LIMIT :limit")
    fun observeRecent(limit: Int = 50): Flow<List<SearchHistoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entry: SearchHistoryEntity)

    @Query("DELETE FROM search_history WHERE query = :query")
    suspend fun deleteByQuery(query: String)

    @Query("DELETE FROM search_history")
    suspend fun clear()
}
