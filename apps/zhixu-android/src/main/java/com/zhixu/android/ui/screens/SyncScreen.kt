package com.zhixu.android.ui.screens

import android.net.Uri
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.zhixu.android.R
import com.zhixu.android.data.AccountPreferences
import com.zhixu.android.data.AccountState
import com.zhixu.android.data.SyncPreferences
import com.zhixu.android.data.ThirdPartyServiceConfig
import com.zhixu.android.data.VaultStorageLocation
import com.zhixu.android.data.VaultSyncConfig
import com.zhixu.android.data.VaultSyncPreferences
import com.zhixu.android.data.WebDavClient
import com.zhixu.android.data.WebDavConfig
import com.zhixu.android.data.VaultRepository
import com.zhixu.android.sync.OfficialSync
import com.zhixu.android.sync.OfficialVaultSyncEngine
import com.zhixu.android.sync.WebDavSyncEngine
import kotlinx.coroutines.launch

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
    val syncPrefs = remember(context) { SyncPreferences(context) }
    val vaultSyncPrefs = remember(context) { VaultSyncPreferences(context.applicationContext) }
    val vaultSyncConfig by vaultSyncPrefs.config.collectAsState(
        initial =
            VaultSyncConfig(
                location = VaultStorageLocation.LOCAL,
                thirdParty =
                    ThirdPartyServiceConfig(
                        url = "",
                        username = "",
                        password = "",
                        e2eeEnabled = false,
                        e2eeMasterPassword = "",
                    ),
            ),
    )

    val accountPrefs = remember(context) { AccountPreferences(context.applicationContext) }
    val accountState by accountPrefs.state.collectAsState(
        initial = AccountState(token = "", username = "", userId = 0L, deviceId = ""),
    )
    val savedConfig by syncPrefs.webDavConfig.collectAsState(
        initial =
            WebDavConfig(
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

    val dividerColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)

    Scaffold(
        contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0, 0, 0, 0),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            Column {
                TopAppBar(
                    windowInsets = TopAppBarDefaults.windowInsets,
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
                    title = { Text(stringResource(R.string.settings_section_sync)) },
                    navigationIcon = {
                        val isRtl = LocalLayoutDirection.current == LayoutDirection.Rtl
                        IconButton(onClick = onBack) {
                            Icon(
                                painter = painterResource(if (isRtl) com.zhixu.android.ui.Ionicons.ArrowForward else com.zhixu.android.ui.Ionicons.ArrowBack),
                                contentDescription = stringResource(R.string.action_back),
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
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (vaultRootUri == null) {
                item { Text(stringResource(R.string.workshop_no_vault)) }
                return@LazyColumn
            }

            item {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.settings_section_sync)) },
                    supportingContent = { Text(stringResource(R.string.settings_sync_placeholder)) },
                    leadingContent = { Icon(painter = painterResource(com.zhixu.android.ui.Ionicons.Sync), contentDescription = null) },
                )
            }

            if (vaultSyncConfig.location == VaultStorageLocation.OFFICIAL_SERVER) {
                item {
                    ListItem(
                        headlineContent = { Text(stringResource(R.string.official_sync_title)) },
                        supportingContent = { Text(stringResource(R.string.vault_settings_official_desc_fmt, OfficialSync.BASE_URL)) },
                    )
                }

                item {
                    val msg =
                        if (!accountState.isLoggedIn) stringResource(R.string.official_sync_not_logged_in)
                        else stringResource(R.string.official_sync_logged_in_as_fmt, accountState.username.ifBlank { "-" })
                    Text(
                        text = msg,
                        color = if (accountState.isLoggedIn) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error,
                    )
                }

                item {
                    RowSwitch(
                        title = stringResource(R.string.webdav_include_index_sqlite),
                        checked = includeIndexSqlite,
                        onCheckedChange = { checked ->
                            includeIndexSqlite = checked
                            scope.launch { syncPrefs.setIncludeIndexSqlite(checked) }
                        },
                    )
                }

                if (testStatus != null) {
                    item { Text(testStatus!!, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }

                item {
                    Button(
                        modifier = Modifier.fillMaxWidth(),
                        enabled = accountState.isLoggedIn,
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

                return@LazyColumn
            }

            item {
                RowSwitch(
                    title = stringResource(R.string.webdav_enable),
                    checked = enabled,
                    onCheckedChange = { enabled = it },
                )
            }

            if (!enabled) return@LazyColumn

            item {
                Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(0.dp)) {
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
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title)
        androidx.compose.material3.Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
