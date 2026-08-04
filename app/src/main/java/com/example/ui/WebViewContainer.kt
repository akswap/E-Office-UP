package com.example.ui

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.net.http.SslError
import android.view.ViewGroup
import android.webkit.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.graphics.graphicsLayer
import kotlinx.coroutines.flow.collectLatest
import androidx.webkit.WebViewFeature
import androidx.webkit.WebViewCompat
import androidx.webkit.ProfileStore

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun WebViewContainer(
    viewModel: BrowserViewModel,
    modifier: Modifier = Modifier,
    onWebViewCreated: (WebView) -> Unit = {}
) {
    val tabs by viewModel.tabs.collectAsState()
    val activeTabId by viewModel.activeTabId.collectAsState()
    val isDesktopMode by viewModel.isDesktopMode.collectAsState()
    val bypassSsl by viewModel.bypassSsl.collectAsState()

    val webViewsRegistry = remember { mutableStateMapOf<String, WebView>() }

    androidx.compose.foundation.layout.Box(modifier = modifier) {
        tabs.forEach { tab ->
            val isSelected = tab.id == activeTabId
            
            androidx.compose.foundation.layout.Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        alpha = if (isSelected) 1f else 0f
                        translationX = if (isSelected) 0f else 99999f
                    }
            ) {
                key(tab.id) {
                    TabWebView(
                        tabId = tab.id,
                        initialUrl = tab.url,
                        isPrivate = tab.isPrivate,
                        viewModel = viewModel,
                        isDesktopMode = isDesktopMode,
                        bypassSsl = bypassSsl,
                        isSelected = isSelected,
                        onWebViewCreated = { webView ->
                            webViewsRegistry[tab.id] = webView
                            if (isSelected) {
                                onWebViewCreated(webView)
                            }
                        },
                        onUrlChanged = { newUrl ->
                            viewModel.updateTabUrl(tab.id, newUrl)
                        },
                        onTitleChanged = { newTitle ->
                            viewModel.updateTabTitle(tab.id, newTitle)
                        }
                    )
                }
            }
        }
    }

    LaunchedEffect(activeTabId, tabs) {
        val currentActiveWebView = webViewsRegistry[activeTabId]
        if (currentActiveWebView != null) {
            onWebViewCreated(currentActiveWebView)
            viewModel.updateUrl(currentActiveWebView.url ?: "")
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun TabWebView(
    tabId: String,
    initialUrl: String,
    isPrivate: Boolean,
    viewModel: BrowserViewModel,
    isDesktopMode: Boolean,
    bypassSsl: Boolean,
    isSelected: Boolean,
    onWebViewCreated: (WebView) -> Unit,
    onUrlChanged: (String) -> Unit,
    onTitleChanged: (String) -> Unit
) {
    var webViewInstance by remember { mutableStateOf<WebView?>(null) }

    if (isPrivate) {
        DisposableEffect(tabId) {
            onDispose {
                if (WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE)) {
                    try {
                        val profileStore = ProfileStore.getInstance()
                        profileStore.deleteProfile("PrivateProfile_$tabId")
                    } catch (e: Exception) {
                        // ignore
                    }
                }
            }
        }
    }

    LaunchedEffect(viewModel.commands, isSelected) {
        if (isSelected) {
            viewModel.commands.collectLatest { command ->
                webViewInstance?.let { webView ->
                    when (command) {
                        is BrowserCommand.GoBack -> {
                            if (webView.canGoBack()) {
                                webView.goBack()
                            } else {
                                viewModel.addLog("ℹ️ Cannot go back: start of history reached")
                            }
                        }
                        is BrowserCommand.GoForward -> {
                            if (webView.canGoForward()) {
                                webView.goForward()
                            } else {
                                viewModel.addLog("ℹ️ Cannot go forward: end of history reached")
                            }
                        }
                        is BrowserCommand.Reload -> {
                            webView.reload()
                        }
                        is BrowserCommand.Print -> {
                            val activity = webView.context as? android.app.Activity
                            activity?.runOnUiThread {
                                try {
                                    val printManager = webView.context.getSystemService(android.content.Context.PRINT_SERVICE) as? android.print.PrintManager
                                    if (printManager != null) {
                                        val jobName = "${webView.title ?: "Document"}_Print"
                                        val printAdapter = webView.createPrintDocumentAdapter(jobName)
                                        printManager.print(jobName, printAdapter, android.print.PrintAttributes.Builder().build())
                                        viewModel.addLog("🖨️ Initiated printing for page: ${webView.title}")
                                    } else {
                                        viewModel.addLog("❌ Print Service is unavailable on this device.")
                                    }
                                } catch (e: Exception) {
                                    viewModel.addLog("❌ Print failed: ${e.localizedMessage}")
                                }
                            }
                        }
                        is BrowserCommand.LoadUrl -> {
                            loadWebViewUrl(webView, command.url, isDesktopMode)
                        }
                    }
                }
            }
        }
    }

    LaunchedEffect(isDesktopMode, webViewInstance) {
        webViewInstance?.let { webView ->
            applyWebViewSettings(webView, isDesktopMode, webView.url)
            if (webView.url != null) {
                webView.reload()
            }
        }
    }

    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { context ->
            WebView(context).apply {
                if (isPrivate && WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE)) {
                    try {
                        WebViewCompat.setProfile(this, "PrivateProfile_$tabId")
                    } catch (e: Exception) {
                        // ignore
                    }
                }
                webViewInstance = this
                onWebViewCreated(this)
                
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )

                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.allowFileAccess = true
                settings.allowContentAccess = true
                settings.mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
                
                // --- Speed & Performance Optimizations ---
                if (isPrivate) {
                    settings.cacheMode = WebSettings.LOAD_NO_CACHE
                    settings.databaseEnabled = false
                    settings.savePassword = false
                    settings.saveFormData = false
                } else {
                    settings.cacheMode = WebSettings.LOAD_DEFAULT
                    settings.databaseEnabled = true
                }
                settings.loadsImagesAutomatically = true
                settings.offscreenPreRaster = true // Pre-renders elements off-screen for instantly smooth scrolling
                setLayerType(android.view.View.LAYER_TYPE_HARDWARE, null) // Forces full hardware rendering pipeline
                // ----------------------------------------

                applyWebViewSettings(this, isDesktopMode, initialUrl)

                if (isPrivate) {
                    settings.savePassword = false
                    settings.saveFormData = false
                }

                webViewClient = object : WebViewClient() {
                    override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                        super.onPageStarted(view, url, favicon)
                        view?.let { wv ->
                            applyWebViewSettings(wv, isDesktopMode, url)
                            
                            // Trigger background JVM pre-warming of government hostnames to bypass DNS/handshake latency
                            if (url != null && (url.contains("districts.upeoffice.gov.in") || url.contains("parichay.nic.in"))) {
                                viewModel.prewarmGovernmentPortals()
                            }
                        }
                        if (isSelected) {
                            viewModel.updateLoading(true)
                        }
                        url?.let {
                            onUrlChanged(it)
                            if (isSelected) {
                                viewModel.updateUrl(it)
                            }
                        }
                    }

                    override fun onPageFinished(view: WebView?, url: String?) {
                        super.onPageFinished(view, url)
                        if (isSelected) {
                            viewModel.updateLoading(false)
                            viewModel.updateProgress(100)
                        }
                        if (url != null && view != null) {
                            val title = view.title ?: url
                            onUrlChanged(url)
                            onTitleChanged(title)
                            if (isSelected) {
                                viewModel.updateUrl(url)
                            }
                            if (!isPrivate) {
                                viewModel.addHistoryEntry(title, url)
                            }
                            
                            val currentUrl = view?.url ?: ""
                            val isIncognitoUrl = isIncognitoLandingPage(currentUrl)
                            val isDesktop = if (isIncognitoUrl) false else viewModel.isDesktopMode.value
                            view.evaluateJavascript(getInjectionJs(isDesktop), null)
                        }
                    }

                    override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                        return false
                    }

                    override fun onReceivedSslError(view: WebView?, handler: SslErrorHandler?, error: SslError?) {
                        if (bypassSsl) {
                            viewModel.addLog("🔒 [SSL Bypassed] Bypassing certificate warning for: ${error?.url}")
                            handler?.proceed()
                        } else {
                            viewModel.addLog("🚨 [SSL Handshake Warning] Blocked untrusted certificate. Feel free to enable 'Bypass SSL' in Settings to load.")
                            super.onReceivedSslError(view, handler, error)
                        }
                    }

                    override fun onReceivedError(
                        view: WebView?,
                        request: WebResourceRequest?,
                        error: WebResourceError?
                    ) {
                        super.onReceivedError(view, request, error)
                        val urlStr = request?.url?.toString() ?: ""
                        if (request?.isForMainFrame == true) {
                            viewModel.addLog("❌ Load Error: ${error?.description} on $urlStr")
                            if (isSelected) {
                                viewModel.updateLoading(false)
                            }
                        }
                    }
                }

                webChromeClient = object : WebChromeClient() {
                    override fun onProgressChanged(view: WebView?, newProgress: Int) {
                        super.onProgressChanged(view, newProgress)
                        if (isSelected) {
                            viewModel.updateProgress(newProgress)
                            if (newProgress >= 100) {
                                viewModel.updateLoading(false)
                            }
                        }
                        if (newProgress > 45 && view != null) {
                            val currentUrl = view?.url ?: ""
                            val isIncognitoUrl = isIncognitoLandingPage(currentUrl)
                            val isDesktop = if (isIncognitoUrl) false else viewModel.isDesktopMode.value
                            view.evaluateJavascript(getInjectionJs(isDesktop), null)
                        }
                    }

                    override fun onReceivedTitle(view: WebView?, title: String?) {
                        super.onReceivedTitle(view, title)
                        if (title != null && !title.startsWith("http", ignoreCase = true)) {
                            val currentUrl = view?.url ?: ""
                            onTitleChanged(title)
                            if (currentUrl.isNotEmpty() && !isPrivate) {
                                viewModel.addHistoryEntry(title, currentUrl)
                            }
                        }
                    }
                }

                val cookieManager = CookieManager.getInstance()
                cookieManager.setAcceptCookie(true)
                cookieManager.setAcceptThirdPartyCookies(this, true)

                setDownloadListener { url, userAgent, contentDisposition, mimetype, contentLength ->
                    if (url.startsWith("blob:", ignoreCase = true) || url.startsWith("data:", ignoreCase = true)) {
                        val guessedName = android.webkit.URLUtil.guessFileName(url, contentDisposition, mimetype)
                        val escapedUrl = url.replace("'", "\\'")
                        val escapedName = (guessedName ?: "document.pdf").replace("'", "\\'")
                        val escapedMime = (mimetype ?: "application/pdf").replace("'", "\\'")
                        val js = """
                            (function() {
                                var url = '$escapedUrl';
                                var filename = '$escapedName';
                                var mime = '$escapedMime';
                                if (url.startsWith('data:')) {
                                    if (window.AndroidPrint) {
                                        window.AndroidPrint.downloadBase64(url, filename, mime);
                                    }
                                } else {
                                    try {
                                        var xhr = new XMLHttpRequest();
                                        xhr.open('GET', url, true);
                                        xhr.responseType = 'blob';
                                        xhr.onload = function() {
                                            if (this.status === 200) {
                                                var reader = new FileReader();
                                                reader.onloadend = function() {
                                                    if (window.AndroidPrint) {
                                                        window.AndroidPrint.downloadBase64(reader.result, filename, mime || this.response.type);
                                                    }
                                                };
                                                reader.readAsDataURL(this.response);
                                            }
                                        };
                                        xhr.onerror = function() {
                                            console.error("XHR blob load error");
                                        };
                                        xhr.send();
                                    } catch(e) {
                                        console.error("XHR trigger error: " + e.message);
                                    }
                                }
                            })();
                        """.trimIndent()
                        evaluateJavascript(js, null)
                    } else {
                        viewModel.startDownload(url, userAgent, contentDisposition, mimetype, contentLength)
                    }
                }

                addJavascriptInterface(WebAppInterface(context, this, viewModel), "AndroidPrint")

                loadWebViewUrl(this, initialUrl, isDesktopMode)
            }
        },
        update = {
            // Updated via LaunchedEffect elements
        }
    )
}

private fun getInjectionJs(isDesktop: Boolean): String {
    val viewportValue = if (isDesktop) {
        "width=1280, initial-scale=0.35, minimum-scale=0.1, maximum-scale=5.0, user-scalable=yes"
    } else {
        "width=device-width, initial-scale=1.0, minimum-scale=0.2, maximum-scale=5.0, user-scalable=yes"
    }
    val desktopOverrideJs = if (isDesktop) {
        """
        try {
            Object.defineProperty(navigator, 'platform', { get: function() { return 'Win32'; } });
            Object.defineProperty(navigator, 'userAgent', { get: function() { return 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36'; } });
            Object.defineProperty(navigator, 'maxTouchPoints', { get: function() { return 1; } });
        } catch(e){}
        
        try {
            Object.defineProperty(window, 'orientation', { get: function() { return 90; } });
        } catch(e){}
        
        try {
            if (window.screen) {
                Object.defineProperty(window.screen, 'width', { get: function() { return 1280; } });
                Object.defineProperty(window.screen, 'height', { get: function() { return 800; } });
                Object.defineProperty(window.screen, 'availWidth', { get: function() { return 1280; } });
                Object.defineProperty(window.screen, 'availHeight', { get: function() { return 800; } });
                
                if (!window.screen.orientation) {
                    window.screen.orientation = {};
                }
                Object.defineProperty(window.screen.orientation, 'angle', { get: function() { return 90; } });
                Object.defineProperty(window.screen.orientation, 'type', { get: function() { return 'landscape-primary'; } });
            }
        } catch(e){}
        
        try {
            Object.defineProperty(window, 'innerWidth', { get: function() { return 1280; } });
            Object.defineProperty(window, 'innerHeight', { get: function() { return 800; } });
            Object.defineProperty(window, 'outerWidth', { get: function() { return 1280; } });
            Object.defineProperty(window, 'outerHeight', { get: function() { return 800; } });
        } catch(e){}
        """.trimIndent()
    } else {
        ""
    }
    return """
        (function() {
            try {
                window.print = function() {
                    if (window.AndroidPrint) {
                        window.AndroidPrint.printPage();
                    } else {
                        console.log("Android print interface is not registered.");
                    }
                };
            } catch(e){}

            // Register Blob Tracking for locally generated document blobs
            try {
                if (!window._createObjectURL_patched) {
                    window._createObjectURL_patched = true;
                    var originalCreateObjectURL = window.URL.createObjectURL;
                    window.URL.createObjectURL = function(obj) {
                        var url = originalCreateObjectURL(obj);
                        try {
                            if (obj instanceof Blob) {
                                if (!window._blobs) window._blobs = {};
                                window._blobs[url] = obj;
                            }
                        } catch(err){}
                        return url;
                    };
                }
            } catch(e){}

            // Intercept helper functions
            function dispatchBlobOrData(url, filename, mimeType) {
                if (url.startsWith('data:')) {
                    if (window.AndroidPrint) {
                        window.AndroidPrint.downloadBase64(url, filename || 'document.pdf', mimeType || 'application/pdf');
                        return true;
                    }
                } else if (url.startsWith('blob:')) {
                    var blobObj = window._blobs ? window._blobs[url] : null;
                    if (blobObj) {
                        var reader = new FileReader();
                        reader.onloadend = function() {
                            if (window.AndroidPrint) {
                                window.AndroidPrint.downloadBase64(reader.result, filename || 'document.pdf', mimeType || blobObj.type);
                            }
                        };
                        reader.readAsDataURL(blobObj);
                        return true;
                    } else {
                        try {
                            var xhr = new XMLHttpRequest();
                            xhr.open('GET', url, true);
                            xhr.responseType = 'blob';
                            xhr.onload = function() {
                                if (this.status === 200) {
                                    var reader = new FileReader();
                                    reader.onloadend = function() {
                                        if (window.AndroidPrint) {
                                            window.AndroidPrint.downloadBase64(reader.result, filename || 'document.pdf', mimeType || this.response.type);
                                        }
                                    };
                                    reader.readAsDataURL(this.response);
                                }
                            };
                            xhr.send();
                        } catch(err){}
                        return true;
                    }
                }
                return false;
            }

            // Universal Click Listener to catch hidden element anchor clicks & dynamic PDF blobs
            try {
                document.addEventListener('click', function(e) {
                    var el = e.target;
                    while (el && el.tagName !== 'A') {
                        el = el.parentNode;
                    }
                    if (el && el.tagName === 'A') {
                        var href = el.getAttribute('href') || '';
                        var downloadName = el.getAttribute('download');
                        if (downloadName !== null || href.startsWith('blob:') || href.startsWith('data:')) {
                            if (href.startsWith('blob:') || href.startsWith('data:')) {
                                if (dispatchBlobOrData(href, downloadName, '')) {
                                    e.preventDefault();
                                    e.stopPropagation();
                                }
                            }
                        }
                    }
                }, true);
            } catch(e){}

            var metas = document.getElementsByTagName('meta');
            var found = false;
            var val = '$viewportValue';
            for (var i = 0; i < metas.length; i++) {
                if (metas[i].getAttribute('name') === 'viewport') {
                    metas[i].setAttribute('content', val);
                    found = true;
                }
            }
            if (!found) {
                var meta = document.createElement('meta');
                meta.name = 'viewport';
                meta.content = val;
                var heads = document.getElementsByTagName('head');
                if (heads.length > 0) {
                    heads[0].appendChild(meta);
                }
            }
            
            $desktopOverrideJs

            function hideLandscapeBlockers() {
                var blockers = document.querySelectorAll(
                    'div[id*="landscape" i], div[class*="landscape" i], ' +
                    'div[id*="orientation" i], div[class*="orientation" i], ' +
                    'section[id*="landscape" i], section[class*="landscape" i], ' +
                    '#portrait-only, .portrait-only, #landscape-blocker, #landscape-warning, .landscape-warning, ' +
                    'div[style*="z-index"][style*="landscape" i], div[style*="z-index"][style*="orientation" i]'
                );
                for (var i = 0; i < blockers.length; i++) {
                    blockers[i].style.setProperty('display', 'none', 'important');
                    blockers[i].style.setProperty('visibility', 'hidden', 'important');
                    blockers[i].style.setProperty('opacity', '0', 'important');
                    blockers[i].style.setProperty('pointer-events', 'none', 'important');
                }

                var divs = document.getElementsByTagName('div');
                for (var j = 0; j < divs.length; j++) {
                    var el = divs[j];
                    if (el && el.innerText && el.children.length < 5) {
                        var txt = el.innerText.toLowerCase();
                        if ((txt.includes('landscape mode') || txt.includes('landscape orientation') || txt.includes('use landscape')) && 
                            !txt.includes('e-office') && !txt.includes('government') && el.offsetWidth > 0) {
                            el.style.setProperty('display', 'none', 'important');
                            el.style.setProperty('visibility', 'hidden', 'important');
                        }
                    }
                }
                
                if (document.body) {
                    document.body.style.setProperty('overflow', 'auto', 'important');
                    document.body.style.setProperty('display', 'block', 'important');
                    document.body.style.setProperty('visibility', 'visible', 'important');
                }
                if (document.documentElement) {
                    document.documentElement.style.setProperty('overflow', 'auto', 'important');
                }
            }

            try {
                hideLandscapeBlockers();
            } catch(e){}

            var attempts = 0;
            var intervalId = setInterval(function() {
                attempts++;
                try {
                    hideLandscapeBlockers();
                } catch(e){}
                if (attempts > 12) {
                    clearInterval(intervalId);
                }
            }, 500);
        })();
    """.trimIndent()
}

fun getSanitizedMobileUserAgent(context: android.content.Context): String {
    return try {
        val defaultUa = WebSettings.getDefaultUserAgent(context)
        // Strip "; wv" and "Version/4.0 " which cause Google and YouTube to block login (disallowed_useragent)
        defaultUa.replace("; wv", "").replace("Version/4.0 ", "")
    } catch (e: Exception) {
        // Fallback to high-fidelity standard Mobile Chrome UA if default retrieval fails
        "Mozilla/5.0 (Linux; Android 13; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
    }
}

class WebAppInterface(private val context: android.content.Context, private val webView: WebView, private val viewModel: BrowserViewModel) {
    @JavascriptInterface
    fun downloadBase64(base64Data: String, fileName: String, mimeType: String) {
        viewModel.saveBase64Download(base64Data, fileName, mimeType)
    }

    @JavascriptInterface
    fun printPage() {
        val activity = context as? android.app.Activity
        activity?.runOnUiThread {
            try {
                val printManager = context.getSystemService(android.content.Context.PRINT_SERVICE) as? android.print.PrintManager
                if (printManager != null) {
                    val jobName = "${webView.title ?: "Document"}_Print"
                    val printAdapter = webView.createPrintDocumentAdapter(jobName)
                    printManager.print(jobName, printAdapter, android.print.PrintAttributes.Builder().build())
                    viewModel.addLog("🖨️ Webpage triggered print request for: ${webView.title}")
                } else {
                    android.widget.Toast.makeText(context, "Print Service not available", android.widget.Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                android.widget.Toast.makeText(context, "Print failed: ${e.localizedMessage}", android.widget.Toast.LENGTH_SHORT).show()
            }
        }
    }

    @JavascriptInterface
    fun isVpnActive(): Boolean {
        return try {
            val connectivityManager = context.getSystemService(android.content.Context.CONNECTIVITY_SERVICE) as? android.net.ConnectivityManager
            var isVpn = false
            if (connectivityManager != null) {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                    val activeNetwork = connectivityManager.activeNetwork
                    val capabilities = connectivityManager.getNetworkCapabilities(activeNetwork)
                    isVpn = capabilities?.hasTransport(android.net.NetworkCapabilities.TRANSPORT_VPN) == true
                } else {
                    val networks = connectivityManager.allNetworks
                    isVpn = networks.any { network ->
                        val capabilities = connectivityManager.getNetworkCapabilities(network)
                        capabilities?.hasTransport(android.net.NetworkCapabilities.TRANSPORT_VPN) == true
                    }
                }
            }
            if (!isVpn) {
                val interfaces = java.net.NetworkInterface.getNetworkInterfaces()
                if (interfaces != null) {
                    for (intf in java.util.Collections.list(interfaces)) {
                        if (intf.isUp) {
                            val name = intf.name.lowercase()
                            if (name.contains("tun") || name.contains("ppp") || name.contains("tap") || name.contains("p2p")) {
                                isVpn = true
                                break
                            }
                        }
                    }
                }
            }
            isVpn
        } catch (e: Exception) {
            false
        }
    }
}

private fun loadWebViewUrl(webView: WebView, url: String, isDesktopMode: Boolean) {
    val isIncognitoUrl = url == "about:incognito" || url == "chrome://incognito" || url == "about:private" || url.lowercase().contains("incognito")
    applyWebViewSettings(webView, isDesktopMode, url)
    if (isIncognitoUrl) {
        webView.loadDataWithBaseURL("https://incognito.local", getIncognitoHtmlSection(), "text/html", "UTF-8", null)
    } else {
        webView.loadUrl(url)
    }
}

private fun isIncognitoLandingPage(url: String?): Boolean {
    if (url == null) return false
    return url == "about:incognito" || 
           url == "chrome://incognito" || 
           url == "about:private" || 
           url == "https://incognito.local" || 
           url == "https://incognito.local/" || 
           url.contains("incognito.local")
}

private fun applyWebViewSettings(webView: WebView, isDesktopMode: Boolean, url: String?) {
    val settings = webView.settings
    val isIncognitoUrl = isIncognitoLandingPage(url)

    if (isDesktopMode && !isIncognitoUrl) {
        settings.userAgentString = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
        settings.useWideViewPort = true
        settings.loadWithOverviewMode = true
    } else {
        settings.userAgentString = getSanitizedMobileUserAgent(webView.context)
        settings.useWideViewPort = false
        settings.loadWithOverviewMode = false
    }
    settings.setSupportZoom(true)
    settings.builtInZoomControls = true
    settings.displayZoomControls = false
}

private fun getIncognitoHtmlSection(): String {
    return """
        <!DOCTYPE html>
        <html lang="en">
        <head>
            <meta charset="UTF-8">
            <meta name="viewport" content="width=device-width, initial-scale=1.0, user-scalable=no">
            <title>You've gone incognito</title>
            <style>
                body {
                    background-color: #0F172A;
                    color: #E2E8F0;
                    font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif;
                    padding: 32px 20px;
                    margin: 0;
                    display: flex;
                    flex-direction: column;
                    align-items: center;
                }
                .container {
                    max-width: 580px;
                    width: 100%;
                }
                .header {
                    text-align: center;
                    margin-bottom: 24px;
                }
                h1 {
                    font-size: 24px;
                    font-weight: 700;
                    color: #F1F5F9;
                    margin-top: 16px;
                    margin-bottom: 8px;
                    letter-spacing: -0.5px;
                }
                p.subtitle {
                    font-size: 15px;
                    color: #94A3B8;
                    line-height: 1.5;
                    margin: 0 auto;
                }
                .search-form {
                    margin: 0 auto 28px auto;
                    max-width: 480px;
                    width: 100%;
                }
                .search-box {
                    display: flex;
                    align-items: center;
                    background-color: #1E293B;
                    border: 1px solid #334155;
                    border-radius: 28px;
                    padding: 4px 6px 4px 14px;
                    box-shadow: 0 4px 12px rgba(0, 0, 0, 0.25);
                    transition: border-color 0.2s, box-shadow 0.2s;
                }
                .search-box:focus-within {
                    border-color: #60A5FA;
                    box-shadow: 0 0 0 2.5px rgba(96, 165, 250, 0.25);
                }
                .search-icon {
                    width: 18px;
                    height: 18px;
                    fill: #94A3B8;
                    margin-right: 12px;
                    flex-shrink: 0;
                }
                .search-box input {
                    flex: 1;
                    background: none;
                    border: none;
                    color: #F8FAFC;
                    font-size: 15px;
                    outline: none;
                    padding: 10px 0;
                    width: 100%;
                    -webkit-appearance: none;
                }
                .search-box input::placeholder {
                    color: #64748B;
                }
                .search-box button {
                    background-color: #3B82F6;
                    color: #FFFFFF;
                    border: none;
                    border-radius: 20px;
                    padding: 8px 18px;
                    font-size: 13.5px;
                    font-weight: 600;
                    cursor: pointer;
                    transition: background-color 0.2s;
                    margin-left: 8px;
                }
                .search-box button:hover {
                    background-color: #2563EB;
                }
                .search-box button:active {
                    background-color: #1D4ED8;
                }
                .vpn-container {
                    text-align: center;
                    margin-top: 12px;
                    min-height: 24px;
                }
                .vpn-warning {
                    color: #F87171;
                    font-size: 14px;
                    font-weight: bold;
                    display: inline-block;
                    animation: fadeIn 0.3s ease-in-out;
                    background-color: rgba(239, 68, 68, 0.15);
                    padding: 4px 12px;
                    border-radius: 6px;
                }
                .vpn-active {
                    color: #34D399;
                    font-size: 14px;
                    font-weight: bold;
                    display: inline-block;
                    animation: fadeIn 0.3s ease-in-out;
                    background-color: rgba(52, 211, 153, 0.15);
                    padding: 4px 12px;
                    border-radius: 6px;
                }
                @keyframes fadeIn {
                    from { opacity: 0; transform: translateY(-3px); }
                    to { opacity: 1; transform: translateY(0); }
                }
                .card-container {
                    display: grid;
                    grid-template-columns: 1fr;
                    gap: 16px;
                    margin-top: 12px;
                }
                @media(min-width: 480px) {
                    .card-container {
                        grid-template-columns: 1fr 1fr;
                    }
                }
                .card {
                    background-color: #1E293B;
                    border-radius: 12px;
                    padding: 18px;
                    box-shadow: 0 4px 6px -1px rgba(0, 0, 0, 0.1);
                    border: 1px solid #334155;
                }
                .card h2 {
                    font-size: 14.5px;
                    font-weight: 650;
                    margin-top: 0;
                    margin-bottom: 10px;
                    color: #F8FAFC;
                }
                .card ul {
                    margin: 0;
                    padding-left: 18px;
                    color: #94A3B8;
                    font-size: 13px;
                }
                .card li {
                    margin-bottom: 6px;
                    line-height: 1.45;
                }
                .card li:last-child {
                    margin-bottom: 0;
                }
            </style>
        </head>
        <body>
            <div class="container">
                <div class="header">
                    <svg height="64" viewBox="0 0 24 24" width="64" fill="#94A3B8" style="display: block; margin: 0 auto;">
                        <path d="M0 0h24v24H0V0z" fill="none"/>
                        <path d="M12 6c-3.1 0-5.8 2.2-6.6 5.1-.3-.1-.7-.1-1-.1-2.2 0-4 1.8-4 4s1.8 4 4 4 4-1.8 4-4c0-.3 0-.7-.1-1 .7.6 1.6 1 2.7 1h1.9c1.1 0 2-.4 2.7-1-.1.3-.1.7-.1 1 0 2.2 1.8 4 4 4s4-1.8 4-4-1.8-4-4-4c-.3 0-.7 0-1 .1C17.8 8.2 15.1 6 12 6zm-7.5 7c1.4 0 2.5 1.1 2.5 2.5S5.9 18 4.5 18 2 16.9 2 15.5 3.1 13 4.5 13zm15 0c1.4 0 2.5 1.1 2.5 2.5S20.9 18 19.5 18 17 16.9 17 15.5 18.1 13 19.5 13z"/>
                    </svg>
                    <h1>You've gone incognito</h1>
                    <p class="subtitle">आप गुप्त रूप से ब्राउज़ कर रहे हैं। इस डिवाइस के अन्य उपयोगकर्ता आपकी गतिविधि नहीं देख पाएंगे।</p>
                </div>
                
                <form id="searchForm" class="search-form">
                    <div class="search-box">
                        <svg class="search-icon" viewBox="0 0 24 24">
                            <path d="M15.5 14h-.79l-.28-.27C15.41 12.59 16 11.11 16 9.5 16 5.91 13.09 3 9.5 3S3 5.91 3 9.5 5.91 16 9.5 16c1.61 0 3.09-.59 4.23-1.57l.27.28v.79l5 4.99L20.49 19l-4.99-5zm-6 0C7.01 14 5 11.99 5 9.5S7.01 5 9.5 5 14 7.01 14 9.5 11.99 14 9.5 14z"/>
                        </svg>
                        <input type="text" id="searchInput" value="https://districts.upeoffice.gov.in" placeholder="Google Search or enter URL..." autocomplete="off" required>
                        <button type="submit">Go</button>
                    </div>
                    <div class="vpn-container">
                        <span id="vpnStatus"></span>
                    </div>
                </form>

                <script>
                    function checkVpnStatus() {
                        var statusEl = document.getElementById('vpnStatus');
                        if (!statusEl) return;
                        
                        var isVpn = false;
                        if (window.AndroidPrint && typeof window.AndroidPrint.isVpnActive === 'function') {
                            isVpn = window.AndroidPrint.isVpnActive();
                        }
                        
                        if (isVpn) {
                            statusEl.className = 'vpn-active';
                            statusEl.innerHTML = '🛡️ VPN Connected';
                        } else {
                            statusEl.className = 'vpn-warning';
                            statusEl.innerHTML = '⚠️ Connect VPN first';
                        }
                    }

                    // Check status initially and continuously every 2 seconds
                    setTimeout(checkVpnStatus, 150);
                    setInterval(checkVpnStatus, 2000);

                    document.getElementById('searchForm').addEventListener('submit', function(e) {
                        e.preventDefault();
                        var query = document.getElementById('searchInput').value.trim();
                        if (!query) return;
                        
                        var isVpn = false;
                        if (window.AndroidPrint && typeof window.AndroidPrint.isVpnActive === 'function') {
                            isVpn = window.AndroidPrint.isVpnActive();
                        }
                        
                        if (!isVpn) {
                            window.location.href = 'https://google.com';
                            return;
                        }
                        
                        var isUrl = false;
                        if (query.startsWith('http://') || query.startsWith('https://')) {
                            isUrl = true;
                        } else {
                            if (query.indexOf('.') !== -1 && query.indexOf(' ') === -1) {
                                isUrl = true;
                            }
                        }
                        
                        if (isUrl) {
                            var url = query;
                            if (!/^https?:\/\//i.test(url)) {
                                url = 'https://' + url;
                            }
                            window.location.href = url;
                        } else {
                            window.location.href = 'https://www.google.com/search?q=' + encodeURIComponent(query);
                        }
                    });
                </script>
                
                <div class="card-container">
                    <div class="card">
                        <h2>Browser won't save (सुरक्षित जानकारियां):</h2>
                        <ul>
                            <li>Your browsing history from this tab</li>
                            <li>Cookies and site data when closed</li>
                            <li>Information entered in forms</li>
                            <li>No local history logs under history manager</li>
                        </ul>
                    </div>
                    <div class="card">
                        <h2>Activity might still be seen (यह दृश्य हो सकता है):</h2>
                        <ul>
                            <li>Websites you visit & identity portals</li>
                            <li>Your employer or school network managers</li>
                            <li>Your ISP / Internet Service Provider</li>
                        </ul>
                    </div>
                </div>
            </div>
        </body>
        </html>
    """.trimIndent()
}

