package app.zhixu.ui.components

import android.os.Handler
import android.os.Looper
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.viewinterop.AndroidView
import androidx.webkit.WebViewAssetLoader
import org.json.JSONObject
import kotlin.math.max
import kotlin.math.min

@Composable
fun CodeMirrorMarkdownEditor(
    modifier: Modifier = Modifier,
    value: TextFieldValue,
    fontSizeSpValue: Float,
    isSourceMode: Boolean,
    placeholder: String = "",
    onValueChange: (TextFieldValue) -> Unit,
) {
    val context = LocalContext.current
    val colors = MaterialTheme.colorScheme
    val mainHandler = remember { Handler(Looper.getMainLooper()) }
    val latestValue by rememberUpdatedState(value)
    val latestOnValueChange by rememberUpdatedState(onValueChange)

    val themeJson =
        remember(colors.surface, colors.onSurface, colors.outline, colors.primary) {
            EditorTheme(
                isDark = colors.surface.toArgb().isProbablyDark(),
                surface = colors.surface.toArgb().toCssHex(),
                onSurface = colors.onSurface.toArgb().toCssHex(),
                outline = colors.outline.copy(alpha = 0.35f).toArgb().toCssHex(),
                primary = colors.primary.toArgb().toCssHex(),
            ).toJson()
        }
    val themeJsonQuoted = remember(themeJson) { JSONObject.quote(themeJson) }

    val payloadText = value.text
    val payloadTextQuoted = remember(payloadText) { JSONObject.quote(payloadText) }
    val selectionStartRaw = value.selection.start
    val selectionEndRaw = value.selection.end
    val selectionStart = min(selectionStartRaw, selectionEndRaw)
    val selectionEnd = max(selectionStartRaw, selectionEndRaw)
    val mode = if (isSourceMode) "source" else "live"
    val effectiveFontSize = fontSizeSpValue.coerceIn(12f, 28f)

    var prepared by remember { mutableStateOf<PreparedEditorState?>(null) }
    LaunchedEffect(themeJsonQuoted, payloadTextQuoted, selectionStart, selectionEnd, mode, effectiveFontSize, placeholder) {
        prepared =
            PreparedEditorState(
                themeJsonQuoted = themeJsonQuoted,
                textQuoted = payloadTextQuoted,
                selectionStart = selectionStart,
                selectionEnd = selectionEnd,
                modeQuoted = JSONObject.quote(mode),
                fontSizePx = effectiveFontSize,
                placeholderQuoted = JSONObject.quote(placeholder),
            )
    }

    val assetLoader =
        remember(context) {
            WebViewAssetLoader.Builder()
                .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(context))
                .build()
        }

    AndroidView(
        modifier = modifier,
        factory = {
            WebView(it).apply {
                layoutParams =
                    ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT,
                    )
                setBackgroundColor(colors.surface.toArgb())
                setTag(TAG_EDITOR_PAGE_LOADED, false)
                setTag(TAG_EDITOR_PENDING_STATE, null)
                setTag(TAG_EDITOR_LAST_SENT_STATE, null)

                isVerticalScrollBarEnabled = false
                isHorizontalScrollBarEnabled = false
                overScrollMode = WebView.OVER_SCROLL_NEVER

                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.cacheMode = WebSettings.LOAD_DEFAULT
                settings.loadsImagesAutomatically = true
                settings.useWideViewPort = true
                settings.loadWithOverviewMode = true
                settings.setSupportZoom(false)
                settings.builtInZoomControls = false
                settings.displayZoomControls = false
                settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                settings.allowFileAccess = false
                settings.allowContentAccess = true

                val bridge =
                    EditorBridge(
                        handler = mainHandler,
                        onDocChanged = { text, selStart, selEnd ->
                            val safeStartRaw = selStart.coerceIn(0, text.length)
                            val safeEndRaw = selEnd.coerceIn(0, text.length)
                            val safeStart = min(safeStartRaw, safeEndRaw)
                            val safeEnd = max(safeStartRaw, safeEndRaw)
                            latestOnValueChange(
                                TextFieldValue(
                                    text = text,
                                    selection = TextRange(safeStart, safeEnd),
                                ),
                            )
                        },
                        onSelectionChanged = { selStart, selEnd ->
                            val current = latestValue
                            val safeStartRaw = selStart.coerceIn(0, current.text.length)
                            val safeEndRaw = selEnd.coerceIn(0, current.text.length)
                            val safeStart = min(safeStartRaw, safeEndRaw)
                            val safeEnd = max(safeStartRaw, safeEndRaw)
                            val nextSelection = TextRange(safeStart, safeEnd)
                            if (current.selection == nextSelection) return@EditorBridge
                            latestOnValueChange(current.copy(selection = nextSelection, composition = null))
                        },
                    )
                bridge.attach(this)
                addJavascriptInterface(
                    bridge,
                    "ZhixuEditor",
                )

                webViewClient =
                    object : WebViewClient() {
                        override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
                            return assetLoader.shouldInterceptRequest(request.url)
                        }

                        override fun onPageFinished(view: WebView, url: String?) {
                            super.onPageFinished(view, url)
                            view.setTag(TAG_EDITOR_PAGE_LOADED, true)
                            (view.getTag(TAG_EDITOR_PENDING_STATE) as? PreparedEditorState)?.let { pending ->
                                applyPreparedState(view, pending)
                            }
                        }
                    }

                // Bust WebView cache so updated JS/CSS takes effect immediately after app update.
                loadUrl("$APPASSETS_ORIGIN/assets/markdown-editor/index.html?t=${System.currentTimeMillis()}")
            }
        },
        update = { webView ->
            webView.setBackgroundColor(colors.surface.toArgb())
            val nextPrepared = prepared ?: return@AndroidView
            val lastSent = webView.getTag(TAG_EDITOR_LAST_SENT_STATE) as? PreparedEditorState
            val lastFromJs = webView.getTag(TAG_EDITOR_LAST_FROM_JS) as? JsReportedState
            val isEchoFromJs =
                lastFromJs?.let {
                    it.selectionStart == selectionStart &&
                        it.selectionEnd == selectionEnd &&
                        (it.text == null || it.text == value.text)
                } ?: false
            val settingsChanged =
                lastSent?.let {
                    it.themeJsonQuoted != nextPrepared.themeJsonQuoted ||
                        it.modeQuoted != nextPrepared.modeQuoted ||
                        it.fontSizePx != nextPrepared.fontSizePx ||
                        it.placeholderQuoted != nextPrepared.placeholderQuoted
                } ?: true
            if (isEchoFromJs && !settingsChanged) {
                webView.setTag(TAG_EDITOR_LAST_FROM_JS, null)
                webView.setTag(TAG_EDITOR_PENDING_STATE, nextPrepared)
                webView.setTag(TAG_EDITOR_LAST_SENT_STATE, nextPrepared)
                return@AndroidView
            }
            if (lastSent === nextPrepared) return@AndroidView

            webView.setTag(TAG_EDITOR_PENDING_STATE, nextPrepared)
            webView.setTag(TAG_EDITOR_LAST_SENT_STATE, nextPrepared)
            webView.setTag(TAG_EDITOR_LAST_FROM_JS, null)

            if (webView.getTag(TAG_EDITOR_PAGE_LOADED) == true) {
                applyPreparedState(webView, nextPrepared)
            }
        },
    )
}

private data class PreparedEditorState(
    val themeJsonQuoted: String,
    val textQuoted: String,
    val selectionStart: Int,
    val selectionEnd: Int,
    val modeQuoted: String,
    val fontSizePx: Float,
    val placeholderQuoted: String,
)

private data class EditorTheme(
    val isDark: Boolean,
    val surface: String,
    val onSurface: String,
    val outline: String,
    val primary: String,
) {
    fun toJson(): String =
        """
        {
          "isDark": $isDark,
          "surface": "$surface",
          "onSurface": "$onSurface",
          "outline": "$outline",
          "primary": "$primary"
        }
        """.trimIndent()
}

private class EditorBridge(
    private val handler: Handler,
    private val onDocChanged: (String, Int, Int) -> Unit,
    private val onSelectionChanged: (Int, Int) -> Unit,
) {
    @Volatile private var webView: WebView? = null

    fun attach(view: WebView?) {
        webView = view
    }

    @JavascriptInterface
    fun docChanged(
        text: String,
        selectionStart: Int,
        selectionEnd: Int,
    ) {
        handler.post {
            val start = min(selectionStart, selectionEnd)
            val end = max(selectionStart, selectionEnd)
            webView?.setTag(TAG_EDITOR_LAST_FROM_JS, JsReportedState(text = text, selectionStart = start, selectionEnd = end))
            onDocChanged(text, selectionStart, selectionEnd)
        }
    }

    @JavascriptInterface
    fun selectionChanged(
        selectionStart: Int,
        selectionEnd: Int,
    ) {
        handler.post {
            val start = min(selectionStart, selectionEnd)
            val end = max(selectionStart, selectionEnd)
            webView?.setTag(TAG_EDITOR_LAST_FROM_JS, JsReportedState(text = null, selectionStart = start, selectionEnd = end))
            onSelectionChanged(selectionStart, selectionEnd)
        }
    }
}

private data class JsReportedState(
    val text: String?,
    val selectionStart: Int,
    val selectionEnd: Int,
)

private fun applyPreparedState(
    view: WebView,
    pending: PreparedEditorState,
) {
    val js =
        """
        (function(){
          if (!window.__setTheme) return;
          window.__setTheme(${pending.themeJsonQuoted});
          window.__setFontSize(${pending.fontSizePx});
          window.__setMode(${pending.modeQuoted});
          window.__setPlaceholder(${pending.placeholderQuoted});
          window.__setDoc(${pending.textQuoted}, ${pending.selectionStart}, ${pending.selectionEnd});
        })();
        """.trimIndent()
    view.post { view.evaluateJavascript(js, null) }
}

private const val TAG_EDITOR_PAGE_LOADED: Int = 0x5A48_4544
private const val TAG_EDITOR_PENDING_STATE: Int = 0x5A48_4545
private const val TAG_EDITOR_LAST_SENT_STATE: Int = 0x5A48_4546
private const val TAG_EDITOR_LAST_FROM_JS: Int = 0x5A48_4547
private const val APPASSETS_HOST: String = "appassets.androidplatform.net"
private const val APPASSETS_ORIGIN: String = "https://$APPASSETS_HOST"

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
