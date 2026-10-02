package com.triphtml.viewer.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PageColorTest {
    @Test fun opaqueColoursAreRead() {
        assertEquals(0xFFF5F7F6.toInt(), PageColor.opaqueArgb("\"rgb(245, 247, 246)\""))
        assertEquals(0xFF0A141E.toInt(), PageColor.opaqueArgb("\"rgba(10, 20, 30, 1)\""))
        assertEquals(0xFF0A141E.toInt(), PageColor.opaqueArgb("\"rgba(10, 20, 30, 1.0)\""))
    }

    @Test fun seeThroughPagesGiveNoColour() {
        assertNull(PageColor.opaqueArgb("\"rgba(0, 0, 0, 0)\""))
        assertNull(PageColor.opaqueArgb("\"rgba(255, 255, 255, 0.5)\""))
        assertNull(PageColor.opaqueArgb("\"rgba(10, 20, 30, 1.2)\""))
    }

    @Test fun malformedValuesGiveNoColourWithoutThrowing() {
        listOf(
            "\"\"", "null", null, "\"rgb(300, 0, 0)\"", "\"linear-gradient(red, blue)\"",
            "\"rgba(10, 20, 30, .)\"", "\"rgba(10, 20, 30, ..)\"", "\"rgba(10, 20, 30, 1..0)\"",
        ).forEach { assertNull("Rejected: $it", PageColor.opaqueArgb(it)) }
    }
}
