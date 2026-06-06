package org.cygnusx1.nzbconnect.domain

/** A configured Newznab indexer. The API key lives in encrypted prefs, keyed by [id]. */
data class Indexer(
    val id: Long = 0,
    val name: String,
    val baseUrl: String,
    val enabled: Boolean = true,
    val apiKey: String = "",
)

/** SABnzbd server configuration. The API key lives in encrypted prefs. */
data class SabConfig(
    val baseUrl: String = "",
    val apiKey: String = "",
    val defaultCategory: String = "",
) {
    val isConfigured: Boolean get() = baseUrl.isNotBlank() && apiKey.isNotBlank()
}

/**
 * A Newznab category advertised by an indexer's caps endpoint. [parentId] is empty for
 * top-level categories; subcategories carry their parent's id so the UI can drill down.
 */
data class NewznabCategory(
    val id: String,
    val name: String,
    val parentId: String = "",
) {
    val isTopLevel: Boolean get() = parentId.isBlank()
}

/** One search result row mapped from an indexer's RSS item. */
data class SearchResult(
    val title: String,
    val guid: String,
    val nzbUrl: String,
    val sizeBytes: Long,
    val pubDateMillis: Long,
    val categoryName: String,
    val indexerName: String,
    val year: Int? = null,
    val grabs: Int = 0,
    val posterUrl: String? = null,
    val imdbId: String? = null,
)

/** A page of search results plus the total the indexer reports for the query. */
data class SearchPage(
    val total: Int,
    val results: List<SearchResult>,
)

/** A SABnzbd active-queue slot. */
data class QueueItem(
    val id: String,
    val name: String,
    val status: String,
    val percentage: Int,
    val sizeLeft: String,
    val timeLeft: String,
    val category: String,
)

/** A SABnzbd history slot (completed/failed). */
data class HistoryItem(
    val id: String,
    val name: String,
    val status: String,
    val size: String,
    val category: String,
    val failMessage: String,
    val completedMillis: Long,
)

/** Snapshot of the SAB queue plus its paused flag and aggregate speed. */
data class QueueSnapshot(
    val paused: Boolean,
    val speed: String,
    val timeLeft: String,
    val sizeLeft: String,
    val diskSpace: String,
    val finishAction: String,
    val items: List<QueueItem>,
)

data class SabWarning(val text: String, val time: String)

data class SabInfo(
    val downloadToday: String,
    val downloadWeek: String,
    val downloadMonth: String,
    val downloadTotal: String,
    val freeSpace: String,
    val uptime: String,
    val onFinish: String,
    val warnings: List<SabWarning>,
)
