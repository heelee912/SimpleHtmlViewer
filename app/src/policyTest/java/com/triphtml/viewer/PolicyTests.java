package com.triphtml.viewer;

public final class PolicyTests {
    private static int assertions;
    public static void main(String[] args) {
        DocumentAddress first = new DocumentAddress("content://provider/document/trip");
        DocumentAddress reopened = new DocumentAddress("content://provider/document/trip");
        DocumentAddress second = new DocumentAddress("content://provider/document/other");
        check(first.url().equals(reopened.url()), "Stable origin on reopen");
        check(!first.url().equals(second.url()), "Storage isolated between documents");
        for (String label : java.net.URI.create(first.url()).getHost().split("\\.")) {
            check(label.length() <= 63, "DNS label length");
        }
        check(first.isDocument(first.url() + "#day2"), "Anchor retained");
        check(first.isDocument(first.url() + "?view=day#day2"), "Query retained");
        check(!first.isDocument(first.url().replace("document.html", "secret.js")), "Sibling inaccessible");
        check(!first.contains(first.url().replace("https://", "https://evil@")), "No userinfo origin spoof");
        check(!first.contains(first.url().replace("https:", "http:")), "No insecure virtual origin");
        check(!first.contains(first.url().replace("/document.html", ":443/document.html")), "Exact virtual port");
        check(LinkPolicy.classify(first, first.url() + "#x", false, true) == LinkPolicy.Destination.DOCUMENT, "Internal navigation allowed");
        check(LinkPolicy.classify(first, "https://example.com", true, true) == LinkPolicy.Destination.EXTERNAL, "User external link");
        check(LinkPolicy.classify(first, "https://example.com", false, true) == LinkPolicy.Destination.BLOCKED, "Background external link blocked");
        check(LinkPolicy.classify(first, "https://example.com", true, false) == LinkPolicy.Destination.BLOCKED, "Subframe cannot launch app");
        for (String url : new String[]{"file:///sdcard/trip.html", "content://private/secret", "javascript:alert(1)",
                "data:text/html,hello", "https://user@example.com", "https://", "bad url", second.url(),
                "https://fake.html-viewer.invalid/file"}) {
            check(!LinkPolicy.isSafeExternal(url), "Unsafe target rejected: " + url);
        }
        for (String url : new String[]{"https://www.google.com/maps/dir/?api=1", "https://maps.app.goo.gl/abc",
                "https://goo.gl/maps/abc", "geo:35,139", "google.navigation:q=Tokyo", "https://www.google.co.jp/maps"}) {
            check(LinkPolicy.isGoogleMaps(url), "Maps identified: " + url);
        }
        for (String url : new String[]{"https://google.com.evil.test/maps", "https://evilgoogle.com/maps",
                "https://www.google.com/maps-evil", "https://goo.gl/other", "https://example.com/maps"}) {
            check(!LinkPolicy.isGoogleMaps(url), "Maps lookalike rejected");
        }
        check(LinkPolicy.classify(first, "intent://maps/#Intent;scheme=https;end", false, true)
                == LinkPolicy.Destination.BLOCKED, "No automatic intent launch");
        check(title("R56.html").equals("R56"), "HTML extension removed from title");
        check(title("Tokyo Trip.HTM").equals("Tokyo Trip"), "Extension match ignores case");
        check(title("  오사카 일정.html ").equals("오사카 일정"), "Title trimmed");
        check(title("notes.html.txt").equals("notes.html.txt"), "Only a final HTML extension is removed");
        check(title(".html").equals(".html"), "A bare extension stays as the name");
        check(DocumentTitle.fromDisplayName(null).isEmpty(), "Missing provider name gives no title");
        check(DocumentTitle.fromDisplayName("   ").isEmpty(), "Blank provider name gives no title");
        check(DocumentTitle.fromDisplayName("a.html").equals(DocumentTitle.fromDisplayName("a")), "Titles compare by value");
        check(CssColor.opaqueArgb("\"rgb(245, 247, 246)\"").orElseThrow() == 0xFFF5F7F6, "Computed rgb() read");
        check(CssColor.opaqueArgb("\"rgba(10, 20, 30, 1)\"").orElseThrow() == 0xFF0A141E, "Opaque rgba() read");
        check(CssColor.opaqueArgb("\"rgba(0, 0, 0, 0)\"").isEmpty(), "Transparent page gives no band colour");
        check(CssColor.opaqueArgb("\"rgba(255, 255, 255, 0.5)\"").isEmpty(), "Translucent page gives no band colour");
        check(CssColor.opaqueArgb("\"\"").isEmpty() && CssColor.opaqueArgb("null").isEmpty()
                && CssColor.opaqueArgb(null).isEmpty(), "Missing value gives no band colour");
        check(CssColor.opaqueArgb("\"rgb(300, 0, 0)\"").isEmpty(), "Out-of-range channel rejected");
        check(CssColor.opaqueArgb("\"linear-gradient(red, blue)\"").isEmpty(), "Non-colour value rejected");
        check(CssColor.opaqueArgb("\"rgba(10, 20, 30, .)\"").isEmpty(), "Incomplete alpha rejected without exception");
        check(CssColor.opaqueArgb("\"rgba(10, 20, 30, ..)\"").isEmpty(), "Repeated dots rejected without exception");
        check(CssColor.opaqueArgb("\"rgba(10, 20, 30, 1..0)\"").isEmpty(), "Malformed decimal rejected without exception");
        check(CssColor.opaqueArgb("\"rgba(10, 20, 30, 1.2)\"").isEmpty(), "Out-of-range alpha rejected");
        check(CssColor.opaqueArgb("\"rgba(10, 20, 30, 1.0)\"").orElseThrow() == 0xFF0A141E, "Opaque decimal alpha accepted");
        System.out.println("PASS: " + assertions + " policy assertions");
    }
    private static String title(String displayName) {
        return DocumentTitle.fromDisplayName(displayName).orElseThrow().text();
    }
    private static void check(boolean condition, String message) {
        assertions++;
        if (!condition) throw new AssertionError(message);
    }
}
