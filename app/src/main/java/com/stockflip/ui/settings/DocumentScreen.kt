package com.stockflip.ui.settings

import android.os.Build
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.stockflip.BuildConfig
import com.stockflip.markdownToHtml
import com.stockflip.ui.theme.Space

/** Tillåtna asset-namn; samma vitlista som `MarkdownAssetFragment`. */
private val ALLOWED_DOCUMENTS = mapOf("manual.md" to "Hjälp", "changelog.md" to "Ändringslogg")

/** Titel för ett dokument, eller `null` om assetnamnet inte är tillåtet. */
internal fun documentTitle(asset: String): String? = ALLOWED_DOCUMENTS[asset]

/**
 * Visar manualen eller ändringsloggen som HTML i en låst WebView (ingen JS, inga nätverksanrop,
 * strikt CSP) — samma säkerhetsinställningar som `MarkdownAssetFragment`.
 */
@Composable
internal fun DocumentScreen(asset: String, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val title = documentTitle(asset)
    Column(modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = Space.sm, vertical = Space.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Tillbaka")
            }
            Text(title ?: "Dokument", style = MaterialTheme.typography.titleLarge)
        }
        if (title != null) MarkdownWebView(asset, Modifier.fillMaxSize())
    }
}

@Composable
private fun MarkdownWebView(asset: String, modifier: Modifier) {
    val context = LocalContext.current
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val html = remember(asset, dark) {
        val markdown = context.assets.open(asset).bufferedReader().use { it.readText() }
        markdownToHtml(markdown, dark)
    }
    var webView: WebView? = null
    DisposableEffect(Unit) { onDispose { webView?.destroy() } }
    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            WebView.setWebContentsDebuggingEnabled(BuildConfig.DEBUG)
            WebView(ctx).also { wv ->
                webView = wv
                with(wv.settings) {
                    javaScriptEnabled = false
                    domStorageEnabled = false
                    allowFileAccess = false
                    allowContentAccess = false
                    blockNetworkLoads = true
                    mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) safeBrowsingEnabled = true
                }
                wv.webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean =
                        (request.url.scheme ?: return true) != "about"
                }
            }
        },
        update = { it.loadDataWithBaseURL("about:blank", html, "text/html", "UTF-8", null) },
    )
}
