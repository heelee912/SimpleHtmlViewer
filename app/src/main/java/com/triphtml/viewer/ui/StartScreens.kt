package com.triphtml.viewer.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.triphtml.viewer.R
import com.triphtml.viewer.domain.DocumentTitle
import com.triphtml.viewer.ui.theme.rememberReducedMotion

@Composable
fun WelcomeScreen(opening: Boolean, onChooseFile: () -> Unit) {
    val animate = !rememberReducedMotion()
    StartLayout(
        tag = "welcome",
        illustration = { modifier ->
            ReaderDemoIllustration(stringResource(R.string.welcome_illustration), animate && !opening, modifier)
        },
        header = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Image(painterResource(R.drawable.ic_brand_mark), contentDescription = null, modifier = Modifier.size(28.dp))
                Spacer(Modifier.width(10.dp))
                Text(stringResource(R.string.app_name), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        text = {
            Text(
                stringResource(R.string.welcome_title), style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.semantics { heading() },
            )
            Spacer(Modifier.height(12.dp))
            Text(stringResource(R.string.welcome_body), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        },
        actions = {
            Button(
                onClick = onChooseFile, enabled = !opening,
                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).testTag("choose_document"),
            ) {
                if (opening) {
                    CircularProgressIndicator(Modifier.size(18.dp), color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 2.dp)
                    Spacer(Modifier.width(12.dp))
                    Text(stringResource(R.string.opening), style = MaterialTheme.typography.titleSmall)
                } else {
                    Icon(painterResource(R.drawable.ic_folder_open), contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(10.dp))
                    Text(stringResource(R.string.choose_document), style = MaterialTheme.typography.titleSmall)
                }
            }
        },
    )
}

@Composable
fun RecoveryScreen(problem: FileProblem, title: DocumentTitle?, canRetry: Boolean, opening: Boolean, onChooseFile: () -> Unit, onRetry: () -> Unit) {
    StartLayout(
        tag = "recovery",
        illustration = { modifier -> MissingDocumentIllustration(stringResource(R.string.recovery_illustration), modifier) },
        header = {},
        text = {
            if (title != null) {
                Text(
                    title.text, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.tertiary,
                    maxLines = 2, overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(8.dp))
            }
            Text(
                stringResource(problem.headline), style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.semantics { heading() },
            )
            Spacer(Modifier.height(10.dp))
            Text(
                stringResource(problem.reason), style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.testTag("document_status"),
            )
        },
        actions = {
            if (problem.offersFileChoice) {
                Button(
                    onClick = onChooseFile, enabled = !opening,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).testTag("choose_document"),
                ) {
                    Icon(painterResource(R.drawable.ic_folder_open), contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(10.dp))
                    Text(stringResource(R.string.choose_again), style = MaterialTheme.typography.titleSmall)
                }
            }
            if (canRetry) {
                TextButton(onClick = onRetry, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).testTag("retry_document")) {
                    Text(stringResource(R.string.retry), style = MaterialTheme.typography.titleSmall)
                }
            }
        },
    )
}

/**
 * Shared frame for the screens shown when no document is on screen. Portrait stacks the illustration over
 * the text; a short landscape screen puts them side by side. Text scrolls with large fonts while the
 * actions stay pinned at the bottom, so the file button is never pushed off screen.
 */
@Composable
private fun StartLayout(
    tag: String,
    illustration: @Composable (Modifier) -> Unit,
    header: @Composable () -> Unit,
    text: @Composable ColumnScope.() -> Unit,
    actions: @Composable ColumnScope.() -> Unit,
) {
    BoxWithConstraints(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)
            .windowInsetsPadding(WindowInsets.safeDrawing).testTag(tag),
    ) {
        val wide = maxWidth > maxHeight && maxHeight < 560.dp
        if (wide) {
            Row(Modifier.fillMaxSize().padding(horizontal = 32.dp, vertical = 20.dp), horizontalArrangement = Arrangement.spacedBy(40.dp)) {
                Box(Modifier.weight(0.4f).fillMaxHeight(), contentAlignment = Alignment.Center) {
                    illustration(Modifier.fillMaxHeight(0.9f))
                }
                Column(Modifier.weight(0.6f).fillMaxHeight()) {
                    Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.Center) {
                        header()
                        Spacer(Modifier.height(16.dp))
                        text()
                    }
                    Spacer(Modifier.height(16.dp))
                    actions()
                }
            }
        } else {
            val art: Dp = (maxHeight * 0.47f).coerceIn(150.dp, 420.dp)
            Column(Modifier.fillMaxSize().padding(horizontal = 24.dp)) {
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                    Spacer(Modifier.height(20.dp))
                    header()
                    Spacer(Modifier.height(28.dp))
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { illustration(Modifier.height(art)) }
                    Spacer(Modifier.height(36.dp))
                    Column(Modifier.widthIn(max = 560.dp)) { text() }
                    Spacer(Modifier.height(24.dp))
                }
                Column(Modifier.widthIn(max = 560.dp).align(Alignment.CenterHorizontally).padding(bottom = 16.dp)) { actions() }
            }
        }
    }
}
