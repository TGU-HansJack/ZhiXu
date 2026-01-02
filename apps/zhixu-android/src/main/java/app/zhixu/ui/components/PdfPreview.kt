package app.zhixu.ui.components

import android.content.Intent
import android.net.Uri
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.os.Handler
import android.os.Looper
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.webkit.WebViewAssetLoader
import app.zhixu.data.UiFontOption
import app.zhixu.data.UiPreferences
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.File
import java.io.InputStream
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.min

enum class PdfLayoutMode {
    FitWidth,
    FitHeight,
    SinglePage,
    SpreadOdd,
    SpreadEven,
}

class PdfPreviewController {
    @Volatile private var webView: WebView? = null
    private val nextThumbRequestId = AtomicLong(1)
    private val thumbCallbacks = ConcurrentHashMap<Long, (String?) -> Unit>()

    internal fun attach(view: WebView?) {
        webView = view
    }

    fun zoomIn() {
        evalJs("window.__zoomIn && window.__zoomIn();")
    }

    fun zoomOut() {
        evalJs("window.__zoomOut && window.__zoomOut();")
    }

    fun setLayoutMode(mode: PdfLayoutMode) {
        val raw =
            when (mode) {
                PdfLayoutMode.FitWidth -> "fit_width"
                PdfLayoutMode.FitHeight -> "fit_height"
                PdfLayoutMode.SinglePage -> "single"
                PdfLayoutMode.SpreadOdd -> "spread_odd"
                PdfLayoutMode.SpreadEven -> "spread_even"
            }
        evalJs("window.__setLayout && window.__setLayout(${JSONObject.quote(raw)});")
    }

    fun goToPage(page: Int) {
        val n = page.coerceAtLeast(1)
        evalJs("window.__goToPage && window.__goToPage($n);")
    }

    fun requestThumbnail(
        page: Int,
        maxWidthPx: Int,
        onResult: (String?) -> Unit,
    ) {
        val id = nextThumbRequestId.getAndIncrement()
        thumbCallbacks[id] = onResult
        val p = page.coerceAtLeast(1)
        val w = maxWidthPx.coerceIn(32, 512)
        evalJs("window.__requestThumbnail && window.__requestThumbnail(${JSONObject.quote(id.toString())}, ${JSONObject.quote(p.toString())}, $w);")
    }

    internal fun deliverThumbnail(
        requestId: Long,
        dataUrl: String?,
    ) {
        val cb = thumbCallbacks.remove(requestId) ?: return
        cb(dataUrl)
    }

    private fun evalJs(js: String) {
        val view = webView ?: return
        view.post { view.evaluateJavascript(js, null) }
    }
}

@Composable
fun PdfPreview(
    modifier: Modifier = Modifier,
    docUri: Uri,
    controller: PdfPreviewController? = null,
    onPageState: ((currentPage: Int, totalPages: Int) -> Unit)? = null,
    onOpenExternal: ((Uri) -> Unit)? = null,
) {
    val context = LocalContext.current
    val colors = MaterialTheme.colorScheme
    val mainHandler = remember { Handler(Looper.getMainLooper()) }

    val uiPrefs = remember(context) { UiPreferences(context.applicationContext) }
    val fontOption by uiPrefs.fontOption.collectAsState(initial = UiFontOption.SYSTEM)
    val fontKey = fontOption.raw

    val themeJson =
        remember(colors.surface, colors.onSurface, colors.outline) {
            PdfTheme(
                isDark = colors.surface.toArgb().isProbablyDark(),
                surface = colors.surface.toArgb().toCssHex(),
                onSurface = colors.onSurface.toArgb().toCssHex(),
                outline = colors.outline.copy(alpha = 0.35f).toArgb().toCssHex(),
            ).toJson()
        }
    val themeJsonQuoted = remember(themeJson) { JSONObject.quote(themeJson) }

    var pageLoaded by remember { mutableStateOf(false) }
    var lastSentTheme by remember { mutableStateOf<String?>(null) }
    var lastSentUri by remember { mutableStateOf<String?>(null) }
    var lastSentFontKey by remember { mutableStateOf<String?>(null) }

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
                setBackgroundColor(colors.surface.toArgb())
                isVerticalScrollBarEnabled = false
                isHorizontalScrollBarEnabled = false
                overScrollMode = WebView.OVER_SCROLL_NEVER

                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.cacheMode = WebSettings.LOAD_DEFAULT
                settings.loadsImagesAutomatically = true
                settings.useWideViewPort = true
                settings.loadWithOverviewMode = true
                // Enable pinch-to-zoom for PDF reading; the editor drawer gestures are disabled in PDF mode.
                settings.setSupportZoom(true)
                settings.builtInZoomControls = true
                settings.displayZoomControls = false
                settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                settings.allowFileAccess = false
                settings.allowContentAccess = true

                addJavascriptInterface(
                    PdfBridge(
                        handler = mainHandler,
                        onState = { current, total -> onPageState?.invoke(current, total) },
                        onThumbnail = { requestId, dataUrl ->
                            controller?.deliverThumbnail(requestId, dataUrl)
                        },
                    ),
                    "ZhixuPdf",
                )

                webViewClient =
                    PdfWebViewClient(
                        context = context,
                        assetLoader = assetLoader,
                        getDocUri = { getTag(TAG_PDF_DOC_URI) as? Uri },
                        onOpenExternal =
                            onOpenExternal ?: { uri ->
                                runCatching {
                                    val intent =
                                        Intent(Intent.ACTION_VIEW, uri).apply {
                                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                        }
                                    context.startActivity(intent)
                                }
                                Unit
                            },
                        onPageReady = { webView ->
                            pageLoaded = true
                            (webView.getTag(TAG_PDF_PENDING_JS) as? String)?.let { pending ->
                                webView.setTag(TAG_PDF_PENDING_JS, null)
                                webView.post { webView.evaluateJavascript(pending, null) }
                            }
                        },
                    )

                setTag(TAG_PDF_DOC_URI, docUri)
                controller?.attach(this)
                loadUrl("$APPASSETS_ORIGIN/assets/pdf-preview/index.html")
            }
        },
        update = { webView ->
            webView.setBackgroundColor(colors.surface.toArgb())
            webView.setTag(TAG_PDF_DOC_URI, docUri)
            controller?.attach(webView)

            val uriKey = docUri.toString()
            val themeKey = themeJson
            val needsPdfUrl = lastSentUri != uriKey
            val needsTheme = lastSentTheme != themeKey
            val needsFontKey = lastSentFontKey != fontKey
            if (!needsPdfUrl && !needsTheme && !needsFontKey) return@AndroidView

            val js =
                """
                (function() {
                  ${if (needsTheme) "window.__setTheme($themeJsonQuoted);" else ""}
                  ${if (needsFontKey) "window.__setFontKey(${JSONObject.quote(fontKey)});" else ""}
                  ${if (needsPdfUrl) "window.__setPdfUrl(${JSONObject.quote("$APPASSETS_ORIGIN/__pdf?t=${System.currentTimeMillis()}")});" else ""}
                })();
                """.trimIndent()

            lastSentUri = uriKey
            lastSentTheme = themeKey
            lastSentFontKey = fontKey

            if (pageLoaded) {
                webView.post { webView.evaluateJavascript(js, null) }
            } else {
                webView.setTag(TAG_PDF_PENDING_JS, js)
            }
        },
    )
}

private class PdfBridge(
    private val handler: Handler,
    private val onState: (current: Int, total: Int) -> Unit,
    private val onThumbnail: (requestId: Long, dataUrl: String?) -> Unit,
) {
    @android.webkit.JavascriptInterface
    fun onState(current: String?, total: String?) {
        val c = current?.trim()?.toIntOrNull() ?: 1
        val t = total?.trim()?.toIntOrNull() ?: 0
        handler.post { onState(c.coerceAtLeast(1), t.coerceAtLeast(0)) }
    }

    @android.webkit.JavascriptInterface
    fun onThumbnail(requestId: String?, dataUrl: String?) {
        val rid = requestId?.trim()?.toLongOrNull() ?: return
        handler.post { onThumbnail(rid, dataUrl) }
    }
}

private data class PdfTheme(
    val isDark: Boolean,
    val surface: String,
    val onSurface: String,
    val outline: String,
) {
    fun toJson(): String =
        """
        {
          "isDark": $isDark,
          "surface": "$surface",
          "onSurface": "$onSurface",
          "outline": "$outline"
        }
        """.trimIndent()
}

private class PdfWebViewClient(
    private val context: android.content.Context,
    private val assetLoader: WebViewAssetLoader,
    private val getDocUri: () -> Uri?,
    private val onOpenExternal: (Uri) -> Unit,
    private val onPageReady: (WebView) -> Unit,
) : WebViewClient() {
    override fun onPageFinished(view: WebView, url: String?) {
        super.onPageFinished(view, url)
        onPageReady(view)
    }

    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
        val uri = request.url ?: return false
        if (uri.host.equals(APPASSETS_HOST, ignoreCase = true)) return false
        when (uri.scheme) {
            "http", "https", "mailto", "tel", "content", "file" -> {
                onOpenExternal(uri)
                return true
            }
        }
        return false
    }

    override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
        val uri = request.url ?: return null
        if (uri.host.equals(APPASSETS_HOST, ignoreCase = true) && uri.path == "/__pdf") {
            val docUri = getDocUri() ?: return emptyNotFound()
            return servePdf(docUri, request)
        }
        return assetLoader.shouldInterceptRequest(uri)
    }

    private fun servePdf(docUri: Uri, request: WebResourceRequest): WebResourceResponse {
        val mime = "application/pdf"
        val resolver = context.contentResolver

        val rangeHeader = request.requestHeaders["Range"]
        val (rangeStart, rangeEnd) = parseRange(rangeHeader)

        val afd =
            if (docUri.scheme.equals("file", ignoreCase = true)) {
                val path = docUri.path ?: return emptyNotFound()
                val file = File(path)
                if (!file.exists() || !file.isFile) return emptyNotFound()
                resolver.openAssetFileDescriptor(Uri.fromFile(file), "r")
            } else {
                resolver.openAssetFileDescriptor(docUri, "r")
            } ?: return emptyNotFound()

        val length = afd.length
        if (length <= 0) {
            val stream = CloseOnCloseInputStream(afd.createInputStream(), onClose = { afd.close() })
            return WebResourceResponse(
                mime,
                null,
                200,
                "OK",
                mapOf("Cache-Control" to "no-store"),
                stream,
            )
        }

        val start = (rangeStart ?: 0L).coerceIn(0L, length - 1)
        val end = (rangeEnd ?: (length - 1)).coerceIn(start, length - 1)
        val bytesToSend = (end - start + 1).coerceAtLeast(0L)
        val baseStream = afd.createInputStream()
        if (!skipFully(baseStream, start)) {
            runCatching { baseStream.close() }
            runCatching { afd.close() }
            return emptyNotFound()
        }
        val stream = CloseOnCloseInputStream(LimitedInputStream(baseStream, bytesToSend), onClose = { afd.close() })

        val headers =
            buildMap {
                put("Cache-Control", "no-store")
                put("Accept-Ranges", "bytes")
                put("Content-Length", bytesToSend.toString())
                if (rangeHeader != null) {
                    put("Content-Range", "bytes $start-$end/$length")
                }
            }

        return if (rangeHeader != null) {
            WebResourceResponse(mime, null, 206, "Partial Content", headers, stream)
        } else {
            WebResourceResponse(mime, null, 200, "OK", headers, stream)
        }
    }

    private fun emptyNotFound(): WebResourceResponse =
        WebResourceResponse(
            "text/plain",
            "utf-8",
            404,
            "Not Found",
            mapOf("Cache-Control" to "no-store"),
            ByteArrayInputStream(ByteArray(0)),
        )
}

private fun parseRange(range: String?): Pair<Long?, Long?> {
    if (range.isNullOrBlank()) return null to null
    val m = Regex("""bytes=(\d*)-(\d*)""", RegexOption.IGNORE_CASE).find(range) ?: return null to null
    val start = m.groupValues[1].toLongOrNull()
    val end = m.groupValues[2].toLongOrNull()
    return start to end
}

private fun skipFully(stream: InputStream, bytes: Long): Boolean {
    var remaining = bytes
    while (remaining > 0) {
        val skipped = stream.skip(remaining)
        if (skipped > 0) {
            remaining -= skipped
            continue
        }
        if (stream.read() == -1) return false
        remaining -= 1
    }
    return true
}

private class LimitedInputStream(
    private val delegate: InputStream,
    byteLimit: Long,
) : InputStream() {
    private var remaining = byteLimit

    override fun read(): Int {
        if (remaining <= 0) return -1
        val r = delegate.read()
        if (r != -1) remaining -= 1
        return r
    }

    override fun read(b: ByteArray, off: Int, len: Int): Int {
        if (remaining <= 0) return -1
        val toRead = min(len.toLong(), remaining).toInt()
        val n = delegate.read(b, off, toRead)
        if (n > 0) remaining -= n.toLong()
        return n
    }

    override fun close() {
        delegate.close()
    }
}

private class CloseOnCloseInputStream(
    private val delegate: InputStream,
    private val onClose: () -> Unit,
) : InputStream() {
    override fun read(): Int = delegate.read()

    override fun read(b: ByteArray, off: Int, len: Int): Int = delegate.read(b, off, len)

    override fun close() {
        runCatching { delegate.close() }
        runCatching { onClose() }
    }
}

private const val TAG_PDF_DOC_URI: Int = 0x5A48_5050
private const val TAG_PDF_PENDING_JS: Int = 0x5A48_5051
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
