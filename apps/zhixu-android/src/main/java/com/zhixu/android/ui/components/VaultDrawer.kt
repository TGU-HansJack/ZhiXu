package com.zhixu.android.ui.components

import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.WindowInsets
import com.zhixu.android.R
import com.zhixu.android.data.VaultRepository
import com.zhixu.android.data.VaultTreeEntry
import com.zhixu.android.ui.Ionicons
import com.zhixu.android.ui.DocListMutation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private object VaultDrawerCache {
    private const val MaxEntries = 4
    private val lock = Any()
    private val cache =
        object : LinkedHashMap<String, Entry>(MaxEntries, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Entry>?): Boolean = size > MaxEntries
        }

    data class Entry(
        val entries: List<VaultTreeEntry>,
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
fun VaultDrawer(
    vaultRootUri: Uri,
    repository: VaultRepository,
    onOpenDoc: (String) -> Unit,
    onCloseDrawer: () -> Unit,
    isActive: Boolean,
    refreshToken: Long,
    mutation: DocListMutation?,
    modifier: Modifier = Modifier,
) {
    var entries by
        remember(vaultRootUri) {
            mutableStateOf(VaultDrawerCache.get(vaultRootUri)?.entries ?: emptyList())
        }
    var errorText by remember(vaultRootUri) { mutableStateOf<String?>(null) }
    var isLoading by remember(vaultRootUri) { mutableStateOf(false) }
    var cacheUpdatedAtMs by remember(vaultRootUri) { mutableStateOf(VaultDrawerCache.get(vaultRootUri)?.updatedAtMs ?: 0L) }
    var handledRefreshToken by remember(vaultRootUri) { mutableStateOf(VaultDrawerCache.get(vaultRootUri)?.refreshToken ?: 0L) }

    suspend fun reorderAndNormalize(next: List<VaultTreeEntry>): List<VaultTreeEntry> =
        withContext(Dispatchers.Default) {
            val entryByPath = next.associateBy { it.relativePath }
            val byParent = next.groupBy { it.parentPath }

            fun sortChildren(children: List<VaultTreeEntry>): List<VaultTreeEntry> =
                children.sortedWith(
                    compareByDescending<VaultTreeEntry> { it.isDirectory }
                        .thenBy { it.name.lowercase() },
                )

            val out = ArrayList<VaultTreeEntry>(next.size)

            fun walk(parentPath: String?, depth: Int) {
                val children = byParent[parentPath] ?: return
                for (child in sortChildren(children)) {
                    out += child.copy(depth = depth)
                    if (child.isDirectory) {
                        walk(child.relativePath, depth + 1)
                    }
                }
            }

            // Best-effort: only emit nodes reachable from root; if we detect orphans, fall back to the original list.
            walk(parentPath = null, depth = 0)
            if (out.size != next.size) next else out
        }

    suspend fun tryApplyMutation(current: List<VaultTreeEntry>, mutation: DocListMutation): List<VaultTreeEntry>? {
        val rel =
            when (mutation) {
                is DocListMutation.Created -> repository.computeRelativePath(vaultRootUri, mutation.doc.uri)
                is DocListMutation.Deleted -> repository.computeRelativePath(vaultRootUri, mutation.docUri)
                is DocListMutation.Renamed -> repository.computeRelativePath(vaultRootUri, mutation.oldUri)
            } ?: return null

        fun parentPathOf(path: String): String? {
            val normalized = path.replace('\\', '/').trimStart('/')
            val slash = normalized.lastIndexOf('/')
            if (slash < 0) return null
            return normalized.substring(0, slash + 1)
        }

        val currentByPath = current.associateBy { it.relativePath }
        val targetParent = parentPathOf(rel)
        if (targetParent != null && currentByPath[targetParent]?.isDirectory != true) return null

        val next =
            when (mutation) {
                is DocListMutation.Created -> {
                    val createdRel = repository.computeRelativePath(vaultRootUri, mutation.doc.uri) ?: return null
                    val name = mutation.doc.name
                    val p = parentPathOf(createdRel)
                    if (p != null && currentByPath[p]?.isDirectory != true) return null
                    val depth = p?.count { it == '/' } ?: 0
                    val newEntry =
                        VaultTreeEntry(
                            relativePath = createdRel,
                            name = name,
                            uri = mutation.doc.uri,
                            isDirectory = false,
                            parentPath = p,
                            depth = depth,
                        )
                    val without = current.filterNot { it.relativePath == createdRel }
                    without + newEntry
                }

                is DocListMutation.Deleted -> {
                    current.filterNot { it.relativePath == rel }
                }

                is DocListMutation.Renamed -> {
                    val oldRel = repository.computeRelativePath(vaultRootUri, mutation.oldUri) ?: return null
                    val newRel = repository.computeRelativePath(vaultRootUri, mutation.newUri) ?: return null
                    val name = newRel.substringAfterLast('/')
                    val p = parentPathOf(newRel)
                    if (p != null && currentByPath[p]?.isDirectory != true) return null
                    val depth = p?.count { it == '/' } ?: 0
                    val newEntry =
                        VaultTreeEntry(
                            relativePath = newRel,
                            name = name,
                            uri = mutation.newUri,
                            isDirectory = false,
                            parentPath = p,
                            depth = depth,
                        )
                    val without = current.filterNot { it.relativePath == oldRel || it.relativePath == newRel }
                    without + newEntry
                }
            }

        return reorderAndNormalize(next)
    }

    LaunchedEffect(vaultRootUri, isActive, refreshToken) {
        if (!isActive) return@LaunchedEffect
        val now = System.currentTimeMillis()
        val cacheAgeMs = if (cacheUpdatedAtMs > 0L) (now - cacheUpdatedAtMs) else Long.MAX_VALUE
        val tokenChanged = refreshToken != handledRefreshToken
        if (tokenChanged && entries.isNotEmpty()) {
            val m = mutation
            val applied = if (m != null) runCatching { tryApplyMutation(entries, m) }.getOrNull() else null
            if (applied != null) {
                entries = applied
                handledRefreshToken = refreshToken
                cacheUpdatedAtMs = now
                VaultDrawerCache.put(
                    vaultRootUri,
                    VaultDrawerCache.Entry(entries = applied, updatedAtMs = now, refreshToken = refreshToken),
                )
                return@LaunchedEffect
            }
        }

        val shouldRefresh = entries.isEmpty() || tokenChanged || cacheAgeMs > 30 * 60 * 1000L
        if (!shouldRefresh) return@LaunchedEffect

        isLoading = true
        errorText = null
        val loaded =
            runCatching {
                repository.listVaultTreeEntries(
                    rootUri = vaultRootUri,
                    includeNonMarkdownFiles = false,
                    includeHidden = false,
                )
            }.getOrElse { e ->
                errorText = e.message ?: e.javaClass.simpleName
                emptyList()
            }
        entries = loaded
        handledRefreshToken = refreshToken
        cacheUpdatedAtMs = now
        VaultDrawerCache.put(
            vaultRootUri,
            VaultDrawerCache.Entry(entries = loaded, updatedAtMs = now, refreshToken = refreshToken),
        )
        isLoading = false
    }

    val expandedDirs = remember(vaultRootUri) { mutableStateMapOf<String, Boolean>() }
    val entryByPath = remember(entries) { entries.associateBy { it.relativePath } }
    val childCountByParent = remember(entries) { entries.groupingBy { it.parentPath }.eachCount() }

    fun isVisible(entry: VaultTreeEntry): Boolean {
        var parent = entry.parentPath
        while (parent != null) {
            if (expandedDirs[parent] != true) return false
            parent = entryByPath[parent]?.parentPath
        }
        return true
    }

    val visibleEntries by remember(entries, expandedDirs) {
        derivedStateOf { entries.filter(::isVisible) }
    }

    ModalDrawerSheet(
        modifier = modifier.width(320.dp).fillMaxHeight(),
        windowInsets = WindowInsets(0, 0, 0, 0),
        drawerContainerColor = MaterialTheme.colorScheme.background,
        drawerTonalElevation = 0.dp,
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding(),
        ) {
            Text(
                text = stringResource(R.string.vault_drawer_title),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(start = 8.dp, end = 8.dp, top = 12.dp, bottom = 12.dp),
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            if (isLoading) {
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

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(vertical = 8.dp),
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
                    val indent = (entry.depth * 10).dp
                    val isExpanded = expandedDirs[entry.relativePath] == true
                    val hasChildren = (childCountByParent[entry.relativePath] ?: 0) > 0
                    val displayName =
                        if (entry.isDirectory) {
                            entry.name
                        } else {
                            entry.name.removeSuffix(".md")
                        }

                    Row(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .heightIn(min = 32.dp)
                                .clickable {
                                    if (entry.isDirectory) {
                                        if (hasChildren) {
                                            expandedDirs[entry.relativePath] = !isExpanded
                                        }
                                    } else {
                                        entry.uri?.toString()?.let(onOpenDoc)
                                        onCloseDrawer()
                                    }
                                }
                                .padding(start = 4.dp, end = 8.dp, top = 4.dp, bottom = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Spacer(modifier = Modifier.width(indent))
                        if (entry.isDirectory) {
                            if (hasChildren) {
                                Icon(
                                    painter = painterResource(if (isExpanded) Ionicons.ChevronDown else Ionicons.ChevronForward),
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            } else {
                                Spacer(modifier = Modifier.size(16.dp))
                            }
                            Icon(
                                painter = painterResource(Ionicons.Vault),
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        } else {
                            Spacer(modifier = Modifier.size(16.dp))
                            Icon(
                                painter = painterResource(Ionicons.DocumentText),
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Text(
                            text = displayName,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
    }
}
