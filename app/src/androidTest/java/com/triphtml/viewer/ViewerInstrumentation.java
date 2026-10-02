package com.triphtml.viewer;

import android.app.Activity;
import android.app.Application;
import android.app.Instrumentation;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.graphics.Rect;
import android.net.Uri;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.InputDevice;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowInsets;
import android.view.accessibility.AccessibilityNodeInfo;
import android.webkit.WebView;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.json.JSONObject;

/** Real WebView + real DocumentsUI tests without shipping test libraries in the app. */
public final class ViewerInstrumentation extends Instrumentation {
    private volatile ViewerActivity current;
    private Bundle arguments;
    private final List<String> failures = new ArrayList<>();
    private int passed;
    private String originalUrl;

    @Override public void onCreate(Bundle args) { arguments = args; start(); }
    @Override public void onStart() {
        Application application = (Application) getTargetContext().getApplicationContext();
        application.registerActivityLifecycleCallbacks(new Application.ActivityLifecycleCallbacks() {
            public void onActivityCreated(Activity activity, Bundle state) { if (activity instanceof ViewerActivity) current = (ViewerActivity) activity; }
            public void onActivityStarted(Activity activity) { }
            public void onActivityResumed(Activity activity) { if (activity instanceof ViewerActivity) current = (ViewerActivity) activity; }
            public void onActivityPaused(Activity activity) { }
            public void onActivityStopped(Activity activity) { }
            public void onActivitySaveInstanceState(Activity activity, Bundle state) { }
            public void onActivityDestroyed(Activity activity) { }
        });
        try {
            current = (ViewerActivity) startActivitySync(new Intent(getTargetContext(), ViewerActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            String phase = arguments.getString("phase", "suite");
            if ("relaunch".equals(phase)) {
                test("process restart retains SAF grant and localStorage", () -> {
                    awaitReady();
                    equal("\"durable-value\"", js("localStorage.getItem('persist-proof')"));
                    check(current.getContentResolver().getPersistedUriPermissions().size() == 1, "Persisted read grant missing");
                });
                test("relaunch opens straight into full-screen reading", () -> {
                    assertReading();
                    equal("no", toolsShown());
                });
            } else if ("presentation".equals(phase)) {
                runPresentation();
            } else if ("presentation-deleted".equals(phase)) {
                try {
                    test("200% landscape file failure retains a reachable recovery action", () -> {
                        check(current.getResources().getConfiguration().fontScale >= 1.99f, "Presentation phase requires 200% font");
                        runOnMainSync(() -> current.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE));
                        await(() -> current.getResources().getConfiguration().orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE, "Landscape not applied");
                        await(() -> onUi(() -> current.findViewById(R.id.document_view).getVisibility() == View.GONE ? "yes" : "no").equals("yes"), "Failure notice absent");
                        revealFileAction();
                        assertActionVisible(R.id.choose_document);
                        capture("failure-font200-landscape.png");
                    });
                } finally {
                    runOnMainSync(() -> current.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT));
                }
            } else if ("deleted".equals(phase)) {
                test("deleted provider document shows recovery UI", () -> {
                    await(() -> onUi(() -> current.findViewById(R.id.document_view).getVisibility() == View.GONE ? "gone" : "visible").equals("gone"), "Deleted document still rendered");
                    equal(current.getString(R.string.file_unavailable), onUi(() -> ((TextView) current.findViewById(R.id.document_status)).getText().toString()));
                });
            } else if (phase.startsWith("maps-")) {
                test(phase + " launches external app and returns to same document", () -> {
                    awaitReady();
                    String visits = js("localStorage.getItem('visits')");
                    String url = js("location.href");
                    clickHtml("maps");
                    await(() -> {
                        AccessibilityNodeInfo root = getUiAutomation().getRootInActiveWindow();
                        if (root == null || root.getPackageName() == null) return false;
                        String visiblePackage = root.getPackageName().toString();
                        return phase.equals("maps-installed") ? visiblePackage.equals("com.google.android.apps.maps")
                                : visiblePackage.equals("com.android.chrome") || visiblePackage.contains("resolver");
                    }, "Expected external app did not appear");
                    getTargetContext().startActivity(new Intent(getTargetContext(), ViewerActivity.class)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT));
                    await(() -> {
                        AccessibilityNodeInfo root = getUiAutomation().getRootInActiveWindow();
                        return root != null && "com.triphtml.viewer".contentEquals(root.getPackageName());
                    }, "Viewer did not resume");
                    equal(visits, js("localStorage.getItem('visits')"));
                    equal(url, js("location.href"));
                });
                if (phase.equals("maps-absent")) test("no geo handler returns failure without crash", () -> {
                    equal("false", onUi(() -> Boolean.toString(new ExternalLinks(current).open("geo:0,0?q=Tokyo"))));
                });
            } else {
                runSuite();
            }
        } catch (Throwable error) { failures.add("Setup: " + error); }
        Bundle result = new Bundle();
        result.putString("stream", "\nViewer tests: " + passed + " passed. " + failures.size() + " failed.\n" + String.join("\n", failures) + "\n");
        finish(failures.isEmpty() ? Activity.RESULT_OK : Activity.RESULT_CANCELED, result);
    }

    /** Runs only on the isolated AVD, with font_scale=2 supplied by the verification caller. */
    private void runPresentation() throws Exception {
        runOnMainSync(() -> current.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT));
        await(() -> current.getResources().getConfiguration().orientation
                == android.content.res.Configuration.ORIENTATION_PORTRAIT, "Presentation portrait setup did not settle");
        awaitReady();
        android.content.SharedPreferences saved = current.getSharedPreferences("document", 0);
        String originalUri = saved.getString("uri", null);
        String originalTitle = saved.getString("title", null);
        check(current.getResources().getConfiguration().fontScale >= 1.99f, "Presentation phase requires 200% font");
        String longTitle = "아주 긴 여행 일정 파일 이름 — 히로시마와 기타큐슈를 돌아보는 여행 계획";
        try {
            test("long title at 200% keeps every document tool reachable", () -> {
                saved.edit().putString("title", longTitle).commit();
                recreateViewer();
                awaitReady();
                sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK);
                await(() -> toolsShown().equals("yes"), "Back did not open tools");
                await(() -> onUi(() -> current.findViewById(R.id.document_title).getWidth() > 0 ? "yes" : "no").equals("yes"), "No title layout");
                equal(longTitle, onUi(() -> ((TextView) current.findViewById(R.id.document_title)).getText().toString()));
                equal("yes", onUi(() -> {
                    android.text.Layout layout = ((TextView) current.findViewById(R.id.document_title)).getLayout();
                    return layout != null && layout.getLineCount() <= 2 ? "yes" : "no";
                }));
                capture("tools-long-title-font200-portrait.png");
                for (int id : new int[]{R.id.refresh, R.id.open_file, R.id.hide_controls}) {
                    reveal(id);
                    assertActionVisible(id);
                }
                runOnMainSync(() -> current.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE));
                await(() -> current.getResources().getConfiguration().orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE, "Landscape not applied");
                await(() -> toolsShown().equals("yes"), "Rotation closed the tools");
                for (int id : new int[]{R.id.refresh, R.id.open_file, R.id.hide_controls}) {
                    reveal(id);
                    assertActionVisible(id);
                }
                equal("yes", onUi(() -> current.findViewById(R.id.hide_controls).getWidth()
                        <= Math.round(560 * current.getResources().getDisplayMetrics().density) ? "yes" : "no"));
                capture("tools-font200-landscape.png");
                runOnMainSync(() -> current.findViewById(R.id.hide_controls).performClick());
                await(() -> toolsShown().equals("no"), "Tools did not close in landscape");
                runOnMainSync(() -> current.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT));
                SystemClock.sleep(700);
            });
            test("200% landscape welcome retains a reachable file action", () -> {
                saved.edit().remove("uri").remove("title").commit();
                recreateViewer();
                runOnMainSync(() -> current.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE));
                await(() -> current.getResources().getConfiguration().orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE, "Landscape not applied");
                await(() -> onUi(() -> current.findViewById(R.id.choose_document).getWidth() > 0 ? "yes" : "no").equals("yes"), "No welcome action layout");
                revealFileAction();
                assertActionVisible(R.id.choose_document);
                capture("welcome-font200-landscape.png");
            });
            test("adaptive color and monochrome icons render at launcher size", () -> {
                android.graphics.drawable.Drawable icon = getTargetContext().getDrawable(R.mipmap.ic_launcher);
                check(icon instanceof android.graphics.drawable.AdaptiveIconDrawable, "Icon is not adaptive");
                android.graphics.drawable.AdaptiveIconDrawable adaptive = (android.graphics.drawable.AdaptiveIconDrawable) icon;
                android.graphics.drawable.Drawable monochrome = adaptive.getMonochrome();
                check(monochrome != null, "Monochrome layer missing");
                saveIcon(adaptive, "icon-color-96.png");
                monochrome = monochrome.getConstantState().newDrawable().mutate();
                monochrome.setTint(android.graphics.Color.rgb(33, 92, 82));
                saveIcon(new android.graphics.drawable.AdaptiveIconDrawable(
                        new android.graphics.drawable.ColorDrawable(android.graphics.Color.rgb(221, 232, 220)),
                        monochrome), "icon-themed-96.png");
            });
        } finally {
            android.content.SharedPreferences.Editor restore = saved.edit().putString("uri", originalUri);
            if (originalTitle == null) restore.remove("title"); else restore.putString("title", originalTitle);
            restore.commit();
            runOnMainSync(() -> current.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT));
            recreateViewer();
            awaitReady();
        }
    }

    private void recreateViewer() throws Exception {
        ViewerActivity previous = current;
        runOnMainSync(previous::recreate);
        await(() -> current != previous, "Activity did not recreate");
        waitForIdleSync();
    }

    private void revealFileAction() throws Exception { reveal(R.id.choose_document); }

    private void reveal(int id) throws Exception {
        await(() -> onUi(() -> current.findViewById(id).getHeight() > 0 ? "yes" : "no").equals("yes"), "No action layout");
        runOnMainSync(() -> {
            View action = current.findViewById(id);
            action.requestRectangleOnScreen(new Rect(0, 0, action.getWidth(), action.getHeight()), true);
        });
        waitForIdleSync();
    }

    private String toolsShown() {
        return onUi(() -> current.findViewById(R.id.document_tools).isShown() ? "yes" : "no");
    }

    private String barsState() {
        return onUi(() -> {
            WindowInsets insets = current.getWindow().getDecorView().getRootWindowInsets();
            return insets != null && insets.isVisible(WindowInsets.Type.statusBars()) ? "shown" : "hidden";
        });
    }

    /** Reading means the page is visible, no file screen covers it and the system bars are hidden. */
    private void assertReading() throws Exception {
        equal("visible", onUi(() -> web().getVisibility() == View.VISIBLE ? "visible" : "gone"));
        equal("gone", onUi(() -> current.findViewById(R.id.file_choice).getVisibility() == View.GONE ? "gone" : "shown"));
        await(() -> barsState().equals("hidden"), "System bars stayed visible while reading");
    }

    private Rect safeArea() {
        android.graphics.Insets safe = current.getWindow().getDecorView().getRootWindowInsets()
                .getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
        return new Rect(safe.left, safe.top, safe.right, safe.bottom);
    }

    /** Size and scroll of the page. Opening or closing tools must not change any of them. */
    private String pageGeometry() {
        return onUi(() -> web().getWidth() + "x" + web().getHeight() + "@" + web().getScrollY());
    }

    private String foregroundPackage() {
        AccessibilityNodeInfo root = getUiAutomation().getRootInActiveWindow();
        return root == null || root.getPackageName() == null ? "" : root.getPackageName().toString();
    }

    /** Visible, at least 48dp in both directions and named for TalkBack. */
    private void assertLabelledAction(int id) {
        equal("ok", onUi(() -> {
            View action = current.findViewById(id);
            if (!action.isShown()) return "hidden";
            CharSequence label = action instanceof TextView ? ((TextView) action).getText() : action.getContentDescription();
            if (label == null || label.length() == 0) return "unlabelled";
            int minimum = Math.round(48 * current.getResources().getDisplayMetrics().density);
            return action.getWidth() >= minimum && action.getHeight() >= minimum ? "ok" : "small";
        }));
    }

    private void assertActionVisible(int id) {
        equal("yes", onUi(() -> {
            View action = current.findViewById(id);
            android.graphics.Rect visible = new android.graphics.Rect();
            int minimum = Math.round(48 * current.getResources().getDisplayMetrics().density);
            return action.isShown() && action.getGlobalVisibleRect(visible)
                    && visible.width() >= minimum && visible.height() >= minimum ? "yes" : "no";
        }));
    }

    private void capture(String name) throws Exception {
        // Layout is ready before Android's rotation transition ends. Capture the settled frame.
        SystemClock.sleep(1000);
        android.graphics.Bitmap bitmap = getUiAutomation().takeScreenshot();
        check(bitmap != null, "Screenshot unavailable");
        saveBitmap(bitmap, name);
        bitmap.recycle();
    }

    private void saveIcon(android.graphics.drawable.Drawable icon, String name) throws Exception {
        android.graphics.Bitmap bitmap = android.graphics.Bitmap.createBitmap(96, 96, android.graphics.Bitmap.Config.ARGB_8888);
        android.graphics.Canvas canvas = new android.graphics.Canvas(bitmap);
        icon.setBounds(0, 0, 96, 96);
        icon.draw(canvas);
        int painted = 0;
        for (int y = 0; y < 96; y++) for (int x = 0; x < 96; x++) {
            if (android.graphics.Color.alpha(bitmap.getPixel(x, y)) > 0) painted++;
        }
        check(painted > 1000 && painted < 96 * 96, "Adaptive mask is empty or absent");
        saveBitmap(bitmap, name);
        bitmap.recycle();
    }

    private void saveBitmap(android.graphics.Bitmap bitmap, String name) throws Exception {
        // Instrumentation runs with the target UID. Its own APK's private directory is inaccessible.
        try (java.io.FileOutputStream out = new java.io.FileOutputStream(new java.io.File(getTargetContext().getFilesDir(), name))) {
            check(bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, out), "PNG write failed");
        }
    }

    private void runSuite() throws Exception {
        test("real SAF selection + inline JavaScript/CSS", () -> {
            selectFixture("trip.html");
            awaitReady();
            equal("\"rgb(0, 128, 0)\"", js("getComputedStyle(document.getElementById('css')).color"));
            equal("true", js("window.fixtureReady"));
            originalUrl = js("location.href");
            check(current.getContentResolver().getPersistedUriPermissions().size() == 1, "No persisted URI");
            equal("\"durable-value\"", js("localStorage.setItem('persist-proof','durable-value');localStorage.getItem('persist-proof')"));
        });
        test("document opens full screen with no host chrome", () -> {
            assertReading();
            equal("yes", onUi(() -> {
                View page = web();
                View content = (View) page.getParent();
                // Only the safe area (cutout) may separate the page from the window edges.
                Rect safe = safeArea();
                return page.getHeight() == content.getHeight() - safe.top - safe.bottom
                        && page.getWidth() == content.getWidth() - safe.left - safe.right ? "yes" : "no";
            }));
        });
        test("Back opens labelled 48dp document tools without resizing the page", () -> {
            js("window.scrollTo(0, 600);true");
            await(() -> onUi(() -> web().getScrollY() > 0 ? "yes" : "no").equals("yes"), "Page did not scroll");
            String before = pageGeometry();
            sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK);
            await(() -> toolsShown().equals("yes"), "Back did not open the document tools");
            equal(before, pageGeometry());
            equal("trip", onUi(() -> ((TextView) current.findViewById(R.id.document_title)).getText().toString()));
            equal("gone", onUi(() -> current.findViewById(R.id.previous_position).getVisibility() == View.GONE ? "gone" : "shown"));
            for (int id : new int[]{R.id.refresh, R.id.open_file, R.id.hide_controls}) assertLabelledAction(id);
            equal(Integer.toString(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS),
                    onUi(() -> Integer.toString(web().getImportantForAccessibility())));
            equal("hidden", barsState());
        });
        test("Continue reading returns to the same scroll position", () -> {
            String before = pageGeometry();
            runOnMainSync(() -> current.findViewById(R.id.hide_controls).performClick());
            await(() -> toolsShown().equals("no"), "Tools did not close");
            equal(before, pageGeometry());
            equal(Integer.toString(View.IMPORTANT_FOR_ACCESSIBILITY_AUTO),
                    onUi(() -> Integer.toString(web().getImportantForAccessibility())));
            assertReading();
        });
        test("accessibility action opens tools and scrim tap closes them", () -> {
            equal("true", onUi(() -> Boolean.toString(web().performAccessibilityAction(R.id.show_controls, null))));
            await(() -> toolsShown().equals("yes"), "Accessibility action did not open tools");
            String before = pageGeometry();
            int[] tap = new int[2];
            runOnMainSync(() -> {
                int[] location = new int[2];
                web().getLocationOnScreen(location);
                tap[0] = location[0] + web().getWidth() / 2;
                tap[1] = location[1] + Math.round(24 * current.getResources().getDisplayMetrics().density);
            });
            long now = SystemClock.uptimeMillis();
            MotionEvent down = MotionEvent.obtain(now, now, MotionEvent.ACTION_DOWN, tap[0], tap[1], 0);
            MotionEvent up = MotionEvent.obtain(now, now + 60, MotionEvent.ACTION_UP, tap[0], tap[1], 0);
            down.setSource(InputDevice.SOURCE_TOUCHSCREEN); up.setSource(InputDevice.SOURCE_TOUCHSCREEN);
            sendPointerSync(down); sendPointerSync(up);
            down.recycle(); up.recycle();
            await(() -> toolsShown().equals("no"), "Scrim tap did not close tools");
            equal(before, pageGeometry());
        });
        test("cancelled picker keeps the document and reading position", () -> {
            String url = js("location.href");
            String before = pageGeometry();
            sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK);
            await(() -> toolsShown().equals("yes"), "Back did not open tools");
            runOnMainSync(() -> current.findViewById(R.id.open_file).performClick());
            await(() -> !"com.triphtml.viewer".equals(foregroundPackage()), "Picker did not open");
            sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK);
            await(() -> "com.triphtml.viewer".equals(foregroundPackage()), "Viewer did not return from the picker");
            equal(url, js("location.href"));
            equal(before, pageGeometry());
            equal("\"durable-value\"", js("localStorage.getItem('persist-proof')"));
            assertReading();
        });
        test("refresh keeps document access and localStorage", () -> {
            int visits = Integer.parseInt(js("Number(localStorage.getItem('visits'))"));
            runOnMainSync(() -> current.findViewById(R.id.refresh).performClick());
            await(() -> Integer.parseInt(js("Number(localStorage.getItem('visits'))")) > visits, "Refresh did not reread HTML");
            equal("\"durable-value\"", js("localStorage.getItem('persist-proof')"));
        });
        test("Activity recreation retains storage and file access", () -> {
            ViewerActivity previous = current;
            runOnMainSync(previous::recreate);
            await(() -> current != previous, "Activity was not recreated");
            awaitReady();
            equal("\"durable-value\"", js("localStorage.getItem('persist-proof')"));
        });
        test("rotation retains document", () -> {
            runOnMainSync(() -> current.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT));
            await(() -> current.getResources().getConfiguration().orientation
                    == android.content.res.Configuration.ORIENTATION_PORTRAIT, "Portrait setup did not settle");
            awaitReady();
            ViewerActivity previous = current;
            runOnMainSync(() -> current.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE));
            await(() -> current != previous && current.getResources().getConfiguration().orientation
                    == android.content.res.Configuration.ORIENTATION_LANDSCAPE, "Rotation did not recreate Activity");
            awaitReady();
            equal("\"durable-value\"", js("localStorage.getItem('persist-proof')"));
            runOnMainSync(() -> current.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT));
            await(() -> current.getResources().getConfiguration().orientation
                    == android.content.res.Configuration.ORIENTATION_PORTRAIT, "Portrait restoration did not settle");
            awaitReady();
        });
        test("internal anchor stays in document", () -> {
            clickHtml("anchor");
            await(() -> js("location.hash").equals("\"#day2\""), "Anchor failed");
            check(js("location.origin").contains("html-viewer.invalid"), "Origin changed");
        });
        test("previous position appears only with document history and returns there", () -> {
            sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK);
            await(() -> toolsShown().equals("yes"), "Back did not open tools");
            assertLabelledAction(R.id.previous_position);
            runOnMainSync(() -> current.findViewById(R.id.previous_position).performClick());
            await(() -> js("location.hash").equals("\"\""), "Previous position did not leave the anchor");
            equal("no", toolsShown());
            assertReading();
        });
        test("refresh after anchor rereads file", () -> {
            clickHtml("anchor");
            await(() -> js("location.hash").equals("\"#day2\""), "Anchor failed before refresh");
            int visits = Integer.parseInt(js("Number(localStorage.getItem('visits'))"));
            runOnMainSync(() -> current.findViewById(R.id.refresh).performClick());
            await(() -> Integer.parseInt(js("Number(localStorage.getItem('visits'))")) > visits, "Anchor refresh did not reread file");
            equal("\"#day2\"", js("location.hash"));
        });
        List<Intent> launched = new ArrayList<>();
        ActivityMonitor monitor = new ActivityMonitor() {
            @Override public ActivityResult onStartActivity(Intent intent) {
                if (Intent.ACTION_VIEW.equals(intent.getAction())) {
                    synchronized (launched) { launched.add(new Intent(intent)); }
                    return new ActivityResult(Activity.RESULT_CANCELED, null);
                }
                return null;
            }
        };
        addMonitor(monitor);
        for (String id : new String[]{"external", "blank", "popup", "maps"}) {
            test("user link dispatch: " + id, () -> {
                synchronized (launched) { launched.clear(); }
                clickHtml(id);
                await(() -> { synchronized (launched) { return !launched.isEmpty(); } }, "External intent not sent");
                Intent intent;
                synchronized (launched) { intent = launched.get(0); check(launched.size() == 1, "Duplicate external intents"); }
                check(intent.hasCategory(Intent.CATEGORY_BROWSABLE), "Missing browsable category");
                if (id.equals("maps")) equal("com.google.android.apps.maps", intent.getPackage());
                else check(intent.getDataString().startsWith("https://example.com/"), "Wrong external URL");
                check(js("location.origin").contains("html-viewer.invalid"), "Document replaced by external URL");
            });
        }
        test("background navigation and popup cannot start apps", () -> {
            synchronized (launched) { launched.clear(); }
            js("setTimeout(()=>{window.open('https://example.com/automatic');location.href='https://example.com/automatic'},50);true");
            SystemClock.sleep(700);
            synchronized (launched) { check(launched.isEmpty(), "Background script launched an app"); }
            check(js("location.origin").contains("html-viewer.invalid"), "Automatic navigation replaced document");
        });
        removeMonitor(monitor);
        test("separate relative resources fail closed with a dismissible overlay notice", () -> {
            String before = pageGeometry();
            equal("404", jsAsync("fetch('missing.js').then(r=>r.status)"));
            await(() -> onUi(() -> current.findViewById(R.id.reading_notice).isShown() ? "yes" : "no").equals("yes"), "Notice absent");
            equal(current.getString(R.string.relative_unsupported),
                    onUi(() -> ((TextView) current.findViewById(R.id.reading_notice)).getText().toString()));
            equal(before, pageGeometry());
            assertLabelledAction(R.id.dismiss_notice);
            runOnMainSync(() -> current.findViewById(R.id.dismiss_notice).performClick());
            equal("no", onUi(() -> current.findViewById(R.id.reading_notice).isShown() ? "yes" : "no"));
            equal(before, pageGeometry());
        });
        test("different document isolates localStorage and original reopens", () -> {
            selectFixture("other.html"); awaitReady();
            equal("null", js("localStorage.getItem('persist-proof')"));
            selectFixture("trip.html"); awaitReady();
            equal("\"durable-value\"", js("localStorage.getItem('persist-proof')"));
            equal(originalUrl, js("location.href"));
        });
        test("revoked permission shows recovery UI", () -> {
            String selected = current.getSharedPreferences("document", 0).getString("uri", null);
            current.getContentResolver().releasePersistableUriPermission(Uri.parse(selected), Intent.FLAG_GRANT_READ_URI_PERMISSION);
            runOnMainSync(() -> current.findViewById(R.id.refresh).performClick());
            await(() -> onUi(() -> current.findViewById(R.id.document_view).getVisibility() == View.GONE ? "gone" : "visible").equals("gone"), "Revoked document still rendered");
            equal(current.getString(R.string.file_unavailable),
                    onUi(() -> ((TextView) current.findViewById(R.id.document_status)).getText().toString()));
            assertLabelledAction(R.id.choose_document);
            assertLabelledAction(R.id.retry_document);
            equal("no", toolsShown());
            equal("shown", barsState());
            selectFixture("trip.html"); awaitReady();
            equal("\"durable-value\"", js("localStorage.getItem('persist-proof')"));
            assertReading();
        });
        test("second Back leaves the app and relaunch resumes reading", () -> {
            String url = js("location.href");
            sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK);
            await(() -> toolsShown().equals("yes"), "First Back did not open tools");
            sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK);
            await(() -> !"com.triphtml.viewer".equals(foregroundPackage()), "Second Back did not leave the app");
            getTargetContext().startActivity(new Intent(getTargetContext(), ViewerActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT));
            await(() -> "com.triphtml.viewer".equals(foregroundPackage()), "Viewer did not relaunch");
            awaitReady();
            equal(url, js("location.href"));
            equal("no", toolsShown());
            assertReading();
        });
        // Give Chromium's storage writer time to flush before the separate process/reboot test.
        js("localStorage.setItem('persist-proof','durable-value');true");
        SystemClock.sleep(2000);
    }

    private void selectFixture(String file) throws Exception {
        runOnMainSync(() -> current.findViewById(R.id.open_file).performClick());
        if (clickNode(file, 800)) { SystemClock.sleep(500); return; }
        if (!clickNode("Viewer test files", 1200)) {
            if (!clickNode("Show roots", 1200)) clickNode("Open navigation drawer", 1200);
            check(clickNode("Viewer test files", 4000), "Fixture root not found in DocumentsUI");
        }
        check(clickNode(file, 4000), "Fixture file not found");
        SystemClock.sleep(500);
    }

    private boolean clickNode(String text, long timeout) {
        long deadline = SystemClock.uptimeMillis() + timeout;
        do {
            AccessibilityNodeInfo root = getUiAutomation().getRootInActiveWindow();
            AccessibilityNodeInfo node = findNode(root, text);
            if (node != null) {
                while (node != null && !node.isClickable()) node = node.getParent();
                if (node != null && node.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return true;
            }
            SystemClock.sleep(100);
        } while (SystemClock.uptimeMillis() < deadline);
        return false;
    }

    private AccessibilityNodeInfo findNode(AccessibilityNodeInfo node, String text) {
        if (node == null) return null;
        if (text.contentEquals(node.getText() == null ? "" : node.getText())
                || text.contentEquals(node.getContentDescription() == null ? "" : node.getContentDescription())) {
            AccessibilityNodeInfo clickable = node;
            while (clickable != null && !clickable.isClickable()) clickable = clickable.getParent();
            if (clickable != null && clickable.isVisibleToUser()) return clickable;
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo found = findNode(node.getChild(i), text);
            if (found != null) return found;
        }
        return null;
    }

    private void clickHtml(String id) throws Exception {
        js("document.getElementById('" + id + "').scrollIntoView({block:'center'});true");
        SystemClock.sleep(200);
        JSONObject rect = new JSONObject(js("(()=>{let r=document.getElementById('" + id + "').getBoundingClientRect();return {x:r.x+r.width/2,y:r.y+r.height/2,scale:devicePixelRatio}})()"));
        int[] location = new int[2];
        runOnMainSync(() -> web().getLocationOnScreen(location));
        float x = (float) (location[0] + rect.getDouble("x") * rect.getDouble("scale"));
        float y = (float) (location[1] + rect.getDouble("y") * rect.getDouble("scale"));
        long now = SystemClock.uptimeMillis();
        MotionEvent down = MotionEvent.obtain(now, now, MotionEvent.ACTION_DOWN, x, y, 0);
        MotionEvent up = MotionEvent.obtain(now, now + 80, MotionEvent.ACTION_UP, x, y, 0);
        down.setSource(InputDevice.SOURCE_TOUCHSCREEN); up.setSource(InputDevice.SOURCE_TOUCHSCREEN);
        sendPointerSync(down); sendPointerSync(up);
        down.recycle(); up.recycle();
    }

    private WebView web() { return current.findViewById(R.id.document_view); }
    private void awaitReady() throws Exception { await(() -> js("window.fixtureReady===true").equals("true"), "HTML did not become ready"); }
    private String js(String script) throws Exception {
        CountDownLatch done = new CountDownLatch(1);
        AtomicReference<String> result = new AtomicReference<>();
        runOnMainSync(() -> web().evaluateJavascript(script, value -> { result.set(value); done.countDown(); }));
        check(done.await(5, TimeUnit.SECONDS), "JavaScript callback timeout");
        return result.get();
    }
    private String jsAsync(String promise) throws Exception {
        js("window.asyncResult=null;(" + promise + ").then(v=>window.asyncResult=v);true");
        await(() -> !js("window.asyncResult").equals("null"), "Async JavaScript timeout");
        return js("window.asyncResult");
    }
    private String onUi(TextSupplier read) {
        AtomicReference<String> result = new AtomicReference<>();
        runOnMainSync(() -> result.set(read.get()));
        return result.get();
    }
    private void await(Condition condition, String message) throws Exception {
        long deadline = SystemClock.uptimeMillis() + 10000;
        do { if (condition.get()) return; SystemClock.sleep(100); } while (SystemClock.uptimeMillis() < deadline);
        throw new AssertionError(message);
    }
    private void test(String name, CheckedRunnable body) {
        Bundle progress = new Bundle();
        try { body.run(); passed++; progress.putString("stream", "PASS " + name + "\n"); }
        catch (Throwable error) { failures.add(name + ": " + error); progress.putString("stream", "FAIL " + name + ": " + error + "\n"); }
        sendStatus(0, progress);
    }
    private void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
    private void equal(String expected, String actual) { check(expected.equals(actual), "Expected " + expected + " but got " + actual); }
    interface CheckedRunnable { void run() throws Exception; }
    interface Condition { boolean get() throws Exception; }
    interface TextSupplier { String get(); }
}
