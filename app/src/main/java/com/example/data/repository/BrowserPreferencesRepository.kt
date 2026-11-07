package com.example.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.data.model.SearchEngine
import com.example.data.model.UrlBarPosition
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.browserDataStore: DataStore<Preferences> by preferencesDataStore(name = "aegis_browser_prefs")

data class BrowserPreferences(
    val isDarkTheme: Boolean = false, // Default is Light mode per user requirement
    val adBlockEnabled: Boolean = true,
    val videoAdSkipperEnabled: Boolean = true,
    val trackerBlockEnabled: Boolean = true,
    val httpsEverywhere: Boolean = true,
    val forceDarkWebPages: Boolean = false,
    val memorySaverEnabled: Boolean = true,
    val blockThirdPartyCookies: Boolean = true,
    val downloadThreads: Int = 8,
    val searchEngine: SearchEngine = SearchEngine.BRAVE,
    val urlBarPosition: UrlBarPosition = UrlBarPosition.TOP,
    val compactSpeedDial: Boolean = false,
    val totalAdsBlocked: Long = 0L,
    val totalVideoAdsBlocked: Long = 0L,
    val totalTrackersBlocked: Long = 0L,
    val totalDataSavedBytes: Long = 0L
)

class BrowserPreferencesRepository(private val context: Context) {

    private object Keys {
        val IS_DARK_THEME = booleanPreferencesKey("is_dark_theme")
        val AD_BLOCK_ENABLED = booleanPreferencesKey("ad_block_enabled")
        val VIDEO_AD_SKIPPER_ENABLED = booleanPreferencesKey("video_ad_skipper_enabled")
        val TRACKER_BLOCK_ENABLED = booleanPreferencesKey("tracker_block_enabled")
        val HTTPS_EVERYWHERE = booleanPreferencesKey("https_everywhere")
        val FORCE_DARK_WEB_PAGES = booleanPreferencesKey("force_dark_web_pages")
        val MEMORY_SAVER_ENABLED = booleanPreferencesKey("memory_saver_enabled")
        val BLOCK_THIRD_PARTY_COOKIES = booleanPreferencesKey("block_third_party_cookies")
        val DOWNLOAD_THREADS = intPreferencesKey("download_threads")
        val SEARCH_ENGINE = stringPreferencesKey("search_engine")
        val URL_BAR_POSITION = stringPreferencesKey("url_bar_position")
        val COMPACT_SPEED_DIAL = booleanPreferencesKey("compact_speed_dial")
        val TOTAL_ADS_BLOCKED = longPreferencesKey("total_ads_blocked")
        val TOTAL_VIDEO_ADS_BLOCKED = longPreferencesKey("total_video_ads_blocked")
        val TOTAL_TRACKERS_BLOCKED = longPreferencesKey("total_trackers_blocked")
        val TOTAL_DATA_SAVED_BYTES = longPreferencesKey("total_data_saved_bytes")
    }

    val preferencesFlow: Flow<BrowserPreferences> = context.applicationContext.browserDataStore.data.map { prefs ->
        val engineName = prefs[Keys.SEARCH_ENGINE] ?: SearchEngine.BRAVE.name
        val barPosName = prefs[Keys.URL_BAR_POSITION] ?: UrlBarPosition.TOP.name
        BrowserPreferences(
            isDarkTheme = prefs[Keys.IS_DARK_THEME] ?: false,
            adBlockEnabled = prefs[Keys.AD_BLOCK_ENABLED] ?: true,
            videoAdSkipperEnabled = prefs[Keys.VIDEO_AD_SKIPPER_ENABLED] ?: true,
            trackerBlockEnabled = prefs[Keys.TRACKER_BLOCK_ENABLED] ?: true,
            httpsEverywhere = prefs[Keys.HTTPS_EVERYWHERE] ?: true,
            forceDarkWebPages = prefs[Keys.FORCE_DARK_WEB_PAGES] ?: false,
            memorySaverEnabled = prefs[Keys.MEMORY_SAVER_ENABLED] ?: true,
            blockThirdPartyCookies = prefs[Keys.BLOCK_THIRD_PARTY_COOKIES] ?: true,
            downloadThreads = prefs[Keys.DOWNLOAD_THREADS] ?: 8,
            searchEngine = SearchEngine.entries.firstOrNull { it.name == engineName } ?: SearchEngine.BRAVE,
            urlBarPosition = UrlBarPosition.entries.firstOrNull { it.name == barPosName } ?: UrlBarPosition.TOP,
            compactSpeedDial = prefs[Keys.COMPACT_SPEED_DIAL] ?: false,
            totalAdsBlocked = prefs[Keys.TOTAL_ADS_BLOCKED] ?: 0L,
            totalVideoAdsBlocked = prefs[Keys.TOTAL_VIDEO_ADS_BLOCKED] ?: 0L,
            totalTrackersBlocked = prefs[Keys.TOTAL_TRACKERS_BLOCKED] ?: 0L,
            totalDataSavedBytes = prefs[Keys.TOTAL_DATA_SAVED_BYTES] ?: 0L
        )
    }

    suspend fun setDarkTheme(isDark: Boolean) {
        context.applicationContext.browserDataStore.edit { it[Keys.IS_DARK_THEME] = isDark }
    }

    suspend fun setAdBlockEnabled(enabled: Boolean) {
        context.applicationContext.browserDataStore.edit { it[Keys.AD_BLOCK_ENABLED] = enabled }
    }

    suspend fun setVideoAdSkipperEnabled(enabled: Boolean) {
        context.applicationContext.browserDataStore.edit { it[Keys.VIDEO_AD_SKIPPER_ENABLED] = enabled }
    }

    suspend fun setTrackerBlockEnabled(enabled: Boolean) {
        context.applicationContext.browserDataStore.edit { it[Keys.TRACKER_BLOCK_ENABLED] = enabled }
    }

    suspend fun setHttpsEverywhere(enabled: Boolean) {
        context.applicationContext.browserDataStore.edit { it[Keys.HTTPS_EVERYWHERE] = enabled }
    }

    suspend fun setForceDarkWebPages(enabled: Boolean) {
        context.applicationContext.browserDataStore.edit { it[Keys.FORCE_DARK_WEB_PAGES] = enabled }
    }

    suspend fun setMemorySaverEnabled(enabled: Boolean) {
        context.applicationContext.browserDataStore.edit { it[Keys.MEMORY_SAVER_ENABLED] = enabled }
    }

    suspend fun setBlockThirdPartyCookies(enabled: Boolean) {
        context.applicationContext.browserDataStore.edit { it[Keys.BLOCK_THIRD_PARTY_COOKIES] = enabled }
    }

    suspend fun setDownloadThreads(threads: Int) {
        context.applicationContext.browserDataStore.edit { it[Keys.DOWNLOAD_THREADS] = threads.coerceIn(1, 16) }
    }

    suspend fun setSearchEngine(engine: SearchEngine) {
        context.applicationContext.browserDataStore.edit { it[Keys.SEARCH_ENGINE] = engine.name }
    }

    suspend fun setUrlBarPosition(position: UrlBarPosition) {
        context.applicationContext.browserDataStore.edit { it[Keys.URL_BAR_POSITION] = position.name }
    }

    suspend fun setCompactSpeedDial(compact: Boolean) {
        context.applicationContext.browserDataStore.edit { it[Keys.COMPACT_SPEED_DIAL] = compact }
    }

    suspend fun recordShieldInterception(
        adsDelta: Int = 0,
        videoAdsDelta: Int = 0,
        trackersDelta: Int = 0,
        bytesSavedDelta: Long = 0L
    ) {
        context.applicationContext.browserDataStore.edit { prefs ->
            if (adsDelta > 0) {
                prefs[Keys.TOTAL_ADS_BLOCKED] = (prefs[Keys.TOTAL_ADS_BLOCKED] ?: 0L) + adsDelta
            }
            if (videoAdsDelta > 0) {
                prefs[Keys.TOTAL_VIDEO_ADS_BLOCKED] = (prefs[Keys.TOTAL_VIDEO_ADS_BLOCKED] ?: 0L) + videoAdsDelta
            }
            if (trackersDelta > 0) {
                prefs[Keys.TOTAL_TRACKERS_BLOCKED] = (prefs[Keys.TOTAL_TRACKERS_BLOCKED] ?: 0L) + trackersDelta
            }
            if (bytesSavedDelta > 0L) {
                prefs[Keys.TOTAL_DATA_SAVED_BYTES] = (prefs[Keys.TOTAL_DATA_SAVED_BYTES] ?: 0L) + bytesSavedDelta
            }
        }
    }
}
