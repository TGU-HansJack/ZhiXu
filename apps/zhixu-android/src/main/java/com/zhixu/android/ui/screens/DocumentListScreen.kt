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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material.icons.outlined.DocumentScanner
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.SmartToy
import androidx.compose.material3.AlertDialog
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import android.widget.Toast
import com.zhixu.android.R
import com.zhixu.android.data.DocSearchResult
import com.zhixu.android.data.SearchResult
import com.zhixu.android.data.TaskSearchResult
import com.zhixu.android.data.UiDoc
import com.zhixu.android.data.VaultRepository
import com.zhixu.android.ui.components.DraggableRadialFab
import com.zhixu.android.ui.components.RadialFabAction
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay

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
    var pendingDelete by remember { mutableStateOf<UiDoc?>(null) }
    var isSearching by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<SearchResult>>(emptyList()) }
    var searchJob by remember { mutableStateOf<Job?>(null) }
    var showAiDialog by remember { mutableStateOf(false) }
    val fabActions =
        remember {
            listOf(
                RadialFabAction("record", "录音（占位）", Icons.Outlined.Mic, ringIndex = 0, angleDegrees = -90f),
                RadialFabAction("clip", "剪存（占位）", Icons.Outlined.ContentPaste, ringIndex = 0, angleDegrees = -30f),
                RadialFabAction("ai", "AI（占位）", Icons.Outlined.SmartToy, ringIndex = 0, angleDegrees = -150f),
                RadialFabAction("photo", "拍照（占位）", Icons.Outlined.PhotoCamera, ringIndex = 1, angleDegrees = 0f),
                RadialFabAction("ocr", "OCR（占位）", Icons.Outlined.DocumentScanner, ringIndex = 1, angleDegrees = 180f),
            )
        }

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

    val context = LocalContext.current

    if (showAiDialog) {
        AlertDialog(
            onDismissRequest = { showAiDialog = false },
            title = { Text("AI") },
            text = { Text("AI 对话（占位）") },
            confirmButton = { TextButton(onClick = { showAiDialog = false }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }

    Box(
        modifier =
            Modifier
                .padding(contentPadding)
                .background(MaterialTheme.colorScheme.background)
                .fillMaxSize(),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (isSearching) {
                    OutlinedTextField(
                        value = query,
                        onValueChange = {
                            query = it
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
                        },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        label = { Text(stringResource(R.string.action_search)) },
                    )
                } else {
                    Text(text = "", modifier = Modifier.weight(1f))
                }

                IconButton(
                    onClick = {
                        isSearching = !isSearching
                        if (!isSearching) {
                            query = ""
                            results = emptyList()
                        }
                    },
                ) {
                    Icon(imageVector = Icons.Outlined.Search, contentDescription = stringResource(R.string.action_search))
                }
                IconButton(onClick = { scope.launch { refresh(reindex = true) } }) {
                    Icon(imageVector = Icons.Outlined.Refresh, contentDescription = stringResource(R.string.action_refresh))
                }
            }

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
                ) {
                if (isSearching && query.isNotBlank()) {
                    if (results.isEmpty()) {
                        item {
                            Text(
                                text = stringResource(R.string.search_empty),
                                modifier = Modifier.padding(16.dp),
                            )
                        }
                    }
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
                                    modifier = Modifier.clickable { onOpenDoc(result.uri.toString(), query, null) },
                                    headlineContent = {
                                        Text(
                                            text = highlightQuery(result.title, query, highlightBg),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    },
                                    supportingContent = {
                                        if (!result.snippet.isNullOrBlank()) {
                                            Text(highlightQuery(result.snippet, query, highlightBg), maxLines = 2, overflow = TextOverflow.Ellipsis)
                                        }
                                    },
                                )
                            }

                            is TaskSearchResult -> {
                                ListItem(
                                    modifier = Modifier.clickable { onOpenDoc(result.docUri.toString(), query, result.lineIndex) },
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
                } else if (docs.isEmpty()) {
                    item {
                        Text(
                            text = stringResource(R.string.docs_empty),
                            modifier = Modifier.padding(16.dp),
                        )
                    }
                }
                items(docs, key = { it.uri.toString() }) { doc ->
                    ListItem(
                        modifier = Modifier.clickable { onOpenDoc(doc.uri.toString(), null, null) },
                        headlineContent = {
                            Text(text = doc.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        },
                        supportingContent = { Text(text = "${doc.size} B") },
                        trailingContent = {
                            IconButton(onClick = { pendingDelete = doc }) {
                                Icon(imageVector = Icons.Outlined.Delete, contentDescription = stringResource(R.string.action_delete))
                            }
                        },
                    )
                }
                }
            }
        }

        DraggableRadialFab(
            modifier = Modifier.fillMaxSize(),
            primaryLabel = "Z",
            onClickPrimary = if (vaultRootUri == null) onChangeVault else onNewDoc,
            actions = fabActions,
            onClickAction = { action ->
                when (action.id) {
                    "ai" -> showAiDialog = true
                    else -> {
                        if (vaultRootUri == null) {
                            Toast.makeText(context, context.getString(R.string.settings_vault_not_selected), Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(context, action.label, Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            },
        )
    }

    if (pendingDelete != null) {
        val doc = pendingDelete!!
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text(stringResource(R.string.dialog_delete_doc_title)) },
            text = { Text(doc.name) },
            confirmButton = {
                androidx.compose.material3.TextButton(
                    onClick = {
                        scope.launch {
                            repository.deleteDoc(doc.uri)
                            pendingDelete = null
                            refresh(reindex = false)
                        }
                    },
                ) { Text(stringResource(R.string.action_delete)) }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { pendingDelete = null }) { Text(stringResource(R.string.action_cancel)) }
            },
        )
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
