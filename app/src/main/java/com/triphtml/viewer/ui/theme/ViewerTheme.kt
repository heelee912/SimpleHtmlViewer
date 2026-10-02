package com.triphtml.viewer.ui.theme

import android.provider.Settings
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.intl.LocaleList
import androidx.compose.ui.text.style.LineBreak

/**
 * Warm paper, deep pine ink and one persimmon accent, the same colours as the launcher icon. Only the
 * app's own screens use this palette: the user's HTML is never restyled.
 */
private val Light = lightColorScheme(
    primary = Color(0xFF2A4B43), onPrimary = Color.White,
    primaryContainer = Color(0xFFCFE5DC), onPrimaryContainer = Color(0xFF0E2A24),
    secondary = Color(0xFF5F625A), onSecondary = Color.White,
    secondaryContainer = Color(0xFFE9E4D8), onSecondaryContainer = Color(0xFF2B2A24),
    tertiary = Color(0xFFA8461F), onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFDBCC), onTertiaryContainer = Color(0xFF3A0F00),
    background = Color(0xFFF7F5F0), onBackground = Color(0xFF1C2422),
    surface = Color(0xFFF7F5F0), onSurface = Color(0xFF1C2422),
    surfaceVariant = Color(0xFFE4E0D7), onSurfaceVariant = Color(0xFF57605B),
    surfaceContainerLowest = Color.White, surfaceContainerLow = Color(0xFFF2EFE8),
    surfaceContainer = Color(0xFFECE9E1), surfaceContainerHigh = Color(0xFFE7E3DA),
    surfaceContainerHighest = Color(0xFFE1DDD3),
    outline = Color(0xFF7B847F), outlineVariant = Color(0xFFD6D1C6),
)

private val Dark = darkColorScheme(
    primary = Color(0xFFA7CFC3), onPrimary = Color(0xFF0F3730),
    primaryContainer = Color(0xFF2A4B43), onPrimaryContainer = Color(0xFFCFE5DC),
    secondary = Color(0xFFC9C6BA), onSecondary = Color(0xFF31302A),
    secondaryContainer = Color(0xFF3A3933), onSecondaryContainer = Color(0xFFE9E4D8),
    tertiary = Color(0xFFFFB596), onTertiary = Color(0xFF5A1B00),
    tertiaryContainer = Color(0xFF7E3415), onTertiaryContainer = Color(0xFFFFDBCC),
    background = Color(0xFF121614), onBackground = Color(0xFFE2E3DF),
    surface = Color(0xFF121614), onSurface = Color(0xFFE2E3DF),
    surfaceVariant = Color(0xFF3F4844), onSurfaceVariant = Color(0xFFBEC8C3),
    surfaceContainerLowest = Color(0xFF0D110F), surfaceContainerLow = Color(0xFF1A1E1C),
    surfaceContainer = Color(0xFF1E2220), surfaceContainerHigh = Color(0xFF282D2A),
    surfaceContainerHighest = Color(0xFF333835),
    outline = Color(0xFF89928E), outlineVariant = Color(0xFF3F4844),
)

/** Korean wraps between syllables unless told otherwise. Phrase breaking keeps words whole. */
private fun TextStyle.korean() = copy(
    localeList = LocaleList("ko-KR"),
    lineBreak = LineBreak(LineBreak.Strategy.HighQuality, LineBreak.Strictness.Normal, LineBreak.WordBreak.Phrase),
)

private val ViewerTypography = Typography().run {
    copy(
        displaySmall = displaySmall.korean(),
        headlineLarge = headlineLarge.copy(fontWeight = FontWeight.SemiBold).korean(),
        headlineMedium = headlineMedium.copy(fontWeight = FontWeight.SemiBold).korean(),
        headlineSmall = headlineSmall.copy(fontWeight = FontWeight.SemiBold).korean(),
        titleLarge = titleLarge.copy(fontWeight = FontWeight.SemiBold).korean(),
        titleMedium = titleMedium.copy(fontWeight = FontWeight.SemiBold).korean(),
        titleSmall = titleSmall.korean(),
        bodyLarge = bodyLarge.korean(),
        bodyMedium = bodyMedium.korean(),
        bodySmall = bodySmall.korean(),
        labelLarge = labelLarge.korean(),
        labelMedium = labelMedium.korean(),
        labelSmall = labelSmall.korean(),
    )
}

@Composable
fun ViewerTheme(content: @Composable () -> Unit) {
    val colors: ColorScheme = if (isSystemInDarkTheme()) Dark else Light
    MaterialTheme(colorScheme = colors, typography = ViewerTypography, content = content)
}

/** True when the user switched animations off. Motion then jumps straight to its end state. */
@Composable
fun rememberReducedMotion(): Boolean {
    val resolver = LocalContext.current.contentResolver
    return remember(resolver) { Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f }
}
