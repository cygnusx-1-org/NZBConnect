package org.cygnusx1.nzbconnect.data.sab

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class VersionResponse(val version: String = "")

@Serializable
data class AddUrlResponse(
    val status: Boolean = false,
    @SerialName("nzo_ids") val nzoIds: List<String> = emptyList(),
)

@Serializable
data class SimpleStatusResponse(
    val status: Boolean = false,
    val error: String? = null,
)

@Serializable
data class CategoriesResponse(
    val categories: List<String> = emptyList(),
)

@Serializable
data class QueueResponse(val queue: Queue = Queue()) {
    @Serializable
    data class Queue(
        val paused: Boolean = false,
        val speed: String = "",
        @SerialName("timeleft") val timeLeft: String = "",
        @SerialName("sizeleft") val sizeLeft: String = "",
        @SerialName("diskspace1") val diskSpace: String = "",
        @SerialName("finishaction") val finishAction: String? = null,
        val slots: List<Slot> = emptyList(),
    )

    @Serializable
    data class Slot(
        @SerialName("nzo_id") val nzoId: String = "",
        val filename: String = "",
        val status: String = "",
        val percentage: String = "0",
        @SerialName("sizeleft") val sizeLeft: String = "",
        @SerialName("timeleft") val timeLeft: String = "",
        @SerialName("cat") val category: String = "",
    )
}

@Serializable
data class StatusResponse(val status: Status = Status()) {
    @Serializable
    data class Status(val uptime: String = "—")
}

@Serializable
data class ServerStatsResponse(
    val total: Long = 0,
    val month: Long = 0,
    val week: Long = 0,
    val day: Long = 0,
)

@Serializable
data class WarningsResponse(
    val warnings: List<Warning> = emptyList(),
) {
    @Serializable
    data class Warning(
        val type: String = "",
        val text: String = "",
        val time: String = "",
    )
}

@Serializable
data class HistoryResponse(val history: History = History()) {
    @Serializable
    data class History(val slots: List<Slot> = emptyList()) {
        @Serializable
        data class Slot(
            @SerialName("nzo_id") val nzoId: String = "",
            val name: String = "",
            val status: String = "",
            val size: String = "",
            val category: String = "",
            @SerialName("fail_message") val failMessage: String = "",
            val completed: Long = 0, // unix epoch seconds
        )
    }
}
