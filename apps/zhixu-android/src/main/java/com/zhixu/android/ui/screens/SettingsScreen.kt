package com.zhixu.android.ui.screens

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import com.zhixu.android.R
import com.zhixu.android.data.SyncPreferences
import com.zhixu.android.data.WebDavConfig
import com.zhixu.android.data.WebDavClient
import com.zhixu.android.data.VaultRepository
import androidx.compose.ui.platform.LocalContext
import com.zhixu.android.sync.WebDavSyncEngine
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(
    contentPadding: PaddingValues,
    vaultRootUri: Uri?,
    repository: VaultRepository,
    onChangeVault: () -> Unit,
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

    LaunchedEffect(savedConfig) {
        enabled = savedConfig.enabled
        baseUrl = savedConfig.baseUrl
        username = savedConfig.username
        password = savedConfig.password
        remoteRoot = savedConfig.remoteRoot
        includeIndexSqlite = savedConfig.includeIndexSqlite
    }

    LazyColumn(
        modifier = Modifier
            .padding(contentPadding)
            .background(MaterialTheme.colorScheme.background)
            .fillMaxSize()
            .imePadding(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
            item {
                Text(text = stringResource(R.string.settings_section_vault))
            }
            item {
                Text(text = vaultRootUri?.toString() ?: stringResource(R.string.settings_vault_not_selected))
            }
            item {
                Button(onClick = onChangeVault) { Text(stringResource(R.string.settings_change_vault)) }
            }

            item { HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp)) }

            item { Text(text = stringResource(R.string.settings_section_sync)) }
            item { Text(text = stringResource(R.string.settings_sync_placeholder)) }
            item {
                RowSwitch(
                    title = stringResource(R.string.webdav_enable),
                    checked = enabled,
                    onCheckedChange = { enabled = it },
                )
            }

            if (enabled) {
                item {
                    OutlinedTextField(
                        value = baseUrl,
                        onValueChange = { baseUrl = it },
                        label = { Text(stringResource(R.string.webdav_base_url)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                item {
                    OutlinedTextField(
                        value = remoteRoot,
                        onValueChange = { remoteRoot = it },
                        label = { Text(stringResource(R.string.webdav_remote_root)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                item {
                    OutlinedTextField(
                        value = username,
                        onValueChange = { username = it },
                        label = { Text(stringResource(R.string.webdav_username)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                item {
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text(stringResource(R.string.webdav_password)) },
                        singleLine = true,
                        visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                item {
                    TextButton(onClick = { showPassword = !showPassword }) {
                        Text(if (showPassword) stringResource(R.string.webdav_hide_password) else stringResource(R.string.webdav_show_password))
                    }
                }
                item {
                    RowSwitch(
                        title = stringResource(R.string.webdav_include_index_sqlite),
                        checked = includeIndexSqlite,
                        onCheckedChange = { includeIndexSqlite = it },
                    )
                }

                if (testStatus != null) {
                    item { Text(testStatus!!) }
                }

                item {
                    Button(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = {
                            val config = WebDavConfig(
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
                }

                item {
                    Button(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = {
                            val config = WebDavConfig(
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
                }

                item {
                    Button(
                        modifier = Modifier.fillMaxWidth(),
                        enabled = vaultRootUri != null,
                        onClick = {
                            val root = vaultRootUri ?: return@Button
                            val config = WebDavConfig(
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
                                val summary = runCatching { engine.syncVault(root, config) }
                                    .getOrElse { e ->
                                        testStatus = context.getString(R.string.webdav_sync_failed, e.message ?: e.javaClass.simpleName)
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
            }
    }
}

@Composable
private fun RowSwitch(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    androidx.compose.foundation.layout.Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(title)
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
