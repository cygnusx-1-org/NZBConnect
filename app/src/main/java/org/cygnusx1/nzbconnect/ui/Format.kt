package org.cygnusx1.nzbconnect.ui

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.math.ln
import kotlin.math.pow

private val timeFormat = SimpleDateFormat("h:mm a", Locale.getDefault())
private val dateFormat = SimpleDateFormat("MM/dd", Locale.getDefault())

/** SABnzbd history timestamp, e.g. "Today – 03:31 PM" or "06/02 – 04:28 AM". */
fun formatHistoryDate(millis: Long): String {
    if (millis <= 0) return ""
    val date = Date(millis)
    val now = Calendar.getInstance()
    val then = Calendar.getInstance().apply { time = date }
    val sameDay = now.get(Calendar.YEAR) == then.get(Calendar.YEAR) &&
        now.get(Calendar.DAY_OF_YEAR) == then.get(Calendar.DAY_OF_YEAR)
    val day = if (sameDay) "Today" else dateFormat.format(date)
    return "$day – ${timeFormat.format(date)}"
}

/** Human-readable byte size, e.g. 1.4 GB. */
fun formatSize(bytes: Long): String {
    if (bytes <= 0) return "—"
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    val digitGroups = (ln(bytes.toDouble()) / ln(1024.0)).toInt().coerceIn(0, units.lastIndex)
    val value = bytes / 1024.0.pow(digitGroups.toDouble())
    return String.format("%.1f %s", value, units[digitGroups])
}

/** Compact age used in result rows, e.g. "3 m", "5 h", "12 d". */
fun formatAgeShort(pubDateMillis: Long): String {
    if (pubDateMillis <= 0) return ""
    val diff = System.currentTimeMillis() - pubDateMillis
    if (diff < 0) return "0 m"
    val days = TimeUnit.MILLISECONDS.toDays(diff)
    val hours = TimeUnit.MILLISECONDS.toHours(diff)
    val minutes = TimeUnit.MILLISECONDS.toMinutes(diff)
    return when {
        days > 0 -> "$days d"
        hours > 0 -> "$hours h"
        else -> "$minutes m"
    }
}

/** Parses a SABnzbd size string like "3.9 GB" or "660.7 MB" to a comparable MB value. */
fun parseSizeMb(sizeStr: String): Double {
    val parts = sizeStr.trim().split(" ", limit = 2)
    if (parts.size != 2) return 0.0
    val value = parts[0].toDoubleOrNull() ?: return 0.0
    return when (parts[1].uppercase()) {
        "B" -> value / 1_048_576.0
        "KB", "K" -> value / 1024.0
        "MB", "M" -> value
        "GB", "G" -> value * 1024.0
        "TB", "T" -> value * 1_048_576.0
        else -> 0.0
    }
}

/** Relative age from an epoch-millis publish date, e.g. "3d ago". */
fun formatAge(pubDateMillis: Long): String {
    if (pubDateMillis <= 0) return ""
    val diff = System.currentTimeMillis() - pubDateMillis
    if (diff < 0) return "just now"
    val days = TimeUnit.MILLISECONDS.toDays(diff)
    val hours = TimeUnit.MILLISECONDS.toHours(diff)
    val minutes = TimeUnit.MILLISECONDS.toMinutes(diff)
    return when {
        days > 0 -> "${days}d ago"
        hours > 0 -> "${hours}h ago"
        minutes > 0 -> "${minutes}m ago"
        else -> "just now"
    }
}
