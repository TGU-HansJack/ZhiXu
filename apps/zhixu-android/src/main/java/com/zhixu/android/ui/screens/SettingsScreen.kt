package com.zhixu.android.ui.screens

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.CloudUpload
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.zhixu.android.R
import com.zhixu.android.data.SyncPreferences
import com.zhixu.android.data.TaskStats
import com.zhixu.android.data.WebDavClient
import com.zhixu.android.data.WebDavConfig
import com.zhixu.android.data.VaultRepository
import com.zhixu.android.sync.WebDavSyncEngine
import kotlinx.coroutines.launch
import java.time.LocalDate

@Composable
fun SettingsScreen(
    contentPadding: PaddingValues,
    vaultRootUri: Uri?,
    repository: VaultRepository,
    onChangeVault: () -> Unit,
    onOpenWorkshop: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val syncPrefs = remember(context) { SyncPreferences(context) }
    val savedConfig by syncPrefs.webDavConfig.collectAsState(
        initial = WebDavConfig(
            enabled = false,
            baseUrl = "",
            username = "",
            password = "",
            remoteRoot = "/",
            includeIndexSqlite = false,
        ),
    )

    var enabled by remember { mutableStateOf(savedConfig.enabled) }
    var baseUrl by remember { mutableStateOf(savedConfig.baseUrl) }
    var username by remember { mutableStateOf(savedConfig.username) }
    var password by remember { mutableStateOf(savedConfig.password) }
    var remoteRoot by remember { mutableStateOf(savedConfig.remoteRoot) }
    var includeIndexSqlite by remember { mutableStateOf(savedConfig.includeIndexSqlite) }
    var testStatus by remember { mutableStateOf<String?>(null) }
    var showPassword by remember { mutableStateOf(false) }
    var docCount by remember { mutableStateOf<Int?>(null) }
    var taskStats by remember { mutableStateOf<TaskStats?>(null) }

    LaunchedEffect(savedConfig) {
        enabled = savedConfig.enabled
        baseUrl = savedConfig.baseUrl
        username = savedConfig.username
        password = savedConfig.password
        remoteRoot = savedConfig.remoteRoot
        includeIndexSqlite = savedConfig.includeIndexSqlite
    }

    LaunchedEffect(vaultRootUri) {
        if (vaultRootUri == null) {
            docCount = null
            taskStats = null
        } else {
            docCount = runCatching { repository.listMarkdownDocs(vaultRootUri).size }.getOrNull()
            taskStats = runCatching { repository.taskStats(vaultRootUri) }.getOrNull()
        }
    }

    val dividerColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)

    LazyColumn(
        modifier =
            Modifier
                .padding(contentPadding)
                .background(MaterialTheme.colorScheme.background)
                .fillMaxSize()
                .imePadding(),
        contentPadding = PaddingValues(0.dp),
        verticalArrangement = Arrangement.spacedBy(0.dp),
    ) {
        item {
            Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        modifier = Modifier.size(56.dp),
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surfaceVariant,
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(text = "Z", style = MaterialTheme.typography.titleLarge)
                        }
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = "Zhixu", style = MaterialTheme.typography.headlineSmall)
                        Text(text = "ID: —", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }

                Spacer(Modifier.height(12.dp))
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    StatCell(title = "使用天数", value = "—", modifier = Modifier.weight(1f))
                    StatDivider()
                    StatCell(title = "文档篇数", value = (docCount?.toString() ?: "—"), modifier = Modifier.weight(1f))
                    StatDivider()
                    StatCell(title = "编辑字数", value = "—", modifier = Modifier.weight(1f))
                }
            }
            HorizontalDivider(color = dividerColor)
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    val done = taskStats?.done ?: 0
                    val total = taskStats?.total ?: 0
                    Text(
                        text = stringResource(R.string.todo_done_fmt, done, total),
                        style = MaterialTheme.typography.headlineSmall,
                    )
                }
                val levels = remember(taskStats) { buildGitHubHeatmapLevels(taskStats?.donePerDay) }
                GitHubHeatmap(levels = levels, rows = 7, cols = 14)
            }
            HorizontalDivider(color = dividerColor)
        }

        item {
            SettingsNavRow(
                icon = Icons.Outlined.Menu,
                title = stringResource(R.string.settings_section_vault),
                subtitle = vaultRootUri?.toString() ?: stringResource(R.string.settings_vault_not_selected),
                onClick = onChangeVault,
            )
            HorizontalDivider(color = dividerColor)
            SettingsNavRow(
                icon = Icons.Outlined.Extension,
                title = stringResource(R.string.settings_section_workshop),
                subtitle = stringResource(R.string.settings_workshop_desc),
                enabled = vaultRootUri != null,
                onClick = onOpenWorkshop,
            )
            HorizontalDivider(color = dividerColor)
        }

        item {
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_section_sync)) },
                supportingContent = { Text(stringResource(R.string.settings_sync_placeholder)) },
                leadingContent = { Icon(imageVector = Icons.Outlined.CloudUpload, contentDescription = null) },
            )
            HorizontalDivider(color = dividerColor)
            RowSwitch(
                title = stringResource(R.string.webdav_enable),
                checked = enabled,
                onCheckedChange = { enabled = it },
            )
            HorizontalDivider(color = dividerColor)
        }

        if (enabled) {
            item {
                Column(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(0.dp)) {
                    TextField(
                        value = baseUrl,
                        onValueChange = { baseUrl = it },
                        label = { Text(stringResource(R.string.webdav_base_url)) },
                        singleLine = true,
                        colors =
                            TextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                                disabledContainerColor = Color.Transparent,
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent,
                            ),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    HorizontalDivider(color = dividerColor)
                    TextField(
                        value = username,
                        onValueChange = { username = it },
                        label = { Text(stringResource(R.string.webdav_username)) },
                        singleLine = true,
                        colors =
                            TextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                                disabledContainerColor = Color.Transparent,
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent,
                            ),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    HorizontalDivider(color = dividerColor)
                    TextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text(stringResource(R.string.webdav_password)) },
                        singleLine = true,
                        visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            TextButton(onClick = { showPassword = !showPassword }) {
                                Text(if (showPassword) stringResource(R.string.action_hide) else stringResource(R.string.action_show))
                            }
                        },
                        colors =
                            TextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                                disabledContainerColor = Color.Transparent,
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent,
                            ),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    HorizontalDivider(color = dividerColor)
                    TextField(
                        value = remoteRoot,
                        onValueChange = { remoteRoot = it },
                        label = { Text(stringResource(R.string.webdav_remote_root)) },
                        singleLine = true,
                        colors =
                            TextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                                disabledContainerColor = Color.Transparent,
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent,
                            ),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    HorizontalDivider(color = dividerColor)

                    RowSwitch(
                        title = stringResource(R.string.webdav_include_index_sqlite),
                        checked = includeIndexSqlite,
                        onCheckedChange = { includeIndexSqlite = it },
                    )

                    Spacer(Modifier.height(12.dp))
                    if (testStatus != null) {
                        Text(testStatus!!, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(12.dp))
                    }

                    Button(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = {
                            val config =
                                WebDavConfig(
                                    enabled = enabled,
                                    baseUrl = baseUrl.trim(),
                                    username = username.trim(),
                                    password = password,
                                    remoteRoot = remoteRoot.trim().ifBlank { "/" },
                                    includeIndexSqlite = includeIndexSqlite,
                                )
                            scope.launch {
                                syncPrefs.saveWebDavConfig(config)
                                testStatus = context.getString(R.string.webdav_saved)
                            }
                        },
                    ) { Text(stringResource(R.string.action_save)) }

                    Button(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = {
                            val config =
                                WebDavConfig(
                                    enabled = enabled,
                                    baseUrl = baseUrl.trim(),
                                    username = username.trim(),
                                    password = password,
                                    remoteRoot = remoteRoot.trim().ifBlank { "/" },
                                    includeIndexSqlite = includeIndexSqlite,
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
                        enabled = vaultRootUri != null,
                        onClick = {
                            val root = vaultRootUri ?: return@Button
                            val config =
                                WebDavConfig(
                                    enabled = enabled,
                                    baseUrl = baseUrl.trim(),
                                    username = username.trim(),
                                    password = password,
                                    remoteRoot = remoteRoot.trim().ifBlank { "/" },
                                    includeIndexSqlite = includeIndexSqlite,
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
                            }
                        },
                    ) { Text(stringResource(R.string.webdav_sync_now)) }
                }
                HorizontalDivider(color = dividerColor)
            }
        }
    }
}

@Composable
private fun RowSwitch(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title)
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun SettingsNavRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String? = null,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    ListItem(
        modifier =
            Modifier
                .fillMaxWidth()
                .let { m -> if (enabled) m.clickable(onClick = onClick) else m },
        leadingContent = { Icon(imageVector = icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
        headlineContent = { Text(title) },
        supportingContent = {
            if (!subtitle.isNullOrBlank()) {
                Text(subtitle, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        },
        trailingContent = { Icon(imageVector = Icons.Outlined.ChevronRight, contentDescription = null) },
    )
}

@Composable
private fun StatCell(
    title: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(title, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(4.dp))
        Text(value, style = MaterialTheme.typography.headlineMedium)
    }
}

@Composable
private fun StatDivider() {
    Spacer(
        Modifier
            .width(1.dp)
            .height(42.dp)
            .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.8f)),
    )
}

@Composable
private fun GitHubHeatmap(
    levels: IntArray,
    rows: Int,
    cols: Int,
    modifier: Modifier = Modifier,
) {
    val size = 11.dp
    val gap = 4.dp
    val shape = androidx.compose.foundation.shape.RoundedCornerShape(3.dp)
    val colors =
        listOf(
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f),
            MaterialTheme.colorScheme.primary.copy(alpha = 0.22f),
            MaterialTheme.colorScheme.primary.copy(alpha = 0.42f),
            MaterialTheme.colorScheme.primary.copy(alpha = 0.65f),
            MaterialTheme.colorScheme.primary.copy(alpha = 0.90f),
        )

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(gap)) {
        for (r in 0 until rows) {
            Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                for (c in 0 until cols) {
                    val idx = c * rows + r
                    val level = levels.getOrNull(idx)?.coerceIn(0, 4) ?: 0
                    Box(
                        modifier =
                            Modifier
                                .size(size)
                                .background(color = colors[level], shape = shape),
                    )
                }
            }
        }
    }
}

private fun buildGitHubHeatmapLevels(
    donePerDay: Map<LocalDate, Int>?,
    rows: Int = 7,
    cols: Int = 14,
): IntArray {
    val days = rows * cols
    val out = IntArray(days) { 0 }
    if (days <= 0) return out
    val counts = donePerDay ?: emptyMap()

    val today = LocalDate.now()
    val start = today.minusDays((days - 1).toLong())
    var row = start.dayOfWeek.value % 7 // Sunday=0
    var col = 0

    var max = 0
    for (i in 0 until days) {
        val date = start.plusDays(i.toLong())
        val v = counts[date] ?: 0
        if (v > max) max = v
    }

    for (i in 0 until days) {
        val date = start.plusDays(i.toLong())
        val v = counts[date] ?: 0
        val level =
            when {
                v <= 0 -> 0
                max <= 1 -> 1
                else -> (1 + ((v - 1) * 3 / (max - 1))).coerceIn(1, 4)
            }

        val idx = col * rows + row
        if (idx in out.indices) out[idx] = level

        row++
        if (row == rows) {
            row = 0
            col++
            if (col == cols) break
        }
    }

    return out
}
