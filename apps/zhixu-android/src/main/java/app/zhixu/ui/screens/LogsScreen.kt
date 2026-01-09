package app.zhixu.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.SystemClock
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.documentfile.provider.DocumentFile
import app.zhixu.R
import app.zhixu.data.AppLogRepository
import app.zhixu.data.LogPreferences
import app.zhixu.data.VaultRepository
import app.zhixu.data.vaultRootToDocumentFile
import app.zhixu.ui.Ionicons
import app.zhixu.ui.ZhixuTopBarIconSize
import app.zhixu.ui.components.ZhixuDialogDefaults
import app.zhixu.ui.components.ZhixuIconButton
import app.zhixu.ui.components.ZhixuSwitch
import app.zhixu.ui.components.ZhixuTopAppBar
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogsScreen(
    contentPadding: PaddingValues,
    vaultRootUri: Uri?,
    repository: VaultRepository,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current

    val prefs = remember(context) { LogPreferences(context.applicationContext) }
    val debugEnabled by prefs.debugEnabled.collectAsState(initial = false)
    val appLogs = remember(context) { AppLogRepository(context.applicationContext) }

    var activeLogDialog by remember { mutableStateOf<LogDialogState?>(null) }

    var syncLogLoading by remember { mutableStateOf(false) }
    var syncLogText by remember { mutableStateOf("") }
    var syncLogLoadedAtMs by remember { mutableStateOf(0L) }

    var pluginLogText by remember { mutableStateOf("") }
    var operationLogText by remember { mutableStateOf("") }
    var aiLogText by remember { mutableStateOf("") }

    suspend fun resolveRelativeUri(rootUri: Uri, relativePath: String): Uri? =
        withContext(Dispatchers.IO) {
            if (rootUri.scheme.equals("file", ignoreCase = true)) {
                val rootPath = rootUri.path ?: return@withContext null
                val file = File(rootPath, relativePath)
                return@withContext if (file.exists()) Uri.fromFile(file) else null
            }
            val root = vaultRootToDocumentFile(context, rootUri) ?: return@withContext null
            var current: DocumentFile = root
            val parts = relativePath.split('/').filter { it.isNotBlank() }
            for (part in parts) {
                val next = current.listFiles().firstOrNull { it.name == part } ?: return@withContext null
                current = next
            }
            current.uri
        }

    fun refreshInternalLogs() {
        pluginLogText = appLogs.readBlocking(AppLogRepository.Kind.Plugin)
        operationLogText = appLogs.readBlocking(AppLogRepository.Kind.Operation)
        aiLogText = appLogs.readBlocking(AppLogRepository.Kind.Ai)
    }

    LaunchedEffect(Unit) {
        refreshInternalLogs()
    }

    LaunchedEffect(activeLogDialog?.kind, vaultRootUri) {
        val state = activeLogDialog ?: return@LaunchedEffect
        if (state.kind != LogKind.Sync) return@LaunchedEffect
        val root = vaultRootUri ?: return@LaunchedEffect

        val now = SystemClock.uptimeMillis()
        if (syncLogLoadedAtMs > 0L && now - syncLogLoadedAtMs in 0..800) return@LaunchedEffect

        syncLogLoading = true
        syncLogText =
            runCatching {
                val uri = resolveRelativeUri(root, ".zhixu/sync/log.jsonl") ?: return@runCatching ""
                val all = repository.readText(uri).trim()
                val lines = all.lines()
                val limited = if (lines.size <= 800) all else lines.takeLast(800).joinToString("\n")
                formatSyncLogForDisplay(limited, context)
            }.getOrDefault("")
        syncLogLoadedAtMs = SystemClock.uptimeMillis()
        syncLogLoading = false
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            Column {
                ZhixuTopAppBar(
                    containerColor = MaterialTheme.colorScheme.surface,
                    title = { Text(stringResource(R.string.settings_logs_title), style = MaterialTheme.typography.titleMedium) },
                    navigationIcon = {
                        ZhixuIconButton(onClick = onBack) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                                contentDescription = stringResource(R.string.action_back),
                                modifier = Modifier.size(ZhixuTopBarIconSize),
                            )
                        }
                    },
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
        },
    ) { innerPadding ->
        LazyColumn(
            modifier =
                Modifier
                    .padding(contentPadding)
                    .padding(innerPadding)
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .background(MaterialTheme.colorScheme.background)
                    .fillMaxSize()
                    .imePadding(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(0.dp),
        ) {
            item { Spacer(modifier = Modifier.height(12.dp)) }

            item {
                Text(
                    text = stringResource(R.string.logs_section_debug),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 6.dp),
                )
            }

            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = MaterialTheme.shapes.extraLarge,
                ) {
                    ListItem(
                        modifier = Modifier.fillMaxWidth(),
                        leadingContent = {
                            Icon(
                                painter = painterResource(Ionicons.SettingsOutline),
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp),
                            )
                        },
                        headlineContent = { Text(stringResource(R.string.logs_debug_title)) },
                        supportingContent = { Text(stringResource(R.string.logs_debug_subtitle), maxLines = 2, overflow = TextOverflow.Ellipsis) },
                        trailingContent = {
                            ZhixuSwitch(
                                checked = debugEnabled,
                                onCheckedChange = { checked -> scope.launch { prefs.setDebugEnabled(checked) } },
                            )
                        },
                    )
                }
            }

            item { Spacer(modifier = Modifier.height(14.dp)) }

            item {
                Text(
                    text = stringResource(R.string.logs_section_all),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 6.dp),
                )
            }

            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = MaterialTheme.shapes.extraLarge,
                ) {
                    LogNavRow(
                        iconRes = Ionicons.SyncOutline,
                        title = stringResource(R.string.sync_logs_title),
                        subtitle =
                            when {
                                vaultRootUri == null -> stringResource(R.string.workshop_no_vault)
                                syncLogLoading -> stringResource(R.string.sync_logs_loading)
                                syncLogText.isBlank() -> stringResource(R.string.sync_logs_empty)
                                else -> stringResource(R.string.sync_logs_tap_to_view)
                            },
                        enabled = vaultRootUri != null,
                        onClick = { activeLogDialog = LogDialogState(kind = LogKind.Sync) },
                    )

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
                    LogNavRow(
                        iconRes = Ionicons.Workshop,
                        title = stringResource(R.string.logs_plugin_title),
                        subtitle = if (pluginLogText.isBlank()) stringResource(R.string.logs_empty) else stringResource(R.string.sync_logs_tap_to_view),
                        onClick = { activeLogDialog = LogDialogState(kind = LogKind.Plugin) },
                    )

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
                    LogNavRow(
                        iconRes = Ionicons.DocumentText,
                        title = stringResource(R.string.logs_operation_title),
                        subtitle = if (operationLogText.isBlank()) stringResource(R.string.logs_empty) else stringResource(R.string.sync_logs_tap_to_view),
                        onClick = { activeLogDialog = LogDialogState(kind = LogKind.Operation) },
                    )

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
                    LogNavRow(
                        iconRes = R.drawable.ic_hi_sparkles_outline,
                        title = stringResource(R.string.logs_ai_title),
                        subtitle = if (aiLogText.isBlank()) stringResource(R.string.logs_empty) else stringResource(R.string.sync_logs_tap_to_view),
                        onClick = { activeLogDialog = LogDialogState(kind = LogKind.Ai) },
                    )
                }
            }
        }
    }

    val dialog = activeLogDialog
    if (dialog != null) {
        val title =
            when (dialog.kind) {
                LogKind.Sync -> context.getString(R.string.sync_logs_title)
                LogKind.Plugin -> context.getString(R.string.logs_plugin_title)
                LogKind.Operation -> context.getString(R.string.logs_operation_title)
                LogKind.Ai -> context.getString(R.string.logs_ai_title)
            }

        val text =
            when (dialog.kind) {
                LogKind.Sync -> syncLogText
                LogKind.Plugin -> pluginLogText
                LogKind.Operation -> operationLogText
                LogKind.Ai -> aiLogText
            }

        val exportText =
            if (text.isBlank()) {
                context.getString(R.string.logs_empty)
            } else {
                text
            }

        AlertDialog(
            modifier = ZhixuDialogDefaults.modifier(),
            onDismissRequest = { activeLogDialog = null },
            properties = ZhixuDialogDefaults.properties,
            title = { Text(title) },
            text = {
                Box(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(420.dp),
                ) {
                    val loading = dialog.kind == LogKind.Sync && syncLogLoading
                    if (loading) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            androidx.compose.material3.CircularProgressIndicator()
                        }
                    } else {
                        val v = rememberScrollState()
                        val h = rememberScrollState()
                        Text(
                            text = exportText,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier =
                                Modifier
                                    .fillMaxSize()
                                    .verticalScroll(v)
                                    .horizontalScroll(h),
                        )
                    }
                }
            },
            confirmButton = {
                androidx.compose.foundation.layout.Row {
                    TextButton(
                        enabled = exportText.isNotBlank(),
                        onClick = {
                            clipboard.setText(AnnotatedString(exportText))
                        },
                    ) { Text(stringResource(R.string.sync_logs_copy)) }

                    TextButton(
                        enabled = exportText.isNotBlank(),
                        onClick = {
                            val intent =
                                Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_SUBJECT, title)
                                    putExtra(Intent.EXTRA_TEXT, exportText)
                                }
                            context.startActivity(Intent.createChooser(intent, context.getString(R.string.sync_logs_export)))
                        },
                    ) { Text(stringResource(R.string.sync_logs_export)) }
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { activeLogDialog = null },
                ) {
                    Text(stringResource(R.string.sync_logs_close))
                }
            },
        )
    }
}

private enum class LogKind {
    Sync,
    Plugin,
    Operation,
    Ai,
}

private data class LogDialogState(
    val kind: LogKind,
)

@Composable
private fun LogNavRow(
    iconRes: Int,
    title: String,
    subtitle: String,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val alpha = if (enabled) 1f else 0.4f
    ListItem(
        modifier =
            Modifier
                .fillMaxWidth()
                .alpha(alpha)
                .clickable(enabled = enabled, onClick = onClick),
        leadingContent = {
            Icon(
                painter = painterResource(iconRes),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
        },
        headlineContent = { Text(title) },
        supportingContent = { Text(subtitle, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        trailingContent = {
            Icon(
                painter = painterResource(Ionicons.ChevronForward),
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
        },
    )
}

private fun formatSyncLogForDisplay(raw: String, context: Context): String {
    if (raw.isBlank()) return ""
    val out = ArrayList<String>()
    for (line in raw.lines()) {
        val trimmed = line.trim()
        if (trimmed.isBlank()) continue
        out += formatSyncLogLineForDisplay(trimmed, context)
    }
    return out.joinToString("\n")
}

private fun formatSyncLogLineForDisplay(line: String, context: Context): String {
    val obj = runCatching { JSONObject(line) }.getOrNull() ?: return line
    val event = obj.optString("event").orEmpty()

    fun opLabel(op: String): String {
        return when (op) {
            "upload" -> context.getString(R.string.sync_log_op_upload)
            "download" -> context.getString(R.string.sync_log_op_download)
            "delete_remote" -> context.getString(R.string.sync_log_op_delete_remote)
            "download_conflict" -> context.getString(R.string.sync_log_op_download_conflict)
            "upload_conflict_overwrite" -> context.getString(R.string.sync_log_op_upload_conflict_overwrite)
            else -> op
        }
    }

    fun engineLabel(engine: String): String {
        return when (engine) {
            "official" -> context.getString(R.string.sync_log_engine_official)
            "webdav" -> context.getString(R.string.sync_log_engine_webdav)
            else -> engine
        }
    }

    val ts = obj.optString("ts").orEmpty()

    return when (event) {
        "start" -> {
            val parts = ArrayList<String>(3)
            val engine = obj.optString("engine").trim()
            if (engine.isNotBlank()) {
                parts += "${context.getString(R.string.sync_log_field_engine)}=${engineLabel(engine)}"
            }
            val includeIndexSqlite = obj.optString("includeIndexSqlite").trim()
            if (includeIndexSqlite.isNotBlank()) {
                parts += "${context.getString(R.string.sync_log_field_include_index_sqlite)}=$includeIndexSqlite"
            }
            val details = parts.joinToString(" ")
            if (details.isBlank()) {
                context.getString(R.string.sync_log_line_start_fmt, ts)
            } else {
                context.getString(R.string.sync_log_line_start_with_details_fmt, ts, details)
            }
        }

        "remote_root" -> {
            val url = obj.optString("url").orEmpty()
            context.getString(R.string.sync_log_line_remote_root_fmt, url)
        }

        "file_failed" -> {
            val op = opLabel(obj.optString("op").orEmpty())
            val path = obj.optString("path").orEmpty()
            val error = obj.optString("error").orEmpty()
            context.getString(R.string.sync_log_line_file_failed_fmt, op, path, error)
        }

        "end" -> {
            val ok = obj.optString("ok").trim().equals("true", ignoreCase = true)
            if (!ok) {
                val error = obj.optString("error").ifBlank { "-" }
                return context.getString(R.string.sync_log_line_end_fail_fmt, error)
            }
            val metrics = ArrayList<String>(6)
            fun metric(key: String, labelRes: Int) {
                val value = obj.optString(key).trim()
                if (value.isNotBlank()) metrics += "${context.getString(labelRes)}=$value"
            }
            metric("uploaded", R.string.sync_log_metric_uploaded)
            metric("downloaded", R.string.sync_log_metric_downloaded)
            metric("deletedRemote", R.string.sync_log_metric_deleted_remote)
            metric("deletedLocal", R.string.sync_log_metric_deleted_local)
            metric("conflicts", R.string.sync_log_metric_conflicts)
            metric("failed", R.string.sync_log_metric_failed)
            context.getString(R.string.sync_log_line_end_ok_fmt, metrics.joinToString(" "))
        }

        else -> line
    }
}
