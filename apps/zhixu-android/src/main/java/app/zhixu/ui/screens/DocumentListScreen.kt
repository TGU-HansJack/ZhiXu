package app.zhixu.ui.screens

import android.content.Intent
import android.net.Uri
import android.os.SystemClock
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
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
import androidx.compose.material3.TextFieldDefaults
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
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
import app.zhixu.ui.components.RefreshStatusBanner
import app.zhixu.ui.components.SheetActionRow
import app.zhixu.ui.components.SheetQuickAction
import app.zhixu.ui.components.ZhixuIconButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

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
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val highlightBg = MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
    val listState = rememberLazyListState()
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
                                DocRow(
                                    title = title,
                                    editedAt = editedAt,
                                    onClick = { onOpenDoc(doc.uri.toString(), null, null) },
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
        DocumentSearchSheet(
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
            shape = RoundedCornerShape(16.dp),
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DocumentSearchSheet(
    query: String,
    onQueryChange: (String) -> Unit,
    results: List<SearchResult>,
    highlightBg: Color,
    onDismiss: () -> Unit,
    onOpenResult: (String, Int?) -> Unit,
) {
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    val visibleState = remember { MutableTransitionState(false).apply { targetState = true } }

    DisposableEffect(Unit) {
        onDispose { keyboardController?.hide() }
    }

    LaunchedEffect(Unit) {
        delay(80)
        focusRequester.requestFocus()
        keyboardController?.show()
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        AnimatedVisibility(
            visibleState = visibleState,
            enter =
                fadeIn(animationSpec = tween(durationMillis = 120)) +
                    slideInVertically(animationSpec = tween(durationMillis = 120)) { fullHeight -> fullHeight / 8 },
            exit =
                fadeOut(animationSpec = tween(durationMillis = 90)) +
                    slideOutVertically(animationSpec = tween(durationMillis = 90)) { fullHeight -> fullHeight / 8 },
        ) {
            Surface(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background)
                        .windowInsetsPadding(WindowInsets.navigationBars),
                color = MaterialTheme.colorScheme.background,
                tonalElevation = 0.dp,
                shadowElevation = 0.dp,
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        ZhixuTextField(
                            value = query,
                            onValueChange = onQueryChange,
                            singleLine = true,
                            placeholder = { Text(stringResource(R.string.action_search)) },
                            leadingIcon = { Icon(painter = painterResource(app.zhixu.ui.Ionicons.Search), contentDescription = null) },
                            modifier = Modifier.weight(1f).focusRequester(focusRequester),
                            shape = MaterialTheme.shapes.extraLarge,
                            colors =
                                TextFieldDefaults.colors(
                                    focusedIndicatorColor = Color.Transparent,
                                    unfocusedIndicatorColor = Color.Transparent,
                                    disabledIndicatorColor = Color.Transparent,
                                    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                                ),
                        )
                        TextButton(onClick = onDismiss) {
                            Text(stringResource(R.string.action_cancel))
                        }
                    }

                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 24.dp),
                    ) {
                        if (query.isBlank()) {
                            item {
                                Text(
                                    text = "",
                            modifier = Modifier.padding(16.dp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else if (results.isEmpty()) {
                    item {
                        Text(
                            text = stringResource(R.string.search_empty),
                            modifier = Modifier.padding(16.dp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else {
                    itemsIndexed(
                        results,
                        key = { index, result ->
                            when (result) {
                                is DocSearchResult -> "doc:${result.uri}#$index"
                                is TaskSearchResult -> "task:${result.docUri}:${result.lineIndex}:${result.taskId}#$index"
                            }
                        },
                    ) { _, result ->
                        when (result) {
                            is DocSearchResult -> {
                                ListItem(
                                    modifier = Modifier.clickable { onOpenResult(result.uri.toString(), null) },
                                    headlineContent = {
                                        Text(
                                            text = highlightQuery(result.title, query, highlightBg),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    },
                                    supportingContent = {
                                        if (!result.snippet.isNullOrBlank()) {
                                            Text(
                                                highlightQuery(result.snippet, query, highlightBg),
                                                maxLines = 2,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                        }
                                    },
                                )
                            }

                            is TaskSearchResult -> {
                                ListItem(
                                    modifier = Modifier.clickable { onOpenResult(result.docUri.toString(), result.lineIndex) },
                                    headlineContent = {
                                        Text(
                                            text = highlightQuery(result.title, query, highlightBg),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    },
                                    supportingContent = { Text(stringResource(R.string.search_task_hit)) },
                                )
                            }
                        }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun highlightQuery(text: String, query: String, highlightBg: Color): AnnotatedString {
    val q = query.trim()
    if (q.isBlank()) return AnnotatedString(text)
    val tokens = q.split(Regex("""\s+""")).filter { it.isNotBlank() }.distinct()
    if (tokens.isEmpty()) return AnnotatedString(text)

    val lowered = text.lowercase()
    val ranges = ArrayList<IntRange>()
    for (token in tokens) {
        val t = token.lowercase()
        if (t.length < 2) continue
        var idx = lowered.indexOf(t)
        while (idx >= 0) {
            ranges += (idx until (idx + t.length))
            idx = lowered.indexOf(t, startIndex = idx + t.length)
        }
    }
    if (ranges.isEmpty()) return AnnotatedString(text)

    val merged = ranges.sortedBy { it.first }.fold(mutableListOf<IntRange>()) { acc, r ->
        val last = acc.lastOrNull()
        if (last == null) acc.add(r)
        else if (r.first <= last.last + 1) acc[acc.lastIndex] = (last.first..maxOf(last.last, r.last))
        else acc.add(r)
        acc
    }

    val highlight = SpanStyle(background = highlightBg, fontWeight = FontWeight.SemiBold)
    return buildAnnotatedString {
        var pos = 0
        for (r in merged) {
            if (pos < r.first) append(text.substring(pos, r.first))
            val end = (r.last + 1).coerceAtMost(text.length)
            pushStyle(highlight)
            append(text.substring(r.first, end))
            pop()
            pos = end
        }
        if (pos < text.length) append(text.substring(pos))
    }
}

@Composable
private fun DocRow(
    title: String,
    editedAt: String,
    onClick: () -> Unit,
    onMoreClick: () -> Unit,
    showDivider: Boolean,
    dividerColor: Color,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
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
                .clickable(onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(Ionicons.DocumentText),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.size(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = stringResource(R.string.edited_at_fmt, editedAt),
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        ZhixuIconButton(onClick = onMoreClick) {
            Icon(
                painter = painterResource(Ionicons.EllipsisHorizontal),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
