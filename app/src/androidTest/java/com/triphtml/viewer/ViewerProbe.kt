package com.triphtml.viewer

import android.content.Intent
import android.graphics.Bitmap
import android.os.SystemClock
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.WindowInsets
import android.webkit.WebView
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import org.json.JSONObject
import org.junit.Assert.assertTrue

/**
 * Drives the real activity, its real WebView and the real system picker. Everything that touches views
 * runs on the main thread; waits poll with a deadline instead of sleeping blindly.
 */
class ViewerProbe {
    var scriptTimeoutSeconds = 5L
    val instrumentation = InstrumentationRegistry.getInstrumentation()
    val device: UiDevice = UiDevice.getInstance(instrumentation)
    private val context = instrumentation.targetContext
    @Volatile var activity: ViewerActivity? = null
        private set

    fun launch(): ActivityScenario<ViewerActivity> =
        ActivityScenario.launch(ViewerActivity::class.java).also { scenario -> scenario.onActivity { activity = it } }

    fun recreate(scenario: ActivityScenario<ViewerActivity>) {
        scenario.recreate()
        scenario.onActivity { activity = it }
    }

    fun forgetDocument() {
        context.getSharedPreferences("document", 0).edit().clear().commit()
        context.contentResolver.persistedUriPermissions.forEach {
            context.contentResolver.releasePersistableUriPermission(it.uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    fun <T> onMain(read: () -> T): T {
        val result = AtomicReference<T>()
        instrumentation.runOnMainSync { result.set(read()) }
        return result.get()
    }

    /** The page view. Looking it up touches no view state, so it is safe from any thread. */
    fun web(): WebView = requireNotNull(requireNotNull(activity).findViewById(R.id.document_view))

    fun js(script: String): String {
        val done = CountDownLatch(1)
        val result = AtomicReference<String>()
        val page = web()
        instrumentation.runOnMainSync { page.evaluateJavascript(script) { result.set(it); done.countDown() } }
        assertTrue("JavaScript callback timeout", done.await(scriptTimeoutSeconds, TimeUnit.SECONDS))
        return result.get()
    }

    fun jsAsync(promise: String): String {
        js("window.asyncResult=null;($promise).then(v=>window.asyncResult=v);true")
        await("Async JavaScript timeout") { js("window.asyncResult") != "null" }
        return js("window.asyncResult")
    }

    fun awaitReady() {
        try {
            await("HTML did not become ready") { onMain { web().isShown } && js("window.fixtureReady===true") == "true" }
        } catch (failure: AssertionError) {
            val page = onMain { web().let { "shown=${it.isShown} visibility=${it.visibility} attached=${it.isAttachedToWindow} url=${it.url}" } }
            throw AssertionError("${failure.message}: $page ready=${js("window.fixtureReady")} fg=${foregroundPackage()}")
        }
    }

    fun await(message: String, timeoutMs: Long = 10_000, condition: () -> Boolean) {
        val deadline = SystemClock.uptimeMillis() + timeoutMs
        while (SystemClock.uptimeMillis() < deadline) {
            if (condition()) return
            SystemClock.sleep(100)
        }
        throw AssertionError(message)
    }

    /** Size and scroll of the page. Opening or closing the tools must not change any of them. */
    fun pageGeometry(): String = onMain { web().let { "${it.width}x${it.height}@${it.scrollY}" } }

    fun systemBarsVisible(): Boolean = onMain {
        requireNotNull(activity).window.decorView.rootWindowInsets?.isVisible(WindowInsets.Type.statusBars()) == true
    }

    fun foregroundPackage(): String = instrumentation.uiAutomation.rootInActiveWindow?.packageName?.toString().orEmpty()

    fun pressBack() = instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)

    fun tap(x: Float, y: Float) {
        val now = SystemClock.uptimeMillis()
        val down = MotionEvent.obtain(now, now, MotionEvent.ACTION_DOWN, x, y, 0).apply { source = InputDevice.SOURCE_TOUCHSCREEN }
        val up = MotionEvent.obtain(now, now + 70, MotionEvent.ACTION_UP, x, y, 0).apply { source = InputDevice.SOURCE_TOUCHSCREEN }
        instrumentation.sendPointerSync(down)
        instrumentation.sendPointerSync(up)
        down.recycle()
        up.recycle()
    }

    /** Taps an element of the HTML the way a finger would, so the page sees a real user gesture. */
    fun tapHtml(id: String) {
        js("document.getElementById('$id').scrollIntoView({block:'center'});true")
        // A refresh restores the previous scroll shortly after loading. Tap only once the element stops moving.
        val measure = "(()=>{let r=document.getElementById('$id').getBoundingClientRect();return {x:r.x+r.width/2,y:r.y+r.height/2,scale:devicePixelRatio}})()"
        var previous = ""
        var current = js(measure)
        val deadline = SystemClock.uptimeMillis() + 3_000
        while (current != previous && SystemClock.uptimeMillis() < deadline) {
            SystemClock.sleep(250)
            previous = current
            current = js(measure)
        }
        val rect = JSONObject(current)
        val origin = onMain { IntArray(2).also(web()::getLocationOnScreen) }
        tap((origin[0] + rect.getDouble("x") * rect.getDouble("scale")).toFloat(), (origin[1] + rect.getDouble("y") * rect.getDouble("scale")).toFloat())
    }

    /** Picks a fixture document in the real DocumentsUI after the app opened it. */
    fun pickFixture(file: String) {
        await("Picker did not open", 8_000) { foregroundPackage().contains("documentsui") }
        if (!clickText(file, 1_500)) {
            if (!clickText(FixtureDocuments.ROOT_TITLE, 1_500)) {
                if (!clickDescription("Show roots", 1_500)) clickDescription("Open navigation drawer", 1_500)
                assertTrue("Fixture root not found", clickText(FixtureDocuments.ROOT_TITLE, 4_000))
            }
            assertTrue("Fixture file not found", clickText(file, 4_000))
        }
        await("Viewer did not return from the picker") { foregroundPackage() == context.packageName }
    }

    fun cancelPicker() {
        await("Picker did not open", 8_000) { foregroundPackage().contains("documentsui") }
        device.pressBack()
        await("Viewer did not return from the picker") { foregroundPackage() == context.packageName }
    }

    private fun clickText(text: String, timeoutMs: Long): Boolean =
        device.wait(Until.findObject(By.text(text)), timeoutMs)?.let { it.click(); true } ?: false

    private fun clickDescription(text: String, timeoutMs: Long): Boolean =
        device.wait(Until.findObject(By.desc(text)), timeoutMs)?.let { it.click(); true } ?: false

    /** Saves a screenshot in the app's private files; the verification script pulls it out. */
    fun capture(name: String) {
        SystemClock.sleep(900) // Let rotation and sheet motion settle.
        val bitmap = requireNotNull(instrumentation.uiAutomation.takeScreenshot()) { "Screenshot unavailable" }
        save(bitmap, name)
    }

    fun save(bitmap: Bitmap, name: String) {
        File(context.filesDir, name).outputStream().use { assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
        bitmap.recycle()
    }

    /** Returns to the live viewer or the new activity created after the user left it. */
    fun relaunchToFront() {
        context.startActivity(
            Intent(context, ViewerActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT),
        )
        await("Viewer did not come back") { foregroundPackage() == context.packageName }
        await("Viewer activity did not resume") {
            onMain {
                ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED)
                    .filterIsInstance<ViewerActivity>().singleOrNull()?.also { activity = it } != null
            }
        }
    }
}
