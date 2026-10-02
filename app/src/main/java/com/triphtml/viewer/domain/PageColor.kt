package com.triphtml.viewer.domain

/**
 * Reads the computed CSS background colour WebView returns from evaluateJavascript, such as
 * `"rgb(245, 247, 246)"`. The reader paints the band beside a camera cutout with it so the band blends
 * into the page instead of looking like a leftover app bar.
 */
object PageColor {
    private val rgb = Regex(
        """rgba?\(\s*(\d{1,3})\s*,\s*(\d{1,3})\s*,\s*(\d{1,3})\s*(?:,\s*(\d+(?:\.\d+)?|\.\d+)\s*)?\)""",
    )

    /** Null unless the value is a fully opaque rgb()/rgba() colour. A translucent page colour would mislead. */
    fun opaqueArgb(javascriptResult: String?): Int? {
        var value = javascriptResult?.trim() ?: return null
        if (value.length >= 2 && value.startsWith("\"") && value.endsWith("\"")) value = value.substring(1, value.length - 1)
        val match = rgb.matchEntire(value) ?: return null
        val alpha = match.groupValues[4]
        if (alpha.isNotEmpty() && alpha.toDouble() != 1.0) return null
        val (red, green, blue) = match.groupValues.subList(1, 4).map { it.toInt() }
        if (red > 255 || green > 255 || blue > 255) return null
        return (0xFF shl 24) or (red shl 16) or (green shl 8) or blue
    }
}
