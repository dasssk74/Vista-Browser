package com.example.ui.components

import android.app.Activity
import android.content.Intent
import android.speech.RecognizerIntent
import android.speech.tts.TextToSpeech
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.SearchEngine
import com.example.engine.AiSearchOverviewData
import com.example.engine.AiSearchUiState
import com.example.engine.PeopleAlsoAskItem
import com.example.engine.WebSiteResult
import kotlinx.coroutines.delay
import java.util.Locale

private val SEARCH_TABS = listOf("All", "Images", "Videos", "Forums", "Shopping", "News")

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun VistaSearchOverviewScreen(
    searchState: AiSearchUiState,
    searchEngine: SearchEngine,
    isDarkTheme: Boolean = false,
    onOpenWebsiteUrl: (String) -> Unit,
    onOpenUrlInNewTab: (String) -> Unit = onOpenWebsiteUrl,
    onBookmarkSite: (title: String, url: String) -> Unit = { _, _ -> },
    onSearchFollowUpQuery: (String) -> Unit,
    onOpenFullSearchEngineWebView: (String) -> Unit,
    onRetryAiSearch: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current

    val activeQuery = when (searchState) {
        is AiSearchUiState.Success -> searchState.data.query
        is AiSearchUiState.Error -> searchState.query
        AiSearchUiState.Idle -> ""
    }

    var searchBoxInput by remember(activeQuery) { mutableStateOf(activeQuery) }
    var selectedTab by remember { mutableStateOf("All") }

    // Text-to-Speech for the blue Speaker button next to "AI Overview"
    var ttsEngine by remember { mutableStateOf<TextToSpeech?>(null) }
    var isSpeaking by remember { mutableStateOf(false) }

    DisposableEffect(context) {
        var engine: TextToSpeech? = null
        engine = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                engine?.language = Locale.getDefault()
            }
        }
        ttsEngine = engine
        onDispose {
            engine?.stop()
            engine?.shutdown()
        }
    }

    // Voice search launcher for the Microphone icon inside the floating search pill
    val voiceLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val spoken = result.data
                ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                ?.firstOrNull()
            if (!spoken.isNullOrBlank()) {
                searchBoxInput = spoken
                onSearchFollowUpQuery(spoken)
            }
        }
    }

    val pageBackground = if (isDarkTheme) Color(0xFF121316) else Color.White
    val sectionDividerColor = if (isDarkTheme) Color(0xFF202124) else Color(0xFFF1F3F4)
    val primaryTextColor = if (isDarkTheme) Color(0xFFE8EAED) else Color(0xFF202124)
    val secondaryTextColor = if (isDarkTheme) Color(0xFF9AA0A6) else Color(0xFF4D5156)

    val availableSites = when (searchState) {
        is AiSearchUiState.Success -> searchState.data.availableSites
        is AiSearchUiState.Error -> searchState.fallbackSites
        else -> emptyList()
    }

    val filteredSites = remember(availableSites, selectedTab) {
        when (selectedTab) {
            "Videos" -> {
                val matches = availableSites.filter {
                    it.category.equals("Videos", ignoreCase = true) ||
                        it.domain.contains("youtube") ||
                        it.domain.contains("vimeo")
                }
                matches.ifEmpty { availableSites }
            }
            "Forums" -> {
                val matches = availableSites.filter {
                    it.category.equals("Forums", ignoreCase = true) ||
                        it.domain.contains("reddit") ||
                        it.domain.contains("quora") ||
                        it.domain.contains("stackexchange") ||
                        it.domain.contains("stackoverflow")
                }
                matches.ifEmpty { availableSites }
            }
            else -> availableSites
        }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(pageBackground)
            .testTag("vista_ai_search_results_screen")
    ) {
        // 1. Floating Google-Style Search Pill + Filter Tabs ("All", "Images", "Videos", "Forums", "Shopping", "News")
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(pageBackground)
                    .padding(top = 12.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(28.dp),
                    color = if (isDarkTheme) Color(0xFF202124) else Color.White,
                    shadowElevation = 4.dp,
                    tonalElevation = 2.dp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .height(52.dp)
                        .border(
                            width = 1.dp,
                            color = if (isDarkTheme) Color(0xFF3C4043) else Color(0xFFE8EAED),
                            shape = RoundedCornerShape(28.dp)
                        )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Search",
                            tint = secondaryTextColor,
                            modifier = Modifier.size(22.dp)
                        )

                        Spacer(modifier = Modifier.width(12.dp))

                        Box(
                            modifier = Modifier.weight(1f),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            if (searchBoxInput.isEmpty()) {
                                Text(
                                    text = "Search Vista or type a question...",
                                    fontSize = 16.sp,
                                    color = secondaryTextColor,
                                    maxLines = 1
                                )
                            }
                            BasicTextField(
                                value = searchBoxInput,
                                onValueChange = { searchBoxInput = it },
                                singleLine = true,
                                textStyle = MaterialTheme.typography.bodyLarge.copy(
                                    fontSize = 16.sp,
                                    color = primaryTextColor
                                ),
                                cursorBrush = SolidColor(Color(0xFF1A73E8)),
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                                keyboardActions = KeyboardActions(
                                    onSearch = {
                                        if (searchBoxInput.isNotBlank()) {
                                            focusManager.clearFocus()
                                            onSearchFollowUpQuery(searchBoxInput.trim())
                                        }
                                    }
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("results_page_search_pill_input")
                            )
                        }

                        if (searchBoxInput.isNotEmpty()) {
                            IconButton(
                                onClick = { searchBoxInput = "" },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Clear search",
                                    tint = secondaryTextColor,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }

                        // Vertical Divider "|" between X and Microphone
                        Box(
                            modifier = Modifier
                                .padding(horizontal = 6.dp)
                                .width(1.dp)
                                .height(24.dp)
                                .background(if (isDarkTheme) Color(0xFF3C4043) else Color(0xFFDADCE0))
                        )

                        IconButton(
                            onClick = {
                                try {
                                    val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                                        putExtra(
                                            RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                                            RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
                                        )
                                        putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak to search Vista")
                                    }
                                    voiceLauncher.launch(intent)
                                } catch (e: Exception) {
                                    // Ignore if speech recognizer activity is absent on device
                                }
                            },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Mic,
                                contentDescription = "Voice Search",
                                tint = secondaryTextColor,
                                modifier = Modifier.size(21.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Horizontal Filter Tabs Row: "All", "Images", "Videos", "Forums", "Shopping", "News"
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(24.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SEARCH_TABS.forEach { tabName ->
                        val isSelected = selectedTab == tabName
                        val tabTextColor by animateColorAsState(
                            targetValue = if (isSelected) primaryTextColor else secondaryTextColor,
                            animationSpec = tween(220),
                            label = "tabColor"
                        )
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .clickable {
                                    if (tabName == "Images" || tabName == "Shopping" || tabName == "News") {
                                        val suffix = when (tabName) {
                                            "Images" -> "&tbm=isch"
                                            "Shopping" -> "&tbm=shop"
                                            else -> "&tbm=nws"
                                        }
                                        onOpenFullSearchEngineWebView(
                                            "https://www.google.com/search?q=${android.net.Uri.encode(activeQuery)}$suffix"
                                        )
                                    } else {
                                        selectedTab = tabName
                                    }
                                }
                                .padding(top = 4.dp)
                        ) {
                            Text(
                                text = tabName,
                                fontSize = 14.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = tabTextColor,
                                modifier = Modifier.padding(bottom = 10.dp)
                            )
                            Box(
                                modifier = Modifier
                                    .width(if (isSelected) 28.dp else 0.dp)
                                    .height(3.dp)
                                    .clip(RoundedCornerShape(999.dp))
                                    .background(if (isSelected) primaryTextColor else Color.Transparent)
                            )
                        }
                    }
                }

                HorizontalDivider(
                    thickness = 1.dp,
                    color = if (isDarkTheme) Color(0xFF303134) else Color(0xFFE8EAED)
                )
            }
        }

        // 2. Direct AI Overview Section (shown directly at the top of the Search Results page)
        item {
            when (searchState) {
                is AiSearchUiState.Success -> {
                    if (searchState.data.isAiOverviewGenerating) {
                        GoogleStyleAiOverviewLoadingSection(
                            query = searchState.data.query,
                            isDarkTheme = isDarkTheme,
                            primaryTextColor = primaryTextColor,
                            secondaryTextColor = secondaryTextColor
                        )
                    } else {
                        GoogleStyleDirectAiOverviewSection(
                            data = searchState.data,
                            isDarkTheme = isDarkTheme,
                            pageBackground = pageBackground,
                            primaryTextColor = primaryTextColor,
                            secondaryTextColor = secondaryTextColor,
                            isSpeaking = isSpeaking,
                            onToggleSpeak = {
                                val engine = ttsEngine ?: return@GoogleStyleDirectAiOverviewSection
                                if (isSpeaking) {
                                    engine.stop()
                                    isSpeaking = false
                                } else {
                                    val speechText = buildString {
                                        append(searchState.data.leadSentence)
                                        append(" ")
                                        append(searchState.data.sectionHeading)
                                        append(". ")
                                        searchState.data.keyPoints.forEach {
                                            append(it)
                                            append(" ")
                                        }
                                    }
                                    engine.speak(speechText, TextToSpeech.QUEUE_FLUSH, null, "vista_ai_overview")
                                    isSpeaking = true
                                }
                            },
                            onFollowUpClick = { followUp ->
                                searchBoxInput = followUp
                                onSearchFollowUpQuery(followUp)
                            }
                        )
                    }
                }
                is AiSearchUiState.Error -> {
                    AiOverviewErrorSection(
                        errorMessage = searchState.message,
                        onRetry = { onRetryAiSearch(searchState.query) },
                        onOpenWebSearch = {
                            onOpenFullSearchEngineWebView(
                                searchEngine.queryUrlPrefix + android.net.Uri.encode(searchState.query)
                            )
                        }
                    )
                }
                AiSearchUiState.Idle -> {}
            }
        }

        // 3. Thick Section Divider below AI Overview (matches the 8dp light-gray bar in the screenshot)
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .background(sectionDividerColor)
            )
        }

        // 4. Direct Search Results (shown immediately alongside the AI Overview!)
        if (filteredSites.isNotEmpty()) {
            itemsIndexed(filteredSites, key = { _, site -> site.url }) { index, site ->
                AnimatedWebResultItem(
                    index = index,
                    site = site,
                    isDarkTheme = isDarkTheme,
                    primaryTextColor = primaryTextColor,
                    secondaryTextColor = secondaryTextColor,
                    sectionDividerColor = sectionDividerColor,
                    onClick = { onOpenWebsiteUrl(site.url) },
                    onOpenInNewTab = { onOpenUrlInNewTab(site.url) },
                    onBookmark = { onBookmarkSite(site.title, site.url) }
                )
            }
        }

        // 5. "People also ask" Accordion Section (matches the bottom of the screenshot)
        val peopleAlsoAsk = (searchState as? AiSearchUiState.Success)?.data?.peopleAlsoAsk ?: emptyList()
        if (peopleAlsoAsk.isNotEmpty()) {
            item {
                PeopleAlsoAskSection(
                    items = peopleAlsoAsk,
                    isDarkTheme = isDarkTheme,
                    primaryTextColor = primaryTextColor,
                    secondaryTextColor = secondaryTextColor,
                    sectionDividerColor = sectionDividerColor,
                    onSearchQuestion = { q ->
                        searchBoxInput = q
                        onSearchFollowUpQuery(q)
                    }
                )
            }
        }
    }
}

/**
 * Compact inline shimmer while AI Overview is streaming above the already-visible search results
 */
@Composable
private fun GoogleStyleAiOverviewLoadingSection(
    query: String,
    isDarkTheme: Boolean,
    primaryTextColor: Color,
    secondaryTextColor: Color
) {
    val infiniteTransition = rememberInfiniteTransition(label = "aiLoadingShimmer")
    val starRotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2400, easing = LinearEasing)
        ),
        label = "starRotation"
    )
    val starScale by infiniteTransition.animateFloat(
        initialValue = 0.85f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "starScale"
    )
    val shimmerOffset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1000f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200, easing = LinearEasing)
        ),
        label = "shimmerOffset"
    )

    val shimmerBrush = Brush.linearGradient(
        colors = if (isDarkTheme) {
            listOf(Color(0xFF202124), Color(0xFF30394A), Color(0xFF202124))
        } else {
            listOf(Color(0xFFE8F0FE), Color(0xFFD2E3FC), Color(0xFFE8F0FE))
        },
        start = Offset(shimmerOffset - 400f, 0f),
        end = Offset(shimmerOffset, 0f)
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 16.dp)
            .testTag("ai_overview_loading_card")
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            FourPointBlueSparkleIcon(
                modifier = Modifier
                    .size(18.dp)
                    .scale(starScale)
                    .rotate(starRotation)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "AI Overview",
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                color = secondaryTextColor
            )
        }

        Spacer(modifier = Modifier.height(14.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .height(18.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(shimmerBrush)
        )
        Spacer(modifier = Modifier.height(10.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth(0.55f)
                .height(20.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(shimmerBrush)
        )
        Spacer(modifier = Modifier.height(12.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(14.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(shimmerBrush)
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "Synthesizing direct AI Overview for \"$query\"...",
            fontSize = 12.sp,
            color = secondaryTextColor
        )
    }
}

/**
 * Direct "✦ AI Overview" section rendered right above the search results:
 * - Four-point blue star + "AI Overview" + circular light-blue speaker button
 * - Direct answer sentence with soft blue highlighted phrase ("the second")
 * - Bold section heading ("Official Definition")
 * - Bullet points + "Show more ⌄" pill button
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun GoogleStyleDirectAiOverviewSection(
    data: AiSearchOverviewData,
    isDarkTheme: Boolean,
    pageBackground: Color,
    primaryTextColor: Color,
    secondaryTextColor: Color,
    isSpeaking: Boolean,
    onToggleSpeak: () -> Unit,
    onFollowUpClick: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val chevronRotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = tween(durationMillis = 280, easing = FastOutSlowInEasing),
        label = "chevronRotation"
    )

    val speakerPulseTransition = rememberInfiniteTransition(label = "speakerPulse")
    val speakerScale by speakerPulseTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (isSpeaking) 1.14f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(550, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "speakerScale"
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioNoBouncy,
                    stiffness = Spring.StiffnessMediumLow
                )
            )
            .padding(horizontal = 16.dp, vertical = 16.dp)
            .testTag("ai_overview_card")
    ) {
        // 1. Header Row: 4-Point Blue Star + "AI Overview" + Circular Light-Blue Speaker Icon
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            FourPointBlueSparkleIcon(
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "AI Overview",
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                color = if (isDarkTheme) Color(0xFFBDC1C6) else Color(0xFF4D5156)
            )
            Spacer(modifier = Modifier.width(12.dp))

            Box(
                modifier = Modifier
                    .size(34.dp)
                    .scale(speakerScale)
                    .clip(CircleShape)
                    .background(if (isDarkTheme) Color(0xFF1E3A5F) else Color(0xFFD2E3FC))
                    .clickable(onClick = onToggleSpeak)
                    .testTag("ai_overview_speaker_button"),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isSpeaking) Icons.Default.Stop else Icons.AutoMirrored.Filled.VolumeUp,
                    contentDescription = "Listen to AI Overview",
                    tint = if (isDarkTheme) Color(0xFF8AB4F8) else Color(0xFF1967D2),
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // 2. Direct Lead Sentence with Blue Highlighted Key Phrase (e.g. "The SI unit of time is [the second] (symbol: s).")
        val highlightBg = if (isDarkTheme) Color(0xFF1E3A5F) else Color(0xFFD2E3FC)
        val highlightFg = if (isDarkTheme) Color(0xFFD2E3FC) else Color(0xFF041E49)

        val annotatedLead = remember(data.leadSentence, data.highlightedPhrase, isDarkTheme) {
            buildAnnotatedString {
                val lead = data.leadSentence
                val phrase = data.highlightedPhrase.trim()
                val startIdx = if (phrase.isNotEmpty()) {
                    lead.indexOf(phrase, ignoreCase = true)
                } else {
                    -1
                }
                if (startIdx >= 0) {
                    val endIdx = startIdx + phrase.length
                    append(lead.substring(0, startIdx))
                    withStyle(
                        SpanStyle(
                            background = highlightBg,
                            color = highlightFg,
                            fontWeight = FontWeight.Medium
                        )
                    ) {
                        append(" ${lead.substring(startIdx, endIdx)} ")
                    }
                    append(lead.substring(endIdx))
                } else {
                    append(lead)
                }
            }
        }

        Text(
            text = annotatedLead,
            fontSize = 17.sp,
            lineHeight = 25.sp,
            color = primaryTextColor
        )

        Spacer(modifier = Modifier.height(16.dp))

        // 3. Bold Section Heading (e.g. "Official Definition")
        Text(
            text = data.sectionHeading,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = primaryTextColor
        )

        Spacer(modifier = Modifier.height(10.dp))

        // 4. Bullet Points + Optional Collapsed Gradient Fade
        Box(modifier = Modifier.fillMaxWidth()) {
            val visibleBullets = if (expanded) {
                data.keyPoints
            } else {
                data.keyPoints.take(1)
            }

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                visibleBullets.forEach { bullet ->
                    Row(
                        verticalAlignment = Alignment.Top,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "•",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = primaryTextColor,
                            modifier = Modifier.padding(start = 6.dp, end = 12.dp)
                        )
                        Text(
                            text = bullet,
                            fontSize = 16.sp,
                            lineHeight = 24.sp,
                            color = primaryTextColor,
                            maxLines = if (expanded) Int.MAX_VALUE else 5,
                            overflow = TextOverflow.Clip
                        )
                    }
                }

                if (expanded && data.aiOverviewSummary.isNotBlank()) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = data.aiOverviewSummary,
                        fontSize = 15.sp,
                        lineHeight = 23.sp,
                        color = secondaryTextColor
                    )
                }

                if (expanded && data.followUpQueries.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Explore related searches",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = secondaryTextColor
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        data.followUpQueries.forEach { followUp ->
                            AssistChip(
                                onClick = { onFollowUpClick(followUp) },
                                label = { Text(followUp, fontSize = 13.sp) },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.Search,
                                        contentDescription = null,
                                        modifier = Modifier.size(15.dp)
                                    )
                                }
                            )
                        }
                    }
                }
            }

            // Bottom Fade Overlay when collapsed (exact match to screenshot)
            if (!expanded) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .align(Alignment.BottomCenter)
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color.Transparent,
                                    pageBackground.copy(alpha = 0.78f),
                                    pageBackground
                                )
                            )
                        )
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // 5. Full-Width Rounded Pill "Show more ⌄" Button matching the screenshot
        Surface(
            shape = RoundedCornerShape(999.dp),
            color = if (isDarkTheme) Color(0xFF202124) else Color.White,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .border(
                    width = 1.dp,
                    color = if (isDarkTheme) Color(0xFF3C4043) else Color(0xFFD2E3FC),
                    shape = RoundedCornerShape(999.dp)
                )
                .clip(RoundedCornerShape(999.dp))
                .clickable { expanded = !expanded }
                .testTag("ai_overview_show_more_button")
        ) {
            Row(
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (expanded) "Show less" else "Show more",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    color = primaryTextColor
                )
                Spacer(modifier = Modifier.width(6.dp))
                Icon(
                    imageVector = Icons.Default.KeyboardArrowDown,
                    contentDescription = null,
                    tint = Color(0xFF1A73E8),
                    modifier = Modifier
                        .size(20.dp)
                        .rotate(chevronRotation)
                )
            }
        }
    }
}

/**
 * Custom 4-Point Blue Star Icon matching the Google "✦ AI Overview" sparkle
 */
@Composable
private fun FourPointBlueSparkleIcon(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val cx = w / 2f
        val cy = h / 2f
        val inner = w * 0.18f

        val path = Path().apply {
            moveTo(cx, 0f)
            quadraticTo(cx + inner * 0.4f, cy - inner * 0.4f, w, cy)
            quadraticTo(cx + inner * 0.4f, cy + inner * 0.4f, cx, h)
            quadraticTo(cx - inner * 0.4f, cy + inner * 0.4f, 0f, cy)
            quadraticTo(cx - inner * 0.4f, cy - inner * 0.4f, cx, 0f)
            close()
        }

        drawPath(
            path = path,
            brush = Brush.linearGradient(
                colors = listOf(
                    Color(0xFF1A73E8),
                    Color(0xFF4285F4)
                )
            )
        )
    }
}

/**
 * Staggered-entrance Google Search Result Card matching the BYJU'S card in the screenshot:
 * - Circular site logo + Site Name ("BYJU'S") + URL ("https://byjus.com") + 3-dot icon
 * - Large blue title ("SI Unit of Time")
 * - 3-line gray snippet
 */
@Composable
private fun AnimatedWebResultItem(
    index: Int,
    site: WebSiteResult,
    isDarkTheme: Boolean,
    primaryTextColor: Color,
    secondaryTextColor: Color,
    sectionDividerColor: Color,
    onClick: () -> Unit,
    onOpenInNewTab: () -> Unit,
    onBookmark: () -> Unit
) {
    var visible by remember { mutableStateOf(false) }
    var showSiteMenu by remember { mutableStateOf(false) }

    LaunchedEffect(site.url) {
        delay((index.coerceAtMost(6) * 45L))
        visible = true
    }

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(240)) + slideInVertically(tween(240)) { it / 5 }
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onClick)
                    .padding(horizontal = 16.dp, vertical = 16.dp)
                    .testTag("web_result_card_${site.domain}")
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                    ) {
                        WebsiteLogoBadge(
                            name = site.siteName,
                            url = site.url,
                            logoUrl = site.faviconUrl,
                            sizeDp = 32
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = site.siteName,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Normal,
                            color = primaryTextColor,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "https://${site.domain}",
                            fontSize = 12.sp,
                            color = secondaryTextColor,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Box {
                        IconButton(
                            onClick = { showSiteMenu = true },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.MoreVert,
                                contentDescription = "Result options",
                                tint = secondaryTextColor,
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        DropdownMenu(
                            expanded = showSiteMenu,
                            onDismissRequest = { showSiteMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Open in New Tab") },
                                leadingIcon = { Icon(Icons.Default.OpenInBrowser, contentDescription = null) },
                                onClick = {
                                    showSiteMenu = false
                                    onOpenInNewTab()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Bookmark Website") },
                                leadingIcon = { Icon(Icons.Default.BookmarkAdd, contentDescription = null) },
                                onClick = {
                                    showSiteMenu = false
                                    onBookmark()
                                }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = site.title,
                    fontSize = 20.sp,
                    lineHeight = 26.sp,
                    fontWeight = FontWeight.Normal,
                    color = if (isDarkTheme) Color(0xFF8AB4F8) else Color(0xFF1967D2),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                if (site.snippet.isNotBlank()) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = site.snippet,
                        fontSize = 14.sp,
                        lineHeight = 20.sp,
                        color = secondaryTextColor,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .background(sectionDividerColor)
            )
        }
    }
}

@Composable
private fun PeopleAlsoAskSection(
    items: List<PeopleAlsoAskItem>,
    isDarkTheme: Boolean,
    primaryTextColor: Color,
    secondaryTextColor: Color,
    sectionDividerColor: Color,
    onSearchQuestion: (String) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 16.dp)
        ) {
            Text(
                text = "People also ask",
                fontSize = 20.sp,
                fontWeight = FontWeight.Medium,
                color = primaryTextColor,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            items.forEachIndexed { idx, item ->
                var expanded by remember { mutableStateOf(false) }
                val rotation by animateFloatAsState(
                    targetValue = if (expanded) 180f else 0f,
                    animationSpec = tween(240),
                    label = "paaChevron_$idx"
                )

                if (idx > 0) {
                    HorizontalDivider(
                        color = if (isDarkTheme) Color(0xFF303134) else Color(0xFFE8EAED)
                    )
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { expanded = !expanded }
                        .padding(vertical = 14.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = item.question,
                            fontSize = 16.sp,
                            color = primaryTextColor,
                            modifier = Modifier
                                .weight(1f)
                                .padding(end = 12.dp)
                        )
                        Icon(
                            imageVector = Icons.Default.KeyboardArrowDown,
                            contentDescription = "Expand answer",
                            tint = secondaryTextColor,
                            modifier = Modifier
                                .size(22.dp)
                                .rotate(rotation)
                        )
                    }

                    AnimatedVisibility(
                        visible = expanded,
                        enter = expandVertically(tween(240)) + fadeIn(tween(240)),
                        exit = shrinkVertically(tween(200)) + fadeOut(tween(200))
                    ) {
                        Column(modifier = Modifier.padding(top = 10.dp)) {
                            Text(
                                text = item.answer,
                                fontSize = 15.sp,
                                lineHeight = 22.sp,
                                color = secondaryTextColor
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Search for \"${item.question}\"",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium,
                                color = if (isDarkTheme) Color(0xFF8AB4F8) else Color(0xFF1967D2),
                                modifier = Modifier.clickable { onSearchQuestion(item.question) }
                            )
                        }
                    }
                }
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .background(sectionDividerColor)
        )
    }
}

@Composable
private fun AiOverviewErrorSection(
    errorMessage: String,
    onRetry: () -> Unit,
    onOpenWebSearch: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
            .testTag("ai_overview_error_card")
    ) {
        Text(
            text = "AI Overview Unavailable",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = errorMessage,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(onClick = onRetry) {
                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Retry AI Overview")
            }
            OutlinedButton(onClick = onOpenWebSearch) {
                Text("Search Web Directly")
            }
        }
    }
}
