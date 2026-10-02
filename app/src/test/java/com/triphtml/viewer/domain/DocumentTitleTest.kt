package com.triphtml.viewer.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DocumentTitleTest {
    private fun title(name: String) = DocumentTitle.fromDisplayName(name)!!.text

    @Test fun htmlExtensionIsRemoved() {
        assertEquals("R56", title("R56.html"))
        assertEquals("Tokyo Trip", title("Tokyo Trip.HTM"))
        assertEquals("오사카 일정", title("  오사카 일정.html "))
    }

    @Test fun onlyAFinalExtensionCounts() {
        assertEquals("notes.html.txt", title("notes.html.txt"))
        assertEquals(".html", title(".html"))
    }

    @Test fun missingNamesGiveNoTitle() {
        assertNull(DocumentTitle.fromDisplayName(null))
        assertNull(DocumentTitle.fromDisplayName("   "))
    }

    @Test fun titlesCompareByValue() {
        assertEquals(DocumentTitle.fromDisplayName("a.html"), DocumentTitle.fromDisplayName("a"))
    }
}
