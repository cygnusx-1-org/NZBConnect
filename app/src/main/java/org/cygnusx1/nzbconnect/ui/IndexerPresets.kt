package org.cygnusx1.nzbconnect.ui

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
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

    private fun hostOf(url: String): String = url.trim().substringAfter("://").substringBefore('/').removePrefix("www.").lowercase()
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

/** The NZBConnect app logo with wordmark (transparent background), shown left of the top-bar titles. */
@Composable
fun AppLogo(modifier: Modifier = Modifier) {
    Image(
        painter = painterResource(R.drawable.app_logo),
        contentDescription = null,
        modifier = modifier,
    )
}

/** The NZBConnect logo mark only — no wordmark (transparent background). */
@Composable
fun AppLogoMark(modifier: Modifier = Modifier) {
    Image(
        painter = painterResource(R.drawable.app_logo_mark),
        contentDescription = null,
        modifier = modifier,
    )
}

/** Brand green sampled from the logo wordmark (#269B14). */
private val BrandGreen = Color(0xFF269B14)

/**
 * The "NZBConnect" wordmark rendered as text, matching the logo: "NZB" in the brand
 * green and "Connect" in the surface foreground colour, bold.
 */
@Composable
fun AppWordmark(modifier: Modifier = Modifier) {
    Text(
        text = buildAnnotatedString {
            withStyle(SpanStyle(color = BrandGreen)) { append("NZB") }
            withStyle(SpanStyle(color = MaterialTheme.colorScheme.onSurface)) { append("Connect") }
        },
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
        modifier = modifier,
    )
}

/** Top-bar branded title: the logo mark, then the wordmark stacked above the screen [title]. */
@Composable
fun AppBrandTitle(title: String, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        AppLogoMark(modifier = Modifier.size(48.dp))
        Column {
            AppWordmark()
            Text(
                title,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}
