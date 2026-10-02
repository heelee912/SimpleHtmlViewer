package com.triphtml.viewer;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/** A stable origin isolates each SAF document's web storage without a network server. */
final class DocumentAddress {
    static final String DOMAIN_SUFFIX = ".html-viewer.invalid";
    private final String host;

    DocumentAddress(String documentUri) {
        if (documentUri == null || !documentUri.startsWith("content://")) {
            throw new IllegalArgumentException("A content document URI is required");
        }
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(documentUri.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte value : digest) hex.append(String.format(java.util.Locale.ROOT, "%02x", value & 255));
            host = "d" + hex.substring(0, 32) + "." + hex.substring(32) + DOMAIN_SUFFIX;
        } catch (NoSuchAlgorithmException error) {
            throw new AssertionError("SHA-256 is required by Java", error);
        }
    }

    String url() { return "https://" + host + "/document.html"; }

    boolean contains(String url) {
        URI uri = LinkPolicy.parse(url);
        return uri != null && "https".equalsIgnoreCase(uri.getScheme())
                && host.equalsIgnoreCase(uri.getHost()) && uri.getUserInfo() == null
                && uri.getPort() == -1;
    }

    boolean isDocument(String url) {
        URI uri = LinkPolicy.parse(url);
        return contains(url) && uri != null && "/document.html".equals(uri.getRawPath());
    }

    static boolean isVirtual(String url) {
        URI uri = LinkPolicy.parse(url);
        return uri != null && uri.getHost() != null
                && uri.getHost().toLowerCase(java.util.Locale.ROOT).endsWith(DOMAIN_SUFFIX);
    }
}
