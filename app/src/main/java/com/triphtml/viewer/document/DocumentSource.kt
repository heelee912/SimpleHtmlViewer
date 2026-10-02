package com.triphtml.viewer.document

import android.content.ContentResolver
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import android.webkit.WebResourceResponse
import com.triphtml.viewer.domain.DocumentAddress
import com.triphtml.viewer.domain.DocumentTitle
import java.io.ByteArrayInputStream
import java.io.IOException

/**
 * The one file the user chose through the system picker. Only this URI is ever read: no filesystem path,
 * sibling file or folder grant is inferred. There is no cached copy, so a deleted or revoked file shows as
 * unavailable instead of silently showing stale content.
 */
class DocumentSource(private val resolver: ContentResolver, val uri: Uri) {
    val address = DocumentAddress(uri.toString())

    /** Keeps read access across reboots. Throws when the provider did not grant a persistable read. */
    fun takeReadPermission(resultFlags: Int) {
        val read = resultFlags and Intent.FLAG_GRANT_READ_URI_PERMISSION
        if (read == 0) throw SecurityException("No read grant")
        resolver.takePersistableUriPermission(uri, read)
        if (!hasReadPermission()) throw SecurityException("No persisted read grant")
    }

    fun hasReadPermission(): Boolean =
        resolver.persistedUriPermissions.any { it.uri == uri && it.isReadPermission }

    fun releaseReadPermission() {
        try {
            resolver.releasePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        } catch (_: SecurityException) {
            // The provider may already have revoked this grant. Nothing is left to release.
        }
    }

    @Throws(IOException::class)
    fun verifyReadable() {
        if (!hasReadPermission()) throw SecurityException("Persisted grant was revoked")
        resolver.openInputStream(uri).use { stream ->
            (stream ?: throw IOException("Provider returned no stream")).read()
        }
    }

    /** The provider's display name. Call off the main thread because providers may block. */
    fun title(): DocumentTitle? = try {
        resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            // Some providers ignore the projection, so the column is looked up by name.
            val column = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (column >= 0 && cursor.moveToFirst() && !cursor.isNull(column)) {
                DocumentTitle.fromDisplayName(cursor.getString(column))
            } else {
                null
            }
        }
    } catch (_: RuntimeException) {
        null // The title is only a label. verifyReadable() and read() report real access failures.
    }

    /** Streams the HTML to WebView. The page's own charset or BOM decides the encoding. */
    @Throws(IOException::class)
    fun read(): WebResourceResponse {
        if (!hasReadPermission()) throw SecurityException("Persisted grant was revoked")
        val stream = resolver.openInputStream(uri) ?: throw IOException("Provider returned no stream")
        return WebResourceResponse(
            "text/html", null, 200, "OK",
            mapOf(
                "Cache-Control" to "no-store",
                "Content-Security-Policy" to "frame-src 'none'; object-src 'none'; form-action 'none'; worker-src 'none'",
            ),
            stream,
        )
    }

    companion object {
        fun blocked(status: Int, message: String) = WebResourceResponse(
            "text/plain", "UTF-8", status, "Unavailable", mapOf("Cache-Control" to "no-store"),
            ByteArrayInputStream(message.toByteArray(Charsets.UTF_8)),
        )
    }
}
