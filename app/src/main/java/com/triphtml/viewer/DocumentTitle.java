package com.triphtml.viewer;

import java.util.Locale;
import java.util.Optional;

/** The name shown above a document: the provider's display name without its HTML extension. */
final class DocumentTitle {
    private static final String[] HTML_EXTENSIONS = {".html", ".htm"};
    private final String text;

    private DocumentTitle(String text) { this.text = text; }

    /** Empty when the provider reported no usable name. The screen then shows the app name instead. */
    static Optional<DocumentTitle> fromDisplayName(String displayName) {
        if (displayName == null) return Optional.empty();
        String name = displayName.trim();
        String lower = name.toLowerCase(Locale.ROOT);
        for (String extension : HTML_EXTENSIONS) {
            if (lower.endsWith(extension) && lower.length() > extension.length()) {
                name = name.substring(0, name.length() - extension.length()).trim();
                break;
            }
        }
        return name.isEmpty() ? Optional.empty() : Optional.of(new DocumentTitle(name));
    }

    String text() { return text; }

    @Override public boolean equals(Object other) {
        return other instanceof DocumentTitle && ((DocumentTitle) other).text.equals(text);
    }

    @Override public int hashCode() { return text.hashCode(); }
}
