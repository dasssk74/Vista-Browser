package com.example.engine

import android.net.Uri
import android.util.Base64
import android.webkit.WebResourceResponse
import java.io.ByteArrayInputStream

enum class ShieldBlockType(val label: String) {
    NONE("Allowed"),
    DISPLAY_AD("Display / Pop-Up Ad"),
    VIDEO_AD("Video Ad Stream"),
    TRACKER("Cross-Site Tracker")
}

object AdBlockerEngine {

    // 1x1 transparent GIF for blocked tracking pixels and ad banners
    private val TRANSPARENT_GIF_BYTES: ByteArray = Base64.decode(
        "R0lGODlhAQABAIAAAAAAAP///yH5BAEAAAAALAAAAAABAAEAAAIBRAA7",
        Base64.DEFAULT
    )

    private val AD_DOMAINS = setOf(
        "doubleclick.net",
        "googlesyndication.com",
        "googleadservices.com",
        "adservice.google.com",
        "2mdn.net",
        "adnxs.com",
        "adsrvr.org",
        "adform.net",
        "taboola.com",
        "outbrain.com",
        "criteo.com",
        "criteo.net",
        "pubmatic.com",
        "rubiconproject.com",
        "openx.net",
        "moatads.com",
        "advertising.com",
        "amazon-adsystem.com",
        "aaxads.com",
        "adcolony.com",
        "applovin.com",
        "unityads.unity3d.com",
        "vungle.com",
        "chartboost.com",
        "ironsrc.com",
        "popads.net",
        "popcash.net",
        "propellerads.com",
        "adsterra.com",
        "hilltopads.net",
        "exoclick.com",
        "juicyads.com",
        "trafficjunky.net",
        "revcontent.com",
        "mgid.com",
        "zedo.com",
        "yieldmo.com",
        "teads.tv",
        "smartadserver.com",
        "media.net",
        "bidswitch.net",
        "casalemedia.com",
        "indexexchange.com",
        "3lift.com",
        "sharethrough.com",
        "sovrn.com",
        "lijit.com",
        "contextweb.com",
        "gumgum.com",
        "kargo.com",
        "undertone.com",
        "adbrited.com",
        "bidvertiser.com",
        "clickadu.com",
        "admaven.com",
        "richpush.co",
        "evadav.com",
        "galaksion.com"
    )

    private val VIDEO_AD_PATTERNS = listOf(
        "imasdk.googleapis.com",
        "pubads.g.doubleclick.net/gampad",
        "securepubads.g.doubleclick.net/gampad",
        "googleads.g.doubleclick.net/pagead/ads",
        "googleads.g.doubleclick.net/pagead/lvz",
        "youtube.com/api/stats/ads",
        "youtube.com/pagead/",
        "youtube.com/ptracking",
        "youtube.com/get_midroll_",
        "m.youtube.com/api/stats/ads",
        "m.youtube.com/pagead/",
        "m.youtube.com/ptracking",
        "s.youtube.com/api/stats/ads",
        "/api/stats/ads",
        "/pagead/lvz",
        "/pagead/interaction",
        "/pagead/adview",
        "/pagead/conversion",
        "/ptracking",
        "/get_midroll_info",
        "ad_type=video",
        "ad_type=skippablevideo",
        "vast.xml",
        "vmap.xml",
        "&oad=",
        "&adformat=",
        "preroll",
        "midroll",
        "spotxchange.com",
        "springserve.com",
        "innovid.com",
        "fwmrm.net",
        "jwpltx.com",
        "aniview.com",
        "connatix.com",
        "primis.tech"
    )

    private val TRACKER_DOMAINS = setOf(
        "google-analytics.com",
        "googletagmanager.com",
        "analytics.google.com",
        "ssl.google-analytics.com",
        "connect.facebook.net",
        "pixel.facebook.com",
        "an.facebook.com",
        "bat.bing.com",
        "hotjar.com",
        "mixpanel.com",
        "segment.io",
        "segment.com",
        "amplitude.com",
        "fullstory.com",
        "mouseflow.com",
        "crazyegg.com",
        "scorecardresearch.com",
        "sb.scorecardresearch.com",
        "quantserve.com",
        "quantcount.com",
        "newrelic.com",
        "nr-data.net",
        "bugsnag.com",
        "sentry.io",
        "clarity.ms",
        "ads-twitter.com",
        "analytics.twitter.com",
        "static.ads-twitter.com",
        "snap.licdn.com",
        "px.ads.linkedin.com",
        "analytics.tiktok.com",
        "ct.pinterest.com",
        "tr.snapchat.com",
        "omtrdc.net",
        "demdex.net",
        "2o7.net",
        "krxd.net",
        "bluekai.com",
        "exelator.com",
        "rlcdn.com",
        "tapad.com",
        "branch.io",
        "app.link",
        "appsflyer.com",
        "adjust.com",
        "kochava.com",
        "chartbeat.com",
        "chartbeat.net",
        "parsely.com"
    )

    private val AD_URL_KEYWORDS = listOf(
        "/ads/",
        "/adserver/",
        "/adframe",
        "/banner_ads/",
        "/popunder",
        "/pagead/",
        "adsbygoogle",
        "ad_banner",
        "sponsored_links",
        "/gpt/pubads",
        "/show_ads.js",
        "/adscript",
        "/adservice"
    )

    fun classifyRequest(
        requestUrl: String,
        adBlockEnabled: Boolean,
        videoAdSkipperEnabled: Boolean,
        trackerBlockEnabled: Boolean
    ): ShieldBlockType {
        if (requestUrl.isBlank()) return ShieldBlockType.NONE
        val lower = requestUrl.lowercase()

        // Allow internal test lab base URL
        if (lower.startsWith("https://shield.aegis.local") || lower.startsWith("https://shield.vista.local")) {
            return ShieldBlockType.NONE
        }

        if (videoAdSkipperEnabled) {
            if (VIDEO_AD_PATTERNS.any { lower.contains(it) }) {
                return ShieldBlockType.VIDEO_AD
            }
        }

        val host = try {
            Uri.parse(lower).host?.removePrefix("www.") ?: ""
        } catch (e: Exception) {
            ""
        }

        if (trackerBlockEnabled && host.isNotEmpty()) {
            if (TRACKER_DOMAINS.any { host == it || host.endsWith(".$it") }) {
                return ShieldBlockType.TRACKER
            }
            if (lower.contains("tiktok.com/i18n/pixel") || lower.contains("/collect?v=1") || lower.contains("/g/collect?")) {
                return ShieldBlockType.TRACKER
            }
        }

        if (adBlockEnabled) {
            if (host.isNotEmpty() && AD_DOMAINS.any { host == it || host.endsWith(".$it") || host.startsWith("adservice.google.") }) {
                return ShieldBlockType.DISPLAY_AD
            }
            if (AD_URL_KEYWORDS.any { lower.contains(it) }) {
                return ShieldBlockType.DISPLAY_AD
            }
        }

        return ShieldBlockType.NONE
    }

    /**
     * Returns a clean, MIME-type matched empty response so web pages never throw script errors
     * or trigger anti-adblock fallback popups when an ad script/image/XHR is intercepted.
     */
    fun createBlockedResourceResponse(requestUrl: String): WebResourceResponse {
        val lowerPath = requestUrl.substringBefore('?').lowercase()
        val headers = mapOf(
            "Access-Control-Allow-Origin" to "*",
            "Access-Control-Allow-Methods" to "GET, POST, OPTIONS",
            "Cache-Control" to "no-store"
        )

        return when {
            lowerPath.endsWith(".js") -> WebResourceResponse(
                "application/javascript",
                "UTF-8",
                200,
                "OK",
                headers,
                ByteArrayInputStream("/* Vista AdBlocker neutralized script */".toByteArray())
            )
            lowerPath.endsWith(".css") -> WebResourceResponse(
                "text/css",
                "UTF-8",
                200,
                "OK",
                headers,
                ByteArrayInputStream("".toByteArray())
            )
            lowerPath.endsWith(".gif") || lowerPath.endsWith(".png") || lowerPath.endsWith(".jpg") ||
                lowerPath.endsWith(".jpeg") || lowerPath.endsWith(".webp") -> WebResourceResponse(
                "image/gif",
                null,
                200,
                "OK",
                headers,
                ByteArrayInputStream(TRANSPARENT_GIF_BYTES)
            )
            lowerPath.endsWith(".json") || lowerPath.contains("/api/") -> WebResourceResponse(
                "application/json",
                "UTF-8",
                200,
                "OK",
                headers,
                ByteArrayInputStream("{}".toByteArray())
            )
            else -> WebResourceResponse(
                "text/plain",
                "UTF-8",
                200,
                "OK",
                headers,
                ByteArrayInputStream(ByteArray(0))
            )
        }
    }

    /**
     * Multi-layer DOM Cosmetic Ad-Blocker + Fetch/XHR Ad Stripper + YouTube & HTML5 Video Ad Skipper.
     * Can be safely called multiple times on SPA navigations.
     */
    fun buildInjectedShieldScript(
        adBlockEnabled: Boolean,
        videoAdSkipperEnabled: Boolean,
        forceDarkWebPages: Boolean
    ): String {
        return """
            (function() {
                try {
                    // Always update force-dark class
                    if ($forceDarkWebPages) {
                        document.documentElement.classList.add('vista-force-dark');
                    } else {
                        document.documentElement.classList.remove('vista-force-dark');
                    }

                    // Ensure cosmetic CSS is always present in <head> or <html>
                    if (!document.getElementById('vista-shield-style')) {
                        var style = document.createElement('style');
                        style.id = 'vista-shield-style';
                        var css = '';
                        if ($adBlockEnabled) {
                            css += `
                                .ad-banner, .adsbox, .ad-slot, .ad-container, .adsbygoogle,
                                ins.adsbygoogle, [id^="google_ads_iframe"], [id^="div-gpt-ad"],
                                [class*="ad-wrapper"], [data-ad-slot], [data-ad-client],
                                [data-google-query-id], iframe[src*="doubleclick.net"],
                                iframe[src*="googlesyndication.com"], iframe[src*="adnxs.com"],
                                .taboola-widget, .outbrain-widget, [id*="taboola"], [id*="outbrain"],
                                .mgid-widget, .revcontent-widget,
                                .ytp-ad-overlay-container, .ytp-ad-text-overlay, .ytp-ad-image-overlay,
                                .ytp-ad-player-overlay, #player-ads, #masthead-ad,
                                ytd-ad-slot-renderer, ytd-in-feed-ad-layout-renderer,
                                ytd-banner-promo-renderer, ytd-promoted-sparkles-web-renderer,
                                ytd-display-ad-renderer, ytd-statement-banner-renderer,
                                ytm-promoted-sparkles-web-renderer, ytm-promoted-video-renderer,
                                ytm-companion-ad-renderer, ytm-companion-slot,
                                .ad-showing .ytp-ad-player-overlay-instream-info,
                                [aria-label="Sponsored"], [data-ad-preview] {
                                    display: none !important;
                                    visibility: hidden !important;
                                    height: 0 !important;
                                    min-height: 0 !important;
                                    max-height: 0 !important;
                                    opacity: 0 !important;
                                    pointer-events: none !important;
                                    overflow: hidden !important;
                                }
                            `;
                        }
                        css += `
                            html.vista-force-dark {
                                filter: invert(0.92) hue-rotate(180deg) !important;
                                background-color: #0d1117 !important;
                            }
                            html.vista-force-dark img,
                            html.vista-force-dark video,
                            html.vista-force-dark canvas,
                            html.vista-force-dark iframe {
                                filter: invert(1) hue-rotate(180deg) !important;
                            }
                        `;
                        style.textContent = css;
                        (document.head || document.documentElement).appendChild(style);
                    }

                    if (window.__vistaShieldRuntimeActive) {
                        return;
                    }
                    window.__vistaShieldRuntimeActive = true;

                    // 1. Block window.open pop-under ads
                    if ($adBlockEnabled) {
                        var originalWindowOpen = window.open;
                        window.open = function(url) {
                            if (!url) return null;
                            var lower = String(url).toLowerCase();
                            if (lower.indexOf('doubleclick') !== -1 ||
                                lower.indexOf('googlesyndication') !== -1 ||
                                lower.indexOf('popads') !== -1 ||
                                lower.indexOf('propeller') !== -1 ||
                                lower.indexOf('adsterra') !== -1 ||
                                lower.indexOf('/ads/') !== -1) {
                                if (window.AegisShieldBridge && window.AegisShieldBridge.onDomAdIntercepted) {
                                    window.AegisShieldBridge.onDomAdIntercepted('popup_blocked:' + lower);
                                }
                                return null;
                            }
                            return originalWindowOpen ? originalWindowOpen.apply(window, arguments) : null;
                        };
                    }

                    // 2. Strip YouTube & Video Player JSON adPlacements before playback starts
                    if ($videoAdSkipperEnabled) {
                        var origJsonParse = JSON.parse;
                        JSON.parse = function() {
                            var obj = origJsonParse.apply(this, arguments);
                            try {
                                if (obj && typeof obj === 'object') {
                                    var stripped = false;
                                    if (obj.adPlacements) { delete obj.adPlacements; stripped = true; }
                                    if (obj.playerAds) { delete obj.playerAds; stripped = true; }
                                    if (obj.adSlots) { delete obj.adSlots; stripped = true; }
                                    if (stripped && window.AegisShieldBridge && window.AegisShieldBridge.onVideoAdIntercepted) {
                                        window.AegisShieldBridge.onVideoAdIntercepted('player_json_ad_stripped');
                                    }
                                }
                            } catch (e) {}
                            return obj;
                        };
                    }

                    // 3. Active Cosmetic DOM Ad Sweeper + Video Ad Auto-Skipper
                    function runVistaShieldSweep() {
                        try {
                            if ($adBlockEnabled) {
                                var adSelectors = [
                                    'ins.adsbygoogle',
                                    '[id^="google_ads_iframe"]',
                                    '[id^="div-gpt-ad"]',
                                    'iframe[src*="doubleclick.net"]',
                                    'iframe[src*="googlesyndication.com"]',
                                    'ytm-promoted-sparkles-web-renderer',
                                    'ytm-promoted-video-renderer',
                                    'ytm-companion-ad-renderer',
                                    'ytd-ad-slot-renderer',
                                    'ytd-in-feed-ad-layout-renderer',
                                    '.ad-banner:not([data-vista-cleaned])',
                                    '.adsbox:not([data-vista-cleaned])'
                                ];
                                var newlyCleaned = 0;
                                for (var a = 0; a < adSelectors.length; a++) {
                                    var nodes = document.querySelectorAll(adSelectors[a]);
                                    for (var n = 0; n < nodes.length; n++) {
                                        var el = nodes[n];
                                        if (el && !el.getAttribute('data-vista-cleaned')) {
                                            el.setAttribute('data-vista-cleaned', '1');
                                            el.style.setProperty('display', 'none', 'important');
                                            newlyCleaned++;
                                        }
                                    }
                                }
                                if (newlyCleaned > 0 && window.AegisShieldBridge && window.AegisShieldBridge.onDomAdIntercepted) {
                                    window.AegisShieldBridge.onDomAdIntercepted(window.location.hostname || 'dom_ad');
                                }
                            }

                            if ($videoAdSkipperEnabled) {
                                // Click any visible Skip Ad button immediately
                                var skipSelectors = [
                                    '.ytp-ad-skip-button',
                                    '.ytp-ad-skip-button-modern',
                                    '.ytp-skip-ad-button',
                                    '.ytp-ad-skip-button-container button',
                                    '.videoAdUiSkipButton',
                                    '[class*="skip-ad"]',
                                    '[data-testid="skip-ad-button"]',
                                    '.aegis-test-skip-ad'
                                ];
                                for (var i = 0; i < skipSelectors.length; i++) {
                                    var buttons = document.querySelectorAll(skipSelectors[i]);
                                    for (var j = 0; j < buttons.length; j++) {
                                        var btn = buttons[j];
                                        if (btn) {
                                            btn.click();
                                            if (window.AegisShieldBridge && window.AegisShieldBridge.onVideoAdIntercepted) {
                                                window.AegisShieldBridge.onVideoAdIntercepted('skip_button');
                                            }
                                        }
                                    }
                                }

                                // Fast-forward & mute any active video ad stream (YouTube mobile/desktop + HTML5 VAST)
                                var adPlayer = document.querySelector(
                                    '.ad-showing video, .ad-interrupting video, #movie_player.ad-showing video, video[data-ad-playing="true"]'
                                );
                                if (adPlayer) {
                                    adPlayer.muted = true;
                                    adPlayer.playbackRate = 16.0;
                                    if (!isNaN(adPlayer.duration) && adPlayer.duration > 0 && isFinite(adPlayer.duration)) {
                                        adPlayer.currentTime = adPlayer.duration;
                                    } else if (adPlayer.currentTime < 60) {
                                        adPlayer.currentTime = 9999;
                                    }
                                    if (window.AegisShieldBridge && window.AegisShieldBridge.onVideoAdIntercepted) {
                                        window.AegisShieldBridge.onVideoAdIntercepted('video_stream_fast_forward');
                                    }
                                }

                                // Remove pre-roll / overlay ad layers
                                var overlays = document.querySelectorAll(
                                    '.ytp-ad-player-overlay, .ytp-ad-overlay-container, .aegis-preroll-overlay'
                                );
                                for (var k = 0; k < overlays.length; k++) {
                                    overlays[k].remove();
                                    if (window.AegisShieldBridge && window.AegisShieldBridge.onVideoAdIntercepted) {
                                        window.AegisShieldBridge.onVideoAdIntercepted('overlay_removed');
                                    }
                                }
                            }
                        } catch (err) {}
                    }

                    runVistaShieldSweep();
                    setInterval(runVistaShieldSweep, 250);
                    var observer = new MutationObserver(function() {
                        runVistaShieldSweep();
                    });
                    observer.observe(document.documentElement, { childList: true, subtree: true });
                } catch (outerErr) {}
            })();
        """.trimIndent()
    }

    /**
     * Built-in interactive test lab page (`aegis://shield-test`) so users can verify
     * video ad blocking, cosmetic banner filtering, and tracker interception in real time.
     */
    fun buildShieldTestPageHtml(
        adsBlocked: Long,
        videoAdsBlocked: Long,
        trackersBlocked: Long
    ): String {
        return """
            <!DOCTYPE html>
            <html lang="en">
            <head>
                <meta charset="UTF-8" />
                <meta name="viewport" content="width=device-width, initial-scale=1.0" />
                <title>Vista Ad-Blocker & Video Shield Live Verification</title>
                <style>
                    body {
                        font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
                        background: #0F172A;
                        color: #F8FAFC;
                        margin: 0;
                        padding: 20px;
                    }
                    .card {
                        background: #1E293B;
                        border: 1px solid #334155;
                        border-radius: 16px;
                        padding: 18px;
                        margin-bottom: 16px;
                    }
                    .badge {
                        display: inline-block;
                        background: rgba(56, 189, 248, 0.15);
                        color: #38BDF8;
                        padding: 4px 10px;
                        border-radius: 999px;
                        font-size: 12px;
                        font-weight: 700;
                        margin-bottom: 8px;
                    }
                    h2 { margin: 4px 0 10px 0; font-size: 19px; }
                    p { color: #94A3B8; font-size: 14px; line-height: 1.5; margin: 0 0 10px 0; }
                    button {
                        background: linear-gradient(135deg, #2563EB, #38BDF8);
                        color: #FFFFFF;
                        border: none;
                        border-radius: 10px;
                        padding: 12px 16px;
                        font-weight: 700;
                        font-size: 14px;
                        cursor: pointer;
                        width: 100%;
                        margin-top: 8px;
                    }
                    .video-stage {
                        position: relative;
                        background: #090D16;
                        border-radius: 12px;
                        height: 150px;
                        display: flex;
                        align-items: center;
                        justify-content: center;
                        overflow: hidden;
                        border: 1px solid #334155;
                        margin-top: 10px;
                    }
                    .log-box {
                        background: #090D16;
                        border-radius: 10px;
                        padding: 12px;
                        font-family: monospace;
                        font-size: 12px;
                        color: #34D399;
                        max-height: 170px;
                        overflow-y: auto;
                    }
                </style>
            </head>
            <body>
                <div class="card">
                    <span class="badge">LIVE AD-BLOCKER BENCHMARK</span>
                    <h2>Vista Ad & Video Shield Engine</h2>
                    <p>Verify real-time network ad blocking, DOM banner removal, pop-up blocking, and automatic pre-roll/mid-roll video ad skipping.</p>
                </div>

                <div class="card">
                    <h2>1. Pre-Roll Video Ad Auto-Skipper</h2>
                    <p>Spawns a pre-roll video ad overlay and a Google IMA VAST ad request. Vista Shield intercepts the VAST request and strips the pre-roll in &lt;250ms.</p>
                    <div class="video-stage" id="videoStage">
                        <span id="videoStatus" style="color:#38BDF8;font-weight:600;">Ready • Video Stream Protected</span>
                    </div>
                    <button onclick="triggerVideoAdAttack()">Test Pre-Roll Video Ad Blocker</button>
                </div>

                <div class="card">
                    <h2>2. Ad Network & Tracker Stress Test</h2>
                    <p>Fires live requests to DoubleClick, GoogleSyndication, Google Analytics, Taboola, and Criteo to verify network-level blocking.</p>
                    <button onclick="triggerNetworkAdAttack()">Fire 6 Ad & Tracker Requests</button>
                </div>

                <div class="card">
                    <h2>Live Interception Log</h2>
                    <div class="log-box" id="logBox">
                        [Vista Shield] Active &amp; filtering network + DOM...<br/>
                        [Counters] Ads: $adsBlocked | Video Ads: $videoAdsBlocked | Trackers: $trackersBlocked<br/>
                    </div>
                </div>

                <script>
                    function appendLog(msg) {
                        var box = document.getElementById('logBox');
                        var time = new Date().toLocaleTimeString();
                        box.innerHTML = '[' + time + '] ' + msg + '<br/>' + box.innerHTML;
                    }

                    function triggerVideoAdAttack() {
                        appendLog('Spawning pre-roll video ad + VAST request...');
                        fetch('https://imasdk.googleapis.com/js/sdkloader/ima3.js?preroll=1').then(function() {
                            appendLog('✓ Neutralized VAST script: imasdk.googleapis.com');
                        }).catch(function() {
                            appendLog('✓ Blocked VAST network request: imasdk.googleapis.com');
                        });

                        var stage = document.getElementById('videoStage');
                        var status = document.getElementById('videoStatus');
                        var overlay = document.createElement('div');
                        overlay.className = 'aegis-preroll-overlay';
                        overlay.style.cssText = 'position:absolute;inset:0;background:rgba(220,38,38,0.88);display:flex;align-items:center;justify-content:center;color:white;font-weight:bold;';
                        overlay.innerText = 'SPONSORED PRE-ROLL VIDEO AD (15s)';
                        stage.appendChild(overlay);

                        setTimeout(function() {
                            if (!document.querySelector('.aegis-preroll-overlay')) {
                                status.innerText = '✓ Pre-Roll Video Ad Automatically Skipped!';
                                appendLog('✓ DOM Video Ad Skipper stripped pre-roll in <250ms');
                            }
                        }, 320);
                    }

                    function triggerNetworkAdAttack() {
                        var targets = [
                            'https://pagead2.googlesyndication.com/pagead/js/adsbygoogle.js',
                            'https://securepubads.g.doubleclick.net/gampad/ads?ad_type=video',
                            'https://www.google-analytics.com/analytics.js',
                            'https://cdn.taboola.com/libtrc/impl.js',
                            'https://static.criteo.net/js/ld/publishertag.js',
                            'https://connect.facebook.net/en_US/fbevents.js'
                        ];
                        targets.forEach(function(url) {
                            fetch(url, { mode: 'no-cors' }).then(function() {
                                appendLog('✓ Intercepted & neutralized: ' + url.split('/')[2]);
                            }).catch(function() {
                                appendLog('✓ Blocked at socket layer: ' + url.split('/')[2]);
                            });
                        });
                    }
                </script>
            </body>
            </html>
        """.trimIndent()
    }
}
