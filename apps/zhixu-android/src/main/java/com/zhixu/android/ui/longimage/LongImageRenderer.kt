package com.zhixu.android.ui.longimage

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.net.Uri
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.json.JSONTokener
import org.json.JSONObject
import java.io.ByteArrayInputStream
import kotlin.math.ceil
import kotlin.math.roundToInt
import kotlin.coroutines.resume

internal data class LongImageRenderInput(
    val context: Context,
    val markdown: String,
    val vaultRootUri: Uri?,
    val themeJson: String,
    val fontScale: Float,
    val backgroundArgb: Int,
    val targetWidthPx: Int,
)

internal suspend fun renderLongImage(input: LongImageRenderInput): Bitmap =
    withContext(Dispatchers.Main) {
        val context = input.context
        // Improve reliability of full-document drawing for offscreen WebViews.
        runCatching { WebView.enableSlowWholeDocumentDraw() }
        val webView =
            WebView(context).apply {
                setBackgroundColor(input.backgroundArgb)
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
                settings.allowFileAccess = true
                settings.allowContentAccess = true
                settings.allowFileAccessFromFileURLs = true
                settings.allowUniversalAccessFromFileURLs = true
            }

        try {
            val viewportHeightPx = (context.resources.displayMetrics.heightPixels).coerceAtLeast(1)
            webView.measure(
                android.view.View.MeasureSpec.makeMeasureSpec(input.targetWidthPx, android.view.View.MeasureSpec.EXACTLY),
                android.view.View.MeasureSpec.makeMeasureSpec(viewportHeightPx, android.view.View.MeasureSpec.EXACTLY),
            )
            webView.layout(0, 0, input.targetWidthPx, viewportHeightPx)

            suspendCancellableCoroutine { cont ->
                webView.webViewClient =
                    LongImageWebViewClient(
                        context = context,
                        vaultRootUri = input.vaultRootUri,
                        onPageReady = { if (cont.isActive) cont.resume(Unit) },
                    )
                webView.loadUrl("file:///android_asset/markdown-preview/index.html")
            }

            val themeQuoted = JSONObject.quote(input.themeJson)
            val vaultRootQuoted = JSONObject.quote(input.vaultRootUri?.toString().orEmpty())
            val markdownQuoted = JSONObject.quote(preprocessWikiLinks(input.markdown))
            val js =
                """
                (function(){
                  if (!window.__setTheme) return;
                  window.__setTheme($themeQuoted);
                  window.__setVaultRoot($vaultRootQuoted);
                  window.__setMarkdown($markdownQuoted);
                  window.__setFontScale(${input.fontScale.coerceIn(0.5f, 2.5f)});
                })();
                """.trimIndent()
            evalJs(webView, js)

            // Wait until DOM is ready and images are loaded (best-effort).
            val metrics = awaitStableMetrics(webView)
            val contentHeightPx =
                ceil(metrics.heightCssPx * metrics.devicePixelRatio).toInt().coerceAtLeast(1)

            // Re-layout WebView to full document height so we can render by translating the canvas
            // (more reliable than scrolling an offscreen WebView on some devices).
            webView.scrollTo(0, 0)
            webView.measure(
                android.view.View.MeasureSpec.makeMeasureSpec(input.targetWidthPx, android.view.View.MeasureSpec.EXACTLY),
                android.view.View.MeasureSpec.makeMeasureSpec(contentHeightPx, android.view.View.MeasureSpec.EXACTLY),
            )
            webView.layout(0, 0, input.targetWidthPx, contentHeightPx)
            awaitNextFrame()

            val tileHeightPx = viewportHeightPx.coerceIn(700, 2200)

            val output =
                Bitmap.createBitmap(input.targetWidthPx, contentHeightPx, Bitmap.Config.ARGB_8888).also { out ->
                    val outCanvas = Canvas(out)
                    outCanvas.drawColor(input.backgroundArgb)

                    for (y in 0 until contentHeightPx step tileHeightPx) {
                        val partHeight = (contentHeightPx - y).coerceAtMost(tileHeightPx)
                        val tile = Bitmap.createBitmap(input.targetWidthPx, partHeight, Bitmap.Config.ARGB_8888)
                        val tileCanvas = Canvas(tile)
                        tileCanvas.drawColor(input.backgroundArgb)

                        // Draw the full-height WebView into the tile by shifting the canvas up.
                        tileCanvas.translate(0f, -y.toFloat())
                        webView.invalidate()
                        awaitNextFrame()
                        webView.draw(tileCanvas)

                        outCanvas.drawBitmap(tile, 0f, y.toFloat(), null)
                        tile.recycle()
                    }
                }

            output
        } finally {
            webView.stopLoading()
            webView.webViewClient = WebViewClient()
            webView.destroy()
        }
    }

private suspend fun evalJs(
    webView: WebView,
    script: String,
): String? =
    withContext(Dispatchers.Main) {
        suspendCancellableCoroutine { cont ->
            webView.evaluateJavascript(script) { value ->
                if (!cont.isActive) return@evaluateJavascript
                cont.resume(value)
            }
        }
    }

private data class DomMetrics(
    val heightCssPx: Int,
    val devicePixelRatio: Float,
    val readyState: String,
    val imagesDone: Boolean,
)

private suspend fun awaitStableMetrics(webView: WebView): DomMetrics {
    var last: DomMetrics? = null
    repeat(160) {
        val metrics = queryMetrics(webView)
        last = metrics
        if (metrics.heightCssPx > 0 && metrics.imagesDone && metrics.readyState != "loading") return metrics
        delay(50)
    }
    return last ?: DomMetrics(heightCssPx = 1, devicePixelRatio = 1f, readyState = "unknown", imagesDone = false)
}

private suspend fun queryMetrics(webView: WebView): DomMetrics {
    val raw =
        evalJs(
            webView,
            """
            (function(){
              var h = document.documentElement ? document.documentElement.scrollHeight : 0;
              var imgs = Array.prototype.slice.call(document.images || []);
              var done = imgs.every(function(i){ return !!i.complete; });
              var dpr = (window.devicePixelRatio || 1);
              return { h: h || 0, done: done, rs: document.readyState || "", dpr: dpr };
            })();
            """.trimIndent(),
        )
    val obj = decodeEvalObject(raw)
    val h = obj?.optInt("h", 0) ?: 0
    val dpr = (obj?.optDouble("dpr", 1.0) ?: 1.0).toFloat().coerceIn(0.5f, 4f)
    val rs = obj?.optString("rs").orEmpty()
    val done = obj?.optBoolean("done", false) ?: false
    return DomMetrics(heightCssPx = h, devicePixelRatio = dpr, readyState = rs, imagesDone = done)
}

private fun decodeEvalObject(value: String?): JSONObject? {
    if (value.isNullOrBlank() || value == "null") return null
    return runCatching {
        val tok = JSONTokener(value).nextValue()
        when (tok) {
            is JSONObject -> tok
            is String -> runCatching { JSONObject(tok) }.getOrNull()
            else -> null
        }
    }.getOrNull()
}

private suspend fun awaitNextFrame() {
    withContext(Dispatchers.Main) {
        suspendCancellableCoroutine { cont ->
            android.view.Choreographer.getInstance().postFrameCallback {
                if (cont.isActive) cont.resume(Unit)
            }
        }
    }
}

private class LongImageWebViewClient(
    private val context: Context,
    internal val vaultRootUri: Uri?,
    private val onPageReady: (WebView) -> Unit,
) : WebViewClient() {
    override fun onPageFinished(view: WebView, url: String?) {
        super.onPageFinished(view, url)
        onPageReady(view)
    }

    override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
        val uri = request.url ?: return null
        if (uri.scheme != "zhixu" || uri.host != "vault") return null
        val root = vaultRootUri ?: return emptyNotFound()
        val relPath = uri.getQueryParameter("path")?.let(Uri::decode).orEmpty()
        if (relPath.isBlank()) return emptyNotFound()
        val resolved = resolveVaultPath(context, root, relPath) ?: return emptyNotFound()
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
}

private fun resolveVaultPath(
    context: Context,
    vaultRootUri: Uri,
    vaultRelativePath: String,
): Uri? {
    val cleaned = vaultRelativePath.trim().trimStart('/')
    if (cleaned.isBlank()) return null
    val root = com.zhixu.android.data.vaultRootToDocumentFile(context, vaultRootUri) ?: return null
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

private fun preprocessWikiLinks(markdown: String): String {
    // Convert [[Name]] -> [Name](zhixu://doc/Name)
    val regex = Regex("""\[\[([^\]]+)\]\]""")
    // Keep YAML front matter intact (matches MarkdownPreview).
    val frontMatterPrefixLen =
        runCatching {
            val offset = if (markdown.startsWith("\uFEFF")) 1 else 0
            val cleaned = markdown.substring(offset)
            if (!cleaned.startsWith("---")) return@runCatching null
            val eol = cleaned.indexOf('\n')
            if (eol == -1) return@runCatching null
            if (cleaned.substring(0, eol).trim() != "---") return@runCatching null
            val end = Regex("""(?m)^\s*---\s*$""").find(cleaned, startIndex = eol + 1) ?: return@runCatching null
            val after = cleaned.indexOf('\n', end.range.last).let { if (it == -1) cleaned.length else it + 1 }
            offset + after
        }.getOrNull()
    val prefixLen = frontMatterPrefixLen ?: 0
    val prefix = if (prefixLen > 0) markdown.substring(0, prefixLen) else ""
    val rest = if (prefixLen > 0) markdown.substring(prefixLen) else markdown
    val processed =
        rest.replace(regex) { m ->
            val name = m.groupValues[1].trim()
            if (name.isBlank()) m.value else "[$name](zhixu://doc/${Uri.encode(name)})"
        }
    return prefix + processed
}
