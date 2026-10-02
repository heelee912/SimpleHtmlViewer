package com.triphtml.viewer.reader

import android.annotation.SuppressLint
import android.app.Activity
import android.graphics.Bitmap
import android.os.Message
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import com.triphtml.viewer.R
import com.triphtml.viewer.document.DocumentSource
import com.triphtml.viewer.document.ReadingPlace
import com.triphtml.viewer.domain.Destination
import com.triphtml.viewer.domain.DocumentAddress
import com.triphtml.viewer.domain.LinkPolicy
import com.triphtml.viewer.domain.PageColor
import com.triphtml.viewer.links.ExternalLinks
import java.io.IOException
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Renders the chosen HTML with JavaScript, CSS and DOM storage, served from the document's own virtual
 * origin so localStorage survives refresh and relaunch. Links the user taps leave for other apps; the page
 * itself can never navigate away, open windows on its own, or read anything except its one file.
 * All [Events] arrive on the main thread.
 */
class DocumentReader(activity: Activity, private val events: Events) {
    interface Events {
        fun onLoadingChanged(loading: Boolean)
        fun onDocumentUnreadable(source: DocumentSource)
        fun onPageFailed()
        fun onSeparateFileBlocked()
        fun onLinkUnavailable()
        fun onPageBackground(argb: Int?)
    }

    private val externalLinks = ExternalLinks(activity)
    private val popups = mutableListOf<WebView>()
    private var restoreScrollY = 0
    private var separateFileReported = false

    /** Read from WebView's network thread, so it must be visible across threads. */
    @Volatile var document: DocumentSource? = null
        private set

    val view: WebView = WebView(activity).apply {
        id = R.id.document_view
        configure(settings)
        CookieManager.getInstance().setAcceptThirdPartyCookies(this, false)
        webViewClient = DocumentClient()
        webChromeClient = PopupLinks()
    }

    val place: ReadingPlace get() = ReadingPlace(view.url, view.scrollY)
    val canReturnToPreviousPlace: Boolean get() = view.canGoBack()

    /** Android Back first belongs to a modal in the chosen document. No native JavaScript interface is exposed. */
    suspend fun hasOpenDialog(): Boolean = pageBoolean("Boolean(document.querySelector('dialog[open]'))")

    suspend fun dismissOpenDialog(): Boolean = pageBoolean(
        "(()=>{const dialogs=[...document.querySelectorAll('dialog[open]')];" +
            "const d=dialogs[dialogs.length-1];if(!d)return false;" +
            "if(d.getAttribute('closedby')==='none')return true;" +
            "if(typeof d.requestClose==='function')d.requestClose();" +
            "else if(d.dispatchEvent(new Event('cancel',{cancelable:true})))d.close();return true;})()",
    )

    private suspend fun pageBoolean(script: String): Boolean = withTimeoutOrNull(1500) {
        suspendCancellableCoroutine { continuation ->
            view.evaluateJavascript(script) { value ->
                if (continuation.isActive) continuation.resume(value == "true")
            }
        }
    } ?: false

    fun open(source: DocumentSource, place: ReadingPlace) {
        document = source
        restoreScrollY = place.scrollY
        separateFileReported = false
        view.stopLoading()
        view.loadUrl(place.url?.takeIf(source.address::isDocument) ?: source.address.url)
    }

    /** Rereads the original file. The page's own localStorage is untouched. */
    fun refresh() {
        val source = document ?: return
        restoreScrollY = view.scrollY
        separateFileReported = false
        if (source.address.isDocument(view.url)) view.reload() else view.loadUrl(source.address.url)
    }

    fun returnToPreviousPlace() {
        if (view.canGoBack()) view.goBack()
    }

    fun pause() {
        view.onPause()
        CookieManager.getInstance().flush()
    }

    fun resume() = view.onResume()

    fun destroy() {
        popups.toList().forEach(::dispose)
        (view.parent as? ViewGroup)?.removeView(view)
        view.destroy()
    }

    @SuppressLint("SetJavaScriptEnabled") // The chosen HTML needs it. No JavaScript bridge to the app exists.
    @Suppress("DEPRECATION") // The file-URL switches are deprecated but still worth pinning off explicitly.
    private fun configure(settings: WebSettings) = with(settings) {
        javaScriptEnabled = true
        domStorageEnabled = true
        allowFileAccess = false
        allowContentAccess = false
        allowFileAccessFromFileURLs = false
        allowUniversalAccessFromFileURLs = false
        mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
        javaScriptCanOpenWindowsAutomatically = false
        setSupportMultipleWindows(true)
        cacheMode = WebSettings.LOAD_NO_CACHE
        builtInZoomControls = true
        displayZoomControls = false
        useWideViewPort = true
        loadWithOverviewMode = true
    }

    private inner class DocumentClient : WebViewClient() {
        /** True between onPageStarted and onPageFinished of a full document load. */
        private var fullLoad = false

        override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
            val source = document
            val url = request.url.toString()
            if (source != null && source.address.isDocument(url) && request.method == "GET") {
                return try {
                    source.read()
                } catch (_: IOException) {
                    unreadable(source)
                } catch (_: SecurityException) {
                    unreadable(source)
                }
            }
            if (DocumentAddress.isVirtual(url)) {
                // Chromium may probe for a favicon even for a self-contained page. That is not the page's fault.
                if (request.url.path != "/favicon.ico") view.post(::reportSeparateFile)
                return DocumentSource.blocked(404, "Companion files are not authorized")
            }
            if (request.isForMainFrame || request.url.scheme != "https") return DocumentSource.blocked(403, "Navigation blocked")
            return null // HTTPS subresources keep WebView's normal TLS and origin checks.
        }

        private fun unreadable(source: DocumentSource): WebResourceResponse {
            view.post { if (document === source) events.onDocumentUnreadable(source) }
            return DocumentSource.blocked(403, "Document permission or file unavailable")
        }

        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
            val url = request.url.toString()
            return when (LinkPolicy.classify(document?.address, url, request.hasGesture(), request.isForMainFrame)) {
                Destination.DOCUMENT -> false
                Destination.EXTERNAL -> {
                    if (!externalLinks.open(url)) events.onLinkUnavailable()
                    true
                }
                Destination.BLOCKED -> {
                    if (DocumentAddress.isVirtual(url)) reportSeparateFile()
                    true
                }
            }
        }

        override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
            if (document?.address?.isDocument(url) != true) return
            fullLoad = true
            events.onLoadingChanged(true)
        }

        override fun onPageFinished(view: WebView, url: String) {
            events.onLoadingChanged(false)
            // WebView also reports #anchor jumps here, without onPageStarted. Clearing history then would erase
            // the in-document places that "이전 위치로" returns to.
            val finishedDocumentLoad = fullLoad && document?.address?.isDocument(url) == true
            fullLoad = false
            if (!finishedDocumentLoad) return
            val scroll = restoreScrollY
            restoreScrollY = 0
            if (scroll > 0) view.post { view.scrollTo(0, scroll) }
            view.clearHistory()
            view.evaluateJavascript(PAGE_BACKGROUND_SCRIPT) { events.onPageBackground(PageColor.opaqueArgb(it)) }
        }

        override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
            if (!request.isForMainFrame) return
            events.onLoadingChanged(false)
            events.onPageFailed()
        }
    }

    private fun reportSeparateFile() {
        if (separateFileReported) return
        separateFileReported = true
        events.onSeparateFileBlocked()
    }

    /** Captures the first address of a window the user asked for, then discards the window unrendered. */
    private inner class PopupLinks : WebChromeClient() {
        override fun onCreateWindow(view: WebView, isDialog: Boolean, isUserGesture: Boolean, resultMsg: Message): Boolean {
            val source = document ?: return false
            if (!isUserGesture) return false
            val popup = WebView(view.context).apply {
                settings.javaScriptEnabled = false
                settings.allowContentAccess = false
                settings.allowFileAccess = false
                settings.blockNetworkLoads = true
            }
            popups += popup
            popup.webViewClient = object : WebViewClient() {
                private var consumed = false

                override fun shouldOverrideUrlLoading(ignored: WebView, request: WebResourceRequest): Boolean {
                    if (consumed || !request.isForMainFrame) return true
                    consumed = true
                    val url = request.url.toString()
                    when (LinkPolicy.classify(source.address, url, userGesture = true, mainFrame = true)) {
                        Destination.DOCUMENT -> this@DocumentReader.view.loadUrl(url)
                        Destination.EXTERNAL -> if (!externalLinks.open(url)) events.onLinkUnavailable()
                        Destination.BLOCKED -> Unit
                    }
                    popup.post { dispose(popup) }
                    return true
                }

                override fun shouldInterceptRequest(ignored: WebView, request: WebResourceRequest) =
                    DocumentSource.blocked(403, "Popup rendering disabled")
            }
            (resultMsg.obj as WebView.WebViewTransport).webView = popup
            resultMsg.sendToTarget()
            popup.postDelayed({ dispose(popup) }, 3000)
            return true
        }
    }

    private fun dispose(popup: WebView) {
        if (popups.remove(popup)) popup.destroy()
    }

    private companion object {
        /** The first opaque page background, so the cutout band can match the page. Reads style only. */
        const val PAGE_BACKGROUND_SCRIPT = "(function(){var e=[document.body,document.documentElement];" +
            "for(var i=0;i<e.length;i++){if(!e[i])continue;var c=getComputedStyle(e[i]).backgroundColor;" +
            "if(c&&c!=='rgba(0, 0, 0, 0)'&&c!=='transparent')return c;}return '';})()"
    }
}
