package com.triphtml.viewer;

import android.content.ContentResolver;
import android.content.Intent;
import android.content.UriPermission;
import android.database.Cursor;
import android.net.Uri;
import android.provider.OpenableColumns;
import android.webkit.WebResourceResponse;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Optional;

/** Reads only the selected URI. No filesystem paths or sibling grants are inferred. */
final class DocumentSource {
    final Uri uri;
    final DocumentAddress address;
    private final ContentResolver resolver;

    DocumentSource(ContentResolver resolver, Uri uri) {
        this.resolver = resolver;
        this.uri = uri;
        address = new DocumentAddress(uri.toString());
    }

    void takeReadPermission(int resultFlags) {
        int flags = resultFlags & Intent.FLAG_GRANT_READ_URI_PERMISSION;
        if (flags == 0) throw new SecurityException("No read grant");
        resolver.takePersistableUriPermission(uri, flags);
        if (!hasReadPermission()) throw new SecurityException("No persisted read grant");
    }

    boolean hasReadPermission() {
        for (UriPermission grant : resolver.getPersistedUriPermissions()) {
            if (uri.equals(grant.getUri()) && grant.isReadPermission()) return true;
        }
        return false;
    }

    void verifyReadable() throws IOException {
        if (!hasReadPermission()) throw new SecurityException("Persisted grant was revoked");
        try (InputStream stream = resolver.openInputStream(uri)) {
            if (stream == null) throw new IOException("Provider returned no stream");
            stream.read();
        }
    }

    /** Reads the provider's display name. Call off the main thread because providers may block. */
    Optional<DocumentTitle> title() {
        try (Cursor cursor = resolver.query(uri, new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) {
            // Some providers ignore the projection, so look the column up by name.
            int column = cursor == null ? -1 : cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
            if (column >= 0 && cursor.moveToFirst() && !cursor.isNull(column)) {
                return DocumentTitle.fromDisplayName(cursor.getString(column));
            }
        } catch (IllegalArgumentException | SecurityException | UnsupportedOperationException ignored) {
            // The title is only a label. verifyReadable() and read() still report real access failures.
        }
        return Optional.empty();
    }

    WebResourceResponse read() throws IOException {
        if (!hasReadPermission()) throw new SecurityException("Persisted grant was revoked");
        InputStream stream = resolver.openInputStream(uri);
        if (stream == null) throw new IOException("Provider returned no stream");
        // Let HTML's charset/BOM determine encoding. No cached copy masks deletion or revocation.
        return new WebResourceResponse("text/html", null, 200, "OK",
                Map.of("Cache-Control", "no-store", "Content-Security-Policy",
                        "frame-src 'none'; object-src 'none'; form-action 'none'; worker-src 'none'"), stream);
    }

    static WebResourceResponse blocked(int status, String message) {
        return new WebResourceResponse("text/plain", "UTF-8", status, "Unavailable",
                Map.of("Cache-Control", "no-store"),
                new ByteArrayInputStream(message.getBytes(StandardCharsets.UTF_8)));
    }
}
