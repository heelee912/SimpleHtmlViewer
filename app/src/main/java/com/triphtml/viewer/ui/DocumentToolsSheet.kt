package com.triphtml.viewer.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.triphtml.viewer.R
import com.triphtml.viewer.domain.DocumentTitle
import com.triphtml.viewer.ui.theme.rememberReducedMotion
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Position of the document tools sheet: 0 is hidden, 1 is fully open. A Back gesture in progress peeks the
 * sheet up to [PEEK] so the gesture visibly leads somewhere before the user commits to it.
 */
@Stable
class ToolsSheetState(private val reduceMotion: Boolean) {
    val fraction = Animatable(0f)

    suspend fun peek(backProgress: Float) = fraction.snapTo(PEEK * backProgress.coerceIn(0f, 1f))

    suspend fun dragBy(delta: Float) = fraction.snapTo((fraction.value + delta).coerceIn(0f, 1f))

    suspend fun settle(open: Boolean) {
        val target = if (open) 1f else 0f
        when {
            reduceMotion -> fraction.snapTo(target)
            open -> fraction.animateTo(target, spring(dampingRatio = 0.86f, stiffness = Spring.StiffnessMediumLow))
            else -> fraction.animateTo(target, tween(durationMillis = 190, easing = FastOutLinearInEasing))
        }
    }

    companion object {
        const val PEEK = 0.3f
    }
}

@Composable
fun rememberToolsSheetState(): ToolsSheetState {
    val reduceMotion = rememberReducedMotion()
    return remember(reduceMotion) { ToolsSheetState(reduceMotion) }
}

/**
 * Temporary tools over the document, opened with Back. The sheet is an overlay: the page underneath keeps
 * its size and scroll position. Drag the handle down, tap the dimmed page or choose "읽기 계속" to close.
 */
@Composable
fun DocumentToolsSheet(state: ToolsSheetState, open: Boolean, title: DocumentTitle?, canReturnToPreviousPlace: Boolean, actions: ReaderActions) {
    val scope = rememberCoroutineScope()
    LaunchedEffect(open) { state.settle(open) }
    val fraction = state.fraction.value
    if (fraction <= 0f) return
    val paneTitle = stringResource(R.string.tools_title)
    BoxWithConstraints(Modifier.fillMaxSize()) {
        Box(
            Modifier.fillMaxSize()
                .graphicsLayer { alpha = fraction }
                .background(Color.Black.copy(alpha = 0.42f))
                .pointerInput(open) { if (open) detectTapGestures { actions.closeTools() } }
                .clearAndSetSemantics {}
                .testTag("tools_scrim"),
        )
        var sheetHeight by remember { mutableIntStateOf(1) }
        val dragState = rememberDraggableState { delta -> scope.launch { state.dragBy(-delta / sheetHeight) } }
        Surface(
            modifier = Modifier.align(Alignment.BottomCenter).widthIn(max = 560.dp).fillMaxWidth()
                .heightIn(max = maxHeight - 40.dp)
                .onSizeChanged { sheetHeight = it.height.coerceAtLeast(1) }
                .graphicsLayer { translationY = (1f - fraction) * sheetHeight }
                .semantics { this.paneTitle = paneTitle }
                .testTag("document_tools"),
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            shadowElevation = 18.dp,
        ) {
            Column(
                Modifier.verticalScroll(rememberScrollState())
                    .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom))
                    .padding(start = 24.dp, end = 24.dp, bottom = 14.dp),
            ) {
                SheetHeader(
                    title = title,
                    modifier = Modifier.draggable(
                        dragState, Orientation.Vertical, enabled = open,
                        onDragStopped = { velocity ->
                            if (state.fraction.value < 0.75f || velocity > 1600f) actions.closeTools() else state.settle(true)
                        },
                    ),
                )
                Spacer(Modifier.height(20.dp))
                if (canReturnToPreviousPlace) {
                    OutlinedButton(
                        onClick = actions::returnToPreviousPlace,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).testTag("previous_place"),
                    ) {
                        Icon(painterResource(R.drawable.ic_arrow_back), contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(10.dp))
                        Text(stringResource(R.string.previous_place), style = MaterialTheme.typography.titleSmall)
                    }
                    Spacer(Modifier.height(12.dp))
                }
                ActionTiles(open, actions)
                Spacer(Modifier.height(12.dp))
                TextButton(
                    onClick = actions::closeTools,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).testTag("resume_reading"),
                ) {
                    Text(stringResource(R.string.resume_reading), style = MaterialTheme.typography.titleSmall)
                }
                Text(
                    stringResource(R.string.exit_hint), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(top = 2.dp).testTag("exit_hint"),
                )
            }
        }
    }
}

@Composable
private fun SheetHeader(title: DocumentTitle?, modifier: Modifier) {
    Column(modifier.fillMaxWidth()) {
        Box(Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 18.dp), contentAlignment = Alignment.Center) {
            Box(Modifier.size(width = 36.dp, height = 4.dp).clip(RoundedCornerShape(2.dp)).background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)))
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(painterResource(R.drawable.ic_document), contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(22.dp))
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.tools_file_label), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    title?.text ?: stringResource(R.string.app_name), style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface, maxLines = 2, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.semantics { heading() }.testTag("document_title"),
                )
            }
        }
    }
}

/** Refresh and open side by side. With very large text they stack so labels never squeeze. */
@Composable
private fun ActionTiles(open: Boolean, actions: ReaderActions) {
    val firstAction = remember { FocusRequester() }
    LaunchedEffect(open) {
        if (open) {
            delay(60) // Let the sheet attach before moving focus into it.
            firstAction.requestFocus()
        }
    }
    val stacked = LocalDensity.current.fontScale > 1.45f
    val refresh: @Composable (Modifier) -> Unit = { modifier ->
        ActionTile(R.drawable.ic_refresh, R.string.refresh, actions::refresh, modifier.focusRequester(firstAction).testTag("refresh"))
    }
    val openFile: @Composable (Modifier) -> Unit = { modifier ->
        ActionTile(R.drawable.ic_folder_open, R.string.open_file, actions::chooseFile, modifier.testTag("open_file"))
    }
    if (stacked) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            refresh(Modifier.fillMaxWidth())
            openFile(Modifier.fillMaxWidth())
        }
    } else {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            refresh(Modifier.weight(1f))
            openFile(Modifier.weight(1f))
        }
    }
}

@Composable
private fun ActionTile(icon: Int, label: Int, onClick: () -> Unit, modifier: Modifier) {
    Surface(
        onClick = onClick,
        modifier = modifier.heightIn(min = 96.dp),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
    ) {
        Column(
            Modifier.padding(horizontal = 12.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
        ) {
            Icon(painterResource(icon), contentDescription = null, modifier = Modifier.size(26.dp))
            Spacer(Modifier.height(10.dp))
            Text(stringResource(label), style = MaterialTheme.typography.titleSmall, textAlign = TextAlign.Center)
        }
    }
}
