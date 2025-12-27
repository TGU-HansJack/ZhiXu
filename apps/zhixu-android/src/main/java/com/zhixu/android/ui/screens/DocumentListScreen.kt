package com.zhixu.android.ui.screens

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Surface
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import com.zhixu.android.R
import com.zhixu.android.data.DocSearchResult
import com.zhixu.android.data.SearchResult
import com.zhixu.android.data.TaskSearchResult
import com.zhixu.android.data.UiDoc
import com.zhixu.android.data.VaultRepository
import com.zhixu.android.ui.components.CapsuleActionBar
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DocumentListScreen(
    contentPadding: PaddingValues,
    vaultRootUri: Uri?,
    repository: VaultRepository,
    onOpenDoc: (String, String?, Int?) -> Unit,
    onNewDoc: () -> Unit,
    onChangeVault: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val highlightBg = MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
    val lifecycleOwner = LocalLifecycleOwner.current
    var docs by remember { mutableStateOf<List<UiDoc>>(emptyList()) }
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<SearchResult>>(emptyList()) }
    var searchJob by remember { mutableStateOf<Job?>(null) }
    var showAiDialog by remember { mutableStateOf(false) }
    var showSearchSheet by remember { mutableStateOf(false) }

    suspend fun refresh(reindex: Boolean) {
        val root = vaultRootUri ?: return
        repository.ensureVaultStructure(root)
        docs = repository.listMarkdownDocs(root)
        if (reindex) {
            runCatching { repository.rebuildIndex(root) }
        }
    }

    LaunchedEffect(vaultRootUri) {
        refresh(reindex = false)
    }

    DisposableEffect(lifecycleOwner, vaultRootUri) {
        val observer =
            LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_RESUME) {
                    scope.launch { refresh(reindex = false) }
                }
            }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    if (showAiDialog) {
        AlertDialog(
            onDismissRequest = { showAiDialog = false },
            title = { Text("AI") },
            text = { Text("AI 对话（占位）") },
            confirmButton = { TextButton(onClick = { showAiDialog = false }) { Text(stringResource(R.string.action_cancel)) } },
        )
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
                if (query.isBlank() || vaultRootUri == null) {
                    results = emptyList()
                    return@launch
                }
                results = repository.search(vaultRootUri, query)
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
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 96.dp),
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
                items(docs, key = { it.uri.toString() }) { doc ->
                    val title = doc.name.removeSuffix(".md").ifBlank { stringResource(R.string.new_doc_default_title) }
                    ListItem(
                        modifier = Modifier.clickable { onOpenDoc(doc.uri.toString(), null, null) },
                        leadingContent = {
                            Surface(
                                modifier = Modifier.size(40.dp),
                                shape = MaterialTheme.shapes.small,
                                color = MaterialTheme.colorScheme.surfaceVariant,
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Outlined.Description,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        },
                        headlineContent = {
                            Text(
                                text = title,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.titleLarge,
                            )
                        },
                        supportingContent = {
                            Text(
                                text = stringResource(R.string.edited_at_fmt, formatEditedAt(doc.lastModified)),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        },
                        trailingContent = {
                            IconButton(onClick = { /* UI only */ }) {
                                Icon(
                                    imageVector = Icons.Outlined.MoreHoriz,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        },
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                }
                }
            }
        }

        CapsuleActionBar(
            modifier =
                Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 18.dp)
                    .windowInsetsPadding(WindowInsets.navigationBars),
            onSearch = { showSearchSheet = true },
            onAdd = { if (vaultRootUri == null) onChangeVault() else onNewDoc() },
            onAi = { showAiDialog = true },
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
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        dragHandle = null,
    ) {
        Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextField(
                    value = query,
                    onValueChange = onQueryChange,
                    singleLine = true,
                    placeholder = { Text(stringResource(R.string.action_search)) },
                    leadingIcon = { Icon(imageVector = Icons.Outlined.Search, contentDescription = null) },
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

private val editedAtFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm")

private fun formatEditedAt(lastModifiedMs: Long): String {
    val instant = Instant.ofEpochMilli(lastModifiedMs)
    val local = instant.atZone(ZoneId.systemDefault()).toLocalDateTime()
    return editedAtFormatter.format(local)
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
