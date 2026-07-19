package org.cygnusx1.nzbconnect.ui.search

import androidx.annotation.DrawableRes
import org.cygnusx1.nzbconnect.R

/**
 * Maps a category name to its icon, or null when nothing matches. Callers supply their own
 * fallback because the sensible default differs per screen: the category list falls back to a
 * generic category icon, a result row to a generic file icon.
 */
@DrawableRes
internal fun categoryIconOrNull(name: String): Int? {
    val n = name.lowercase()
    return when {
        "movie" in n -> R.drawable.ic_movie
        "tv" in n -> R.drawable.ic_tv
        "audio" in n || "music" in n -> R.drawable.ic_headphones
        "console" in n || "game" in n -> R.drawable.ic_sports_esports
        "pc" in n || "app" in n -> R.drawable.ic_apps
        "book" in n -> R.drawable.ic_menu_book
        "xxx" in n || "adult" in n -> R.drawable.ic_favorite
        else -> null
    }
}
