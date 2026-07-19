package org.cygnusx1.nzbconnect

import kotlinx.serialization.json.Json
import org.cygnusx1.nzbconnect.data.sab.AddUrlResponse
import org.cygnusx1.nzbconnect.data.sab.QueueResponse
import org.cygnusx1.nzbconnect.data.sab.VersionResponse
import org.cygnusx1.nzbconnect.ui.formatSize
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SabnzbdDtoTest {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    @Test
    fun parsesQueueWithSlots() {
        val payload = """
            {"queue":{"paused":false,"speed":"5.2 M","timeleft":"0:01:30","slots":[
              {"nzo_id":"SABnzbd_nzo_abc","filename":"Some.Release","status":"Downloading",
               "percentage":"42","sizeleft":"512 MB","timeleft":"0:00:45","cat":"tv"}
            ]}}
        """.trimIndent()

        val result = json.decodeFromString<QueueResponse>(payload)

        assertEquals(1, result.queue.slots.size)
        val slot = result.queue.slots.first()
        assertEquals("SABnzbd_nzo_abc", slot.nzoId)
        assertEquals("42", slot.percentage)
        assertEquals("tv", slot.category)
        assertEquals("5.2 M", result.queue.speed)
    }

    @Test
    fun parsesAddUrlSuccess() {
        val result = json.decodeFromString<AddUrlResponse>(
            """{"status":true,"nzo_ids":["SABnzbd_nzo_xyz"]}""",
        )
        assertTrue(result.status)
        assertEquals("SABnzbd_nzo_xyz", result.nzoIds.first())
    }

    @Test
    fun toleratesUnknownVersionFields() {
        val result = json.decodeFromString<VersionResponse>("""{"version":"4.3.2","extra":"ignored"}""")
        assertEquals("4.3.2", result.version)
    }

    @Test
    fun formatsSizes() {
        assertEquals("—", formatSize(0))
        assertTrue(formatSize(1_500_000_000).endsWith("GB"))
        assertTrue(formatSize(2048).startsWith("2.0 KB"))
    }
}
