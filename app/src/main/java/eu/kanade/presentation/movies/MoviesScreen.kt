package eu.kanade.presentation.movies

import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.net.Uri
import android.os.Message
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import java.io.ByteArrayInputStream
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.CollectionsBookmark
import androidx.compose.material.icons.outlined.Clear
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.TextButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import com.kevinnzou.web.AccompanistWebChromeClient
import com.kevinnzou.web.AccompanistWebViewClient
import com.kevinnzou.web.LoadingState
import com.kevinnzou.web.WebView
import com.kevinnzou.web.rememberWebViewNavigator
import com.kevinnzou.web.rememberWebViewState
import eu.kanade.presentation.components.AppBar
import eu.kanade.tachiyomi.BuildConfig
import eu.kanade.tachiyomi.ui.movies.MoviesScreenModel
import eu.kanade.tachiyomi.util.system.setDefaultSettings
import eu.kanade.tachiyomi.util.system.toShareIntent
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat
import tachiyomi.i18n.MR
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.i18n.stringResource

/** Stream URLs caught by the tab before playback takes over. */
private class MovieStreams {
    private val _streams = mutableStateOf<List<String>>(emptyList())
    val streams: List<String> get() = _streams.value

    fun add(url: String) {
        if (url.isNotBlank() && url !in _streams.value) {
            _streams.value = _streams.value + url
        }
    }

    fun clear() {
        _streams.value = emptyList()
    }
}

/**
 * Popups are blocked outright; they are how pirate sites spam ads.
 * HTML5 video fullscreen (YouTube and every other site) is supported like a real browser:
 * the page hands us a fullscreen custom view and we overlay it on the whole screen.
 */
private class MoviesWebChromeClient(
    private val activity: android.app.Activity?,
) : AccompanistWebChromeClient() {

    private var fullscreenView: android.view.View? = null
    private var fullscreenCallback: android.webkit.WebChromeClient.CustomViewCallback? = null
    private var fullscreenParent: android.view.ViewGroup? = null
    private var fullscreenPreviousOrientation: Int? = null
    private var fullscreenPreviousBackground: android.graphics.drawable.Drawable? = null

    override fun onCreateWindow(
        view: android.webkit.WebView?,
        isDialog: Boolean,
        isUserGesture: Boolean,
        resultMsg: Message?,
    ): Boolean = false

    override fun onShowCustomView(
        view: android.view.View,
        callback: android.webkit.WebChromeClient.CustomViewCallback,
    ) {
        val content = activity?.findViewById<android.widget.FrameLayout>(android.R.id.content)
        if (view == null || content == null) {
            super.onShowCustomView(view, callback)
            return
        }
        if (fullscreenView != null) onHideCustomView()

        (view.parent as? android.view.ViewGroup)?.removeView(view)
        for (i in 0 until content.childCount) {
            content.getChildAt(i).visibility = android.view.View.GONE
        }
        fullscreenPreviousBackground = content.background
        content.setBackgroundColor(android.graphics.Color.BLACK)

        // Back press exits fullscreen, like every fullscreen browser.
        // The wrapper pads itself by the system bars + display cutout, so fullscreen
        // never draws over the navigation buttons or the notch, in any orientation.
        val wrapper = android.widget.FrameLayout(content.context).apply {
            setBackgroundColor(android.graphics.Color.BLACK)
            isFocusable = true
            isFocusableInTouchMode = true
            setOnKeyListener { _, keyCode, _ ->
                if (keyCode == android.view.KeyEvent.KEYCODE_BACK) {
                    onHideCustomView()
                    true
                } else {
                    false
                }
            }
            ViewCompat.setOnApplyWindowInsetsListener(this) { v, insets ->
                val bars = insets.getInsets(
                    WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout(),
                )
                v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
                insets
            }
            addView(
                view,
                android.widget.FrameLayout.LayoutParams(
                    android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                    android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                ),
            )
        }
        content.addView(
            wrapper,
            android.widget.FrameLayout.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
            ),
        )
        wrapper.requestApplyInsets()
        wrapper.requestFocus()
        fullscreenParent = content
        fullscreenView = wrapper
        fullscreenCallback = callback
        // Default to landscape while fullscreen; restore whatever the app had before.
        fullscreenPreviousOrientation = activity?.requestedOrientation
        activity?.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
    }

    override fun onHideCustomView() {
        val content = fullscreenParent
        val view = fullscreenView
        if (content != null && view != null) {
            content.removeView(view)
            for (i in 0 until content.childCount) {
                content.getChildAt(i).visibility = android.view.View.VISIBLE
            }
            content.background = fullscreenPreviousBackground
        }
        try {
            fullscreenCallback?.onCustomViewHidden()
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e) { "onCustomViewHidden failed" }
        }
        fullscreenView = null
        fullscreenCallback = null
        fullscreenParent = null
        fullscreenPreviousOrientation?.let { prev ->
            activity?.requestedOrientation = prev
        }
        fullscreenPreviousOrientation = null
        fullscreenPreviousBackground = null
    }
}

@Composable
fun MoviesTabScreen() {
    val context = LocalContext.current
    remember { MovieAdBlockList.load(context.applicationContext) }
    val scope = rememberCoroutineScope()
    val screenModel = remember { MoviesScreenModel() }

    var query by remember { mutableStateOf("") }
    var url by remember { mutableStateOf<String?>(null) }
    var pageTitle by remember { mutableStateOf("") }
    val adBlockState = remember { mutableStateOf(true) }
    val streams = remember { MovieStreams() }
    val detected = streams.streams

    var savedLinks by remember { mutableStateOf(SavedLinksStore.load(context)) }
    var showSavedLinks by remember { mutableStateOf(false) }

    fun goTo(target: String) {
        streams.clear()
        pageTitle = ""
        url = target
    }

    fun goToResolved(target: String) = goTo(resolveUrl(target))

    Scaffold(
        topBar = {
            AppBar(
                title = stringResource(AYMR.strings.label_movies),
                actions = {
                    val currentLink = url
                    if (currentLink != null) {
                        val isSaved = savedLinks.any { it.url == currentLink }
                        IconButton(onClick = {
                            val pageUrl = currentLink
                            val name = pageTitle.ifBlank {
                                runCatching { Uri.parse(pageUrl).host?.lowercase() }.getOrNull() ?: pageUrl
                            }
                            val updated = if (isSaved) {
                                savedLinks.filterNot { it.url == pageUrl }
                            } else {
                                savedLinks + SavedLink(name, pageUrl)
                            }
                            SavedLinksStore.save(context, updated)
                            savedLinks = updated
                        }) {
                            Icon(
                                imageVector = if (isSaved) Icons.Filled.Bookmark else Icons.Outlined.BookmarkBorder,
                                contentDescription = stringResource(AYMR.strings.movies_save_link),
                                tint = if (isSaved) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                            )
                        }
                    }
                    IconButton(onClick = { showSavedLinks = true }) {
                        Icon(
                            imageVector = Icons.Outlined.CollectionsBookmark,
                            contentDescription = stringResource(AYMR.strings.movies_saved_links),
                        )
                    }
                    IconButton(onClick = { adBlockState.value = !adBlockState.value }) {
                        Icon(
                            imageVector = Icons.Outlined.Security,
                            contentDescription = stringResource(
                                if (adBlockState.value) {
                                    AYMR.strings.movies_adblock_on
                                } else {
                                    AYMR.strings.movies_adblock_off
                                },
                            ),
                            tint = if (adBlockState.value) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        )
                    }
                },
            )
        },
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
        ) {
            // Search bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { text -> query = text },
                    singleLine = true,
                    modifier = Modifier
                        .weight(1f)
                        .height(52.dp),
                    placeholder = { Text(text = stringResource(AYMR.strings.movies_search_hint)) },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Outlined.Search,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    },
                    trailingIcon = {
                        if (query.isNotEmpty()) {
                            IconButton(onClick = { query = "" }) {
                                Icon(
                                    imageVector = Icons.Outlined.Clear,
                                    contentDescription = stringResource(AYMR.strings.action_clear),
                                )
                            }
                        }
                    },
                    colors = TextFieldDefaults.colors(
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        unfocusedIndicatorColor = MaterialTheme.colorScheme.outlineVariant,
                        focusedIndicatorColor = MaterialTheme.colorScheme.primary,
                    ),
                    shape = MaterialTheme.shapes.extraLarge,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                    keyboardActions = KeyboardActions(
                        onGo = {
                            val t = query.trim()
                            if (t.isNotBlank()) goToResolved(t)
                        },
                    ),
                )
                Spacer(Modifier.width(8.dp))
                OutlinedButton(
                    onClick = {
                        val t = query.trim()
                        if (t.isNotBlank()) goToResolved(t)
                    },
                    modifier = Modifier.height(52.dp),
                ) {
                    Text(stringResource(MR.strings.action_search))
                }
                Spacer(Modifier.width(4.dp))
                val currentUrl = url
                if (currentUrl != null) {
                    IconButton(
                        onClick = {
                            try {
                                context.startActivity(
                                    currentUrl.toUri().toShareIntent(context, type = "text/plain"),
                                )
                            } catch (e: Exception) {
                                logcat(LogPriority.ERROR, e) { "Could not share $currentUrl" }
                            }
                        },
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Share,
                            contentDescription = stringResource(MR.strings.action_share),
                        )
                    }
                }
            }

            if (url == null) {
                Column(
                    modifier = Modifier.fillMaxSize().padding(32.dp),
                    horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
                    verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
                ) {
                    Text(
                        text = stringResource(AYMR.strings.movies_empty_title),
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = stringResource(AYMR.strings.movies_empty_summary),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                return@Column
            }

            val currentUrl = url!!
            val webState = rememberWebViewState(url = currentUrl)
            val navigator = rememberWebViewNavigator()
            val chromeClient = remember { MoviesWebChromeClient(activity = context as? android.app.Activity) }
            val client = remember(currentUrl, streams) {
                val topHost = Uri.parse(currentUrl).host
                val extractYoutubeStreams: (android.webkit.WebView) -> Unit = { webView ->
                    webView.evaluateJavascript(YOUTUBE_STREAMS_JS) { raw ->
                        val json = raw?.trim()
                        if (json.isNullOrEmpty() || json == "null") return@evaluateJavascript
                        val inner = if (json.startsWith("\"")) {
                            runCatching { Json.decodeFromString<String>(json) }.getOrNull()
                                ?: return@evaluateJavascript
                        } else {
                            json
                        }
                        val root = runCatching { Json.parseToJsonElement(inner).jsonObject }.getOrNull()
                            ?: return@evaluateJavascript
                        val title = root["title"]?.jsonPrimitive?.contentOrNull.orEmpty()
                        if (title.isNotBlank()) pageTitle = title
                        val arr = runCatching { root["streams"]?.jsonArray }.getOrNull()
                            ?: return@evaluateJavascript
                        var bestUrl: String? = null
                        var bestScore = -1
                        for (el in arr) {
                            val obj = runCatching { el.jsonObject }.getOrNull() ?: continue
                            val streamUrl = obj["url"]?.jsonPrimitive?.contentOrNull ?: continue
                            val quality = obj["quality"]?.jsonPrimitive?.contentOrNull?.lowercase().orEmpty()
                            val score = run {
                                val digits = quality.filter { it.isDigit() }.toIntOrNull()
                                when {
                                    digits != null && digits >= 144 -> digits
                                    quality == "medium" -> 360
                                    quality == "high" -> 480
                                    quality == "small" || quality == "tiny" -> 240
                                    else -> 100
                                }
                            }
                            if (score > bestScore) {
                                bestScore = score
                                bestUrl = streamUrl
                            }
                        }
                        bestUrl?.let { if (it !in streams.streams) streams.add(it) }
                    }
                }
                object : AccompanistWebViewClient() {
                    override fun shouldInterceptRequest(
                        view: android.webkit.WebView,
                        request: WebResourceRequest?,
                    ): WebResourceResponse? {
                        if (request?.isForMainFrame == true) {
                            // Never blank the page the user is actually on.
                            return super.shouldInterceptRequest(view, request)
                        }
                        val host = request?.url?.host
                        val initiator = refererHost(request) ?: topHost
                        if (adBlockState.value && host != null &&
                            (isBlockedHost(host) || MovieAdBlockList.isBlocked(host, initiator))
                        ) {
                            // Cut the ad off before it fires.
                            return emptyResponse()
                        }
                        val requestUrl = request?.url?.toString().orEmpty()
                        if (isStreamUrl(requestUrl) && requestUrl !in streams.streams) {
                            streams.add(requestUrl)
                        }
                        return super.shouldInterceptRequest(view, request)
                    }

                    override fun shouldOverrideUrlLoading(
                        view: android.webkit.WebView?,
                        request: WebResourceRequest?,
                    ): Boolean {
                        val host = request?.url?.host
                        val initiator = refererHost(request) ?: topHost
                        if (adBlockState.value && host != null &&
                            (isBlockedHost(host) || MovieAdBlockList.isBlocked(host, initiator))
                        ) {
                            // Redirect/jump to an ad; kill it and stay on the movie page.
                            return true
                        }
                        val requestUrl = request?.url?.toString().orEmpty()
                        if (isStreamUrl(requestUrl)) {
                            scope.launch { screenModel.playMovie(context, requestUrl, requestUrl) }
                            return true
                        }
                        return super.shouldOverrideUrlLoading(view, request)
                    }

                    override fun onPageStarted(
                        view: android.webkit.WebView,
                        url: String?,
                        favicon: android.graphics.Bitmap?,
                    ) {
                        super.onPageStarted(view, url, favicon)
                        view.evaluateJavascript(AD_SHIM_JS, null)
                    }

                    override fun onPageFinished(
                        view: android.webkit.WebView,
                        url: String?,
                    ) {
                        super.onPageFinished(view, url)
                        view.evaluateJavascript(AD_SHIM_JS, null)
                        view.evaluateJavascript("(function(){return document.title||''})()") { raw ->
                            val t = raw?.trim().orEmpty()
                            if (t.length > 1 && t.startsWith("\"")) {
                                runCatching { Json.decodeFromString<String>(t) }.getOrNull()?.let {
                                    if (it.isNotBlank()) pageTitle = it
                                }
                            }
                        }
                        if (url?.lowercase()?.contains("youtube.com") == true) {
                            extractYoutubeStreams(view)
                        }
                    }

                    override fun doUpdateVisitedHistory(
                        view: android.webkit.WebView,
                        url: String?,
                        isReload: Boolean,
                    ) {
                        super.doUpdateVisitedHistory(view, url, isReload)
                        if (url?.lowercase()?.contains("youtube.com") == true) {
                            extractYoutubeStreams(view)
                        }
                    }
                }
            }
            val isLoading = webState.loadingState is LoadingState.Initializing ||
                webState.loadingState is LoadingState.Loading
            if (isLoading) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }

            WebView(
                state = webState,
                modifier = Modifier.fillMaxSize(),
                navigator = navigator,
                client = client,
                chromeClient = chromeClient,
                onCreated = { webView ->
                    webView.setDefaultSettings()
                    if (BuildConfig.DEBUG) {
                        android.webkit.WebView.setWebContentsDebuggingEnabled(true)
                    }
                },
            )

            if (detected.isNotEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    contentAlignment = androidx.compose.ui.Alignment.BottomEnd,
                ) {
                    FilledTonalButton(
                        onClick = {
                            val first = detected.first()
                            scope.launch { screenModel.playMovie(context, first, pageTitle.ifBlank { first }) }
                        },
                    ) {
                        Icon(
                            imageVector = Icons.Filled.PlayArrow,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(AYMR.strings.movies_play_in_alexyomi))
                    }
                }
            }

            if (showSavedLinks) {
                AlertDialog(
                    onDismissRequest = { showSavedLinks = false },
                    title = { Text(stringResource(AYMR.strings.movies_saved_links)) },
                    text = {
                        if (savedLinks.isEmpty()) {
                            Text(
                                text = stringResource(AYMR.strings.movies_saved_empty),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        } else {
                            LazyColumn(modifier = Modifier.heightIn(max = 420.dp)) {
                                items(savedLinks, key = { it.url }) { link ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                                    ) {
                                        Column(
                                            modifier = Modifier
                                                .weight(1f)
                                                .clickable {
                                                    showSavedLinks = false
                                                    goToResolved(link.url)
                                                }
                                                .padding(vertical = 10.dp, horizontal = 4.dp),
                                        ) {
                                            Text(
                                                text = link.name,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                style = MaterialTheme.typography.bodyLarge,
                                            )
                                            Text(
                                                text = link.url,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        }
                                        IconButton(
                                            onClick = {
                                                val updated = savedLinks.filterNot { it.url == link.url }
                                                SavedLinksStore.save(context, updated)
                                                savedLinks = updated
                                            },
                                        ) {
                                            Icon(
                                                imageVector = Icons.Outlined.Clear,
                                                contentDescription = stringResource(AYMR.strings.movies_remove_link),
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = { showSavedLinks = false }) {
                            Text(stringResource(MR.strings.action_close))
                        }
                    },
                )
            }
        }
    }
}

// Extracts the direct (muxed, audio+video) progressive stream URLs YouTube embeds in the
// watch page (ytInitialPlayerResponse.streamingData.progressiveFormats) plus the page title.
private const val YOUTUBE_STREAMS_JS = """
(function () {
  try {
    var p = window.ytInitialPlayerResponse;
    if (!p || !p.streamingData) {
      return JSON.stringify({ streams: [], title: document.title || '' });
    }
    var sd = p.streamingData || {};
    var acc = [];
    var arr = sd.progressiveFormats || [];
    for (var i = 0; i < arr.length; i++) {
      var f = arr[i];
      if (f && f.url) {
        acc.push({
          url: f.url,
          quality: f.qualityLabel || f.quality || '',
          itag: f.itag || 0
        });
      }
    }
    return JSON.stringify({ streams: acc, title: document.title || '' });
  } catch (e) {
    return JSON.stringify({ streams: [], title: '' });
  }
})();
"""

// Injected into every page: neuter window.open (how pirate sites pop ads) and hide
// the ad elements they inject into their own pages. Timed re-scans instead of a
// MutationObserver — a persistent observer starved the page's JS thread and froze it.
// YouTube is handled separately (see YOUTUBE_STREAMS_JS below).
private const val AD_SHIM_JS = """
(function () {
  if (window.__alexyomiBlock) return;
  window.__alexyomiBlock = true;
  try {
    window.open = function () { return null; };
  } catch (e) {}
  var ua = (navigator.userAgent || '');
  var isAndroid = /Android/i.test(ua);
  var desktopSite = !isAndroid && /Windows|Macintosh|CrOS|Linux x86_64|X11/.test(ua);
  function hideAds () {
    // Desktop-site layouts are far more fragile; only drop the explicit Google-ad
    // markers there. Mobile sites get the fuller (still interactive-safe) list.
    var mobileSels = [
      'ins.adsbygoogle', '.adsbygoogle',
      '#google_ads_iframe_*', 'iframe[id*="google_ads_iframe"]',
      'div[class="ad"]', 'div[class="ads"]', 'div[class="ad-slot"]', 'div[class="ad-box"]',
      '.advertisement', '.ad-slot', '.ad-placeholder', '.ad-unit', '.banner-ad',
      '.sponsor-banner', '.adbox', '.advert-placeholder',
      '[class*="popunder" i]', '[class*="pop-under" i]'
    ];
    var desktopSels = [
      'ins.adsbygoogle', '.adsbygoogle',
      'iframe[id*="google_ads_iframe"]',
      '[data-ad-slot]', '[data-ad-unit]', '[data-google-ad-client]',
      'iframe[src*="googlesyndication.com"]'
    ];
    // Only leaf ad nodes: never hide anything containing the video, player chrome,
    // links or controls — those are the site's UI, not ads.
    var sels = desktopSite ? desktopSels : mobileSels;
    for (var i = 0; i < sels.length; i++) {
      try {
        var nodes = document.querySelectorAll(sels[i]);
        for (var j = 0; j < nodes.length; j++) {
          var n = nodes[j];
          if (n.querySelector('video, iframe, button, input, select, a, canvas')) continue;
          if (n.style.display !== 'none') {
            n.style.setProperty('display', 'none', 'important');
          }
        }
      } catch (e) {}
    }
  }
  try {
    hideAds();
    document.addEventListener('DOMContentLoaded', hideAds);
    setTimeout(hideAds, 1200);
    setTimeout(hideAds, 3500);
  } catch (e) {}
})();
"""

private fun emptyResponse(): WebResourceResponse =
    WebResourceResponse("text/plain", "utf-8", ByteArrayInputStream(ByteArray(0)))

private fun isBlockedHost(host: String): Boolean {
    val h = host.lowercase().trimEnd('.')
    return BLOCKED_HOSTS.any { h == it || h.endsWith(".$it") }
}

private val BLOCKED_HOSTS = setOf(
    // --- Mainstream ad stacks / RTB exchanges (Google, DoubleClick & co.) ---
    "adnxs.com", "doubleclick.net", "2mdn.net", "googleadservices.com", "googlesyndication.com",
    "googletagmanager.com", "googletagservices.com", "pagead2.googlesyndication.com", "adsensecustomsearchads.com",
    "advertising.com", "adsymptotic.com", "adsafeprotected.com", "adcolony.com", "adroll.com", "adsrvr.org",
    "criteo.com", "criteo.net", "casalemedia.com", "bidswitch.net", "adform.net", "smartadserver.com",
    "indexww.com", "moatads.com", "serving-sys.com", "yieldmanager.com", "yieldmo.com", "quantserve.com",
    "scorecardresearch.com", "sharethis.com", "pubmatic.com", "rubiconproject.com", "betrad.com", "openx.net",
    "contextweb.com", "lijit.com", "sovrn.com", "adthrive.com", "bttrack.com",
    "mopub.com", "applovin.com", "inmobi.com", "smaato.com", "startapp.com", "flurry.com",
    "adjust.com", "appsflyer.com", "branch.io", "kochava.com", "singular.net",
    "ads.yahoo.com", "adtechus.com", "adsymptotic.net", "adsymptotic.io",
    "revcontent.com", "mgid.com", "taboola.com", "outbrain.com", "content.ad", "medianet.com", "contextual.net",
    "ads-api.twitter.com", "ton.twitter.com", "ads.linkedin.com",
    // --- Popunder / redirect networks (what movie sites actually fire) ---
    "propellerads.com", "propellerads.net", "propellerads.ru", "propeller-tracking.com", "propush.me", "propush.link", "propu.sh",
    "monetag.com", "monetag.net", "mntg.co", "mntg.win", "referrer.top", "onclicka.link", "onclicka.top",
    "adsterra.com", "adsterra.net", "highperformanceformat.com", "profitableratecpm.com", "adrevenue.adsterra.com",
    "exoclick.com", "exadsrv.com", "exdynsrv.com", "exadosrv.com",
    "juicyads.com", "juicyads.net",
    "hilltopads.com", "hilltopads.net", "hilltop-ads.com",
    "popads.net", "popcash.net", "popcash.co",
    "clickadu.com", "clickadu.net",
    "yllix.com", "yllix.net",
    "admaven.com", "admaven.net", "admaven.co",
    "galaksion.com", "galaksion.net",
    "tsyndicate.com", "tsyndicate.net",
    "adcash.com", "adcash.net",
    "trafficjunky.com", "trafficstarz.com", "trafficstars.com",
    "ad-maven.com", "ad-maven.net", "contentabc.com",
    "ero-advertising.com", "plugrush.com", "plugg-rush.com",
    "bidvertiser.com", "popin.cc", "muchacha.club",
    "protrafficinspector.com", "protraffinspector.com",
    "adpx.pro", "adbest.pro",
    "selfgo.site", "go2affise.com",
    "adxpansion.com", "adxpansion.net",
    "tsyndicate.link", "track.tsyndicate.com",
    // --- Common redirect/tracking intermediates seen on streaming sites ---
    "clktrack.com", "clktrk.link", "affiliate-click.link", "href.li", "go2l.ink",
    "za.gl", "clk.sh", "trk.link", "gotrkd.com",
    "ad-market.io", "adpepper.com", "adpartner.io",
    "retrack.me", "realstat1.com", "redirectingat.com",
    "popunder.ru", "popunder.net",
    // --- Push-ad syndication ---
    "subpush.com", "pospush.com", "pushassist.com", "push5.xyz", "nofication.net",
    "webpushl.com", "web-push.io", "truepush.io",
)

private val STREAM_EXTENSIONS = listOf(
    ".m3u8",
    ".mpd",
    ".mp4",
    ".webm",
    ".mkv",
    ".mov",
    ".flv",
    ".ogv",
    ".ts",
    ".m4v",
)

private fun isStreamUrl(url: String): Boolean {
    val host = runCatching { Uri.parse(url).host?.lowercase() }.getOrNull()
    // YouTube's googlevideo segment URLs look like streams but are not playable as files;
    // leave YouTube in the WebView (fullscreen now works there) instead of offering a broken FAB.
    if (host != null &&
        (host.contains("googlevideo.com") || host.contains("youtube.com") || host.contains("ytimg.com"))
    ) {
        return false
    }
    val lower = url.lowercase().substringBefore('#').substringBefore('?')
    if (lower.contains("application/vnd.apple.mpegurl")) return true
    if (lower.contains("application/dash")) return true
    if (lower.contains("video/")) return true
    return STREAM_EXTENSIONS.any { lower.endsWith(it) }
}

private fun resolveUrl(query: String): String {
    val trimmed = query.trim()
    if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) return trimmed
    if (trimmed.startsWith("www.")) return "https://$trimmed"
    if (' ' !in trimmed && trimmed.any { it == '.' }) return "https://$trimmed"
    return "https://www.google.com/search?q=${Uri.encode(trimmed)}"
}
