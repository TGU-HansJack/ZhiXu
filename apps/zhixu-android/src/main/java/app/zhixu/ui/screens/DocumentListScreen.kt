package app.zhixu.ui.screens

import android.content.Intent
import android.content.res.Configuration
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
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.itemsIndexed as staggeredItemsIndexed
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import app.zhixu.ui.components.ZhixuTextField
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalConfiguration
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
import app.zhixu.data.UiPreferences
import app.zhixu.data.VaultIndexUpdater
import app.zhixu.data.VaultRepository
import app.zhixu.draw.ZhixuDrawFormat
import app.zhixu.draw.ZhixuDrawPage
import app.zhixu.draw.ui.DrawDocumentPreviewRow
import app.zhixu.ui.Ionicons
import app.zhixu.ui.DocListMutation
import app.zhixu.ui.components.RefreshStatusBanner
import app.zhixu.ui.components.SheetActionRow
import app.zhixu.ui.components.SheetQuickAction
import app.zhixu.ui.components.ZhixuCompactDragHandle
import app.zhixu.ui.components.VaultSearchDialog
import io.noties.markwon.AbstractMarkwonPlugin
import io.noties.markwon.Markwon
import io.noties.markwon.core.MarkwonTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import androidx.compose.ui.input.pointer.pointerInteropFilter
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

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
    sortFilterRequestToken: Long,
    useGridLayout: Boolean,
    pinnedDocUris: List<String> = emptyList(),
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
    val configuration = LocalConfiguration.current
    val isTablet = configuration.smallestScreenWidthDp >= 600
    val listState = rememberLazyListState()
    val gridState = rememberLazyStaggeredGridState()

    var savedFirstVisibleKey by rememberSaveable(vaultRootUri) { mutableStateOf<String?>(null) }
    var savedFirstVisibleIndex by rememberSaveable(vaultRootUri) { mutableIntStateOf(0) }
    var savedFirstVisibleOffset by rememberSaveable(vaultRootUri) { mutableIntStateOf(0) }
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
    var sortOrder by rememberSaveable(vaultRootUri) { mutableStateOf(DocListSortOrder.EditedDesc) }
    var typeFilter by rememberSaveable(vaultRootUri) { mutableStateOf(DocListTypeFilter.All) }
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<SearchResult>>(emptyList()) }
    var searchJob by remember { mutableStateOf<Job?>(null) }
    var lastRefreshBannerAtMs by remember(vaultRootUri) { mutableStateOf(0L) }
    var manualRefreshToken by remember(vaultRootUri) { mutableLongStateOf(0L) }
    var showSearchSheet by remember { mutableStateOf(false) }
    var showSortFilterSheet by remember { mutableStateOf(false) }
    var pendingOpenSearch by remember { mutableStateOf(false) }
    var pendingOpenSortFilter by remember { mutableStateOf(false) }
    var lastSearchToken by remember { mutableLongStateOf(searchRequestToken) }
    var lastSortFilterToken by remember { mutableLongStateOf(sortFilterRequestToken) }

    var selectedDoc by remember { mutableStateOf<UiDoc?>(null) }
    var showDocMenu by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var renameInput by remember { mutableStateOf("") }
    var showDeleteDialog by remember { mutableStateOf(false) }

    val isIndexUpdating by indexUpdater.isUpdating.collectAsState()

    val previewCache = remember(vaultRootUri) { mutableStateMapOf<String, String>() }
    val drawingPreviewCache = remember(vaultRootUri) { mutableStateMapOf<String, List<ZhixuDrawPage>>() }

    val uiPrefs = remember(context) { UiPreferences(context.applicationContext) }
    val strictDocListPreview by uiPrefs.strictDocListPreview.collectAsState(initial = true)
    val showDocPreview =
        isTablet ||
            strictDocListPreview ||
            configuration.orientation != Configuration.ORIENTATION_LANDSCAPE

    val pinnedSet = remember(pinnedDocUris) { pinnedDocUris.toHashSet() }

    val visibleDocs =
        remember(docs, sortOrder, typeFilter, pinnedDocUris) {
            val filtered =
                when (typeFilter) {
                    DocListTypeFilter.All -> docs
                    DocListTypeFilter.Markdown -> docs.filter { it.name.endsWith(".md", ignoreCase = true) }
                    DocListTypeFilter.Drawing -> docs.filter { ZhixuDrawFormat.hasDrawingExtension(it.name) }
                }

            val sorted =
                when (sortOrder) {
                    DocListSortOrder.EditedDesc -> filtered
                    DocListSortOrder.CreatedDesc ->
                        filtered.sortedWith(
                            compareByDescending<UiDoc> { it.createdAt }.thenBy { it.name.lowercase() },
                        )
                    DocListSortOrder.NameAsc ->
                        filtered.sortedWith(
                            compareBy<UiDoc> { it.baseName.ifBlank { it.name }.lowercase() },
                        )
                }

            if (pinnedDocUris.isEmpty()) return@remember sorted

            val pinnedSet = pinnedDocUris.toHashSet()
            val byUri = sorted.associateBy { it.uri.toString() }
            val pinned = ArrayList<UiDoc>(pinnedDocUris.size)
            for (uriStr in pinnedDocUris) {
                val doc = byUri[uriStr] ?: continue
                pinned += doc
            }
            val rest = sorted.filterNot { it.uri.toString() in pinnedSet }
            pinned + rest
        }

    LaunchedEffect(isActive, visibleDocs, useGridLayout) {
        if (!isActive) return@LaunchedEffect
        if (visibleDocs.isEmpty()) return@LaunchedEffect

        val needsRestore =
            if (useGridLayout) {
                gridState.firstVisibleItemIndex == 0 &&
                    gridState.firstVisibleItemScrollOffset == 0 &&
                    (savedFirstVisibleIndex != 0 || savedFirstVisibleOffset != 0)
            } else {
                listState.firstVisibleItemIndex == 0 &&
                    listState.firstVisibleItemScrollOffset == 0 &&
                    (savedFirstVisibleIndex != 0 || savedFirstVisibleOffset != 0)
            }

        if (needsRestore) {
            val targetIndexFromKey =
                savedFirstVisibleKey?.let { key ->
                    visibleDocs.indexOfFirst { it.uri.toString() == key }.takeIf { it >= 0 }
                }

            val targetIndex =
                when {
                    targetIndexFromKey != null -> targetIndexFromKey
                    savedFirstVisibleIndex in visibleDocs.indices -> savedFirstVisibleIndex
                    else -> 0
                }

            if (targetIndex != 0 || savedFirstVisibleOffset != 0) {
                if (useGridLayout) {
                    gridState.scrollToItem(
                        index = targetIndex,
                        scrollOffset = savedFirstVisibleOffset.coerceAtLeast(0),
                    )
                } else {
                    listState.scrollToItem(
                        index = targetIndex,
                        scrollOffset = savedFirstVisibleOffset.coerceAtLeast(0),
                    )
                }
            }
        }

        snapshotFlow {
            if (useGridLayout) {
                gridState.firstVisibleItemIndex to gridState.firstVisibleItemScrollOffset
            } else {
                listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset
            }
        }
            .distinctUntilChanged()
            .collect { (index, offset) ->
                savedFirstVisibleIndex = index
                savedFirstVisibleOffset = offset
                savedFirstVisibleKey = visibleDocs.getOrNull(index)?.uri?.toString()
            }
    }

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

    LaunchedEffect(sortFilterRequestToken) {
        if (sortFilterRequestToken == lastSortFilterToken) return@LaunchedEffect
        lastSortFilterToken = sortFilterRequestToken
        pendingOpenSortFilter = true
    }

    LaunchedEffect(isActive, pendingOpenSortFilter) {
        if (!isActive || !pendingOpenSortFilter) return@LaunchedEffect
        pendingOpenSortFilter = false
        showSortFilterSheet = true
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
        }
    }

    LaunchedEffect(manualRefreshToken, vaultRootUri, isActive) {
        if (!isActive) return@LaunchedEffect
        if (manualRefreshToken <= 0L) return@LaunchedEffect
        if (vaultRootUri == null) return@LaunchedEffect
        indexUpdater.requestRefresh()
        // Wait for a full updating cycle so we don't show "Updated" immediately when already idle.
        indexUpdater.isUpdating.filter { it }.first()
        indexUpdater.isUpdating.filter { !it }.first()
        lastRefreshBannerAtMs = SystemClock.uptimeMillis()
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
                val nowMs = System.currentTimeMillis()
                val timeBelowPreview = !isTablet
                val listBg = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.70f)
                val pullState = rememberPullToRefreshState()
            PullToRefreshBox(
                isRefreshing = isActive && isIndexUpdating,
                onRefresh = {
                    if (!isActive) return@PullToRefreshBox
                    if (isIndexUpdating) return@PullToRefreshBox
                    manualRefreshToken += 1L
                },
                state = pullState,
                modifier = Modifier.fillMaxSize(),
                indicator = {},
            ) {
                    Box(modifier = Modifier.fillMaxSize().background(listBg)) {
                        if (useGridLayout) {
                            LazyVerticalStaggeredGrid(
                                modifier = Modifier.fillMaxSize(),
                                columns = StaggeredGridCells.Fixed(2),
                                state = gridState,
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalItemSpacing = 10.dp,
                            ) {
                                if (visibleDocs.isEmpty()) {
                                    item(span = StaggeredGridItemSpan.FullLine) {
                                        Text(
                                            text = stringResource(R.string.docs_empty),
                                            modifier = Modifier.padding(8.dp),
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                                staggeredItemsIndexed(
                                    items = visibleDocs,
                                    key = { _, doc -> doc.uri },
                                    contentType = { _, _ -> "doc" },
                                ) { _, doc ->
                                    val title = doc.baseName.ifBlank { defaultTitle }
                                    val timeText =
                                        formatDocListTimeText(
                                            context = context,
                                            createdAtMs = doc.createdAt,
                                            editedAtMs = doc.lastModified,
                                            nowMs = nowMs,
                                        ).ifBlank { editedAtDash }
                                    val docUriStr = doc.uri.toString()
                                    val pinned = docUriStr in pinnedSet
                                    val isDrawing = ZhixuDrawFormat.hasDrawingExtension(doc.name)
                                    val previewKey =
                                        remember(docUriStr, doc.lastModified) { "$docUriStr@${doc.lastModified}" }
                                    val previewMarkdown = if (isDrawing) null else previewCache[previewKey]
                                    val previewPages = if (isDrawing) drawingPreviewCache[previewKey] else null

                                    if (showDocPreview) {
                                        if (!isDrawing) {
                                            LaunchedEffect(previewKey) {
                                                if (previewCache.containsKey(previewKey)) return@LaunchedEffect
                                                previewCache[previewKey] = ""
                                                val raw = repository.readTextPreview(doc.uri, maxChars = 2500)
                                                previewCache[previewKey] = extractDocPreviewMarkdown(raw, maxChars = 150)
                                            }
                                        } else {
                                            LaunchedEffect(previewKey) {
                                                if (drawingPreviewCache.containsKey(previewKey)) return@LaunchedEffect
                                                drawingPreviewCache[previewKey] = emptyList()
                                                val pages =
                                                    withContext(Dispatchers.IO) {
                                                        val bytes = repository.readBytes(doc.uri) ?: return@withContext emptyList<ZhixuDrawPage>()
                                                        runCatching { ZhixuDrawFormat.decode(bytes).pages.take(4) }.getOrDefault(emptyList())
                                                    }
                                                drawingPreviewCache[previewKey] = pages
                                            }
                                        }
                                    }

                                    val previewContent: (@Composable () -> Unit)? =
                                        if (!showDocPreview) {
                                            null
                                        } else {
                                            when {
                                                isDrawing -> {
                                                    {
                                                        Surface(
                                                            modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                                                            color = listBg,
                                                            shape = RoundedCornerShape(8.dp),
                                                        ) {
                                                            Box(
                                                                modifier = Modifier.fillMaxWidth().padding(8.dp),
                                                                contentAlignment = Alignment.Center,
                                                            ) {
                                                                DrawDocumentPreviewRow(
                                                                    pages = previewPages,
                                                                    maxHeight = configuration.screenHeightDp.dp / 3,
                                                                    modifier = Modifier.fillMaxWidth(),
                                                                )
                                                            }
                                                        }
                                                    }
                                                }

                                                !previewMarkdown.isNullOrBlank() -> {
                                                    {
                                                        Surface(
                                                            modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                                                            color = listBg,
                                                            shape = RoundedCornerShape(8.dp),
                                                        ) {
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
                                                                        .padding(horizontal = 12.dp, vertical = 10.dp)
                                                                        .pointerInteropFilter { false },
                                                            )
                                                        }
                                                    }
                                                }

                                                else -> null
                                            }
                                        }

                                    DocRow(
                                        title = title,
                                        timeText = timeText,
                                        pinned = pinned,
                                        compactHeader = useGridLayout,
                                        timeBelowPreview = timeBelowPreview,
                                        previewContent = previewContent,
                                        selectionMode = selectionMode,
                                        selected = docUriStr in selectedDocUris,
                                        onClick = {
                                            if (selectionMode) onToggleDocSelection(docUriStr) else onOpenDoc(docUriStr, null, null)
                                        },
                                        onLongClick = { onToggleDocSelection(docUriStr) },
                                    )
                                }
                            }
                        } else {
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                state = listState,
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                if (visibleDocs.isEmpty()) {
                                    item {
                                        Text(
                                            text = stringResource(R.string.docs_empty),
                                            modifier = Modifier.padding(8.dp),
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                                itemsIndexed(
                                    items = visibleDocs,
                                    key = { _, doc -> doc.uri },
                                    contentType = { _, _ -> "doc" },
                                ) { _, doc ->
                                val title = doc.baseName.ifBlank { defaultTitle }
                                val timeText =
                                    formatDocListTimeText(
                                        context = context,
                                        createdAtMs = doc.createdAt,
                                        editedAtMs = doc.lastModified,
                                        nowMs = nowMs,
                                    ).ifBlank { editedAtDash }
                                val docUriStr = doc.uri.toString()
                                val pinned = docUriStr in pinnedSet
                                val isDrawing = ZhixuDrawFormat.hasDrawingExtension(doc.name)
                                val previewKey = remember(docUriStr, doc.lastModified) { "$docUriStr@${doc.lastModified}" }
                                val previewMarkdown = if (isDrawing) null else previewCache[previewKey]
                                val previewPages = if (isDrawing) drawingPreviewCache[previewKey] else null

                                if (showDocPreview) {
                                    if (!isDrawing) {
                                        LaunchedEffect(previewKey) {
                                            if (previewCache.containsKey(previewKey)) return@LaunchedEffect
                                            previewCache[previewKey] = ""
                                            val raw = repository.readTextPreview(doc.uri, maxChars = 2500)
                                            previewCache[previewKey] = extractDocPreviewMarkdown(raw, maxChars = 150)
                                        }
                                    } else {
                                        LaunchedEffect(previewKey) {
                                            if (drawingPreviewCache.containsKey(previewKey)) return@LaunchedEffect
                                            drawingPreviewCache[previewKey] = emptyList()
                                            val pages =
                                                withContext(Dispatchers.IO) {
                                                    val bytes = repository.readBytes(doc.uri) ?: return@withContext emptyList<ZhixuDrawPage>()
                                                    runCatching { ZhixuDrawFormat.decode(bytes).pages.take(4) }.getOrDefault(emptyList())
                                                }
                                            drawingPreviewCache[previewKey] = pages
                                        }
                                    }
                                }

                                val previewContent: (@Composable () -> Unit)? =
                                    if (!showDocPreview) {
                                        null
                                    } else {
                                        when {
                                            isDrawing -> {
                                                {
                                                    Surface(
                                                        modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                                                        color = listBg,
                                                        shape = RoundedCornerShape(8.dp),
                                                    ) {
                                                        Box(
                                                            modifier = Modifier.fillMaxWidth().padding(8.dp),
                                                            contentAlignment = Alignment.Center,
                                                        ) {
                                                            DrawDocumentPreviewRow(
                                                                pages = previewPages,
                                                                maxHeight = configuration.screenHeightDp.dp / 3,
                                                                modifier = Modifier.fillMaxWidth(),
                                                            )
                                                        }
                                                    }
                                                }
                                            }

                                            !previewMarkdown.isNullOrBlank() -> {
                                                {
                                                    Surface(
                                                        modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                                                        color = listBg,
                                                        shape = RoundedCornerShape(8.dp),
                                                    ) {
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
                                                                    .padding(horizontal = 12.dp, vertical = 10.dp)
                                                                    .pointerInteropFilter { false },
                                                        )
                                                    }
                                                }
                                            }

                                            else -> null
                                        }
                                    }

                                DocRow(
                                    title = title,
                                    timeText = timeText,
                                    pinned = pinned,
                                    compactHeader = useGridLayout,
                                    timeBelowPreview = timeBelowPreview,
                                    previewContent = previewContent,
                                    selectionMode = selectionMode,
                                    selected = docUriStr in selectedDocUris,
                                    onClick = {
                                        if (selectionMode) onToggleDocSelection(docUriStr) else onOpenDoc(docUriStr, null, null)
                                    },
                                    onLongClick = { onToggleDocSelection(docUriStr) },
                                )
                            }
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

    val sortFilterSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val docMenuSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    if (showSortFilterSheet) {
        ModalBottomSheet(
            onDismissRequest = { showSortFilterSheet = false },
            sheetState = sortFilterSheetState,
            containerColor = MaterialTheme.colorScheme.background,
            tonalElevation = 0.dp,
            dragHandle = { ZhixuCompactDragHandle() },
        ) {
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                        .windowInsetsPadding(WindowInsets.navigationBars),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = stringResource(R.string.docs_sort_filter_title),
                    style = MaterialTheme.typography.titleMedium,
                )

                Text(
                    text = stringResource(R.string.docs_sort_title),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                DocListSheetOption(
                    title = stringResource(R.string.docs_sort_edited_desc),
                    selected = sortOrder == DocListSortOrder.EditedDesc,
                    onClick = { sortOrder = DocListSortOrder.EditedDesc },
                )
                DocListSheetOption(
                    title = stringResource(R.string.docs_sort_created_desc),
                    selected = sortOrder == DocListSortOrder.CreatedDesc,
                    onClick = { sortOrder = DocListSortOrder.CreatedDesc },
                )
                DocListSheetOption(
                    title = stringResource(R.string.docs_sort_name_asc),
                    selected = sortOrder == DocListSortOrder.NameAsc,
                    onClick = { sortOrder = DocListSortOrder.NameAsc },
                )

                Spacer(modifier = Modifier.size(8.dp))

                Text(
                    text = stringResource(R.string.docs_filter_title),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                DocListSheetOption(
                    title = stringResource(R.string.docs_filter_all),
                    selected = typeFilter == DocListTypeFilter.All,
                    onClick = { typeFilter = DocListTypeFilter.All },
                )
                DocListSheetOption(
                    title = stringResource(R.string.docs_filter_markdown),
                    selected = typeFilter == DocListTypeFilter.Markdown,
                    onClick = { typeFilter = DocListTypeFilter.Markdown },
                )
                DocListSheetOption(
                    title = stringResource(R.string.docs_filter_drawing),
                    selected = typeFilter == DocListTypeFilter.Drawing,
                    onClick = { typeFilter = DocListTypeFilter.Drawing },
                )

                Spacer(modifier = Modifier.size(8.dp))
            }
        }
    }

    if (showDocMenu && selectedDoc != null) {
        ModalBottomSheet(
            onDismissRequest = { showDocMenu = false },
            sheetState = docMenuSheetState,
            containerColor = MaterialTheme.colorScheme.background,
            tonalElevation = 0.dp,
            dragHandle = { ZhixuCompactDragHandle() },
        ) {
            DocumentRowActionsSheet(
                onRename = {
                    val doc = selectedDoc ?: return@DocumentRowActionsSheet
                    renameInput = doc.baseName.ifBlank { ZhixuDrawFormat.stripDrawingExtension(doc.name).removeSuffix(".md") }
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
                        val isDrawing = ZhixuDrawFormat.hasDrawingExtension(doc.name)
                        val payload =
                            withContext(Dispatchers.IO) {
                                if (isDrawing) doc.uri.toString() else repository.readText(doc.uri)
                            }
                        clipboard.setText(AnnotatedString(payload))
                        Toast.makeText(context, context.getString(R.string.common_copied), Toast.LENGTH_SHORT).show()
                    }
                },
                onShare = {
                    val doc = selectedDoc ?: return@DocumentRowActionsSheet
                    showDocMenu = false
                    scope.launch {
                        val isDrawing = ZhixuDrawFormat.hasDrawingExtension(doc.name)
                        val subject = doc.baseName.ifBlank { ZhixuDrawFormat.stripDrawingExtension(doc.name).ifBlank { "Zhixu" } }
                        val intent =
                            if (isDrawing) {
                                Intent(Intent.ACTION_SEND).apply {
                                    type = ZhixuDrawFormat.MIME_TYPE
                                    putExtra(Intent.EXTRA_SUBJECT, subject)
                                    putExtra(Intent.EXTRA_STREAM, doc.uri)
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                            } else {
                                val text = withContext(Dispatchers.IO) { repository.readText(doc.uri) }
                                Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_SUBJECT, subject)
                                    putExtra(Intent.EXTRA_TEXT, text)
                                }
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
                                    if (ZhixuDrawFormat.hasDrawingExtension(doc.name)) {
                                        repository.renameFile(doc.uri, desiredName)
                                    } else {
                                        repository.renameDoc(doc.uri, desiredName)
                                    }
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

private enum class DocListSortOrder {
    EditedDesc,
    CreatedDesc,
    NameAsc,
}

private enum class DocListTypeFilter {
    All,
    Markdown,
    Drawing,
}

@Composable
private fun DocListSheetOption(
    title: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    ListItem(
        headlineContent = { Text(title) },
        trailingContent = {
            if (selected) {
                Icon(
                    painter = painterResource(Ionicons.Checkmark),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp),
                )
            }
        },
        modifier = Modifier.clickable(onClick = onClick),
    )
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
    timeText: String,
    pinned: Boolean,
    compactHeader: Boolean,
    timeBelowPreview: Boolean,
    previewContent: (@Composable () -> Unit)?,
    selectionMode: Boolean,
    selected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    Surface(
        modifier =
            Modifier
                .fillMaxWidth()
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = onLongClick,
                ),
        color = MaterialTheme.colorScheme.surface,
        border = if (selected) BorderStroke(1.dp, MaterialTheme.colorScheme.primary) else null,
        shape = RoundedCornerShape(8.dp),
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    val titleStyle = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium)
                    if (pinned) {
                        val pinnedColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                painter = painterResource(Ionicons.Pin),
                                contentDescription = null,
                                tint = pinnedColor,
                                modifier = Modifier.size(14.dp),
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = stringResource(R.string.doc_pinned_label),
                                color = pinnedColor,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Light),
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = title,
                                maxLines = if (compactHeader) 2 else 1,
                                overflow = TextOverflow.Ellipsis,
                                style = titleStyle,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    } else {
                        Text(
                            text = title,
                            maxLines = if (compactHeader) 2 else 1,
                            overflow = TextOverflow.Ellipsis,
                            style = titleStyle,
                        )
                    }
                    if (!timeBelowPreview && compactHeader) {
                        Spacer(modifier = Modifier.size(2.dp))
                        Text(
                            text = stringResource(R.string.edited_at_fmt, timeText),
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Light),
                        )
                    }
                }
                if (!timeBelowPreview && !compactHeader) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.edited_at_fmt, timeText),
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Light),
                        textAlign = TextAlign.End,
                        modifier = Modifier.widthIn(max = 140.dp),
                    )
                }
                if (selectionMode) {
                    Spacer(modifier = Modifier.width(10.dp))
                    if (selected) {
                        Box(
                            modifier =
                                Modifier
                                    .size(20.dp)
                                    .background(MaterialTheme.colorScheme.primary, CircleShape),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                painter = painterResource(Ionicons.CircleCheck),
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    } else {
                        Icon(
                            painter = painterResource(Ionicons.Circle),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f),
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
            }

            previewContent?.invoke()

            if (timeBelowPreview) {
                Spacer(modifier = Modifier.size(if (previewContent == null) 6.dp else 8.dp))
                Text(
                    text = stringResource(R.string.edited_at_fmt, timeText),
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Light),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

private val docListTimeFormatterTimeOnly: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
private val docListTimeFormatterThisYear: DateTimeFormatter = DateTimeFormatter.ofPattern("MM-dd HH:mm")
private val docListTimeFormatterOtherYear: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")

private fun formatDocListTimeText(
    context: android.content.Context,
    createdAtMs: Long,
    editedAtMs: Long,
    nowMs: Long,
): String {
    val baseMs = if (createdAtMs > 0L) createdAtMs else editedAtMs
    if (baseMs <= 0L) return ""

    val zoneId = ZoneId.systemDefault()
    val nowDate = Instant.ofEpochMilli(nowMs).atZone(zoneId).toLocalDate()
    val baseText = formatDocListTimestamp(context, baseMs, nowDate, zoneId)

    val showEdited = editedAtMs > 0L && editedAtMs > baseMs
    if (!showEdited) return baseText

    val editedText = formatDocListTimestamp(context, editedAtMs, nowDate, zoneId)
    if (editedText.isBlank() || editedText == baseText) return baseText

    return baseText + " " + context.getString(R.string.doc_time_edited_at_paren_fmt, editedText)
}

private fun formatDocListTimestamp(
    context: android.content.Context,
    timestampMs: Long,
    nowDate: LocalDate,
    zoneId: ZoneId,
): String {
    if (timestampMs <= 0L) return ""
    val localDateTime = Instant.ofEpochMilli(timestampMs).atZone(zoneId).toLocalDateTime()
    val date = localDateTime.toLocalDate()

    return when (date) {
        nowDate ->
            context.getString(
                R.string.doc_time_today_time_fmt,
                docListTimeFormatterTimeOnly.format(localDateTime),
            )
        nowDate.minusDays(1) ->
            context.getString(
                R.string.doc_time_yesterday_time_fmt,
                docListTimeFormatterTimeOnly.format(localDateTime),
            )
        nowDate.minusDays(2) ->
            context.getString(
                R.string.doc_time_day_before_yesterday_time_fmt,
                docListTimeFormatterTimeOnly.format(localDateTime),
            )
        else ->
            when (date.year) {
                nowDate.year -> docListTimeFormatterThisYear.format(localDateTime)
                else -> docListTimeFormatterOtherYear.format(localDateTime)
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
