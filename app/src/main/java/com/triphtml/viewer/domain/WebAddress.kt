package com.triphtml.viewer.domain

import java.net.URI
import java.net.URISyntaxException

/** Strict URL parsing shared by the link rules. Malformed input is simply "not an address". */
internal object WebAddress {
    fun parse(url: String?): URI? = try {
        url?.let(::URI)
    } catch (_: URISyntaxException) {
        null
    }
}
