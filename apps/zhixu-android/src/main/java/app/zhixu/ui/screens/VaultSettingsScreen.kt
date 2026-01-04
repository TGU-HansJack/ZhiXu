package app.zhixu.ui.screens

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
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
import app.zhixu.R
import app.zhixu.data.ProPreferences
import app.zhixu.data.ThirdPartyServiceConfig
import app.zhixu.data.VaultPreferences
import app.zhixu.data.VaultRepository
import app.zhixu.data.VaultStorageLocation
import app.zhixu.data.VaultSyncConfig
import app.zhixu.data.VaultSyncPreferences
import app.zhixu.data.appManagedVaultRootUri
import app.zhixu.data.vaultRootToDocumentFile
import app.zhixu.sync.OfficialSync
import app.zhixu.ui.Ionicons
import app.zhixu.ui.ZhixuTopBarIconSize
import app.zhixu.ui.components.ZhixuIconButton
import app.zhixu.ui.components.ZhixuPasswordToggleIconButton
import app.zhixu.ui.components.ZhixuTextField
import app.zhixu.ui.components.ZhixuSwitch
import app.zhixu.ui.components.ZhixuTopAppBar
import kotlinx.coroutines.delay
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
    val proPrefs = remember(context) { ProPreferences(context.applicationContext) }
    val isProEnabled by proPrefs.isProEnabled.collectAsState(initial = false)
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
                    ZhixuTopAppBar(
                        containerColor = MaterialTheme.colorScheme.surface,
                        title = { Text(stringResource(R.string.settings_section_vault), style = MaterialTheme.typography.titleMedium) },
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

    var location by remember { mutableStateOf(savedConfig.location) }
    var thirdPartyUrl by remember { mutableStateOf(savedConfig.thirdParty.url) }
    var thirdPartyUsername by remember { mutableStateOf(savedConfig.thirdParty.username) }
    var thirdPartyPassword by remember { mutableStateOf(savedConfig.thirdParty.password) }
    var thirdPartyE2eeEnabled by remember { mutableStateOf(savedConfig.thirdParty.e2eeEnabled) }
    var thirdPartyE2eeMasterPassword by remember { mutableStateOf(savedConfig.thirdParty.e2eeMasterPassword) }
    var showThirdPartyPassword by remember { mutableStateOf(false) }
    var showMasterPassword by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf<String?>(null) }
    var lastSavedAtMs by remember { mutableStateOf(0L) }
    var showJustSaved by remember { mutableStateOf(false) }

    var vaultSizeBytes by remember { mutableStateOf<Long?>(null) }
    var vaultSizeLoading by remember { mutableStateOf(false) }

    LaunchedEffect(lastSavedAtMs) {
        if (lastSavedAtMs <= 0L) return@LaunchedEffect
        showJustSaved = true
        delay(1_200)
        showJustSaved = false
    }

    fun notifySaved() {
        lastSavedAtMs = System.currentTimeMillis()
        Toast.makeText(context, context.getString(R.string.vault_settings_saved), Toast.LENGTH_SHORT).show()
    }

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
                        statusMessage = it.message ?: it.javaClass.simpleName
                        return@launch
                    }
                vaultPrefs.setVaultRootUri(uri.toString())
                syncPrefs.setLocation(VaultStorageLocation.LOCAL)
                statusMessage = null
                notifySaved()
            }
        }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            Column {
                ZhixuTopAppBar(
                    containerColor = MaterialTheme.colorScheme.surface,
                    title = { Text(stringResource(R.string.settings_section_vault), style = MaterialTheme.typography.titleMedium) },
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
                StorageLocationCards(
                    selected = location,
                    isProEnabled = isProEnabled,
                    onSelected = { next ->
                        location = next
                        scope.launch {
                            syncPrefs.setLocation(next)
                            if (next != VaultStorageLocation.LOCAL) {
                                val uri = appManagedVaultRootUri(context.applicationContext)
                                runCatching { repository.ensureVaultStructure(uri) }
                                vaultPrefs.setVaultRootUri(uri.toString())
                                statusMessage = null
                                notifySaved()
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
                        val isAppPrivateDir =
                            root?.scheme.equals("file", ignoreCase = true) &&
                                root?.path?.replace('\\', '/')?.endsWith("/ZhixuVault") == true

                        val displayName =
                            when {
                                root == null -> stringResource(R.string.settings_vault_not_selected)
                                folderName.isNotBlank() -> folderName
                                else -> root.toString()
                            }
                        val kindText =
                            when {
                                root == null -> null
                                isAppPrivateDir -> stringResource(R.string.vault_settings_dir_kind_private)
                                else -> stringResource(R.string.vault_settings_dir_kind_custom)
                            }

                        val actualPath =
                            when {
                                root == null -> null
                                root.scheme.equals("file", ignoreCase = true) -> root.path
                                else -> root.toString()
                            }

                        var showActualPath by remember(root) { mutableStateOf(false) }

                        OutlinedCard(modifier = Modifier.fillMaxWidth()) {
                            Column(
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .clickable(enabled = actualPath != null) { showActualPath = !showActualPath }
                                        .padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        val label =
                                            buildString {
                                                append(stringResource(R.string.vault_settings_storage_dir_fmt, displayName))
                                                if (!kindText.isNullOrBlank() && root != null) {
                                                    append(" (")
                                                    append(kindText)
                                                    append(")")
                                                }
                                            }
                                        Text(text = label, style = MaterialTheme.typography.bodyMedium)
                                    }
                                    TextButton(onClick = { folderLauncher.launch(null) }) {
                                        Text(stringResource(R.string.vault_settings_change_location))
                                    }
                                }

                                AnimatedVisibility(
                                    visible = showActualPath && !actualPath.isNullOrBlank(),
                                    enter = fadeIn(),
                                    exit = fadeOut(),
                                ) {
                                    Text(
                                        text = stringResource(R.string.vault_settings_actual_path_fmt, actualPath.orEmpty()),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        style = MaterialTheme.typography.bodySmall,
                                    )
                                }
                            }
                        }
                    }
                    item {
                        val sizeText =
                            when {
                                vaultRootUri == null -> "-"
                                vaultSizeLoading -> stringResource(R.string.vault_settings_calculating)
                                vaultSizeBytes == null -> "-"
                                else -> formatBytes(vaultSizeBytes!!)
                            }

                        OutlinedCard(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(12.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Text(
                                            text = stringResource(R.string.vault_settings_local_size_fmt, sizeText),
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                        if (showJustSaved) {
                                            Icon(
                                                painter = painterResource(Ionicons.CheckmarkCircle),
                                                contentDescription = null,
                                                modifier = Modifier.size(16.dp),
                                                tint = MaterialTheme.colorScheme.primary,
                                            )
                                        }
                                    }

                                    if (!statusMessage.isNullOrBlank()) {
                                        Spacer(Modifier.height(4.dp))
                                        Text(
                                            text = statusMessage!!,
                                            color = MaterialTheme.colorScheme.error,
                                            style = MaterialTheme.typography.bodySmall,
                                        )
                                    }
                                }

                                TextButton(
                                    enabled = vaultRootUri != null && !vaultSizeLoading,
                                    onClick = {
                                        val root = vaultRootUri ?: return@TextButton
                                        scope.launch { refreshVaultSize(root) }
                                    },
                                ) {
                                    if (vaultSizeLoading) {
                                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                    } else {
                                        Text(stringResource(R.string.action_refresh))
                                    }
                                }
                            }
                        }
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
                            text = stringResource(R.string.vault_settings_official_desc),
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
                            ZhixuTextField(
                                value = thirdPartyUrl,
                                onValueChange = { thirdPartyUrl = it },
                                label = { Text(stringResource(R.string.vault_settings_third_party_url)) },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                                modifier = Modifier.fillMaxWidth(),
                            )
                            HorizontalDivider(color = dividerColor)
                            ZhixuTextField(
                                value = thirdPartyUsername,
                                onValueChange = { thirdPartyUsername = it },
                                label = { Text(stringResource(R.string.vault_settings_third_party_username)) },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                                modifier = Modifier.fillMaxWidth(),
                            )
                            HorizontalDivider(color = dividerColor)
                            ZhixuTextField(
                                value = thirdPartyPassword,
                                onValueChange = { thirdPartyPassword = it },
                                label = { Text(stringResource(R.string.vault_settings_third_party_password)) },
                                singleLine = true,
                                visualTransformation =
                                    if (showThirdPartyPassword) VisualTransformation.None else PasswordVisualTransformation(),
                                trailingIcon = {
                                    ZhixuPasswordToggleIconButton(
                                        show = showThirdPartyPassword,
                                        onClick = { showThirdPartyPassword = !showThirdPartyPassword },
                                    )
                                },
                                modifier = Modifier.fillMaxWidth(),
                            )
                            HorizontalDivider(color = dividerColor)

                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(stringResource(R.string.vault_settings_third_party_e2ee))
                                ZhixuSwitch(
                                    checked = thirdPartyE2eeEnabled,
                                    onCheckedChange = { thirdPartyE2eeEnabled = it },
                                )
                            }

                            if (thirdPartyE2eeEnabled) {
                                HorizontalDivider(color = dividerColor)
                                ZhixuTextField(
                                    value = thirdPartyE2eeMasterPassword,
                                    onValueChange = { thirdPartyE2eeMasterPassword = it },
                                    label = { Text(stringResource(R.string.vault_settings_third_party_master_password)) },
                                    singleLine = true,
                                    visualTransformation =
                                        if (showMasterPassword) VisualTransformation.None else PasswordVisualTransformation(),
                                    trailingIcon = {
                                        ZhixuPasswordToggleIconButton(
                                            show = showMasterPassword,
                                            onClick = { showMasterPassword = !showMasterPassword },
                                        )
                                    },
                                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
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
                                    statusMessage = null
                                    notifySaved()
                                }
                            },
                        ) { Text(stringResource(R.string.action_save)) }
                    }
                }
            }
        }
    }
}

@Composable
private fun StorageLocationCards(
    selected: VaultStorageLocation,
    isProEnabled: Boolean,
    onSelected: (VaultStorageLocation) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        StorageLocationCard(
            selected = selected == VaultStorageLocation.LOCAL,
            title = stringResource(R.string.vault_settings_location_local),
            description = stringResource(R.string.vault_settings_local_title),
            enabled = true,
            onClick = { onSelected(VaultStorageLocation.LOCAL) },
        )
        StorageLocationCard(
            selected = selected == VaultStorageLocation.OFFICIAL_SERVER,
            title = stringResource(R.string.vault_settings_location_official),
            description = if (isProEnabled) stringResource(R.string.vault_settings_official_desc) else "需要启用知序 PRO",
            enabled = isProEnabled,
            onClick = { onSelected(VaultStorageLocation.OFFICIAL_SERVER) },
        )
        StorageLocationCard(
            selected = selected == VaultStorageLocation.THIRD_PARTY_SERVICE,
            title = stringResource(R.string.vault_settings_location_third_party),
            description = if (isProEnabled) stringResource(R.string.vault_settings_third_party_desc) else "需要启用知序 PRO",
            enabled = isProEnabled,
            onClick = { onSelected(VaultStorageLocation.THIRD_PARTY_SERVICE) },
        )
    }
}

@Composable
private fun StorageLocationCard(
    selected: Boolean,
    title: String,
    description: String,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val alpha = if (enabled) 1f else 0.4f
    val containerColor =
        if (selected) {
            MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
        } else {
            CardDefaults.outlinedCardColors().containerColor
        }

    OutlinedCard(
        modifier = Modifier.fillMaxWidth().alpha(alpha),
        onClick = { if (enabled) onClick() },
        colors = CardDefaults.outlinedCardColors(containerColor = containerColor),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(text = title, style = MaterialTheme.typography.titleSmall)
                Text(text = description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (selected) {
                Icon(
                    painter = painterResource(Ionicons.CheckmarkCircle),
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

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
