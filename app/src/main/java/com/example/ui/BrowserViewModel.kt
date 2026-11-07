package com.example.ui

import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.BlockedRequestEvent
import com.example.data.model.BookmarkItem
import com.example.data.model.BrowserTab
import com.example.data.model.DownloadTask
import com.example.data.model.HistoryEntry
import com.example.data.model.SearchEngine
import com.example.data.model.ShortcutItem
import com.example.data.model.UrlBarPosition
import com.example.data.repository.BrowserPreferences
import com.example.data.repository.BrowserPreferencesRepository
import com.example.data.repository.BrowserSyncRepository
import com.example.engine.AiSearchUiState
import com.example.engine.ShieldBlockType
import com.example.engine.TurboDownloadEngine
import com.example.engine.VistaAiSearchEngine
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.UUID

sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>
}

class BrowserViewModel(
    private val syncRepository: BrowserSyncRepository,
    private val preferencesRepository: BrowserPreferencesRepository,
    private val downloadEngine: TurboDownloadEngine,
    private val aiSearchEngine: VistaAiSearchEngine,
    private val currentUserId: String
) : ViewModel() {

    val preferences: StateFlow<BrowserPreferences> = preferencesRepository.preferencesFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = BrowserPreferences()
        )

    val bookmarksState: StateFlow<UiState<List<BookmarkItem>>> = syncRepository
        .observeBookmarks(currentUserId)
        .map<List<BookmarkItem>, UiState<List<BookmarkItem>>> { UiState.Success(it) }
        .catch { error ->
            val firestoreError = generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (firestoreError == null && error is CancellationException) throw error
            val rootError = firestoreError ?: error
            Log.w(TAG, "Error observing bookmarks", rootError)
            emit(UiState.Error(rootError.message ?: "Failed to load synced bookmarks"))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = UiState.Loading
        )

    val shortcutsState: StateFlow<UiState<List<ShortcutItem>>> = syncRepository
        .observeShortcuts(currentUserId)
        .map<List<ShortcutItem>, UiState<List<ShortcutItem>>> { list ->
            UiState.Success(list)
        }
        .catch { error ->
            val firestoreError = generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (firestoreError == null && error is CancellationException) throw error
            val rootError = firestoreError ?: error
            Log.w(TAG, "Error observing shortcuts", rootError)
            emit(UiState.Error(rootError.message ?: "Failed to load homepage shortcuts"))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = UiState.Loading
        )

    val downloads: StateFlow<List<DownloadTask>> = downloadEngine.downloads

    private val initialTab = BrowserTab(
        id = UUID.randomUUID().toString(),
        title = "New Tab",
        url = "",
        isHome = true
    )

    private val _tabs = MutableStateFlow(listOf(initialTab))
    val tabs: StateFlow<List<BrowserTab>> = _tabs.asStateFlow()

    private val _activeTabId = MutableStateFlow(initialTab.id)
    val activeTabId: StateFlow<String> = _activeTabId.asStateFlow()

    private val _aiSearchState = MutableStateFlow<AiSearchUiState>(AiSearchUiState.Idle)
    val aiSearchState: StateFlow<AiSearchUiState> = _aiSearchState.asStateFlow()
    private var aiSearchJob: Job? = null

    private val _isFullScreen = MutableStateFlow(false)
    val isFullScreen: StateFlow<Boolean> = _isFullScreen.asStateFlow()

    private val _history = MutableStateFlow<List<HistoryEntry>>(emptyList())
    val history: StateFlow<List<HistoryEntry>> = _history.asStateFlow()

    private val _recentBlockedRequests = MutableStateFlow<List<BlockedRequestEvent>>(emptyList())
    val recentBlockedRequests: StateFlow<List<BlockedRequestEvent>> = _recentBlockedRequests.asStateFlow()

    // Memory telemetry
    private val _memoryUsedMb = MutableStateFlow(42L)
    val memoryUsedMb: StateFlow<Long> = _memoryUsedMb.asStateFlow()

    private val _memoryMaxMb = MutableStateFlow(256L)
    val memoryMaxMb: StateFlow<Long> = _memoryMaxMb.asStateFlow()

    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage: StateFlow<String?> = _statusMessage.asStateFlow()

    private val _webViewCommand = MutableStateFlow<WebViewCommand?>(null)
    val webViewCommand: StateFlow<WebViewCommand?> = _webViewCommand.asStateFlow()

    init {
        refreshMemoryStats()
        viewModelScope.launch {
            while (isActive) {
                delay(5000L)
                refreshMemoryStats()
            }
        }
    }

    fun showStatus(message: String) {
        _statusMessage.value = message
    }

    fun clearStatus() {
        _statusMessage.value = null
    }

    // --- Tab Management & Memory Hibernation ---

    fun createNewTab(url: String = "", isPrivate: Boolean = false, switchImmediately: Boolean = true) {
        val isHome = url.isBlank()
        val resolvedUrl = if (isHome) "" else BrowserSyncRepository.normalizeWebsiteUrl(url)
        val newTab = BrowserTab(
            id = UUID.randomUUID().toString(),
            url = resolvedUrl,
            title = if (isHome) {
                if (isPrivate) "Private Tab" else "New Tab"
            } else {
                BrowserSyncRepository.extractDomainTitle(resolvedUrl)
            },
            isHome = isHome,
            isAiSearchOverview = false,
            isPrivate = isPrivate
        )
        _tabs.update { currentTabs ->
            val currentActive = _activeTabId.value
            val updated = if (preferences.value.memorySaverEnabled && switchImmediately) {
                currentTabs.map { tab ->
                    if (tab.id == currentActive && !tab.isHome) {
                        tab.copy(isSuspendedForMemory = true)
                    } else {
                        tab
                    }
                }
            } else {
                currentTabs
            }
            updated + newTab
        }
        if (switchImmediately) {
            _activeTabId.value = newTab.id
        }
        refreshMemoryStats()
    }

    fun selectTab(tabId: String) {
        val previousId = _activeTabId.value
        if (previousId == tabId) return
        _activeTabId.value = tabId
        _tabs.update { list ->
            list.map { tab ->
                when {
                    tab.id == tabId -> tab.copy(
                        isSuspendedForMemory = false,
                        lastActiveTimestamp = System.currentTimeMillis()
                    )
                    tab.id == previousId && preferences.value.memorySaverEnabled && !tab.isHome ->
                        tab.copy(isSuspendedForMemory = true)
                    else -> tab
                }
            }
        }
        refreshMemoryStats()
    }

    fun closeTab(tabId: String) {
        val currentList = _tabs.value
        if (currentList.size <= 1) {
            val resetTab = BrowserTab(
                id = UUID.randomUUID().toString(),
                title = "New Tab",
                url = "",
                isHome = true,
                isAiSearchOverview = false
            )
            _tabs.value = listOf(resetTab)
            _activeTabId.value = resetTab.id
            return
        }
        val index = currentList.indexOfFirst { it.id == tabId }
        val filtered = currentList.filterNot { it.id == tabId }
        _tabs.value = filtered
        if (_activeTabId.value == tabId) {
            val nextTab = filtered.getOrElse((index - 1).coerceAtLeast(0)) { filtered.first() }
            _activeTabId.value = nextTab.copy(isSuspendedForMemory = false).id
        }
        refreshMemoryStats()
    }

    fun closeAllTabs() {
        val freshTab = BrowserTab(
            id = UUID.randomUUID().toString(),
            title = "New Tab",
            url = "",
            isHome = true,
            isAiSearchOverview = false
        )
        _tabs.value = listOf(freshTab)
        _activeTabId.value = freshTab.id
        refreshMemoryStats()
        showStatus("Closed all tabs & reclaimed WebView memory")
    }

    /**
     * Exact routing requested by user:
     * 1. If the user types any valid URL (e.g. youtube.com, https://github.com), directly redirect to that website.
     * 2. Otherwise, directly open the web search (Brave Search by default) in the browser WebView without any intermediate screen.
     */
    fun loadUrlOrSearchInActiveTab(rawInput: String) {
        val trimmed = rawInput.trim()
        if (trimmed.isEmpty()) return

        val isDirectUrl = trimmed.equals("aegis://shield-test", ignoreCase = true) ||
            trimmed.equals("vista://shield-test", ignoreCase = true) ||
            trimmed.startsWith("http://", ignoreCase = true) ||
            trimmed.startsWith("https://", ignoreCase = true) ||
            BrowserSyncRepository.isValidWebsiteUrl(trimmed)

        if (isDirectUrl) {
            openDirectUrlInActiveTab(
                if (trimmed.equals("aegis://shield-test", ignoreCase = true) ||
                    trimmed.equals("vista://shield-test", ignoreCase = true)
                ) {
                    "aegis://shield-test"
                } else {
                    BrowserSyncRepository.normalizeWebsiteUrl(trimmed)
                }
            )
        } else {
            val searchUrl = preferences.value.searchEngine.queryUrlPrefix + Uri.encode(trimmed)
            openDirectUrlInActiveTab(searchUrl)
        }
    }

    fun openDirectUrlInActiveTab(targetUrl: String) {
        val currentId = _activeTabId.value
        _tabs.update { list ->
            list.map { tab ->
                if (tab.id == currentId) {
                    tab.copy(
                        url = targetUrl,
                        title = if (targetUrl == "aegis://shield-test") "Shield Test Lab" else BrowserSyncRepository.extractDomainTitle(targetUrl),
                        isHome = false,
                        isAiSearchOverview = false,
                        isSuspendedForMemory = false,
                        isLoading = true,
                        progress = 15,
                        lastActiveTimestamp = System.currentTimeMillis()
                    )
                } else {
                    tab
                }
            }
        }
    }

    fun triggerAiSearchOverviewInActiveTab(query: String) {
        loadUrlOrSearchInActiveTab(query)
    }

    fun openHomeInActiveTab() {
        val currentId = _activeTabId.value
        _tabs.update { list ->
            list.map { tab ->
                if (tab.id == currentId) {
                    tab.copy(
                        isHome = true,
                        isAiSearchOverview = false,
                        url = "",
                        title = if (tab.isPrivate) "Private Tab" else "New Tab",
                        isLoading = false,
                        progress = 0
                    )
                } else {
                    tab
                }
            }
        }
    }

    fun updateActiveTabPageState(
        tabId: String,
        url: String? = null,
        title: String? = null,
        isLoading: Boolean? = null,
        progress: Int? = null,
        canGoBack: Boolean? = null,
        canGoForward: Boolean? = null
    ) {
        _tabs.update { list ->
            list.map { tab ->
                if (tab.id == tabId) {
                    val updatedUrl = url ?: tab.url
                    val updatedTitle = title?.takeIf { it.isNotBlank() } ?: tab.title
                    if (!tab.isPrivate && isLoading == false && updatedUrl.startsWith("http")) {
                        recordHistory(updatedTitle, updatedUrl)
                    }
                    tab.copy(
                        url = updatedUrl,
                        title = updatedTitle,
                        isLoading = isLoading ?: tab.isLoading,
                        progress = progress ?: tab.progress,
                        canGoBack = canGoBack ?: tab.canGoBack,
                        canGoForward = canGoForward ?: tab.canGoForward
                    )
                } else {
                    tab
                }
            }
        }
    }

    private fun recordHistory(title: String, url: String) {
        _history.update { current ->
            if (current.firstOrNull()?.url == url) {
                current
            } else {
                (listOf(HistoryEntry(UUID.randomUUID().toString(), title, url)) + current).take(100)
            }
        }
    }

    fun clearBrowsingHistory() {
        _history.value = emptyList()
        issueWebViewCommand(WebViewCommand.ClearBrowsingData)
        showStatus("Browsing history, cache & cookies cleared")
    }

    fun toggleDesktopSiteInActiveTab() {
        val currentId = _activeTabId.value
        var newMode = false
        _tabs.update { list ->
            list.map { tab ->
                if (tab.id == currentId) {
                    newMode = !tab.isDesktopMode
                    tab.copy(isDesktopMode = newMode)
                } else {
                    tab
                }
            }
        }
        issueWebViewCommand(WebViewCommand.Reload)
        showStatus(if (newMode) "Desktop Site enabled" else "Mobile Site enabled")
    }

    fun setFullScreenMode(enabled: Boolean) {
        _isFullScreen.value = enabled
        if (enabled) {
            showStatus("Full-Screen Mode active • Tap floating pill to restore bars")
        }
    }

    fun issueWebViewCommand(command: WebViewCommand) {
        _webViewCommand.value = command
    }

    fun consumeWebViewCommand() {
        _webViewCommand.value = null
    }

    // --- Ad & Video Ad Blocker Telemetry ---

    fun onShieldBlockedItem(
        tabId: String,
        blockType: ShieldBlockType,
        blockedUrl: String = ""
    ) {
        if (blockType == ShieldBlockType.NONE) return
        val adsDelta = if (blockType == ShieldBlockType.DISPLAY_AD || blockType == ShieldBlockType.VIDEO_AD) 1 else 0
        val videoDelta = if (blockType == ShieldBlockType.VIDEO_AD) 1 else 0
        val trackerDelta = if (blockType == ShieldBlockType.TRACKER) 1 else 0
        val savedBytes = when (blockType) {
            ShieldBlockType.VIDEO_AD -> 420_000L
            ShieldBlockType.DISPLAY_AD -> 45_000L
            ShieldBlockType.TRACKER -> 12_000L
            ShieldBlockType.NONE -> 0L
        }

        val domainOrRule = try {
            Uri.parse(blockedUrl).host?.removePrefix("www.")?.takeIf { it.isNotBlank() }
                ?: blockedUrl.take(48)
        } catch (e: Exception) {
            blockedUrl.take(48)
        }.ifBlank { blockType.label }

        _recentBlockedRequests.update { current ->
            val event = BlockedRequestEvent(
                id = UUID.randomUUID().toString(),
                domainOrRule = domainOrRule,
                fullUrl = blockedUrl,
                category = blockType.label
            )
            (listOf(event) + current).take(40)
        }

        _tabs.update { list ->
            list.map { tab ->
                if (tab.id == tabId) {
                    tab.copy(
                        adsBlockedInTab = tab.adsBlockedInTab + adsDelta,
                        videoAdsBlockedInTab = tab.videoAdsBlockedInTab + videoDelta,
                        trackersBlockedInTab = tab.trackersBlockedInTab + trackerDelta,
                        dataSavedBytes = tab.dataSavedBytes + savedBytes
                    )
                } else {
                    tab
                }
            }
        }

        viewModelScope.launch {
            preferencesRepository.recordShieldInterception(
                adsDelta = adsDelta,
                videoAdsDelta = videoDelta,
                trackersDelta = trackerDelta,
                bytesSavedDelta = savedBytes
            )
        }
    }

    // --- Cloud Synced Bookmarks & Homepage Shortcuts ---

    fun addBookmark(title: String, url: String, folder: String = "General") {
        viewModelScope.launch {
            val result = syncRepository.addBookmark(title = title, url = url, folder = folder)
            if (result.isSuccess) {
                showStatus("Bookmark synced to cloud ($folder)")
            } else {
                showStatus("Failed to sync bookmark: ${result.exceptionOrNull()?.localizedMessage ?: "Error"}")
            }
        }
    }

    fun deleteBookmark(bookmarkId: String) {
        viewModelScope.launch {
            syncRepository.deleteBookmark(bookmarkId)
            showStatus("Bookmark removed from cloud sync")
        }
    }

    fun addWebsiteShortcut(name: String, url: String, isPinned: Boolean = true) {
        viewModelScope.launch {
            val result = syncRepository.addShortcut(name = name, url = url, isPinned = isPinned)
            if (result.isSuccess) {
                showStatus("Shortcut \"${name.ifBlank { BrowserSyncRepository.extractDomainTitle(url) }}\" added to Homepage")
            } else {
                showStatus("Could not save shortcut: ${result.exceptionOrNull()?.localizedMessage ?: "Error"}")
            }
        }
    }

    fun deleteWebsiteShortcut(shortcutId: String) {
        viewModelScope.launch {
            syncRepository.deleteShortcut(shortcutId)
            showStatus("Homepage shortcut removed")
        }
    }

    // --- Turbo Multi-Thread Downloads ---

    fun startTurboDownload(url: String, suggestedFileName: String? = null) {
        val threads = preferences.value.downloadThreads
        downloadEngine.enqueueDownload(
            url = url,
            suggestedFileName = suggestedFileName,
            threadCount = threads
        )
        showStatus("Turbo Download started ($threads parallel streams)")
    }

    fun removeDownload(downloadId: String) {
        downloadEngine.cancelOrRemoveDownload(downloadId)
    }

    // --- Memory Efficiency & Customization ---

    fun refreshMemoryStats() {
        val runtime = Runtime.getRuntime()
        val used = (runtime.totalMemory() - runtime.freeMemory()) / (1024L * 1024L)
        val max = (runtime.maxMemory() / (1024L * 1024L)).coerceAtLeast(128L)
        _memoryUsedMb.value = used.coerceAtLeast(18L)
        _memoryMaxMb.value = max
    }

    fun optimizeMemoryNow() {
        val activeId = _activeTabId.value
        var hibernatedCount = 0
        _tabs.update { list ->
            list.map { tab ->
                if (tab.id != activeId && !tab.isHome && !tab.isSuspendedForMemory) {
                    hibernatedCount++
                    tab.copy(isSuspendedForMemory = true)
                } else {
                    tab
                }
            }
        }
        issueWebViewCommand(WebViewCommand.TrimMemory)
        System.gc()
        refreshMemoryStats()
        showStatus("RAM Optimized • Hibernated $hibernatedCount background tab(s)")
    }

    fun setDarkTheme(isDark: Boolean) {
        viewModelScope.launch { preferencesRepository.setDarkTheme(isDark) }
    }

    fun setAdBlockEnabled(enabled: Boolean) {
        viewModelScope.launch {
            preferencesRepository.setAdBlockEnabled(enabled)
            issueWebViewCommand(WebViewCommand.Reload)
        }
    }

    fun setVideoAdSkipperEnabled(enabled: Boolean) {
        viewModelScope.launch {
            preferencesRepository.setVideoAdSkipperEnabled(enabled)
            issueWebViewCommand(WebViewCommand.Reload)
        }
    }

    fun setTrackerBlockEnabled(enabled: Boolean) {
        viewModelScope.launch {
            preferencesRepository.setTrackerBlockEnabled(enabled)
            issueWebViewCommand(WebViewCommand.Reload)
        }
    }

    fun setForceDarkWebPages(enabled: Boolean) {
        viewModelScope.launch {
            preferencesRepository.setForceDarkWebPages(enabled)
            issueWebViewCommand(WebViewCommand.Reload)
        }
    }

    fun setMemorySaverEnabled(enabled: Boolean) {
        viewModelScope.launch { preferencesRepository.setMemorySaverEnabled(enabled) }
    }

    fun setDownloadThreads(threads: Int) {
        viewModelScope.launch { preferencesRepository.setDownloadThreads(threads) }
    }

    fun setSearchEngine(engine: SearchEngine) {
        viewModelScope.launch { preferencesRepository.setSearchEngine(engine) }
    }

    fun setUrlBarPosition(position: UrlBarPosition) {
        viewModelScope.launch { preferencesRepository.setUrlBarPosition(position) }
    }

    fun setCompactSpeedDial(compact: Boolean) {
        viewModelScope.launch { preferencesRepository.setCompactSpeedDial(compact) }
    }

    private companion object {
        const val TAG = "BrowserViewModel"
    }
}

sealed interface WebViewCommand {
    data object GoBack : WebViewCommand
    data object GoForward : WebViewCommand
    data object Reload : WebViewCommand
    data object ClearBrowsingData : WebViewCommand
    data object TrimMemory : WebViewCommand
    data class FindInPage(val query: String) : WebViewCommand
    data object FindNext : WebViewCommand
    data object ClearFind : WebViewCommand
}
