package com.example.ui.components

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.graphics.drawable.Icon as AndroidIcon
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.AddToHomeScreen
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import com.example.MainActivity
import com.example.R
import com.example.data.model.ShortcutItem
import com.example.data.repository.BrowserPreferences
import com.example.data.repository.BrowserSyncRepository
import com.example.ui.UiState

@OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
fun HomepageSpeedDial(
    preferences: BrowserPreferences,
    shortcutsState: UiState<List<ShortcutItem>>,
    onNavigateToUrlOrSearch: (String) -> Unit,
    onAddShortcut: (name: String, url: String) -> Unit,
    onDeleteShortcut: (String) -> Unit,
    onOpenShieldSheet: () -> Unit,
    onOpenDownloadsSheet: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var showAddShortcutDialog by remember { mutableStateOf(false) }
    var selectedShortcutForAction by remember { mutableStateOf<ShortcutItem?>(null) }
    var showPrivacyStatsNumbers by remember { mutableStateOf(true) }
    var showBottomPromoCard by remember { mutableStateOf(true) }

    val shortcutsList = when (shortcutsState) {
        is UiState.Success -> shortcutsState.data
        else -> emptyList()
    }

    val totalTrackersAndAds = preferences.totalAdsBlocked +
        preferences.totalVideoAdsBlocked +
        preferences.totalTrackersBlocked
    val dataSavedKb = (preferences.totalDataSavedBytes / 1024L)
    val estTimeSavedSec = (totalTrackersAndAds * 1L) / 2L

    Box(
        modifier = modifier
            .fillMaxSize()
            .testTag("homepage_speed_dial_grid")
    ) {
        // 1. Full-bleed Aerial Coastal Rocky Ocean Wallpaper matching the user's screenshot
        Image(
            painter = painterResource(id = R.drawable.img_vista_ocean_wallpaper_1794060224400),
            contentDescription = "Vista Ocean Coast Wallpaper",
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )

        // 2. Top Frosted Glass Cards ("Privacy Stats" + "+ Add new" Shortcuts Container)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 640.dp)
                .align(Alignment.TopCenter)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // CARD 1: Privacy Stats Frosted Dark Glass Card
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = Color(0x8A151A24),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("privacy_stats_glass_card")
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp)
                ) {
                    // Header row: Shield icon + "Privacy Stats" + Eye-Off icon
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.VerifiedUser,
                                contentDescription = null,
                                tint = Color(0xFFD1D5DB),
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Privacy Stats",
                                style = MaterialTheme.typography.titleMedium,
                                color = Color.White,
                                fontWeight = FontWeight.Medium
                            )
                        }

                        IconButton(
                            onClick = { showPrivacyStatsNumbers = !showPrivacyStatsNumbers },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                imageVector = if (showPrivacyStatsNumbers) {
                                    Icons.Default.VisibilityOff
                                } else {
                                    Icons.Default.Visibility
                                },
                                contentDescription = "Toggle Privacy Stats Visibility",
                                tint = Color(0xFFD1D5DB),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    AnimatedVisibility(visible = showPrivacyStatsNumbers) {
                        Column {
                            Spacer(modifier = Modifier.height(14.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.Top
                            ) {
                                // Column 1: Trackers & Ads Blocked (Orange/Coral)
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text(
                                        text = totalTrackersAndAds.toString(),
                                        fontSize = 32.sp,
                                        fontWeight = FontWeight.Normal,
                                        color = Color(0xFFFF6347)
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "Trackers & Ads\nBlocked",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color(0xFFE5E7EB),
                                        textAlign = TextAlign.Center,
                                        lineHeight = 16.sp
                                    )
                                }

                                // Column 2: Est. Data Saved (Light Periwinkle Blue)
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    val dataValueStr = if (dataSavedKb >= 1024L) {
                                        String.format("%.1f", dataSavedKb / 1024f)
                                    } else {
                                        dataSavedKb.toString()
                                    }
                                    val dataUnitStr = if (dataSavedKb >= 1024L) "MB" else "KB"
                                    Text(
                                        text = buildAnnotatedString {
                                            withStyle(SpanStyle(fontSize = 32.sp, color = Color(0xFF8AB4F8))) {
                                                append(dataValueStr)
                                            }
                                            withStyle(SpanStyle(fontSize = 20.sp, color = Color(0xFF93C5FD))) {
                                                append(dataUnitStr)
                                            }
                                        }
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "Est. Data\nSaved",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color(0xFFE5E7EB),
                                        textAlign = TextAlign.Center,
                                        lineHeight = 16.sp
                                    )
                                }

                                // Column 3: Est. Time Saved (White)
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text(
                                        text = buildAnnotatedString {
                                            withStyle(SpanStyle(fontSize = 32.sp, color = Color.White)) {
                                                append(estTimeSavedSec.toString())
                                            }
                                            withStyle(SpanStyle(fontSize = 20.sp, color = Color.White)) {
                                                append("s")
                                            }
                                        }
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "Est. Time\nSaved",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color(0xFFE5E7EB),
                                        textAlign = TextAlign.Center,
                                        lineHeight = 16.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // CARD 2: Frosted Dark Glass Shortcuts Card with Circular White "+" ("Add new")
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = Color(0x78151A24),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("shortcuts_glass_card")
            ) {
                FlowRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 18.dp),
                    horizontalArrangement = Arrangement.spacedBy(20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Saved website shortcuts
                    shortcutsList.forEach { shortcut ->
                        GlassShortcutCircleItem(
                            shortcut = shortcut,
                            onClick = { onNavigateToUrlOrSearch(shortcut.url) },
                            onLongClick = { selectedShortcutForAction = shortcut }
                        )
                    }

                    // The iconic White Circular "+" ("Add new") button from the screenshot
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .width(72.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .clickable { showAddShortcutDialog = true }
                            .padding(vertical = 4.dp)
                            .testTag("add_shortcut_plus_tile")
                    ) {
                        Box(
                            modifier = Modifier
                                .size(60.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFF3F4F6)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = "Add new website shortcut",
                                tint = Color(0xFF374151),
                                modifier = Modifier.size(32.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Add new",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(220.dp))
        }

        // 3. Bottom "Photo by Dylan Malval" + Dismissible Translucent Bottom Card
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Text(
                text = "Photo by Dylan Malval",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(start = 6.dp, bottom = 10.dp)
            )

            AnimatedVisibility(visible = showBottomPromoCard) {
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = Color(0xB82D3142),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 14.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(end = 28.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Quick access chips inside the translucent bottom card
                            val isShieldOn = preferences.adBlockEnabled || preferences.videoAdSkipperEnabled
                            Surface(
                                shape = RoundedCornerShape(999.dp),
                                color = if (isShieldOn) Color(0xFFFF5722).copy(alpha = 0.28f) else Color.White.copy(alpha = 0.12f),
                                modifier = Modifier
                                    .clip(RoundedCornerShape(999.dp))
                                    .clickable { onOpenShieldSheet() }
                                    .testTag("homepage_adblock_shield_card")
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.VerifiedUser,
                                        contentDescription = "Ad-Blocker Shield",
                                        tint = if (isShieldOn) Color(0xFFFF8A65) else Color.White,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = if (isShieldOn) "Ad Shield Active" else "Ad Shield Off",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = Color.White
                                    )
                                }
                            }

                            Surface(
                                shape = RoundedCornerShape(999.dp),
                                color = Color.White.copy(alpha = 0.12f),
                                modifier = Modifier
                                    .clip(RoundedCornerShape(999.dp))
                                    .clickable { onOpenDownloadsSheet() }
                                    .testTag("open_turbo_downloads_card")
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Download,
                                        contentDescription = "Turbo Downloads",
                                        tint = Color.White,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "${preferences.downloadThreads}x Downloads",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = Color.White
                                    )
                                }
                            }

                            Surface(
                                shape = RoundedCornerShape(999.dp),
                                color = Color.White.copy(alpha = 0.12f),
                                modifier = Modifier
                                    .clip(RoundedCornerShape(999.dp))
                                    .clickable { onNavigateToUrlOrSearch("aegis://shield-test") }
                                    .testTag("open_shield_test_lab_card")
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Science,
                                        contentDescription = "Video Ad Lab",
                                        tint = Color.White,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "Ad Lab",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = Color.White
                                    )
                                }
                            }
                        }

                        IconButton(
                            onClick = { showBottomPromoCard = false },
                            modifier = Modifier
                                .align(Alignment.CenterEnd)
                                .size(28.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Dismiss card",
                                tint = Color(0xFF9CA3AF),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }
        }
    }

    if (showAddShortcutDialog) {
        AddWebsiteShortcutDialog(
            onDismiss = { showAddShortcutDialog = false },
            onConfirm = { name, url ->
                onAddShortcut(name, url)
                showAddShortcutDialog = false
            }
        )
    }

    selectedShortcutForAction?.let { item ->
        ShortcutOptionsDialog(
            shortcut = item,
            onDismiss = { selectedShortcutForAction = null },
            onOpen = {
                selectedShortcutForAction = null
                onNavigateToUrlOrSearch(item.url)
            },
            onInstallToLauncher = {
                pinWebsiteShortcutToAndroidHomeScreen(context, item.name, item.url)
                selectedShortcutForAction = null
            },
            onDelete = {
                if (item.id.isNotBlank()) {
                    onDeleteShortcut(item.id)
                }
                selectedShortcutForAction = null
            }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun GlassShortcutCircleItem(
    shortcut: ShortcutItem,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(72.dp)
            .clip(RoundedCornerShape(14.dp))
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
            .padding(vertical = 4.dp)
            .testTag("shortcut_tile_${shortcut.name.lowercase().replace(" ", "_")}")
    ) {
        Surface(
            shape = CircleShape,
            color = Color(0xFFF3F4F6),
            modifier = Modifier.size(60.dp)
        ) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                WebsiteLogoBadge(
                    name = shortcut.name,
                    url = shortcut.url,
                    logoUrl = shortcut.logoUrl,
                    sizeDp = 42
                )
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = shortcut.name,
            style = MaterialTheme.typography.bodySmall,
            color = Color.White,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
fun WebsiteLogoBadge(
    name: String,
    url: String,
    logoUrl: String,
    sizeDp: Int = 46
) {
    val context = LocalContext.current
    val resolvedLogoUrl = logoUrl.ifBlank { BrowserSyncRepository.buildFaviconUrl(url) }
    val fallbackInitial = (
        name.trim().firstOrNull()
            ?: BrowserSyncRepository.extractDomainTitle(url).firstOrNull()
            ?: 'W'
        ).uppercaseChar()

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
        modifier = Modifier.size(sizeDp.dp)
    ) {
        if (resolvedLogoUrl.isNotBlank()) {
            SubcomposeAsyncImage(
                model = ImageRequest.Builder(context)
                    .data(resolvedLogoUrl)
                    .crossfade(true)
                    .build(),
                contentDescription = "$name logo",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(4.dp)
                    .clip(RoundedCornerShape(8.dp)),
                error = {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                        Text(
                            text = fallbackInitial.toString(),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                },
                loading = {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                        Text(
                            text = fallbackInitial.toString(),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            )
        } else {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                Text(
                    text = fallbackInitial.toString(),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
fun AddWebsiteShortcutDialog(
    initialName: String = "",
    initialUrl: String = "",
    onDismiss: () -> Unit,
    onConfirm: (name: String, url: String) -> Unit
) {
    var nameInput by remember { mutableStateOf(initialName) }
    var urlInput by remember { mutableStateOf(initialUrl) }
    val isUrlValid = remember(urlInput) { BrowserSyncRepository.isValidWebsiteUrl(urlInput) }
    val liveLogoUrl = remember(urlInput, isUrlValid) {
        if (isUrlValid) BrowserSyncRepository.buildFaviconUrl(urlInput) else ""
    }
    val autoInferredName = remember(urlInput, isUrlValid) {
        if (isUrlValid) BrowserSyncRepository.extractDomainTitle(urlInput) else "Website"
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringResource(R.string.add_shortcut_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        WebsiteLogoBadge(
                            name = nameInput.ifBlank { autoInferredName },
                            url = urlInput,
                            logoUrl = liveLogoUrl,
                            sizeDp = 52
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = nameInput.ifBlank { autoInferredName },
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = if (isUrlValid) Icons.Default.CheckCircle else Icons.Default.Language,
                                    contentDescription = null,
                                    tint = if (isUrlValid) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = if (isUrlValid) {
                                        "Valid URL • Logo auto-loaded (${BrowserSyncRepository.extractHost(urlInput)})"
                                    } else {
                                        "Enter a valid domain (e.g. youtube.com) to auto-load logo"
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (isUrlValid) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                OutlinedTextField(
                    value = nameInput,
                    onValueChange = { nameInput = it },
                    label = { Text(stringResource(R.string.shortcut_name_label)) },
                    placeholder = { Text("e.g. YouTube, Wikipedia, GitHub") },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("shortcut_name_input")
                )

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = urlInput,
                    onValueChange = { urlInput = it },
                    label = { Text(stringResource(R.string.shortcut_url_label)) },
                    placeholder = { Text("https://example.com") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Uri,
                        imeAction = ImeAction.Done
                    ),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("shortcut_url_input")
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val finalName = nameInput.trim().ifEmpty { autoInferredName }
                    onConfirm(finalName, urlInput.trim())
                },
                enabled = isUrlValid,
                modifier = Modifier.testTag("shortcut_ok_button")
            ) {
                Text(stringResource(R.string.ok_button))
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("shortcut_cancel_button")
            ) {
                Text(stringResource(R.string.cancel_button))
            }
        }
    )
}

@Composable
private fun ShortcutOptionsDialog(
    shortcut: ShortcutItem,
    onDismiss: () -> Unit,
    onOpen: () -> Unit,
    onInstallToLauncher: () -> Unit,
    onDelete: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                WebsiteLogoBadge(
                    name = shortcut.name,
                    url = shortcut.url,
                    logoUrl = shortcut.logoUrl,
                    sizeDp = 38
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(shortcut.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(shortcut.url, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = onOpen,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Public, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Open Website")
                }
                OutlinedButton(
                    onClick = onInstallToLauncher,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.AutoMirrored.Filled.AddToHomeScreen, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Install / Create Home Screen Shortcut")
                }
                OutlinedButton(
                    onClick = onDelete,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        Icons.Default.DeleteOutline,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Remove from Speed Dial", color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}

fun pinWebsiteShortcutToAndroidHomeScreen(
    context: Context,
    title: String,
    url: String
) {
    val cleanTitle = title.trim().ifEmpty { BrowserSyncRepository.extractDomainTitle(url) }
    val normalizedUrl = BrowserSyncRepository.normalizeWebsiteUrl(url)
    try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val shortcutManager = context.getSystemService(ShortcutManager::class.java)
            if (shortcutManager != null && shortcutManager.isRequestPinShortcutSupported) {
                val launchIntent = Intent(Intent.ACTION_VIEW, Uri.parse(normalizedUrl), context, MainActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                }
                val shortcutInfo = ShortcutInfo.Builder(context, "web_${normalizedUrl.hashCode()}")
                    .setShortLabel(cleanTitle.take(15))
                    .setLongLabel(cleanTitle.take(30))
                    .setIcon(AndroidIcon.createWithResource(context, R.mipmap.ic_launcher))
                    .setIntent(launchIntent)
                    .build()

                val pinnedCallbackIntent = shortcutManager.createShortcutResultIntent(shortcutInfo)
                val successCallback = PendingIntent.getBroadcast(
                    context,
                    0,
                    pinnedCallbackIntent,
                    PendingIntent.FLAG_IMMUTABLE
                )
                shortcutManager.requestPinShortcut(shortcutInfo, successCallback.intentSender)
                Toast.makeText(context, "Installing \"$cleanTitle\" shortcut to Home Screen", Toast.LENGTH_SHORT).show()
                return
            }
        }
        Toast.makeText(context, "Shortcut \"$cleanTitle\" installed to Vista Speed Dial & Launcher", Toast.LENGTH_SHORT).show()
    } catch (e: Exception) {
        Toast.makeText(context, "Shortcut \"$cleanTitle\" saved to Vista Speed Dial", Toast.LENGTH_SHORT).show()
    }
}
