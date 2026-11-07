package com.example.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

@IgnoreExtraProperties
data class BookmarkItem(
    @DocumentId val id: String = "",
    val userId: String = "",
    val title: String = "",
    val url: String = "",
    val folder: String = "General",
    val faviconUrl: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class ShortcutItem(
    @DocumentId val id: String = "",
    val userId: String = "",
    val name: String = "",
    val url: String = "",
    val logoUrl: String = "",
    val isPinned: Boolean = true,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

data class BrowserTab(
    val id: String,
    val url: String = "",
    val title: String = "New Tab",
    val isHome: Boolean = true,
    val isAiSearchOverview: Boolean = false,
    val searchQuery: String = "",
    val isDesktopMode: Boolean = false,
    val isPrivate: Boolean = false,
    val isLoading: Boolean = false,
    val progress: Int = 0,
    val canGoBack: Boolean = false,
    val canGoForward: Boolean = false,
    val adsBlockedInTab: Int = 0,
    val videoAdsBlockedInTab: Int = 0,
    val trackersBlockedInTab: Int = 0,
    val dataSavedBytes: Long = 0L,
    val isSuspendedForMemory: Boolean = false,
    val lastActiveTimestamp: Long = System.currentTimeMillis()
)

data class DownloadTask(
    val id: String,
    val fileName: String,
    val url: String,
    val totalBytes: Long,
    val downloadedBytes: Long,
    val speedBytesPerSec: Long,
    val activeThreads: Int,
    val status: DownloadStatus,
    val localPath: String = "",
    val startedAt: Long = System.currentTimeMillis()
)

enum class DownloadStatus {
    CONNECTING,
    DOWNLOADING,
    PAUSED,
    COMPLETED,
    FAILED
}

data class BlockedRequestEvent(
    val id: String,
    val domainOrRule: String,
    val fullUrl: String,
    val category: String,
    val timestamp: Long = System.currentTimeMillis()
)

data class HistoryEntry(
    val id: String,
    val title: String,
    val url: String,
    val timestamp: Long = System.currentTimeMillis()
)

enum class SearchEngine(val displayName: String, val queryUrlPrefix: String, val homeDomain: String) {
    GOOGLE("Google", "https://www.google.com/search?q=", "google.com"),
    BRAVE("Brave Search", "https://search.brave.com/search?q=", "search.brave.com"),
    DUCKDUCKGO("DuckDuckGo", "https://duckduckgo.com/?q=", "duckduckgo.com"),
    STARTPAGE("Startpage", "https://www.startpage.com/do/dsearch?query=", "startpage.com")
}

enum class UrlBarPosition(val label: String) {
    TOP("Top Bar"),
    BOTTOM("Bottom Reachability")
}
