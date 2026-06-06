package org.cygnusx1.nzbconnect.ui.search

import org.cygnusx1.nzbconnect.domain.SearchResult

/** Client-side sort options offered in the results screen (nzb360-style menu). */
enum class SortOption(val label: String) {
    TIME("By Time"),
    TITLE("By Title"),
    SIZE("By Size"),
    YEAR("By Year"),
    GRABS("By Grabs"),
}

/** How a quality dimension constrains the result list. */
enum class QualityRule { SHOW, REMOVE, ONLY }

private const val MAX_SIZE_GB = 30 // slider tops out here, meaning "30 GB and up"
const val FILTER_MAX_GB = MAX_SIZE_GB

/** Client-side result filter: a size window plus per-quality Show/Remove/Only rules. */
data class ResultFilter(
    val minGb: Int = 0,
    val maxGb: Int = MAX_SIZE_GB, // == MAX_SIZE_GB means no upper bound
    val bluRay: QualityRule = QualityRule.SHOW,
    val fourK: QualityRule = QualityRule.SHOW,
    val hdr: QualityRule = QualityRule.SHOW,
) {
    val isActive: Boolean
        get() = minGb > 0 || maxGb < MAX_SIZE_GB ||
            bluRay != QualityRule.SHOW || fourK != QualityRule.SHOW || hdr != QualityRule.SHOW

    private fun sizeAllows(bytes: Long): Boolean {
        val gb = bytes / 1_000_000_000.0
        if (gb < minGb) return false
        if (maxGb < MAX_SIZE_GB && gb > maxGb) return false
        return true
    }

    private fun qualityAllows(rule: QualityRule, has: Boolean): Boolean = when (rule) {
        QualityRule.SHOW -> true
        QualityRule.REMOVE -> !has
        QualityRule.ONLY -> has
    }

    fun accepts(result: SearchResult): Boolean {
        if (!sizeAllows(result.sizeBytes)) return false
        val b = badgesOf(result.title)
        return qualityAllows(bluRay, b.isBluRay) &&
            qualityAllows(fourK, b.is4k) &&
            qualityAllows(hdr, b.isHdr)
    }
}

/** Apply the active filter then the chosen sort to produce the displayed list. */
fun List<SearchResult>.applyFilterAndSort(filter: ResultFilter, sort: SortOption): List<SearchResult> {
    val filtered = filter { filter.accepts(it) }
    return when (sort) {
        SortOption.TIME -> filtered.sortedByDescending { it.pubDateMillis }
        SortOption.TITLE -> filtered.sortedBy { it.title.lowercase() }
        SortOption.SIZE -> filtered.sortedByDescending { it.sizeBytes }
        SortOption.YEAR -> filtered.sortedByDescending { it.displayYear() ?: 0 }
        SortOption.GRABS -> filtered.sortedByDescending { it.grabs }
    }
}
