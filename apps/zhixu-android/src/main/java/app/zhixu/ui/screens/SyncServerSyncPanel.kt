package app.zhixu.ui.screens

import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.zhixu.R
import app.zhixu.data.VaultRepository
import app.zhixu.sync.OfficialVaultSyncSummary
import app.zhixu.sync.SyncServerSyncTask
import app.zhixu.sync.SyncServerSyncTaskManager
import app.zhixu.sync.SyncServerSyncTaskOpState
import app.zhixu.sync.SyncServerSyncTaskStoreState
import app.zhixu.sync.SyncServerSyncTaskTrigger
import app.zhixu.sync.WebDavPlannedOpKind
import app.zhixu.ui.Ionicons
import app.zhixu.ui.ZhixuTopBarIconSize
import app.zhixu.ui.components.ZhixuIconButton
import app.zhixu.ui.components.ZhixuTopAppBar
import kotlinx.coroutines.launch

@Composable
fun SyncServerSyncPanel(
    vaultRootUri: Uri?,
    repository: VaultRepository,
    baseUrl: String,
    token: String,
    includeIndexSqlite: Boolean,
    syncReady: Boolean,
    syncNotReadyHint: String? = null,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    val manager = remember(context, repository) { SyncServerSyncTaskManager(context, repository) }
    val dividerColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)

    var state by remember { mutableStateOf<SyncServerSyncTaskStoreState?>(null) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    var showTaskDetail by remember { mutableStateOf(false) }
    var detailTask by remember { mutableStateOf<SyncServerSyncTask?>(null) }
    var historyExpanded by remember { mutableStateOf(false) }

    suspend fun reload() {
        val root = vaultRootUri ?: return
        loading = true
        error = null
        val loaded =
            runCatching { manager.load(root) }
                .onFailure { error = it.message ?: it.javaClass.simpleName }
                .getOrNull()
        state = loaded

        val detailId = detailTask?.id
        if (showTaskDetail && detailId != null && loaded != null) {
            val refreshed =
                loaded.current?.takeIf { it.id == detailId } ?:
                    loaded.history.firstOrNull { it.id == detailId }
            if (refreshed != null) {
                detailTask = refreshed
            }
        }
        loading = false
    }

    LaunchedEffect(vaultRootUri) {
        if (vaultRootUri == null) {
            state = null
            return@LaunchedEffect
        }
        reload()
    }

    fun isConfigUsable(): Boolean {
        if (!syncReady) return false
        val base = baseUrl.trim()
        if (base.isBlank()) return false
        if (!base.startsWith("http://") && !base.startsWith("https://")) return false
        if (token.isBlank()) return false
        return true
    }

    val current = state?.current
    val history = state?.history.orEmpty()
    val last = history.firstOrNull()

    val successColor = Color(0xFF2E7D32)
    val syncingColor = Color(0xFF1976D2)
    val warningColor = Color(0xFFF9A825)
    val disabledColor = MaterialTheme.colorScheme.error
    val neutralColor = MaterialTheme.colorScheme.onSurfaceVariant

    val isSyncRunning = current?.run != null && current.run.endedAtMs == 0L

    fun summaryCounts(summary: OfficialVaultSyncSummary?): Triple<Int, Int, Int> {
        val okCount = summary?.let { it.uploaded + it.downloaded + it.deletedRemote + it.deletedLocal } ?: 0
        val conflictsCount = summary?.conflicts ?: 0
        val failedCount = summary?.failed ?: 0
        return Triple(okCount, conflictsCount, failedCount)
    }

    val lastSummary = last?.run?.summary
    val (successCount, conflictCount, failedCount) = summaryCounts(lastSummary)

    val lastStatusText =
        when {
            !syncReady && !syncNotReadyHint.isNullOrBlank() -> syncNotReadyHint
            !syncReady -> stringResource(R.string.cloud_sync_status_not_configured)
            isSyncRunning -> stringResource(R.string.cloud_sync_state_syncing)
            last == null -> stringResource(R.string.webdav_sync_panel_last_status_none)
            last.run?.error.isNullOrBlank() && (lastSummary?.failed ?: 0) == 0 -> stringResource(R.string.webdav_sync_panel_last_status_ok)
            else -> stringResource(R.string.webdav_sync_panel_last_status_failed)
        }

    val statusKind: String =
        when {
            !syncReady -> "disabled"
            isSyncRunning -> "syncing"
            last == null -> "none"
            last.run?.error.isNullOrBlank() && (lastSummary?.failed ?: 0) == 0 -> "ok"
            else -> "failed"
        }

    val statusIconRes =
        when (statusKind) {
            "disabled" -> Ionicons.CloudOff
            "syncing" -> Ionicons.CloudBackup
            "ok" -> Ionicons.CloudCheck
            "failed" -> Ionicons.CloudAlert
            else -> Ionicons.Cloud
        }
    val statusIconTint =
        when (statusKind) {
            "disabled" -> disabledColor
            "syncing" -> syncingColor
            "ok" -> successColor
            "failed" -> warningColor
            else -> neutralColor
        }
    val statusTextColor =
        when (statusKind) {
            "disabled" -> disabledColor
            "syncing" -> syncingColor
            "ok" -> successColor
            "failed" -> disabledColor
            else -> neutralColor
        }

    ListItem(
        modifier = Modifier.fillMaxWidth(),
        leadingContent = {
            Icon(
                painter = painterResource(statusIconRes),
                contentDescription = null,
                tint = statusIconTint,
                modifier = Modifier.size(20.dp),
            )
        },
        headlineContent = { Text(stringResource(R.string.webdav_sync_panel_last_status_title)) },
        supportingContent = {
            when {
                loading -> {
                    Text(stringResource(R.string.common_loading), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }

                !error.isNullOrBlank() -> {
                    Text(error!!, color = MaterialTheme.colorScheme.error, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }

                last == null -> {
                    Text("-", maxLines = 1, overflow = TextOverflow.Ellipsis)
                }

                else -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(successCount.toString(), color = successColor, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(conflictCount.toString(), color = warningColor, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(failedCount.toString(), color = disabledColor, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        },
        trailingContent = {
            Text(
                text = lastStatusText,
                color = statusTextColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
    )

    HorizontalDivider(color = dividerColor)

    val currentSubtitle =
        if (current == null) {
            stringResource(R.string.webdav_sync_panel_no_current_task)
        } else {
            stringResource(R.string.webdav_task_operations_count_fmt, current.operations.size)
        }

    val canGenerateTask = vaultRootUri != null && isConfigUsable() && !loading

    ListItem(
        modifier =
            Modifier
                .fillMaxWidth()
                .then(
                    if (current != null) {
                        Modifier.clickable {
                            detailTask = current
                            showTaskDetail = true
                        }
                    } else {
                        Modifier
                    },
                ),
        leadingContent = {
            Icon(
                painter = painterResource(Ionicons.ClipboardList),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
        },
        headlineContent = { Text(stringResource(R.string.webdav_sync_panel_current_task_title)) },
        supportingContent = { Text(currentSubtitle, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        trailingContent = {
            if (current == null) {
                AssistChip(
                    enabled = canGenerateTask,
                    onClick = {
                        val root = vaultRootUri ?: return@AssistChip
                        scope.launch {
                            runCatching {
                                manager.generateTask(
                                    rootUri = root,
                                    baseUrl = baseUrl,
                                    token = token,
                                    includeIndexSqlite = includeIndexSqlite,
                                    trigger = SyncServerSyncTaskTrigger.MANUAL,
                                )
                                reload()
                                val refreshed = manager.load(root).current
                                if (refreshed != null) {
                                    detailTask = refreshed
                                    showTaskDetail = true
                                }
                            }.onFailure { error = it.message ?: it.javaClass.simpleName }
                        }
                    },
                    shape = androidx.compose.foundation.shape.CircleShape,
                    border = null,
                    colors =
                        AssistChipDefaults.assistChipColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            labelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        ),
                    label = { Text(stringResource(R.string.webdav_sync_panel_generate_task), maxLines = 1) },
                )
            } else {
                Icon(
                    painter = painterResource(Ionicons.ChevronForward),
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
            }
        },
    )

    HorizontalDivider(color = dividerColor)

    ListItem(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable { historyExpanded = !historyExpanded },
        leadingContent = {
            Icon(
                painter = painterResource(Ionicons.ListTodo),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
        },
        headlineContent = { Text(stringResource(R.string.webdav_sync_panel_history_title)) },
        supportingContent = {
            if (history.isEmpty()) {
                Text(
                    text = stringResource(R.string.webdav_sync_panel_history_empty),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            } else {
                Text(
                    text = stringResource(R.string.webdav_sync_panel_history_subtitle_fmt, minOf(history.size, 5)),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        },
        trailingContent = {
            Icon(
                painter = painterResource(if (historyExpanded) Ionicons.ChevronDown else Ionicons.ChevronForward),
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
        },
    )

    if (historyExpanded) {
        val max = 5
        val shown = history.take(max)
        shown.forEach { t ->
            HorizontalDivider(color = dividerColor)
            val ended = t.run?.endedAtMs ?: 0L
            val timeText =
                if (ended > 0L) {
                    SyncServerSyncTaskManager.formatEpochMs(ended)
                } else {
                    SyncServerSyncTaskManager.formatEpochMs(t.createdAtMs)
                }
            val statusText =
                when {
                    t.run?.endedAtMs == 0L -> stringResource(R.string.cloud_sync_state_syncing)
                    t.run?.error.isNullOrBlank() && (t.run?.summary?.failed ?: 0) == 0 -> stringResource(R.string.webdav_sync_panel_last_status_ok)
                    else -> stringResource(R.string.webdav_sync_panel_last_status_failed)
                }
            ListItem(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .clickable {
                            detailTask = t
                            showTaskDetail = true
                        },
                headlineContent = {
                    Text(
                        text = timeText,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                supportingContent = {
                    Text(
                        text = statusText,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                trailingContent = {
                    Icon(
                        painter = painterResource(Ionicons.ChevronForward),
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                },
            )
        }
    }

    val detail = detailTask
    if (showTaskDetail && detail != null) {
        val isCurrent = current?.id == detail.id
        SyncServerTaskDetailDialog(
            manager = manager,
            vaultRootUri = vaultRootUri,
            task = detail,
            isCurrent = isCurrent,
            baseUrl = baseUrl,
            token = token,
            includeIndexSqlite = includeIndexSqlite,
            syncReady = syncReady,
            syncNotReadyHint = syncNotReadyHint,
            onDismiss = { showTaskDetail = false },
            onReload = { scope.launch { reload() } },
        )
    }
}

@Composable
private fun SyncServerTaskDetailDialog(
    manager: SyncServerSyncTaskManager,
    vaultRootUri: Uri?,
    task: SyncServerSyncTask,
    isCurrent: Boolean,
    baseUrl: String,
    token: String,
    includeIndexSqlite: Boolean,
    syncReady: Boolean,
    syncNotReadyHint: String?,
    onDismiss: () -> Unit,
    onReload: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val dividerColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)

    var working by remember { mutableStateOf(false) }
    var localError by remember { mutableStateOf<String?>(null) }

    fun canExecute(): Boolean {
        if (!isCurrent) return false
        if (!syncReady) return false
        if (vaultRootUri == null) return false
        if (token.isBlank()) return false
        val base = baseUrl.trim()
        if (base.isBlank()) return false
        if (!base.startsWith("http://") && !base.startsWith("https://")) return false
        if (working) return false
        return true
    }

    Dialog(
        onDismissRequest = { if (!working) onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background,
        ) {
            Scaffold(
                contentWindowInsets = WindowInsets(0, 0, 0, 0),
                containerColor = MaterialTheme.colorScheme.background,
                topBar = {
                    Column {
                        ZhixuTopAppBar(
                            containerColor = MaterialTheme.colorScheme.surface,
                            title = { Text(stringResource(R.string.webdav_task_detail_title), style = MaterialTheme.typography.titleMedium) },
                            navigationIcon = {
                                ZhixuIconButton(
                                    onClick = onDismiss,
                                    enabled = !working,
                                ) {
                                    Icon(
                                        painter = painterResource(Ionicons.ArrowBack),
                                        contentDescription = stringResource(R.string.action_back),
                                        modifier = Modifier.size(ZhixuTopBarIconSize),
                                    )
                                }
                            },
                            actions = {
                                if (isCurrent) {
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                        AssistChip(
                                            enabled = vaultRootUri != null && !working,
                                            onClick = {
                                                val root = vaultRootUri ?: return@AssistChip
                                                scope.launch {
                                                    working = true
                                                    localError = null
                                                    runCatching {
                                                        manager.discardCurrentTask(root, reason = "discarded")
                                                        onReload()
                                                        onDismiss()
                                                    }.onFailure { e ->
                                                        localError = e.message ?: e.javaClass.simpleName
                                                    }
                                                    working = false
                                                }
                                            },
                                            shape = CircleShape,
                                            border = null,
                                            colors =
                                                AssistChipDefaults.assistChipColors(
                                                    containerColor = MaterialTheme.colorScheme.errorContainer,
                                                    labelColor = MaterialTheme.colorScheme.onErrorContainer,
                                                ),
                                            label = { Text(stringResource(R.string.webdav_sync_panel_discard_task), maxLines = 1) },
                                        )

                                        AssistChip(
                                            enabled = canExecute(),
                                            onClick = {
                                                val root = vaultRootUri ?: return@AssistChip
                                                scope.launch {
                                                    working = true
                                                    localError = null
                                                    runCatching {
                                                        manager.executeCurrentTask(
                                                            rootUri = root,
                                                            baseUrl = baseUrl,
                                                            token = token,
                                                            includeIndexSqlite = includeIndexSqlite,
                                                        )
                                                        onReload()
                                                        onDismiss()
                                                    }.onFailure { e ->
                                                        localError = e.message ?: e.javaClass.simpleName
                                                    }
                                                    working = false
                                                }
                                            },
                                            shape = CircleShape,
                                            border = null,
                                            colors =
                                                AssistChipDefaults.assistChipColors(
                                                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                                                    labelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                                ),
                                            label = { Text(stringResource(R.string.webdav_task_execute), maxLines = 1) },
                                        )
                                    }
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
                            .padding(innerPadding)
                            .windowInsetsPadding(WindowInsets.navigationBars)
                            .fillMaxSize()
                            .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(
                        text = stringResource(R.string.webdav_task_created_at_fmt, SyncServerSyncTaskManager.formatEpochMs(task.createdAtMs)),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                    )
                    val ended = task.run?.endedAtMs ?: 0L
                    if (ended > 0L) {
                        Text(
                            text = stringResource(R.string.webdav_task_ended_at_fmt, SyncServerSyncTaskManager.formatEpochMs(ended)),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }

                    if (!syncReady && !syncNotReadyHint.isNullOrBlank()) {
                        Text(text = syncNotReadyHint, color = MaterialTheme.colorScheme.error)
                    }
                    if (!localError.isNullOrBlank()) {
                        Text(text = localError!!, color = MaterialTheme.colorScheme.error)
                    }

                    HorizontalDivider(color = dividerColor)

                    Text(
                        text = stringResource(R.string.webdav_task_operations_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )

                    LazyColumn(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .weight(1f, fill = true),
                        verticalArrangement = Arrangement.spacedBy(0.dp),
                    ) {
                        items(task.operations) { op ->
                            val isDelete = op.kind == WebDavPlannedOpKind.DELETE_LOCAL || op.kind == WebDavPlannedOpKind.DELETE_REMOTE
                            val statusText =
                                when (op.state) {
                                    SyncServerSyncTaskOpState.PENDING -> stringResource(R.string.webdav_task_op_state_pending)
                                    SyncServerSyncTaskOpState.SKIPPED -> stringResource(R.string.webdav_task_op_state_skipped)
                                    SyncServerSyncTaskOpState.DONE -> stringResource(R.string.webdav_task_op_state_done)
                                    SyncServerSyncTaskOpState.FAILED -> stringResource(R.string.webdav_task_op_state_failed)
                                }
                            ListItem(
                                headlineContent = {
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Text(op.kind.name, fontFamily = FontFamily.Monospace, fontSize = 12.sp)
                                        if (isDelete) {
                                            Text(
                                                text = stringResource(R.string.webdav_task_delete_risk),
                                                color = MaterialTheme.colorScheme.error,
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.SemiBold,
                                            )
                                        }
                                    }
                                },
                                supportingContent = {
                                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Text(op.path, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                        if (op.reason.isNotBlank()) {
                                            Text(op.reason, fontFamily = FontFamily.Monospace, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                        if (!op.error.isNullOrBlank()) {
                                            Text(op.error!!, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                                        }
                                        Text(statusText, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                                    }
                                },
                            )
                            HorizontalDivider(color = dividerColor)
                        }
                    }
                }
            }
        }
    }
}
