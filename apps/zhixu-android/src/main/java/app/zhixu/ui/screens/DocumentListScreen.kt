package app.zhixu.ui.screens

import android.content.Intent
import android.graphics.Typeface
import android.net.Uri
import android.os.SystemClock
import android.util.TypedValue
import android.view.ViewGroup
import android.widget.Toast
import android.widget.TextView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Surface
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import app.zhixu.ui.components.ZhixuTextField
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.zhixu.R
import app.zhixu.data.DocSearchResult
import app.zhixu.data.DocumentIndex
import app.zhixu.data.SearchResult
import app.zhixu.data.TaskSearchResult
import app.zhixu.data.UiDoc
import app.zhixu.data.VaultIndexUpdater
import app.zhixu.data.VaultRepository
import app.zhixu.ui.Ionicons
import app.zhixu.ui.DocListMutation
import app.zhixu.ui.components.RefreshStatusBanner
import app.zhixu.ui.components.SheetActionRow
import app.zhixu.ui.components.SheetQuickAction
import app.zhixu.ui.components.ZhixuIconButton
import app.zhixu.ui.components.VaultSearchDialog
import io.noties.markwon.AbstractMarkwonPlugin
import io.noties.markwon.Markwon
import io.noties.markwon.core.MarkwonTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import androidx.compose.ui.input.pointer.pointerInteropFilter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DocumentListScreen(
    contentPadding: PaddingValues,
    vaultRootUri: Uri?,
    repository: VaultRepository,
    documentIndex: DocumentIndex,
    indexUpdater: VaultIndexUpdater,
    isActive: Boolean,
    searchRequestToken: Long,
    onOpenDoc: (String, String?, Int?) -> Unit,
    onNewDoc: () -> Unit,
    onChangeVault: () -> Unit,
    onDocListMutated: (DocListMutation) -> Unit = {},
    selectedDocUris: Set<String>,
    onToggleDocSelection: (String) -> Unit,
    onClearDocSelection: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val highlightBg = MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
    val listState = rememberLazyListState()
    val markwon =
        remember(context) {
            Markwon
                .builder(context)
                .usePlugin(
                    object : AbstractMarkwonPlugin() {
                        override fun configureTheme(builder: MarkwonTheme.Builder) {
                            builder.headingTextSizeMultipliers(floatArrayOf(1f, 1f, 1f, 1f, 1f, 1f))
                        }
                    },
                )
                .build()
        }
    var docs by remember(vaultRootUri) { mutableStateOf<List<UiDoc>>(emptyList()) }
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<SearchResult>>(emptyList()) }
    var searchJob by remember { mutableStateOf<Job?>(null) }
    var lastRefreshBannerAtMs by remember { mutableStateOf(0L) }
    var showSearchSheet by remember { mutableStateOf(false) }
    var pendingOpenSearch by remember { mutableStateOf(false) }
    var lastSearchToken by remember { mutableLongStateOf(searchRequestToken) }
    var pendingShowUpdatedBanner by remember { mutableStateOf(false) }

    var selectedDoc by remember { mutableStateOf<UiDoc?>(null) }
    var showDocMenu by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var renameInput by remember { mutableStateOf("") }
    var showDeleteDialog by remember { mutableStateOf(false) }

    val isIndexUpdating by indexUpdater.isUpdating.collectAsState()

    val previewCache = remember(vaultRootUri) { mutableStateMapOf<String, String>() }

    LaunchedEffect(searchRequestToken) {
        if (searchRequestToken == lastSearchToken) return@LaunchedEffect
        lastSearchToken = searchRequestToken
        pendingOpenSearch = true
    }

    LaunchedEffect(isActive, pendingOpenSearch) {
        if (!isActive || !pendingOpenSearch) return@LaunchedEffect
        pendingOpenSearch = false
        showSearchSheet = true
    }


    suspend fun reloadFromIndex() {
        val root = vaultRootUri ?: return
        docs = withContext(Dispatchers.IO) { documentIndex.listDocs(root) }
    }

    LaunchedEffect(vaultRootUri, isActive) {
        if (!isActive) return@LaunchedEffect
        if (vaultRootUri == null) {
            docs = emptyList()
            return@LaunchedEffect
        }
        // Let the first frame render (shell) before kicking off SQLite reads.
        withFrameNanos { }
        reloadFromIndex()
    }

    LaunchedEffect(vaultRootUri, isActive) {
        if (!isActive) return@LaunchedEffect
        if (vaultRootUri == null) return@LaunchedEffect
        documentIndex.changes.collect {
            reloadFromIndex()
            if (pendingShowUpdatedBanner) {
                lastRefreshBannerAtMs = SystemClock.uptimeMillis()
                pendingShowUpdatedBanner = false
            }
        }
    }

    fun clearSearch() {
        query = ""
        results = emptyList()
        searchJob?.cancel()
        searchJob = null
    }

    fun updateQuery(newQuery: String) {
        query = newQuery
        searchJob?.cancel()
        searchJob =
            scope.launch {
                delay(200)
                if (query.isBlank()) {
                    results = emptyList()
                    return@launch
                }
                results =
                    withContext(Dispatchers.IO) {
                        documentIndex.search(query)
                    }
            }
    }

    Box(
        modifier =
            Modifier
                .padding(contentPadding)
                .background(MaterialTheme.colorScheme.background)
                .fillMaxSize(),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            if (vaultRootUri == null) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(stringResource(R.string.settings_vault_not_selected))
                    TextButton(onClick = onChangeVault) { Text(stringResource(R.string.vault_select_button)) }
                }
            } else {
                val selectionMode = selectedDocUris.isNotEmpty()
                val defaultTitle = stringResource(R.string.new_doc_default_title)
                val editedAtDash = "—"
                val dividerColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                val pullState = rememberPullToRefreshState()
            PullToRefreshBox(
                isRefreshing = isActive && isIndexUpdating,
                onRefresh = {
                    if (!isActive) return@PullToRefreshBox
                    pendingShowUpdatedBanner = true
                    indexUpdater.requestForceRefresh()
                },
                state = pullState,
                modifier = Modifier.fillMaxSize(),
                indicator = {},
            ) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            state = listState,
                            contentPadding = PaddingValues(bottom = 12.dp),
                        ) {
                            if (docs.isEmpty()) {
                                item {
                                    Text(
                                        text = stringResource(R.string.docs_empty),
                                        modifier = Modifier.padding(16.dp),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                            itemsIndexed(
                                items = docs,
                                key = { _, doc -> doc.uri },
                                contentType = { _, _ -> "doc" },
                            ) { index, doc ->
                                val title = doc.baseName.ifBlank { defaultTitle }
                                val editedAt = doc.createdAtText.ifBlank { editedAtDash }
                                val docUriStr = doc.uri.toString()
                                val previewKey = remember(docUriStr, doc.lastModified) { "$docUriStr@${doc.lastModified}" }
                                val previewMarkdown = previewCache[previewKey]

                                LaunchedEffect(previewKey) {
                                    if (previewCache.containsKey(previewKey)) return@LaunchedEffect
                                    previewCache[previewKey] = ""
                                    val raw = repository.readTextPreview(doc.uri, maxChars = 2500)
                                    previewCache[previewKey] = extractDocPreviewMarkdown(raw, maxChars = 300)
                                }

                                DocRow(
                                    title = title,
                                    editedAt = editedAt,
                                    previewMarkdown = previewMarkdown?.takeIf { it.isNotBlank() },
                                    markwon = markwon,
                                    selectionMode = selectionMode,
                                    selected = docUriStr in selectedDocUris,
                                    onClick = {
                                        if (selectionMode) onToggleDocSelection(docUriStr) else onOpenDoc(docUriStr, null, null)
                                    },
                                    onLongClick = { onToggleDocSelection(docUriStr) },
                                    onMoreClick = {
                                        selectedDoc = doc
                                        showDocMenu = true
                                    },
                                    showDivider = index != docs.lastIndex,
                                    dividerColor = dividerColor,
                                )
                            }
                        }

                        RefreshStatusBanner(
                            isRefreshing = isIndexUpdating,
                            lastRefreshedAtMs = lastRefreshBannerAtMs,
                            modifier = Modifier.align(Alignment.TopCenter).padding(top = 10.dp),
                        )
                    }
                }
            }
        }
    }

    val docMenuSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    if (showDocMenu && selectedDoc != null) {
        ModalBottomSheet(
            onDismissRequest = { showDocMenu = false },
            sheetState = docMenuSheetState,
            containerColor = MaterialTheme.colorScheme.background,
            tonalElevation = 0.dp,
        ) {
            DocumentRowActionsSheet(
                onRename = {
                    val doc = selectedDoc ?: return@DocumentRowActionsSheet
                    renameInput = doc.baseName.ifBlank { doc.name.removeSuffix(".md") }
                    showDocMenu = false
                    showRenameDialog = true
                },
                onMove = {
                    showDocMenu = false
                    Toast.makeText(context, context.getString(R.string.common_not_implemented), Toast.LENGTH_SHORT).show()
                },
                onCopy = {
                    val doc = selectedDoc ?: return@DocumentRowActionsSheet
                    showDocMenu = false
                    scope.launch {
                        val text = withContext(Dispatchers.IO) { repository.readText(doc.uri) }
                        clipboard.setText(AnnotatedString(text))
                        Toast.makeText(context, context.getString(R.string.common_copied), Toast.LENGTH_SHORT).show()
                    }
                },
                onShare = {
                    val doc = selectedDoc ?: return@DocumentRowActionsSheet
                    showDocMenu = false
                    scope.launch {
                        val text = withContext(Dispatchers.IO) { repository.readText(doc.uri) }
                        val subject = doc.baseName.ifBlank { doc.name.removeSuffix(".md").ifBlank { "Zhixu" } }
                        val intent =
                            Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_SUBJECT, subject)
                                putExtra(Intent.EXTRA_TEXT, text)
                            }
                        runCatching {
                            context.startActivity(Intent.createChooser(intent, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                        }.onFailure {
                            Toast.makeText(context, context.getString(R.string.editor_share_failed), Toast.LENGTH_SHORT).show()
                        }
                    }
                },
                onAddToDesktop = {
                    showDocMenu = false
                    Toast.makeText(context, context.getString(R.string.common_not_implemented), Toast.LENGTH_SHORT).show()
                },
                onDelete = {
                    showDocMenu = false
                    showDeleteDialog = true
                },
            )
        }
    }

    if (showRenameDialog && selectedDoc != null) {
        AlertDialog(
            onDismissRequest = {
                showRenameDialog = false
                selectedDoc = null
            },
            title = { Text(stringResource(R.string.action_rename)) },
            text = {
                ZhixuTextField(
                    value = renameInput,
                    onValueChange = { renameInput = it },
                    singleLine = true,
                    placeholder = { Text(stringResource(R.string.field_file_name)) },
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val doc = selectedDoc ?: return@TextButton
                        val desiredName = renameInput.trim()
                        if (desiredName.isBlank()) return@TextButton
                        showRenameDialog = false
                        selectedDoc = null
                        scope.launch {
                            val renamed =
                                withContext(Dispatchers.IO) {
                                    repository.renameDoc(doc.uri, desiredName)
                                }
                            if (renamed == null) {
                                Toast.makeText(context, context.getString(R.string.editor_rename_failed_generic), Toast.LENGTH_SHORT).show()
                            } else {
                                onDocListMutated(DocListMutation.Renamed(oldUri = doc.uri, newUri = renamed))
                            }
                        }
                    },
                ) { Text(stringResource(R.string.action_rename)) }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showRenameDialog = false
                        selectedDoc = null
                    },
                ) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }

    if (showDeleteDialog && selectedDoc != null) {
        AlertDialog(
            onDismissRequest = {
                showDeleteDialog = false
                selectedDoc = null
            },
            title = { Text(stringResource(R.string.dialog_delete_doc_title)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        val doc = selectedDoc ?: return@TextButton
                        scope.launch {
                            val ok =
                                withContext(Dispatchers.IO) {
                                    repository.deleteDoc(doc.uri)
                                }
                            if (!ok) {
                                Toast.makeText(context, context.getString(R.string.doc_delete_failed), Toast.LENGTH_SHORT).show()
                            } else {
                                onDocListMutated(DocListMutation.Deleted(docUri = doc.uri))
                            }
                            showDeleteDialog = false
                            selectedDoc = null
                        }
                    },
                ) { Text(stringResource(R.string.action_delete)) }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showDeleteDialog = false
                        selectedDoc = null
                    },
                ) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }

    if (showSearchSheet) {
        VaultSearchDialog(
            query = query,
            onQueryChange = ::updateQuery,
            results = results,
            highlightBg = highlightBg,
            onDismiss = {
                showSearchSheet = false
                clearSearch()
            },
            onOpenResult = { uriStr, lineIndex ->
                showSearchSheet = false
                onOpenDoc(uriStr, query, lineIndex)
                clearSearch()
            },
        )
    }
}

@Composable
private fun DocumentRowActionsSheet(
    onRename: () -> Unit,
    onMove: () -> Unit,
    onCopy: () -> Unit,
    onShare: () -> Unit,
    onAddToDesktop: () -> Unit,
    onDelete: () -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .windowInsetsPadding(WindowInsets.navigationBars),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SheetQuickAction(
                title = stringResource(R.string.action_rename),
                iconRes = Ionicons.TextOutline,
                onClick = onRename,
                modifier = Modifier.weight(1f),
            )
            SheetQuickAction(
                title = stringResource(R.string.action_move),
                iconRes = Ionicons.ArrowForward,
                onClick = onMove,
                modifier = Modifier.weight(1f),
            )
            SheetQuickAction(
                title = stringResource(R.string.common_copy),
                iconRes = Ionicons.CopyOutline,
                onClick = onCopy,
                modifier = Modifier.weight(1f),
            )
            SheetQuickAction(
                title = stringResource(R.string.editor_overflow_share),
                iconRes = Ionicons.ShareSocialOutline,
                onClick = onShare,
                modifier = Modifier.weight(1f),
            )
        }

        Spacer(modifier = Modifier.size(14.dp))

        Surface(
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
            tonalElevation = 0.dp,
            shadowElevation = 0.dp,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column {
                SheetActionRow(
                    title = stringResource(R.string.action_add_to_desktop),
                    iconRes = Ionicons.ArrowUpCircleOutline,
                    onClick = onAddToDesktop,
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f))
                SheetActionRow(
                    title = stringResource(R.string.action_delete),
                    iconRes = Ionicons.TrashOutline,
                    iconTint = MaterialTheme.colorScheme.error,
                    onClick = onDelete,
                )
            }
        }

        Spacer(modifier = Modifier.size(8.dp))
    }
}

@Composable
private fun DocRow(
    title: String,
    editedAt: String,
    previewMarkdown: String?,
    markwon: Markwon,
    selectionMode: Boolean,
    selected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onMoreClick: () -> Unit,
    showDivider: Boolean,
    dividerColor: Color,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.08f) else Color.Transparent)
                .drawBehind {
                    if (!showDivider) return@drawBehind
                    val strokeWidth = 0.5.dp.toPx()
                    val y = size.height - (strokeWidth / 2f)
                    drawLine(
                        color = dividerColor,
                        start = Offset(0f, y),
                        end = Offset(size.width, y),
                        strokeWidth = strokeWidth,
                    )
                }
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = onLongClick,
                )
                .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = title,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                    modifier = Modifier.weight(1f),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.edited_at_fmt, editedAt),
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Light),
                    textAlign = TextAlign.End,
                    modifier = Modifier.widthIn(max = 140.dp),
                )
                Spacer(modifier = Modifier.width(4.dp))

                ZhixuIconButton(
                    onClick = if (selectionMode) onClick else onMoreClick,
                    modifier = Modifier.size(24.dp),
                ) {
                    Icon(
                        painter =
                            painterResource(
                                if (selectionMode) {
                                    if (selected) Ionicons.CheckmarkCircle else Ionicons.SquareOutline
                                } else {
                                    Ionicons.EllipsisHorizontal
                                },
                            ),
                        contentDescription = null,
                        tint = if (selectionMode && selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }

            if (!previewMarkdown.isNullOrBlank()) {
                MarkwonPreviewText(
                    markwon = markwon,
                    markdown = previewMarkdown,
                    textStyle = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Light),
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                    maxLines = Int.MAX_VALUE,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .wrapContentHeight()
                            .padding(top = 8.dp)
                            .pointerInteropFilter { false },
                )
            }
        }
    }
}

private fun extractDocPreviewMarkdown(
    raw: String,
    maxChars: Int,
): String {
    if (maxChars <= 0) return ""
    if (raw.isBlank()) return ""

    val normalized = raw.replace("\r\n", "\n").replace('\r', '\n')
    val withoutFrontmatter =
        if (normalized.startsWith("---\n")) {
            val end = normalized.indexOf("\n---\n", startIndex = 4)
            if (end in 4..2000) normalized.substring(end + "\n---\n".length) else normalized
        } else {
            normalized
        }

    val lines = withoutFrontmatter.lineSequence().dropWhile { it.isBlank() }.toList()
    if (lines.isEmpty()) return ""
    val candidate =
        lines
            .take(12)
            .joinToString("\n")
            .trim()
            .replace(Regex("\n{3,}"), "\n\n")

    return if (candidate.length <= maxChars) candidate else candidate.take(maxChars).trimEnd() + "…"
}

@Composable
private fun MarkwonPreviewText(
    markwon: Markwon,
    markdown: String,
    textStyle: androidx.compose.ui.text.TextStyle,
    color: Color,
    maxLines: Int,
    modifier: Modifier = Modifier,
) {
    val fontSize = textStyle.fontSize
    val fontSizeSp = if (fontSize == TextUnit.Unspecified) 14f else fontSize.value
    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            TextView(ctx).apply {
                layoutParams =
                    ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                    )
                isClickable = false
                isLongClickable = false
                setTextIsSelectable(false)
                includeFontPadding = false
                setHorizontallyScrolling(false)
                setTextColor(color.toArgb())
                setTextSize(TypedValue.COMPLEX_UNIT_SP, fontSizeSp)
                typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
                ellipsize = null
                this.maxLines = maxLines
            }
        },
        update = { view ->
            view.layoutParams =
                ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                )
            view.setTextColor(color.toArgb())
            view.setTextSize(TypedValue.COMPLEX_UNIT_SP, fontSizeSp)
            view.typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
            view.setHorizontallyScrolling(false)
            view.ellipsize = null
            view.maxLines = maxLines
            markwon.setMarkdown(view, markdown)
        },
    )
}
