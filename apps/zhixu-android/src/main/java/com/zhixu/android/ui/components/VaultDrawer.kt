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
import androidx.compose.runtime.rememberCoroutineScope
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
    val scope = rememberCoroutineScope()
    val initialCache = remember(vaultRootUri) { VaultDrawerCache.get(vaultRootUri) }

    var entries by remember(vaultRootUri) { mutableStateOf(initialCache?.entries ?: emptyList()) }
    var errorText by remember(vaultRootUri) { mutableStateOf<String?>(null) }
    var cacheUpdatedAtMs by remember(vaultRootUri) { mutableStateOf(initialCache?.updatedAtMs ?: 0L) }
    var handledRefreshToken by remember(vaultRootUri) { mutableStateOf(initialCache?.refreshToken ?: 0L) }

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
            if (key.isBlank()) {
                errorText = null
                val loaded =
                    runCatching {
                        repository.listVaultChildrenEntries(
                            rootUri = vaultRootUri,
                            parentRelativePath = "",
                            includeNonMarkdownFiles = false,
                            includeHidden = false,
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
                        repository.listVaultChildrenEntries(
                            rootUri = vaultRootUri,
                            parentRelativePath = key,
                            includeNonMarkdownFiles = false,
                            includeHidden = false,
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
        val rel =
            when (m) {
                is DocListMutation.Created -> repository.computeRelativePath(vaultRootUri, m.doc.uri)
                is DocListMutation.Deleted -> repository.computeRelativePath(vaultRootUri, m.docUri)
                is DocListMutation.Renamed -> repository.computeRelativePath(vaultRootUri, m.newUri)
            } ?: run { persistCache(); return@LaunchedEffect }

        val parentDir = parentPathOf(rel) ?: ""
        if (loadedDirs[parentDir] == true) {
            reloadDir(parentDir)
        } else {
            persistCache()
        }
    }

    val entryByPath = remember(entries) { entries.associateBy { it.relativePath } }

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
                    val isDirLoading = entry.isDirectory && (loadingDirs[entry.relativePath] == true)
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
                                        val dirPath = entry.relativePath
                                        if (isDirLoading) return@clickable
                                        if (!isExpanded && loadedDirs[dirPath] != true) {
                                            expandedDirs[dirPath] = true
                                            scope.launch { reloadDir(dirPath) }
                                        } else {
                                            expandedDirs[dirPath] = !isExpanded
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
                            Icon(
                                painter = painterResource(if (isExpanded) Ionicons.ChevronDown else Ionicons.ChevronForward),
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
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
                        if (entry.isDirectory && isDirLoading) {
                            CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                        }
                    }
                }
            }
        }
    }
}
