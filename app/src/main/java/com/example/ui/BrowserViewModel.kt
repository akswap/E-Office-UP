package com.example.ui

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.webkit.ProxyConfig
import androidx.webkit.ProxyController
import androidx.webkit.WebViewFeature
import com.example.data.AppDatabase
import com.example.data.Bookmark
import com.example.data.BrowserHistory
import com.example.data.DownloadItem
import com.example.data.HostMapping
import com.example.data.Repository
import com.example.network.LocalProxyServer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface BrowserCommand {
    object GoBack : BrowserCommand
    object GoForward : BrowserCommand
    object Reload : BrowserCommand
    object Print : BrowserCommand
    data class LoadUrl(val url: String) : BrowserCommand
}

data class BrowserTab(
    val id: String = java.util.UUID.randomUUID().toString(),
    val url: String = "https://districts.upeoffice.gov.in/",
    val title: String = "e-Office UP",
    val isPrivate: Boolean = false
)

class BrowserViewModel(application: Application) : AndroidViewModel(application) {
    private val database = AppDatabase.getDatabase(application)
    private val repository = Repository(database)

    // Tab-based browsing state
    private val _tabs = MutableStateFlow<List<BrowserTab>>(
        listOf(BrowserTab(id = "default", url = "https://districts.upeoffice.gov.in/", title = "e-Office UP", isPrivate = false))
    )
    val tabs: StateFlow<List<BrowserTab>> = _tabs.asStateFlow()

    private val _activeTabId = MutableStateFlow("default")
    val activeTabId: StateFlow<String> = _activeTabId.asStateFlow()

    fun addNewTab(url: String = "https://google.com", isPrivate: Boolean = false) {
        val targetUrl = if (isPrivate && (url == "https://google.com" || url.isEmpty())) "about:incognito" else url
        val newId = java.util.UUID.randomUUID().toString()
        val title = if (isPrivate) "Incognito" else "New Tab"
        val newTab = BrowserTab(id = newId, url = targetUrl, title = title, isPrivate = isPrivate)
        val currentTabs = _tabs.value.toMutableList()
        currentTabs.add(newTab)
        _tabs.value = currentTabs
        _activeTabId.value = newId
        _currentUrl.value = targetUrl
        addLog(if (isPrivate) "🕶️ Opened New Incognito Window" else "➕ Opened new Tab")
    }

    fun clearSessionData() {
        viewModelScope.launch(Dispatchers.Main) {
            try {
                val cookieManager = android.webkit.CookieManager.getInstance()
                cookieManager.removeSessionCookies(null)
                cookieManager.flush()
                
                val webStorage = android.webkit.WebStorage.getInstance()
                webStorage.deleteAllData()
                
                addLog("🧹 Incognito closed: Discarded temporary cookies & site storage")
            } catch (e: Exception) {
                addLog("❌ Error clearing incognito session: ${e.localizedMessage}")
            }
        }
    }

    fun closeTab(tabId: String) {
        val currentTabs = _tabs.value
        val index = currentTabs.indexOfFirst { it.id == tabId }
        if (index == -1) return

        val closedTab = currentTabs[index]
        
        if (currentTabs.size <= 1) {
            // Reset to default instead of empty list
            val startUrl = if (_isVpnConnected.value) "https://districts.upeoffice.gov.in/" else "https://google.com"
            val startTitle = if (_isVpnConnected.value) "e-Office UP" else "Google"
            val defaultTab = BrowserTab(id = "default", url = startUrl, title = startTitle, isPrivate = false)
            _tabs.value = listOf(defaultTab)
            _activeTabId.value = "default"
            _currentUrl.value = defaultTab.url
            addLog("🧹 Closed all tabs, reset to default")
            
            // If the single closed tab was private, execute session cleanup
            if (closedTab.isPrivate) {
                clearSessionData()
            }
            return
        }

        val newList = currentTabs.toMutableList()
        newList.removeAt(index)
        _tabs.value = newList

        if (_activeTabId.value == tabId) {
            val newActiveIndex = if (index >= newList.size) newList.size - 1 else index
            val nextActive = newList[newActiveIndex]
            _activeTabId.value = nextActive.id
            _currentUrl.value = nextActive.url
        }
        addLog("🗑️ Closed Tab")

        // If closed tab was private, check if that was the last incognito window
        if (closedTab.isPrivate) {
            val remPrivateCount = newList.count { it.isPrivate }
            if (remPrivateCount == 0) {
                clearSessionData()
            }
        }
    }

    fun selectTab(tabId: String) {
        val found = _tabs.value.find { it.id == tabId }
        if (found != null) {
            _activeTabId.value = tabId
            _currentUrl.value = found.url
            addLog("🔀 Switched to tab: ${found.title}")
        }
    }

    fun updateTabUrl(tabId: String, newUrl: String) {
        val currentTabs = _tabs.value
        val index = currentTabs.indexOfFirst { it.id == tabId }
        if (index != -1) {
            val oldTab = currentTabs[index]
            if (oldTab.url != newUrl) {
                val newList = currentTabs.toMutableList()
                newList[index] = oldTab.copy(url = newUrl)
                _tabs.value = newList
                
                if (_activeTabId.value == tabId) {
                    _currentUrl.value = newUrl
                }
            }
        }
    }

    fun updateTabTitle(tabId: String, newTitle: String) {
        val currentTabs = _tabs.value
        val index = currentTabs.indexOfFirst { it.id == tabId }
        if (index != -1) {
            val oldTab = currentTabs[index]
            if (oldTab.title != newTitle) {
                val newList = currentTabs.toMutableList()
                newList[index] = oldTab.copy(title = newTitle)
                _tabs.value = newList
            }
        }
    }

    // Current web browsing state
    private val _currentUrl = MutableStateFlow("https://districts.upeoffice.gov.in")
    val currentUrl: StateFlow<String> = _currentUrl.asStateFlow()

    private val _commands = MutableSharedFlow<BrowserCommand>(extraBufferCapacity = 1)
    val commands = _commands.asSharedFlow()

    fun goBack() {
        _commands.tryEmit(BrowserCommand.GoBack)
    }

    fun goForward() {
        _commands.tryEmit(BrowserCommand.GoForward)
    }

    fun reload() {
        _commands.tryEmit(BrowserCommand.Reload)
    }

    fun triggerPrint() {
        _commands.tryEmit(BrowserCommand.Print)
    }

    fun loadUrl(url: String) {
        val trimmed = url.trim()
        if (trimmed.isEmpty()) return

        var finalUrl = trimmed
        val isUrl = isLikelyUrl(trimmed)

        if (isUrl) {
            if (!finalUrl.startsWith("http://", ignoreCase = true) && 
                !finalUrl.startsWith("https://", ignoreCase = true)) {
                finalUrl = "https://$finalUrl"
            }
        } else {
            // Treat as keyword search using Google search engine
            val encodedQuery = try {
                java.net.URLEncoder.encode(trimmed, "UTF-8")
            } catch (e: Exception) {
                trimmed
            }
            finalUrl = "https://www.google.com/search?q=$encodedQuery"
        }

        _currentUrl.value = finalUrl
        _commands.tryEmit(BrowserCommand.LoadUrl(finalUrl))
    }

    private fun isLikelyUrl(input: String): Boolean {
        val str = input.lowercase().trim()
        // If there's spaces, it's definitely a search query
        if (str.contains(" ")) return false
        if (str.startsWith("http://") || str.startsWith("https://")) return true
        if (str == "localhost") return true
        
        // Simple heuristic to check if it has a pattern like "domain.com"
        val dotIndex = str.indexOf('.')
        if (dotIndex > 0 && dotIndex < str.length - 1) {
            val hostPart = if (str.contains("/")) str.substringBefore("/") else str
            return hostPart.contains(".") && hostPart.all { it.isLetterOrDigit() || it == '.' || it == '-' || it == '_' || it == ':' }
        }
        return false
    }

    private val _progress = MutableStateFlow(0)
    val progress: StateFlow<Int> = _progress.asStateFlow()

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _isDesktopMode = MutableStateFlow(true)
    val isDesktopMode: StateFlow<Boolean> = _isDesktopMode.asStateFlow()

    private val _bypassSsl = MutableStateFlow(true) // Enabled by default for VPN / private enterprise intranets
    val bypassSsl: StateFlow<Boolean> = _bypassSsl.asStateFlow()

    private val _isProxyActive = MutableStateFlow(false)
    val isProxyActive: StateFlow<Boolean> = _isProxyActive.asStateFlow()

    private val _isVpnConnected = MutableStateFlow(false)
    val isVpnConnected: StateFlow<Boolean> = _isVpnConnected.asStateFlow()

    private var lastVpnState: Boolean? = null

    private val _internetType = MutableStateFlow("None")
    val internetType: StateFlow<String> = _internetType.asStateFlow()

    private val _networkSignalLevel = MutableStateFlow(4) // 0 to 4 Signal Bars
    val networkSignalLevel: StateFlow<Int> = _networkSignalLevel.asStateFlow()

    private val _onlyVpnForSecured = MutableStateFlow(true)
    val onlyVpnForSecured: StateFlow<Boolean> = _onlyVpnForSecured.asStateFlow()

    fun toggleOnlyVpnForSecured() {
        _onlyVpnForSecured.value = !_onlyVpnForSecured.value
        addLog("Alert VPN only for e-Office/HOST: ${_onlyVpnForSecured.value}")
    }

    fun isUrlSecuredOrMapped(url: String): Boolean {
        val lowerUrl = url.lowercase().trim()
        if (lowerUrl.contains("eoffice")) {
            return true
        }
        try {
            val uri = java.net.URI(lowerUrl)
            // Handle case where custom host handles port inside URI parsed as authority
            val host = uri.host?.lowercase() ?: ""
            if (host.isNotEmpty()) {
                if (activeMappings.keys.any { mappedHost -> 
                    host == mappedHost || host.endsWith(".$mappedHost")
                }) {
                    return true
                }
            } else {
                // Fallback for simple local names or string-based contains matching
                if (activeMappings.keys.any { mappedHost -> lowerUrl.contains(mappedHost) }) {
                    return true
                }
            }
        } catch (e: Exception) {
            if (activeMappings.keys.any { mappedHost -> lowerUrl.contains(mappedHost) }) {
                return true
            }
        }
        return false
    }

    // Host mappings from Database
    val hostMappings: StateFlow<List<HostMapping>> = repository.allMappings
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    // User bookmarks
    val bookmarks: StateFlow<List<Bookmark>> = repository.bookmarks
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    // User browsing history
    val history: StateFlow<List<BrowserHistory>> = repository.history
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    // User downloaded files list
    val downloads: StateFlow<List<DownloadItem>> = repository.downloads
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    // Session Logs
    private val _logs = MutableStateFlow<List<String>>(emptyList())
    val logs: StateFlow<List<String>> = _logs.asStateFlow()

    // Helper map of hostname -> IP address computed on changes
    @Volatile
    private var activeMappings: Map<String, String> = emptyMap()

    // Local proxy daemon
    private val proxyServer = LocalProxyServer(
        getHostIp = { host ->
            activeMappings[host.lowercase().trim()]
        },
        onLog = { message ->
            addLog(message)
        }
    )

    init {
        // First check VPN to determine startup URL
        checkVpnStatus()
        val initialUrl = if (_isVpnConnected.value) {
            "https://districts.upeoffice.gov.in/"
        } else {
            "https://google.com"
        }
        _currentUrl.value = initialUrl
        _tabs.value = listOf(
            BrowserTab(
                id = "default",
                url = initialUrl,
                title = if (_isVpnConnected.value) "e-Office UP" else "Google",
                isPrivate = false
            )
        )
        lastVpnState = _isVpnConnected.value
        prewarmGovernmentPortals()

        viewModelScope.launch {
            repository.seedDefaultsIfEmpty()
            
            // Auto-start proxy immediately after seeding completes to guarantee it is active on first paint
            setProxyState(true)
            
            // Collect mappings reactively to update the active in-memory cache
            hostMappings.collect { mappings ->
                activeMappings = mappings
                    .filter { it.isEnabled }
                    .associate { it.hostname.lowercase().trim() to it.ipAddress.trim() }
                
                // If the proxy is already active, restart it with updated mappings
                if (_isProxyActive.value) {
                    restartProxyWithUpdatedMappings()
                }
            }
        }

        // Periodically check VPN status every 3 seconds to keep UI responsive
        viewModelScope.launch(Dispatchers.IO) {
            while (true) {
                checkVpnStatus()
                delay(3000)
            }
        }
    }

    fun addLog(message: String) {
        val timestamp = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())
        _logs.update { current ->
            listOf("[$timestamp] $message") + current.take(199) // limit to 200 logs
        }
    }

    fun clearLogs() {
        _logs.value = listOf("[System] Logs cleared")
    }

    fun prewarmGovernmentPortals() {
        viewModelScope.launch(Dispatchers.IO) {
            val targets = listOf(
                "https://districts.upeoffice.gov.in/",
                "https://parichay.nic.in/",
                "https://services.parichay.nic.in/",
                "https://janparichay.nic.in/",
                "https://sso.nic.in/"
            )
            for (target in targets) {
                try {
                    val url = java.net.URL(target)
                    // 1. Resolve DNS to system cache
                    java.net.InetAddress.getAllByName(url.host)
                    
                    // 2. Open Http connection to prewarm SSL Handshake and keep alive TCP connections
                    val conn = if (_isProxyActive.value && proxyServer.port > 0) {
                        url.openConnection(
                            java.net.Proxy(
                                java.net.Proxy.Type.HTTP,
                                java.net.InetSocketAddress("127.0.0.1", proxyServer.port)
                            )
                        ) as java.net.HttpURLConnection
                    } else {
                        url.openConnection() as java.net.HttpURLConnection
                    }
                    conn.requestMethod = "HEAD"
                    conn.connectTimeout = 3000
                    conn.readTimeout = 3000
                    conn.connect()
                    conn.disconnect()
                } catch (e: Exception) {
                    // Ignore background warming errors
                }
            }
        }
    }

    fun updateUrl(url: String) {
        _currentUrl.value = url
    }

    fun updateProgress(value: Int) {
        _progress.value = value
    }

    fun updateLoading(loading: Boolean) {
        _isLoading.value = loading
    }

    fun toggleDesktopMode() {
        _isDesktopMode.value = !_isDesktopMode.value
        addLog("Desktop mode toggled: ${_isDesktopMode.value}")
    }

    fun toggleBypassSsl() {
        _bypassSsl.value = !_bypassSsl.value
        addLog("Bypass SSL errors toggled: ${_bypassSsl.value}")
    }

    fun setProxyState(enabled: Boolean) {
        if (_isProxyActive.value == enabled) return
        _isProxyActive.value = enabled
        
        if (enabled) {
            enableProxyOverride()
        } else {
            disableProxyOverride()
        }
    }

    private fun enableProxyOverride() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                // Ensure correct mappings are in-memory
                val mappings = repository.getEnabledMappings()
                activeMappings = mappings.associate { it.hostname.lowercase().trim() to it.ipAddress.trim() }

                proxyServer.stop()
                proxyServer.start()

                // Wait for bind
                var attempts = 0
                while (proxyServer.port == 0 && attempts < 20) {
                    delay(50)
                    attempts++
                }

                val actualPort = proxyServer.port
                if (actualPort > 0) {
                    withContext(Dispatchers.Main) {
                        if (WebViewFeature.isFeatureSupported(WebViewFeature.PROXY_OVERRIDE)) {
                            val proxyConfig = ProxyConfig.Builder()
                                .addProxyRule("127.0.0.1:$actualPort")
                                .build()
                            ProxyController.getInstance().setProxyOverride(
                                proxyConfig,
                                { executor -> executor.run() },
                                {
                                    addLog("✔️ WebView Proxy Override ACTIVE on 127.0.0.1:$actualPort")
                                    // Load the start URL through the reliable newly initialized proxy pathway to prevent empty black startup pages
                                    loadUrl(_currentUrl.value)
                                }
                            )
                        } else {
                            addLog("❌ API Error: PROXY_OVERRIDE is unsupported on this system.")
                        }
                    }
                } else {
                    addLog("❌ Failed to bind local proxy server to a port.")
                }
            } catch (e: Exception) {
                addLog("💥 Error configuring local proxy: ${e.localizedMessage}")
            }
        }
    }

    private fun disableProxyOverride() {
        viewModelScope.launch(Dispatchers.IO) {
            proxyServer.stop()
            withContext(Dispatchers.Main) {
                if (WebViewFeature.isFeatureSupported(WebViewFeature.PROXY_OVERRIDE)) {
                    ProxyController.getInstance().clearProxyOverride(
                        { executor -> executor.run() },
                        {
                            addLog("🚫 WebView Proxy Override INACTIVE (NATIVE)")
                        }
                    )
                } else {
                    addLog("🚫 WebView Proxy Override stopped (local proxy closed)")
                }
            }
        }
    }

    private suspend fun restartProxyWithUpdatedMappings() {
        if (_isProxyActive.value) {
            // Hot reload local mappings, no need to tear down proxy override since port is the same!
            // But we do log the update
            addLog("🔄 Synced ${activeMappings.size} active local DNS host rules")
        }
    }

    // Database access forwards
    fun insertMapping(hostname: String, ipAddress: String) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.insertMapping(HostMapping(hostname = hostname.trim(), ipAddress = ipAddress.trim()))
            addLog("➕ Added rule: $hostname ➔ $ipAddress")
        }
    }

    fun deleteMapping(mapping: HostMapping) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.deleteMapping(mapping)
            addLog("➖ Removed rule: ${mapping.hostname}")
        }
    }

    fun toggleMappingEnabled(mapping: HostMapping) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.updateMapping(mapping.copy(isEnabled = !mapping.isEnabled))
            addLog("🔄 Toggled rule: ${mapping.hostname} (${if (!mapping.isEnabled) "Enabled" else "Disabled"})")
        }
    }

    fun addBookmarkCurrent(title: String, url: String) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.addBookmark(Bookmark(title = title, url = url))
            addLog("⭐ Bookmarked: $title")
        }
    }

    fun deleteBookmark(bookmark: Bookmark) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.deleteBookmarkById(bookmark.id)
            addLog("🗑️ Removed bookmark: ${bookmark.title}")
        }
    }

    fun addHistoryEntry(title: String, url: String) {
        val activeTab = _tabs.value.find { it.id == _activeTabId.value }
        if (activeTab?.isPrivate == true) {
            // Private mode tab: completely bypass saving browsing history
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            repository.addHistory(BrowserHistory(title = title, url = url))
        }
    }

    fun deleteDownload(download: DownloadItem) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.deleteDownload(download)
            try {
                // Delete local file too helper
                val file = java.io.File(download.filePath)
                if (file.exists()) {
                    file.delete()
                }
            } catch(e: Exception){}
            addLog("🗑️ Deleted downloaded record: ${download.fileName}")
        }
    }

    fun startDownload(
        url: String,
        userAgent: String,
        contentDisposition: String,
        mimeType: String,
        contentLength: Long
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            var connection: java.net.HttpURLConnection? = null
            var inputStream: java.io.InputStream? = null
            var outputStream: java.io.FileOutputStream? = null
            
            // Try to resolve a reliable filename
            val guessedName = android.webkit.URLUtil.guessFileName(url, contentDisposition, mimeType)
            val finalFileName = if (guessedName.isNullOrBlank() || guessedName == "downloadfile") {
                val ext = when {
                    mimeType.contains("pdf", ignoreCase = true) -> ".pdf"
                    mimeType.contains("word", ignoreCase = true) || mimeType.contains("msword", ignoreCase = true) -> ".doc"
                    mimeType.contains("officedocument", ignoreCase = true) -> ".docx"
                    else -> ""
                }
                "document_${System.currentTimeMillis()}$ext"
            } else {
                guessedName
            }

            val targetDir = getApplication<Application>().getExternalFilesDir(android.os.Environment.DIRECTORY_DOWNLOADS)
                ?: getApplication<Application>().filesDir
            val localFile = java.io.File(targetDir, finalFileName)
            
            // Create initial DownloadItem row
            val downloadItem = DownloadItem(
                fileName = finalFileName,
                fileUrl = url,
                filePath = localFile.absolutePath,
                mimeType = mimeType,
                fileSize = if (contentLength > 0) contentLength else 0L,
                downloadStatus = "DOWNLOADING",
                timestamp = System.currentTimeMillis()
            )
            val dbId = repository.insertDownload(downloadItem).toInt()
            
            addLog("⏳ Download started: $finalFileName")

            try {
                if (url.startsWith("data:", ignoreCase = true)) {
                    val commaIndex = url.indexOf(',')
                    if (commaIndex != -1) {
                        val metadata = url.substring(0, commaIndex)
                        val dataPart = url.substring(commaIndex + 1)
                        val isBase64 = metadata.contains(";base64", ignoreCase = true)
                        val bytes = if (isBase64) {
                            android.util.Base64.decode(dataPart, android.util.Base64.DEFAULT)
                        } else {
                            java.net.URLDecoder.decode(dataPart, "UTF-8").toByteArray()
                        }
                        
                        val fos = java.io.FileOutputStream(localFile)
                        fos.write(bytes)
                        fos.flush()
                        fos.close()
                        
                        val completedItem = downloadItem.copy(
                            id = dbId,
                            fileSize = bytes.size.toLong(),
                            downloadStatus = "COMPLETED"
                        )
                        repository.updateDownload(completedItem)
                        addLog("✔️ Download complete: $finalFileName")
                        return@launch
                    }
                }

                val conn = if (_isProxyActive.value && proxyServer.port > 0) {
                    java.net.URL(url).openConnection(
                        java.net.Proxy(
                            java.net.Proxy.Type.HTTP,
                            java.net.InetSocketAddress("127.0.0.1", proxyServer.port)
                        )
                    ) as java.net.HttpURLConnection
                } else {
                    java.net.URL(url).openConnection() as java.net.HttpURLConnection
                }
                
                connection = conn
                conn.requestMethod = "GET"
                conn.connectTimeout = 15000
                conn.readTimeout = 15000
                
                // CRITICAL: Inject current session cookies to bypass portal auth login layers
                val cookie = android.webkit.CookieManager.getInstance().getCookie(url)
                if (!cookie.isNullOrEmpty()) {
                    conn.setRequestProperty("Cookie", cookie)
                }
                conn.setRequestProperty("User-Agent", userAgent.ifEmpty { "Mozilla/5.0" })
                
                conn.connect()
                
                val responseCode = conn.responseCode
                if (responseCode in 200..299) {
                    val length = if (conn.contentLengthLong > 0) conn.contentLengthLong else contentLength
                    inputStream = conn.inputStream
                    outputStream = java.io.FileOutputStream(localFile)
                    
                    val buffer = ByteArray(4096)
                    var bytesRead: Int
                    var totalRead = 0L
                    
                    while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                        outputStream.write(buffer, 0, bytesRead)
                        totalRead += bytesRead
                    }
                    outputStream.flush()
                    
                    // Update entry to COMPLETED
                    val completedItem = downloadItem.copy(
                        id = dbId,
                        fileSize = if (length > 0) length else totalRead,
                        downloadStatus = "COMPLETED"
                    )
                    repository.updateDownload(completedItem)
                    addLog("✔️ Download complete: $finalFileName")
                } else {
                    throw java.io.IOException("Server returned status core: $responseCode")
                }
            } catch (e: Exception) {
                Log.e("BrowserViewModel", "Download failed", e)
                val failedItem = downloadItem.copy(
                    id = dbId,
                    downloadStatus = "FAILED"
                )
                repository.updateDownload(failedItem)
                addLog("❌ Download failed for: $finalFileName. ${e.localizedMessage}")
                if (localFile.exists()) {
                    localFile.delete()
                }
            } finally {
                try { inputStream?.close() } catch(e: Exception){}
                try { outputStream?.close() } catch(e: Exception){}
                try { connection?.disconnect() } catch(e: Exception){}
            }
        }
    }

    fun saveBase64Download(
        base64Data: String,
        fileName: String,
        mimeType: String
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val cleanBase64 = if (base64Data.contains(",")) {
                    base64Data.substring(base64Data.indexOf(",") + 1)
                } else {
                    base64Data
                }
                
                val bytes = android.util.Base64.decode(cleanBase64, android.util.Base64.DEFAULT)
                
                val finalFileName = if (fileName.isNullOrBlank() || fileName == "downloadfile") {
                    val ext = when {
                        mimeType.contains("pdf", ignoreCase = true) -> ".pdf"
                        mimeType.contains("word", ignoreCase = true) || mimeType.contains("msword", ignoreCase = true) -> ".doc"
                        mimeType.contains("officedocument", ignoreCase = true) -> ".docx"
                        else -> ""
                    }
                    "document_${System.currentTimeMillis()}$ext"
                } else {
                    fileName
                }

                val targetDir = getApplication<Application>().getExternalFilesDir(android.os.Environment.DIRECTORY_DOWNLOADS)
                    ?: getApplication<Application>().filesDir
                val localFile = java.io.File(targetDir, finalFileName)
                
                java.io.FileOutputStream(localFile).use { out ->
                    out.write(bytes)
                    out.flush()
                }

                val downloadItem = DownloadItem(
                    fileName = finalFileName,
                    fileUrl = "base64://data",
                    filePath = localFile.absolutePath,
                    mimeType = mimeType.ifEmpty { "application/pdf" },
                    fileSize = bytes.size.toLong(),
                    downloadStatus = "COMPLETED",
                    timestamp = System.currentTimeMillis()
                )
                repository.insertDownload(downloadItem)
                addLog("✔️ Download complete: $finalFileName")
            } catch (e: Exception) {
                Log.e("BrowserViewModel", "JS download failed", e)
                addLog("❌ JS Download failed: ${e.localizedMessage}")
            }
        }
    }

    fun clearBrowsingHistory() {
        viewModelScope.launch(Dispatchers.IO) {
            repository.clearHistory()
            addLog("🧹 Cleared browsing history")
        }
    }

    fun clearSelectiveEOfficeBrowsingData(webView: android.webkit.WebView?) {
        viewModelScope.launch(Dispatchers.Main) {
            try {
                // 1. Clear general WebView Cache to ensure absolute 0-Cache (HTTP files, stylesheets, images, cached scripts)
                webView?.clearCache(true)

                val domains = listOf(
                    "districts.upeoffice.gov.in",
                    "upeoffice.gov.in",
                    ".upeoffice.gov.in",
                    "parichay.nic.in",
                    ".parichay.nic.in",
                    "services.parichay.nic.in",
                    "janparichay.nic.in",
                    "sso.nic.in"
                )

                // 1. If webview is currently on any e-office / parichay page, execute Javascript to clear localStorage/sessionStorage
                webView?.let { wv ->
                    val currentUrl = wv.url
                    if (!currentUrl.isNullOrEmpty()) {
                        val isEOfficeUrl = domains.any { domain -> 
                            currentUrl.contains(domain, ignoreCase = true)
                        }
                        if (isEOfficeUrl) {
                            wv.evaluateJavascript(
                                "(function() { " +
                                "   try { " +
                                "       window.localStorage.clear(); " +
                                "       window.sessionStorage.clear(); " +
                                "       if (window.indexedDB && window.indexedDB.databases) { " +
                                "           window.indexedDB.databases().then(dbs => { " +
                                "               dbs.forEach(db => window.indexedDB.deleteDatabase(db.name)); " +
                                "           }); " +
                                "       } " +
                                "   } catch(e) {} " +
                                "})();", null
                            )
                            // Redirect to blank page to secure the session
                            wv.loadUrl("about:blank")
                        }
                    }
                }

                // 2. Clear Cookies for the specific domains (including subdomains and wildcards)
                val cookieManager = android.webkit.CookieManager.getInstance()
                for (domain in domains) {
                    val url1 = "https://$domain"
                    val url2 = "http://$domain"
                    for (url in listOf(url1, url2)) {
                        val cookies = cookieManager.getCookie(url)
                        if (!cookies.isNullOrEmpty()) {
                            val cookiePairs = cookies.split(";")
                            for (cookiePair in cookiePairs) {
                                val parts = cookiePair.split("=")
                                if (parts.isNotEmpty()) {
                                    val cookieName = parts[0].trim()
                                    
                                    // Expire cookie without domain
                                    cookieManager.setCookie(url, "$cookieName=; Path=/; Expires=Thu, 01 Jan 1970 00:00:00 GMT")
                                    // Expire cookie with explicit domain
                                    cookieManager.setCookie(url, "$cookieName=; Path=/; Domain=$domain; Expires=Thu, 01 Jan 1970 00:00:00 GMT")
                                    // Expire cookie with wildcard domain (essential for wildcard matches)
                                    val cleanDomain = if (domain.startsWith(".")) domain else ".$domain"
                                    cookieManager.setCookie(url, "$cookieName=; Path=/; Domain=$cleanDomain; Expires=Thu, 01 Jan 1970 00:00:00 GMT")
                                    
                                    // Expire cookie with Secure attribute
                                    cookieManager.setCookie(url, "$cookieName=; Path=/; Secure; Expires=Thu, 01 Jan 1970 00:00:00 GMT")
                                    cookieManager.setCookie(url, "$cookieName=; Path=/; Domain=$domain; Secure; Expires=Thu, 01 Jan 1970 00:00:00 GMT")
                                    cookieManager.setCookie(url, "$cookieName=; Path=/; Domain=$cleanDomain; Secure; Expires=Thu, 01 Jan 1970 00:00:00 GMT")
                                }
                            }
                        }
                    }
                }
                cookieManager.flush()

                // 3. Clear HTML5 WebStorage & Databases (localStorage, sessionStorage, IndexedDB) for these origins
                val webStorage = android.webkit.WebStorage.getInstance()
                val origins = listOf(
                    "https://districts.upeoffice.gov.in",
                    "http://districts.upeoffice.gov.in",
                    "https://upeoffice.gov.in",
                    "http://upeoffice.gov.in",
                    "https://parichay.nic.in",
                    "http://parichay.nic.in",
                    "https://services.parichay.nic.in",
                    "http://services.parichay.nic.in",
                    "https://janparichay.nic.in",
                    "http://janparichay.nic.in",
                    "https://sso.nic.in",
                    "http://sso.nic.in"
                )
                for (origin in origins) {
                    webStorage.deleteOrigin(origin)
                }

                // 4. Clear matching history from local database
                withContext(Dispatchers.IO) {
                    repository.deleteHistoryMatching("%districts.upeoffice.gov.in%")
                    repository.deleteHistoryMatching("%upeoffice.gov.in%")
                    repository.deleteHistoryMatching("%parichay.nic.in%")
                    repository.deleteHistoryMatching("%services.parichay.nic.in%")
                    repository.deleteHistoryMatching("%janparichay.nic.in%")
                    repository.deleteHistoryMatching("%sso.nic.in%")
                }

                addLog("🧹 Cleared selective eOffice & Parichay cookies, storage & history completely.")
            } catch (e: Exception) {
                addLog("❌ Error cleaning selective storage: ${e.localizedMessage}")
            }
        }
    }

    fun checkVpnStatus() {
        try {
            val connectivityManager = getApplication<Application>().getSystemService(android.content.Context.CONNECTIVITY_SERVICE) as? android.net.ConnectivityManager
            if (connectivityManager != null) {
                val isConnected = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                    val activeNetwork = connectivityManager.activeNetwork
                    val capabilities = connectivityManager.getNetworkCapabilities(activeNetwork)
                    capabilities?.hasTransport(android.net.NetworkCapabilities.TRANSPORT_VPN) ?: false
                } else {
                    val networks = connectivityManager.allNetworks
                    var vpnConnected = false
                    for (network in networks) {
                        val capabilities = connectivityManager.getNetworkCapabilities(network)
                        if (capabilities != null && capabilities.hasTransport(android.net.NetworkCapabilities.TRANSPORT_VPN)) {
                            vpnConnected = true
                            break
                        }
                    }
                    vpnConnected
                }

                val previousState = lastVpnState
                _isVpnConnected.value = isConnected
                lastVpnState = isConnected

                if (previousState != null && previousState != isConnected) {
                    viewModelScope.launch(Dispatchers.Main) {
                        if (isConnected) {
                            addLog("🟢 VPN Connected! Pre-warming UP e-Office & Parichay portals...")
                            prewarmGovernmentPortals()
                        } else {
                            addLog("🔴 VPN Disconnected. (VPN डिस्कनेक्ट)")
                        }
                    }
                }

                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                    val activeNetwork = connectivityManager.activeNetwork
                    val capabilities = connectivityManager.getNetworkCapabilities(activeNetwork)
                    if (capabilities != null) {
                        val isWifi = capabilities.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI)
                        val isCellular = capabilities.hasTransport(android.net.NetworkCapabilities.TRANSPORT_CELLULAR)
                        val dslSpeed = capabilities.linkDownstreamBandwidthKbps

                        if (isWifi) {
                            _internetType.value = "Wifi"
                            _networkSignalLevel.value = when {
                                dslSpeed > 80000 -> 4
                                dslSpeed > 40000 -> 3
                                dslSpeed > 10000 -> 2
                                else -> 1
                            }
                        } else if (isCellular) {
                            val isHighSpeed = dslSpeed > 50000
                            _internetType.value = if (isHighSpeed) "5G" else "4G"
                            _networkSignalLevel.value = when {
                                dslSpeed > 40000 -> 4
                                dslSpeed > 20000 -> 3
                                dslSpeed > 5000 -> 2
                                else -> 1
                            }
                        } else {
                            if (capabilities.hasTransport(android.net.NetworkCapabilities.TRANSPORT_ETHERNET)) {
                                _internetType.value = "Ethernet"
                                _networkSignalLevel.value = 4
                            } else {
                                _internetType.value = "None"
                                _networkSignalLevel.value = 0
                            }
                        }
                    } else {
                        _internetType.value = "None"
                        _networkSignalLevel.value = 0
                    }
                } else {
                    val networks = connectivityManager.allNetworks
                    var tempInternetType = "None"
                    var tempSignalLevel = 0
                    for (network in networks) {
                        val capabilities = connectivityManager.getNetworkCapabilities(network)
                        if (capabilities != null) {
                            if (capabilities.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI)) {
                                tempInternetType = "Wifi"
                                tempSignalLevel = 4
                            } else if (capabilities.hasTransport(android.net.NetworkCapabilities.TRANSPORT_CELLULAR)) {
                                tempInternetType = "4G"
                                tempSignalLevel = 3
                            }
                        }
                    }
                    _internetType.value = tempInternetType
                    _networkSignalLevel.value = tempSignalLevel
                }
            } else {
                _isVpnConnected.value = false
                lastVpnState = false
                _internetType.value = "None"
                _networkSignalLevel.value = 0
            }
        } catch (e: Exception) {
            Log.e("BrowserViewModel", "Error checking VPN status", e)
            _isVpnConnected.value = false
            lastVpnState = false
            _internetType.value = "None"
            _networkSignalLevel.value = 0
        }
    }

    fun openVpnSettings(context: android.content.Context) {
        try {
            val intent = android.content.Intent(android.provider.Settings.ACTION_VPN_SETTINGS)
            intent.flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK
            context.startActivity(intent)
            addLog("⚙️ Opened VPN Settings")
        } catch (e: Exception) {
            try {
                val intent = android.content.Intent(android.provider.Settings.ACTION_WIRELESS_SETTINGS)
                intent.flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK
                context.startActivity(intent)
                addLog("⚙️ Opened Wireless Settings")
            } catch (ex: Exception) {
                val intent = android.content.Intent(android.provider.Settings.ACTION_SETTINGS)
                intent.flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK
                context.startActivity(intent)
                addLog("⚙️ Opened System Settings")
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        proxyServer.stop()
    }
}
