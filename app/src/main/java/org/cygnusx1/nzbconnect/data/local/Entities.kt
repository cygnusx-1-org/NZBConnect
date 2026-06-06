package org.cygnusx1.nzbconnect.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "indexers")
data class IndexerEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val baseUrl: String,
    val enabled: Boolean = true,
)

@Entity(
    tableName = "categories",
    indices = [Index(value = ["indexerId"])],
)
data class CategoryEntity(
    @PrimaryKey(autoGenerate = true) val rowId: Long = 0,
    val indexerId: Long,
    val catId: String,
    val name: String,
    val parentId: String = "", // empty for top-level categories
)

@Entity(tableName = "search_history")
data class SearchHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val query: String,
    val indexerId: Long?, // null = "All indexers"
    val searchType: String,
    val timestamp: Long,
)
