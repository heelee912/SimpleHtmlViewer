package com.triphtml.viewer.ui

import android.view.View
import android.webkit.WebView
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.triphtml.viewer.ui.theme.ViewerTheme
import kotlin.coroutines.cancellation.CancellationException

/**
 * The whole app. While reading, the page fills the screen; nothing of the app is drawn over it except a
 * thin progress line while it loads. The band beside a camera cutout takes the page's own background.
 * Back first dismisses an open HTML dialog. Otherwise it peeks the tools and opens them on release. With the tools open
 * no handler is registered, so the next Back is the system's own and leaves the app.
 */
@Composable
fun ReaderApp(state: ReaderUiState, document: WebView, actions: ReaderActions, notices: SnackbarHostState) {
    ViewerTheme {
        val sheet = rememberToolsSheetState()
        val reading = state.isReading
        val band = state.pageColor?.let(::Color) ?: Color.White
        Box(Modifier.fillMaxSize().background(if (reading) band else MaterialTheme.colorScheme.surface)) {
            AndroidView(
                factory = { document },
                modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing),
                update = { page ->
                    // Read state here, not from a captured value: AndroidView reruns this block only when
                    // state read inside it changes, so a captured flag would leave the page hidden.
                    page.visibility = if (state.isReading) View.VISIBLE else View.GONE
                    // While the tools are open they are modal: the page is hidden from screen readers.
                    page.importantForAccessibility = if (state.toolsOpen) {
                        View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
                    } else {
                        View.IMPORTANT_FOR_ACCESSIBILITY_AUTO
                    }
                },
            )
            AnimatedVisibility(
                visible = reading && state.loading, enter = fadeIn(), exit = fadeOut(),
                modifier = Modifier.align(Alignment.TopCenter)
                    .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)),
            ) {
                LinearProgressIndicator(
                    Modifier.fillMaxWidth().height(3.dp).testTag("loading"),
                    color = MaterialTheme.colorScheme.tertiary, trackColor = Color.Transparent,
                )
            }
            Crossfade(targetState = state.screen, label = "screen") { screen ->
                when (screen) {
                    ReaderScreen.Welcome -> WelcomeScreen(state.opening, actions::chooseFile)
                    is ReaderScreen.Problem -> RecoveryScreen(
                        screen.problem, state.title, screen.canRetry, state.opening, actions::chooseFile, actions::refresh,
                    )
                    ReaderScreen.Reading -> Unit
                }
            }
            DocumentToolsSheet(sheet, state.toolsOpen && reading, state.title, state.canReturnToPreviousPlace, actions)
            SnackbarHost(
                notices,
                Modifier.align(Alignment.BottomCenter).windowInsetsPadding(WindowInsets.safeDrawing).padding(horizontal = 8.dp),
            )
        }
        PredictiveBackHandler(enabled = reading && !state.toolsOpen) { progress ->
            try {
                val pageDialogWasOpen = actions.hasPageDialog()
                progress.collect { if (!pageDialogWasOpen) sheet.peek(it.progress) }
                val handledByPage = actions.dismissPageDialog()
                if (pageDialogWasOpen || handledByPage) sheet.settle(open = false) else actions.openTools()
            } catch (cancelled: CancellationException) {
                sheet.settle(open = false)
                throw cancelled
            }
        }
    }
}
