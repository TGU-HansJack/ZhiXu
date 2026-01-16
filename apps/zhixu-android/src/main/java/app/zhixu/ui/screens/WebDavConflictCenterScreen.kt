package app.zhixu.ui.screens

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.zhixu.R
import app.zhixu.data.VaultRepository
import app.zhixu.data.WebDavConfig
import app.zhixu.data.WebDavConflictStrategy
import app.zhixu.sync.WebDavSyncEngine
import app.zhixu.sync.WebDavUnresolvedConflict
import app.zhixu.ui.Ionicons
import app.zhixu.ui.ZhixuTopBarIconSize
import app.zhixu.ui.components.DiffLine
import app.zhixu.ui.components.DiffOp
import app.zhixu.ui.components.LineDiff
import app.zhixu.ui.components.ZhixuIconButton
import app.zhixu.ui.components.ZhixuTopAppBar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private data class WebDavConflictHistoryEntry(
    val tsMs: Long,
    val path: String,
    val reason: String?,
    val conflictPath: String?,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WebDavConflictCenterScreen(
    contentPadding: PaddingValues,
    vaultRootUri: Uri?,
    repository: VaultRepository,
    webDavConfig: WebDavConfig,
    onBack: () -> Unit,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    val engine = remember(context, repository) { WebDavSyncEngine(context, repository) }

    var activeTab by remember { mutableStateOf(0) }
    var unresolvedLoading by remember { mutableStateOf(false) }
    var unresolved by remember { mutableStateOf<List<WebDavUnresolvedConflict>>(emptyList()) }
    var historyLoading by remember { mutableStateOf(false) }
    var history by remember { mutableStateOf<List<WebDavConflictHistoryEntry>>(emptyList()) }

    var selected by remember { mutableStateOf<WebDavUnresolvedConflict?>(null) }

    suspend fun loadUnresolved() {
        val root = vaultRootUri ?: return
        unresolvedLoading = true
        unresolved =
            runCatching { engine.listUnresolvedConflicts(root) }
                .getOrDefault(emptyList())
        unresolvedLoading = false
    }

    suspend fun loadHistory() {
        val root = vaultRootUri ?: return
        historyLoading = true
        history =
            withContext(Dispatchers.IO) {
                val uri = repository.resolveVaultFileUri(root, ".zhixu/sync/conflicts.jsonl") ?: return@withContext emptyList()
                val raw = runCatching { repository.readText(uri) }.getOrNull().orEmpty().trim()
                if (raw.isBlank()) return@withContext emptyList()
                val lines = raw.lines()
                val limited = if (lines.size <= 800) lines else lines.takeLast(800)
                limited.mapNotNull { line ->
                    val obj = runCatching { JSONObject(line) }.getOrNull() ?: return@mapNotNull null
                    val ts = obj.optLong("ts", 0L).coerceAtLeast(0L)
                    val path = obj.optString("path").orEmpty().trim().trimStart('/')
                    if (path.isBlank()) return@mapNotNull null
                    WebDavConflictHistoryEntry(
                        tsMs = ts,
                        path = path,
                        reason = obj.optString("reason").orEmpty().trim().ifBlank { null },
                        conflictPath = obj.optString("conflictPath").orEmpty().trim().ifBlank { null },
                    )
                }.sortedByDescending { it.tsMs }
            }
        historyLoading = false
    }

    LaunchedEffect(vaultRootUri) {
        loadUnresolved()
        loadHistory()
    }

    val sel = selected
    if (sel != null) {
        WebDavConflictDetailScreen(
            contentPadding = contentPadding,
            vaultRootUri = vaultRootUri,
            repository = repository,
            webDavConfig = webDavConfig,
            conflict = sel,
            engine = engine,
            onResolved = {
                scope.launch {
                    loadUnresolved()
                    loadHistory()
                    selected = null
                }
            },
            onDismiss = {
                scope.launch {
                    val root = vaultRootUri ?: return@launch
                    engine.dismissUnresolvedConflict(root, sel.path)
                    loadUnresolved()
                    selected = null
                }
            },
            onBack = { selected = null },
        )
        return
    }

    val dividerColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            Column {
                ZhixuTopAppBar(
                    containerColor = MaterialTheme.colorScheme.surface,
                    title = { Text(stringResource(R.string.webdav_conflict_center_title), style = MaterialTheme.typography.titleMedium) },
                    navigationIcon = {
                        ZhixuIconButton(onClick = onBack) {
                            Icon(
                                painter = painterResource(Ionicons.ArrowBack),
                                contentDescription = stringResource(R.string.action_back),
                                modifier = Modifier.size(ZhixuTopBarIconSize),
                            )
                        }
                    },
                    actions = {
                        TextButton(
                            enabled = vaultRootUri != null && !unresolvedLoading && !historyLoading,
                            onClick = {
                                scope.launch {
                                    loadUnresolved()
                                    loadHistory()
                                }
                            },
                        ) { Text(stringResource(R.string.action_refresh)) }
                    },
                )
                HorizontalDivider(color = dividerColor)
            }
        },
    ) { innerPadding ->
        Column(
            modifier =
                Modifier
                    .padding(contentPadding)
                    .padding(innerPadding)
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background),
        ) {
            TabRow(selectedTabIndex = activeTab) {
                Tab(
                    selected = activeTab == 0,
                    onClick = { activeTab = 0 },
                    text = { Text(stringResource(R.string.webdav_conflict_center_tab_unresolved)) },
                )
                Tab(
                    selected = activeTab == 1,
                    onClick = { activeTab = 1 },
                    text = { Text(stringResource(R.string.webdav_conflict_center_tab_history)) },
                )
            }

            when (activeTab) {
                0 -> {
                    if (vaultRootUri == null) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(text = stringResource(R.string.webdav_autosync_status_no_vault), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        return@Column
                    }

                    if (unresolvedLoading) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(text = stringResource(R.string.common_loading), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        return@Column
                    }

                    if (unresolved.isEmpty()) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(text = stringResource(R.string.webdav_conflict_center_empty), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        return@Column
                    }

                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        items(unresolved, key = { it.path }) { item ->
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                shape = MaterialTheme.shapes.extraLarge,
                            ) {
                                ListItem(
                                    modifier =
                                        Modifier
                                            .fillMaxWidth()
                                            .clickable { selected = item },
                                    headlineContent = { Text(item.path, maxLines = 2, overflow = TextOverflow.Ellipsis) },
                                    supportingContent = {
                                        val msg = listOfNotNull(reasonLabel(context, item.reason), formatEpochMs(item.createdAtMs)).joinToString(" · ")
                                        Text(msg, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                    },
                                    trailingContent = {
                                        Icon(
                                            painter = painterResource(Ionicons.ChevronForward),
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(18.dp),
                                        )
                                    },
                                )
                            }
                        }
                    }
                }

                else -> {
                    if (vaultRootUri == null) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(text = stringResource(R.string.webdav_autosync_status_no_vault), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        return@Column
                    }

                    if (historyLoading) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(text = stringResource(R.string.common_loading), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        return@Column
                    }

                    if (history.isEmpty()) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(text = "-", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        return@Column
                    }

                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        items(history) { item ->
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                shape = MaterialTheme.shapes.extraLarge,
                            ) {
                                ListItem(
                                    headlineContent = { Text(item.path, maxLines = 2, overflow = TextOverflow.Ellipsis) },
                                    supportingContent = {
                                        val msg = listOfNotNull(reasonLabel(context, item.reason), formatEpochMs(item.tsMs)).joinToString(" · ")
                                        Text(msg, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WebDavConflictDetailScreen(
    contentPadding: PaddingValues,
    vaultRootUri: Uri?,
    repository: VaultRepository,
    webDavConfig: WebDavConfig,
    conflict: WebDavUnresolvedConflict,
    engine: WebDavSyncEngine,
    onResolved: () -> Unit,
    onDismiss: () -> Unit,
    onBack: () -> Unit,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    var showOnlyChanges by remember { mutableStateOf(true) }

    var localText by remember { mutableStateOf<String?>(null) }
    var remoteText by remember { mutableStateOf<String?>(null) }
    var diffLoading by remember { mutableStateOf(false) }

    var resolving by remember { mutableStateOf(false) }
    var statusText by remember { mutableStateOf<String?>(null) }
    var confirmStrategy by remember { mutableStateOf<WebDavConflictStrategy?>(null) }

    BackHandler { onBack() }

    suspend fun readText(relativePath: String?): String? {
        val root = vaultRootUri ?: return null
        val rel = relativePath.orEmpty().trim().trimStart('/').replace('\\', '/')
        if (rel.isBlank()) return null
        val uri = repository.resolveVaultFileUri(root, rel) ?: return null
        val raw = runCatching { repository.readText(uri) }.getOrNull() ?: return null
        val maxChars = 200_000
        return if (raw.length > maxChars) raw.take(maxChars) + "\n…(truncated)" else raw
    }

    LaunchedEffect(vaultRootUri, conflict.path, conflict.localArtifactPath, conflict.remoteArtifactPath) {
        diffLoading = true
        val localCurrent = readText(conflict.path)
        val localArtifact = if (localCurrent == null) readText(conflict.localArtifactPath) else null
        localText = localCurrent ?: localArtifact
        remoteText = readText(conflict.remoteArtifactPath)
        diffLoading = false
    }

    val diffLines =
        remember(localText, remoteText) {
            val local = localText.orEmpty()
            val remote = remoteText.orEmpty()
            if (local.isBlank() && remote.isBlank()) {
                listOf(DiffLine(DiffOp.Equal, "No preview"))
            } else {
                runCatching { LineDiff.diff(oldText = remote, newText = local) }
                    .getOrElse { listOf(DiffLine(DiffOp.Equal, "Diff failed: ${it.javaClass.simpleName}")) }
            }
        }

    val dividerColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            Column {
                ZhixuTopAppBar(
                    containerColor = MaterialTheme.colorScheme.surface,
                    title = { Text(stringResource(R.string.webdav_conflict_center_detail_title), style = MaterialTheme.typography.titleMedium) },
                    navigationIcon = {
                        ZhixuIconButton(onClick = onBack) {
                            Icon(
                                painter = painterResource(Ionicons.ArrowBack),
                                contentDescription = stringResource(R.string.action_back),
                                modifier = Modifier.size(ZhixuTopBarIconSize),
                            )
                        }
                    },
                    actions = {
                        TextButton(onClick = { showOnlyChanges = !showOnlyChanges }) {
                            Text(
                                if (showOnlyChanges) stringResource(R.string.editor_history_show_all) else stringResource(R.string.editor_history_show_changes),
                            )
                        }
                    },
                )
                HorizontalDivider(color = dividerColor)
            }
        },
    ) { innerPadding ->
        Column(
            modifier =
                Modifier
                    .padding(contentPadding)
                    .padding(innerPadding)
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background)
                    .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = MaterialTheme.shapes.extraLarge,
            ) {
                Column(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(text = conflict.path, style = MaterialTheme.typography.bodyMedium)
                    Text(
                        text = listOfNotNull(reasonLabel(context, conflict.reason), formatEpochMs(conflict.createdAtMs)).joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (!statusText.isNullOrBlank()) {
                        Text(text = statusText!!, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            val enabledForResolve =
                vaultRootUri != null &&
                    webDavConfig.enabled &&
                    webDavConfig.baseUrl.trim().isNotBlank() &&
                    (webDavConfig.baseUrl.trim().startsWith("http://") || webDavConfig.baseUrl.trim().startsWith("https://"))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(enabled = !resolving, onClick = onDismiss) { Text(stringResource(R.string.webdav_conflict_center_dismiss)) }
                Spacer(modifier = Modifier.weight(1f))
                TextButton(enabled = enabledForResolve && !resolving, onClick = { confirmStrategy = WebDavConflictStrategy.KEEP_BOTH }) {
                    Text(stringResource(R.string.webdav_conflict_center_resolve_keep_both))
                }
                Button(
                    enabled = enabledForResolve && !resolving,
                    onClick = { confirmStrategy = WebDavConflictStrategy.LOCAL_WINS },
                    shape = RoundedCornerShape(8.dp),
                ) { Text(stringResource(R.string.webdav_conflict_center_resolve_local)) }
                Button(
                    enabled = enabledForResolve && !resolving,
                    onClick = { confirmStrategy = WebDavConflictStrategy.REMOTE_WINS },
                    shape = RoundedCornerShape(8.dp),
                ) { Text(stringResource(R.string.webdav_conflict_center_resolve_remote)) }
            }

            val strategy = confirmStrategy
            if (strategy != null) {
                AlertDialog(
                    onDismissRequest = { confirmStrategy = null },
                    confirmButton = {
                        TextButton(
                            enabled = !resolving,
                            onClick = {
                                confirmStrategy = null
                                val root = vaultRootUri ?: return@TextButton
                                scope.launch {
                                    resolving = true
                                    statusText = context.getString(R.string.webdav_syncing)
                                    val cfg = webDavConfig.copy(conflictStrategy = strategy)
                                    runCatching { engine.syncVaultPaths(root, cfg, setOf(conflict.path)) }
                                        .fold(
                                            onSuccess = {
                                                statusText = context.getString(R.string.webdav_conflict_center_resolved)
                                                resolving = false
                                                onResolved()
                                            },
                                            onFailure = { e ->
                                                statusText =
                                                    context.getString(
                                                        R.string.webdav_sync_failed,
                                                        e.message ?: e.javaClass.simpleName,
                                                    )
                                                resolving = false
                                            },
                                        )
                                }
                            },
                        ) { Text(stringResource(R.string.action_confirm)) }
                    },
                    dismissButton = {
                        TextButton(enabled = !resolving, onClick = { confirmStrategy = null }) { Text(stringResource(R.string.action_cancel)) }
                    },
                    title = { Text(stringResource(R.string.action_confirm)) },
                    text = { Text(stringResource(R.string.webdav_conflict_center_confirm_fmt, strategy.name)) },
                )
            }

            Text(text = stringResource(R.string.webdav_conflict_center_diff_title), style = MaterialTheme.typography.titleSmall)
            if (diffLoading) {
                Text(text = stringResource(R.string.common_loading), color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                val codeStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace)
                LazyColumn(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = 220.dp, max = 520.dp)
                            .background(Color(0xFFF7F7F7), RoundedCornerShape(8.dp))
                            .padding(vertical = 6.dp),
                ) {
                    items(diffLines.size) { idx ->
                        val line = diffLines[idx]
                        if (showOnlyChanges && line.op == DiffOp.Equal) return@items
                        val bg =
                            when (line.op) {
                                DiffOp.Insert -> Color(0xFF52C41A).copy(alpha = 0.18f)
                                DiffOp.Delete -> Color(0xFFFF4D4F).copy(alpha = 0.18f)
                                DiffOp.Equal -> Color.Transparent
                            }
                        val prefix =
                            when (line.op) {
                                DiffOp.Insert -> "+ "
                                DiffOp.Delete -> "- "
                                DiffOp.Equal -> "  "
                            }
                        val style =
                            when (line.op) {
                                DiffOp.Delete ->
                                    codeStyle.copy(
                                        color = Color(0xFFFF4D4F),
                                        textDecoration = TextDecoration.LineThrough,
                                    )
                                DiffOp.Insert -> codeStyle.copy(color = Color(0xFF237804))
                                DiffOp.Equal -> codeStyle.copy(color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.9f))
                            }
                        Box(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .background(bg)
                                    .padding(horizontal = 10.dp, vertical = 4.dp),
                        ) {
                            Text(text = prefix + line.text, style = style, fontSize = 12.sp)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))
            if (!conflict.localArtifactPath.isNullOrBlank() || !conflict.remoteArtifactPath.isNullOrBlank()) {
                Text(
                    text =
                        buildString {
                            if (!conflict.localArtifactPath.isNullOrBlank()) append("local: ${conflict.localArtifactPath}")
                            if (!conflict.remoteArtifactPath.isNullOrBlank()) {
                                if (isNotEmpty()) append("\n")
                                append("remote: ${conflict.remoteArtifactPath}")
                            }
                        },
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private fun formatEpochMs(ms: Long): String {
    if (ms <= 0L) return "-"
    return runCatching {
        Instant
            .ofEpochMilli(ms)
            .atZone(ZoneId.systemDefault())
            .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))
    }.getOrElse { ms.toString() }
}

private fun reasonLabel(context: android.content.Context, raw: String?): String? {
    val r = raw.orEmpty().trim()
    if (r.isBlank()) return null
    return when (r) {
        "both_changed" -> context.getString(R.string.webdav_conflict_reason_both_changed)
        "both_changed_no_base" -> context.getString(R.string.webdav_conflict_reason_both_changed)
        "local_vs_delete" -> context.getString(R.string.webdav_conflict_reason_local_vs_delete)
        "tombstoned_local" -> context.getString(R.string.webdav_conflict_reason_tombstoned_local)
        "tombstoned_remote" -> context.getString(R.string.webdav_conflict_reason_tombstoned_remote)
        "unresolved_conflict" -> context.getString(R.string.webdav_conflict_reason_unresolved)
        else -> r
    }
}

