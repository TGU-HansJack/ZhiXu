package app.zhixu.ui.components

import android.net.Uri
import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.TextButton
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material3.Surface
import app.zhixu.R
import app.zhixu.data.VaultRepository
import app.zhixu.data.VaultTreeEntry
import app.zhixu.ui.Ionicons
import app.zhixu.ui.DocListMutation
import kotlinx.coroutines.launch

private object VaultDrawerCache {
    private const val MaxEntries = 4
    private val lock = Any()
    private val cache =
        object : LinkedHashMap<String, Entry>(MaxEntries, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Entry>?): Boolean = size > MaxEntries
        }

    data class Entry(
        val entries: List<VaultTreeEntry>,
        val loadedDirs: Set<String>,
        val updatedAtMs: Long,
        val refreshToken: Long,
    )

    fun get(rootUri: Uri?): Entry? {
        val key = rootUri?.toString().orEmpty()
        if (key.isBlank()) return null
        return synchronized(lock) { cache[key] }
    }

    fun put(rootUri: Uri, entry: Entry) {
        synchronized(lock) { cache[rootUri.toString()] = entry }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun VaultDrawer(
    vaultRootUri: Uri,
    repository: VaultRepository,
    onOpenDoc: (String) -> Unit,
    onCloseDrawer: () -> Unit,
    isActive: Boolean,
    refreshToken: Long,
    mutation: DocListMutation?,
    onDocListMutated: (DocListMutation) -> Unit = {},
    enableMultiSelect: Boolean = false,
    selectedEntryUris: Set<String> = emptySet(),
    onToggleEntrySelection: (String) -> Unit = {},
    activeDirectoryRelativePath: String? = null,
    onActiveDirectoryChange: (String?) -> Unit = {},
    sheetWidth: Dp = 320.dp,
    drawerContainerColor: Color = MaterialTheme.colorScheme.background,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    useSystemInsets: Boolean = true,
    showHeader: Boolean = true,
    headerMinHeight: Dp = 56.dp,
    itemMinHeight: Dp = 32.dp,
    itemTextStyle: TextStyle = MaterialTheme.typography.bodySmall,
    itemIconSize: Dp = 18.dp,
    itemChevronSize: Dp = 16.dp,
    listContentPadding: PaddingValues = PaddingValues(vertical = 8.dp),
    listItemSpacing: Dp = 0.dp,
    useDocumentListStyle: Boolean = false,
    documentItemPadding: PaddingValues = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
    itemShape: RoundedCornerShape = RoundedCornerShape(10.dp),
    allDirsExpanded: Boolean? = null,
    entryFilter: ((VaultTreeEntry) -> Boolean)? = null,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val initialCache = remember(vaultRootUri) { VaultDrawerCache.get(vaultRootUri) }

    var entries by remember(vaultRootUri) { mutableStateOf(initialCache?.entries ?: emptyList()) }
    var errorText by remember(vaultRootUri) { mutableStateOf<String?>(null) }
    var cacheUpdatedAtMs by remember(vaultRootUri) { mutableStateOf(initialCache?.updatedAtMs ?: 0L) }
    var handledRefreshToken by remember(vaultRootUri) { mutableStateOf(initialCache?.refreshToken ?: 0L) }

    var selectedEntry by remember(vaultRootUri) { mutableStateOf<VaultTreeEntry?>(null) }
    var showEntryMenu by remember(vaultRootUri) { mutableStateOf(false) }
    var showRenameDialog by remember(vaultRootUri) { mutableStateOf(false) }
    var renameInput by remember(vaultRootUri) { mutableStateOf("") }
    var showDeleteDialog by remember(vaultRootUri) { mutableStateOf(false) }
    var showNewDocDialog by remember(vaultRootUri) { mutableStateOf(false) }
    var newDocTargetDir by remember(vaultRootUri) { mutableStateOf<VaultTreeEntry?>(null) }
    var newDocName by remember(vaultRootUri) { mutableStateOf("") }

    val loadedDirs =
        remember(vaultRootUri) {
            mutableStateMapOf<String, Boolean>().apply {
                val init = initialCache?.loadedDirs?.takeIf { it.isNotEmpty() } ?: setOf("")
                for (dir in init) this[dir] = true
                this[""] = true
            }
        }
    val loadingDirs = remember(vaultRootUri) { mutableStateMapOf<String, Boolean>() }
    val expandedDirs = remember(vaultRootUri) { mutableStateMapOf<String, Boolean>() }

    fun parentPathOf(relativePath: String): String? {
        val cleaned = relativePath.replace('\\', '/').trimStart('/')
        val idx = cleaned.lastIndexOf('/')
        if (idx < 0) return null
        return cleaned.substring(0, idx + 1).takeIf { it.isNotBlank() }
    }

    fun persistCache(now: Long = System.currentTimeMillis()) {
        cacheUpdatedAtMs = now
        VaultDrawerCache.put(
            vaultRootUri,
            VaultDrawerCache.Entry(
                entries = entries,
                loadedDirs = loadedDirs.keys.toSet(),
                updatedAtMs = now,
                refreshToken = handledRefreshToken,
            ),
        )
    }

    fun pruneSubtree(dirPath: String) {
        if (dirPath.isBlank()) return
        entries = entries.filterNot { it.relativePath != dirPath && it.relativePath.startsWith(dirPath) }
        loadedDirs.keys.filter { it != dirPath && it.startsWith(dirPath) }.forEach { loadedDirs.remove(it) }
        loadingDirs.keys.filter { it != dirPath && it.startsWith(dirPath) }.forEach { loadingDirs.remove(it) }
        expandedDirs.keys.filter { it != dirPath && it.startsWith(dirPath) }.forEach { expandedDirs.remove(it) }
    }

    suspend fun reloadDir(dirPath: String) {
        val key = dirPath
        if (loadingDirs[key] == true) return
        loadingDirs[key] = true
        try {
            repository.ensureDirIndexBuilt(vaultRootUri, force = false)
            runCatching {
                repository.refreshDirIndexForDirectory(
                    rootUri = vaultRootUri,
                    parentRelativePath = key.takeIf { it.isNotBlank() },
                )
            }
            if (key.isBlank()) {
                errorText = null
                val loaded =
                    runCatching {
                        repository.listVaultChildrenEntriesIndexed(
                            rootUri = vaultRootUri,
                            parentRelativePath = null,
                        )
                    }.getOrElse { e ->
                        errorText = e.message ?: e.javaClass.simpleName
                        emptyList()
                    }
                entries = loaded
                loadedDirs.clear()
                loadedDirs[""] = true
                expandedDirs.clear()
            } else {
                pruneSubtree(key)
                val loaded =
                    runCatching {
                        repository.listVaultChildrenEntriesIndexed(
                            rootUri = vaultRootUri,
                            parentRelativePath = key,
                        )
                    }.getOrElse { emptyList() }

                val idx = entries.indexOfFirst { it.relativePath == key }
                if (idx >= 0) {
                    val prefix = entries.take(idx + 1)
                    val suffix = entries.drop(idx + 1)
                    val withoutDupes = loaded.filter { child -> entries.none { it.relativePath == child.relativePath } }
                    entries = prefix + withoutDupes + suffix
                    loadedDirs[key] = true
                }
            }
            persistCache()
        } finally {
            loadingDirs[key] = false
        }
    }

    LaunchedEffect(vaultRootUri, isActive) {
        if (!isActive) return@LaunchedEffect
        val now = System.currentTimeMillis()
        val cacheAgeMs = if (cacheUpdatedAtMs > 0L) (now - cacheUpdatedAtMs) else Long.MAX_VALUE
        if (entries.isEmpty() || cacheAgeMs > 30 * 60 * 1000L) {
            reloadDir("")
        }
    }

    LaunchedEffect(vaultRootUri, isActive, refreshToken) {
        if (!isActive) return@LaunchedEffect
        if (refreshToken == handledRefreshToken) return@LaunchedEffect
        handledRefreshToken = refreshToken

        val m = mutation ?: run { persistCache(); return@LaunchedEffect }
        val dirsToRefresh =
            when (m) {
                is DocListMutation.Created ->
                    listOfNotNull(repository.computeRelativePath(vaultRootUri, m.doc.uri))
                        .map { parentPathOf(it) ?: "" }

                is DocListMutation.Deleted ->
                    listOfNotNull(repository.computeRelativePath(vaultRootUri, m.docUri))
                        .map { parentPathOf(it) ?: "" }

                is DocListMutation.Renamed ->
                    listOfNotNull(
                        repository.computeRelativePath(vaultRootUri, m.oldUri),
                        repository.computeRelativePath(vaultRootUri, m.newUri),
                    ).map { parentPathOf(it) ?: "" }

                is DocListMutation.EntryChanged ->
                    listOfNotNull(repository.computeRelativePath(vaultRootUri, m.entryUri))
                        .map { parentPathOf(it) ?: "" }
            }.distinct()

        var refreshedAny = false
        for (dir in dirsToRefresh) {
            if (loadedDirs[dir] == true) {
                refreshedAny = true
                runCatching { repository.refreshDirIndexForDirectory(vaultRootUri, parentRelativePath = dir) }
                reloadDir(dir)
            }
        }
        if (!refreshedAny) persistCache()
    }

    val entryByPath = remember(entries) { entries.associateBy { it.relativePath } }

    LaunchedEffect(allDirsExpanded) {
        if (allDirsExpanded == false) expandedDirs.clear()
    }

    LaunchedEffect(allDirsExpanded, entries) {
        if (allDirsExpanded != true) return@LaunchedEffect
        for (entry in entries) {
            if (entry.isDirectory) expandedDirs[entry.relativePath] = true
        }
    }

    fun isVisible(entry: VaultTreeEntry): Boolean {
        var parent = entry.parentPath
        while (parent != null) {
            if (expandedDirs[parent] != true) return false
            parent = entryByPath[parent]?.parentPath
        }
        return true
    }

    val visibleEntries by remember(entries, expandedDirs, entryFilter) {
        derivedStateOf {
            entries
                .filter(::isVisible)
                .filter { entry ->
                    if (entry.isDirectory) true else entryFilter?.invoke(entry) != false
                }
        }
    }

    val sheetModifier =
        modifier
            .then(if (sheetWidth == Dp.Unspecified) Modifier.fillMaxWidth() else Modifier.width(sheetWidth))
            .fillMaxHeight()

    val bodyModifier =
        Modifier
            .fillMaxSize()
            .then(if (useSystemInsets) Modifier.statusBarsPadding().navigationBarsPadding() else Modifier)
            .padding(contentPadding)

    ModalDrawerSheet(
        modifier = sheetModifier,
        windowInsets = WindowInsets(0, 0, 0, 0),
        drawerContainerColor = drawerContainerColor,
        drawerTonalElevation = 0.dp,
    ) {
        Column(
            modifier = bodyModifier,
        ) {
            if (showHeader) {
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = headerMinHeight)
                            .padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.vault_drawer_title),
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }

            val rootLoading = (loadingDirs[""] == true) && entries.isEmpty()
            if (rootLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                }
                return@ModalDrawerSheet
            }

            if (errorText != null) {
                Text(
                    text = errorText.orEmpty(),
                    modifier = Modifier.padding(16.dp),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
                return@ModalDrawerSheet
            }

            val listArrangement =
                if (listItemSpacing > 0.dp) {
                    Arrangement.spacedBy(listItemSpacing)
                } else {
                    Arrangement.Top
                }
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = listContentPadding,
                verticalArrangement = listArrangement,
            ) {
                if (visibleEntries.isEmpty()) {
                    item {
                        Text(
                            text = stringResource(R.string.vault_drawer_empty),
                            modifier = Modifier.padding(start = 8.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                items(visibleEntries, key = { it.relativePath }) { entry ->
                    val indent = (entry.depth * 8).dp
                    val isExpanded = expandedDirs[entry.relativePath] == true
                    val isDirLoading = entry.isDirectory && (loadingDirs[entry.relativePath] == true)
                    val isMarkdownDoc = !entry.isDirectory && entry.name.endsWith(".md", ignoreCase = true)
                    val isPdf = !entry.isDirectory && entry.name.endsWith(".pdf", ignoreCase = true)
                    val isDrawing = !entry.isDirectory && entry.name.endsWith(".zhixud", ignoreCase = true)
                    val isImage =
                        !entry.isDirectory &&
                            run {
                                val ext =
                                    entry.name
                                        .lowercase()
                                        .substringAfterLast('.', missingDelimiterValue = "")
                                if (
                                    ext in
                                        setOf(
                                            "png",
                                            "jpg",
                                            "jpeg",
                                            "webp",
                                            "gif",
                                            "bmp",
                                            "heic",
                                            "heif",
                                            "tif",
                                            "tiff",
                                            "svg",
                                            "avif",
                                        )
                                ) {
                                    true
                                } else {
                                    val uri = entry.uri ?: return@run false
                                    context.contentResolver.getType(uri)?.startsWith("image/") == true
                                }
                            }
                    val displayName =
                        if (entry.isDirectory) {
                            entry.name
                        } else {
                            when {
                                isMarkdownDoc -> entry.name.removeSuffix(".md")
                                isPdf -> entry.name.removeSuffix(".pdf")
                                isImage -> entry.name.substringBeforeLast('.', missingDelimiterValue = entry.name)
                                isDrawing -> entry.name.substringBeforeLast('.', missingDelimiterValue = entry.name)
                                else -> entry.name
                            }
                        }

                    val selectionMode = enableMultiSelect && selectedEntryUris.isNotEmpty()
                    val uriStr = entry.uri?.toString()
                    val selected = uriStr != null && uriStr in selectedEntryUris
                    val activeDirSelected =
                        !selectionMode &&
                            entry.isDirectory &&
                            !activeDirectoryRelativePath.isNullOrBlank() &&
                            entry.relativePath == activeDirectoryRelativePath

                    val borderModifier =
                        if (activeDirSelected) {
                            Modifier.border(
                                width = 1.dp,
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.85f),
                                shape = itemShape,
                            )
                        } else {
                            Modifier
                        }

                    val actionButtonSize = if (useDocumentListStyle) 30.dp else 34.dp

                    val onEntryClick: () -> Unit = {
                        if (selectionMode && uriStr != null) {
                            onToggleEntrySelection(uriStr)
                        } else if (entry.isDirectory) {
                            val dirPath = entry.relativePath
                            onActiveDirectoryChange(if (activeDirSelected) null else dirPath)
                            if (!isDirLoading) {
                                if (!isExpanded && loadedDirs[dirPath] != true) {
                                    expandedDirs[dirPath] = true
                                    scope.launch { reloadDir(dirPath) }
                                } else {
                                    expandedDirs[dirPath] = !isExpanded
                                }
                            }
                        } else {
                            entry.uri?.toString()?.let(onOpenDoc)
                            onCloseDrawer()
                        }
                    }

                    val onEntryLongClick =
                        if (enableMultiSelect && uriStr != null) {
                            { onToggleEntrySelection(uriStr) }
                        } else {
                            null
                        }

                    val entryRowContent: @Composable RowScope.() -> Unit = {
                        Spacer(modifier = Modifier.width(indent))
                        if (entry.isDirectory) {
                            Icon(
                                painter = painterResource(if (isExpanded) Ionicons.ChevronDown else Ionicons.ChevronForward),
                                contentDescription = null,
                                modifier = Modifier.size(itemChevronSize),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Icon(
                                painter = painterResource(Ionicons.Vault),
                                contentDescription = null,
                                modifier = Modifier.size(itemIconSize),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        } else {
                            Spacer(modifier = Modifier.size(itemChevronSize))
                            Icon(
                                painter =
                                    painterResource(
                                        when {
                                            isImage -> Ionicons.ImageOutline
                                            isPdf -> Ionicons.DocumentOutline
                                            isDrawing -> R.drawable.ic_hero_paint_brush
                                            else -> Ionicons.DocumentText
                                        },
                                    ),
                                contentDescription = null,
                                modifier = Modifier.size(itemIconSize),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Text(
                            text = displayName,
                            style = itemTextStyle,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        if (entry.isDirectory) {
                            ZhixuIconButton(
                                onClick = {
                                    newDocTargetDir = entry
                                    newDocName = context.getString(R.string.new_doc_default_title)
                                    showNewDocDialog = true
                                },
                                enabled = !isDirLoading && !selectionMode,
                                modifier = Modifier.size(actionButtonSize),
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Add,
                                    contentDescription = stringResource(R.string.action_create),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(20.dp),
                                )
                            }
                        }
                        ZhixuIconButton(
                            onClick = {
                                if (selectionMode && uriStr != null) {
                                    onToggleEntrySelection(uriStr)
                                } else {
                                    selectedEntry = entry
                                    showEntryMenu = true
                                }
                            },
                            modifier = Modifier.size(actionButtonSize),
                        ) {
                            if (selectionMode && uriStr != null) {
                                Icon(
                                    painter = painterResource(if (selected) Ionicons.CheckmarkCircle else Ionicons.SquareOutline),
                                    contentDescription = null,
                                    tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(20.dp),
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Outlined.MoreVert,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(20.dp),
                                )
                            }
                        }
                        if (entry.isDirectory && isDirLoading) {
                            CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                        }
                    }

                    if (useDocumentListStyle) {
                        val containerColor =
                            when {
                                selected -> MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)
                                activeDirSelected -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.75f)
                                else -> MaterialTheme.colorScheme.surface
                            }
                        Surface(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = itemMinHeight)
                                    .then(borderModifier)
                                    .combinedClickable(
                                        onClick = onEntryClick,
                                        onLongClick = onEntryLongClick,
                                    ),
                            color = containerColor,
                            shape = itemShape,
                            tonalElevation = 0.dp,
                            shadowElevation = 0.dp,
                        ) {
                            Row(
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(documentItemPadding),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                content = entryRowContent,
                            )
                        }
                    } else {
                        Row(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = itemMinHeight)
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                                    .clip(itemShape)
                                    .background(
                                        when {
                                            selected -> MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
                                            activeDirSelected -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.75f)
                                            else -> Color.Transparent
                                        },
                                    )
                                    .then(borderModifier)
                                    .combinedClickable(
                                        onClick = onEntryClick,
                                        onLongClick = onEntryLongClick,
                                    )
                                    .padding(start = 0.dp, end = 6.dp, top = 4.dp, bottom = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            content = entryRowContent,
                        )
                    }
                }
            }
        }
    }

    val entryMenuSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    if (showEntryMenu && selectedEntry != null) {
        ModalBottomSheet(
            onDismissRequest = { showEntryMenu = false },
            sheetState = entryMenuSheetState,
            containerColor = MaterialTheme.colorScheme.background,
            tonalElevation = 0.dp,
        ) {
            VaultEntryActionsSheet(
                onRename = {
                    val entry = selectedEntry ?: return@VaultEntryActionsSheet
                    renameInput =
                        if (entry.isDirectory) entry.name
                        else entry.name.removeSuffix(".md").removeSuffix(".pdf").removeSuffix(".zhixud").ifBlank { entry.name }
                    showEntryMenu = false
                    showRenameDialog = true
                },
                onMove = {
                    showEntryMenu = false
                    android.widget.Toast
                        .makeText(context, context.getString(R.string.common_not_implemented), android.widget.Toast.LENGTH_SHORT)
                        .show()
                },
                onCopy = {
                    val entry = selectedEntry ?: return@VaultEntryActionsSheet
                    showEntryMenu = false
                    scope.launch {
                        if (entry.isDirectory) {
                            clipboard.setText(androidx.compose.ui.text.AnnotatedString(entry.relativePath))
                            android.widget.Toast
                                .makeText(context, context.getString(R.string.common_copied), android.widget.Toast.LENGTH_SHORT)
                                .show()
                            return@launch
                        }
                        val uri = entry.uri ?: return@launch
                        val payload =
                            if (entry.name.endsWith(".md", ignoreCase = true)) {
                                repository.readText(uri)
                            } else {
                                uri.toString()
                            }
                        clipboard.setText(androidx.compose.ui.text.AnnotatedString(payload))
                        android.widget.Toast
                            .makeText(context, context.getString(R.string.common_copied), android.widget.Toast.LENGTH_SHORT)
                            .show()
                    }
                },
                onAddToDesktop = {
                    showEntryMenu = false
                    android.widget.Toast
                        .makeText(context, context.getString(R.string.common_not_implemented), android.widget.Toast.LENGTH_SHORT)
                        .show()
                },
                onDelete = {
                    showEntryMenu = false
                    showDeleteDialog = true
                },
            )
        }
    }

    if (showRenameDialog && selectedEntry != null) {
        AlertDialog(
            onDismissRequest = {
                showRenameDialog = false
                selectedEntry = null
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
                        val entry = selectedEntry ?: return@TextButton
                        val desiredName = renameInput.trim()
                        if (desiredName.isBlank()) return@TextButton
                        val parentDir = entry.parentPath ?: ""
                        val uri = entry.uri
                        showRenameDialog = false
                        selectedEntry = null
                        if (uri == null) {
                            android.widget.Toast
                                .makeText(context, context.getString(R.string.editor_rename_failed_generic), android.widget.Toast.LENGTH_SHORT)
                                .show()
                            return@TextButton
                        }
                        scope.launch {
                            val ok =
                                runCatching {
                                    if (entry.isDirectory) {
                                        repository.renameDirectory(uri, desiredName) ?: return@runCatching false
                                        repository.refreshDirIndexForDirectory(vaultRootUri, parentRelativePath = parentDir)
                                        expandedDirs.clear()
                                        reloadDir("")
                                    } else {
                                        if (entry.name.endsWith(".md", ignoreCase = true)) {
                                            val renamedDoc = repository.renameDoc(uri, desiredName) ?: return@runCatching false
                                            onDocListMutated(DocListMutation.Renamed(oldUri = uri, newUri = renamedDoc))
                                        } else {
                                            repository.renameFile(uri, desiredName) ?: return@runCatching false
                                        }
                                        repository.refreshDirIndexForDirectory(vaultRootUri, parentRelativePath = parentDir)
                                        reloadDir(parentDir)
                                    }
                                    true
                                }.getOrElse { false }
                            if (!ok) {
                                android.widget.Toast
                                    .makeText(context, context.getString(R.string.editor_rename_failed_generic), android.widget.Toast.LENGTH_SHORT)
                                    .show()
                            }
                        }
                    },
                ) { Text(stringResource(R.string.action_rename)) }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showRenameDialog = false
                        selectedEntry = null
                    },
                ) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }

    if (showDeleteDialog && selectedEntry != null) {
        AlertDialog(
            onDismissRequest = {
                showDeleteDialog = false
                selectedEntry = null
            },
            title = { Text(stringResource(R.string.action_delete)) },
            text = { Text(text = selectedEntry?.name.orEmpty()) },
            confirmButton = {
                TextButton(
                    onClick = {
                        val entry = selectedEntry ?: return@TextButton
                        val uri = entry.uri ?: return@TextButton
                        val parentDir = entry.parentPath ?: ""
                        scope.launch {
                            val ok =
                                runCatching {
                                    if (entry.isDirectory) {
                                        val deleted = repository.deleteEntry(uri)
                                        if (!deleted) return@runCatching false
                                        onDocListMutated(DocListMutation.Deleted(docUri = uri))
                                        repository.refreshDirIndexForDirectory(vaultRootUri, parentRelativePath = parentDir)
                                        expandedDirs.clear()
                                        reloadDir("")
                                    } else {
                                        if (entry.name.endsWith(".md", ignoreCase = true)) {
                                            val deleted = repository.deleteDoc(uri)
                                            if (deleted) onDocListMutated(DocListMutation.Deleted(docUri = uri))
                                            if (!deleted) return@runCatching false
                                        } else {
                                            val deleted = repository.deleteEntry(uri)
                                            if (deleted) onDocListMutated(DocListMutation.Deleted(docUri = uri))
                                            if (!deleted) return@runCatching false
                                        }
                                        repository.refreshDirIndexForDirectory(vaultRootUri, parentRelativePath = parentDir)
                                        reloadDir(parentDir)
                                    }
                                    true
                                }.getOrElse { false }
                            if (!ok) {
                                android.widget.Toast
                                    .makeText(context, context.getString(R.string.editor_delete_failed), android.widget.Toast.LENGTH_SHORT)
                                    .show()
                            }
                            showDeleteDialog = false
                            selectedEntry = null
                        }
                    },
                ) { Text(stringResource(R.string.action_delete)) }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showDeleteDialog = false
                        selectedEntry = null
                    },
                ) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }

    if (showNewDocDialog && newDocTargetDir != null) {
        AlertDialog(
            onDismissRequest = {
                showNewDocDialog = false
                newDocTargetDir = null
            },
            title = { Text(stringResource(R.string.new_doc_title)) },
            text = {
                ZhixuTextField(
                    value = newDocName,
                    onValueChange = { newDocName = it },
                    singleLine = true,
                    placeholder = { Text(stringResource(R.string.field_file_name)) },
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val dir = newDocTargetDir ?: return@TextButton
                        val dirUri = dir.uri ?: return@TextButton
                        val fileName = newDocName.trim()
                        if (fileName.isBlank()) return@TextButton
                        showNewDocDialog = false
                        newDocTargetDir = null
                        scope.launch {
                            val created =
                                runCatching { repository.createDocInDirectory(vaultRootUri, dirUri, fileName) }
                                    .getOrElse { e ->
                                        android.widget.Toast
                                            .makeText(
                                                context,
                                                context.getString(R.string.new_doc_error_create_failed, e.message ?: e.javaClass.simpleName),
                                                android.widget.Toast.LENGTH_SHORT,
                                            ).show()
                                        return@launch
                                    }
                                    ?: run {
                                        android.widget.Toast
                                            .makeText(
                                                context,
                                                context.getString(R.string.new_doc_error_create_failed, "unknown"),
                                                android.widget.Toast.LENGTH_SHORT,
                                            ).show()
                                        return@launch
                                    }
                            val title = created.name.removeSuffix(".md").trim().ifBlank { context.getString(R.string.new_doc_default_title) }
                            val wrote =
                                runCatching {
                                    repository.writeText(created.uri, "# $title\n\n")
                                }.isSuccess
                            if (!wrote) {
                                android.widget.Toast
                                    .makeText(context, context.getString(R.string.new_doc_error_write_failed), android.widget.Toast.LENGTH_SHORT)
                                    .show()
                            }
                            runCatching { repository.indexDocUri(created.uri) }
                            runCatching { repository.refreshDirIndexForDirectory(vaultRootUri, parentRelativePath = dir.relativePath) }
                            runCatching { reloadDir(dir.relativePath) }
                            onDocListMutated(DocListMutation.Created(created))
                            onOpenDoc(created.uri.toString())
                        }
                    },
                ) { Text(stringResource(R.string.action_create)) }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showNewDocDialog = false
                        newDocTargetDir = null
                    },
                ) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }
}

@Composable
private fun VaultEntryActionsSheet(
    onRename: () -> Unit,
    onMove: () -> Unit,
    onCopy: () -> Unit,
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
            VaultEntryQuickAction(
                title = stringResource(R.string.action_rename),
                iconRes = Ionicons.TextOutline,
                onClick = onRename,
                modifier = Modifier.weight(1f),
            )
            VaultEntryQuickAction(
                title = stringResource(R.string.action_move),
                iconRes = Ionicons.ArrowForward,
                onClick = onMove,
                modifier = Modifier.weight(1f),
            )
            VaultEntryQuickAction(
                title = stringResource(R.string.common_copy),
                iconRes = Ionicons.CopyOutline,
                onClick = onCopy,
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
                VaultEntryActionRow(
                    title = stringResource(R.string.action_add_to_desktop),
                    iconRes = Ionicons.ArrowUpCircleOutline,
                    onClick = onAddToDesktop,
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f))
                VaultEntryActionRow(
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
private fun VaultEntryQuickAction(
    title: String,
    iconRes: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
        modifier = modifier,
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onClick)
                    .padding(vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(
                painter = painterResource(iconRes),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(26.dp),
            )
            Spacer(modifier = Modifier.size(10.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun VaultEntryActionRow(
    title: String,
    iconRes: Int,
    iconTint: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurface,
    onClick: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(horizontal = 18.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
        )
        Spacer(modifier = Modifier.weight(1f))
        Icon(
            painter = painterResource(iconRes),
            contentDescription = null,
            tint = iconTint,
            modifier = Modifier.size(22.dp),
        )
    }
}
