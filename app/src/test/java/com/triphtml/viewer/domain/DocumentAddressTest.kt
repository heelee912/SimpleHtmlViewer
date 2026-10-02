package com.triphtml.viewer.domain

import java.net.URI
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DocumentAddressTest {
    private val first = DocumentAddress("content://provider/document/trip")
    private val second = DocumentAddress("content://provider/document/other")

    /** Values produced by the 1.x Java implementation. A mismatch would lose users' saved localStorage. */
    @Test fun originMatchesVersionOne() {
        assertEquals(
            "https://d5891e20ba1c0657657cff951820ae28f.299bd4b76b489eba04518d464311df69.html-viewer.invalid/document.html",
            first.url,
        )
        assertEquals(
            "https://d7f91a63066830ed0b2e269ff4ca71e01.e2cfa074195051e474fe0b2eadd694f1.html-viewer.invalid/document.html",
            DocumentAddress("content://com.android.externalstorage.documents/document/primary%3ADownload%2FR56.html").url,
        )
        assertEquals(
            "https://d5d211db81cdc9357e31d71a4233dd211.af87fece6720d05ddbbe1e136c322ae4.html-viewer.invalid/document.html",
            DocumentAddress("content://com.triphtml.viewer.test.documents/document/trip.html").url,
        )
    }

    @Test fun reopeningKeepsOriginAndDocumentsAreIsolated() {
        assertEquals(first.url, DocumentAddress("content://provider/document/trip").url)
        assertNotEquals(first.url, second.url)
    }

    @Test fun hostLabelsFitDns() {
        URI.create(first.url).host.split('.').forEach { assertTrue("DNS label length", it.length <= 63) }
    }

    @Test fun anchorsAndQueriesStayInTheDocument() {
        assertTrue(first.isDocument(first.url + "#day2"))
        assertTrue(first.isDocument(first.url + "?view=day#day2"))
    }

    @Test fun siblingsAndSpoofsAreNotTheDocument() {
        assertFalse("Sibling inaccessible", first.isDocument(first.url.replace("document.html", "secret.js")))
        assertFalse("No userinfo spoof", first.contains(first.url.replace("https://", "https://evil@")))
        assertFalse("No insecure origin", first.contains(first.url.replace("https:", "http:")))
        assertFalse("Exact port", first.contains(first.url.replace("/document.html", ":443/document.html")))
    }

    @Test(expected = IllegalArgumentException::class)
    fun onlyContentUrisHaveAddresses() {
        DocumentAddress("file:///sdcard/trip.html")
    }
}
