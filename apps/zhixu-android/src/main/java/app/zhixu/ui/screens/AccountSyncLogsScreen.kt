package app.zhixu.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.zhixu.R
import app.zhixu.data.AccountPreferences
import app.zhixu.data.AccountState
import app.zhixu.sync.OfficialSync
import app.zhixu.sync.SyncServerClient
import app.zhixu.sync.SyncServerResult
import app.zhixu.ui.Ionicons
import app.zhixu.ui.ZhixuTopBarIconSize
import app.zhixu.ui.components.ZhixuIconButton
import app.zhixu.ui.components.ZhixuTopAppBar
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.ln
import kotlin.math.pow
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountSyncLogsScreen(
    contentPadding: PaddingValues,
    accountPrefs: AccountPreferences,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val state by accountPrefs.state.collectAsState(
        initial = AccountState(token = "", username = "", userId = 0L, email = "", avatarUri = "", avatarUpdatedAtMs = 0L),
    )

    var loading by remember { mutableStateOf(false) }
    var logs by remember { mutableStateOf<List<SyncServerClient.AccountSyncLog>>(emptyList()) }
    var error by remember { mutableStateOf<String?>(null) }

    val serverUnreachableText = stringResource(R.string.error_server_unreachable)

    fun <T> SyncServerResult<T>.toUiMessage(fallback: String): String {
        return when {
            statusCode == 0 || errorMessage == "NETWORK_UNREACHABLE" -> serverUnreachableText
            !errorMessage.isNullOrBlank() -> errorMessage!!
            else -> fallback
        }
    }

    suspend fun refresh() {
        if (!state.isLoggedIn) {
            logs = emptyList()
            error = context.getString(R.string.account_login_required)
            return
        }
        loading = true
        error = null
        try {
            val res = SyncServerClient.listSyncLogs(OfficialSync.BASE_URL, token = state.token)
            logs = res.value ?: emptyList()
            if (!res.ok) error = res.toUiMessage(context.getString(R.string.account_sync_logs_failed))
        } finally {
            loading = false
        }
    }

    LaunchedEffect(state.token) { refresh() }

    fun formatBytes(bytes: Long): String {
        val b = bytes.coerceAtLeast(0L).toDouble()
        if (b < 1024.0) return "${bytes.coerceAtLeast(0L)} B"
        val unit = 1024.0
        val exp = (ln(b) / ln(unit)).toInt().coerceIn(1, 6)
        val pre = "KMGTPE"[exp - 1]
        val value = b / unit.pow(exp.toDouble())
        return String.format("%.1f %sB", value, pre)
    }

    fun formatEpochMs(ms: Long): String {
        if (ms <= 0L) return "-"
        return runCatching {
            Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))
        }.getOrElse { ms.toString() }
    }

    @Composable
    fun actionLabel(action: String): String {
        return when (action.trim().lowercase()) {
            "file_put" -> stringResource(R.string.sync_log_op_upload)
            "file_get" -> stringResource(R.string.sync_log_op_download)
            "file_delete" -> stringResource(R.string.sync_log_op_delete_remote)
            "changes_snapshot" -> stringResource(R.string.account_sync_logs_action_changes_snapshot)
            "changes_delta" -> stringResource(R.string.account_sync_logs_action_changes_delta)
            else -> action.ifBlank { "-" }
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            Column {
                ZhixuTopAppBar(
                    containerColor = MaterialTheme.colorScheme.surface,
                    title = { Text(stringResource(R.string.account_sync_logs_title), style = MaterialTheme.typography.titleMedium) },
                    navigationIcon = {
                        ZhixuIconButton(onClick = onBack) {
                            androidx.compose.material3.Icon(
                                painter = painterResource(Ionicons.ArrowBack),
                                contentDescription = stringResource(R.string.action_back),
                                modifier = Modifier.size(ZhixuTopBarIconSize),
                            )
                        }
                    },
                    actions = {
                        TextButton(onClick = { scope.launch { refresh() } }, enabled = !loading) {
                            Text(stringResource(R.string.action_refresh))
                        }
                    },
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
        },
    ) { innerPadding ->
        if (loading && logs.isEmpty()) {
            Box(
                modifier =
                    Modifier
                        .padding(innerPadding)
                        .padding(contentPadding)
                        .windowInsetsPadding(WindowInsets.navigationBars)
                        .fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        LazyColumn(
            modifier =
                Modifier
                    .padding(innerPadding)
                    .padding(contentPadding)
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .fillMaxSize()
                    .imePadding(),
            contentPadding = PaddingValues(0.dp),
            verticalArrangement = Arrangement.spacedBy(0.dp),
        ) {
            if (!error.isNullOrBlank()) {
                item {
                    Text(
                        text = error!!,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp),
                    )
                }
            }

            if (logs.isEmpty() && error.isNullOrBlank()) {
                item {
                    Text(
                        text = stringResource(R.string.account_sync_logs_empty),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp),
                    )
                }
            }

            items(logs.size) { idx ->
                val l = logs[idx]
                val headline =
                    listOfNotNull(
                        actionLabel(l.action).takeIf { it.isNotBlank() },
                        l.path.trim().ifBlank { null },
                    ).joinToString(" · ")

                val meta =
                    buildList {
                        val dn = l.deviceName.trim().ifBlank { null }
                        val ip = l.ip.trim().ifBlank { null }
                        val client = l.client.trim().ifBlank { null }
                        val size = l.sizeBytes.takeIf { it > 0L }?.let { formatBytes(it) }
                        if (dn != null) add(dn)
                        if (ip != null) add(ip)
                        if (client != null) add(client)
                        if (size != null) add(size)
                    }.joinToString(" · ")

                ListItem(
                    modifier = Modifier.fillMaxWidth(),
                    headlineContent = { Text(text = headline.ifBlank { "-" }, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    supportingContent = {
                        if (meta.isNotBlank()) {
                            Text(text = meta, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                    },
                    trailingContent = {
                        Text(
                            text = formatEpochMs(l.createdAtMs),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            }
        }
    }
}
