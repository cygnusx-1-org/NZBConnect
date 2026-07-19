package org.cygnusx1.nzbconnect.data.newznab

import org.cygnusx1.nzbconnect.data.local.CategoryDao
import org.cygnusx1.nzbconnect.data.local.CategoryEntity
import org.cygnusx1.nzbconnect.domain.ApiResult
import org.cygnusx1.nzbconnect.domain.Indexer
import org.cygnusx1.nzbconnect.domain.NewznabCategory
import org.cygnusx1.nzbconnect.domain.SearchPage
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NewznabRepository @Inject constructor(
    private val api: NewznabApi,
    private val categoryDao: CategoryDao,
) {

    /**
     * Search (or, with a blank [query], browse) a single indexer. [categories] is a
     * comma-separated list of Newznab category ids to scope the request.
     */
    suspend fun search(
        indexer: Indexer,
        query: String,
        categories: String = "",
        limit: Int = 100,
    ): ApiResult<SearchPage> {
        val params = buildMap {
            put("t", "search")
            put("apikey", indexer.apiKey)
            if (query.isNotBlank()) put("q", query)
            put("limit", limit.toString())
            put("extended", "1")
            if (categories.isNotBlank()) put("cat", categories)
        }
        return when (val res = rawGet(indexer.baseUrl, params)) {
            is ApiResult.Failure -> res

            is ApiResult.Success -> ApiResult.Success(
                NewznabParser.parseSearch(res.data, indexer.name),
            )
        }
    }

    /** Fetch caps, cache the category hierarchy in Room, and return it. */
    suspend fun refreshCategories(indexer: Indexer): ApiResult<List<NewznabCategory>> = when (val res = rawGet(indexer.baseUrl, mapOf("t" to "caps", "apikey" to indexer.apiKey))) {
        is ApiResult.Failure -> res

        is ApiResult.Success -> {
            val cats = NewznabParser.parseCaps(res.data)
            categoryDao.clearForIndexer(indexer.id)
            categoryDao.insertAll(
                cats.map {
                    CategoryEntity(
                        indexerId = indexer.id,
                        catId = it.id,
                        name = it.name,
                        parentId = it.parentId,
                    )
                },
            )
            ApiResult.Success(cats)
        }
    }

    /** Categories from the Room cache, refreshing from the indexer if the cache is empty. */
    suspend fun getCategories(indexer: Indexer): ApiResult<List<NewznabCategory>> {
        val cached = categoryDao.getForIndexer(indexer.id)
        if (cached.isNotEmpty()) {
            return ApiResult.Success(cached.map { NewznabCategory(it.catId, it.name, it.parentId) })
        }
        return refreshCategories(indexer)
    }

    /** Lightweight reachability/auth probe used by the "Test" button. */
    suspend fun test(indexer: Indexer): ApiResult<Unit> = when (val r = rawGet(indexer.baseUrl, mapOf("t" to "caps", "apikey" to indexer.apiKey))) {
        is ApiResult.Success -> ApiResult.Success(Unit)
        is ApiResult.Failure -> r
    }

    private fun apiUrl(baseUrl: String): String {
        val trimmed = baseUrl.trim().trimEnd('/')
        return if (trimmed.endsWith("/api")) trimmed else "$trimmed/api"
    }

    private suspend fun rawGet(baseUrl: String, params: Map<String, String>): ApiResult<String> = try {
        val response = api.get(apiUrl(baseUrl), params)
        val body = response.body()?.string().orEmpty()
        if (!response.isSuccessful) {
            ApiResult.Failure("HTTP ${response.code()}")
        } else {
            val err = NewznabParser.parseError(body)
            if (err != null) ApiResult.Failure(err) else ApiResult.Success(body)
        }
    } catch (e: Exception) {
        ApiResult.Failure(e.message ?: "Network error")
    }
}
