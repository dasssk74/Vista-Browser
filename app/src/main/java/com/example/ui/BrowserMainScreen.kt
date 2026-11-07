package com.example.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.AddToHomeScreen
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FindInPage
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.credentials.CredentialManager
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.data.model.BrowserTab
import com.example.data.model.UrlBarPosition
import com.example.data.repository.BrowserSyncRepository
import com.example.ui.auth.signOutUser
import com.example.ui.components.AddWebsiteShortcutDialog
import com.example.ui.components.AegisWebViewContainer
import com.example.ui.components.CloudBookmarksBottomSheet
import com.example.ui.components.CustomizationSettingsBottomSheet
import com.example.ui.components.HomepageSpeedDial
import com.example.ui.components.TabsManagerBottomSheet
import com.example.ui.components.TurboDownloadsBottomSheet
import com.example.ui.components.VistaAdBlockerShieldBottomSheet
import com.example.ui.components.VistaSearchOverviewScreen
import com.example.ui.components.pinWebsiteShortcutToAndroidHomeScreen

private enum class ActiveBottomSheet {
    NONE,
    TABS,
    BOOKMARKS,
    SHIELD,
    DOWNLOADS,
    SETTINGS
}

@Composable
fun BrowserMainScreen(
    viewModel: BrowserViewModel,
    onToggleSystemFullScreen: (Boolean) -> Unit
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val searchFocusRequester = remember { FocusRequester() }
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    val preferences by viewModel.preferences.collectAsStateWithLifecycle()
    val tabs by viewModel.tabs.collectAsStateWithLifecycle()
    val activeTabId by viewModel.activeTabId.collectAsStateWithLifecycle()
    val aiSearchState by viewModel.aiSearchState.collectAsStateWithLifecycle()
    val isFullScreen by viewModel.isFullScreen.collectAsStateWithLifecycle()
    val bookmarksState by viewModel.bookmarksState.collectAsStateWithLifecycle()
    val shortcutsState by viewModel.shortcutsState.collectAsStateWithLifecycle()
    val recentBlockedRequests by viewModel.recentBlockedRequests.collectAsStateWithLifecycle()
    val downloads by viewModel.downloads.collectAsStateWithLifecycle()
    val history by viewModel.history.collectAsStateWithLifecycle()
    val memoryUsedMb by viewModel.memoryUsedMb.collectAsStateWithLifecycle()
    val memoryMaxMb by viewModel.memoryMaxMb.collectAsStateWithLifecycle()
    val statusMessage by viewModel.statusMessage.collectAsStateWithLifecycle()
    val webViewCommand by viewModel.webViewCommand.collectAsStateWithLifecycle()

    val activeTab = remember(tabs, activeTabId) {
        tabs.firstOrNull { it.id == activeTabId } ?: tabs.first()
    }

    var addressBarText by remember(activeTab.id, activeTab.url, activeTab.isHome, activeTab.isAiSearchOverview) {
        mutableStateOf(
            when {
                activeTab.isHome -> ""
                activeTab.isAiSearchOverview -> "google.com/search"
                else -> activeTab.url
            }
        )
    }

    var activeSheet by remember { mutableStateOf(ActiveBottomSheet.NONE) }
    var showOverflowMenu by remember { mutableStateOf(false) }
    var showTopOverflowMenu by remember { mutableStateOf(false) }
    var showInstallShortcutDialog by remember { mutableStateOf(false) }
    var showFindInPageBar by remember { mutableStateOf(false) }
    var findQuery by remember { mutableStateOf("") }

    val isCurrentUrlBookmarked = remember(bookmarksState, activeTab.url) {
        val list = (bookmarksState as? UiState.Success)?.data ?: emptyList()
        !activeTab.isHome && !activeTab.isAiSearchOverview && activeTab.url.isNotBlank() && list.any {
            BrowserSyncRepository.normalizeWebsiteUrl(it.url) == BrowserSyncRepository.normalizeWebsiteUrl(activeTab.url)
        }
    }

    LaunchedEffect(isFullScreen) {
        onToggleSystemFullScreen(isFullScreen)
    }

    LaunchedEffect(statusMessage) {
        val msg = statusMessage ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(msg)
        viewModel.clearStatus()
    }

    BackHandler(enabled = isFullScreen || showFindInPageBar || !activeTab.isHome) {
        when {
            isFullScreen -> viewModel.setFullScreenMode(false)
            showFindInPageBar -> {
                showFindInPageBar = false
                viewModel.issueWebViewCommand(WebViewCommand.ClearFind)
            }
            !activeTab.isAiSearchOverview && activeTab.canGoBack ->
                viewModel.issueWebViewCommand(WebViewCommand.GoBack)
            !activeTab.isHome -> viewModel.openHomeInActiveTab()
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
            AnimatedVisibility(visible = !isFullScreen) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surface)
                        .statusBarsPadding()
                ) {
                    if (preferences.urlBarPosition == UrlBarPosition.TOP) {
                        AnimatedContent(
                            targetState = activeTab.isHome,
                            transitionSpec = { fadeIn() togetherWith fadeOut() },
                            label = "topBarModeTransition"
                        ) { isHome ->
                            if (isHome) {
                                // Homepage Omnibox Bar (matches Screenshot 1: "Search Vista or type URL" + Shield + Triangle)
                                VistaHomepageOmniboxBar(
                                    addressText = addressBarText,
                                    onAddressTextChange = { addressBarText = it },
                                    onSubmitAddress = {
                                        focusManager.clearFocus()
                                        viewModel.loadUrlOrSearchInActiveTab(addressBarText)
                                    },
                                    focusRequester = searchFocusRequester,
                                    onShieldIconClick = { activeSheet = ActiveBottomSheet.SHIELD },
                                    onVistaTriangleIconClick = {
                                        if (addressBarText.isNotBlank()) {
                                            focusManager.clearFocus()
                                            viewModel.triggerAiSearchOverviewInActiveTab(addressBarText)
                                        } else {
                                            viewModel.setDarkTheme(!preferences.isDarkTheme)
                                        }
                                    }
                                )
                            } else {
                                // Chrome/Search Top Address Bar (matches Screenshot 2: Home, "google.com/search" pill, +, [3], ⋮)
                                VistaBrowsingTopBar(
                                    addressText = addressBarText,
                                    onAddressTextChange = { addressBarText = it },
                                    onSubmitAddress = {
                                        focusManager.clearFocus()
                                        viewModel.loadUrlOrSearchInActiveTab(addressBarText)
                                    },
                                    focusRequester = searchFocusRequester,
                                    tabCount = tabs.size,
                                    activeTab = activeTab,
                                    isDarkTheme = preferences.isDarkTheme,
                                    adShieldActive = preferences.adBlockEnabled || preferences.videoAdSkipperEnabled,
                                    showOverflowMenu = showTopOverflowMenu,
                                    onToggleOverflowMenu = { showTopOverflowMenu = it },
                                    onHomeClick = { viewModel.openHomeInActiveTab() },
                                    onNewTabClick = { viewModel.createNewTab(isPrivate = false) },
                                    onTabsClick = { activeSheet = ActiveBottomSheet.TABS },
                                    onShieldClick = { activeSheet = ActiveBottomSheet.SHIELD },
                                    onToggleDesktopSite = { viewModel.toggleDesktopSiteInActiveTab() },
                                    onEnterFullScreen = { viewModel.setFullScreenMode(true) },
                                    onToggleTheme = { viewModel.setDarkTheme(!preferences.isDarkTheme) },
                                    onMenuInstallShortcut = {
                                        showTopOverflowMenu = false
                                        showInstallShortcutDialog = true
                                    },
                                    onMenuBookmarkPage = {
                                        showTopOverflowMenu = false
                                        if (!activeTab.isHome && !activeTab.isAiSearchOverview && activeTab.url.isNotBlank()) {
                                            viewModel.addBookmark(activeTab.title, activeTab.url)
                                        } else {
                                            activeSheet = ActiveBottomSheet.BOOKMARKS
                                        }
                                    },
                                    onMenuFindInPage = {
                                        showTopOverflowMenu = false
                                        showFindInPageBar = true
                                    },
                                    onMenuReload = {
                                        showTopOverflowMenu = false
                                        if (activeTab.isAiSearchOverview) {
                                            viewModel.triggerAiSearchOverviewInActiveTab(activeTab.searchQuery)
                                        } else {
                                            viewModel.issueWebViewCommand(WebViewCommand.Reload)
                                        }
                                    },
                                    onMenuOpenShield = {
                                        showTopOverflowMenu = false
                                        activeSheet = ActiveBottomSheet.SHIELD
                                    },
                                    onMenuOpenDownloads = {
                                        showTopOverflowMenu = false
                                        activeSheet = ActiveBottomSheet.DOWNLOADS
                                    },
                                    onMenuOpenSettings = {
                                        showTopOverflowMenu = false
                                        activeSheet = ActiveBottomSheet.SETTINGS
                                    }
                                )
                            }
                        }
                    }

                    if (showFindInPageBar) {
                        FindInPageToolbar(
                            query = findQuery,
                            onQueryChange = {
                                findQuery = it
                                viewModel.issueWebViewCommand(WebViewCommand.FindInPage(it))
                            },
                            onFindNext = { viewModel.issueWebViewCommand(WebViewCommand.FindNext) },
                            onClose = {
                                showFindInPageBar = false
                                findQuery = ""
                                viewModel.issueWebViewCommand(WebViewCommand.ClearFind)
                            }
                        )
                    }

                    if (activeTab.isLoading && !activeTab.isHome && !activeTab.isAiSearchOverview) {
                        LinearProgressIndicator(
                            progress = { (activeTab.progress / 100f).coerceIn(0.05f, 1f) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(2.5.dp),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        },
        bottomBar = {
            AnimatedVisibility(visible = !isFullScreen && (activeTab.isHome || preferences.urlBarPosition == UrlBarPosition.BOTTOM)) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surface)
                        .navigationBarsPadding()
                ) {
                    if (preferences.urlBarPosition == UrlBarPosition.BOTTOM) {
                        VistaHomepageOmniboxBar(
                            addressText = addressBarText,
                            onAddressTextChange = { addressBarText = it },
                            onSubmitAddress = {
                                focusManager.clearFocus()
                                viewModel.loadUrlOrSearchInActiveTab(addressBarText)
                            },
                            focusRequester = searchFocusRequester,
                            onShieldIconClick = { activeSheet = ActiveBottomSheet.SHIELD },
                            onVistaTriangleIconClick = {
                                if (addressBarText.isNotBlank()) {
                                    focusManager.clearFocus()
                                    viewModel.triggerAiSearchOverviewInActiveTab(addressBarText)
                                } else {
                                    viewModel.setDarkTheme(!preferences.isDarkTheme)
                                }
                            }
                        )
                    }

                    // Bottom 5-icon bar matching Screenshot 1: Home, Bookmark, Search, [1] Tabs, 3-Dot Menu
                    VistaBottomNavigationBar(
                        tabCount = tabs.size,
                        isBookmarked = isCurrentUrlBookmarked,
                        showOverflowMenu = showOverflowMenu,
                        onToggleOverflowMenu = { showOverflowMenu = it },
                        activeTab = activeTab,
                        isDarkTheme = preferences.isDarkTheme,
                        adShieldActive = preferences.adBlockEnabled || preferences.videoAdSkipperEnabled,
                        onHomeClick = { viewModel.openHomeInActiveTab() },
                        onBookmarksClick = { activeSheet = ActiveBottomSheet.BOOKMARKS },
                        onSearchClick = {
                            if (addressBarText.isNotBlank() && !activeTab.isAiSearchOverview && addressBarText != "google.com/search") {
                                focusManager.clearFocus()
                                viewModel.triggerAiSearchOverviewInActiveTab(addressBarText)
                            } else {
                                try {
                                    searchFocusRequester.requestFocus()
                                } catch (e: Exception) {
                                    // Ignore if not attached
                                }
                            }
                        },
                        onTabsClick = { activeSheet = ActiveBottomSheet.TABS },
                        onToggleDesktopSite = { viewModel.toggleDesktopSiteInActiveTab() },
                        onEnterFullScreen = { viewModel.setFullScreenMode(true) },
                        onToggleTheme = { viewModel.setDarkTheme(!preferences.isDarkTheme) },
                        onMenuInstallShortcut = {
                            showOverflowMenu = false
                            showInstallShortcutDialog = true
                        },
                        onMenuBookmarkPage = {
                            showOverflowMenu = false
                            if (!activeTab.isHome && !activeTab.isAiSearchOverview && activeTab.url.isNotBlank()) {
                                viewModel.addBookmark(activeTab.title, activeTab.url)
                            } else {
                                activeSheet = ActiveBottomSheet.BOOKMARKS
                            }
                        },
                        onMenuFindInPage = {
                            showOverflowMenu = false
                            showFindInPageBar = true
                        },
                        onMenuReload = {
                            showOverflowMenu = false
                            viewModel.issueWebViewCommand(WebViewCommand.Reload)
                        },
                        onMenuOpenShield = {
                            showOverflowMenu = false
                            activeSheet = ActiveBottomSheet.SHIELD
                        },
                        onMenuOpenDownloads = {
                            showOverflowMenu = false
                            activeSheet = ActiveBottomSheet.DOWNLOADS
                        },
                        onMenuOpenSettings = {
                            showOverflowMenu = false
                            activeSheet = ActiveBottomSheet.SETTINGS
                        }
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(if (isFullScreen) PaddingValues(0.dp) else innerPadding)
        ) {
            when {
                activeTab.isHome -> {
                    HomepageSpeedDial(
                        preferences = preferences,
                        shortcutsState = shortcutsState,
                        onNavigateToUrlOrSearch = { viewModel.loadUrlOrSearchInActiveTab(it) },
                        onAddShortcut = { name, url -> viewModel.addWebsiteShortcut(name, url) },
                        onDeleteShortcut = { id -> viewModel.deleteWebsiteShortcut(id) },
                        onOpenShieldSheet = { activeSheet = ActiveBottomSheet.SHIELD },
                        onOpenDownloadsSheet = { activeSheet = ActiveBottomSheet.DOWNLOADS }
                    )
                }
                else -> {
                    AegisWebViewContainer(
                        activeTab = activeTab,
                        preferences = preferences,
                        webViewCommand = webViewCommand,
                        onConsumeCommand = { viewModel.consumeWebViewCommand() },
                        onPageStateChanged = { tabId, url, title, isLoading, progress, canBack, canFwd ->
                            viewModel.updateActiveTabPageState(
                                tabId = tabId,
                                url = url,
                                title = title,
                                isLoading = isLoading,
                                progress = progress,
                                canGoBack = canBack,
                                canGoForward = canFwd
                            )
                        },
                        onShieldBlocked = { tabId, blockType, blockedUrl ->
                            viewModel.onShieldBlockedItem(tabId, blockType, blockedUrl)
                        },
                        onDownloadRequested = { url, fileName ->
                            viewModel.startTurboDownload(url, fileName)
                            activeSheet = ActiveBottomSheet.DOWNLOADS
                        }
                    )
                }
            }

            // Floating Non-Intrusive Pill when Full-Screen Mode is active
            AnimatedVisibility(
                visible = isFullScreen,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = Color(0xCC0A0E17),
                    tonalElevation = 6.dp,
                    shadowElevation = 6.dp,
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .clickable { viewModel.setFullScreenMode(false) }
                        .testTag("exit_fullscreen_floating_button")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.FullscreenExit,
                            contentDescription = stringResource(R.string.exit_fullscreen),
                            tint = Color(0xFF00E5FF),
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = stringResource(R.string.exit_fullscreen),
                            style = MaterialTheme.typography.labelLarge,
                            color = Color.White
                        )
                    }
                }
            }
        }
    }

    if (showInstallShortcutDialog) {
        val prefillName = if (activeTab.isHome) "" else activeTab.title
        val prefillUrl = if (activeTab.isHome || activeTab.isAiSearchOverview) "" else activeTab.url
        AddWebsiteShortcutDialog(
            initialName = prefillName,
            initialUrl = prefillUrl,
            onDismiss = { showInstallShortcutDialog = false },
            onConfirm = { name, url ->
                viewModel.addWebsiteShortcut(name, url)
                pinWebsiteShortcutToAndroidHomeScreen(context, name, url)
                showInstallShortcutDialog = false
            }
        )
    }

    when (activeSheet) {
        ActiveBottomSheet.TABS -> {
            TabsManagerBottomSheet(
                tabs = tabs,
                activeTabId = activeTab.id,
                memoryUsedMb = memoryUsedMb,
                onSelectTab = { viewModel.selectTab(it) },
                onCloseTab = { viewModel.closeTab(it) },
                onNewNormalTab = { viewModel.createNewTab(isPrivate = false) },
                onNewPrivateTab = { viewModel.createNewTab(isPrivate = true) },
                onCloseAllTabs = { viewModel.closeAllTabs() },
                onOptimizeMemory = { viewModel.optimizeMemoryNow() },
                onDismiss = { activeSheet = ActiveBottomSheet.NONE }
            )
        }
        ActiveBottomSheet.BOOKMARKS -> {
            CloudBookmarksBottomSheet(
                bookmarksState = bookmarksState,
                currentTabTitle = activeTab.title,
                currentTabUrl = activeTab.url,
                isCurrentTabHome = activeTab.isHome || activeTab.isAiSearchOverview,
                onAddBookmark = { title, url, folder -> viewModel.addBookmark(title, url, folder) },
                onDeleteBookmark = { viewModel.deleteBookmark(it) },
                onOpenUrl = { viewModel.loadUrlOrSearchInActiveTab(it) },
                onSignOut = {
                    activeSheet = ActiveBottomSheet.NONE
                    signOutUser(
                        credentialManager = CredentialManager.create(context),
                        scope = coroutineScope
                    )
                },
                onDismiss = { activeSheet = ActiveBottomSheet.NONE }
            )
        }
        ActiveBottomSheet.SHIELD -> {
            VistaAdBlockerShieldBottomSheet(
                preferences = preferences,
                recentBlockedRequests = recentBlockedRequests,
                onToggleAdBlock = { viewModel.setAdBlockEnabled(it) },
                onToggleVideoAdSkipper = { viewModel.setVideoAdSkipperEnabled(it) },
                onToggleTrackerBlock = { viewModel.setTrackerBlockEnabled(it) },
                onOpenShieldTestLab = { viewModel.openDirectUrlInActiveTab("aegis://shield-test") },
                onDismiss = { activeSheet = ActiveBottomSheet.NONE }
            )
        }
        ActiveBottomSheet.DOWNLOADS -> {
            TurboDownloadsBottomSheet(
                downloads = downloads,
                configuredThreads = preferences.downloadThreads,
                onChangeThreadCount = { viewModel.setDownloadThreads(it) },
                onStartDownload = { url, name -> viewModel.startTurboDownload(url, name) },
                onRemoveDownload = { viewModel.removeDownload(it) },
                onDismiss = { activeSheet = ActiveBottomSheet.NONE }
            )
        }
        ActiveBottomSheet.SETTINGS -> {
            CustomizationSettingsBottomSheet(
                preferences = preferences,
                history = history,
                memoryUsedMb = memoryUsedMb,
                memoryMaxMb = memoryMaxMb,
                onToggleDarkTheme = { viewModel.setDarkTheme(it) },
                onToggleAdBlock = { viewModel.setAdBlockEnabled(it) },
                onToggleVideoAdSkipper = { viewModel.setVideoAdSkipperEnabled(it) },
                onToggleTrackerBlock = { viewModel.setTrackerBlockEnabled(it) },
                onToggleForceDarkWebPages = { viewModel.setForceDarkWebPages(it) },
                onToggleMemorySaver = { viewModel.setMemorySaverEnabled(it) },
                onSelectSearchEngine = { viewModel.setSearchEngine(it) },
                onSelectUrlBarPosition = { viewModel.setUrlBarPosition(it) },
                onToggleCompactSpeedDial = { viewModel.setCompactSpeedDial(it) },
                onOptimizeMemoryNow = { viewModel.optimizeMemoryNow() },
                onClearHistoryAndCache = { viewModel.clearBrowsingHistory() },
                onOpenHistoryUrl = { viewModel.loadUrlOrSearchInActiveTab(it) },
                onDismiss = { activeSheet = ActiveBottomSheet.NONE }
            )
        }
        ActiveBottomSheet.NONE -> {}
    }
}

/**
 * Top Bar when on Search Results / Browsing Page (matches Screenshot 2):
 * - Home Icon (left)
 * - Rounded light-gray pill ("google.com/search" or current URL + compact Shield badge)
 * - "+" New Tab Button
 * - "[3]" Rounded Square Tab Counter
 * - "⋮" 3-Dot Menu Button
 */
@Composable
private fun VistaBrowsingTopBar(
    addressText: String,
    onAddressTextChange: (String) -> Unit,
    onSubmitAddress: () -> Unit,
    focusRequester: FocusRequester,
    tabCount: Int,
    activeTab: BrowserTab,
    isDarkTheme: Boolean,
    adShieldActive: Boolean,
    showOverflowMenu: Boolean,
    onToggleOverflowMenu: (Boolean) -> Unit,
    onHomeClick: () -> Unit,
    onNewTabClick: () -> Unit,
    onTabsClick: () -> Unit,
    onShieldClick: () -> Unit,
    onToggleDesktopSite: () -> Unit,
    onEnterFullScreen: () -> Unit,
    onToggleTheme: () -> Unit,
    onMenuInstallShortcut: () -> Unit,
    onMenuBookmarkPage: () -> Unit,
    onMenuFindInPage: () -> Unit,
    onMenuReload: () -> Unit,
    onMenuOpenShield: () -> Unit,
    onMenuOpenDownloads: () -> Unit,
    onMenuOpenSettings: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (isDarkTheme) Color(0xFF121316) else Color.White)
            .padding(horizontal = 6.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 1. Home Icon on far left
        IconButton(
            onClick = onHomeClick,
            modifier = Modifier
                .size(40.dp)
                .testTag("top_bar_home_button")
        ) {
            Icon(
                imageVector = Icons.Outlined.Home,
                contentDescription = "Home",
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(24.dp)
            )
        }

        // 2. Rounded Pill URL / Search Domain Bar ("google.com/search")
        Surface(
            shape = RoundedCornerShape(999.dp),
            color = if (isDarkTheme) Color(0xFF202124) else Color(0xFFF1F3F4),
            modifier = Modifier
                .weight(1f)
                .height(42.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier.weight(1f),
                    contentAlignment = Alignment.CenterStart
                ) {
                    if (addressText.isEmpty()) {
                        Text(
                            text = stringResource(R.string.search_or_type_url),
                            fontSize = 15.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    BasicTextField(
                        value = addressText,
                        onValueChange = onAddressTextChange,
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyLarge.copy(
                            fontSize = 16.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        ),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(
                            onSearch = { onSubmitAddress() },
                            onGo = { onSubmitAddress() },
                            onDone = { onSubmitAddress() }
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(focusRequester)
                            .testTag("omnibox_url_field")
                    )
                }

                // Compact Shield Button inside the pill
                IconButton(
                    onClick = onShieldClick,
                    modifier = Modifier
                        .size(28.dp)
                        .testTag("omnibox_shield_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Shield,
                        contentDescription = "Vista Ad Shield",
                        tint = if (adShieldActive) Color(0xFFFF5722) else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.width(4.dp))

        // 3. "+" New Tab Button
        IconButton(
            onClick = onNewTabClick,
            modifier = Modifier
                .size(40.dp)
                .testTag("top_bar_new_tab_plus_button")
        ) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = "New Tab",
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(24.dp)
            )
        }

        // 4. Rounded Square Tab Counter [3]
        IconButton(
            onClick = onTabsClick,
            modifier = Modifier
                .size(40.dp)
                .testTag("top_bar_tabs_button")
        ) {
            Box(
                modifier = Modifier
                    .size(22.dp)
                    .border(
                        width = 1.8.dp,
                        color = MaterialTheme.colorScheme.onSurface,
                        shape = RoundedCornerShape(6.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = tabCount.toString(),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }

        // 5. 3-Dot Vertical Menu Icon
        Box {
            IconButton(
                onClick = { onToggleOverflowMenu(true) },
                modifier = Modifier
                    .size(40.dp)
                    .testTag("top_bar_overflow_menu_button")
            ) {
                Icon(
                    imageVector = Icons.Default.MoreVert,
                    contentDescription = "Menu",
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(23.dp)
                )
            }

            VistaBrowserOverflowMenu(
                expanded = showOverflowMenu,
                onDismiss = { onToggleOverflowMenu(false) },
                activeTab = activeTab,
                isDarkTheme = isDarkTheme,
                adShieldActive = adShieldActive,
                onToggleTheme = onToggleTheme,
                onToggleDesktopSite = onToggleDesktopSite,
                onEnterFullScreen = onEnterFullScreen,
                onMenuInstallShortcut = onMenuInstallShortcut,
                onMenuBookmarkPage = onMenuBookmarkPage,
                onMenuOpenShield = onMenuOpenShield,
                onMenuOpenDownloads = onMenuOpenDownloads,
                onMenuFindInPage = onMenuFindInPage,
                onMenuReload = onMenuReload,
                onMenuOpenSettings = onMenuOpenSettings
            )
        }
    }
}

/**
 * Homepage Top Omnibox Bar matching Screenshot 1:
 * Rounded light-gray pill containing:
 * - Left: Search Magnifying Glass Icon + "Search Vista or type URL"
 * - Right: Dark Shield Crest Icon + Colorful Gradient Triangle Icon
 */
@Composable
private fun VistaHomepageOmniboxBar(
    addressText: String,
    onAddressTextChange: (String) -> Unit,
    onSubmitAddress: () -> Unit,
    focusRequester: FocusRequester,
    onShieldIconClick: () -> Unit,
    onVistaTriangleIconClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 10.dp, vertical = 8.dp)
    ) {
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = "Search",
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(22.dp)
                )

                Spacer(modifier = Modifier.width(10.dp))

                Box(
                    modifier = Modifier.weight(1f),
                    contentAlignment = Alignment.CenterStart
                ) {
                    if (addressText.isEmpty()) {
                        Text(
                            text = stringResource(R.string.search_or_type_url),
                            style = MaterialTheme.typography.bodyLarge.copy(fontSize = 18.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    BasicTextField(
                        value = addressText,
                        onValueChange = onAddressTextChange,
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyLarge.copy(
                            fontSize = 17.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        ),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(
                            onSearch = { onSubmitAddress() },
                            onGo = { onSubmitAddress() },
                            onDone = { onSubmitAddress() }
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(focusRequester)
                            .testTag("omnibox_url_field")
                    )
                }

                if (addressText.isNotEmpty()) {
                    IconButton(
                        onClick = { onAddressTextChange("") },
                        modifier = Modifier.size(30.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Clear",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                // Shield Emblem Icon
                IconButton(
                    onClick = onShieldIconClick,
                    modifier = Modifier
                        .size(36.dp)
                        .testTag("omnibox_shield_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Shield,
                        contentDescription = "Vista Ad-Blocker Shield",
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.78f),
                        modifier = Modifier.size(24.dp)
                    )
                }

                Spacer(modifier = Modifier.width(4.dp))

                // Official Vista Logo Emblem
                IconButton(
                    onClick = onVistaTriangleIconClick,
                    modifier = Modifier
                        .size(36.dp)
                        .testTag("omnibox_vista_triangle_button")
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.vista_brand_logo),
                        contentDescription = "Vista Logo",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                    )
                }
            }
        }
    }
}

/**
 * Bottom 5-Icon Navigation Bar matching Screenshot 1:
 * 1. Home Icon
 * 2. Bookmark Ribbon Icon
 * 3. Center Search Magnifying Glass Icon
 * 4. Rounded Square Tab Counter [1]
 * 5. 3-Dot Vertical Menu Icon
 */
@Composable
private fun VistaBottomNavigationBar(
    tabCount: Int,
    isBookmarked: Boolean,
    showOverflowMenu: Boolean,
    onToggleOverflowMenu: (Boolean) -> Unit,
    activeTab: BrowserTab,
    isDarkTheme: Boolean,
    adShieldActive: Boolean,
    onHomeClick: () -> Unit,
    onBookmarksClick: () -> Unit,
    onSearchClick: () -> Unit,
    onTabsClick: () -> Unit,
    onToggleDesktopSite: () -> Unit,
    onEnterFullScreen: () -> Unit,
    onToggleTheme: () -> Unit,
    onMenuInstallShortcut: () -> Unit,
    onMenuBookmarkPage: () -> Unit,
    onMenuFindInPage: () -> Unit,
    onMenuReload: () -> Unit,
    onMenuOpenShield: () -> Unit,
    onMenuOpenDownloads: () -> Unit,
    onMenuOpenSettings: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 1. Home Icon
        IconButton(
            onClick = onHomeClick,
            modifier = Modifier.testTag("bottom_home_button")
        ) {
            Icon(
                imageVector = Icons.Outlined.Home,
                contentDescription = "Home",
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(26.dp)
            )
        }

        // 2. Bookmark Ribbon Icon
        IconButton(
            onClick = onBookmarksClick,
            modifier = Modifier.testTag("bottom_bookmarks_button")
        ) {
            Icon(
                imageVector = if (isBookmarked) Icons.Default.Bookmark else Icons.Outlined.BookmarkBorder,
                contentDescription = "Bookmarks",
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(25.dp)
            )
        }

        // 3. Center Search Magnifying Glass Icon
        IconButton(
            onClick = onSearchClick,
            modifier = Modifier.testTag("bottom_search_button")
        ) {
            Icon(
                imageVector = Icons.Outlined.Search,
                contentDescription = "Search",
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(27.dp)
            )
        }

        // 4. Rounded Square Tab Counter [1]
        IconButton(
            onClick = onTabsClick,
            modifier = Modifier.testTag("bottom_tabs_button")
        ) {
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .border(
                        width = 2.dp,
                        color = MaterialTheme.colorScheme.onSurface,
                        shape = RoundedCornerShape(6.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = tabCount.toString(),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }

        // 5. 3-Dot Vertical Menu Icon
        Box {
            IconButton(
                onClick = { onToggleOverflowMenu(true) },
                modifier = Modifier.testTag("toolbar_overflow_menu_button")
            ) {
                Icon(
                    imageVector = Icons.Default.MoreVert,
                    contentDescription = "Menu",
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(25.dp)
                )
            }

            VistaBrowserOverflowMenu(
                expanded = showOverflowMenu,
                onDismiss = { onToggleOverflowMenu(false) },
                activeTab = activeTab,
                isDarkTheme = isDarkTheme,
                adShieldActive = adShieldActive,
                onToggleTheme = onToggleTheme,
                onToggleDesktopSite = onToggleDesktopSite,
                onEnterFullScreen = onEnterFullScreen,
                onMenuInstallShortcut = onMenuInstallShortcut,
                onMenuBookmarkPage = onMenuBookmarkPage,
                onMenuOpenShield = onMenuOpenShield,
                onMenuOpenDownloads = onMenuOpenDownloads,
                onMenuFindInPage = onMenuFindInPage,
                onMenuReload = onMenuReload,
                onMenuOpenSettings = onMenuOpenSettings
            )
        }
    }
}

@Composable
private fun VistaBrowserOverflowMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    activeTab: BrowserTab,
    isDarkTheme: Boolean,
    adShieldActive: Boolean,
    onToggleTheme: () -> Unit,
    onToggleDesktopSite: () -> Unit,
    onEnterFullScreen: () -> Unit,
    onMenuInstallShortcut: () -> Unit,
    onMenuBookmarkPage: () -> Unit,
    onMenuOpenShield: () -> Unit,
    onMenuOpenDownloads: () -> Unit,
    onMenuFindInPage: () -> Unit,
    onMenuReload: () -> Unit,
    onMenuOpenSettings: () -> Unit
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss
    ) {
        DropdownMenuItem(
            text = { Text(if (isDarkTheme) "Switch to Light Mode" else "Switch to Dark Mode") },
            leadingIcon = {
                Icon(
                    if (isDarkTheme) Icons.Default.LightMode else Icons.Default.DarkMode,
                    contentDescription = null
                )
            },
            onClick = {
                onDismiss()
                onToggleTheme()
            }
        )
        DropdownMenuItem(
            text = { Text(if (activeTab.isDesktopMode) "✓ Desktop Site Active" else "Desktop Site") },
            leadingIcon = { Icon(Icons.Default.Computer, contentDescription = null) },
            onClick = {
                onDismiss()
                onToggleDesktopSite()
            }
        )
        DropdownMenuItem(
            text = { Text("Full-Screen Mode") },
            leadingIcon = { Icon(Icons.Default.Fullscreen, contentDescription = null) },
            onClick = {
                onDismiss()
                onEnterFullScreen()
            }
        )
        DropdownMenuItem(
            text = { Text("Install / Create Website Shortcut") },
            leadingIcon = { Icon(Icons.AutoMirrored.Filled.AddToHomeScreen, contentDescription = null) },
            onClick = onMenuInstallShortcut
        )
        DropdownMenuItem(
            text = { Text("Bookmark Current Page") },
            leadingIcon = { Icon(Icons.Outlined.BookmarkBorder, contentDescription = null) },
            onClick = onMenuBookmarkPage
        )
        DropdownMenuItem(
            text = { Text(if (adShieldActive) "✓ Vista Ad & Video Blocker" else "Vista Ad & Video Blocker") },
            leadingIcon = { Icon(Icons.Default.Shield, contentDescription = null) },
            onClick = onMenuOpenShield
        )
        DropdownMenuItem(
            text = { Text("Turbo Downloads Manager") },
            leadingIcon = { Icon(Icons.Default.Download, contentDescription = null) },
            onClick = onMenuOpenDownloads
        )
        DropdownMenuItem(
            text = { Text("Find in Page") },
            leadingIcon = { Icon(Icons.Default.FindInPage, contentDescription = null) },
            onClick = onMenuFindInPage
        )
        DropdownMenuItem(
            text = { Text("Reload Page") },
            leadingIcon = { Icon(Icons.Default.Refresh, contentDescription = null) },
            onClick = onMenuReload
        )
        HorizontalDivider()
        DropdownMenuItem(
            text = { Text("Settings, Privacy & Memory") },
            leadingIcon = { Icon(Icons.Default.Tune, contentDescription = null) },
            onClick = onMenuOpenSettings
        )
    }
}

@Composable
private fun FindInPageToolbar(
    query: String,
    onQueryChange: (String) -> Unit,
    onFindNext: () -> Unit,
    onClose: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            placeholder = { Text("Find in page...") },
            singleLine = true,
            modifier = Modifier.weight(1f)
        )
        Spacer(modifier = Modifier.width(8.dp))
        IconButton(onClick = onFindNext) {
            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Next Match")
        }
        IconButton(onClick = onClose) {
            Icon(Icons.Default.Close, contentDescription = "Close Find")
        }
    }
}
