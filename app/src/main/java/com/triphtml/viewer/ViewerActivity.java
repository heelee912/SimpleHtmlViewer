package com.triphtml.viewer;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Rect;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Message;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.accessibility.AccessibilityNodeInfo;
import android.webkit.CookieManager;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.ProgressBar;
import android.widget.Toast;
import android.window.OnBackInvokedCallback;
import android.window.OnBackInvokedDispatcher;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Reading-first viewer. A rendered document fills the screen with the system bars hidden. Back opens
 * the document tools over the page, and Back again leaves the app. Without a document the file choice
 * screen takes over and Back behaves as on any other screen.
 */
public final class ViewerActivity extends Activity {
    private static final int OPEN_DOCUMENT = 10;
    /** Reads the first opaque page background so the cutout band blends into the document. */
    private static final String PAGE_BACKGROUND_SCRIPT = "(function(){var e=[document.body,document.documentElement];"
            + "for(var i=0;i<e.length;i++){if(!e[i])continue;var c=getComputedStyle(e[i]).backgroundColor;"
            + "if(c&&c!=='rgba(0, 0, 0, 0)'&&c!=='transparent')return c;}return '';})()";
    private final ExecutorService documentReads = Executors.newSingleThreadExecutor();
    private final List<WebView> popups = new ArrayList<>();
    private volatile DocumentSource document;
    private Optional<DocumentTitle> documentTitle = Optional.empty();
    private FrameLayout root;
    private WebView webView;
    private ProgressBar loading;
    private FileChoiceScreen fileChoice;
    private ReadingNotice notice;
    private DocumentToolsPanel tools;
    private ReadingBackGate readingBackGate;
    private SharedPreferences preferences;
    private ExternalLinks externalLinks;
    private int documentBackground = Color.WHITE; // WebView paints white until the page says otherwise.
    private boolean relativeNoticeShown;
    private int selectionGeneration;
    private int restoreScrollY;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        preferences = getSharedPreferences("document", MODE_PRIVATE);
        externalLinks = new ExternalLinks(this);
        buildScreen();
        if (Build.VERSION.SDK_INT >= 33) readingBackGate = new ReadingBackGate(this::openTools);
        String selected = preferences.getString("uri", null);
        if (selected == null) {
            showWelcome();
            return;
        }
        document = new DocumentSource(getContentResolver(), Uri.parse(selected));
        documentTitle = DocumentTitle.fromDisplayName(preferences.getString("title", null));
        String savedUrl = state == null ? preferences.getString("url", null) : state.getString("url");
        restoreScrollY = state == null ? preferences.getInt("scroll", 0) : state.getInt("scroll");
        loadDocument(savedUrl);
        if (state != null && state.getBoolean("tools")) openTools();
    }

    private void buildScreen() {
        root = new FrameLayout(this);
        root.setBackgroundColor(getColor(R.color.surface));

        webView = new WebView(this);
        webView.setId(R.id.document_view);
        configureWebView(webView);
        webView.setWebViewClient(new DocumentBrowser());
        webView.setWebChromeClient(new PopupLinks());
        webView.setAccessibilityDelegate(new View.AccessibilityDelegate() {
            @Override public void onInitializeAccessibilityNodeInfo(View host, AccessibilityNodeInfo info) {
                super.onInitializeAccessibilityNodeInfo(host, info);
                info.addAction(new AccessibilityNodeInfo.AccessibilityAction(R.id.show_controls,
                        getString(R.string.show_controls)));
            }
            @Override public boolean performAccessibilityAction(View host, int action, Bundle arguments) {
                if (action == R.id.show_controls) { openTools(); return true; }
                return super.performAccessibilityAction(host, action, arguments);
            }
        });
        root.addView(webView, new FrameLayout.LayoutParams(-1, -1));

        fileChoice = new FileChoiceScreen(this, this::chooseDocument, this::refreshDocument);
        root.addView(fileChoice.view, new FrameLayout.LayoutParams(-1, -1));

        loading = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        loading.setId(R.id.loading_progress);
        loading.setIndeterminate(true);
        loading.setIndeterminateTintList(ColorStateList.valueOf(getColor(R.color.accent)));
        loading.setVisibility(View.GONE);
        root.addView(loading, new FrameLayout.LayoutParams(-1, ScreenUnits.dp(this, 4), Gravity.TOP));

        notice = new ReadingNotice(this);
        root.addView(notice.view);

        tools = new DocumentToolsPanel(this, new ToolActions());
        root.addView(tools.view, new FrameLayout.LayoutParams(-1, -1));

        // Only this listener turns insets into spacing, so nothing is padded twice.
        root.setOnApplyWindowInsetsListener((view, insets) -> {
            applySafeArea(safeArea(insets));
            return insets;
        });
        setContentView(root);
    }

    private static Rect safeArea(WindowInsets insets) {
        if (Build.VERSION.SDK_INT >= 30) {
            // Hidden bars report zero here, so reading uses the whole screen except a display cutout.
            android.graphics.Insets safe = insets.getInsets(WindowInsets.Type.systemBars()
                    | WindowInsets.Type.displayCutout());
            return new Rect(safe.left, safe.top, safe.right, safe.bottom);
        }
        return new Rect(insets.getSystemWindowInsetLeft(), insets.getSystemWindowInsetTop(),
                insets.getSystemWindowInsetRight(), insets.getSystemWindowInsetBottom());
    }

    private void applySafeArea(Rect safe) {
        FrameLayout.LayoutParams page = (FrameLayout.LayoutParams) webView.getLayoutParams();
        page.setMargins(safe.left, safe.top, safe.right, safe.bottom);
        webView.setLayoutParams(page);
        fileChoice.view.setPadding(safe.left, safe.top, safe.right, safe.bottom);
        FrameLayout.LayoutParams progress = (FrameLayout.LayoutParams) loading.getLayoutParams();
        progress.setMargins(safe.left, safe.top, safe.right, 0);
        loading.setLayoutParams(progress);
        notice.applySafeArea(safe);
        tools.applySafeArea(safe);
    }

    @SuppressLint("SetJavaScriptEnabled") // Required to render user-selected interactive HTML. No native bridge exists.
    private void configureWebView(WebView view) {
        WebSettings settings = view.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(false);
        settings.setAllowFileAccessFromFileURLs(false);
        settings.setAllowUniversalAccessFromFileURLs(false);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        settings.setJavaScriptCanOpenWindowsAutomatically(false);
        settings.setSupportMultipleWindows(true);
        settings.setCacheMode(WebSettings.LOAD_NO_CACHE);
        settings.setBuiltInZoomControls(true);
        settings.setDisplayZoomControls(false);
        settings.setUseWideViewPort(true);
        settings.setLoadWithOverviewMode(true);
        CookieManager.getInstance().setAcceptThirdPartyCookies(view, false);
    }

    // ---- Screen states -------------------------------------------------------------------------

    private boolean isReading() { return webView.getVisibility() == View.VISIBLE; }

    private void showWelcome() {
        webView.setVisibility(View.GONE);
        notice.hide();
        fileChoice.showWelcome();
        updateWindow();
    }

    private void showReading() {
        fileChoice.hide();
        webView.setVisibility(View.VISIBLE);
        updateWindow();
    }

    /** No document is on screen. Recovery replaces the reader, and Back is no longer intercepted. */
    private void showProblem(FileChoiceScreen.Problem problem, boolean canRetry) {
        setLoading(false);
        closeTools(false);
        notice.hide();
        webView.setVisibility(View.GONE);
        fileChoice.showProblem(problem, document == null ? Optional.empty() : documentTitle, canRetry);
        updateWindow();
    }

    private void updateWindow() {
        boolean reading = isReading();
        root.setBackgroundColor(reading ? documentBackground : getColor(R.color.surface));
        applySystemBars(reading);
        updateBackHandling();
        root.requestApplyInsets();
    }

    private void applySystemBars(boolean reading) {
        if (Build.VERSION.SDK_INT >= 30) {
            WindowInsetsController controller = getWindow().getInsetsController();
            if (controller == null) return;
            controller.setSystemBarsBehavior(WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
            if (reading) controller.hide(WindowInsets.Type.systemBars());
            else controller.show(WindowInsets.Type.systemBars());
        } else {
            getWindow().getDecorView().setSystemUiVisibility(reading
                    ? View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                    : View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        }
    }

    /** The picker, permission dialogs and transient bars can bring the bars back. Reading hides them again. */
    @Override public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) applySystemBars(isReading());
    }

    private void setLoading(boolean active) {
        loading.setVisibility(active ? View.VISIBLE : View.GONE);
    }

    // ---- Back and document tools ---------------------------------------------------------------

    /** Back opens the tools only while reading with the tools closed. Everywhere else it leaves normally. */
    private boolean backOpensTools() { return isReading() && !tools.isShown(); }

    private void updateBackHandling() {
        if (readingBackGate != null) readingBackGate.intercept(this, backOpensTools());
    }

    @SuppressWarnings("deprecation")
    @Override public void onBackPressed() {
        // Android 12 and older. Android 13+ uses ReadingBackGate and the system default instead.
        if (backOpensTools()) openTools();
        else super.onBackPressed();
    }

    private void openTools() {
        if (!isReading() || tools.isShown()) return;
        tools.show(documentTitle, webView.canGoBack());
        webView.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
        updateBackHandling();
    }

    // The document was hidden from accessibility while the panel was open. Without this, TalkBack keeps
    // focus on a node that no longer exists and the reader loses their place.
    @SuppressLint("AccessibilityFocus")
    private void closeTools(boolean returnFocusToDocument) {
        if (!tools.isShown()) return;
        tools.hide();
        webView.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_AUTO);
        updateBackHandling();
        if (returnFocusToDocument) {
            webView.requestFocus();
            webView.post(() -> webView.performAccessibilityAction(AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS, null));
        }
    }

    private final class ToolActions implements DocumentToolsPanel.Actions {
        @Override public void resumeReading() { closeTools(true); }

        @Override public void previousPosition() {
            closeTools(true);
            if (webView.canGoBack()) webView.goBack();
        }

        @Override public void refresh() {
            closeTools(true);
            refreshDocument();
        }

        @Override public void openOtherFile() {
            closeTools(false);
            chooseDocument();
        }
    }

    /** Registers the reader's Back callback only while it should open the tools, so system Back stays native. */
    @TargetApi(33)
    @SuppressLint("UseRequiresApi") // @RequiresApi lives in androidx, and the app deliberately has no dependencies.
    private static final class ReadingBackGate {
        private final OnBackInvokedCallback openTools;
        private boolean registered;

        ReadingBackGate(Runnable onBack) { openTools = onBack::run; }

        void intercept(Activity activity, boolean intercept) {
            if (intercept == registered) return;
            OnBackInvokedDispatcher dispatcher = activity.getOnBackInvokedDispatcher();
            if (intercept) dispatcher.registerOnBackInvokedCallback(OnBackInvokedDispatcher.PRIORITY_DEFAULT, openTools);
            else dispatcher.unregisterOnBackInvokedCallback(openTools);
            registered = intercept;
        }
    }

    // ---- Choosing and loading documents --------------------------------------------------------

    private void chooseDocument() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT)
                .addCategory(Intent.CATEGORY_OPENABLE).setType("*/*")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        // Providers often label .html as text/plain or application/octet-stream. Do not hide those files.
        try { startActivityForResult(intent, OPEN_DOCUMENT); }
        catch (ActivityNotFoundException error) {
            if (isReading()) notice.show(R.string.picker_unavailable);
            else showProblem(FileChoiceScreen.Problem.PICKER_MISSING, false);
        }
    }

    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        // A cancelled picker returns here with nothing to do: the current document and scroll stay as they were.
        if (request != OPEN_DOCUMENT || result != RESULT_OK || data == null || data.getData() == null) return;
        DocumentSource candidate;
        try {
            candidate = new DocumentSource(getContentResolver(), data.getData());
            candidate.takeReadPermission(data.getFlags());
        } catch (IllegalArgumentException | SecurityException error) {
            if (isReading()) notice.show(R.string.permission_unavailable);
            else showProblem(FileChoiceScreen.Problem.PERMISSION_REFUSED, false);
            return;
        }
        int generation = ++selectionGeneration;
        boolean keepCurrentDocument = isReading();
        if (keepCurrentDocument) setLoading(true);
        else fileChoice.showLoading(Optional.empty());
        documentReads.execute(() -> {
            try {
                candidate.verifyReadable();
                Optional<DocumentTitle> title = candidate.title();
                runOnUiThread(() -> {
                    if (isDestroyed() || generation != selectionGeneration) return;
                    DocumentSource previous = document;
                    document = candidate;
                    documentTitle = title;
                    SharedPreferences.Editor saved = preferences.edit().putString("uri", candidate.uri.toString())
                            .remove("url").putInt("scroll", 0);
                    if (title.isPresent()) saved.putString("title", title.get().text());
                    else saved.remove("title");
                    saved.apply();
                    restoreScrollY = 0;
                    loadDocument(null);
                    if (previous != null && !previous.uri.equals(candidate.uri)) releaseReadPermission(previous);
                });
            } catch (IOException | SecurityException error) {
                runOnUiThread(() -> {
                    if (isDestroyed() || generation != selectionGeneration) return;
                    setLoading(false);
                    if (keepCurrentDocument && isReading()) notice.show(R.string.selection_failed);
                    else showProblem(FileChoiceScreen.Problem.UNREADABLE, false);
                });
            }
        });
    }

    private void releaseReadPermission(DocumentSource source) {
        try { getContentResolver().releasePersistableUriPermission(source.uri, Intent.FLAG_GRANT_READ_URI_PERMISSION); }
        catch (SecurityException ignored) { /* The provider may already have revoked this grant. */ }
    }

    private void loadDocument(String savedUrl) {
        if (document == null) return;
        relativeNoticeShown = false;
        notice.hide();
        showReading();
        webView.stopLoading();
        webView.loadUrl(savedUrl != null && document.address.isDocument(savedUrl)
                ? savedUrl : document.address.url());
    }

    private void refreshDocument() {
        if (document == null) { chooseDocument(); return; }
        restoreScrollY = webView.getScrollY();
        relativeNoticeShown = false;
        notice.hide();
        showReading();
        if (document.address.isDocument(webView.getUrl())) webView.reload();
        else loadDocument(null);
    }

    private void documentFailed(DocumentSource failedSource) {
        runOnUiThread(() -> {
            if (isDestroyed() || document != failedSource) return;
            showProblem(FileChoiceScreen.Problem.UNREADABLE, true);
        });
    }

    private void showRelativeNotice() {
        runOnUiThread(() -> {
            if (isDestroyed() || relativeNoticeShown || !isReading()) return;
            relativeNoticeShown = true;
            notice.show(R.string.relative_unsupported);
        });
    }

    private void matchCutoutBandToPage(WebView view) {
        view.evaluateJavascript(PAGE_BACKGROUND_SCRIPT, value -> {
            if (isDestroyed()) return;
            documentBackground = CssColor.opaqueArgb(value).orElse(Color.WHITE);
            if (isReading()) root.setBackgroundColor(documentBackground);
        });
    }

    // ---- Lifecycle -----------------------------------------------------------------------------

    @Override protected void onSaveInstanceState(Bundle state) {
        super.onSaveInstanceState(state);
        state.putString("url", webView.getUrl());
        state.putInt("scroll", webView.getScrollY());
        state.putBoolean("tools", tools.isShown());
    }

    @Override protected void onPause() {
        super.onPause();
        // "hidden" belonged to the old toolbar. Reading is now always the default, so the key is dropped.
        preferences.edit().putString("url", webView.getUrl()).putInt("scroll", webView.getScrollY())
                .remove("hidden").apply();
        webView.onPause();
        CookieManager.getInstance().flush();
    }

    @Override protected void onStop() {
        super.onStop();
        // Leaving with Back or Home returns to plain reading. Rotation keeps the open panel.
        if (!isChangingConfigurations()) closeTools(false);
    }

    @Override protected void onResume() { super.onResume(); if (webView != null) webView.onResume(); }

    @Override protected void onDestroy() {
        ++selectionGeneration;
        documentReads.shutdownNow();
        for (WebView popup : new ArrayList<>(popups)) disposePopup(popup);
        if (webView != null) {
            ((ViewGroup) webView.getParent()).removeView(webView);
            webView.destroy();
        }
        super.onDestroy();
    }

    private final class DocumentBrowser extends WebViewClient {
        @Override public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
            DocumentSource source = document;
            String url = request.getUrl().toString();
            if (source != null && source.address.isDocument(url) && request.getMethod().equals("GET")) {
                try { return source.read(); }
                catch (IOException | SecurityException error) {
                    documentFailed(source);
                    return DocumentSource.blocked(403, "Document permission or file unavailable");
                }
            }
            if (DocumentAddress.isVirtual(url)) {
                // Chromium may probe for an optional favicon even in a self-contained document.
                if (!"/favicon.ico".equals(request.getUrl().getPath())) showRelativeNotice();
                return DocumentSource.blocked(404, "Companion files are not authorized");
            }
            if (request.isForMainFrame() || !"https".equals(request.getUrl().getScheme())) {
                return DocumentSource.blocked(403, "Navigation blocked");
            }
            return null; // HTTPS subresources retain normal WebView TLS and origin checks.
        }

        @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
            DocumentSource source = document;
            String url = request.getUrl().toString();
            LinkPolicy.Destination destination = LinkPolicy.classify(source == null ? null : source.address,
                    url, request.hasGesture(), request.isForMainFrame());
            if (destination == LinkPolicy.Destination.DOCUMENT) return false;
            if (destination == LinkPolicy.Destination.EXTERNAL && !externalLinks.open(url)) {
                Toast.makeText(ViewerActivity.this, R.string.link_unavailable, Toast.LENGTH_SHORT).show();
            } else if (DocumentAddress.isVirtual(url)) { showRelativeNotice(); }
            return true;
        }

        /** True between onPageStarted and onPageFinished of a full document load. */
        private boolean documentLoading;

        @Override public void onPageStarted(WebView view, String url, Bitmap favicon) {
            if (document == null || !document.address.isDocument(url)) return;
            documentLoading = true;
            setLoading(true);
        }

        @Override public void onPageFinished(WebView view, String url) {
            setLoading(false);
            // WebView also reports anchor jumps here without onPageStarted. Clearing history then would
            // erase the in-document positions that "이전 위치로" returns to.
            boolean fullLoad = documentLoading;
            documentLoading = false;
            if (fullLoad && document != null && document.address.isDocument(url) && isReading()) {
                int scroll = restoreScrollY;
                restoreScrollY = 0;
                if (scroll > 0) view.post(() -> view.scrollTo(0, scroll));
                view.clearHistory();
                matchCutoutBandToPage(view);
            }
        }

        @Override public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
            // A provider failure already switched to recovery with a precise reason. Keep that message.
            if (!request.isForMainFrame()) return;
            setLoading(false);
            if (isReading()) showProblem(FileChoiceScreen.Problem.PAGE_FAILED, true);
        }
    }

    /** The temporary WebView captures one user-requested popup URL and never renders remote content. */
    private final class PopupLinks extends WebChromeClient {
        @Override public boolean onCreateWindow(WebView view, boolean dialog, boolean userGesture, Message result) {
            if (!userGesture || document == null) return false;
            WebView popup = new WebView(ViewerActivity.this);
            popup.getSettings().setJavaScriptEnabled(false);
            popup.getSettings().setAllowContentAccess(false);
            popup.getSettings().setAllowFileAccess(false);
            popup.getSettings().setBlockNetworkLoads(true);
            popups.add(popup);
            popup.setWebViewClient(new WebViewClient() {
                private boolean consumed;
                @Override public boolean shouldOverrideUrlLoading(WebView ignored, WebResourceRequest request) {
                    if (consumed || !request.isForMainFrame()) return true;
                    consumed = true;
                    String url = request.getUrl().toString();
                    LinkPolicy.Destination destination = LinkPolicy.classify(document.address, url, true, true);
                    if (destination == LinkPolicy.Destination.DOCUMENT) webView.loadUrl(url);
                    else if (destination == LinkPolicy.Destination.EXTERNAL && !externalLinks.open(url)) {
                        Toast.makeText(ViewerActivity.this, R.string.link_unavailable, Toast.LENGTH_SHORT).show();
                    }
                    popup.post(() -> disposePopup(popup));
                    return true;
                }
                @Override public WebResourceResponse shouldInterceptRequest(WebView ignored, WebResourceRequest request) {
                    return DocumentSource.blocked(403, "Popup rendering disabled");
                }
            });
            ((WebView.WebViewTransport) result.obj).setWebView(popup);
            result.sendToTarget();
            popup.postDelayed(() -> disposePopup(popup), 3000);
            return true;
        }
    }

    private void disposePopup(WebView popup) {
        if (popups.remove(popup)) popup.destroy();
    }
}
