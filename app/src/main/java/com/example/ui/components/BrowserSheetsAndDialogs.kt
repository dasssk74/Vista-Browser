package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Policy
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.model.BlockedRequestEvent
import com.example.data.model.BookmarkItem
import com.example.data.model.BrowserTab
import com.example.data.model.DownloadStatus
import com.example.data.model.DownloadTask
import com.example.data.model.HistoryEntry
import com.example.data.model.SearchEngine
import com.example.data.model.UrlBarPosition
import com.example.data.repository.BrowserPreferences
import com.example.data.repository.BrowserSyncRepository
import com.example.engine.TurboDownloadEngine
import com.example.ui.UiState
import com.google.firebase.Firebase
import com.google.firebase.auth.auth

// 1. MULTI-TAB SWITCHER SHEET
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TabsManagerBottomSheet(
    tabs: List<BrowserTab>,
    activeTabId: String,
    memoryUsedMb: Long,
    onSelectTab: (String) -> Unit,
    onCloseTab: (String) -> Unit,
    onNewNormalTab: () -> Unit,
    onNewPrivateTab: () -> Unit,
    onCloseAllTabs: () -> Unit,
    onOptimizeMemory: () -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val hibernatedCount = tabs.count { it.isSuspendedForMemory }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp)
                .testTag("tabs_manager_sheet")
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Open Browser Tabs (${tabs.size})",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "$hibernatedCount hibernated • ${memoryUsedMb}MB active heap",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilledTonalButton(
                        onClick = onOptimizeMemory,
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        modifier = Modifier.testTag("sheet_hibernate_tabs_button")
                    ) {
                        Icon(Icons.Default.Memory, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Save RAM")
                    }
                    OutlinedButton(
                        onClick = onCloseAllTabs,
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text("Close All")
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = {
                        onNewNormalTab()
                        onDismiss()
                    },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("new_normal_tab_button")
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("New Tab")
                }
                FilledTonalButton(
                    onClick = {
                        onNewPrivateTab()
                        onDismiss()
                    },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("new_private_tab_button")
                ) {
                    Icon(Icons.Default.Policy, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Private Tab")
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 380.dp)
            ) {
                items(tabs, key = { it.id }) { tab ->
                    val isSelected = tab.id == activeTabId
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = if (isSelected) {
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(
                                width = if (isSelected) 2.dp else 1.dp,
                                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
                                shape = RoundedCornerShape(16.dp)
                            )
                            .clip(RoundedCornerShape(16.dp))
                            .clickable {
                                onSelectTab(tab.id)
                                onDismiss()
                            }
                            .testTag("tab_card_${tab.id}")
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            WebsiteLogoBadge(
                                name = tab.title,
                                url = tab.url,
                                logoUrl = if (tab.isHome || tab.isAiSearchOverview) "" else BrowserSyncRepository.buildFaviconUrl(tab.url),
                                sizeDp = 42
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    if (tab.isPrivate) {
                                        Surface(
                                            shape = RoundedCornerShape(6.dp),
                                            color = MaterialTheme.colorScheme.secondaryContainer,
                                            modifier = Modifier.padding(end = 6.dp)
                                        ) {
                                            Text(
                                                text = "PRIVATE",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                    if (tab.isDesktopMode) {
                                        Surface(
                                            shape = RoundedCornerShape(6.dp),
                                            color = MaterialTheme.colorScheme.tertiaryContainer,
                                            modifier = Modifier.padding(end = 6.dp)
                                        ) {
                                            Text(
                                                text = "DESKTOP",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onTertiaryContainer,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                    Text(
                                        text = tab.title,
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                Text(
                                    text = when {
                                        tab.isHome -> "Speed Dial Homepage"
                                        tab.isAiSearchOverview -> "AI Search • ${tab.searchQuery}"
                                        else -> tab.url
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = buildString {
                                        append(if (tab.isSuspendedForMemory) "Hibernated (0 MB RAM)" else "Active in Memory")
                                        if (tab.adsBlockedInTab > 0 || tab.videoAdsBlockedInTab > 0) {
                                            append(" • ${tab.adsBlockedInTab} ads / ${tab.videoAdsBlockedInTab} video ads blocked")
                                        }
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            IconButton(onClick = { onCloseTab(tab.id) }) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Close Tab"
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// 2. CLOUD SYNCED BOOKMARKS SHEET
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun CloudBookmarksBottomSheet(
    bookmarksState: UiState<List<BookmarkItem>>,
    currentTabTitle: String,
    currentTabUrl: String,
    isCurrentTabHome: Boolean,
    onAddBookmark: (title: String, url: String, folder: String) -> Unit,
    onDeleteBookmark: (String) -> Unit,
    onOpenUrl: (String) -> Unit,
    onSignOut: () -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val userEmail = Firebase.auth.currentUser?.email ?: "Signed-In Cloud Account"
    val folders = listOf("All", "General", "Privacy", "Work", "Media", "Reading List")
    var selectedFolder by remember { mutableStateOf("All") }
    var newTitle by remember { mutableStateOf(if (isCurrentTabHome) "" else currentTabTitle) }
    var newUrl by remember { mutableStateOf(if (isCurrentTabHome) "" else currentTabUrl) }
    var newFolder by remember { mutableStateOf("General") }
    var showAddForm by remember { mutableStateOf(!isCurrentTabHome && currentTabUrl.isNotBlank()) }

    val bookmarks = when (bookmarksState) {
        is UiState.Success -> bookmarksState.data
        else -> emptyList()
    }
    val filteredBookmarks = remember(bookmarks, selectedFolder) {
        if (selectedFolder == "All") bookmarks else bookmarks.filter { it.folder.equals(selectedFolder, ignoreCase = true) }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp)
                .testTag("cloud_bookmarks_sheet")
        ) {
            // Cloud Sync Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.CloudDone,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.size(26.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "Cloud Synced Bookmarks",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Real-time Firestore sync • $userEmail",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Row {
                    IconButton(onClick = { showAddForm = !showAddForm }) {
                        Icon(
                            imageVector = Icons.Default.BookmarkAdd,
                            contentDescription = "Add Bookmark",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    IconButton(onClick = onSignOut) {
                        Icon(
                            imageVector = Icons.Default.Logout,
                            contentDescription = "Sign Out",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }

            if (showAddForm) {
                Spacer(modifier = Modifier.height(10.dp))
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(
                            text = "Save Bookmark to Cloud",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = newTitle,
                            onValueChange = { newTitle = it },
                            label = { Text("Bookmark Title") },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("bookmark_title_input")
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = newUrl,
                            onValueChange = { newUrl = it },
                            label = { Text("Website URL") },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("bookmark_url_input")
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            folders.drop(1).forEach { folderOption ->
                                FilterChip(
                                    selected = newFolder == folderOption,
                                    onClick = { newFolder = folderOption },
                                    label = { Text(folderOption) }
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(
                            onClick = {
                                if (newUrl.isNotBlank()) {
                                    onAddBookmark(newTitle, newUrl, newFolder)
                                    showAddForm = false
                                }
                            },
                            enabled = newUrl.isNotBlank(),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("save_cloud_bookmark_button")
                        ) {
                            Text("Sync Bookmark Across Devices")
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Folder Filter Row
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                folders.forEach { folder ->
                    FilterChip(
                        selected = selectedFolder == folder,
                        onClick = { selectedFolder = folder },
                        label = { Text(folder) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            if (filteredBookmarks.isEmpty()) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 16.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.Folder,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(36.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "No synced bookmarks in \"$selectedFolder\"",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Tap the + Bookmark icon above to sync bookmarks across all your devices.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 340.dp)
                ) {
                    items(filteredBookmarks, key = { it.id }) { bm ->
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .clickable {
                                    onOpenUrl(bm.url)
                                    onDismiss()
                                }
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                WebsiteLogoBadge(
                                    name = bm.title,
                                    url = bm.url,
                                    logoUrl = bm.faviconUrl,
                                    sizeDp = 40
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = bm.title,
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = bm.url,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "Folder: ${bm.folder}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                                IconButton(onClick = { onDeleteBookmark(bm.id) }) {
                                    Icon(
                                        imageVector = Icons.Default.DeleteOutline,
                                        contentDescription = "Delete Bookmark",
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// 3. VISTA AD-BLOCKER & VIDEO SHIELD BOTTOM SHEET (Replaces VPN Sheet)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VistaAdBlockerShieldBottomSheet(
    preferences: BrowserPreferences,
    recentBlockedRequests: List<BlockedRequestEvent>,
    onToggleAdBlock: (Boolean) -> Unit,
    onToggleVideoAdSkipper: (Boolean) -> Unit,
    onToggleTrackerBlock: (Boolean) -> Unit,
    onOpenShieldTestLab: () -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val isShieldActive = preferences.adBlockEnabled || preferences.videoAdSkipperEnabled || preferences.trackerBlockEnabled

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp)
                .testTag("vista_adblock_shield_sheet")
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(
                                if (isShieldActive) Color(0xFFFF5722).copy(alpha = 0.16f)
                                else MaterialTheme.colorScheme.surfaceVariant
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Shield,
                            contentDescription = null,
                            tint = if (isShieldActive) Color(0xFFFF5722) else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "Vista Ad & Video Shield",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = if (isShieldActive) {
                                "Active • Blocking network ads, pop-ups & video pre-rolls"
                            } else {
                                "Shields paused for current browsing session"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Switch(
                    checked = isShieldActive,
                    onCheckedChange = { enabled ->
                        onToggleAdBlock(enabled)
                        onToggleVideoAdSkipper(enabled)
                        onToggleTrackerBlock(enabled)
                    },
                    modifier = Modifier.testTag("sheet_adblock_master_switch")
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Live Counter Summary Card
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "${preferences.totalAdsBlocked}",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFFF5722)
                        )
                        Text("Ads Blocked", style = MaterialTheme.typography.labelSmall)
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "${preferences.totalVideoAdsBlocked}",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text("Video Ads Skipped", style = MaterialTheme.typography.labelSmall)
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "${preferences.totalTrackersBlocked}",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.tertiary
                        )
                        Text("Trackers Blocked", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Granular Toggles
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    SettingToggleRow(
                        title = "Block Display Banners & Pop-Up Ads",
                        subtitle = "Blocks 80+ ad networks, pop-unders & cosmetic DOM slots",
                        checked = preferences.adBlockEnabled,
                        onCheckedChange = onToggleAdBlock
                    )
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    SettingToggleRow(
                        title = "YouTube & HTML5 Video Ad Blocker",
                        subtitle = "Strips player ad JSON & auto-skips pre-roll/mid-roll video ads",
                        checked = preferences.videoAdSkipperEnabled,
                        onCheckedChange = onToggleVideoAdSkipper
                    )
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    SettingToggleRow(
                        title = "Block Cross-Site Trackers & Pixels",
                        subtitle = "Neutralizes analytics beacons and third-party tracking scripts",
                        checked = preferences.trackerBlockEnabled,
                        onCheckedChange = onToggleTrackerBlock
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Button(
                onClick = {
                    onDismiss()
                    onOpenShieldTestLab()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("launch_shield_benchmark_button")
            ) {
                Icon(Icons.Default.Science, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Run Live Ad & Video Blocker Test Lab")
            }

            if (recentBlockedRequests.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "Recently Blocked Requests (${recentBlockedRequests.size})",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(6.dp))
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 160.dp)
                ) {
                    items(recentBlockedRequests.take(15), key = { it.id }) { event ->
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Block,
                                    contentDescription = null,
                                    tint = Color(0xFFFF5722),
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = event.domainOrRule,
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = event.category,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// 4. TURBO MULTI-THREAD DOWNLOAD MANAGER SHEET
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TurboDownloadsBottomSheet(
    downloads: List<DownloadTask>,
    configuredThreads: Int,
    onChangeThreadCount: (Int) -> Unit,
    onStartDownload: (url: String, fileName: String?) -> Unit,
    onRemoveDownload: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var customUrl by remember { mutableStateOf("") }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp)
                .testTag("turbo_downloads_sheet")
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Turbo Multi-Thread Downloads",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Splits files into parallel HTTP byte-range chunks for maximum internet speed",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Parallel Streams Selector (4x, 8x, 16x)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Parallel Connections per File:",
                    style = MaterialTheme.typography.labelLarge
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(4, 8, 16).forEach { count ->
                        FilterChip(
                            selected = configuredThreads == count,
                            onClick = { onChangeThreadCount(count) },
                            label = { Text("${count}x Streams") }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Direct URL Download Input
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = customUrl,
                    onValueChange = { customUrl = it },
                    placeholder = { Text("Paste direct file URL to turbo download...") },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("download_url_input")
                )
                Button(
                    onClick = {
                        if (customUrl.isNotBlank()) {
                            onStartDownload(customUrl.trim(), null)
                            customUrl = ""
                        }
                    },
                    enabled = customUrl.isNotBlank(),
                    modifier = Modifier.testTag("start_download_button")
                ) {
                    Icon(Icons.Default.Download, contentDescription = "Download")
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Quick Benchmark Test File Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilledTonalButton(
                    onClick = {
                        onStartDownload(
                            "https://raw.githubusercontent.com/JetBrains/kotlin/master/ReadMe.md",
                            "Kotlin_Engine_Specs.md"
                        )
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Speed, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Test Spec File", maxLines = 1)
                }
                FilledTonalButton(
                    onClick = {
                        onStartDownload(
                            "https://www.w3.org/WAI/ER/tests/xhtml/testfiles/resources/pdf/dummy.pdf",
                            "W3C_Benchmark_Doc.pdf"
                        )
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Test PDF (Multi-Stream)", maxLines = 1)
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            if (downloads.isEmpty()) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "No active or completed downloads yet",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Any file you download from a webpage or via URL above will automatically accelerate using $configuredThreads parallel streams.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 280.dp)
                ) {
                    items(downloads, key = { it.id }) { task ->
                        val progressFraction = if (task.totalBytes > 0) {
                            (task.downloadedBytes.toFloat() / task.totalBytes.toFloat()).coerceIn(0f, 1f)
                        } else if (task.status == DownloadStatus.COMPLETED) {
                            1f
                        } else {
                            0.35f
                        }

                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = task.fileName,
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = buildString {
                                                append(task.status.name)
                                                append(" • ${task.activeThreads}x Threads")
                                                append(" • ${TurboDownloadEngine.formatBytes(task.downloadedBytes)}")
                                                if (task.totalBytes > 0) {
                                                    append(" / ${TurboDownloadEngine.formatBytes(task.totalBytes)}")
                                                }
                                                if (task.speedBytesPerSec > 0) {
                                                    append(" (${TurboDownloadEngine.formatBytes(task.speedBytesPerSec)}/s)")
                                                }
                                            },
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                    IconButton(onClick = { onRemoveDownload(task.id) }) {
                                        Icon(Icons.Default.DeleteOutline, contentDescription = "Remove")
                                    }
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                                LinearProgressIndicator(
                                    progress = { progressFraction },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(6.dp)
                                        .clip(RoundedCornerShape(999.dp))
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// 5. CUSTOMIZATION, THEME & MEMORY EFFICIENCY SHEET
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun CustomizationSettingsBottomSheet(
    preferences: BrowserPreferences,
    history: List<HistoryEntry>,
    memoryUsedMb: Long,
    memoryMaxMb: Long,
    onToggleDarkTheme: (Boolean) -> Unit,
    onToggleAdBlock: (Boolean) -> Unit,
    onToggleVideoAdSkipper: (Boolean) -> Unit,
    onToggleTrackerBlock: (Boolean) -> Unit,
    onToggleForceDarkWebPages: (Boolean) -> Unit,
    onToggleMemorySaver: (Boolean) -> Unit,
    onSelectSearchEngine: (SearchEngine) -> Unit,
    onSelectUrlBarPosition: (UrlBarPosition) -> Unit,
    onToggleCompactSpeedDial: (Boolean) -> Unit,
    onOptimizeMemoryNow: () -> Unit,
    onClearHistoryAndCache: () -> Unit,
    onOpenHistoryUrl: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        LazyColumn(
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 560.dp)
                .padding(bottom = 24.dp)
                .testTag("customization_settings_sheet")
        ) {
            item {
                Text(
                    text = "Customization, Privacy & Memory",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Tailor Vista Browser's theme, toolbar position, ad shield, and memory footprint",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Theme Switcher Section (Light by default, user can switch between Light & Dark)
            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = if (preferences.isDarkTheme) Icons.Default.DarkMode else Icons.Default.LightMode,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text(
                                        text = "Appearance Theme (Light / Dark)",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = if (preferences.isDarkTheme) "Dark Mode active for low-light comfort" else "Light Mode active (Default)",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            Switch(
                                checked = preferences.isDarkTheme,
                                onCheckedChange = onToggleDarkTheme,
                                modifier = Modifier.testTag("settings_dark_mode_switch")
                            )
                        }

                        HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp))

                        SettingToggleRow(
                            title = "Force Dark Mode on Webpages",
                            subtitle = "Inverts bright white websites for night reading",
                            checked = preferences.forceDarkWebPages,
                            onCheckedChange = onToggleForceDarkWebPages
                        )
                    }
                }
            }

            // Interface Layout Customization
            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(
                            text = "Address Bar Position",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            UrlBarPosition.entries.forEach { pos ->
                                FilterChip(
                                    selected = preferences.urlBarPosition == pos,
                                    onClick = { onSelectUrlBarPosition(pos) },
                                    label = { Text(pos.label) }
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        Text(
                            text = "Default Search Engine",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            SearchEngine.entries.forEach { engine ->
                                FilterChip(
                                    selected = preferences.searchEngine == engine,
                                    onClick = { onSelectSearchEngine(engine) },
                                    label = { Text(engine.displayName) }
                                )
                            }
                        }

                        HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp))

                        SettingToggleRow(
                            title = "Compact Homepage Speed-Dial Grid",
                            subtitle = "Fit more saved website shortcuts per row",
                            checked = preferences.compactSpeedDial,
                            onCheckedChange = onToggleCompactSpeedDial
                        )
                    }
                }
            }

            // Built-in Ad & Video Ad Blocker Controls
            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(
                            text = "Privacy & Video Ad-Blocker Engine",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        SettingToggleRow(
                            title = "Built-in Ad Blocker (Banners & Popups)",
                            subtitle = "Blocks DoubleClick, AdSense, Taboola & 80+ ad networks",
                            checked = preferences.adBlockEnabled,
                            onCheckedChange = onToggleAdBlock
                        )
                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                        SettingToggleRow(
                            title = "Video Ad Blocker & Auto-Skipper",
                            subtitle = "Blocks VAST/IMA video ad requests & auto-skips pre-roll video ads",
                            checked = preferences.videoAdSkipperEnabled,
                            onCheckedChange = onToggleVideoAdSkipper
                        )
                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                        SettingToggleRow(
                            title = "Cross-Site Tracker & Fingerprint Shield",
                            subtitle = "Blocks analytics pixels and telemetry beacons",
                            checked = preferences.trackerBlockEnabled,
                            onCheckedChange = onToggleTrackerBlock
                        )
                    }
                }
            }

            // Memory Efficiency & Browsing Data
            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(
                            text = "Memory Efficiency (${memoryUsedMb}MB / ${memoryMaxMb}MB)",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        SettingToggleRow(
                            title = "Automatic Background Tab Hibernation",
                            subtitle = "Suspends inactive tabs automatically to keep RAM usage minimal",
                            checked = preferences.memorySaverEnabled,
                            onCheckedChange = onToggleMemorySaver
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilledTonalButton(
                                onClick = onOptimizeMemoryNow,
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.Memory, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Purge RAM Now")
                            }
                            OutlinedButton(
                                onClick = onClearHistoryAndCache,
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.DeleteOutline, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Clear Data")
                            }
                        }
                    }
                }
            }

            if (history.isNotEmpty()) {
                item {
                    Text(
                        text = "Recent Browsing History",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
                items(history.take(8), key = { it.id }) { item ->
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable {
                                onOpenHistoryUrl(item.url)
                                onDismiss()
                            }
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.History,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(item.title, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(item.url, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
            Text(text = title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
            Text(text = subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
