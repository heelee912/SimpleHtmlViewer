package com.triphtml.viewer.domain

import java.util.Locale

/** The name shown for a document: the provider's display name without its HTML extension. */
@ConsistentCopyVisibility
data class DocumentTitle private constructor(val text: String) {
    companion object {
        private val htmlExtensions = listOf(".html", ".htm")

        /** Null when the provider reported no usable name. The screen then names the app instead. */
        fun fromDisplayName(displayName: String?): DocumentTitle? {
            var name = displayName?.trim() ?: return null
            val lower = name.lowercase(Locale.ROOT)
            htmlExtensions.firstOrNull { lower.endsWith(it) && lower.length > it.length }?.let {
                name = name.dropLast(it.length).trim()
            }
            return if (name.isEmpty()) null else DocumentTitle(name)
        }
    }
}
