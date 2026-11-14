package eu.kanade.presentation.movies

import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.net.Uri
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.Clear
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.FilledTonalButton
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
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
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

@Composable
fun MoviesTabScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val screenModel = remember { MoviesScreenModel() }

    var query by remember { mutableStateOf("") }
    var url by remember { mutableStateOf<String?>(null) }
    val streams = remember { MovieStreams() }
    val detected = streams.streams

    fun goTo(target: String) {
        streams.clear()
        url = target
    }

    fun goToResolved(target: String) = goTo(resolveUrl(target))

    Scaffold(
        topBar = {
            AppBar(
                title = stringResource(AYMR.strings.label_movies),
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
            val client = remember(currentUrl, streams) {
                object : AccompanistWebViewClient() {
                    override fun shouldInterceptRequest(
                        view: WebView,
                        request: WebResourceRequest?,
                    ): WebResourceResponse? {
                        val host = request?.url?.host
                        if (host != null && isBlockedHost(host)) {
                            // Cut the ad off before it fires.
                            return WebResourceResponse("text/plain", "utf-8", null)
                        }
                        val requestUrl = request?.url?.toString().orEmpty()
                        if (isStreamUrl(requestUrl) && requestUrl !in streams.streams) {
                            streams.add(requestUrl)
                        }
                        return super.shouldInterceptRequest(view, request)
                    }

                    override fun shouldOverrideUrlLoading(
                        view: WebView?,
                        request: WebResourceRequest?,
                    ): Boolean {
                        val requestUrl = request?.url?.toString().orEmpty()
                        if (isStreamUrl(requestUrl)) {
                            scope.launch { screenModel.playMovie(context, requestUrl, requestUrl) }
                            return true
                        }
                        return super.shouldOverrideUrlLoading(view, request)
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
                onCreated = { webView ->
                    webView.setDefaultSettings()
                    if (BuildConfig.DEBUG) {
                        WebView.setWebContentsDebuggingEnabled(true)
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
                            scope.launch { screenModel.playMovie(context, first, first) }
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
        }
    }
}

private fun isBlockedHost(host: String): Boolean {
    val h = host.lowercase()
    if (h in BLOCKED_DOMAINS) return true
    return BLOCKED_SUFFIXES.any { h.endsWith(it) }
}

private val BLOCKED_DOMAINS = setOf(
    "adnxs.com",
    "adcolony.com",
    "adroll.com",
    "adsrvr.org",
    "adthrive.com",
    "bttrack.com",
    "criteo.com",
    "criteo.net",
    "doubleclick.net",
    "googleadservices.com",
    "googlesyndication.com",
    "googletagmanager.com",
    "googletagservices.com",
    "mopub.com",
    "moatads.com",
    "openx.net",
    "outbrain.com",
    "pubmatic.com",
    "quantserve.com",
    "rubiconproject.com",
    "serving-sys.com",
    "sharethis.com",
    "scorecardresearch.com",
    "taboola.com",
    "yieldmanager.com",
    "yieldmo.com",
)

private val BLOCKED_SUFFIXES = listOf(
    ".googletagmanager.com",
    ".googlesyndication.com",
    ".doubleclick.net",
    ".criteo.com",
    ".outbrain.com",
    ".taboola.com",
    ".moatads.com",
    ".yieldmo.com",
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
