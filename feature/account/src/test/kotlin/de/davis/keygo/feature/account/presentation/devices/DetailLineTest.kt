package de.davis.keygo.feature.account.presentation.devices

import kotlin.test.Test
import kotlin.test.assertEquals

class DetailLineTest {

    @Test
    fun `segments are joined with bullets`() {
        assertEquals(
            "android • 2.2.0",
            detailLine(listOf("android", "2.2.0")),
        )
    }

    @Test
    fun `a line can only break after a bullet`() {
        val line = detailLine(listOf("Active 3 hours ago", "android", "2.2.0"))

        assertEquals(
            listOf("Active 3 hours ago •", "android •", "2.2.0"),
            line.split(' '),
        )
    }

    @Test
    fun `a single segment has no bullet`() {
        assertEquals("android", detailLine(listOf("android")))
    }
}
