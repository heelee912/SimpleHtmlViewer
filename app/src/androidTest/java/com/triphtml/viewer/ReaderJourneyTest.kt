package com.triphtml.viewer

import android.app.Activity
import android.app.Instrumentation
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.os.SystemClock
import android.util.Log
import android.view.View
import android.view.WindowInsets
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.assertTouchHeightIsEqualTo
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * One reader's journey through the app, in order: first visit, choosing a file, reading full screen,
 * the Back-opened tools, refresh, rotation, in-document places, links, recovery and leaving.
 * Every step uses the real picker, the real WebView and real input.
 */
@RunWith(AndroidJUnit4::class)
class ReaderJourneyTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val probe get() = shared

    private fun text(id: Int) = probe.instrumentation.targetContext.getString(id)

    private fun toolsShown(): Boolean = compose.onAllNodesWithTagCount("document_tools") > 0

    private fun awaitTools(open: Boolean) = compose.waitUntil(5_000) { toolsShown() == open }

    private fun assertAction(tag: String) {
        compose.onNodeWithTag(tag).assertIsDisplayed().assertWidthIsAtLeast(48.dp).assertHeightIsAtLeast(48.dp)
    }

    private fun assertReading() {
        compose.waitUntil(5_000) { compose.onAllNodesWithTagCount("welcome") == 0 && compose.onAllNodesWithTagCount("recovery") == 0 }
        assertTrue("Page not shown", probe.onMain { probe.web().isShown })
        probe.await("System bars stayed visible while reading") { !probe.systemBarsVisible() }
    }

    private fun accessibility(): Int = probe.onMain { probe.web().importantForAccessibility }

    private fun t00_firstVisitShowsHowItWorksAndOneFileAction() {
        compose.onNodeWithTag("welcome").assertIsDisplayed()
        compose.onNodeWithContentDescription(text(R.string.welcome_illustration)).assertIsDisplayed()
        assertAction("choose_document")
        compose.onNodeWithText(text(R.string.choose_document)).assertIsDisplayed()
        assertFalse("Page shown before a file was chosen", probe.onMain { probe.web().isShown })
        assertTrue("System bars hidden on the welcome screen", probe.systemBarsVisible())
    }

    private fun t01_choosingAFileOpensItFullScreen() {
        compose.onNodeWithTag("choose_document").performClick()
        probe.pickFixture("trip.html")
        probe.awaitReady()
        assertEquals("\"rgb(0, 128, 0)\"", probe.js("getComputedStyle(document.getElementById('css')).color"))
        assertEquals(1, probe.instrumentation.targetContext.contentResolver.persistedUriPermissions.size)
        assertEquals("\"durable-value\"", probe.js("localStorage.setItem('persist-proof','durable-value');localStorage.getItem('persist-proof')"))
        originalUrl = probe.js("location.href")
        assertReading()
        assertFalse(toolsShown())
        // Only a camera cutout may separate the page from the screen edges.
        assertEquals("ok", probe.onMain {
            val decor = requireNotNull(probe.activity).window.decorView
            val safe = decor.rootWindowInsets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
            val page = probe.web()
            if (page.width == decor.width - safe.left - safe.right && page.height == decor.height - safe.top - safe.bottom) "ok"
            else "page ${page.width}x${page.height} decor ${decor.width}x${decor.height} safe $safe"
        })
    }

    private fun t02_backOpensLabelledToolsWithoutMovingThePage() {
        probe.js("window.scrollTo(0, 600);true")
        probe.await("Page did not scroll") { probe.onMain { probe.web().scrollY } > 0 }
        val before = probe.pageGeometry()
        probe.pressBack()
        awaitTools(open = true)
        assertEquals(before, probe.pageGeometry())
        compose.onNodeWithTag("document_title").assertTextEquals("trip")
        assertEquals(0, compose.onAllNodesWithTagCount("previous_place"))
        listOf("refresh", "open_file", "resume_reading").forEach(::assertAction)
        compose.onNodeWithText(text(R.string.exit_hint)).assertIsDisplayed()
        assertEquals(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS, accessibility())
        assertFalse(probe.systemBarsVisible())
    }

    private fun t03_resumeReadingReturnsToTheSamePlace() {
        val before = probe.pageGeometry()
        compose.onNodeWithTag("resume_reading").performClick()
        awaitTools(open = false)
        assertEquals(before, probe.pageGeometry())
        assertEquals(View.IMPORTANT_FOR_ACCESSIBILITY_AUTO, accessibility())
        assertReading()
    }

    private fun t04_accessibilityActionOpensToolsAndTappingThePageClosesThem() {
        assertTrue(probe.onMain { probe.web().performAccessibilityAction(R.id.show_controls, null) })
        awaitTools(open = true)
        val before = probe.pageGeometry()
        val width = probe.onMain { probe.web().width }
        probe.tap(width / 2f, 120f)
        awaitTools(open = false)
        assertEquals(before, probe.pageGeometry())
    }

    private fun t05_draggingTheHandleDownCloses() {
        val before = probe.pageGeometry()
        probe.pressBack()
        awaitTools(open = true)
        SystemClock.sleep(500) // Let the sheet finish rising before grabbing it.
        val bounds = compose.onNodeWithTag("document_title").fetchSemanticsNode().boundsInRoot
        val x = bounds.center.x.toInt()
        val y = bounds.center.y.toInt()
        probe.device.swipe(x, y, x, y + 900, 12)
        awaitTools(open = false)
        assertEquals(before, probe.pageGeometry())
    }

    private fun t06_cancelledPickerKeepsTheDocumentAndPlace() {
        val url = probe.js("location.href")
        val before = probe.pageGeometry()
        probe.pressBack()
        awaitTools(open = true)
        compose.onNodeWithTag("open_file").performClick()
        probe.cancelPicker()
        assertEquals(url, probe.js("location.href"))
        assertEquals(before, probe.pageGeometry())
        assertEquals("\"durable-value\"", probe.js("localStorage.getItem('persist-proof')"))
        assertFalse(toolsShown())
        assertReading()
    }

    private fun t07_refreshRereadsTheFileAndKeepsStorage() {
        val visits = probe.js("Number(localStorage.getItem('visits'))").toInt()
        probe.pressBack()
        awaitTools(open = true)
        compose.onNodeWithTag("refresh").performClick()
        probe.await("Refresh did not reread the file") { probe.js("Number(localStorage.getItem('visits'))").toInt() > visits }
        assertEquals("\"durable-value\"", probe.js("localStorage.getItem('persist-proof')"))
        assertFalse(toolsShown())
    }

    private fun t08_rotationKeepsTheLivePage() {
        probe.awaitReady()
        probe.js("window.liveMarker=42;true")
        val visits = probe.js("localStorage.getItem('visits')")
        val activity = requireNotNull(probe.activity)
        try {
            probe.instrumentation.runOnMainSync { activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE }
            probe.await("Landscape not applied") { activity.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE }
            SystemClock.sleep(600)
            assertEquals("Page was reloaded by rotation", "42", probe.js("window.liveMarker"))
            assertEquals(visits, probe.js("localStorage.getItem('visits')"))
            assertReading()
        } finally {
            probe.instrumentation.runOnMainSync { activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT }
            probe.await("Portrait not restored") { activity.resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT }
            SystemClock.sleep(600)
        }
    }

    private fun t09_anchorStaysInTheDocument() {
        probe.tapHtml("anchor")
        probe.await("Anchor failed") { probe.js("location.hash") == "\"#day2\"" }
        assertTrue(probe.js("location.origin").contains("html-viewer.invalid"))
    }

    private fun t10_previousPlaceAppearsOnlyWithHistoryAndGoesBack() {
        probe.pressBack()
        awaitTools(open = true)
        assertAction("previous_place")
        compose.onNodeWithTag("previous_place").performClick()
        probe.await("Previous place did not leave the anchor") { probe.js("location.hash") == "\"\"" }
        awaitTools(open = false)
        assertReading()
    }

    private fun t11_refreshAfterAnAnchorKeepsTheAnchor() {
        probe.tapHtml("anchor")
        probe.await("Anchor failed") { probe.js("location.hash") == "\"#day2\"" }
        val visits = probe.js("Number(localStorage.getItem('visits'))").toInt()
        probe.pressBack()
        awaitTools(open = true)
        compose.onNodeWithTag("refresh").performClick()
        probe.await("Refresh did not reread the file") { probe.js("Number(localStorage.getItem('visits'))").toInt() > visits }
        assertEquals("\"#day2\"", probe.js("location.hash"))
    }

    private fun t12_tappedLinksGoToAndroidAndMapsGoesToMaps() {
        val launched = mutableListOf<Intent>()
        val monitor = object : Instrumentation.ActivityMonitor() {
            override fun onStartActivity(intent: Intent): Instrumentation.ActivityResult? {
                if (intent.action != Intent.ACTION_VIEW) return null
                synchronized(launched) { launched += Intent(intent) }
                return Instrumentation.ActivityResult(Activity.RESULT_CANCELED, null)
            }
        }
        probe.instrumentation.addMonitor(monitor)
        try {
            for (id in listOf("external", "blank", "popup", "maps")) {
                synchronized(launched) { launched.clear() }
                probe.tapHtml(id)
                probe.await("No Android intent for $id") { synchronized(launched) { launched.isNotEmpty() } }
                SystemClock.sleep(300)
                val intent = synchronized(launched) { assertEquals("Duplicate intents for $id", 1, launched.size); launched[0] }
                assertTrue(intent.hasCategory(Intent.CATEGORY_BROWSABLE))
                if (id == "maps") assertEquals("com.google.android.apps.maps", intent.`package`)
                else assertTrue(intent.dataString!!.startsWith("https://example.com/"))
                assertTrue("Document replaced by $id", probe.js("location.origin").contains("html-viewer.invalid"))
            }
            synchronized(launched) { launched.clear() }
            probe.js("setTimeout(()=>{window.open('https://example.com/automatic');location.href='https://example.com/automatic'},50);true")
            SystemClock.sleep(800)
            synchronized(launched) { assertTrue("A script launched an app on its own", launched.isEmpty()) }
            assertTrue(probe.js("location.origin").contains("html-viewer.invalid"))
        } finally {
            probe.instrumentation.removeMonitor(monitor)
        }
    }

    private fun t13_separateFilesAreRefusedWithADismissibleNotice() {
        val before = probe.pageGeometry()
        assertEquals("404", probe.jsAsync("fetch('missing.js').then(r=>r.status)"))
        compose.waitUntil(5_000) { compose.onAllNodesWithTextCount(text(R.string.relative_unsupported)) > 0 }
        assertEquals(before, probe.pageGeometry())
        // Snackbar's action is drawn at 40dp; Compose expands its actual touch area to 48dp.
        compose.onNodeWithText(text(R.string.dismiss)).assertIsDisplayed().assertTouchHeightIsEqualTo(48.dp).performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithTextCount(text(R.string.relative_unsupported)) == 0 }
        assertEquals(before, probe.pageGeometry())
    }

    private fun t14_anotherDocumentHasItsOwnStorageAndTheFirstReopens() {
        probe.pressBack()
        awaitTools(open = true)
        compose.onNodeWithTag("open_file").performClick()
        probe.pickFixture("other.html")
        probe.awaitReady()
        awaitTools(open = false)
        assertEquals("null", probe.js("localStorage.getItem('persist-proof')"))
        probe.pressBack()
        awaitTools(open = true)
        compose.onNodeWithTag("document_title").assertTextEquals("other")
        compose.onNodeWithTag("open_file").performClick()
        probe.pickFixture("trip.html")
        probe.awaitReady()
        assertEquals("\"durable-value\"", probe.js("localStorage.getItem('persist-proof')"))
        assertEquals(originalUrl, probe.js("location.href"))
    }

    private fun t15_revokedPermissionLeadsToRecovery() {
        val resolver = probe.instrumentation.targetContext.contentResolver
        resolver.persistedUriPermissions.forEach { resolver.releasePersistableUriPermission(it.uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
        probe.pressBack()
        awaitTools(open = true)
        compose.onNodeWithTag("refresh").performClick()
        compose.waitUntil(8_000) { compose.onAllNodesWithTagCount("recovery") > 0 }
        compose.onNodeWithTag("document_status").assertTextEquals(text(R.string.file_unavailable))
        assertAction("choose_document")
        assertAction("retry_document")
        assertFalse(toolsShown())
        probe.await("System bars hidden on recovery") { probe.systemBarsVisible() }
        compose.onNodeWithTag("choose_document").performClick()
        probe.pickFixture("trip.html")
        probe.awaitReady()
        assertEquals("\"durable-value\"", probe.js("localStorage.getItem('persist-proof')"))
        assertReading()
    }

    private fun t16_secondBackLeavesAndRelaunchResumesReading() {
        val url = probe.js("location.href")
        probe.pressBack()
        awaitTools(open = true)
        probe.pressBack()
        probe.await("Second Back did not leave the app") { probe.foregroundPackage() != probe.instrumentation.targetContext.packageName }
        probe.relaunchToFront()
        probe.awaitReady()
        assertEquals(url, probe.js("location.href"))
        assertFalse(toolsShown())
        assertReading()
        // Give Chromium's storage writer time to flush before the separate process-restart test.
        probe.js("localStorage.setItem('persist-proof','durable-value');true")
        SystemClock.sleep(2_000)
    }

    /**
     * The steps share one live activity, so they run in order inside one test. The activity starts after
     * the Compose rule so the rule can see its screens. A failure names the step it happened in.
     */
    @Test fun readerJourney() {
        shared.forgetDocument()
        val steps = listOf<Pair<String, () -> Unit>>(
            "first visit" to ::t00_firstVisitShowsHowItWorksAndOneFileAction,
            "choose file" to ::t01_choosingAFileOpensItFullScreen,
            "Back opens tools" to ::t02_backOpensLabelledToolsWithoutMovingThePage,
            "resume reading" to ::t03_resumeReadingReturnsToTheSamePlace,
            "a11y action and scrim" to ::t04_accessibilityActionOpensToolsAndTappingThePageClosesThem,
            "drag to close" to ::t05_draggingTheHandleDownCloses,
            "cancelled picker" to ::t06_cancelledPickerKeepsTheDocumentAndPlace,
            "refresh" to ::t07_refreshRereadsTheFileAndKeepsStorage,
            "rotation keeps live page" to ::t08_rotationKeepsTheLivePage,
            "anchor" to ::t09_anchorStaysInTheDocument,
            "previous place" to ::t10_previousPlaceAppearsOnlyWithHistoryAndGoesBack,
            "refresh after anchor" to ::t11_refreshAfterAnAnchorKeepsTheAnchor,
            "links to Android" to ::t12_tappedLinksGoToAndroidAndMapsGoesToMaps,
            "separate files" to ::t13_separateFilesAreRefusedWithADismissibleNotice,
            "storage isolation" to ::t14_anotherDocumentHasItsOwnStorageAndTheFirstReopens,
            "revoked permission" to ::t15_revokedPermissionLeadsToRecovery,
            "second Back exits" to ::t16_secondBackLeavesAndRelaunchResumesReading,
        )
        shared.launch().use {
            steps.forEachIndexed { index, (name, step) ->
                try {
                    // Finish the previous screen/sheet animation before the next physical page input.
                    compose.waitForIdle()
                    step()
                } catch (failure: Throwable) {
                    throw AssertionError("Step ${index + 1}/${steps.size} \"$name\" failed: ${failure.message}", failure)
                }
                Log.i(TAG, "PASS ${index + 1}/${steps.size} $name")
            }
        }
    }

    companion object {
        private const val TAG = "ViewerJourney"
        private val shared = ViewerProbe()
        private var originalUrl: String? = null
    }
}
