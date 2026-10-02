package com.triphtml.viewer.domain

import java.security.MessageDigest
import java.util.Locale

/**
 * A stable virtual HTTPS origin for one SAF document. The host is derived from the document URI, so each
 * document gets its own web storage and reopening the same file finds its localStorage again. No server
 * exists behind it: `.invalid` can never resolve, and the reader answers these requests itself.
 *
 * The derivation must stay byte-for-byte identical to version 1.x. Changing it would orphan every
 * document's saved localStorage on update. `DocumentAddressTest` pins it with values from 1.x.
 */
class DocumentAddress(documentUri: String) {
    private val host: String

    init {
        require(documentUri.startsWith("content://")) { "A content document URI is required" }
        val digest = MessageDigest.getInstance("SHA-256").digest(documentUri.toByteArray(Charsets.UTF_8))
        val hex = digest.joinToString("") { String.format(Locale.ROOT, "%02x", it.toInt() and 0xFF) }
        host = "d" + hex.substring(0, 32) + "." + hex.substring(32) + DOMAIN_SUFFIX
    }

    val url: String get() = "https://$host$DOCUMENT_PATH"

    /** True for any URL on this document's own origin, with no userinfo or port tricks. */
    fun contains(url: String?): Boolean {
        val uri = WebAddress.parse(url) ?: return false
        return "https".equals(uri.scheme, ignoreCase = true) && host.equals(uri.host, ignoreCase = true) &&
            uri.userInfo == null && uri.port == -1
    }

    /** True for the document itself, including its query and #anchor. Siblings are never the document. */
    fun isDocument(url: String?): Boolean = contains(url) && WebAddress.parse(url)?.rawPath == DOCUMENT_PATH

    companion object {
        const val DOMAIN_SUFFIX = ".html-viewer.invalid"
        private const val DOCUMENT_PATH = "/document.html"

        /** True for any virtual document origin, including other documents' and made-up ones. */
        fun isVirtual(url: String?): Boolean =
            WebAddress.parse(url)?.host?.lowercase(Locale.ROOT)?.endsWith(DOMAIN_SUFFIX) == true
    }
}
