package app.zhixu.ui.screens

import android.content.Intent
import android.content.Context
import android.net.Uri
import android.os.SystemClock
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.zhixu.R
import app.zhixu.data.AccountPreferences
import app.zhixu.data.AccountState
import app.zhixu.data.SyncPreferences
import app.zhixu.data.ThirdPartyServiceConfig
import app.zhixu.data.VaultStorageLocation
import app.zhixu.data.VaultSyncConfig
import app.zhixu.data.VaultSyncPreferences
import app.zhixu.data.WebDavClient
import app.zhixu.data.WebDavConfig
import app.zhixu.data.VaultRepository
import app.zhixu.data.vaultRootToDocumentFile
import app.zhixu.sync.OfficialSync
import app.zhixu.sync.OfficialVaultSyncEngine
import app.zhixu.sync.WebDavSyncEngine
import app.zhixu.ui.ZhixuTopBarIconSize
import app.zhixu.ui.components.ZhixuDialogDefaults
import app.zhixu.ui.components.ZhixuIconButton
import app.zhixu.ui.components.ZhixuPasswordToggleIconButton
import app.zhixu.ui.components.ZhixuTextField
import app.zhixu.ui.components.ZhixuSwitch
import app.zhixu.ui.components.ZhixuTopAppBar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SyncScreen(
    contentPadding: PaddingValues,
    vaultRootUri: Uri?,
    repository: VaultRepository,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current
    val syncPrefs = remember(context) { SyncPreferences(context) }
    val vaultSyncPrefs = remember(context) { VaultSyncPreferences(context.applicationContext) }
    val vaultSyncConfig by
        vaultSyncPrefs.config
            .map { it as VaultSyncConfig? }
            .collectAsState(initial = null)

    val accountPrefs = remember(context) { AccountPreferences(context.applicationContext) }
    val accountState by accountPrefs.state.collectAsState(
        initial = AccountState(token = "", username = "", userId = 0L, email = "", avatarUri = "", avatarUpdatedAtMs = 0L),
    )
    val savedConfig by
        syncPrefs.webDavConfig
            .map { it as WebDavConfig? }
            .collectAsState(initial = null)

    val dividerColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)

    val loadedVaultSyncConfig = vaultSyncConfig
    val loadedWebDavConfig = savedConfig
    if (loadedVaultSyncConfig == null || loadedWebDavConfig == null) {
        Scaffold(
            contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0, 0, 0, 0),
            containerColor = MaterialTheme.colorScheme.background,
            topBar = {
                Column {
                    ZhixuTopAppBar(
                        containerColor = MaterialTheme.colorScheme.surface,
                        title = { Text(stringResource(R.string.settings_section_sync), style = MaterialTheme.typography.titleMedium) },
                        navigationIcon = {
                            val isRtl = LocalLayoutDirection.current == LayoutDirection.Rtl
                            ZhixuIconButton(onClick = onBack) {
                                Icon(
                                    painter = painterResource(if (isRtl) app.zhixu.ui.Ionicons.ArrowForward else app.zhixu.ui.Ionicons.ArrowBack),
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
            Box(
                modifier =
                    Modifier
                        .padding(innerPadding)
                        .fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }
        }
        return
    }

    var enabled by remember { mutableStateOf(loadedWebDavConfig.enabled) }
    var baseUrl by remember { mutableStateOf(loadedWebDavConfig.baseUrl) }
    var username by remember { mutableStateOf(loadedWebDavConfig.username) }
    var password by remember { mutableStateOf(loadedWebDavConfig.password) }
    var remoteRoot by remember { mutableStateOf(loadedWebDavConfig.remoteRoot) }
    var includeIndexSqlite by remember { mutableStateOf(loadedWebDavConfig.includeIndexSqlite) }
    var testStatus by remember { mutableStateOf<String?>(null) }
    var showPassword by remember { mutableStateOf(false) }

    LaunchedEffect(loadedWebDavConfig) {
        enabled = loadedWebDavConfig.enabled
        baseUrl = loadedWebDavConfig.baseUrl
        username = loadedWebDavConfig.username
        password = loadedWebDavConfig.password
        remoteRoot = loadedWebDavConfig.remoteRoot
        includeIndexSqlite = loadedWebDavConfig.includeIndexSqlite
    }

    suspend fun resolveRelativeUri(rootUri: Uri, relativePath: String): Uri? =
        withContext(Dispatchers.IO) {
            if (rootUri.scheme.equals("file", ignoreCase = true)) {
                val rootPath = rootUri.path ?: return@withContext null
                val file = File(rootPath, relativePath)
                return@withContext if (file.exists()) Uri.fromFile(file) else null
            }
            val root = vaultRootToDocumentFile(context, rootUri) ?: return@withContext null
            var current = root
            val parts = relativePath.split('/').filter { it.isNotBlank() }
            for (part in parts) {
                val next = current.listFiles().firstOrNull { it.name == part } ?: return@withContext null
                current = next
            }
            current.uri
        }

    var lastWebDavSummaryLoading by remember { mutableStateOf(false) }
    var lastWebDavSummary by remember { mutableStateOf<String?>(null) }

    suspend fun reloadLastWebDavSummary() {
        val root = vaultRootUri ?: return
        lastWebDavSummaryLoading = true
        lastWebDavSummary =
            runCatching {
                val uri = resolveRelativeUri(root, ".zhixu/sync/webdav_last_summary.json") ?: return@runCatching null
                val raw = repository.readText(uri).trim()
                if (raw.isBlank()) null else formatWebDavLastSummaryForDisplay(raw, context)
            }.getOrNull()
        lastWebDavSummaryLoading = false
    }

    LaunchedEffect(vaultRootUri) {
        reloadLastWebDavSummary()
    }

    var showSyncLog by remember { mutableStateOf(false) }
    var syncLogLoading by remember { mutableStateOf(false) }
    var syncLogText by remember { mutableStateOf("") }
    var syncLogLoadedAtMs by remember { mutableStateOf(0L) }

    LaunchedEffect(showSyncLog, vaultRootUri) {
        val root = vaultRootUri ?: return@LaunchedEffect
        if (!showSyncLog) return@LaunchedEffect
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
        contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0, 0, 0, 0),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            Column {
                ZhixuTopAppBar(
                    containerColor = MaterialTheme.colorScheme.surface,
                    title = { Text(stringResource(R.string.settings_section_sync), style = MaterialTheme.typography.titleMedium) },
                    navigationIcon = {
                        val isRtl = LocalLayoutDirection.current == LayoutDirection.Rtl
                        ZhixuIconButton(onClick = onBack) {
                            Icon(
                                painter = painterResource(if (isRtl) app.zhixu.ui.Ionicons.ArrowForward else app.zhixu.ui.Ionicons.ArrowBack),
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
                    .padding(innerPadding)
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .background(MaterialTheme.colorScheme.background)
                    .fillMaxSize()
                    .imePadding(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(0.dp),
        ) {
            if (vaultRootUri == null) {
                item { Text(stringResource(R.string.workshop_no_vault)) }
                return@LazyColumn
            }

            item {
                Spacer(modifier = Modifier.height(12.dp))
            }

            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = MaterialTheme.shapes.extraLarge,
                ) {
                    ListItem(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .clickable { showSyncLog = true },
                        headlineContent = { Text(stringResource(R.string.sync_logs_title)) },
                        supportingContent = {
                            val hint =
                                if (syncLogLoading) stringResource(R.string.sync_logs_loading)
                                else if (syncLogText.isBlank()) stringResource(R.string.sync_logs_empty)
                                else stringResource(R.string.sync_logs_tap_to_view)
                            Text(hint)
                        },
                        leadingContent = {
                            Icon(
                                painter = painterResource(app.zhixu.ui.Ionicons.DocumentText),
                                contentDescription = null,
                            )
                        },
                        trailingContent = {
                            Icon(
                                painter = painterResource(app.zhixu.ui.Ionicons.ChevronForward),
                                contentDescription = null,
                            )
                        },
                    )
                }
            }

            if (loadedVaultSyncConfig.location == VaultStorageLocation.OFFICIAL_SERVER) {
                item { Spacer(modifier = Modifier.height(14.dp)) }

                item {
                    Text(
                        text = stringResource(R.string.official_sync_title),
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
                        Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
                            Text(
                                text = stringResource(R.string.vault_settings_official_desc_fmt, OfficialSync.BASE_URL),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            val msg =
                                if (!accountState.isLoggedIn) stringResource(R.string.official_sync_not_logged_in)
                                else stringResource(R.string.official_sync_logged_in_as_fmt, accountState.username.ifBlank { "-" })
                            Text(
                                text = msg,
                                color = if (accountState.isLoggedIn) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error,
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            RowSwitch(
                                title = stringResource(R.string.webdav_include_index_sqlite),
                                checked = includeIndexSqlite,
                                onCheckedChange = { checked ->
                                    includeIndexSqlite = checked
                                    scope.launch { syncPrefs.setIncludeIndexSqlite(checked) }
                                },
                            )

                            if (testStatus != null) {
                                Spacer(modifier = Modifier.height(10.dp))
                                Text(testStatus!!, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }

                            Spacer(modifier = Modifier.height(12.dp))
                            Button(
                                modifier = Modifier.fillMaxWidth(),
                                enabled = accountState.isLoggedIn,
                                shape = RoundedCornerShape(8.dp),
                                onClick = {
                                    val root = vaultRootUri ?: return@Button
                                    val token = accountState.token
                                    scope.launch {
                                        testStatus = context.getString(R.string.official_sync_syncing)
                                        val engine = OfficialVaultSyncEngine(context, repository)
                                        val summary =
                                            runCatching {
                                                engine.syncVault(
                                                    rootUri = root,
                                                    baseUrl = OfficialSync.BASE_URL,
                                                    token = token,
                                                    includeIndexSqlite = includeIndexSqlite,
                                                )
                                            }.getOrElse { e ->
                                                testStatus =
                                                    context.getString(
                                                        R.string.official_sync_failed,
                                                        e.message ?: e.javaClass.simpleName,
                                                    )
                                                return@launch
                                            }
                                        testStatus =
                                            context.getString(
                                                R.string.official_sync_ok,
                                                summary.uploaded,
                                                summary.downloaded,
                                                summary.deletedRemote,
                                                summary.deletedLocal,
                                                summary.conflicts,
                                                summary.failed,
                                            )
                                    }
                                },
                            ) { Text(stringResource(R.string.official_sync_now)) }
                        }
                    }
                }

                return@LazyColumn
            }

            item { Spacer(modifier = Modifier.height(14.dp)) }

            item {
                Text(
                    text = "WebDAV",
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
                    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)) {
                        RowSwitch(
                            title = stringResource(R.string.webdav_enable),
                            checked = enabled,
                            onCheckedChange = { enabled = it },
                        )
                    }
                }
            }

            if (enabled) {
                item { Spacer(modifier = Modifier.height(12.dp)) }
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        shape = MaterialTheme.shapes.extraLarge,
                    ) {
                        Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(0.dp)) {
                            ZhixuTextField(
                                value = baseUrl,
                                onValueChange = { baseUrl = it },
                                label = { Text(stringResource(R.string.webdav_base_url)) },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                            )
                            HorizontalDivider(color = dividerColor)
                            ZhixuTextField(
                                value = username,
                                onValueChange = { username = it },
                                label = { Text(stringResource(R.string.webdav_username)) },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                            )
                            HorizontalDivider(color = dividerColor)
                            ZhixuTextField(
                                value = password,
                                onValueChange = { password = it },
                                label = { Text(stringResource(R.string.webdav_password)) },
                                singleLine = true,
                                visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                                trailingIcon = {
                                    ZhixuPasswordToggleIconButton(
                                        show = showPassword,
                                        onClick = { showPassword = !showPassword },
                                    )
                                },
                                modifier = Modifier.fillMaxWidth(),
                            )
                            HorizontalDivider(color = dividerColor)
                            ZhixuTextField(
                                value = remoteRoot,
                                onValueChange = { remoteRoot = it },
                                label = { Text(stringResource(R.string.webdav_remote_root)) },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                            )
                            HorizontalDivider(color = dividerColor)

                            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)) {
                                RowSwitch(
                                    title = stringResource(R.string.webdav_include_index_sqlite),
                                    checked = includeIndexSqlite,
                                    onCheckedChange = { includeIndexSqlite = it },
                                )

                                if (!lastWebDavSummary.isNullOrBlank()) {
                                    Spacer(Modifier.height(12.dp))
                                    Text(lastWebDavSummary!!, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                } else if (lastWebDavSummaryLoading) {
                                    Spacer(Modifier.height(12.dp))
                                    Text("...", color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }

                                Spacer(Modifier.height(12.dp))
                                if (testStatus != null) {
                                    Text(testStatus!!, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Spacer(Modifier.height(12.dp))
                                }

                                Button(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(8.dp),
                                    onClick = {
                                        val config =
                                            WebDavConfig(
                                                enabled = enabled,
                                                baseUrl = baseUrl.trim(),
                                                username = username.trim(),
                                                password = password,
                                                remoteRoot = remoteRoot.trim().ifBlank { "/" },
                                                includeIndexSqlite = includeIndexSqlite,
                                                conflictStrategy = app.zhixu.data.WebDavConflictStrategy.KEEP_BOTH,
                                            )
                                        scope.launch {
                                            syncPrefs.saveWebDavConfig(config)
                                            testStatus = context.getString(R.string.webdav_saved)
                                        }
                                    },
                                ) { Text(stringResource(R.string.action_save)) }

                                Button(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(8.dp),
                                    onClick = {
                                        val config =
                                            WebDavConfig(
                                                enabled = enabled,
                                                baseUrl = baseUrl.trim(),
                                                username = username.trim(),
                                                password = password,
                                                remoteRoot = remoteRoot.trim().ifBlank { "/" },
                                                includeIndexSqlite = includeIndexSqlite,
                                                conflictStrategy = app.zhixu.data.WebDavConflictStrategy.KEEP_BOTH,
                                            )
                                        scope.launch {
                                            testStatus = context.getString(R.string.webdav_testing)
                                            val result = WebDavClient.testConnection(config)
                                            testStatus =
                                                if (result.success) {
                                                    context.getString(R.string.webdav_test_ok, result.statusCode)
                                                } else {
                                                    context.getString(R.string.webdav_test_fail, result.statusCode, result.message)
                                                }
                                        }
                                    },
                                ) { Text(stringResource(R.string.webdav_test)) }

                                Button(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(8.dp),
                                    onClick = {
                                        val root = vaultRootUri
                                        val config =
                                            WebDavConfig(
                                                enabled = enabled,
                                                baseUrl = baseUrl.trim(),
                                                username = username.trim(),
                                                password = password,
                                                remoteRoot = remoteRoot.trim().ifBlank { "/" },
                                                includeIndexSqlite = includeIndexSqlite,
                                                conflictStrategy = app.zhixu.data.WebDavConflictStrategy.KEEP_BOTH,
                                            )
                                        scope.launch {
                                            testStatus = context.getString(R.string.webdav_syncing)
                                            val engine = WebDavSyncEngine(context, repository)
                                            val summary =
                                                runCatching { engine.syncVault(root, config) }
                                                    .getOrElse { e ->
                                                        testStatus =
                                                            context.getString(
                                                                R.string.webdav_sync_failed,
                                                                e.message ?: e.javaClass.simpleName,
                                                            )
                                                        reloadLastWebDavSummary()
                                                        return@launch
                                                    }
                                            testStatus =
                                                context.getString(
                                                    R.string.webdav_sync_ok,
                                                    summary.uploaded,
                                                    summary.downloaded,
                                                    summary.conflicts,
                                                    summary.failed,
                                                )
                                            reloadLastWebDavSummary()
                                        }
                                    },
                                ) { Text(stringResource(R.string.webdav_sync_now)) }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showSyncLog) {
        val exportText = if (syncLogText.isBlank()) stringResource(R.string.sync_logs_empty) else syncLogText
        AlertDialog(
            modifier = ZhixuDialogDefaults.modifier(),
            onDismissRequest = { showSyncLog = false },
            properties = ZhixuDialogDefaults.properties,
            title = { Text(stringResource(R.string.sync_logs_title)) },
            text = {
                Box(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(420.dp),
                ) {
                    if (syncLogLoading) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                    } else {
                        val v = rememberScrollState()
                        val h = rememberScrollState()
                        Text(
                            text = exportText,
                            modifier =
                                Modifier
                                    .fillMaxSize()
                                    .verticalScroll(v)
                                    .horizontalScroll(h),
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            },
            confirmButton = {
                androidx.compose.foundation.layout.Row {
                    TextButton(
                        enabled = !syncLogLoading,
                        onClick = {
                            clipboard.setText(AnnotatedString(exportText))
                        },
                    ) { Text(stringResource(R.string.sync_logs_copy)) }
                    TextButton(
                        enabled = !syncLogLoading,
                        onClick = {
                            val intent =
                                Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_SUBJECT, context.getString(R.string.sync_logs_title))
                                    putExtra(Intent.EXTRA_TEXT, exportText)
                                }
                            context.startActivity(Intent.createChooser(intent, context.getString(R.string.sync_logs_export)))
                        },
                    ) { Text(stringResource(R.string.sync_logs_export)) }
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showSyncLog = false },
                ) { Text(stringResource(R.string.sync_logs_close)) }
            },
        )
    }
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

private fun formatEpochMs(ms: Long): String {
    if (ms <= 0L) return ""
    return runCatching {
        Instant
            .ofEpochMilli(ms)
            .atZone(ZoneId.systemDefault())
            .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))
    }.getOrElse { ms.toString() }
}

private fun formatWebDavLastSummaryForDisplay(raw: String, context: Context): String {
    val obj = runCatching { JSONObject(raw) }.getOrNull() ?: return raw
    val ok = obj.optBoolean("ok", false)
    val endedAt = obj.optLong("endedAt", 0L)
    val timeText = formatEpochMs(endedAt)
    return if (!ok) {
        val error = obj.optString("error").ifBlank { "-" }
        val msg = context.getString(R.string.webdav_sync_failed, error)
        if (timeText.isNotBlank()) "$timeText  $msg" else msg
    } else {
        val uploaded = obj.optInt("uploaded", 0)
        val downloaded = obj.optInt("downloaded", 0)
        val conflicts = obj.optInt("conflicts", 0)
        val failed = obj.optInt("failed", 0)
        val msg = context.getString(R.string.webdav_sync_ok, uploaded, downloaded, conflicts, failed)
        if (timeText.isNotBlank()) "$timeText  $msg" else msg
    }
}

@Composable
private fun RowSwitch(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    androidx.compose.foundation.layout.Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title)
        ZhixuSwitch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
