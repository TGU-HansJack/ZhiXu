package app.zhixu.ui.screens

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
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
import androidx.compose.ui.draw.rotate
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
import app.zhixu.data.WebDavConfig
import app.zhixu.data.WebDavConflictStrategy
import app.zhixu.sync.WebDavPlannedOpKind
import app.zhixu.sync.WebDavSyncTask
import app.zhixu.sync.WebDavSyncTaskManager
import app.zhixu.sync.WebDavSyncTaskOpState
import app.zhixu.sync.WebDavSyncTaskStoreState
import app.zhixu.sync.WebDavSyncTaskTrigger
import app.zhixu.ui.Ionicons
import app.zhixu.ui.ZhixuTopBarIconSize
import app.zhixu.ui.components.ZhixuIconButton
import app.zhixu.ui.components.ZhixuTopAppBar
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WebDavSyncPanel(
    vaultRootUri: Uri?,
    repository: VaultRepository,
    webDavConfig: WebDavConfig,
    legacyLastSummaryText: String?,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    val manager = remember(context, repository) { WebDavSyncTaskManager(context, repository) }
    val dividerColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)

    var state by remember { mutableStateOf<WebDavSyncTaskStoreState?>(null) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    var showTaskDetail by remember { mutableStateOf(false) }
    var detailTask by remember { mutableStateOf<WebDavSyncTask?>(null) }
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

        // Keep the detail dialog in sync; otherwise in-dialog edits (skip / conflict resolution)
        // are persisted but won't reflect until the user re-enters the task page.
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
        if (!webDavConfig.enabled) return false
        val base = webDavConfig.baseUrl.trim()
        if (base.isBlank()) return false
        if (!base.startsWith("http://") && !base.startsWith("https://")) return false
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

    val lastStatusText =
        when {
            !webDavConfig.enabled -> stringResource(R.string.cloud_sync_status_disabled)
            isSyncRunning -> stringResource(R.string.webdav_syncing)
            last == null -> stringResource(R.string.webdav_sync_panel_last_status_none)
            last.run?.error.isNullOrBlank() && last.operations.none { it.state == WebDavSyncTaskOpState.FAILED } -> stringResource(R.string.webdav_sync_panel_last_status_ok)
            last.operations.any { it.state == WebDavSyncTaskOpState.DONE } -> stringResource(R.string.webdav_sync_panel_last_status_partial)
            else -> stringResource(R.string.webdav_sync_panel_last_status_failed)
        }

    val statusKind: String =
        when {
            !webDavConfig.enabled -> "disabled"
            isSyncRunning -> "syncing"
            last == null -> "none"
            last.run?.error.isNullOrBlank() && last.operations.none { it.state == WebDavSyncTaskOpState.FAILED } -> "ok"
            last.operations.any { it.state == WebDavSyncTaskOpState.DONE } -> "partial"
            else -> "failed"
        }

    val statusIconRes =
        when (statusKind) {
            "disabled" -> Ionicons.CloudOff
            "syncing" -> Ionicons.CloudBackup
            "ok" -> Ionicons.CloudCheck
            "partial" -> Ionicons.CloudAlert
            "failed" -> Ionicons.CloudAlert
            else -> Ionicons.Cloud
        }
    val statusIconTint =
        when (statusKind) {
            "disabled" -> disabledColor
            "syncing" -> syncingColor
            "ok" -> successColor
            "partial" -> warningColor
            "failed" -> warningColor
            else -> neutralColor
        }
    val statusTextColor =
        when (statusKind) {
            "disabled" -> disabledColor
            "syncing" -> syncingColor
            "ok" -> successColor
            "partial" -> warningColor
            "failed" -> disabledColor
            else -> neutralColor
        }

    val lastSummary = last?.run?.summary
    val successCount =
        lastSummary?.let { it.uploaded + it.downloaded + it.deletedRemote + it.deletedLocal }
            ?: last?.operations?.count { it.state == WebDavSyncTaskOpState.DONE && it.kind != WebDavPlannedOpKind.CONFLICT }
            ?: 0
    val conflictCount =
        lastSummary?.conflicts
            ?: last?.operations?.count { it.kind == WebDavPlannedOpKind.CONFLICT }
            ?: 0
    val failedCount =
        lastSummary?.failed
            ?: last?.operations?.count { it.state == WebDavSyncTaskOpState.FAILED }
            ?: 0

    val displayTimeMs =
        when {
            isSyncRunning -> current?.run?.startedAtMs ?: 0L
            else -> last?.run?.endedAtMs?.takeIf { it > 0L } ?: last?.createdAtMs ?: 0L
        }
    val displayTimeText = displayTimeMs.takeIf { it > 0L }?.let { WebDavSyncTaskManager.formatEpochMs(it) }

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
                        if (displayTimeText != null) {
                            Text(displayTimeText, color = neutralColor, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
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
            val totalOps = current.operations.size
            val unresolvedConflicts = current.operations.count { it.kind == WebDavPlannedOpKind.CONFLICT && it.resolution == null }
            stringResource(R.string.webdav_sync_panel_current_task_summary_fmt, totalOps, unresolvedConflicts)
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
                                manager.generateTask(root, webDavConfig, trigger = WebDavSyncTaskTrigger.MANUAL)
                                reload()
                                val refreshed = manager.load(root).current
                                if (refreshed != null) {
                                    detailTask = refreshed
                                    showTaskDetail = true
                                }
                            }.onFailure { error = it.message ?: it.javaClass.simpleName }
                        }
                    },
                    shape = CircleShape,
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
                painter = painterResource(Ionicons.ChevronDownLucide),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp).rotate(if (historyExpanded) 180f else 0f),
            )
        },
    )
    if (historyExpanded && history.isNotEmpty()) {
        HorizontalDivider(color = dividerColor)
        history.take(5).forEachIndexed { idx, t ->
            ListItem(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .clickable {
                            detailTask = t
                            showTaskDetail = true
                        },
                leadingContent = {
                    Icon(
                        painter = painterResource(Ionicons.TimeOutline),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp),
                    )
                },
                headlineContent = {
                    val title =
                        when (t.trigger) {
                            WebDavSyncTaskTrigger.MANUAL -> stringResource(R.string.webdav_sync_panel_task_trigger_manual)
                            WebDavSyncTaskTrigger.AUTO -> stringResource(R.string.webdav_sync_panel_task_trigger_auto)
                        }
                    Text(title)
                },
                supportingContent = {
                    val ended = t.run?.endedAtMs?.takeIf { it > 0L } ?: t.createdAtMs
                    val line =
                        buildString {
                            append(WebDavSyncTaskManager.formatEpochMs(ended))
                            if (!t.run?.error.isNullOrBlank()) {
                                append(" · ")
                                append(t.run?.error)
                            }
                        }
                    Text(line, maxLines = 1, overflow = TextOverflow.Ellipsis)
                },
            )
            if (idx != minOf(history.size, 5) - 1) HorizontalDivider(color = dividerColor)
        }
    }

    val show = showTaskDetail
    val task = detailTask
    if (show && task != null) {
        FullScreenDialog(onDismiss = { showTaskDetail = false }) {
            WebDavTaskDetailScreen(
                vaultRootUri = vaultRootUri,
                repository = repository,
                manager = manager,
                webDavConfig = webDavConfig,
                task = task,
                onReload = { scope.launch { reload() } },
                onClose = { showTaskDetail = false },
            )
        }
    }
}

@Composable
private fun FullScreenDialog(
    onDismiss: () -> Unit,
    content: @Composable () -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background,
        ) {
            content()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WebDavTaskDetailScreen(
    vaultRootUri: Uri?,
    repository: VaultRepository,
    manager: WebDavSyncTaskManager,
    webDavConfig: WebDavConfig,
    task: WebDavSyncTask,
    onReload: () -> Unit,
    onClose: () -> Unit,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    val dividerColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)

    var working by remember { mutableStateOf(false) }
    var localError by remember { mutableStateOf<String?>(null) }

    // Best-effort: treat as current when the task isn't finished yet.
    val isCurrent = task.run == null || task.run.endedAtMs == 0L

    val configMismatch =
        task.baseUrl != webDavConfig.baseUrl.trim() ||
            task.remoteRoot != webDavConfig.remoteRoot.trim().ifBlank { "/" } ||
            task.includeIndexSqlite != webDavConfig.includeIndexSqlite

    val unresolvedConflicts =
        task.operations.any { it.kind == WebDavPlannedOpKind.CONFLICT && it.resolution == null }

    val hasExecutableOps =
        task.operations.any { op ->
            if (op.state == WebDavSyncTaskOpState.SKIPPED) return@any false
            if (op.kind == WebDavPlannedOpKind.CONFLICT) op.resolution != null else true
        }

    val canExecute =
        isCurrent &&
            !configMismatch &&
            vaultRootUri != null &&
            webDavConfig.enabled &&
            !unresolvedConflicts &&
            hasExecutableOps &&
            !working

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            Column {
                ZhixuTopAppBar(
                    containerColor = MaterialTheme.colorScheme.surface,
                    title = { Text(stringResource(R.string.webdav_task_detail_title), style = MaterialTheme.typography.titleMedium) },
                    navigationIcon = {
                        ZhixuIconButton(onClick = onClose) {
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
                                                manager.discardCurrentTask(root)
                                                onReload()
                                                onClose()
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
                                    enabled = canExecute,
                                    onClick = {
                                        val root = vaultRootUri ?: return@AssistChip
                                        scope.launch {
                                            working = true
                                            localError = null
                                            runCatching {
                                                manager.executeCurrentTask(root, webDavConfig)
                                                onReload()
                                                onClose()
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
                                    label = {
                                        Text(
                                            text =
                                                stringResource(
                                                    if (hasExecutableOps) R.string.webdav_task_execute else R.string.webdav_task_no_executable_ops,
                                                ),
                                            maxLines = 1,
                                        )
                                    },
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
                    .background(MaterialTheme.colorScheme.background)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = MaterialTheme.shapes.extraLarge,
            ) {
                Column(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(text = "ID: ${task.id}", fontFamily = FontFamily.Monospace, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(text = stringResource(R.string.webdav_task_created_at_fmt, WebDavSyncTaskManager.formatEpochMs(task.createdAtMs)))
                    if (task.run != null) {
                        val ended = task.run.endedAtMs.takeIf { it > 0L } ?: 0L
                        if (ended > 0L) {
                            Text(text = stringResource(R.string.webdav_task_ended_at_fmt, WebDavSyncTaskManager.formatEpochMs(ended)))
                        }
                        if (!task.run.error.isNullOrBlank()) {
                            Text(text = task.run.error!!, color = MaterialTheme.colorScheme.error)
                        }
                    }
                    if (configMismatch) {
                        Text(text = stringResource(R.string.webdav_task_config_changed), color = MaterialTheme.colorScheme.error)
                    }
                    if (unresolvedConflicts) {
                        Text(text = stringResource(R.string.webdav_task_unresolved_conflicts), color = MaterialTheme.colorScheme.error)
                    }
                    if (!localError.isNullOrBlank()) {
                        Text(text = localError!!, color = MaterialTheme.colorScheme.error)
                    }
                }
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = MaterialTheme.shapes.extraLarge,
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    ListItem(
                        headlineContent = { Text(stringResource(R.string.webdav_task_operations_title)) },
                        supportingContent = {
                            Text(
                                stringResource(R.string.webdav_task_operations_count_fmt, task.operations.size),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        },
                    )
                    HorizontalDivider(color = dividerColor)
                    task.operations.forEachIndexed { idx, op ->
                        val isDelete = op.kind == WebDavPlannedOpKind.DELETE_LOCAL || op.kind == WebDavPlannedOpKind.DELETE_REMOTE
                        val statusText =
                            when (op.state) {
                                WebDavSyncTaskOpState.PENDING -> stringResource(R.string.webdav_task_op_state_pending)
                                WebDavSyncTaskOpState.SKIPPED -> stringResource(R.string.webdav_task_op_state_skipped)
                                WebDavSyncTaskOpState.DONE -> stringResource(R.string.webdav_task_op_state_done)
                                WebDavSyncTaskOpState.FAILED -> stringResource(R.string.webdav_task_op_state_failed)
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
                                    if (op.kind == WebDavPlannedOpKind.CONFLICT) {
                                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                            @Composable fun pick(strategy: WebDavConflictStrategy, labelRes: Int) {
                                                FilterChip(
                                                    selected = op.resolution == strategy,
                                                    enabled = isCurrent && !working,
                                                    onClick = {
                                                        val root = vaultRootUri ?: return@FilterChip
                                                        scope.launch {
                                                            manager.updateCurrentTask(root) { cur ->
                                                                cur.copy(
                                                                    operations =
                                                                        cur.operations.mapIndexed { i, x ->
                                                                            if (i != idx) x else x.copy(resolution = strategy)
                                                                        },
                                                                )
                                                            }
                                                            onReload()
                                                        }
                                                    },
                                                    shape = CircleShape,
                                                    colors =
                                                        FilterChipDefaults.filterChipColors(
                                                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                                            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                                        ),
                                                    label = { Text(stringResource(labelRes), fontWeight = if (op.resolution == strategy) FontWeight.Bold else FontWeight.Normal, maxLines = 1) },
                                                )
                                            }
                                            pick(WebDavConflictStrategy.LOCAL_WINS, R.string.webdav_task_conflict_use_local)
                                            pick(WebDavConflictStrategy.REMOTE_WINS, R.string.webdav_task_conflict_use_remote)
                                            pick(WebDavConflictStrategy.KEEP_BOTH, R.string.webdav_task_conflict_keep_both)
                                        }
                                    }
                                }
                            },
                            trailingContent = {
                                Column(horizontalAlignment = Alignment.End) {
                                    Text(statusText, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    if (op.kind != WebDavPlannedOpKind.CONFLICT && isCurrent && !working) {
                                        val root = vaultRootUri
                                        AssistChip(
                                            enabled = root != null,
                                            onClick = {
                                                val r = root ?: return@AssistChip
                                                scope.launch {
                                                    manager.updateCurrentTask(r) { cur ->
                                                        cur.copy(
                                                            operations =
                                                                cur.operations.mapIndexed { i, x ->
                                                                    if (i != idx) x
                                                                    else {
                                                                        val nextState =
                                                                            if (x.state == WebDavSyncTaskOpState.SKIPPED) WebDavSyncTaskOpState.PENDING else WebDavSyncTaskOpState.SKIPPED
                                                                        x.copy(state = nextState)
                                                                    }
                                                                },
                                                        )
                                                    }
                                                    onReload()
                                                }
                                            },
                                            shape = CircleShape,
                                            border = null,
                                            colors =
                                                AssistChipDefaults.assistChipColors(
                                                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                                    labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                                ),
                                            label = {
                                                Text(
                                                    if (op.state == WebDavSyncTaskOpState.SKIPPED) stringResource(R.string.webdav_task_unskip) else stringResource(R.string.webdav_task_skip),
                                                    maxLines = 1,
                                                )
                                            },
                                        )
                                    }
                                }
                            },
                        )
                        if (idx != task.operations.lastIndex) HorizontalDivider(color = dividerColor)
                    }
                }
            }

            if (!isCurrent && task.operations.any { it.state == WebDavSyncTaskOpState.FAILED } && vaultRootUri != null) {
                Button(
                    enabled = !working,
                    onClick = {
                        val root = vaultRootUri ?: return@Button
                        val failedPaths =
                            task.operations
                                .filter { it.state == WebDavSyncTaskOpState.FAILED }
                                .map { it.path }
                                .toSet()
                        scope.launch {
                            working = true
                            localError = null
                            runCatching {
                                manager.generateTask(
                                    rootUri = root,
                                    config = webDavConfig,
                                    trigger = WebDavSyncTaskTrigger.MANUAL,
                                    onlyPaths = failedPaths,
                                )
                                onReload()
                                onClose()
                            }.onFailure { e -> localError = e.message ?: e.javaClass.simpleName }
                            working = false
                        }
                    },
                ) { Text(stringResource(R.string.webdav_task_retry_failed)) }
            }
        }
    }
}
