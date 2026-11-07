package com.example.ui.components

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.os.Message
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.URLUtil
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebStorage
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.viewinterop.AndroidView
import com.example.data.model.BrowserTab
import com.example.data.repository.BrowserPreferences
import com.example.engine.AdBlockerEngine
import com.example.engine.ShieldBlockType
import com.example.ui.WebViewCommand

private const val DESKTOP_USER_AGENT =
    "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"

private class AegisJsShieldBridge(
    private val onVideoAdSkipped: (String) -> Unit,
    private val onDomAdBlocked: (String) -> Unit
) {
    @JavascriptInterface
    fun onVideoAdIntercepted(reason: String) {
        onVideoAdSkipped(reason)
    }

    @JavascriptInterface
    fun onDomAdIntercepted(reason: String) {
        onDomAdBlocked(reason)
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun AegisWebViewContainer(
    activeTab: BrowserTab,
    preferences: BrowserPreferences,
    webViewCommand: WebViewCommand?,
    onConsumeCommand: () -> Unit,
    onPageStateChanged: (
        tabId: String,
        url: String?,
        title: String?,
        isLoading: Boolean?,
        progress: Int?,
        canGoBack: Boolean?,
        canGoForward: Boolean?
    ) -> Unit,
    onShieldBlocked: (tabId: String, blockType: ShieldBlockType, blockedUrl: String) -> Unit,
    onDownloadRequested: (url: String, suggestedFileName: String) -> Unit,
    modifier: Modifier = Modifier
) {
    val currentTab by rememberUpdatedState(activeTab)
    val currentPrefs by rememberUpdatedState(preferences)
    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    var defaultMobileUserAgent by remember { mutableStateOf<String?>(null) }

    // Handle imperative commands from ViewModel
    LaunchedEffect(webViewCommand, webViewRef) {
        val wv = webViewRef ?: return@LaunchedEffect
        val cmd = webViewCommand ?: return@LaunchedEffect
        when (cmd) {
            WebViewCommand.GoBack -> if (wv.canGoBack()) wv.goBack()
            WebViewCommand.GoForward -> if (wv.canGoForward()) wv.goForward()
            WebViewCommand.Reload -> wv.reload()
            WebViewCommand.ClearBrowsingData -> {
                wv.clearCache(true)
                wv.clearHistory()
                wv.clearFormData()
                CookieManager.getInstance().removeAllCookies(null)
                WebStorage.getInstance().deleteAllData()
            }
            WebViewCommand.TrimMemory -> {
                wv.clearCache(false)
                wv.freeMemory()
            }
            is WebViewCommand.FindInPage -> {
                wv.findAllAsync(cmd.query)
            }
            WebViewCommand.FindNext -> {
                wv.findNext(true)
            }
            WebViewCommand.ClearFind -> {
                wv.clearMatches()
            }
        }
        onConsumeCommand()
    }

    // Apply Desktop vs Mobile User-Agent whenever activeTab.isDesktopMode changes
    LaunchedEffect(activeTab.id, activeTab.isDesktopMode, webViewRef) {
        val wv = webViewRef ?: return@LaunchedEffect
        val mobileUa = defaultMobileUserAgent ?: wv.settings.userAgentString
        val desiredUa = if (activeTab.isDesktopMode) DESKTOP_USER_AGENT else mobileUa
        if (wv.settings.userAgentString != desiredUa) {
            wv.settings.userAgentString = desiredUa
            wv.settings.useWideViewPort = true
            wv.settings.loadWithOverviewMode = true
            if (activeTab.url.isNotBlank() && !activeTab.isHome && !activeTab.isAiSearchOverview) {
                wv.reload()
            }
        }
    }

    // Load target URL when activeTab.url or activeTab.id changes
    LaunchedEffect(activeTab.id, activeTab.url, webViewRef) {
        val wv = webViewRef ?: return@LaunchedEffect
        val targetUrl = activeTab.url
        if (targetUrl.isBlank() || activeTab.isHome || activeTab.isAiSearchOverview) return@LaunchedEffect

        if (targetUrl == "aegis://shield-test" || targetUrl == "vista://shield-test") {
            val html = AdBlockerEngine.buildShieldTestPageHtml(
                adsBlocked = currentPrefs.totalAdsBlocked,
                videoAdsBlocked = currentPrefs.totalVideoAdsBlocked,
                trackersBlocked = currentPrefs.totalTrackersBlocked
            )
            wv.loadDataWithBaseURL(
                "https://shield.vista.local",
                html,
                "text/html",
                "UTF-8",
                null
            )
        } else if (wv.url != targetUrl) {
            wv.loadUrl(targetUrl)
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            webViewRef?.stopLoading()
        }
    }

    AndroidView(
        modifier = modifier
            .fillMaxSize()
            .testTag("aegis_webview"),
        factory = { context ->
            WebView(context).apply {
                if (defaultMobileUserAgent == null) {
                    defaultMobileUserAgent = settings.userAgentString
                }

                settings.apply {
                    javaScriptEnabled = true
                    domStorageEnabled = true
                    databaseEnabled = true
                    loadsImagesAutomatically = true
                    useWideViewPort = true
                    loadWithOverviewMode = true
                    builtInZoomControls = true
                    displayZoomControls = false
                    setSupportZoom(true)
                    mediaPlaybackRequiresUserGesture = false
                    javaScriptCanOpenWindowsAutomatically = false
                    setSupportMultipleWindows(true)
                    cacheMode = WebSettings.LOAD_DEFAULT
                    mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
                }

                CookieManager.getInstance().setAcceptCookie(true)
                CookieManager.getInstance().setAcceptThirdPartyCookies(
                    this,
                    !currentPrefs.blockThirdPartyCookies
                )

                addJavascriptInterface(
                    AegisJsShieldBridge(
                        onVideoAdSkipped = { reason ->
                            post {
                                onShieldBlocked(currentTab.id, ShieldBlockType.VIDEO_AD, "video-ad://$reason")
                            }
                        },
                        onDomAdBlocked = { reason ->
                            post {
                                onShieldBlocked(currentTab.id, ShieldBlockType.DISPLAY_AD, "dom-cosmetic://$reason")
                            }
                        }
                    ),
                    "AegisShieldBridge"
                )

                setDownloadListener { url, _, contentDisposition, mimeType, _ ->
                    val guessedFileName = URLUtil.guessFileName(url, contentDisposition, mimeType)
                    onDownloadRequested(url, guessedFileName)
                }

                webChromeClient = object : WebChromeClient() {
                    override fun onCreateWindow(
                        view: WebView?,
                        isDialog: Boolean,
                        isUserGesture: Boolean,
                        resultMsg: Message?
                    ): Boolean {
                        // Block automatic pop-under and ad popup windows
                        if (currentPrefs.adBlockEnabled) {
                            view?.post {
                                onShieldBlocked(currentTab.id, ShieldBlockType.DISPLAY_AD, "popup://blocked-window")
                            }
                            return false
                        }
                        return super.onCreateWindow(view, isDialog, isUserGesture, resultMsg)
                    }

                    override fun onProgressChanged(view: WebView?, newProgress: Int) {
                        onPageStateChanged(
                            currentTab.id,
                            view?.url,
                            view?.title,
                            newProgress < 100,
                            newProgress,
                            view?.canGoBack() ?: false,
                            view?.canGoForward() ?: false
                        )
                    }

                    override fun onReceivedTitle(view: WebView?, title: String?) {
                        onPageStateChanged(
                            currentTab.id,
                            view?.url,
                            title,
                            null,
                            null,
                            view?.canGoBack() ?: false,
                            view?.canGoForward() ?: false
                        )
                    }
                }

                webViewClient = object : WebViewClient() {
                    private fun injectShield(view: WebView?) {
                        view?.evaluateJavascript(
                            AdBlockerEngine.buildInjectedShieldScript(
                                adBlockEnabled = currentPrefs.adBlockEnabled,
                                videoAdSkipperEnabled = currentPrefs.videoAdSkipperEnabled,
                                forceDarkWebPages = currentPrefs.forceDarkWebPages
                            ),
                            null
                        )
                    }

                    override fun shouldOverrideUrlLoading(
                        view: WebView?,
                        request: WebResourceRequest?
                    ): Boolean {
                        val reqUrl = request?.url?.toString() ?: return false
                        val prefs = currentPrefs
                        val blockType = AdBlockerEngine.classifyRequest(
                            requestUrl = reqUrl,
                            adBlockEnabled = prefs.adBlockEnabled,
                            videoAdSkipperEnabled = prefs.videoAdSkipperEnabled,
                            trackerBlockEnabled = prefs.trackerBlockEnabled
                        )
                        if (blockType != ShieldBlockType.NONE) {
                            view?.post {
                                onShieldBlocked(currentTab.id, blockType, reqUrl)
                            }
                            return true // Block ad/tracker redirect navigation
                        }
                        return false
                    }

                    override fun shouldInterceptRequest(
                        view: WebView?,
                        request: WebResourceRequest?
                    ): WebResourceResponse? {
                        val reqUrl = request?.url?.toString() ?: return null
                        val prefs = currentPrefs
                        val blockType = AdBlockerEngine.classifyRequest(
                            requestUrl = reqUrl,
                            adBlockEnabled = prefs.adBlockEnabled,
                            videoAdSkipperEnabled = prefs.videoAdSkipperEnabled,
                            trackerBlockEnabled = prefs.trackerBlockEnabled
                        )
                        if (blockType != ShieldBlockType.NONE) {
                            view?.post {
                                onShieldBlocked(currentTab.id, blockType, reqUrl)
                            }
                            return AdBlockerEngine.createBlockedResourceResponse(reqUrl)
                        }
                        return super.shouldInterceptRequest(view, request)
                    }

                    override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                        super.onPageStarted(view, url, favicon)
                        val displayUrl = if (url == "https://shield.vista.local/" || url == "https://shield.aegis.local/") {
                            "aegis://shield-test"
                        } else {
                            url
                        }
                        onPageStateChanged(
                            currentTab.id,
                            displayUrl,
                            view?.title,
                            true,
                            15,
                            view?.canGoBack() ?: false,
                            view?.canGoForward() ?: false
                        )
                        injectShield(view)
                    }

                    override fun onPageCommitVisible(view: WebView?, url: String?) {
                        super.onPageCommitVisible(view, url)
                        injectShield(view)
                    }

                    override fun doUpdateVisitedHistory(view: WebView?, url: String?, isReload: Boolean) {
                        super.doUpdateVisitedHistory(view, url, isReload)
                        // Crucial for SPA sites like YouTube where navigating between videos doesn't trigger onPageStarted
                        injectShield(view)
                    }

                    override fun onPageFinished(view: WebView?, url: String?) {
                        super.onPageFinished(view, url)
                        val displayUrl = if (url == "https://shield.vista.local/" || url == "https://shield.aegis.local/") {
                            "aegis://shield-test"
                        } else {
                            url
                        }
                        injectShield(view)
                        onPageStateChanged(
                            currentTab.id,
                            displayUrl,
                            view?.title,
                            false,
                            100,
                            view?.canGoBack() ?: false,
                            view?.canGoForward() ?: false
                        )
                    }
                }

                webViewRef = this
                if (activeTab.url.isNotBlank() && !activeTab.isHome && !activeTab.isAiSearchOverview) {
                    if (activeTab.url == "aegis://shield-test" || activeTab.url == "vista://shield-test") {
                        loadDataWithBaseURL(
                            "https://shield.vista.local",
                            AdBlockerEngine.buildShieldTestPageHtml(
                                adsBlocked = currentPrefs.totalAdsBlocked,
                                videoAdsBlocked = currentPrefs.totalVideoAdsBlocked,
                                trackersBlocked = currentPrefs.totalTrackersBlocked
                            ),
                            "text/html",
                            "UTF-8",
                            null
                        )
                    } else {
                        loadUrl(activeTab.url)
                    }
                }
            }
        }
    )
}
