package com.zhixu.android.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.documentfile.provider.DocumentFile
import com.zhixu.android.R
import com.zhixu.android.data.ThirdPartyServiceConfig
import com.zhixu.android.data.VaultPreferences
import com.zhixu.android.data.VaultRepository
import com.zhixu.android.data.VaultStorageLocation
import com.zhixu.android.data.VaultSyncConfig
import com.zhixu.android.data.VaultSyncPreferences
import com.zhixu.android.data.appManagedVaultRootUri
import com.zhixu.android.data.vaultRootToDocumentFile
import com.zhixu.android.sync.OfficialSync
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.map
import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.pow

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VaultSettingsScreen(
    contentPadding: PaddingValues,
    vaultRootUri: Uri?,
    repository: VaultRepository,
    vaultPrefs: VaultPreferences,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val syncPrefs = remember(context) { VaultSyncPreferences(context.applicationContext) }
    val saved by
        syncPrefs.config
            .map { it as VaultSyncConfig? }
            .collectAsState(initial = null)

    val dividerColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)

    val savedConfig = saved
    if (savedConfig == null) {
        Scaffold(
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            containerColor = MaterialTheme.colorScheme.background,
            topBar = {
                Column {
                    TopAppBar(
                        windowInsets = TopAppBarDefaults.windowInsets,
                        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
                        title = { Text(stringResource(R.string.settings_section_vault)) },
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

    var location by remember { mutableStateOf(savedConfig.location) }
    var thirdPartyUrl by remember { mutableStateOf(savedConfig.thirdParty.url) }
    var thirdPartyUsername by remember { mutableStateOf(savedConfig.thirdParty.username) }
    var thirdPartyPassword by remember { mutableStateOf(savedConfig.thirdParty.password) }
    var thirdPartyE2eeEnabled by remember { mutableStateOf(savedConfig.thirdParty.e2eeEnabled) }
    var thirdPartyE2eeMasterPassword by remember { mutableStateOf(savedConfig.thirdParty.e2eeMasterPassword) }
    var showThirdPartyPassword by remember { mutableStateOf(false) }
    var showMasterPassword by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }

    var vaultSizeBytes by remember { mutableStateOf<Long?>(null) }
    var vaultSizeLoading by remember { mutableStateOf(false) }

    LaunchedEffect(savedConfig) {
        location = savedConfig.location
        thirdPartyUrl = savedConfig.thirdParty.url
        thirdPartyUsername = savedConfig.thirdParty.username
        thirdPartyPassword = savedConfig.thirdParty.password
        thirdPartyE2eeEnabled = savedConfig.thirdParty.e2eeEnabled
        thirdPartyE2eeMasterPassword = savedConfig.thirdParty.e2eeMasterPassword
    }

    suspend fun refreshVaultSize(root: Uri) {
        vaultSizeLoading = true
        vaultSizeBytes = runCatching { repository.computeVaultTotalSizeBytes(root) }.getOrNull()
        vaultSizeLoading = false
    }

    LaunchedEffect(vaultRootUri, location) {
        if (location != VaultStorageLocation.LOCAL) return@LaunchedEffect
        val root = vaultRootUri ?: return@LaunchedEffect
        refreshVaultSize(root)
    }

    val folderLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.OpenDocumentTree(),
        ) { uri: Uri? ->
            if (uri == null) return@rememberLauncherForActivityResult
            val flags =
                Intent.FLAG_GRANT_READ_URI_PERMISSION or
                    Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            runCatching { context.contentResolver.takePersistableUriPermission(uri, flags) }

            scope.launch {
                runCatching { repository.ensureVaultStructure(uri) }
                    .onFailure {
                        status = it.message ?: it.javaClass.simpleName
                        return@launch
                    }
                vaultPrefs.setVaultRootUri(uri.toString())
                syncPrefs.setLocation(VaultStorageLocation.LOCAL)
                status = context.getString(R.string.vault_settings_saved)
            }
        }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            Column {
                TopAppBar(
                    windowInsets = TopAppBarDefaults.windowInsets,
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
                    title = { Text(stringResource(R.string.settings_section_vault)) },
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
        androidx.compose.foundation.lazy.LazyColumn(
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
            item {
                Text(
                    text = stringResource(R.string.vault_settings_storage_location),
                    style = MaterialTheme.typography.titleMedium,
                )
            }

            item {
                StorageLocationRow(
                    selected = location,
                    onSelected = { next ->
                        location = next
                        scope.launch {
                            syncPrefs.setLocation(next)
                            if (next != VaultStorageLocation.LOCAL) {
                                val uri = appManagedVaultRootUri(context.applicationContext)
                                runCatching { repository.ensureVaultStructure(uri) }
                                vaultPrefs.setVaultRootUri(uri.toString())
                                status = context.getString(R.string.vault_settings_saved)
                            }
                        }
                    },
                )
            }

            item { HorizontalDivider(color = dividerColor) }

            when (location) {
                VaultStorageLocation.LOCAL -> {
                    item {
                        Text(
                            text = stringResource(R.string.vault_settings_local_title),
                            style = MaterialTheme.typography.titleMedium,
                        )
                    }
                    item {
                        val root = vaultRootUri
                        val folderName = root?.let { vaultRootToDocumentFile(context, it)?.name }.orEmpty()
                        val pathText =
                            when {
                                root == null -> stringResource(R.string.settings_vault_not_selected)
                                folderName.isNotBlank() -> "$folderName\n${root}"
                                else -> root.toString()
                            }
                        Text(pathText, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    item {
                        Button(
                            modifier = Modifier.fillMaxWidth(),
                            onClick = { folderLauncher.launch(null) },
                        ) { Text(stringResource(R.string.vault_settings_choose_local_folder)) }
                    }
                    item {
                        val sizeText =
                            when {
                                vaultRootUri == null -> "-"
                                vaultSizeLoading -> stringResource(R.string.vault_settings_calculating)
                                vaultSizeBytes == null -> "-"
                                else -> formatBytes(vaultSizeBytes!!)
                            }
                        Text(
                            text = stringResource(R.string.vault_settings_local_size_fmt, sizeText),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    item {
                        OutlinedButton(
                            modifier = Modifier.fillMaxWidth(),
                            enabled = vaultRootUri != null && !vaultSizeLoading,
                            onClick = {
                                val root = vaultRootUri ?: return@OutlinedButton
                                scope.launch { refreshVaultSize(root) }
                            },
                        ) { Text(stringResource(R.string.action_refresh)) }
                    }
                }

                VaultStorageLocation.OFFICIAL_SERVER -> {
                    item {
                        Text(
                            text = stringResource(R.string.vault_settings_official_title),
                            style = MaterialTheme.typography.titleMedium,
                        )
                    }
                    item {
                        Text(
                            text = stringResource(R.string.vault_settings_official_desc_fmt, OfficialSync.BASE_URL),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                VaultStorageLocation.THIRD_PARTY_SERVICE -> {
                    item {
                        Text(
                            text = stringResource(R.string.vault_settings_third_party_title),
                            style = MaterialTheme.typography.titleMedium,
                        )
                    }
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
                            TextField(
                                value = thirdPartyUrl,
                                onValueChange = { thirdPartyUrl = it },
                                label = { Text(stringResource(R.string.vault_settings_third_party_url)) },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                                colors = transparentTextFieldColors(),
                                modifier = Modifier.fillMaxWidth(),
                            )
                            HorizontalDivider(color = dividerColor)
                            TextField(
                                value = thirdPartyUsername,
                                onValueChange = { thirdPartyUsername = it },
                                label = { Text(stringResource(R.string.vault_settings_third_party_username)) },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                                colors = transparentTextFieldColors(),
                                modifier = Modifier.fillMaxWidth(),
                            )
                            HorizontalDivider(color = dividerColor)
                            TextField(
                                value = thirdPartyPassword,
                                onValueChange = { thirdPartyPassword = it },
                                label = { Text(stringResource(R.string.vault_settings_third_party_password)) },
                                singleLine = true,
                                visualTransformation =
                                    if (showThirdPartyPassword) VisualTransformation.None else PasswordVisualTransformation(),
                                trailingIcon = {
                                    TextButton(onClick = { showThirdPartyPassword = !showThirdPartyPassword }) {
                                        Text(if (showThirdPartyPassword) stringResource(R.string.action_hide) else stringResource(R.string.action_show))
                                    }
                                },
                                colors = transparentTextFieldColors(),
                                modifier = Modifier.fillMaxWidth(),
                            )
                            HorizontalDivider(color = dividerColor)

                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(stringResource(R.string.vault_settings_third_party_e2ee))
                                Switch(
                                    checked = thirdPartyE2eeEnabled,
                                    onCheckedChange = { thirdPartyE2eeEnabled = it },
                                )
                            }

                            if (thirdPartyE2eeEnabled) {
                                HorizontalDivider(color = dividerColor)
                                TextField(
                                    value = thirdPartyE2eeMasterPassword,
                                    onValueChange = { thirdPartyE2eeMasterPassword = it },
                                    label = { Text(stringResource(R.string.vault_settings_third_party_master_password)) },
                                    singleLine = true,
                                    visualTransformation =
                                        if (showMasterPassword) VisualTransformation.None else PasswordVisualTransformation(),
                                    trailingIcon = {
                                        TextButton(onClick = { showMasterPassword = !showMasterPassword }) {
                                            Text(if (showMasterPassword) stringResource(R.string.action_hide) else stringResource(R.string.action_show))
                                        }
                                    },
                                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                                    colors = transparentTextFieldColors(),
                                    modifier = Modifier.fillMaxWidth(),
                                )
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    text = stringResource(R.string.vault_settings_third_party_e2ee_warning),
                                    color = MaterialTheme.colorScheme.error,
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                        }
                    }
                    item {
                        Button(
                            modifier = Modifier.fillMaxWidth(),
                            onClick = {
                                val config =
                                    VaultSyncConfig(
                                        location = VaultStorageLocation.THIRD_PARTY_SERVICE,
                                        thirdParty =
                                            ThirdPartyServiceConfig(
                                                url = thirdPartyUrl.trim(),
                                                username = thirdPartyUsername.trim(),
                                                password = thirdPartyPassword,
                                                e2eeEnabled = thirdPartyE2eeEnabled,
                                                e2eeMasterPassword = thirdPartyE2eeMasterPassword,
                                            ),
                                    )
                                scope.launch {
                                    syncPrefs.saveConfig(config)
                                    status = context.getString(R.string.vault_settings_saved)
                                }
                            },
                        ) { Text(stringResource(R.string.action_save)) }
                    }
                }
            }

            if (!status.isNullOrBlank()) {
                item {
                    Spacer(Modifier.height(8.dp))
                    Text(status!!, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun StorageLocationRow(
    selected: VaultStorageLocation,
    onSelected: (VaultStorageLocation) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        LocationButton(
            selected = selected == VaultStorageLocation.LOCAL,
            text = stringResource(R.string.vault_settings_location_local),
            onClick = { onSelected(VaultStorageLocation.LOCAL) },
            modifier = Modifier.weight(1f),
        )
        LocationButton(
            selected = selected == VaultStorageLocation.OFFICIAL_SERVER,
            text = stringResource(R.string.vault_settings_location_official),
            onClick = { onSelected(VaultStorageLocation.OFFICIAL_SERVER) },
            modifier = Modifier.weight(1f),
        )
        LocationButton(
            selected = selected == VaultStorageLocation.THIRD_PARTY_SERVICE,
            text = stringResource(R.string.vault_settings_location_third_party),
            onClick = { onSelected(VaultStorageLocation.THIRD_PARTY_SERVICE) },
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun LocationButton(
    selected: Boolean,
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (selected) {
        Button(modifier = modifier, onClick = onClick) { Text(text) }
    } else {
        OutlinedButton(modifier = modifier, onClick = onClick) { Text(text) }
    }
}

@Composable
private fun transparentTextFieldColors() =
    TextFieldDefaults.colors(
        focusedContainerColor = Color.Transparent,
        unfocusedContainerColor = Color.Transparent,
        disabledContainerColor = Color.Transparent,
        focusedIndicatorColor = Color.Transparent,
        unfocusedIndicatorColor = Color.Transparent,
    )

private fun formatBytes(bytes: Long): String {
    if (bytes < 0) return "-"
    if (bytes < 1024) return "${bytes} B"
    val unit = 1024.0
    val exp = (log10(bytes.toDouble()) / log10(unit)).toInt().coerceAtLeast(0)
    val pre = "KMGTPE"[exp - 1].toString()
    val value = bytes / unit.pow(exp.toDouble())
    val rounded = if (abs(value) >= 10) String.format("%.0f", value) else String.format("%.1f", value)
    return "$rounded ${pre}B"
}
