package com.triphtml.viewer;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;
import java.util.Set;

/** Only user gestures may leave the document. Scheme allowlisting excludes file and app internals. */
final class LinkPolicy {
    enum Destination { DOCUMENT, EXTERNAL, BLOCKED }
    private static final Set<String> EXTERNAL_SCHEMES = Set.of(
            "https", "http", "geo", "google.navigation", "google.streetview", "mailto", "tel", "sms");

    static URI parse(String url) {
        try { return url == null ? null : new URI(url); }
        catch (URISyntaxException error) { return null; }
    }

    static Destination classify(DocumentAddress document, String url, boolean userGesture, boolean mainFrame) {
        if (!mainFrame) return Destination.BLOCKED;
        if (document != null && document.isDocument(url)) return Destination.DOCUMENT;
        if (!userGesture || DocumentAddress.isVirtual(url)) return Destination.BLOCKED;
        URI uri = parse(url);
        if (uri == null || uri.getScheme() == null) return Destination.BLOCKED;
        String scheme = uri.getScheme().toLowerCase(Locale.ROOT);
        if ("intent".equals(scheme)) return Destination.EXTERNAL; // Sanitized before dispatch.
        return isSafeExternal(url) ? Destination.EXTERNAL : Destination.BLOCKED;
    }

    static boolean isSafeExternal(String url) {
        URI uri = parse(url);
        if (uri == null || uri.getScheme() == null || DocumentAddress.isVirtual(url)) return false;
        String scheme = uri.getScheme().toLowerCase(Locale.ROOT);
        if (!EXTERNAL_SCHEMES.contains(scheme)) return false;
        if (scheme.equals("http") || scheme.equals("https")) {
            return uri.getHost() != null && uri.getUserInfo() == null;
        }
        return uri.getRawSchemeSpecificPart() != null && !uri.getRawSchemeSpecificPart().isEmpty();
    }

    static boolean isGoogleMaps(String url) {
        URI uri = parse(url);
        if (uri == null || uri.getScheme() == null) return false;
        String scheme = uri.getScheme().toLowerCase(Locale.ROOT);
        if (Set.of("geo", "google.navigation", "google.streetview").contains(scheme)) return true;
        if (!scheme.equals("https") && !scheme.equals("http")) return false;
        String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT);
        String path = uri.getPath() == null ? "" : uri.getPath();
        if (Set.of("maps.google.com", "maps.app.goo.gl").contains(host)) return true;
        if (host.equals("goo.gl")) return path.startsWith("/maps/") || path.equals("/maps");
        return Set.of("google.com", "www.google.com", "google.co.jp", "www.google.co.jp",
                "google.co.kr", "www.google.co.kr").contains(host)
                && (path.equals("/maps") || path.startsWith("/maps/"));
    }
}
