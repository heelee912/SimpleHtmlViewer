package com.triphtml.viewer

import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.view.View
import android.view.accessibility.AccessibilityManager
import android.view.accessibility.AccessibilityNodeInfo
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.snapshotFlow
import androidx.core.net.toUri
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import com.triphtml.viewer.document.DocumentSource
import com.triphtml.viewer.document.ReadingMemory
import com.triphtml.viewer.document.ReadingPlace
import com.triphtml.viewer.domain.DocumentTitle
import com.triphtml.viewer.reader.DocumentReader
import com.triphtml.viewer.ui.FileProblem
import com.triphtml.viewer.ui.ReaderActions
import com.triphtml.viewer.ui.ReaderApp
import com.triphtml.viewer.ui.ReaderScreen
import com.triphtml.viewer.ui.ReaderUiState
import java.io.IOException
import java.util.concurrent.Executors
import kotlinx.coroutines.launch

/**
 * Opens one HTML file chosen with the system picker and keeps reading it across refreshes and relaunches.
 * The activity owns the live page and the file; [ReaderApp] draws whatever [state] says.
 */
class ViewerActivity : ComponentActivity() {
    private val state = ReaderUiState()
    private val notices = SnackbarHostState()
    private val documentReads = Executors.newSingleThreadExecutor()
    private lateinit var memory: ReadingMemory
    private lateinit var reader: DocumentReader
    private var selection = 0
    private val picker = registerForActivityResult(ActivityResultContracts.StartActivityForResult(), ::onDocumentPicked)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        memory = ReadingMemory(this)
        reader = DocumentReader(this, ReaderEvents())
        reader.view.accessibilityDelegate = ToolsAccessibilityAction()
        setContent { ReaderApp(state, reader.view, Actions(), notices) }
        lifecycleScope.launch { snapshotFlow { state.isReading }.collect(::applySystemBars) }

        val uri = memory.documentUri
        if (uri == null) {
            state.screen = ReaderScreen.Welcome
        } else {
            state.title = memory.title
            startReading(DocumentSource(contentResolver, uri.toUri()), memory.place)
        }
    }

    private fun startReading(source: DocumentSource, place: ReadingPlace) {
        state.toolsOpen = false
        state.screen = ReaderScreen.Reading
        notices.currentSnackbarData?.dismiss()
        reader.open(source, place)
    }

    private fun showProblem(problem: FileProblem, canRetry: Boolean) {
        state.loading = false
        state.opening = false
        state.toolsOpen = false
        notices.currentSnackbarData?.dismiss()
        state.screen = ReaderScreen.Problem(problem, canRetry)
    }

    private fun notify(@StringRes text: Int, duration: SnackbarDuration = SnackbarDuration.Indefinite) {
        lifecycleScope.launch {
            notices.currentSnackbarData?.dismiss()
            notices.showSnackbar(getString(text), actionLabel = getString(R.string.dismiss), duration = duration)
        }
    }

    // ---- Choosing files --------------------------------------------------------------------------

    private fun chooseFile() {
        state.toolsOpen = false
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT)
            .addCategory(Intent.CATEGORY_OPENABLE)
            // Providers often label .html as text/plain or application/octet-stream. Do not hide those files.
            .setType("*/*")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
        try {
            picker.launch(intent)
        } catch (_: ActivityNotFoundException) {
            if (state.isReading) notify(R.string.picker_unavailable) else showProblem(FileProblem.PICKER_MISSING, canRetry = false)
        }
    }

    /** A cancelled picker changes nothing: the open document and its place stay exactly as they were. */
    private fun onDocumentPicked(result: ActivityResult) {
        val data = result.data
        val uri = data?.data
        if (result.resultCode != RESULT_OK || uri == null) return
        val keepCurrent = state.isReading
        val candidate = try {
            DocumentSource(contentResolver, uri).also { it.takeReadPermission(data.flags) }
        } catch (_: IllegalArgumentException) {
            null
        } catch (_: SecurityException) {
            null
        }
        if (candidate == null) {
            if (keepCurrent) notify(R.string.permission_unavailable) else showProblem(FileProblem.PERMISSION_REFUSED, canRetry = false)
            return
        }
        val generation = ++selection
        state.opening = true
        if (keepCurrent) state.loading = true
        documentReads.execute {
            val title = try {
                candidate.verifyReadable()
                Result.success(candidate.title())
            } catch (error: IOException) {
                Result.failure(error)
            } catch (error: SecurityException) {
                Result.failure(error)
            }
            runOnUiThread {
                if (isDestroyed || generation != selection) return@runOnUiThread
                state.opening = false
                title.onSuccess { readable -> replaceDocument(candidate, readable) }
                title.onFailure {
                    state.loading = false
                    if (keepCurrent && state.isReading) {
                        notify(R.string.selection_failed)
                    } else {
                        state.title = null
                        showProblem(FileProblem.UNREADABLE, canRetry = false)
                    }
                }
            }
        }
    }

    private fun replaceDocument(candidate: DocumentSource, title: DocumentTitle?) {
        val previous = reader.document
        memory.rememberDocument(candidate.uri.toString(), title)
        state.title = title
        startReading(candidate, ReadingPlace(url = null, scrollY = 0))
        if (previous != null && previous.uri != candidate.uri) previous.releaseReadPermission()
    }

    private fun refresh() {
        state.toolsOpen = false
        if (reader.document == null) {
            chooseFile()
            return
        }
        state.screen = ReaderScreen.Reading
        reader.refresh()
    }

    // ---- Document tools --------------------------------------------------------------------------

    private fun openTools() {
        if (!state.isReading) return
        state.canReturnToPreviousPlace = reader.canReturnToPreviousPlace
        state.toolsOpen = true
    }

    // The page was hidden from accessibility while the tools were open. Without moving focus back, TalkBack
    // would be left on a node that no longer exists and the reader would lose their place.
    @SuppressLint("AccessibilityFocus")
    private fun closeTools() {
        if (!state.toolsOpen) return
        state.toolsOpen = false
        reader.view.post {
            reader.view.requestFocus()
            if (getSystemService(AccessibilityManager::class.java).isEnabled) {
                reader.view.performAccessibilityAction(AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS, null)
            }
        }
    }

    private inner class Actions : ReaderActions {
        override suspend fun hasPageDialog() = reader.hasOpenDialog()
        override suspend fun dismissPageDialog() = reader.dismissOpenDialog()
        override fun chooseFile() = this@ViewerActivity.chooseFile()
        override fun refresh() = this@ViewerActivity.refresh()
        override fun openTools() = this@ViewerActivity.openTools()
        override fun closeTools() = this@ViewerActivity.closeTools()
        override fun returnToPreviousPlace() {
            closeTools()
            reader.returnToPreviousPlace()
        }
    }

    /** Screen readers get "문서 도구 열기" on the page, the same as Back. */
    private inner class ToolsAccessibilityAction : View.AccessibilityDelegate() {
        override fun onInitializeAccessibilityNodeInfo(host: View, info: AccessibilityNodeInfo) {
            super.onInitializeAccessibilityNodeInfo(host, info)
            info.addAction(AccessibilityNodeInfo.AccessibilityAction(R.id.show_controls, getString(R.string.show_tools)))
        }

        override fun performAccessibilityAction(host: View, action: Int, arguments: Bundle?): Boolean {
            if (action != R.id.show_controls) return super.performAccessibilityAction(host, action, arguments)
            openTools()
            return true
        }
    }

    private inner class ReaderEvents : DocumentReader.Events {
        override fun onLoadingChanged(loading: Boolean) {
            state.loading = loading
        }

        override fun onDocumentUnreadable(source: DocumentSource) = showProblem(FileProblem.UNREADABLE, canRetry = true)

        override fun onPageFailed() {
            if (state.isReading) showProblem(FileProblem.PAGE_FAILED, canRetry = true)
        }

        override fun onSeparateFileBlocked() {
            if (state.isReading) notify(R.string.relative_unsupported)
        }

        override fun onLinkUnavailable() = notify(R.string.link_unavailable, SnackbarDuration.Short)

        override fun onPageBackground(argb: Int?) {
            state.pageColor = argb
        }
    }

    // ---- Window and lifecycle --------------------------------------------------------------------

    /** Reading hides both system bars; a swipe from the edge shows them briefly without resizing the page. */
    private fun applySystemBars(reading: Boolean) {
        WindowCompat.getInsetsController(window, window.decorView).apply {
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            if (reading) hide(WindowInsetsCompat.Type.systemBars()) else show(WindowInsetsCompat.Type.systemBars())
        }
    }

    /** The picker, permission dialogs and transient bars can bring the bars back. Reading hides them again. */
    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) applySystemBars(state.isReading)
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        enableEdgeToEdge() // Re-picks light or dark system bar icons after a theme change.
        applySystemBars(state.isReading)
    }

    override fun onPause() {
        super.onPause()
        if (reader.document != null) memory.rememberPlace(reader.place)
        reader.pause()
    }

    override fun onResume() {
        super.onResume()
        reader.resume()
    }

    override fun onStop() {
        super.onStop()
        // Leaving with Back or Home returns to plain reading next time.
        if (!isChangingConfigurations) state.toolsOpen = false
    }

    override fun onDestroy() {
        ++selection
        documentReads.shutdownNow()
        reader.destroy()
        super.onDestroy()
    }
}
