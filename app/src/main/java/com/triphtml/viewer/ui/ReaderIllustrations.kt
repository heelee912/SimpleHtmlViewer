package com.triphtml.viewer.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

private const val PHONE_ASPECT = 0.54f

/**
 * Shows, instead of explaining, how the reader works: a document fills a phone, the Back gesture comes in
 * from the edge and the document tools rise from the bottom. It loops slowly. With animations switched
 * off it holds the frame where the tools are up.
 */
@Composable
fun ReaderDemoIllustration(description: String, animate: Boolean, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val time = if (animate) {
        val loop by rememberInfiniteTransition(label = "demo").animateFloat(
            initialValue = 0f, targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(5200, easing = LinearEasing), RepeatMode.Restart),
            label = "demoTime",
        )
        loop
    } else {
        0.64f
    }
    Canvas(modifier.aspectRatio(PHONE_ASPECT).semantics { contentDescription = description }) {
        val screen = drawPhone(colors)
        clipPath(Path().apply { addRoundRect(screen) }) {
            drawDocument(screen, colors)
            val sheet = phase(time, 0.36f, 0.52f) * (1f - phase(time, 0.82f, 0.93f))
            // Lighter than the real scrim: at this size a strong dim turns the page header muddy.
            drawRect(Color.Black.copy(alpha = 0.2f * sheet), Offset(screen.left, screen.top), Size(screen.width, screen.height))
            drawToolsSheet(screen, colors, sheet)
            val gesture = phase(time, 0.16f, 0.3f) * (1f - phase(time, 0.4f, 0.5f))
            drawBackGesture(screen, colors, gesture)
        }
    }
}

/** A phone showing a document that can no longer be found, for the recovery screen. */
@Composable
fun MissingDocumentIllustration(description: String, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    Canvas(modifier.aspectRatio(PHONE_ASPECT).semantics { contentDescription = description }) {
        val screen = drawPhone(colors)
        val pageWidth = screen.width * 0.56f
        val pageHeight = pageWidth * 1.3f
        val left = screen.left + (screen.width - pageWidth) / 2
        val top = screen.top + (screen.height - pageHeight) / 2 - screen.height * 0.04f
        drawRoundRect(
            colors.outline, Offset(left, top), Size(pageWidth, pageHeight), CornerRadius(pageWidth * 0.08f),
            style = Stroke(width = 2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(10.dp.toPx(), 7.dp.toPx()))),
        )
        val center = Offset(left + pageWidth / 2, top + pageHeight / 2)
        val radius = pageWidth * 0.2f
        drawCircle(colors.tertiaryContainer, radius, center)
        val bar = radius * 0.16f
        drawLine(colors.onTertiaryContainer, center + Offset(0f, -radius * 0.45f), center + Offset(0f, radius * 0.12f), bar, StrokeCap.Round)
        drawCircle(colors.onTertiaryContainer, bar * 0.6f, center + Offset(0f, radius * 0.45f))
    }
}

/** Eased progress of [time] through the window [start]..[end]. */
private fun phase(time: Float, start: Float, end: Float): Float =
    FastOutSlowInEasing.transform(((time - start) / (end - start)).coerceIn(0f, 1f))

/** Draws the phone body and returns the rounded screen inside it. */
private fun DrawScope.drawPhone(colors: ColorScheme): RoundRect {
    val corner = size.width * 0.13f
    drawRoundRect(colors.surfaceContainerHighest, size = size, cornerRadius = CornerRadius(corner))
    drawRoundRect(colors.outline.copy(alpha = 0.55f), size = size, cornerRadius = CornerRadius(corner), style = Stroke(1.5.dp.toPx()))
    val inset = size.width * 0.04f
    return RoundRect(inset, inset, size.width - inset, size.height - inset, CornerRadius(corner - inset))
}

private fun DrawScope.drawDocument(screen: RoundRect, colors: ColorScheme) {
    drawRect(colors.surfaceContainerLowest, Offset(screen.left, screen.top), Size(screen.width, screen.height))
    val heroHeight = screen.height * 0.24f
    drawRect(colors.tertiaryContainer, Offset(screen.left, screen.top), Size(screen.width, heroHeight))
    val margin = screen.width * 0.1f
    val bar = screen.height * 0.024f
    bar(colors.onTertiaryContainer.copy(alpha = 0.72f), screen.left + margin, screen.top + heroHeight * 0.48f, screen.width * 0.5f, bar * 1.5f)
    bar(colors.onTertiaryContainer.copy(alpha = 0.4f), screen.left + margin, screen.top + heroHeight * 0.72f, screen.width * 0.32f, bar)
    val widths = floatArrayOf(0.78f, 0.7f, 0.82f, 0.5f, 0.76f, 0.64f, 0.8f, 0.46f, 0.72f)
    widths.forEachIndexed { index, width ->
        val y = screen.top + heroHeight + screen.height * (0.07f + index * 0.072f)
        bar(colors.onSurfaceVariant.copy(alpha = 0.22f), screen.left + margin, y, screen.width * width, bar)
    }
}

private fun DrawScope.drawToolsSheet(screen: RoundRect, colors: ColorScheme, fraction: Float) {
    if (fraction <= 0f) return
    val height = screen.height * 0.42f
    val top = screen.bottom - height * fraction
    val corner = screen.width * 0.08f
    drawRoundRect(colors.surfaceContainerLow, Offset(screen.left, top), Size(screen.width, height + corner), CornerRadius(corner))
    val handleWidth = screen.width * 0.16f
    bar(colors.onSurfaceVariant.copy(alpha = 0.4f), screen.left + (screen.width - handleWidth) / 2, top + height * 0.05f, handleWidth, height * 0.022f)
    val margin = screen.width * 0.08f
    bar(colors.onSurface.copy(alpha = 0.75f), screen.left + margin, top + height * 0.15f, screen.width * 0.42f, height * 0.05f)
    val gap = screen.width * 0.04f
    val tileWidth = (screen.width - margin * 2 - gap) / 2
    val tileTop = top + height * 0.32f
    for (column in 0..1) {
        val left = screen.left + margin + column * (tileWidth + gap)
        drawRoundRect(colors.secondaryContainer, Offset(left, tileTop), Size(tileWidth, height * 0.34f), CornerRadius(corner * 0.7f))
        drawCircle(colors.onSecondaryContainer.copy(alpha = 0.55f), height * 0.05f, Offset(left + tileWidth / 2, tileTop + height * 0.13f))
        bar(colors.onSecondaryContainer.copy(alpha = 0.45f), left + tileWidth * 0.25f, tileTop + height * 0.23f, tileWidth * 0.5f, height * 0.035f)
    }
    bar(colors.onSurfaceVariant.copy(alpha = 0.35f), screen.left + screen.width * 0.34f, top + height * 0.8f, screen.width * 0.32f, height * 0.035f)
}

private fun DrawScope.drawBackGesture(screen: RoundRect, colors: ColorScheme, progress: Float) {
    if (progress <= 0f) return
    val radius = screen.width * 0.085f
    val center = Offset(screen.left - radius + progress * radius * 2.1f, screen.top + screen.height * 0.5f)
    drawCircle(colors.primary.copy(alpha = progress), radius, center)
    val arm = radius * 0.42f
    val stroke = radius * 0.2f
    val tip = center + Offset(-arm * 0.45f, 0f)
    drawLine(colors.onPrimary, tip, tip + Offset(arm, -arm), stroke, StrokeCap.Round)
    drawLine(colors.onPrimary, tip, tip + Offset(arm, arm), stroke, StrokeCap.Round)
}

private fun DrawScope.bar(color: Color, left: Float, top: Float, width: Float, height: Float) =
    drawRoundRect(color, Offset(left, top), Size(width, height), CornerRadius(height / 2))
