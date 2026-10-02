package com.triphtml.viewer.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LinkPolicyTest {
    private val document = DocumentAddress("content://provider/document/trip")
    private val other = DocumentAddress("content://provider/document/other")

    @Test fun internalNavigationStays() {
        assertEquals(Destination.DOCUMENT, LinkPolicy.classify(document, document.url + "#x", userGesture = false, mainFrame = true))
    }

    @Test fun onlyUserTapsLeaveTheDocument() {
        assertEquals(Destination.EXTERNAL, LinkPolicy.classify(document, "https://example.com", userGesture = true, mainFrame = true))
        assertEquals(Destination.BLOCKED, LinkPolicy.classify(document, "https://example.com", userGesture = false, mainFrame = true))
        assertEquals(Destination.BLOCKED, LinkPolicy.classify(document, "https://example.com", userGesture = true, mainFrame = false))
        assertEquals(
            Destination.BLOCKED,
            LinkPolicy.classify(document, "intent://maps/#Intent;scheme=https;end", userGesture = false, mainFrame = true),
        )
    }

    @Test fun unsafeTargetsNeverLeave() {
        listOf(
            "file:///sdcard/trip.html", "content://private/secret", "javascript:alert(1)", "data:text/html,hello",
            "https://user@example.com", "https://", "bad url", other.url, "https://fake.html-viewer.invalid/file",
        ).forEach { assertFalse("Unsafe target rejected: $it", LinkPolicy.isSafeExternal(it)) }
    }

    @Test fun mapsLinksAreRecognised() {
        listOf(
            "https://www.google.com/maps/dir/?api=1", "https://maps.app.goo.gl/abc", "https://goo.gl/maps/abc",
            "geo:35,139", "google.navigation:q=Tokyo", "https://www.google.co.jp/maps",
        ).forEach { assertTrue("Maps identified: $it", LinkPolicy.isGoogleMaps(it)) }
    }

    @Test fun mapsLookalikesAreNot() {
        listOf(
            "https://google.com.evil.test/maps", "https://evilgoogle.com/maps", "https://www.google.com/maps-evil",
            "https://goo.gl/other", "https://example.com/maps",
        ).forEach { assertFalse("Maps lookalike rejected: $it", LinkPolicy.isGoogleMaps(it)) }
    }
}
