package com.zhixu.android.ui.components

import android.content.Intent
import android.net.Uri
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.ByteArrayInputStream

@Composable
fun MarkdownPreview(
    modifier: Modifier = Modifier,
    markdown: String,
    fontScale: Float = 1f,
    vaultRootUri: Uri? = null,
    onOpenWikiLink: ((String) -> Unit)? = null,
    onOpenVaultDocUri: ((Uri) -> Unit)? = null,
    loadWikiLinkPreview: (suspend (String) -> String?)? = null,
) {
    val context = LocalContext.current
    val colors = MaterialTheme.colorScheme
    val scope = rememberCoroutineScope()

    var previewDialog by remember { mutableStateOf<WikiPreviewDialogState?>(null) }

    val vaultResolver =
        remember(context, vaultRootUri) {
            vaultRootUri?.let { VaultPathResolver(context = context, vaultRootUri = it) }
        }

    val rawMarkdown = markdown
    val vaultRoot = vaultRootUri?.toString().orEmpty()
    val effectiveFontScale = fontScale.coerceIn(0.5f, 2.5f)
    val nextTheme =
        PreviewTheme(
            isDark = colors.surface.toArgb().isProbablyDark(),
            surface = colors.surface.toArgb().toCssHex(),
            onSurface = colors.onSurface.toArgb().toCssHex(),
            outline = colors.outline.copy(alpha = 0.35f).toArgb().toCssHex(),
            quoteBg = colors.surfaceVariant.copy(alpha = 0.55f).toArgb().toCssHex(),
            link = colors.primary.toArgb().toCssHex(),
            inlineCodeBg = colors.secondaryContainer.copy(alpha = 0.55f).toArgb().toCssHex(),
            inlineCodeFg = colors.onSecondaryContainer.toArgb().toCssHex(),
        )
    val themeJson = nextTheme.toJson()

    var prepared by remember { mutableStateOf<PreparedPreviewState?>(null) }
    LaunchedEffect(rawMarkdown, vaultRoot, themeJson, effectiveFontScale) {
        // Move heavy string processing (regex + JSON escaping) off the main thread to avoid traversal jank.
        val next =
            withContext(Dispatchers.Default) {
                val preprocessed = preprocessWikiLinks(rawMarkdown)
                PreparedPreviewState(
                    themeJsonQuoted = JSONObject.quote(themeJson),
                    vaultRootQuoted = JSONObject.quote(vaultRoot),
                    markdownQuoted = JSONObject.quote(preprocessed),
                    fontScale = effectiveFontScale,
                )
            }
        prepared = next
    }

    AndroidView(
        modifier = modifier,
        factory = {
            WebView(it).apply {
                setBackgroundColor(colors.surface.toArgb())
                setTag(TAG_PAGE_LOADED, false)
                setTag(TAG_LAST_SENT_STATE, null)
                isVerticalScrollBarEnabled = false
                isHorizontalScrollBarEnabled = false
                overScrollMode = WebView.OVER_SCROLL_NEVER

                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.cacheMode = WebSettings.LOAD_DEFAULT
                settings.loadsImagesAutomatically = true
                settings.useWideViewPort = true
                settings.loadWithOverviewMode = true
                settings.setSupportZoom(true)
                settings.builtInZoomControls = true
                settings.displayZoomControls = false
                settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                settings.allowFileAccess = true
                settings.allowContentAccess = true
                settings.allowFileAccessFromFileURLs = true
                settings.allowUniversalAccessFromFileURLs = true

                addJavascriptInterface(
                    PreviewBridge(
                        onCopy = { text ->
                            val clip = android.content.ClipData.newPlainText("code", text)
                            context.getSystemService(android.content.ClipboardManager::class.java).setPrimaryClip(clip)
                        },
                        onWikiLinkLongPress = onWikiLinkLongPress@{ name ->
                            val loader = loadWikiLinkPreview ?: return@onWikiLinkLongPress
                            scope.launch {
                                val snippet =
                                    runCatching { loader(name) }.getOrNull()
                                previewDialog = WikiPreviewDialogState(name = name, snippet = snippet)
                            }
                        },
                    ),
                    "ZhixuPreview",
                )

                webViewClient =
                    PreviewWebViewClient(
                        context = context,
                        vaultResolver = vaultResolver,
                        onOpenWikiLink = onOpenWikiLink,
                        onOpenVaultDocUri = onOpenVaultDocUri,
                        onPageReady = { webView ->
                            webView.setTag(TAG_PAGE_LOADED, true)
                            (webView.getTag(TAG_PENDING_STATE) as? PreparedPreviewState)?.let { pending ->
                                applyPreparedState(webView, pending)
                            }
                        },
                    )

                loadUrl("file:///android_asset/markdown-preview/index.html")
            }
        },
        update = { view ->
            val nextPrepared = prepared ?: return@AndroidView
            val lastSent = view.getTag(TAG_LAST_SENT_STATE) as? PreparedPreviewState
            if (lastSent === nextPrepared) return@AndroidView

            // Keep WebView background in sync with theme changes.
            view.setBackgroundColor(colors.surface.toArgb())
            view.setTag(TAG_PENDING_STATE, nextPrepared)
            view.setTag(TAG_LAST_SENT_STATE, nextPrepared)

            if (view.getTag(TAG_PAGE_LOADED) == true) {
                applyPreparedState(view, nextPrepared)
            }
        },
    )

    DisposableEffect(Unit) {
        onDispose {
            // WebView is owned by AndroidView; no extra work needed here, but keep hook for future cleanup.
        }
    }

    val dialog = previewDialog
    if (dialog != null) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { previewDialog = null },
            title = { androidx.compose.material3.Text(text = dialog.name) },
            text = {
                androidx.compose.material3.Text(
                    text = dialog.snippet ?: "未找到预览内容",
                    style = MaterialTheme.typography.bodyMedium,
                )
            },
            confirmButton = {
                androidx.compose.material3.TextButton(
                    onClick = {
                        onOpenWikiLink?.invoke(dialog.name)
                        previewDialog = null
                    },
                ) { androidx.compose.material3.Text("打开") }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { previewDialog = null }) {
                    androidx.compose.material3.Text("关闭")
                }
            },
        )
    }
}

private fun preprocessWikiLinks(markdown: String): String {
    // Convert [[Name]] -> [Name](zhixu://doc/Name)
    val regex = Regex("""\[\[([^\]]+)\]\]""")
    return markdown.replace(regex) { m ->
        val name = m.groupValues[1].trim()
        if (name.isBlank()) m.value else "[$name](zhixu://doc/${Uri.encode(name)})"
    }
}

private data class WikiPreviewDialogState(
    val name: String,
    val snippet: String?,
)

private data class PreparedPreviewState(
    val themeJsonQuoted: String,
    val vaultRootQuoted: String,
    val markdownQuoted: String,
    val fontScale: Float,
)

private data class PreviewTheme(
    val isDark: Boolean,
    val surface: String,
    val onSurface: String,
    val outline: String,
    val quoteBg: String,
    val link: String,
    val inlineCodeBg: String,
    val inlineCodeFg: String,
) {
    fun toJson(): String =
        """
        {
          "isDark": $isDark,
          "surface": "$surface",
          "onSurface": "$onSurface",
          "outline": "$outline",
          "quoteBg": "$quoteBg",
          "link": "$link",
          "inlineCodeBg": "$inlineCodeBg",
          "inlineCodeFg": "$inlineCodeFg"
        }
        """.trimIndent()
}

private class PreviewBridge(
    private val onCopy: (String) -> Unit,
    private val onWikiLinkLongPress: (String) -> Unit,
) {
    @JavascriptInterface
    fun copy(text: String) {
        onCopy(text)
    }

    @JavascriptInterface
    fun wikiLongPress(name: String) {
        onWikiLinkLongPress(name)
    }
}

private class PreviewWebViewClient(
    private val context: android.content.Context,
    private val vaultResolver: VaultPathResolver?,
    private val onOpenWikiLink: ((String) -> Unit)?,
    private val onOpenVaultDocUri: ((Uri) -> Unit)?,
    private val onPageReady: (WebView) -> Unit,
) : WebViewClient() {
    override fun onPageFinished(view: WebView, url: String?) {
        super.onPageFinished(view, url)
        onPageReady(view)
    }

    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
        val uri = request.url ?: return false
        when (uri.scheme) {
            "zhixu" -> {
                when (uri.host) {
                    "doc" -> {
                        val name = uri.lastPathSegment?.trim().orEmpty()
                        if (name.isNotBlank()) onOpenWikiLink?.invoke(Uri.decode(name))
                        return true
                    }
                    "vault" -> {
                        val relPath = uri.getQueryParameter("path")?.let(Uri::decode).orEmpty()
                        if (relPath.isBlank()) return true
                        val resolved = vaultResolver?.resolve(relPath)
                        if (resolved != null && relPath.endsWith(".md", ignoreCase = true)) {
                            onOpenVaultDocUri?.invoke(resolved)
                            return true
                        }
                        if (resolved != null) {
                            openExternal(resolved)
                        }
                        return true
                    }
                    else -> return true
                }
            }
            "http", "https", "mailto", "tel", "file", "content" -> {
                openExternal(uri)
                return true
            }
            else -> return false
        }
    }

    override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
        val uri = request.url ?: return null
        if (uri.scheme != "zhixu" || uri.host != "vault") return null
        val relPath = uri.getQueryParameter("path")?.let(Uri::decode).orEmpty()
        if (relPath.isBlank()) return emptyNotFound()
        val resolved = vaultResolver?.resolve(relPath) ?: return emptyNotFound()

        return runCatching {
            val resolver = context.contentResolver
            val mime = resolver.getType(resolved) ?: "application/octet-stream"
            val stream = resolver.openInputStream(resolved) ?: return@runCatching emptyNotFound()
            WebResourceResponse(mime, "utf-8", stream)
        }.getOrNull()
    }

    private fun emptyNotFound(): WebResourceResponse =
        WebResourceResponse(
            "text/plain",
            "utf-8",
            404,
            "Not Found",
            mapOf("Cache-Control" to "no-cache"),
            ByteArrayInputStream(ByteArray(0)),
        )

    private fun openExternal(uri: Uri) {
        runCatching {
            val intent =
                Intent(Intent.ACTION_VIEW, uri).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
            context.startActivity(intent)
        }
    }
}

private class VaultPathResolver(
    private val context: android.content.Context,
    private val vaultRootUri: Uri,
) {
    fun resolve(vaultRelativePath: String): Uri? {
        val cleaned = vaultRelativePath.trim().trimStart('/')
        if (cleaned.isBlank()) return null
        val root = DocumentFile.fromTreeUri(context, vaultRootUri) ?: return null
        var current: DocumentFile = root
        val normalized =
            buildList {
                for (segment in cleaned.split('/').map { it.trim() }.filter { it.isNotBlank() }) {
                    when (segment) {
                        "." -> Unit
                        ".." -> if (isNotEmpty()) removeAt(lastIndex)
                        else -> add(segment)
                    }
                }
            }

        for (segment in normalized) {
            val next = current.findFile(segment) ?: return null
            current = next
        }
        return current.uri
    }
}

private fun Int.toCssHex(): String {
    val rgb = this and 0x00FFFFFF
    return String.format("#%06X", rgb)
}

private fun Int.isProbablyDark(): Boolean {
    val r = (this shr 16) and 0xFF
    val g = (this shr 8) and 0xFF
    val b = this and 0xFF
    val luma = (0.2126 * r + 0.7152 * g + 0.0722 * b) / 255.0
    return luma < 0.5
}

private fun applyPreparedState(
    view: WebView,
    pending: PreparedPreviewState,
) {
    // Single bridge call avoids repeated JNI crossings and lets JS batch the apply.
    val js =
        """
        (function(){
          if (!window.__setTheme) return;
          window.__setTheme(${pending.themeJsonQuoted});
          window.__setVaultRoot(${pending.vaultRootQuoted});
          window.__setMarkdown(${pending.markdownQuoted});
          window.__setFontScale(${pending.fontScale});
        })();
        """.trimIndent()
    view.post { view.evaluateJavascript(js, null) }
}

private const val TAG_PENDING_STATE: Int = 0x5A48_4958
private const val TAG_PAGE_LOADED: Int = 0x5A48_4959
private const val TAG_LAST_SENT_STATE: Int = 0x5A48_4960
