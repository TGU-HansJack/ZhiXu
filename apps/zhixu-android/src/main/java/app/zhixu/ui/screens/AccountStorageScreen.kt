package app.zhixu.ui.screens

import android.content.Intent
import android.widget.Toast
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
import androidx.core.content.FileProvider
import app.zhixu.R
import app.zhixu.data.AccountPreferences
import app.zhixu.data.AccountState
import app.zhixu.sync.OfficialSync
import app.zhixu.sync.SyncServerClient
import app.zhixu.sync.SyncServerResult
import app.zhixu.sync.SyncServerStorageStats
import app.zhixu.ui.Ionicons
import app.zhixu.ui.ZhixuTopBarIconSize
import app.zhixu.ui.components.ZhixuIconButton
import app.zhixu.ui.components.ZhixuTopAppBar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.ln
import kotlin.math.pow

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountStorageScreen(
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
    var exporting by remember { mutableStateOf(false) }
    var stats by remember { mutableStateOf<SyncServerStorageStats?>(null) }
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
            stats = null
            error = context.getString(R.string.account_login_required)
            return
        }
        loading = true
        error = null
        try {
            val res = SyncServerClient.storageStats(OfficialSync.BASE_URL, token = state.token)
            stats = res.value
            if (!res.ok) error = res.toUiMessage(context.getString(R.string.account_storage_failed))
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

    suspend fun exportZip() {
        if (!state.isLoggedIn) return
        exporting = true
        try {
            val res = SyncServerClient.exportStorageZip(OfficialSync.BASE_URL, token = state.token)
            val v = res.value
            if (!res.ok || v == null) {
                Toast.makeText(context, res.toUiMessage(context.getString(R.string.account_storage_export_failed)), Toast.LENGTH_SHORT).show()
                return
            }

            val file =
                withContext(Dispatchers.IO) {
                    val safeName = v.filename.trim().ifBlank { "zhixu-vault.zip" }
                    val dest = File(context.cacheDir, safeName)
                    dest.writeBytes(v.bytes)
                    dest
                }
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            val intent =
                Intent(Intent.ACTION_SEND).apply {
                    type = "application/zip"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
            context.startActivity(Intent.createChooser(intent, context.getString(R.string.account_storage_export_share)))
        } finally {
            exporting = false
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            Column(modifier = Modifier.fillMaxWidth()) {
                ZhixuTopAppBar(
                    title = { Text(stringResource(R.string.account_storage_title)) },
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
                        TextButton(onClick = { scope.launch { refresh() } }, enabled = !loading && !exporting) {
                            Text(stringResource(R.string.action_refresh))
                        }
                    },
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
        },
    ) { innerPadding ->
        if (loading && stats == null) {
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

        val st = stats
        LazyColumn(
            modifier =
                Modifier
                    .padding(innerPadding)
                    .padding(contentPadding)
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .fillMaxSize()
                    .imePadding(),
            verticalArrangement = Arrangement.spacedBy(0.dp),
            contentPadding = PaddingValues(0.dp),
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

            if (st != null) {
                item {
                    ListItem(
                        headlineContent = { Text(stringResource(R.string.account_storage_used)) },
                        supportingContent = {
                            val subtitle = context.getString(R.string.account_storage_used_subtitle_fmt, formatBytes(st.usedBytes), formatBytes(st.limitBytes))
                            Text(subtitle, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        },
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                }
                item {
                    ListItem(
                        headlineContent = { Text(stringResource(R.string.account_storage_file_count)) },
                        supportingContent = { Text(st.fileCount.toString()) },
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                }
                item {
                    ListItem(
                        headlineContent = { Text(stringResource(R.string.account_storage_last_updated)) },
                        supportingContent = { Text(formatEpochMs(st.lastUpdatedAtMs)) },
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                }
                item {
                    ListItem(
                        modifier = Modifier.fillMaxWidth(),
                        headlineContent = { Text(stringResource(R.string.account_storage_export)) },
                        supportingContent = { Text(stringResource(R.string.account_storage_export_desc)) },
                        trailingContent = {
                            TextButton(
                                enabled = state.isLoggedIn && !exporting,
                                onClick = { scope.launch { exportZip() } },
                            ) {
                                Text(if (exporting) stringResource(R.string.common_loading) else stringResource(R.string.sync_logs_export))
                            }
                        },
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                }
            } else if (error.isNullOrBlank()) {
                item {
                    Text(
                        text = stringResource(R.string.account_storage_empty),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp),
                    )
                }
            }
        }
    }
}
