package app.zhixu.ui.screens

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.zhixu.R
import app.zhixu.data.VaultRepository
import app.zhixu.sync.WebDavSyncEngine
import app.zhixu.ui.Ionicons
import app.zhixu.ui.ZhixuTopBarIconSize
import app.zhixu.ui.components.ZhixuIconButton
import app.zhixu.ui.components.ZhixuTextField
import app.zhixu.ui.components.ZhixuTopAppBar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private data class WebDavLoggedOp(
    val kind: String,
    val path: String,
    val reason: String,
    val strategy: String?,
)

private data class WebDavLastPlan(
    val ok: Boolean,
    val endedAtMs: Long,
    val operationsTotal: Int,
    val operationsTruncated: Boolean,
    val operations: List<WebDavLoggedOp>,
    val error: String?,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WebDavLogsScreen(
    contentPadding: PaddingValues,
    vaultRootUri: Uri?,
    repository: VaultRepository,
    onBack: () -> Unit,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    val engine = remember(context, repository) { WebDavSyncEngine(context, repository) }
    val dividerColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)

    var activeTab by remember { mutableStateOf(0) }
    var loading by remember { mutableStateOf(false) }

    var lastSummaryText by remember { mutableStateOf<String?>(null) }
    var unresolvedCount by remember { mutableStateOf(0) }
    var lastPlan by remember { mutableStateOf<WebDavLastPlan?>(null) }
    var technicalLog by remember { mutableStateOf("") }

    var fileKindFilter by remember { mutableStateOf("ALL") }
    var fileQuery by remember { mutableStateOf("") }

    fun formatEpochMs(ms: Long): String {
        if (ms <= 0L) return "-"
        return runCatching {
            Instant
                .ofEpochMilli(ms)
                .atZone(ZoneId.systemDefault())
                .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))
        }.getOrElse { ms.toString() }
    }

    suspend fun reloadAll() {
        val root = vaultRootUri ?: return
        loading = true

        unresolvedCount =
            runCatching { engine.listUnresolvedConflicts(root).size }
                .getOrDefault(0)

        lastSummaryText =
            withContext(Dispatchers.IO) {
                val uri = repository.resolveVaultFileUri(root, ".zhixu/sync/webdav_last_summary.json") ?: return@withContext null
                val raw = runCatching { repository.readText(uri) }.getOrNull().orEmpty().trim()
                if (raw.isBlank()) return@withContext null
                val obj = runCatching { JSONObject(raw) }.getOrNull() ?: return@withContext raw
                val ok = obj.optBoolean("ok", false)
                val endedAt = obj.optLong("endedAt", 0L)
                if (!ok) {
                    val error = obj.optString("error").ifBlank { "-" }
                    return@withContext context.getString(R.string.webdav_sync_failed, error)
                }
                val uploaded = obj.optInt("uploaded", 0)
                val downloaded = obj.optInt("downloaded", 0)
                val deletedRemote = obj.optInt("deletedRemote", 0)
                val deletedLocal = obj.optInt("deletedLocal", 0)
                val conflicts = obj.optInt("conflicts", 0)
                val failed = obj.optInt("failed", 0)
                val msg = context.getString(R.string.webdav_sync_ok_v2, uploaded, downloaded, deletedRemote, deletedLocal, conflicts, failed)
                val time = formatEpochMs(endedAt)
                "$msg · $time"
            }

        lastPlan =
            withContext(Dispatchers.IO) {
                val uri = repository.resolveVaultFileUri(root, ".zhixu/sync/webdav_last_plan.json") ?: return@withContext null
                val raw = runCatching { repository.readText(uri) }.getOrNull().orEmpty().trim()
                if (raw.isBlank()) return@withContext null
                val obj = runCatching { JSONObject(raw) }.getOrNull() ?: return@withContext null
                val ok = obj.optBoolean("ok", false)
                val endedAt = obj.optLong("endedAt", 0L)
                val operationsTotal = obj.optInt("operationsTotal", 0).coerceAtLeast(0)
                val operationsTruncated = obj.optBoolean("operationsTruncated", false)
                val error = obj.optString("error").orEmpty().trim().ifBlank { null }
                val ops =
                    (obj.optJSONArray("operations") ?: JSONArray()).let { arr ->
                        buildList {
                            for (i in 0 until arr.length()) {
                                val o = arr.optJSONObject(i) ?: continue
                                val kind = o.optString("kind").orEmpty().trim()
                                val path = o.optString("path").orEmpty().trim().trimStart('/')
                                if (kind.isBlank() || path.isBlank()) continue
                                add(
                                    WebDavLoggedOp(
                                        kind = kind,
                                        path = path,
                                        reason = o.optString("reason").orEmpty(),
                                        strategy = o.optString("strategy").orEmpty().trim().ifBlank { null },
                                    ),
                                )
                            }
                        }
                    }
                WebDavLastPlan(
                    ok = ok,
                    endedAtMs = endedAt,
                    operationsTotal = operationsTotal,
                    operationsTruncated = operationsTruncated,
                    operations = ops,
                    error = error,
                )
            }

        technicalLog =
            withContext(Dispatchers.IO) {
                val uri = repository.resolveVaultFileUri(root, ".zhixu/sync/log.jsonl") ?: return@withContext ""
                val raw = runCatching { repository.readText(uri) }.getOrNull().orEmpty().trim()
                if (raw.isBlank()) return@withContext ""
                val lines = raw.lines()
                if (lines.size <= 800) raw else lines.takeLast(800).joinToString("\n")
            }

        loading = false
    }

    LaunchedEffect(vaultRootUri) {
        val root = vaultRootUri ?: return@LaunchedEffect
        reloadAll()
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            Column {
                ZhixuTopAppBar(
                    containerColor = MaterialTheme.colorScheme.surface,
                    title = { Text(stringResource(R.string.webdav_logs_title), style = MaterialTheme.typography.titleMedium) },
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
                            enabled = vaultRootUri != null && !loading,
                            onClick = { scope.launch { reloadAll() } },
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
                    text = { Text(stringResource(R.string.webdav_logs_tab_overview)) },
                )
                Tab(
                    selected = activeTab == 1,
                    onClick = { activeTab = 1 },
                    text = { Text(stringResource(R.string.webdav_logs_tab_files)) },
                )
                Tab(
                    selected = activeTab == 2,
                    onClick = { activeTab = 2 },
                    text = { Text(stringResource(R.string.webdav_logs_tab_technical)) },
                )
            }

            when (activeTab) {
                0 -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        item {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                shape = MaterialTheme.shapes.extraLarge,
                            ) {
                                Column(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text(stringResource(R.string.webdav_logs_section_overview), style = MaterialTheme.typography.titleSmall)
                                    Text(
                                        text = lastSummaryText ?: "-",
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontSize = 13.sp,
                                    )
                                    Text(
                                        text = context.getString(R.string.webdav_logs_unresolved_count_fmt, unresolvedCount),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontSize = 13.sp,
                                    )
                                    val lp = lastPlan
                                    if (lp != null) {
                                        val t = formatEpochMs(lp.endedAtMs)
                                        val extra = if (lp.operationsTruncated) " (truncated)" else ""
                                        Text(
                                            text = context.getString(R.string.webdav_logs_last_plan_fmt, lp.operationsTotal) + extra + " · " + t,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            fontSize = 13.sp,
                                        )
                                        if (!lp.error.isNullOrBlank()) {
                                            Text(
                                                text = lp.error,
                                                color = MaterialTheme.colorScheme.error,
                                                fontSize = 13.sp,
                                                maxLines = 2,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                1 -> {
                    val plan = lastPlan
                    if (plan == null) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(text = "-", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        return@Column
                    }

                    Column(
                        modifier = Modifier.fillMaxSize().padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        ZhixuTextField(
                            value = fileQuery,
                            onValueChange = { fileQuery = it },
                            label = { Text(stringResource(R.string.webdav_logs_filter_hint)) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )

                        val filters =
                            listOf(
                                "ALL" to stringResource(R.string.webdav_logs_filter_all),
                                "UPLOAD" to stringResource(R.string.webdav_logs_filter_upload),
                                "DOWNLOAD" to stringResource(R.string.webdav_logs_filter_download),
                                "DELETE" to stringResource(R.string.webdav_logs_filter_delete),
                                "CONFLICT" to stringResource(R.string.webdav_logs_filter_conflict),
                            )
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            for ((key, label) in filters) {
                                TextButton(onClick = { fileKindFilter = key }) {
                                    Text(
                                        text = label,
                                        color =
                                            if (fileKindFilter == key) MaterialTheme.colorScheme.primary
                                            else MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }

                        val q = fileQuery.trim().lowercase()
                        val shown =
                            plan.operations.filter { op ->
                                val matchesKind =
                                    when (fileKindFilter) {
                                        "UPLOAD" -> op.kind == "UPLOAD"
                                        "DOWNLOAD" -> op.kind == "DOWNLOAD"
                                        "DELETE" -> op.kind == "DELETE_REMOTE" || op.kind == "DELETE_LOCAL"
                                        "CONFLICT" -> op.kind == "CONFLICT"
                                        else -> true
                                    }
                                val matchesQuery =
                                    q.isBlank() || op.path.lowercase().contains(q) || op.reason.lowercase().contains(q)
                                matchesKind && matchesQuery
                            }.take(1500)

                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            items(shown) { op ->
                                val prefix =
                                    when (op.kind) {
                                        "DOWNLOAD" -> "↓"
                                        "UPLOAD" -> "↑"
                                        "DELETE_REMOTE" -> "delR"
                                        "DELETE_LOCAL" -> "delL"
                                        "CONFLICT" -> "conflict"
                                        else -> op.kind
                                    }
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                    shape = MaterialTheme.shapes.extraLarge,
                                ) {
                                    ListItem(
                                        headlineContent = { Text("$prefix ${op.path}", maxLines = 2, overflow = TextOverflow.Ellipsis) },
                                        supportingContent = {
                                            val extra = op.strategy?.let { " · $it" }.orEmpty()
                                            Text(op.reason + extra, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                        },
                                    )
                                }
                            }
                            if (shown.size < plan.operations.size) {
                                item {
                                    Text(
                                        text = "… +${plan.operations.size - shown.size}",
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                }

                else -> {
                    val mono = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace)
                    val vScroll = rememberScrollState()
                    val hScroll = rememberScrollState()
                    Column(
                        modifier =
                            Modifier
                                .fillMaxSize()
                                .padding(16.dp)
                                .background(MaterialTheme.colorScheme.surface, MaterialTheme.shapes.extraLarge)
                                .padding(12.dp)
                                .verticalScroll(vScroll)
                                .horizontalScroll(hScroll),
                    ) {
                        Text(text = if (technicalLog.isBlank()) "-" else technicalLog, style = mono)
                    }
                }
            }
        }
    }
}
