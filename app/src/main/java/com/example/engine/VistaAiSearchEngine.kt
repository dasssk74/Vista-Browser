package com.example.engine

import android.net.Uri
import android.util.Log
import com.example.data.repository.BrowserSyncRepository
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLDecoder
import java.util.concurrent.TimeUnit

data class WebSiteResult(
    val siteName: String,
    val title: String,
    val url: String,
    val domain: String,
    val snippet: String,
    val faviconUrl: String,
    val category: String = "All"
)

data class PeopleAlsoAskItem(
    val question: String,
    val answer: String
)

data class AiSearchOverviewData(
    val query: String,
    val isAiOverviewGenerating: Boolean = false,
    val leadSentence: String = "",
    val highlightedPhrase: String = "",
    val sectionHeading: String = "Official Definition",
    val aiOverviewSummary: String = "",
    val keyPoints: List<String> = emptyList(),
    val followUpQueries: List<String> = emptyList(),
    val peopleAlsoAsk: List<PeopleAlsoAskItem> = emptyList(),
    val availableSites: List<WebSiteResult> = emptyList()
)

sealed interface AiSearchUiState {
    data object Idle : AiSearchUiState
    data class Success(val data: AiSearchOverviewData) : AiSearchUiState
    data class Error(
        val query: String,
        val message: String,
        val fallbackSites: List<WebSiteResult>
    ) : AiSearchUiState
}

class VistaAiSearchEngine {

    private val searchSchema = Schema.obj(
        properties = mapOf(
            "leadSentence" to Schema.string(
                description = "A direct, single-sentence answer to the user's query (e.g. 'The SI unit of time is the second (symbol: s).')."
            ),
            "highlightedPhrase" to Schema.string(
                description = "The exact 1-to-5 word core answer phrase inside leadSentence to highlight in blue (e.g. 'the second')."
            ),
            "sectionHeading" to Schema.string(
                description = "A concise 2-to-3 word heading for the bullet points section (e.g. 'Official Definition', 'Key Facts', or 'Overview')."
            ),
            "summary" to Schema.string(
                description = "A clear 2-sentence explanation expanding on the answer."
            ),
            "keyPoints" to Schema.array(
                items = Schema.string(),
                description = "2 to 3 detailed, informative bullet points explaining the core definition and facts."
            ),
            "followUpQueries" to Schema.array(
                items = Schema.string(),
                description = "3 helpful follow-up search queries related to this topic."
            ),
            "peopleAlsoAsk" to Schema.array(
                items = Schema.obj(
                    properties = mapOf(
                        "question" to Schema.string(description = "A related question people frequently ask about this topic"),
                        "answer" to Schema.string(description = "A clear 2-sentence answer to that related question")
                    )
                ),
                description = "4 related questions and answers for the 'People also ask' section."
            ),
            "sites" to Schema.array(
                items = Schema.obj(
                    properties = mapOf(
                        "siteName" to Schema.string(description = "Brand or publisher name of the website (e.g. 'BYJU\\'S', 'Wikipedia', 'Britannica', 'NIST', 'Khan Academy')"),
                        "title" to Schema.string(description = "Headline title of the webpage (e.g. 'SI Unit of Time')"),
                        "url" to Schema.string(description = "Full valid HTTPS URL to the website (e.g. https://byjus.com/physics/unit-of-time/)"),
                        "snippet" to Schema.string(description = "2-to-3 sentence informative snippet describing what the page covers"),
                        "category" to Schema.string(description = "One of: All, Videos, Forums, Images")
                    )
                ),
                description = "6 to 8 real, authoritative websites relevant to the user's search query."
            )
        )
    )

    private val config = generationConfig {
        responseMimeType = "application/json"
        responseSchema = searchSchema
    }

    // Lazy initialization per firebase-ai-logic-android requirements
    private val geminiModel by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash",
            generationConfig = config
        )
    }

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(6, TimeUnit.SECONDS)
        .readTimeout(6, TimeUnit.SECONDS)
        .build()

    /**
     * Streams the unified Search Results + Direct AI Overview on a single screen:
     * 1. Immediately fetches live organic web search results (DuckDuckGo HTML + Wikipedia + Instant Answer)
     *    so search result cards appear right away.
     * 2. Generates the Direct AI Overview + authoritative web results via Gemini and updates the same screen.
     */
    fun streamSearchWithDirectAiOverview(query: String): Flow<AiSearchUiState> = flow {
        val cleanQuery = query.trim()

        // Step 1: Fetch live organic web results in parallel (<400ms)
        val fastLiveSites = coroutineScope {
            val ddgHtmlDeferred = async(Dispatchers.IO) { fetchDuckDuckGoHtmlSearchResults(cleanQuery) }
            val wikiDeferred = async(Dispatchers.IO) { fetchWikipediaSearchResults(cleanQuery) }
            val ddgInstantDeferred = async(Dispatchers.IO) { fetchDuckDuckGoInstantSites(cleanQuery) }
            (ddgHtmlDeferred.await() + wikiDeferred.await() + ddgInstantDeferred.await())
                .distinctBy { BrowserSyncRepository.normalizeWebsiteUrl(it.url).lowercase() }
        }

        // Emit initial state on the unified Search Results screen
        emit(
            AiSearchUiState.Success(
                AiSearchOverviewData(
                    query = cleanQuery,
                    isAiOverviewGenerating = true,
                    availableSites = fastLiveSites
                )
            )
        )

        // Step 2: Generate the Direct AI Overview + Authoritative Web Results via Gemini
        try {
            val prompt = """
                You are the Google-style Direct AI Overview & Web Search engine inside Vista Browser.
                User search query: "$cleanQuery"
                
                1. Write a direct 1-sentence "leadSentence" that immediately answers "$cleanQuery" (for example: "The SI unit of time is the second (symbol: s).").
                2. Set "highlightedPhrase" to the exact key answer phrase inside "leadSentence" (for example: "the second") so the UI highlights it in blue.
                3. Provide a bold "sectionHeading" (for example: "Official Definition").
                4. Provide 2 to 3 detailed, authoritative bullet points in "keyPoints".
                5. Provide additional context in "summary".
                6. Provide 4 "peopleAlsoAsk" items with question and answer.
                7. Provide 6 to 8 real, well-known, authoritative websites ("sites") with publisher "siteName" (e.g. BYJU'S, Wikipedia, Britannica, NIST), page "title", valid https:// "url", 2-3 line "snippet", and "category" (All, Videos, Forums, or Images).
            """.trimIndent()

            val response = withTimeout(60_000L) {
                geminiModel.generateContent(prompt)
            }

            val jsonText = response.text
                ?: throw IllegalStateException("Gemini returned an empty response.")

            val json = JSONObject(jsonText)
            val leadSentence = json.optString("leadSentence", "")
            val highlightedPhrase = json.optString("highlightedPhrase", "")
            val sectionHeading = json.optString("sectionHeading", "Official Definition")
            val summary = json.optString("summary", "")

            val keyPointsArray = json.optJSONArray("keyPoints")
            val keyPoints = buildList {
                if (keyPointsArray != null) {
                    for (i in 0 until keyPointsArray.length()) {
                        val pt = keyPointsArray.optString(i)
                        if (pt.isNotBlank()) add(pt)
                    }
                }
            }

            val followUpArray = json.optJSONArray("followUpQueries")
            val followUps = buildList {
                if (followUpArray != null) {
                    for (i in 0 until followUpArray.length()) {
                        val fq = followUpArray.optString(i)
                        if (fq.isNotBlank()) add(fq)
                    }
                }
            }

            val paaArray = json.optJSONArray("peopleAlsoAsk")
            val peopleAlsoAsk = buildList {
                if (paaArray != null) {
                    for (i in 0 until paaArray.length()) {
                        val obj = paaArray.optJSONObject(i) ?: continue
                        val q = obj.optString("question", "")
                        val a = obj.optString("answer", "")
                        if (q.isNotBlank() && a.isNotBlank()) {
                            add(PeopleAlsoAskItem(question = q, answer = a))
                        }
                    }
                }
            }

            val aiSitesArray = json.optJSONArray("sites")
            val aiSites = buildList {
                if (aiSitesArray != null) {
                    for (i in 0 until aiSitesArray.length()) {
                        val obj = aiSitesArray.optJSONObject(i) ?: continue
                        val rawUrl = obj.optString("url", "")
                        val normalizedUrl = BrowserSyncRepository.normalizeWebsiteUrl(rawUrl)
                        val domain = BrowserSyncRepository.extractHost(normalizedUrl)
                        val siteName = obj.optString("siteName", "").ifBlank {
                            BrowserSyncRepository.extractDomainTitle(normalizedUrl)
                        }
                        val title = obj.optString("title", domain)
                        val snippet = obj.optString("snippet", "")
                        val category = obj.optString("category", "All")
                        if (domain.isNotBlank()) {
                            add(
                                WebSiteResult(
                                    siteName = siteName,
                                    title = title,
                                    url = normalizedUrl,
                                    domain = domain,
                                    snippet = snippet,
                                    faviconUrl = BrowserSyncRepository.buildFaviconUrl(normalizedUrl),
                                    category = category
                                )
                            )
                        }
                    }
                }
            }

            val mergedSites = (aiSites + fastLiveSites)
                .distinctBy { BrowserSyncRepository.normalizeWebsiteUrl(it.url).lowercase() }

            emit(
                AiSearchUiState.Success(
                    AiSearchOverviewData(
                        query = cleanQuery,
                        isAiOverviewGenerating = false,
                        leadSentence = leadSentence.ifBlank { summary.substringBefore(". ") + "." },
                        highlightedPhrase = highlightedPhrase,
                        sectionHeading = sectionHeading.ifBlank { "Official Definition" },
                        aiOverviewSummary = summary,
                        keyPoints = keyPoints,
                        followUpQueries = followUps,
                        peopleAlsoAsk = peopleAlsoAsk,
                        availableSites = mergedSites
                    )
                )
            )
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Gemini AI Overview timed out", e)
            emit(
                AiSearchUiState.Error(
                    query = cleanQuery,
                    message = "AI Overview timed out after 60 seconds. Please try again.",
                    fallbackSites = fastLiveSites
                )
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Gemini AI Overview failed", e)
            emit(
                AiSearchUiState.Error(
                    query = cleanQuery,
                    message = e.localizedMessage ?: "Failed to generate AI Overview.",
                    fallbackSites = fastLiveSites
                )
            )
        }
    }

    /**
     * Fetches real organic web search results from DuckDuckGo HTML endpoint so the user
     * immediately gets real search result cards alongside the Direct AI Overview.
     */
    private suspend fun fetchDuckDuckGoHtmlSearchResults(query: String): List<WebSiteResult> = withContext(Dispatchers.IO) {
        val results = mutableListOf<WebSiteResult>()
        try {
            val encoded = Uri.encode(query)
            val url = "https://html.duckduckgo.com/html/?q=$encoded"
            val request = Request.Builder()
                .url(url)
                .header(
                    "User-Agent",
                    "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36"
                )
                .build()

            httpClient.newCall(request).execute().use { response ->
                val html = response.body?.string() ?: return@use
                val blockRegex = Regex(
                    """<a[^>]*class="result__a"[^>]*href="([^"]+)"[^>]*>(.*?)</a>.*?<a[^>]*class="result__snippet"[^>]*>(.*?)</a>""",
                    setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)
                )
                for (match in blockRegex.findAll(html).take(6)) {
                    val rawHref = match.groupValues[1]
                    val resolvedUrl = if (rawHref.contains("uddg=")) {
                        val uddg = rawHref.substringAfter("uddg=").substringBefore("&")
                        URLDecoder.decode(uddg, "UTF-8")
                    } else {
                        rawHref
                    }
                    if (!resolvedUrl.startsWith("http")) continue
                    val cleanTitle = match.groupValues[2]
                        .replace(Regex("<[^>]*>"), "")
                        .replace("&amp;", "&")
                        .replace("&quot;", "\"")
                        .replace("&#x27;", "'")
                        .trim()
                    val cleanSnippet = match.groupValues[3]
                        .replace(Regex("<[^>]*>"), "")
                        .replace("&amp;", "&")
                        .replace("&quot;", "\"")
                        .replace("&#x27;", "'")
                        .trim()
                    val domain = BrowserSyncRepository.extractHost(resolvedUrl)
                    val siteName = BrowserSyncRepository.extractDomainTitle(resolvedUrl)
                    if (domain.isNotBlank() && cleanTitle.isNotBlank()) {
                        results.add(
                            WebSiteResult(
                                siteName = siteName,
                                title = cleanTitle,
                                url = resolvedUrl,
                                domain = domain,
                                snippet = cleanSnippet,
                                faviconUrl = BrowserSyncRepository.buildFaviconUrl(resolvedUrl)
                            )
                        )
                    }
                }
            }
        } catch (e: Exception) {
            // Ignore optional HTML scrape errors
        }
        results
    }

    private suspend fun fetchWikipediaSearchResults(query: String): List<WebSiteResult> = withContext(Dispatchers.IO) {
        val results = mutableListOf<WebSiteResult>()
        try {
            val encoded = Uri.encode(query)
            val url = "https://en.wikipedia.org/w/api.php?action=query&list=search&srsearch=$encoded&utf8=&format=json&srlimit=4"
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0 (Android; VistaBrowser/1.0)")
                .build()

            httpClient.newCall(request).execute().use { response ->
                val body = response.body?.string() ?: return@use
                val json = JSONObject(body)
                val searchArray: JSONArray? = json.optJSONObject("query")?.optJSONArray("search")
                if (searchArray != null) {
                    for (i in 0 until searchArray.length()) {
                        val item = searchArray.optJSONObject(i) ?: continue
                        val title = item.optString("title", "")
                        val rawSnippet = item.optString("snippet", "")
                            .replace(Regex("<[^>]*>"), "")
                            .replace("&quot;", "\"")
                            .replace("&#039;", "'")
                            .replace("&amp;", "&")
                        if (title.isNotBlank()) {
                            val pageUrl = "https://en.wikipedia.org/wiki/${Uri.encode(title.replace(" ", "_"))}"
                            results.add(
                                WebSiteResult(
                                    siteName = "Wikipedia",
                                    title = title,
                                    url = pageUrl,
                                    domain = "en.wikipedia.org",
                                    snippet = rawSnippet,
                                    faviconUrl = BrowserSyncRepository.buildFaviconUrl(pageUrl)
                                )
                            )
                        }
                    }
                }
            }
        } catch (e: Exception) {
            // Ignore optional network enrichment errors
        }
        results
    }

    private suspend fun fetchDuckDuckGoInstantSites(query: String): List<WebSiteResult> = withContext(Dispatchers.IO) {
        val results = mutableListOf<WebSiteResult>()
        try {
            val encoded = Uri.encode(query)
            val url = "https://api.duckduckgo.com/?q=$encoded&format=json&no_html=1&skip_disambig=1"
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0 (Android; VistaBrowser/1.0)")
                .build()

            httpClient.newCall(request).execute().use { response ->
                val body = response.body?.string() ?: return@use
                val json = JSONObject(body)

                val abstractUrl = json.optString("AbstractURL", "")
                val abstractText = json.optString("AbstractText", "")
                val heading = json.optString("Heading", query)
                val abstractSource = json.optString("AbstractSource", "Reference")
                if (abstractUrl.startsWith("http") && abstractText.isNotBlank()) {
                    results.add(
                        WebSiteResult(
                            siteName = abstractSource,
                            title = heading,
                            url = abstractUrl,
                            domain = BrowserSyncRepository.extractHost(abstractUrl),
                            snippet = abstractText,
                            faviconUrl = BrowserSyncRepository.buildFaviconUrl(abstractUrl)
                        )
                    )
                }

                val relatedTopics = json.optJSONArray("RelatedTopics")
                if (relatedTopics != null) {
                    for (i in 0 until relatedTopics.length().coerceAtMost(5)) {
                        val item = relatedTopics.optJSONObject(i) ?: continue
                        val firstUrl = item.optString("FirstURL", "")
                        val text = item.optString("Text", "")
                        if (firstUrl.startsWith("http") && text.isNotBlank()) {
                            val titlePart = text.substringBefore(" - ").take(85)
                            val domainTitle = BrowserSyncRepository.extractDomainTitle(firstUrl)
                            results.add(
                                WebSiteResult(
                                    siteName = domainTitle,
                                    title = titlePart.ifBlank { domainTitle },
                                    url = firstUrl,
                                    domain = BrowserSyncRepository.extractHost(firstUrl),
                                    snippet = text,
                                    faviconUrl = BrowserSyncRepository.buildFaviconUrl(firstUrl)
                                )
                            )
                        }
                    }
                }
            }
        } catch (e: Exception) {
            // Ignore network/parsing errors on optional live enrichment
        }
        results
    }

    private companion object {
        const val TAG = "VistaAiSearchEngine"
    }
}
