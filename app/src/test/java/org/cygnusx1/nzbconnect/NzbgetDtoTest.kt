package org.cygnusx1.nzbconnect

import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import org.cygnusx1.nzbconnect.data.nzbget.NzbgetConfigItem
import org.cygnusx1.nzbconnect.data.nzbget.NzbgetGroup
import org.cygnusx1.nzbconnect.data.nzbget.NzbgetStatus
import org.cygnusx1.nzbconnect.data.nzbget.combineHiLo
import org.cygnusx1.nzbconnect.data.nzbget.nzbgetCategories
import org.cygnusx1.nzbconnect.data.nzbget.nzbgetPercentage
import org.junit.Assert.assertEquals
import org.junit.Test

class NzbgetDtoTest {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    @Test
    fun parsesStatusIgnoringUnknownFields() {
        val payload = """
            {"DownloadPaused":true,"DownloadRate":1572864,"DownloadLimit":0,
             "RemainingSizeMB":2048,"FreeDiskSpaceMB":211985,"UpTimeSec":493,
             "DaySizeMB":100,"MonthSizeMB":500,"DownloadedSizeMB":700,"NewsServers":[]}
        """.trimIndent()

        val status = json.decodeFromString<NzbgetStatus>(payload)

        assertEquals(true, status.downloadPaused)
        assertEquals(1572864L, status.downloadRate)
        assertEquals(2048L, status.remainingSizeMB)
        assertEquals(493L, status.upTimeSec)
    }

    @Test
    fun combinesHiLoIntoSixtyFourBitValue() {
        assertEquals(4_294_967_296L, combineHiLo(hi = 1, lo = 0))
        assertEquals(4_294_967_295L, combineHiLo(hi = 0, lo = 4_294_967_295L)) // unsigned 32-bit lo
        assertEquals(0L, combineHiLo(hi = 0, lo = 0))
    }

    @Test
    fun computesGroupPercentageFromHiLoSizes() {
        // 1000 MB total, 250 MB remaining -> 75% done.
        val group = json.decodeFromString<NzbgetGroup>(
            """
            {"NZBID":3,"NZBName":"Some.Release","Status":"DOWNLOADING","Category":"Movies",
             "FileSizeLo":1048576000,"FileSizeHi":0,
             "RemainingSizeLo":262144000,"RemainingSizeHi":0}
            """.trimIndent(),
        )

        assertEquals(3, group.nzbId)
        assertEquals("Movies", group.category)
        assertEquals(75, nzbgetPercentage(group))
    }

    @Test
    fun zeroTotalYieldsZeroPercent() {
        val group = NzbgetGroup(nzbId = 1)
        assertEquals(0, nzbgetPercentage(group))
    }

    @Test
    fun extractsCategoryNamesFromConfig() {
        val payload = """
            [{"Name":"MainDir","Value":"/downloads"},
             {"Name":"Category1.Name","Value":"Movies"},
             {"Name":"Category1.DestDir","Value":""},
             {"Name":"Category2.Name","Value":"Series"},
             {"Name":"Category3.Name","Value":""}]
        """.trimIndent()

        val items = json.decodeFromString(ListSerializer(NzbgetConfigItem.serializer()), payload)

        assertEquals(listOf("Movies", "Series"), nzbgetCategories(items))
    }
}
