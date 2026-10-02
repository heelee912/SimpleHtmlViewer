package com.triphtml.viewer;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads the computed CSS background colour that WebView returns from evaluateJavascript, such as
 * "\"rgb(245, 247, 246)\"". The reader paints the camera-cutout band with it so the band blends into
 * the document instead of looking like a leftover app bar.
 */
final class CssColor {
    private static final Pattern RGB = Pattern.compile(
            "rgba?\\(\\s*(\\d{1,3})\\s*,\\s*(\\d{1,3})\\s*,\\s*(\\d{1,3})\\s*(?:,\\s*(\\d+(?:\\.\\d+)?|\\.\\d+)\\s*)?\\)");

    private CssColor() { }

    /** Empty unless the value is a fully opaque rgb()/rgba() colour. Translucent colours would mislead. */
    static Optional<Integer> opaqueArgb(String javascriptResult) {
        if (javascriptResult == null) return Optional.empty();
        String value = javascriptResult.trim();
        if (value.length() >= 2 && value.startsWith("\"") && value.endsWith("\"")) {
            value = value.substring(1, value.length() - 1);
        }
        Matcher match = RGB.matcher(value);
        if (!match.matches()) return Optional.empty();
        if (match.group(4) != null && Double.parseDouble(match.group(4)) != 1) return Optional.empty();
        int red = Integer.parseInt(match.group(1));
        int green = Integer.parseInt(match.group(2));
        int blue = Integer.parseInt(match.group(3));
        if (red > 255 || green > 255 || blue > 255) return Optional.empty();
        return Optional.of(0xFF000000 | (red << 16) | (green << 8) | blue);
    }
}
