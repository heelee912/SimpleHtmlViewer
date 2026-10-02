package com.triphtml.viewer

import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.ColorDrawable
import android.os.SystemClock
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Runs in a fresh process after the journey, or after a device reboot. */
@RunWith(AndroidJUnit4::class)
class RelaunchTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val probe = ViewerProbe()

    @Test fun reopensTheSameDocumentStraightIntoReading() {
        probe.launch().use {
            probe.awaitReady()
            assertEquals("\"durable-value\"", probe.js("localStorage.getItem('persist-proof')"))
            assertEquals(1, probe.instrumentation.targetContext.contentResolver.persistedUriPermissions.size)
            assertEquals(0, compose.onAllNodesWithTagCount("welcome"))
            assertEquals(0, compose.onAllNodesWithTagCount("document_tools"))
            probe.await("System bars visible after relaunch") { !probe.systemBarsVisible() }
        }
    }
}

/** The verification script marks the fixture deleted before this runs. */
@RunWith(AndroidJUnit4::class)
class DeletedDocumentTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val probe = ViewerProbe()

    @Test fun aDeletedDocumentShowsRecoveryWithBothWaysOut() {
        val landscape = InstrumentationRegistry.getArguments().getString("orientation") == "landscape"
        probe.launch().use {
            val activity = requireNotNull(probe.activity)
            try {
                if (landscape) {
                    probe.instrumentation.runOnMainSync { activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE }
                    probe.await("Landscape not applied") { activity.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE }
                }
                compose.waitUntil(10_000) { compose.onAllNodesWithTagCount("recovery") > 0 }
                compose.onNodeWithTag("document_status").assertTextEquals(probe.instrumentation.targetContext.getString(R.string.file_unavailable))
                for (tag in listOf("choose_document", "retry_document")) {
                    compose.onNodeWithTag(tag).assertIsDisplayed().assertWidthIsAtLeast(48.dp).assertHeightIsAtLeast(48.dp)
                }
                probe.capture(if (landscape) "recovery-landscape.png" else "recovery.png")
            } finally {
                probe.instrumentation.runOnMainSync { activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED }
            }
        }
    }
}

/** A real hand-off to Google Maps (installed) or the browser and resolver (Maps disabled by the script). */
@RunWith(AndroidJUnit4::class)
class MapsHandoffTest {
    private val probe = ViewerProbe()

    @Test fun mapsLinkLeavesForAnotherAppAndTheDocumentWaits() {
        val mapsInstalled = InstrumentationRegistry.getArguments().getString("maps") != "absent"
        probe.launch().use {
            probe.awaitReady()
            val visits = probe.js("localStorage.getItem('visits')")
            val url = probe.js("location.href")
            probe.tapHtml("maps")
            probe.await("Expected app did not appear") {
                val shown = probe.foregroundPackage()
                if (mapsInstalled) shown == "com.google.android.apps.maps" else shown == "com.android.chrome" || shown.contains("resolver")
            }
            probe.relaunchToFront()
            probe.awaitReady()
            assertEquals(visits, probe.js("localStorage.getItem('visits')"))
            assertEquals(url, probe.js("location.href"))
            if (!mapsInstalled) {
                assertFalse(probe.onMain { com.triphtml.viewer.links.ExternalLinks(requireNotNull(probe.activity)).open("geo:0,0?q=Tokyo") })
            }
        }
    }
}

/** Run by the presentation script with 200% fonts, optionally on a narrow screen. */
@RunWith(AndroidJUnit4::class)
class PresentationTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val probe = ViewerProbe()
    private val context = probe.instrumentation.targetContext

    private fun assertReachable(tag: String) {
        compose.onNodeWithTag(tag).performScrollTo().assertIsDisplayed().assertWidthIsAtLeast(48.dp).assertHeightIsAtLeast(48.dp)
    }

    private fun rotate(orientation: Int, expected: Int) {
        val activity = requireNotNull(probe.activity)
        probe.instrumentation.runOnMainSync { activity.requestedOrientation = orientation }
        probe.await("Orientation not applied") { activity.resources.configuration.orientation == expected }
        SystemClock.sleep(700)
    }

    @Test fun largeTextKeepsEveryToolAndTheFileActionReachable() {
        assertTrue("Run with font_scale 2.0", context.resources.configuration.fontScale >= 1.99f)
        val saved = context.getSharedPreferences("document", 0)
        val uri = saved.getString("uri", null)
        val title = saved.getString("title", null)
        val longTitle = "아주 긴 여행 일정 파일 이름 — 히로시마와 기타큐슈를 돌아보는 여행 계획"
        try {
            saved.edit().putString("title", longTitle).commit()
            probe.launch().use {
                probe.awaitReady()
                probe.pressBack()
                compose.waitUntil(5_000) { compose.onAllNodesWithTagCount("document_tools") > 0 }
                compose.onNodeWithTag("document_title").assertTextEquals(longTitle)
                val layout = mutableListOf<TextLayoutResult>()
                compose.onNodeWithTag("document_title").fetchSemanticsNode().config[SemanticsActions.GetTextLayoutResult].action?.invoke(layout)
                assertTrue("Title takes more than two lines", layout.single().lineCount <= 2)
                probe.capture("tools-font200-portrait.png")
                listOf("refresh", "open_file", "resume_reading").forEach(::assertReachable)
                rotate(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE, Configuration.ORIENTATION_LANDSCAPE)
                assertEquals("Rotation closed the tools", 1, compose.onAllNodesWithTagCount("document_tools"))
                listOf("refresh", "open_file", "resume_reading").forEach(::assertReachable)
                val sheetWidth = compose.onNodeWithTag("document_tools").fetchSemanticsNode().size.width
                assertTrue("Sheet wider than 560dp", sheetWidth <= 560 * context.resources.displayMetrics.density + 1)
                probe.capture("tools-font200-landscape.png")
                rotate(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT, Configuration.ORIENTATION_PORTRAIT)
            }
            saved.edit().remove("uri").remove("title").commit()
            probe.launch().use {
                rotate(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE, Configuration.ORIENTATION_LANDSCAPE)
                compose.waitUntil(5_000) { compose.onAllNodesWithTagCount("welcome") > 0 }
                compose.onNodeWithTag("choose_document").assertIsDisplayed().assertWidthIsAtLeast(48.dp).assertHeightIsAtLeast(48.dp)
                probe.capture("welcome-font200-landscape.png")
                rotate(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT, Configuration.ORIENTATION_PORTRAIT)
                compose.onNodeWithTag("choose_document").assertIsDisplayed()
                probe.capture("welcome-font200-portrait.png")
            }
        } finally {
            saved.edit().putString("uri", uri).apply { if (title == null) remove("title") else putString("title", title) }.commit()
        }
    }

    @Test fun launcherIconHasColourAndMonochromeLayers() {
        val icon = context.getDrawable(R.mipmap.ic_launcher)
        assertTrue("Icon is not adaptive", icon is AdaptiveIconDrawable)
        val adaptive = icon as AdaptiveIconDrawable
        val monochrome = assertNotNullAndGet(adaptive.monochrome)
        probe.save(render(adaptive), "icon-color-96.png")
        val tinted = requireNotNull(monochrome.constantState).newDrawable().mutate().apply { setTint(Color.rgb(24, 63, 56)) }
        probe.save(render(AdaptiveIconDrawable(ColorDrawable(Color.rgb(215, 232, 225)), tinted)), "icon-themed-96.png")
    }

    private fun assertNotNullAndGet(value: android.graphics.drawable.Drawable?): android.graphics.drawable.Drawable {
        assertNotNull("Monochrome layer missing", value)
        return value!!
    }

    private fun render(icon: android.graphics.drawable.Drawable): Bitmap {
        val bitmap = Bitmap.createBitmap(96, 96, Bitmap.Config.ARGB_8888)
        icon.setBounds(0, 0, 96, 96)
        icon.draw(Canvas(bitmap))
        var painted = 0
        for (y in 0 until 96) for (x in 0 until 96) if (Color.alpha(bitmap.getPixel(x, y)) > 0) painted++
        assertTrue("Adaptive mask is empty or missing", painted in 1001 until 96 * 96)
        return bitmap
    }
}
