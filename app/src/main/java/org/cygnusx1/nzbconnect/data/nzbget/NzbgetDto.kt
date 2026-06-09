package org.cygnusx1.nzbconnect.data.nzbget

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * NZBGet exposes 64-bit byte counts as split 32-bit `…Lo`/`…Hi` pairs (plus a rounded `…MB`).
 * Helpers in [NzbgetRepository] reconstruct the full value via [combineHiLo].
 */

@Serializable
data class NzbgetStatus(
    @SerialName("DownloadPaused") val downloadPaused: Boolean = false,
    @SerialName("ServerStandBy") val serverStandBy: Boolean = false,
    @SerialName("DownloadRate") val downloadRate: Long = 0, // bytes/sec
    @SerialName("DownloadLimit") val downloadLimit: Long = 0, // bytes/sec, 0 = unlimited
    @SerialName("RemainingSizeMB") val remainingSizeMB: Long = 0,
    @SerialName("FreeDiskSpaceMB") val freeDiskSpaceMB: Long = 0,
    @SerialName("UpTimeSec") val upTimeSec: Long = 0,
    @SerialName("DaySizeMB") val daySizeMB: Long = 0,
    @SerialName("MonthSizeMB") val monthSizeMB: Long = 0,
    @SerialName("DownloadedSizeMB") val downloadedSizeMB: Long = 0,
)

@Serializable
data class NzbgetGroup(
    @SerialName("NZBID") val nzbId: Int = 0,
    @SerialName("NZBName") val nzbName: String = "",
    @SerialName("Status") val status: String = "",
    @SerialName("Category") val category: String = "",
    @SerialName("FileSizeLo") val fileSizeLo: Long = 0,
    @SerialName("FileSizeHi") val fileSizeHi: Long = 0,
    @SerialName("RemainingSizeLo") val remainingSizeLo: Long = 0,
    @SerialName("RemainingSizeHi") val remainingSizeHi: Long = 0,
)

@Serializable
data class NzbgetHistoryItem(
    @SerialName("NZBID") val nzbId: Int = 0,
    @SerialName("Name") val name: String = "",
    @SerialName("Status") val status: String = "",
    @SerialName("Category") val category: String = "",
    @SerialName("FileSizeLo") val fileSizeLo: Long = 0,
    @SerialName("FileSizeHi") val fileSizeHi: Long = 0,
    @SerialName("Kind") val kind: String = "",
    @SerialName("HistoryTime") val historyTime: Long = 0, // epoch seconds (0 if absent)
)

@Serializable
data class NzbgetConfigItem(
    @SerialName("Name") val name: String = "",
    @SerialName("Value") val value: String = "",
)

@Serializable
data class NzbgetLogEntry(
    @SerialName("ID") val id: Int = 0,
    @SerialName("Kind") val kind: String = "",
    @SerialName("Time") val time: Long = 0, // epoch seconds
    @SerialName("Text") val text: String = "",
)

/** Reconstruct a 64-bit value from NZBGet's split unsigned 32-bit hi/lo pair. */
fun combineHiLo(hi: Long, lo: Long): Long = (hi shl 32) or (lo and 0xFFFFFFFFL)
