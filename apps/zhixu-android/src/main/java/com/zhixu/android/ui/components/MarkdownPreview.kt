package com.zhixu.android.ui.components

import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.text.Selection
import android.text.Spannable
import android.text.Spanned
import android.text.method.LinkMovementMethod
import android.text.style.ClickableSpan
import android.text.style.URLSpan
import android.util.TypedValue
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.widget.TextView
import android.widget.FrameLayout
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.widget.NestedScrollView
import androidx.documentfile.provider.DocumentFile
import io.noties.markwon.AbstractMarkwonPlugin
import io.noties.markwon.LinkResolver
import io.noties.markwon.Markwon
import io.noties.markwon.MarkwonConfiguration
import io.noties.markwon.MarkwonSpansFactory
import io.noties.markwon.core.CorePlugin
import io.noties.markwon.core.MarkwonTheme
import io.noties.markwon.ext.latex.JLatexMathPlugin
import io.noties.markwon.ext.strikethrough.StrikethroughPlugin
import io.noties.markwon.ext.tables.TablePlugin
import io.noties.markwon.ext.tasklist.TaskListPlugin
import io.noties.markwon.image.AsyncDrawable
import io.noties.markwon.image.AsyncDrawableLoader
import io.noties.markwon.image.AsyncDrawableScheduler
import io.noties.markwon.image.ImageSpanFactory
import io.noties.markwon.inlineparser.MarkwonInlineParserPlugin
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.commonmark.node.Image
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.Future

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
    val density = LocalDensity.current

    var previewDialog by remember { mutableStateOf<WikiPreviewDialogState?>(null) }

    val vaultResolver =
        remember(context, vaultRootUri) {
            vaultRootUri?.let { VaultPathResolver(context = context, vaultRootUri = it) }
        }

    val effectiveFontScale = fontScale.coerceIn(0.5f, 2.5f)
    val baseTextSizeSp = 16f * effectiveFontScale
    val baseTextSizePx = with(density) { baseTextSizeSp.sp.toPx() }

    val linkResolver =
        remember(context, vaultResolver, onOpenWikiLink, onOpenVaultDocUri) {
            PreviewLinkResolver(
                context = context,
                vaultResolver = vaultResolver,
                onOpenWikiLink = onOpenWikiLink,
                onOpenVaultDocUri = onOpenVaultDocUri,
            )
        }

    val asyncDrawableLoader =
        remember(context, vaultResolver) {
            VaultAsyncDrawableLoader(
                context = context,
                vaultResolver = vaultResolver,
            )
        }
    DisposableEffect(asyncDrawableLoader) {
        onDispose { asyncDrawableLoader.shutdown() }
    }

    val markwon =
        remember(context, colors, baseTextSizePx, linkResolver, asyncDrawableLoader) {
            Markwon.builderNoCore(context)
                .usePlugin(CorePlugin.create().hasExplicitMovementMethod(true))
                .usePlugin(MarkwonInlineParserPlugin.create())
                .usePlugin(TablePlugin.create(context))
                .usePlugin(TaskListPlugin.create(context))
                .usePlugin(StrikethroughPlugin.create())
                .usePlugin(JLatexMathPlugin.create(baseTextSizePx))
                .usePlugin(
                    object : AbstractMarkwonPlugin() {
                        override fun configureTheme(builder: MarkwonTheme.Builder) {
                            builder
                                .linkColor(colors.primary.toArgb())
                                .codeTextColor(colors.onSecondaryContainer.toArgb())
                                .codeBackgroundColor(colors.secondaryContainer.copy(alpha = 0.55f).toArgb())
                                .codeBlockBackgroundColor(colors.secondaryContainer.copy(alpha = 0.35f).toArgb())
                                .blockQuoteColor(colors.outline.copy(alpha = 0.25f).toArgb())
                                .listItemColor(colors.onSurface.toArgb())
                        }

                        override fun configureConfiguration(builder: MarkwonConfiguration.Builder) {
                            builder
                                .linkResolver(linkResolver)
                                .asyncDrawableLoader(asyncDrawableLoader)
                        }

                        override fun configureSpansFactory(builder: MarkwonSpansFactory.Builder) {
                            builder.setFactory(Image::class.java, ImageSpanFactory())
                        }

                        override fun beforeSetText(textView: TextView, markdown: Spanned) {
                            AsyncDrawableScheduler.unschedule(textView)
                        }

                        override fun afterSetText(textView: TextView) {
                            AsyncDrawableScheduler.schedule(textView)
                        }
                    },
                )
                .build()
        }

    val linkMovementMethod =
        remember(context, loadWikiLinkPreview, scope) {
            LongPressLinkMovementMethod { link ->
                val uri = runCatching { Uri.parse(link) }.getOrNull() ?: return@LongPressLinkMovementMethod
                if (uri.scheme != "zhixu" || uri.host != "doc") return@LongPressLinkMovementMethod
                val name = uri.lastPathSegment?.trim().orEmpty().let(Uri::decode)
                if (name.isBlank()) return@LongPressLinkMovementMethod
                val loader = loadWikiLinkPreview ?: return@LongPressLinkMovementMethod
                scope.launch {
                    val snippet = runCatching { loader(name) }.getOrNull()
                    previewDialog = WikiPreviewDialogState(name = name, snippet = snippet)
                }
            }
        }

    var rendered by remember { mutableStateOf<Spanned?>(null) }
    LaunchedEffect(markdown, markwon) {
        val processed = preprocessWikiLinks(markdown)
        rendered =
            withContext(Dispatchers.Default) {
                val node = markwon.parse(processed)
                markwon.render(node)
            }
    }

    AndroidView(
        modifier = modifier,
        factory = { viewContext ->
            val textView =
                TextView(viewContext).apply {
                    setTextColor(colors.onSurface.toArgb())
                    setBackgroundColor(colors.surface.toArgb())
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, baseTextSizeSp)
                    setTextIsSelectable(true)
                    isVerticalScrollBarEnabled = false
                    movementMethod = linkMovementMethod
                }

            NestedScrollView(viewContext).apply {
                isFillViewport = true
                overScrollMode = View.OVER_SCROLL_NEVER
                setBackgroundColor(colors.surface.toArgb())
                addView(
                    textView,
                    FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.WRAP_CONTENT,
                    ),
                )
                setTag(TAG_TEXT_VIEW, textView)
            }
        },
        update = { scrollView ->
            val textView = scrollView.getTag(TAG_TEXT_VIEW) as TextView
            scrollView.setBackgroundColor(colors.surface.toArgb())
            textView.setBackgroundColor(colors.surface.toArgb())
            textView.setTextColor(colors.onSurface.toArgb())
            textView.setTextSize(TypedValue.COMPLEX_UNIT_SP, baseTextSizeSp)
            textView.movementMethod = linkMovementMethod

            val next = rendered ?: return@AndroidView
            val last = textView.getTag(TAG_LAST_RENDERED) as? Spanned
            if (last === next) return@AndroidView
            textView.setTag(TAG_LAST_RENDERED, next)
            markwon.setParsedMarkdown(textView, next)
        },
    )

    val dialog = previewDialog
    if (dialog != null) {
        AlertDialog(
            onDismissRequest = { previewDialog = null },
            title = { Text(text = dialog.name) },
            text = {
                Text(
                    text = dialog.snippet ?: "未找到预览内容",
                    style = MaterialTheme.typography.bodyMedium,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onOpenWikiLink?.invoke(dialog.name)
                        previewDialog = null
                    },
                ) { Text("打开") }
            },
            dismissButton = {
                TextButton(onClick = { previewDialog = null }) {
                    Text("关闭")
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

private class PreviewLinkResolver(
    private val context: Context,
    private val vaultResolver: VaultPathResolver?,
    private val onOpenWikiLink: ((String) -> Unit)?,
    private val onOpenVaultDocUri: ((Uri) -> Unit)?,
) : LinkResolver {
    override fun resolve(view: View, link: String) {
        val uri = Uri.parse(link)
        val scheme = uri.scheme

        when (scheme) {
            "zhixu" -> {
                when (uri.host) {
                    "doc" -> {
                        val name = uri.lastPathSegment?.trim().orEmpty()
                        if (name.isNotBlank()) onOpenWikiLink?.invoke(Uri.decode(name))
                    }

                    "vault" -> {
                        val relPath = uri.getQueryParameter("path")?.let(Uri::decode).orEmpty()
                        if (relPath.isBlank()) return
                        val resolved = vaultResolver?.resolve(relPath) ?: return
                        if (relPath.endsWith(".md", ignoreCase = true)) {
                            onOpenVaultDocUri?.invoke(resolved)
                        } else {
                            openExternal(resolved)
                        }
                    }
                }

                return
            }

            "http", "https", "mailto", "tel", "file", "content" -> {
                openExternal(uri)
                return
            }

            null -> {
                val rawPath = link.substringBefore('#').trim()
                if (rawPath.isBlank()) return
                val resolved = vaultResolver?.resolve(rawPath)
                if (resolved != null) {
                    if (rawPath.endsWith(".md", ignoreCase = true)) {
                        onOpenVaultDocUri?.invoke(resolved)
                    } else {
                        openExternal(resolved)
                    }
                    return
                }
            }
        }

        openExternal(uri)
    }

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

private class LongPressLinkMovementMethod(
    private val onLongPressLink: (String) -> Unit,
) : LinkMovementMethod() {
    private val handler = Handler(Looper.getMainLooper())
    private var longPressRunnable: Runnable? = null
    private var pressedSpan: ClickableSpan? = null
    private var longPressed: Boolean = false
    private var downX: Float = 0f
    private var downY: Float = 0f

    override fun onTouchEvent(widget: TextView, buffer: Spannable, event: MotionEvent): Boolean {
        val action = event.actionMasked
        when (action) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.x
                downY = event.y
                longPressed = false

                val span = clickableSpanUnderTouch(widget, buffer, event)
                pressedSpan = span
                if (span == null) {
                    Selection.removeSelection(buffer)
                    cancelLongPress()
                    return false
                }

                Selection.setSelection(buffer, buffer.getSpanStart(span), buffer.getSpanEnd(span))
                scheduleLongPress(span)
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                val span = pressedSpan ?: return false
                val slop = ViewConfiguration.get(widget.context).scaledTouchSlop
                val moved = kotlin.math.abs(event.x - downX) > slop || kotlin.math.abs(event.y - downY) > slop
                if (moved || clickableSpanUnderTouch(widget, buffer, event) !== span) {
                    cancelLongPress()
                    Selection.removeSelection(buffer)
                    pressedSpan = null
                    return false
                }
                return true
            }

            MotionEvent.ACTION_UP -> {
                val span = pressedSpan
                cancelLongPress()
                Selection.removeSelection(buffer)
                pressedSpan = null

                if (span == null) return false
                if (!longPressed) span.onClick(widget)
                return true
            }

            MotionEvent.ACTION_CANCEL -> {
                cancelLongPress()
                Selection.removeSelection(buffer)
                pressedSpan = null
                return false
            }
        }
        return super.onTouchEvent(widget, buffer, event)
    }

    private fun scheduleLongPress(span: ClickableSpan) {
        cancelLongPress()
        val timeout = ViewConfiguration.getLongPressTimeout().toLong()
        val runnable =
            Runnable {
                longPressed = true
                val link =
                    when (span) {
                        is io.noties.markwon.core.spans.LinkSpan -> span.link
                        is URLSpan -> span.url
                        else -> null
                    }
                if (!link.isNullOrBlank()) onLongPressLink(link)
            }
        longPressRunnable = runnable
        handler.postDelayed(runnable, timeout)
    }

    private fun cancelLongPress() {
        longPressRunnable?.let(handler::removeCallbacks)
        longPressRunnable = null
    }

    private fun clickableSpanUnderTouch(widget: TextView, buffer: Spannable, event: MotionEvent): ClickableSpan? {
        val layout = widget.layout ?: return null
        val x = (event.x - widget.totalPaddingLeft + widget.scrollX).toInt()
        val y = (event.y - widget.totalPaddingTop + widget.scrollY).toInt()
        val line = layout.getLineForVertical(y)
        val off = layout.getOffsetForHorizontal(line, x.toFloat())
        return buffer.getSpans(off, off, ClickableSpan::class.java).firstOrNull()
    }
}

private class VaultPathResolver(
    private val context: Context,
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

private class VaultAsyncDrawableLoader(
    private val context: Context,
    private val vaultResolver: VaultPathResolver?,
) : AsyncDrawableLoader() {
    private val handler = Handler(Looper.getMainLooper())
    private val executor = Executors.newFixedThreadPool(2)
    private val inflight = ConcurrentHashMap<AsyncDrawable, Future<*>>()
    private val okHttp = OkHttpClient()

    override fun load(drawable: AsyncDrawable) {
        cancel(drawable)

        val future =
            executor.submit {
                var posted = false
                try {
                    val uri = resolveToUri(drawable.destination) ?: return@submit

                    val bitmap =
                        when (uri.scheme) {
                            "http", "https" -> {
                                val request = Request.Builder().url(uri.toString()).build()
                                okHttp.newCall(request).execute().use { response ->
                                    if (!response.isSuccessful) return@submit
                                    val stream = response.body?.byteStream() ?: return@submit
                                    stream.use(BitmapFactory::decodeStream)
                                }
                            }

                            else -> {
                                val stream = context.contentResolver.openInputStream(uri) ?: return@submit
                                stream.use(BitmapFactory::decodeStream)
                            }
                        } ?: return@submit

                    val result = BitmapDrawable(context.resources, bitmap)

                    posted = true
                    handler.post {
                        if (inflight.remove(drawable) != null) drawable.setResult(result)
                    }
                } finally {
                    if (!posted) inflight.remove(drawable)
                }
            }

        inflight[drawable] = future
    }

    override fun cancel(drawable: AsyncDrawable) {
        inflight.remove(drawable)?.cancel(true)
    }

    override fun placeholder(drawable: AsyncDrawable) = null

    fun shutdown() {
        inflight.values.forEach { it.cancel(true) }
        inflight.clear()
        executor.shutdownNow()
    }

    private fun resolveToUri(destination: String): Uri? {
        val trimmed = destination.trim()
        if (trimmed.isBlank()) return null

        val parsed = runCatching { Uri.parse(trimmed) }.getOrNull()

        if (parsed == null || parsed.scheme.isNullOrBlank()) {
            val rawPath = trimmed.substringBefore('#')
            return vaultResolver?.resolve(rawPath)
        }

        return when (parsed.scheme) {
            "content", "file", "http", "https" -> parsed
            "zhixu" -> {
                if (parsed.host != "vault") return null
                val relPath = parsed.getQueryParameter("path")?.let(Uri::decode).orEmpty()
                if (relPath.isBlank()) null else vaultResolver?.resolve(relPath)
            }

            else -> null
        }
    }
}

private const val TAG_TEXT_VIEW: Int = 0x5A48_4D50
private const val TAG_LAST_RENDERED: Int = 0x5A48_4D51
