package com.zhixu.android.ui.screens

import android.database.ContentObserver
import android.net.Uri
import android.os.SystemClock
import android.os.Handler
import android.os.Looper
import android.provider.DocumentsContract
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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Surface
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.zhixu.android.R
import com.zhixu.android.data.DocSearchResult
import com.zhixu.android.data.SearchResult
import com.zhixu.android.data.TaskSearchResult
import com.zhixu.android.data.UiDoc
import com.zhixu.android.data.VaultPreferences
import com.zhixu.android.data.VaultRepository
import com.zhixu.android.ui.components.CapsuleActionBar
import com.zhixu.android.ui.components.CapsuleActionBarDefaults
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DocumentListScreen(
    contentPadding: PaddingValues,
    vaultRootUri: Uri?,
    prefs: VaultPreferences,
    repository: VaultRepository,
    isActive: Boolean,
    refreshToken: Long,
    onOpenDoc: (String, String?, Int?) -> Unit,
    onNewDoc: () -> Unit,
    onChangeVault: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val highlightBg = MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
    val lifecycleOwner = LocalLifecycleOwner.current
    val listState = rememberLazyListState()
    val initialCacheEntry = remember(vaultRootUri) { DocumentListCache.get(vaultRootUri) }
    var docs by remember(vaultRootUri) { mutableStateOf(initialCacheEntry?.docs ?: emptyList()) }
    var docsSig by remember(vaultRootUri) { mutableLongStateOf(0L) }
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<SearchResult>>(emptyList()) }
    var searchJob by remember { mutableStateOf<Job?>(null) }
    var refreshJob by remember { mutableStateOf<Job?>(null) }
    var scheduleJob by remember { mutableStateOf<Job?>(null) }
    var lastRefreshAtMs by remember { mutableStateOf(0L) }
    var cacheUpdatedAtMs by remember(vaultRootUri) { mutableLongStateOf(initialCacheEntry?.updatedAtMs ?: 0L) }
    var ensuredVaultThisSession by remember(vaultRootUri) { mutableStateOf(false) }
    var showAiDialog by remember { mutableStateOf(false) }
    var showSearchSheet by remember { mutableStateOf(false) }
    var pendingRefresh by remember(vaultRootUri) { mutableStateOf<PendingRefresh?>(null) }
    var docsDirUri by remember(vaultRootUri) { mutableStateOf<Uri?>(null) }
    var skipNextResumeRefresh by remember(vaultRootUri) { mutableStateOf(true) }

    val latestVaultRootUri by rememberUpdatedState(vaultRootUri)
    val latestIsActive by rememberUpdatedState(isActive)

    fun requestRefresh(force: Boolean, reindex: Boolean) {
        if (!isActive) return
        val root = vaultRootUri ?: return
        if (listState.isScrollInProgress || showSearchSheet) {
            pendingRefresh = PendingRefresh(force = force, reindex = reindex)
            return
        }
        val effectiveForce = force || docs.isEmpty()
        if (!effectiveForce) {
            val now = SystemClock.uptimeMillis()
            val elapsed = now - lastRefreshAtMs
            if (elapsed in 0..1_500) return

            // If we have a fairly fresh cache, avoid refreshing on app resume.
            val cacheAgeMs = if (cacheUpdatedAtMs > 0L) (System.currentTimeMillis() - cacheUpdatedAtMs) else Long.MAX_VALUE
            if (cacheUpdatedAtMs > 0L && cacheAgeMs in 0..(10 * 60 * 1000L)) return
        }
        refreshJob?.cancel()
        val needEnsure = !ensuredVaultThisSession && docs.isEmpty() && cacheUpdatedAtMs == 0L
        refreshJob =
            scope.launch {
                val now = SystemClock.uptimeMillis()
                val elapsed = now - lastRefreshAtMs
                if (elapsed in 0..250) delay(250 - elapsed)

                val (nextDocs, nextSig, didEnsure) =
                    withContext(Dispatchers.IO) {
                        if (needEnsure) runCatching { repository.ensureVaultStructure(root) }
                        val listed = repository.listMarkdownDocsCached(root, force = effectiveForce)
                        Triple(listed, docListSignature(listed), needEnsure)
                    }
                if (didEnsure) ensuredVaultThisSession = true
                docsDirUri = runCatching { withContext(Dispatchers.IO) { repository.getDocsDirUri(root) } }.getOrNull()
                val validatedAtMs = System.currentTimeMillis()
                if (nextSig != docsSig) {
                    docs = nextDocs
                    docsSig = nextSig
                    cacheUpdatedAtMs = validatedAtMs
                    DocumentListCache.put(root, docs = nextDocs, updatedAtMs = validatedAtMs)
                    runCatching { prefs.setDocListCache(root, nextDocs, updatedAtMs = validatedAtMs) }
                } else {
                    cacheUpdatedAtMs = validatedAtMs
                    DocumentListCache.put(root, docs = docs, updatedAtMs = validatedAtMs)
                    runCatching { prefs.touchDocListCacheUpdatedAt(root, updatedAtMs = validatedAtMs) }
                }

                if (reindex) {
                    withContext(Dispatchers.IO) { runCatching { repository.rebuildIndex(root) } }
                }
                lastRefreshAtMs = SystemClock.uptimeMillis()
            }
    }

    fun scheduleRefresh(force: Boolean, reindex: Boolean, debounceMs: Long) {
        scheduleJob?.cancel()
        scheduleJob =
            scope.launch {
                if (debounceMs > 0) delay(debounceMs)
                requestRefresh(force = force, reindex = reindex)
            }
    }

    LaunchedEffect(vaultRootUri, isActive) {
        if (!isActive) return@LaunchedEffect
        val root = vaultRootUri ?: return@LaunchedEffect

        // Let the first frame render (cached list / shell) before kicking off any I/O.
        withFrameNanos { }

        val cached =
            if (docs.isEmpty() || cacheUpdatedAtMs == 0L) prefs.getDocListCache(root) else null
        if (cached != null) {
            if (docs.isEmpty() && cached.docs.isNotEmpty()) {
                docs = cached.docs
            }
            if (cached.updatedAtMs > 0L) cacheUpdatedAtMs = cached.updatedAtMs
            if (docs.isNotEmpty() && cacheUpdatedAtMs > 0L) {
                DocumentListCache.put(root, docs = docs, updatedAtMs = cacheUpdatedAtMs)
            }
        }

        if (docsSig == 0L && docs.isNotEmpty()) {
            val snapshot = docs
            docsSig = withContext(Dispatchers.Default) { docListSignature(snapshot) }
        }

        // Defer docs directory lookups/observers until the list is visible and initial decisions are done.
        launch {
            delay(400)
            docsDirUri = withContext(Dispatchers.IO) { repository.getDocsDirUri(root) }
        }

        if (docs.isEmpty()) {
            // No cached list available: refresh after a short delay.
            delay(120)
            requestRefresh(force = true, reindex = false)
        }
    }

    LaunchedEffect(vaultRootUri, isActive, refreshToken) {
        if (!isActive) return@LaunchedEffect
        if (refreshToken <= 0L) return@LaunchedEffect
        if (vaultRootUri == null) return@LaunchedEffect
        repository.invalidateDocListCache(vaultRootUri)
        scheduleRefresh(force = true, reindex = false, debounceMs = 0)
    }

    LaunchedEffect(pendingRefresh, isActive) {
        val req = pendingRefresh ?: return@LaunchedEffect
        if (!isActive) return@LaunchedEffect
        snapshotFlow { listState.isScrollInProgress || showSearchSheet }
            .filter { busy -> !busy }
            .first()
        pendingRefresh = null
        requestRefresh(force = req.force, reindex = req.reindex)
    }

    DisposableEffect(lifecycleOwner, vaultRootUri, isActive) {
        if (!isActive || vaultRootUri == null) return@DisposableEffect onDispose {}
        val observer =
            LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_RESUME) {
                    // On cold start, ON_RESUME happens before we load cached docs; avoid forcing a refresh here.
                    if (skipNextResumeRefresh) {
                        skipNextResumeRefresh = false
                        return@LifecycleEventObserver
                    }
                    requestRefresh(force = false, reindex = false)
                }
            }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    DisposableEffect(docsDirUri, isActive) {
        if (!isActive) return@DisposableEffect onDispose {}
        val dirUri = docsDirUri ?: return@DisposableEffect onDispose {}
        val docId = runCatching { DocumentsContract.getDocumentId(dirUri) }.getOrNull() ?: return@DisposableEffect onDispose {}
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(dirUri, docId)
        val resolver = context.contentResolver
        val observer =
            object : ContentObserver(Handler(Looper.getMainLooper())) {
                override fun onChange(selfChange: Boolean) {
                    val root = latestVaultRootUri ?: return
                    if (!latestIsActive) return
                    repository.invalidateDocListCache(root)
                    scheduleRefresh(force = false, reindex = false, debounceMs = 250)
                }

                override fun onChange(selfChange: Boolean, uri: Uri?) {
                    onChange(selfChange)
                }
            }

        // ContentResolver observer registration can block on Binder; do it off the main thread to avoid jank.
        val registerJob =
            scope.launch(Dispatchers.IO) {
                runCatching { resolver.registerContentObserver(childrenUri, true, observer) }
            }
        onDispose {
            registerJob.cancel()
            scope.launch(Dispatchers.IO) {
                runCatching { resolver.unregisterContentObserver(observer) }
            }
        }
    }

    LaunchedEffect(docsDirUri, vaultRootUri, isActive) {
        if (!isActive) return@LaunchedEffect
        val root = vaultRootUri ?: return@LaunchedEffect
        val dirUri = docsDirUri ?: return@LaunchedEffect

        var lastDirModified = withContext(Dispatchers.IO) { repository.getDocumentLastModified(dirUri) }
        var ticks = 0
        while (currentCoroutineContext().isActive) {
            delay(4_000)
            ticks++

            val modified = withContext(Dispatchers.IO) { repository.getDocumentLastModified(dirUri) }
            if (modified > 0L && modified != lastDirModified) {
                lastDirModified = modified
                repository.invalidateDocListCache(root)
                scheduleRefresh(force = false, reindex = false, debounceMs = 200)
                continue
            }

            if (modified <= 0L && ticks % 8 == 0) {
                // Some providers don't update folder timestamps reliably; force a periodic refresh as a fallback.
                repository.invalidateDocListCache(root)
                scheduleRefresh(force = true, reindex = false, debounceMs = 0)
            }
        }
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
                results =
                    withContext(Dispatchers.IO) {
                        repository.search(vaultRootUri, query)
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
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    state = listState,
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
                    itemsIndexed(
                        items = docs,
                        key = { _, doc -> doc.uri },
                        contentType = { _, _ -> "doc" },
                    ) { index, doc ->
                        val title = doc.baseName.ifBlank { defaultTitle }
                        val editedAt = doc.editedAtText.ifBlank { editedAtDash }
                        DocRow(
                            title = title,
                            editedAt = editedAt,
                            onClick = { onOpenDoc(doc.uri.toString(), null, null) },
                            showDivider = index != docs.lastIndex,
                            dividerColor = dividerColor,
                        )
                    }
                }
            }
        }

        CapsuleActionBar(
            modifier =
                Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 18.dp + CapsuleActionBarDefaults.Height)
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

private data class PendingRefresh(
    val force: Boolean,
    val reindex: Boolean,
)

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
    }
}

private object DocumentListCache {
    private const val MaxEntries = 4
    private val lock = Any()
    private val cache =
        object : LinkedHashMap<String, Entry>(MaxEntries, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Entry>?): Boolean = size > MaxEntries
        }

    data class Entry(
        val docs: List<UiDoc>,
        val updatedAtMs: Long,
    )

    fun get(rootUri: Uri?): Entry? {
        val key = rootUri?.toString().orEmpty()
        if (key.isBlank()) return null
        return synchronized(lock) { cache[key] }
    }

    fun put(
        rootUri: Uri,
        docs: List<UiDoc>,
        updatedAtMs: Long,
    ) {
        synchronized(lock) { cache[rootUri.toString()] = Entry(docs = docs, updatedAtMs = updatedAtMs) }
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

private fun docListSignature(docs: List<UiDoc>): Long {
    var sig = 1125899906842597L
    for (doc in docs) {
        sig = 31 * sig + doc.uri.hashCode().toLong()
        sig = 31 * sig + doc.lastModified
        sig = 31 * sig + doc.size
    }
    return sig
}

@Composable
private fun DocRow(
    title: String,
    editedAt: String,
    onClick: () -> Unit,
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
            imageVector = Icons.Outlined.Description,
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
        Spacer(modifier = Modifier.size(12.dp))
        Icon(
            imageVector = Icons.Outlined.MoreHoriz,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
