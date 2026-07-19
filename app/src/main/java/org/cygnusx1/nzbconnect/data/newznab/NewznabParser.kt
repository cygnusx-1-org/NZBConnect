package org.cygnusx1.nzbconnect.data.newznab

import android.util.Xml
import org.cygnusx1.nzbconnect.domain.NewznabCategory
import org.cygnusx1.nzbconnect.domain.SearchPage
import org.cygnusx1.nzbconnect.domain.SearchResult
import org.xmlpull.v1.XmlPullParser
import java.io.StringReader
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * Hand-rolled pull parsing of Newznab RSS. We parse XML rather than JSON because the
 * RSS shape is stable across Newznab/Spotweb forks, whereas the JSON differs.
 */
object NewznabParser {

    private val rfc1123 =
        SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss Z", Locale.US)

    /** Parse a `t=search`/`t=tvsearch`/`t=movie` RSS feed into a [SearchPage]. */
    fun parseSearch(xml: String, indexerName: String): SearchPage {
        val parser = newParser(xml)
        val results = mutableListOf<SearchResult>()
        var total = 0

        var inItem = false
        var title = ""
        var guid = ""
        var link = ""
        var enclosureUrl = ""
        var enclosureLen = 0L
        var attrSize = 0L
        var category = ""
        var pubDate = 0L
        var grabs = 0
        var year: Int? = null
        var poster: String? = null
        var imdb: String? = null

        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> when (parser.name) {
                    // <newznab:response offset=".." total=".."/>
                    "newznab:response", "response" ->
                        parser.getAttributeValue(null, "total")?.toIntOrNull()?.let { total = it }

                    "item" -> {
                        inItem = true
                        title = ""
                        guid = ""
                        link = ""
                        enclosureUrl = ""
                        enclosureLen = 0L
                        attrSize = 0L
                        category = ""
                        pubDate = 0L
                        grabs = 0
                        year = null
                        poster = null
                        imdb = null
                    }

                    "title" -> if (inItem) title = parser.nextText().trim()

                    "guid" -> if (inItem) guid = parser.nextText().trim()

                    "link" -> if (inItem) link = parser.nextText().trim()

                    "category" -> if (inItem && category.isEmpty()) category = parser.nextText().trim()

                    "pubDate" -> if (inItem) pubDate = parseDate(parser.nextText().trim())

                    "enclosure" -> if (inItem) {
                        enclosureUrl = parser.getAttributeValue(null, "url").orEmpty()
                        enclosureLen = parser.getAttributeValue(null, "length")?.toLongOrNull() ?: 0L
                    }

                    // <newznab:attr name="size" value="123"/> — name may be prefixed
                    "attr", "newznab:attr" -> if (inItem) {
                        val name = parser.getAttributeValue(null, "name")
                        val value = parser.getAttributeValue(null, "value")
                        when (name) {
                            "size" -> attrSize = value?.toLongOrNull() ?: attrSize
                            "grabs" -> grabs = value?.toIntOrNull() ?: grabs
                            "category" -> if (category.isEmpty()) category = value.orEmpty()
                            "year" -> year = value?.toIntOrNull() ?: year
                            "coverurl", "poster" -> poster = value?.takeIf { it.isNotBlank() } ?: poster
                            "imdb", "imdbid" -> imdb = value?.takeIf { it.isNotBlank() } ?: imdb
                        }
                    }
                }

                XmlPullParser.END_TAG -> if (parser.name == "item" && inItem) {
                    inItem = false
                    val nzbUrl = enclosureUrl.ifBlank { link }
                    if (nzbUrl.isNotBlank()) {
                        results += SearchResult(
                            title = title,
                            guid = guid.ifBlank { nzbUrl },
                            nzbUrl = nzbUrl,
                            sizeBytes = if (attrSize > 0) attrSize else enclosureLen,
                            pubDateMillis = pubDate,
                            categoryName = category,
                            indexerName = indexerName,
                            year = year,
                            grabs = grabs,
                            posterUrl = poster,
                            imdbId = imdb,
                        )
                    }
                }
            }
            event = parser.next()
        }
        return SearchPage(total = if (total > 0) total else results.size, results = results)
    }

    /**
     * Parse a `t=caps` feed into a flat list where top-level categories have a blank
     * [NewznabCategory.parentId] and subcategories carry their parent's id.
     */
    fun parseCaps(xml: String): List<NewznabCategory> {
        val parser = newParser(xml)
        val cats = mutableListOf<NewznabCategory>()
        var parentId = ""

        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.START_TAG) {
                when (parser.name) {
                    "category" -> {
                        val id = parser.getAttributeValue(null, "id").orEmpty()
                        val name = parser.getAttributeValue(null, "name").orEmpty()
                        parentId = id
                        if (id.isNotBlank()) cats += NewznabCategory(id, name, parentId = "")
                    }

                    "subcat" -> {
                        val id = parser.getAttributeValue(null, "id").orEmpty()
                        val name = parser.getAttributeValue(null, "name").orEmpty()
                        if (id.isNotBlank()) cats += NewznabCategory(id, name, parentId = parentId)
                    }
                }
            }
            event = parser.next()
        }
        return cats
    }

    /** Detect a Newznab `<error code=.. description=..>` envelope. */
    fun parseError(xml: String): String? {
        if (!xml.contains("<error", ignoreCase = true)) return null
        val parser = newParser(xml)
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.START_TAG && parser.name == "error") {
                return parser.getAttributeValue(null, "description")
                    ?: "Indexer returned an error"
            }
            event = parser.next()
        }
        return null
    }

    private fun newParser(xml: String): XmlPullParser = Xml.newPullParser().apply {
        setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
        setInput(StringReader(xml))
    }

    private fun parseDate(raw: String): Long = try {
        synchronized(rfc1123) { rfc1123.parse(raw)?.time ?: 0L }
    } catch (_: Exception) {
        0L
    }
}
