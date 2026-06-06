package org.cygnusx1.nzbconnect.ui

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import org.cygnusx1.nzbconnect.R

/** A known public indexer offered as a one-tap option that pre-fills the add form. */
data class IndexerPreset(
    val name: String,
    val baseUrl: String,
    @param:DrawableRes val logoRes: Int,
    val tintLogo: Boolean = true,
)

object IndexerPresets {
    val ALL: List<IndexerPreset> = listOf(
        IndexerPreset(name = "NZBs.in", baseUrl = "https://nzbs.in/", logoRes = R.drawable.ic_indexer_nzbsin),
        IndexerPreset(name = "Drunkenslug", baseUrl = "https://api.drunkenslug.com", logoRes = R.drawable.ic_indexer_drunkenslug, tintLogo = false),
        IndexerPreset(name = "NZBgeek", baseUrl = "https://api.nzbgeek.info", logoRes = R.drawable.ic_indexer_nzbgeek, tintLogo = false),
    )

    fun presetFor(baseUrl: String): IndexerPreset? {
        val host = hostOf(baseUrl)
        return ALL.firstOrNull { hostOf(it.baseUrl) == host }
    }

    /** The logo for a configured indexer whose base URL matches a known preset host. */
    @DrawableRes
    fun logoFor(baseUrl: String): Int? = presetFor(baseUrl)?.logoRes

    private fun hostOf(url: String): String =
        url.trim().substringAfter("://").substringBefore('/').removePrefix("www.").lowercase()
}

/**
 * Renders an indexer logo tinted to [MaterialTheme]'s `onSurface` colour. The bundled
 * logos are monochrome silhouettes, so this makes them flip between dark (light mode)
 * and light (dark mode) automatically.
 */
@Composable
fun IndexerLogo(@DrawableRes logoRes: Int, tint: Boolean = true, modifier: Modifier = Modifier) {
    Image(
        painter = painterResource(logoRes),
        contentDescription = null,
        colorFilter = if (tint) ColorFilter.tint(MaterialTheme.colorScheme.onSurface) else null,
        modifier = modifier,
    )
}
