package org.cygnusx1.nzbconnect.ui.search

import org.cygnusx1.nzbconnect.domain.SearchResult
import java.net.URLEncoder

/** Quality flags derived from a release title, shown as badges and used by filters. */
data class ReleaseBadges(
    val is4k: Boolean,
    val isHdr: Boolean,
    val isBluRay: Boolean,
)

private val FK_REGEX = Regex("\\b(2160p|4k|uhd)\\b", RegexOption.IGNORE_CASE)
private val HDR_REGEX = Regex("\\b(hdr|hdr10\\+?|dolby ?vision|dovi|\\bdv\\b)\\b", RegexOption.IGNORE_CASE)
private val BLURAY_REGEX = Regex("\\b(blu-?ray|bdremux|remux|bdrip|brrip)\\b", RegexOption.IGNORE_CASE)
private val YEAR_REGEX = Regex("\\b(19\\d{2}|20\\d{2})\\b")

fun badgesOf(title: String): ReleaseBadges = ReleaseBadges(
    is4k = FK_REGEX.containsMatchIn(title),
    isHdr = HDR_REGEX.containsMatchIn(title),
    isBluRay = BLURAY_REGEX.containsMatchIn(title),
)

/** The release's year, preferring the indexer-provided value over one parsed from the title. */
fun SearchResult.displayYear(): Int? = year ?: YEAR_REGEX.find(title)?.value?.toIntOrNull()

/**
 * Turn a scene release name into a human title for an IMDb lookup: cut at the year and
 * strip dots/underscores, e.g. "The.Hobbit.2014.2160p…" -> "The Hobbit".
 */
fun SearchResult.cleanTitle(): String {
    val raw = title
    val yearMatch = YEAR_REGEX.find(raw)
    val head = if (yearMatch != null) raw.substring(0, yearMatch.range.first) else raw
    return head.replace('.', ' ').replace('_', ' ').trim().ifBlank { raw }
}

/**
 * The IMDb URL to show in the detail screen. Prefers the indexer-provided IMDb id
 * (a direct title page, as nzb360 does) and falls back to a title/year search only
 * when the result carries no id.
 */
fun SearchResult.imdbUrl(): String {
    val digits = imdbId?.filter { it.isDigit() }?.takeIf { it.isNotEmpty() }
    if (digits != null) {
        return "https://www.imdb.com/title/tt${digits.padStart(7, '0')}/"
    }
    val query = buildString {
        append(cleanTitle())
        displayYear()?.let { append(" $it") }
    }.trim()
    return "https://www.imdb.com/find/?q=" + URLEncoder.encode(query, "UTF-8")
}
