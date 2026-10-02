package com.triphtml.viewer.domain

import java.util.Locale

/** Where a navigation from the document may go. */
enum class Destination {
    /** Stays inside the open document, such as an #anchor. */
    DOCUMENT,

    /** Leaves the reader and is handed to another Android app. */
    EXTERNAL,

    /** Swallowed: background navigation, subframes, other virtual origins, unsafe schemes. */
    BLOCKED,
}

/**
 * Only a user's own tap may leave the document, and only for schemes another app can safely handle.
 * File, content, JavaScript and data URLs never leave, so a page cannot reach into the app or device.
 */
object LinkPolicy {
    private val externalSchemes = setOf("https", "http", "geo", "google.navigation", "google.streetview", "mailto", "tel", "sms")
    private val mapsSchemes = setOf("geo", "google.navigation", "google.streetview")
    private val mapsHosts = setOf("maps.google.com", "maps.app.goo.gl")
    private val googleHosts = setOf("google.com", "www.google.com", "google.co.jp", "www.google.co.jp", "google.co.kr", "www.google.co.kr")

    fun classify(document: DocumentAddress?, url: String?, userGesture: Boolean, mainFrame: Boolean): Destination {
        if (!mainFrame) return Destination.BLOCKED
        if (document != null && document.isDocument(url)) return Destination.DOCUMENT
        if (!userGesture || DocumentAddress.isVirtual(url)) return Destination.BLOCKED
        val scheme = WebAddress.parse(url)?.scheme?.lowercase(Locale.ROOT) ?: return Destination.BLOCKED
        if (scheme == "intent") return Destination.EXTERNAL // Rebuilt from its data URL before dispatch.
        return if (isSafeExternal(url)) Destination.EXTERNAL else Destination.BLOCKED
    }

    fun isSafeExternal(url: String?): Boolean {
        val uri = WebAddress.parse(url) ?: return false
        val scheme = uri.scheme?.lowercase(Locale.ROOT) ?: return false
        if (DocumentAddress.isVirtual(url) || scheme !in externalSchemes) return false
        if (scheme == "http" || scheme == "https") return uri.host != null && uri.userInfo == null
        return !uri.rawSchemeSpecificPart.isNullOrEmpty()
    }

    /** Google Maps links go straight to the Maps app. Lookalike hosts and paths do not count. */
    fun isGoogleMaps(url: String?): Boolean {
        val uri = WebAddress.parse(url) ?: return false
        val scheme = uri.scheme?.lowercase(Locale.ROOT) ?: return false
        if (scheme in mapsSchemes) return true
        if (scheme != "https" && scheme != "http") return false
        val host = uri.host?.lowercase(Locale.ROOT).orEmpty()
        val path = uri.path.orEmpty()
        if (host in mapsHosts) return true
        if (host == "goo.gl") return path == "/maps" || path.startsWith("/maps/")
        return host in googleHosts && (path == "/maps" || path.startsWith("/maps/"))
    }
}
