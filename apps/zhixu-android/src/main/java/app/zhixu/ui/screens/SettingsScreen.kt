package app.zhixu.ui.screens

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Android
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.LightMode
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
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.zhixu.R
import app.zhixu.data.AccountPreferences
import app.zhixu.data.AccountState
import app.zhixu.data.ProPreferences
import app.zhixu.data.SyncPreferences
import app.zhixu.data.VaultPreferences
import app.zhixu.data.VaultRepository
import app.zhixu.data.VaultStorageLocation
import app.zhixu.data.VaultSyncPreferences
import app.zhixu.data.WebDavClient
import app.zhixu.data.WebDavAutomationSettings
import app.zhixu.data.WebDavConfig
import app.zhixu.data.WebDavConflictStrategy
import app.zhixu.data.UiPreferences
import app.zhixu.data.UiSettings
import app.zhixu.data.UiThemeMode
import app.zhixu.data.vaultRootToDocumentFile
import app.zhixu.ui.Ionicons
import app.zhixu.ui.components.ZhixuDialogDefaults
import app.zhixu.ui.components.ZhixuIconButton
import app.zhixu.ui.components.ZhixuTopAppBar
import app.zhixu.ui.components.ZhixuTextField
import app.zhixu.ui.components.ZhixuSwitch
import app.zhixu.sync.OfficialSync
import app.zhixu.sync.OfficialVaultSyncEngine
import app.zhixu.sync.SyncServerSyncRuntime
import app.zhixu.sync.VaultAutoSync
import app.zhixu.sync.WebDavAutoSyncStateStore
import app.zhixu.sync.WebDavSyncEngine
import app.zhixu.sync.WebDavSyncPlan
import app.zhixu.sync.WebDavPlannedOpKind
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.pow
import org.json.JSONObject
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    contentPadding: PaddingValues,
    vaultRootUri: Uri?,
    showAccountManagement: Boolean = true,
    showWorkshop: Boolean = true,
    showAbout: Boolean = true,
    onOpenAiSettings: () -> Unit,
    onOpenWorkshop: () -> Unit,
    onOpenPomodoroSettings: () -> Unit,
    onOpenNotificationSettings: () -> Unit,
    onOpenDailyReminder: () -> Unit,
    onOpenReminderSound: () -> Unit,
    onOpenReminderVibration: () -> Unit,
    onOpenReminderPopup: () -> Unit,
    vaultPrefs: VaultPreferences,
    vaultSyncPrefs: VaultSyncPreferences,
    repository: VaultRepository,
    onOpenTermsOfUse: () -> Unit,
    onOpenPrivacyPolicy: () -> Unit,
    onOpenOpenSourceLicense: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val accountPrefs = remember(context) { AccountPreferences(context.applicationContext) }
    val accountState by accountPrefs.state.collectAsState(
        initial = AccountState(token = "", username = "", userId = 0L, email = "", avatarUri = "", avatarUpdatedAtMs = 0L),
    )

    val proPrefs = remember(context) { ProPreferences(context.applicationContext) }
    val isProEnabled by proPrefs.isProEnabled.collectAsState(initial = false)

    val uiPrefs = remember(context) { UiPreferences(context.applicationContext) }
    val uiSettings by
        uiPrefs.settings.collectAsState(
            initial =
                UiSettings(
                    languageTag = "",
                    themeMode = UiThemeMode.SYSTEM,
                    strictDocListPreview = true,
                ),
        )

    var showAccountDialog by remember { mutableStateOf(false) }
    var showProDialog by remember { mutableStateOf(false) }
    var showSpaceDialog by remember { mutableStateOf(false) }
    var showEditorDialog by remember { mutableStateOf(false) }
    var showUiDialog by remember { mutableStateOf(false) }
    var showSyncDialog by remember { mutableStateOf(false) }
    var showPermissionsDialog by remember { mutableStateOf(false) }
    var showAboutDialog by remember { mutableStateOf(false) }
    var showLogsDialog by remember { mutableStateOf(false) }

    fun toast(text: String) {
        Toast.makeText(context, text, Toast.LENGTH_SHORT).show()
    }

    val dividerColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)

    Column(
        modifier =
            Modifier
                .padding(contentPadding)
                .windowInsetsPadding(WindowInsets.navigationBars),
    ) {
        LazyColumn(
            modifier =
                Modifier
                    .background(MaterialTheme.colorScheme.background)
                    .fillMaxSize()
                    .imePadding(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(0.dp),
        ) {
            item {
                SettingsSectionTitle(text = stringResource(R.string.settings_section_options))
            }

            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = MaterialTheme.shapes.extraLarge,
                ) {
                    val proSubtitle =
                        if (isProEnabled) {
                            stringResource(R.string.settings_pro_enabled)
                        } else {
                            stringResource(R.string.settings_pro_disabled)
                        }
                    val accountSubtitle =
                        if (accountState.isLoggedIn) {
                            context.getString(R.string.account_logged_in_as_fmt, accountState.username.ifBlank { "Zhixu" })
                        } else {
                            context.getString(R.string.account_not_logged_in_short)
                        }
                    if (showAccountManagement) {
                        SettingsRow(
                            iconRes = Ionicons.User,
                            title = stringResource(R.string.account_manage_title),
                            subtitle = accountSubtitle,
                            onClick = { showAccountDialog = true },
                        )

                        HorizontalDivider(color = dividerColor)
                    }
                    SettingsRow(
                        iconRes = Ionicons.Sparkles,
                        title = stringResource(R.string.settings_pro_title),
                        subtitle = proSubtitle,
                        onClick = { showProDialog = true },
                    )

                    HorizontalDivider(color = dividerColor)
                    SettingsRow(
                        iconRes = Ionicons.DocumentOutline,
                        title = stringResource(R.string.editor_title),
                        subtitle = stringResource(R.string.editor_settings_subtitle),
                        onClick = { showEditorDialog = true },
                    )

                    HorizontalDivider(color = dividerColor)
                    val vaultRowSubtitle =
                        if (vaultRootUri == null) {
                            stringResource(R.string.settings_vault_not_selected)
                        } else {
                            val folderName = vaultRootToDocumentFile(context, vaultRootUri)?.name.orEmpty()
                            if (folderName.isNotBlank()) folderName else vaultRootUri.toString()
                        }
                    SettingsRow(
                        iconRes = Ionicons.Vault,
                        title = stringResource(R.string.settings_section_vault),
                        subtitle = vaultRowSubtitle,
                        onClick = { showSpaceDialog = true },
                    )

                    HorizontalDivider(color = dividerColor)
                    SettingsRow(
                        iconRes = Ionicons.Cloud,
                        title = stringResource(R.string.settings_section_sync),
                        subtitle = stringResource(R.string.settings_sync_subtitle),
                        enabled = vaultRootUri != null,
                        onClick = { showSyncDialog = true },
                    )

                    HorizontalDivider(color = dividerColor)
                    SettingsRow(
                        iconRes = Ionicons.LayersOutline,
                        title = stringResource(R.string.settings_placeholder_ui),
                        subtitle = uiSettingsSummaryText(context, uiSettings),
                        onClick = { showUiDialog = true },
                    )

                    HorizontalDivider(color = dividerColor)
                    SettingsRow(
                        iconRes = Ionicons.AccessibilityOutline,
                        title = stringResource(R.string.settings_permissions_title),
                        subtitle = stringResource(R.string.settings_permissions_subtitle),
                        onClick = { showPermissionsDialog = true },
                    )

                    HorizontalDivider(color = dividerColor)
                    SettingsRow(
                        iconRes = R.drawable.ic_hi_sparkles_outline,
                        title = stringResource(R.string.settings_ai_title),
                        subtitle =
                            if (isProEnabled) {
                                stringResource(R.string.settings_ai_subtitle)
                            } else {
                                stringResource(R.string.settings_requires_pro)
                            },
                        enabled = isProEnabled,
                        onClick = onOpenAiSettings,
                    )

                    if (showAbout) {
                        HorizontalDivider(color = dividerColor)
                        SettingsRow(
                            iconRes = Ionicons.HelpCircleOutline,
                            title = stringResource(R.string.settings_placeholder_about),
                            subtitle = stringResource(R.string.settings_about_subtitle),
                            onClick = { showAboutDialog = true },
                        )
                    }
                }
            }

            if (showWorkshop) {
                item { Spacer(modifier = Modifier.height(14.dp)) }

                item {
                    SettingsSectionTitle(text = stringResource(R.string.settings_section_extensions))
                }

                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        shape = MaterialTheme.shapes.extraLarge,
                    ) {
                        val workshopEnabled = vaultRootUri != null && isProEnabled
                        val workshopSubtitle =
                            when {
                                vaultRootUri == null -> stringResource(R.string.workshop_no_vault)
                                !isProEnabled -> stringResource(R.string.settings_requires_pro)
                                else -> stringResource(R.string.settings_workshop_subtitle)
                            }
                        SettingsRow(
                            iconRes = Ionicons.Workshop,
                            title = stringResource(R.string.settings_section_workshop),
                            subtitle = workshopSubtitle,
                            enabled = workshopEnabled,
                            onClick = onOpenWorkshop,
                        )
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(14.dp)) }

            item {
                SettingsSectionTitle(text = stringResource(R.string.settings_section_others))
            }

            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = MaterialTheme.shapes.extraLarge,
                ) {
                    SettingsRow(
                        iconRes = Ionicons.DocumentText,
                        title = stringResource(R.string.settings_logs_title),
                        subtitle = stringResource(R.string.settings_logs_subtitle),
                        onClick = { showLogsDialog = true },
                    )
                }
            }

            item { Spacer(modifier = Modifier.height(18.dp)) }
        }
    }

    if (showAccountDialog) {
        AccountManagementDialog(
            accountPrefs = accountPrefs,
            onDismiss = { showAccountDialog = false },
        )
    }

    if (showProDialog) {
        FullScreenDialog(onDismiss = { showProDialog = false }) {
            ProSettingsScreen(
                contentPadding = PaddingValues(0.dp),
                proPrefs = proPrefs,
                isProEnabled = isProEnabled,
                onBack = { showProDialog = false },
            )
        }
    }

    if (showSpaceDialog) {
        FullScreenDialog(onDismiss = { showSpaceDialog = false }) {
            SpaceSettingsScreen(
                contentPadding = PaddingValues(0.dp),
                vaultRootUri = vaultRootUri,
                vaultPrefs = vaultPrefs,
                vaultSyncPrefs = vaultSyncPrefs,
                repository = repository,
                onBack = { showSpaceDialog = false },
            )
        }
    }

    if (showEditorDialog) {
        FullScreenDialog(onDismiss = { showEditorDialog = false }) {
            EditorSettingsScreen(
                contentPadding = PaddingValues(0.dp),
                onBack = { showEditorDialog = false },
            )
        }
    }

    if (showUiDialog) {
        FullScreenDialog(onDismiss = { showUiDialog = false }) {
            UiSettingsFullScreen(
                contentPadding = PaddingValues(0.dp),
                uiPrefs = uiPrefs,
                uiSettings = uiSettings,
                onBack = { showUiDialog = false },
            )
        }
    }

    if (showSyncDialog) {
        FullScreenDialog(onDismiss = { showSyncDialog = false }) {
            SyncSettingsScreen(
                contentPadding = PaddingValues(0.dp),
                vaultRootUri = vaultRootUri,
                repository = repository,
                accountState = accountState,
                onBack = { showSyncDialog = false },
            )
        }
    }

    if (showPermissionsDialog) {
        FullScreenDialog(onDismiss = { showPermissionsDialog = false }) {
            PermissionsOverlayScreen(
                contentPadding = PaddingValues(0.dp),
                onBack = { showPermissionsDialog = false },
            )
        }
    }

    if (showAboutDialog) {
        FullScreenDialog(onDismiss = { showAboutDialog = false }) {
            AboutOverlayScreen(
                contentPadding = PaddingValues(0.dp),
                onBack = { showAboutDialog = false },
            )
        }
    }

    if (showLogsDialog) {
        FullScreenDialog(onDismiss = { showLogsDialog = false }) {
            LogsScreen(
                contentPadding = PaddingValues(0.dp),
                vaultRootUri = vaultRootUri,
                repository = repository,
                onBack = { showLogsDialog = false },
            )
        }
    }
}

@Composable
private fun SettingsSectionTitle(text: String) {
    Text(
        text = text,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontSize = 13.sp,
        modifier = Modifier.padding(horizontal = 4.dp, vertical = 6.dp),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SpaceSettingsScreen(
    contentPadding: PaddingValues,
    vaultRootUri: Uri?,
    vaultPrefs: VaultPreferences,
    vaultSyncPrefs: VaultSyncPreferences,
    repository: VaultRepository,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var vaultSizeBytes by remember { mutableStateOf<Long?>(null) }
    var vaultSizeLoading by remember { mutableStateOf(false) }
    var rebuildIndexLoading by remember { mutableStateOf(false) }
    var rebuildDirIndexLoading by remember { mutableStateOf(false) }
    var showRebuildIndexDialog by remember { mutableStateOf(false) }
    var showRebuildDirIndexDialog by remember { mutableStateOf(false) }

    suspend fun refreshVaultSize(root: Uri) {
        vaultSizeLoading = true
        vaultSizeBytes = runCatching { repository.computeVaultTotalSizeBytes(root) }.getOrNull()
        vaultSizeLoading = false
    }

    androidx.compose.runtime.LaunchedEffect(vaultRootUri) {
        val root = vaultRootUri ?: return@LaunchedEffect
        refreshVaultSize(root)
    }

    val folderLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.OpenDocumentTree(),
        ) { uri: Uri? ->
            if (uri == null) return@rememberLauncherForActivityResult
            val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            runCatching { context.contentResolver.takePersistableUriPermission(uri, flags) }
            scope.launch {
                runCatching { repository.ensureVaultStructure(uri) }
                    .onFailure { e ->
                        Toast.makeText(context, e.message ?: e.javaClass.simpleName, Toast.LENGTH_SHORT).show()
                        return@launch
                    }
                vaultPrefs.setVaultRootUri(uri.toString())
                vaultSyncPrefs.setLocation(VaultStorageLocation.LOCAL)
                Toast.makeText(context, context.getString(R.string.vault_settings_saved), Toast.LENGTH_SHORT).show()
                refreshVaultSize(uri)
            }
        }

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

    val dividerColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            Column {
                ZhixuTopAppBar(
                    containerColor = MaterialTheme.colorScheme.surface,
                    title = { Text(stringResource(R.string.settings_section_vault), style = MaterialTheme.typography.titleMedium) },
                    navigationIcon = {
                        ZhixuIconButton(onClick = onBack) {
                            Icon(
                                painter = painterResource(Ionicons.ArrowBack),
                                contentDescription = stringResource(R.string.action_back),
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    },
                )
                HorizontalDivider(color = dividerColor)
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
                SettingsSectionTitle(text = stringResource(R.string.settings_change_vault))
            }

            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = MaterialTheme.shapes.extraLarge,
                ) {
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
                            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
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

            item { Spacer(modifier = Modifier.height(10.dp)) }

            item {
                val sizeText =
                    when {
                        vaultRootUri == null -> "-"
                        vaultSizeLoading -> stringResource(R.string.vault_settings_calculating)
                        vaultSizeBytes == null -> "-"
                        else -> formatBytes(vaultSizeBytes!!)
                    }

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = MaterialTheme.shapes.extraLarge,
                ) {
                    ListItem(
                        modifier = Modifier.fillMaxWidth(),
                        leadingContent = {
                            Icon(
                                painter = painterResource(Ionicons.Vault),
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp),
                            )
                        },
                        headlineContent = { Text(stringResource(R.string.vault_settings_local_size_fmt, sizeText)) },
                        trailingContent = {
                            TextButton(
                                enabled = vaultRootUri != null && !vaultSizeLoading,
                                onClick = {
                                    val rootUri = vaultRootUri ?: return@TextButton
                                    scope.launch { refreshVaultSize(rootUri) }
                                },
                            ) {
                                if (vaultSizeLoading) {
                                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                } else {
                                    Text(stringResource(R.string.action_refresh))
                                }
                            }
                        },
                    )
                }
            }

            item { Spacer(modifier = Modifier.height(14.dp)) }

            item {
                SettingsSectionTitle(text = stringResource(R.string.vault_maintenance_title))
            }

            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = MaterialTheme.shapes.extraLarge,
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        ListItem(
                            modifier = Modifier.fillMaxWidth(),
                            leadingContent = {
                                Icon(
                                    painter = painterResource(Ionicons.RefreshOutline),
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(20.dp),
                                )
                            },
                            headlineContent = { Text(stringResource(R.string.vault_rebuild_search_index_title)) },
                            supportingContent = {
                                Text(
                                    text = stringResource(R.string.vault_rebuild_search_index_subtitle),
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            },
                            trailingContent = {
                                val running = rebuildIndexLoading || repository.isIndexBuildInProgress()
                                TextButton(
                                    enabled = vaultRootUri != null && !running,
                                    onClick = { showRebuildIndexDialog = true },
                                ) {
                                    if (running) {
                                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                    } else {
                                        Text(stringResource(R.string.action_rebuild))
                                    }
                                }
                            },
                        )

                        HorizontalDivider(color = dividerColor)

                        ListItem(
                            modifier = Modifier.fillMaxWidth(),
                            leadingContent = {
                                Icon(
                                    painter = painterResource(Ionicons.RefreshOutline),
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(20.dp),
                                )
                            },
                            headlineContent = { Text(stringResource(R.string.vault_rebuild_dir_index_title)) },
                            supportingContent = {
                                Text(
                                    text = stringResource(R.string.vault_rebuild_dir_index_subtitle),
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            },
                            trailingContent = {
                                val running = rebuildDirIndexLoading || repository.isDirIndexBuildInProgress()
                                TextButton(
                                    enabled = vaultRootUri != null && !running,
                                    onClick = { showRebuildDirIndexDialog = true },
                                ) {
                                    if (running) {
                                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                    } else {
                                        Text(stringResource(R.string.action_rebuild))
                                    }
                                }
                            },
                        )
                    }
                }
            }
        }
    }

    if (showRebuildIndexDialog) {
        AlertDialog(
            onDismissRequest = { showRebuildIndexDialog = false },
            title = { Text(stringResource(R.string.vault_rebuild_search_index_title)) },
            text = { Text(stringResource(R.string.vault_rebuild_search_index_confirm_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showRebuildIndexDialog = false
                        val root = vaultRootUri ?: return@TextButton
                        scope.launch {
                            rebuildIndexLoading = true
                            val ok =
                                withContext(Dispatchers.IO) {
                                    runCatching {
                                        repository.invalidateDocListCache(root)
                                        repository.rebuildIndex(rootUri = root, forceScan = true)
                                        true
                                    }.getOrDefault(false)
                                }
                            rebuildIndexLoading = false
                            Toast
                                .makeText(
                                    context,
                                    if (ok) context.getString(R.string.vault_rebuild_done) else context.getString(R.string.common_failed),
                                    Toast.LENGTH_SHORT,
                                ).show()
                        }
                    },
                ) { Text(stringResource(R.string.action_rebuild)) }
            },
            dismissButton = {
                TextButton(onClick = { showRebuildIndexDialog = false }) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }

    if (showRebuildDirIndexDialog) {
        AlertDialog(
            onDismissRequest = { showRebuildDirIndexDialog = false },
            title = { Text(stringResource(R.string.vault_rebuild_dir_index_title)) },
            text = { Text(stringResource(R.string.vault_rebuild_dir_index_confirm_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showRebuildDirIndexDialog = false
                        val root = vaultRootUri ?: return@TextButton
                        scope.launch {
                            rebuildDirIndexLoading = true
                            val ok =
                                withContext(Dispatchers.IO) {
                                    runCatching {
                                        repository.ensureDirIndexBuilt(root, force = true)
                                        true
                                    }.getOrDefault(false)
                                }
                            rebuildDirIndexLoading = false
                            Toast
                                .makeText(
                                    context,
                                    if (ok) context.getString(R.string.vault_rebuild_done) else context.getString(R.string.common_failed),
                                    Toast.LENGTH_SHORT,
                                ).show()
                        }
                    },
                ) { Text(stringResource(R.string.action_rebuild)) }
            },
            dismissButton = {
                TextButton(onClick = { showRebuildDirIndexDialog = false }) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun UiSettingsFullScreen(
    contentPadding: PaddingValues,
    uiPrefs: UiPreferences,
    uiSettings: UiSettings,
    onBack: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val dividerColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            Column {
                ZhixuTopAppBar(
                    containerColor = MaterialTheme.colorScheme.surface,
                    title = { Text(stringResource(R.string.settings_placeholder_ui), style = MaterialTheme.typography.titleMedium) },
                    navigationIcon = {
                        ZhixuIconButton(onClick = onBack) {
                            Icon(
                                painter = painterResource(Ionicons.ArrowBack),
                                contentDescription = stringResource(R.string.action_back),
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    },
                )
                HorizontalDivider(color = dividerColor)
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
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item { Spacer(modifier = Modifier.height(12.dp)) }

            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = MaterialTheme.shapes.extraLarge,
                ) {
                    @Composable
                    fun ModeCard(
                        mode: UiThemeMode,
                        label: String,
                        icon: androidx.compose.ui.graphics.vector.ImageVector,
                    ) {
                        val selected = uiSettings.themeMode == mode
                        val borderColor =
                            if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
                        val tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        Surface(
                            modifier =
                                Modifier
                                    .size(width = 96.dp, height = 76.dp)
                                    .clickable { scope.launch { uiPrefs.setThemeMode(mode) } },
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(2.dp, borderColor),
                            color = MaterialTheme.colorScheme.surface,
                        ) {
                            Column(
                                modifier = Modifier.fillMaxSize().padding(10.dp),
                                verticalArrangement = Arrangement.Center,
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                Icon(
                                    imageVector = icon,
                                    contentDescription = null,
                                    tint = tint,
                                    modifier = Modifier.size(28.dp),
                                )
                                Spacer(Modifier.height(8.dp))
                                Text(text = label, color = tint, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }

                    SettingsSectionTitle(text = stringResource(R.string.settings_ui_theme_title))
                    Row(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState())
                                .padding(start = 12.dp, end = 12.dp, bottom = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        ModeCard(UiThemeMode.SYSTEM, stringResource(R.string.settings_ui_theme_system), Icons.Outlined.Android)
                        ModeCard(UiThemeMode.LIGHT, stringResource(R.string.settings_ui_theme_light), Icons.Outlined.LightMode)
                        ModeCard(UiThemeMode.DARK, stringResource(R.string.settings_ui_theme_dark), Icons.Outlined.DarkMode)
                    }
                }
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
                                .clickable { scope.launch { uiPrefs.setStrictDocListPreview(!uiSettings.strictDocListPreview) } },
                        leadingContent = {
                            Icon(
                                painter = painterResource(Ionicons.EyeOutline),
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp),
                            )
                        },
                        headlineContent = { Text(stringResource(R.string.settings_ui_strict_preview_title)) },
                        supportingContent = {
                            Text(
                                stringResource(R.string.settings_ui_strict_preview_subtitle),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        },
                        trailingContent = {
                            ZhixuSwitch(
                                checked = uiSettings.strictDocListPreview,
                                onCheckedChange = { checked -> scope.launch { uiPrefs.setStrictDocListPreview(checked) } },
                            )
                        },
                    )
                }
            }

            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SettingsSectionTitle(text = stringResource(R.string.settings_language_title))

                    data class LanguageOption(val tag: String, val label: String)
                    val options =
                        listOf(
                            LanguageOption("", context.getString(R.string.settings_language_system)),
                            LanguageOption("zh-CN", context.getString(R.string.settings_language_zh_cn)),
                            LanguageOption("en", context.getString(R.string.settings_language_en)),
                        )
                    val selectedTag = uiSettings.languageTag.trim()
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        shape = MaterialTheme.shapes.extraLarge,
                    ) {
                        options.forEachIndexed { index, opt ->
                            val selected = selectedTag == opt.tag || (opt.tag.isBlank() && selectedTag.isBlank())
                            ListItem(
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .clickable { scope.launch { uiPrefs.setLanguageTag(opt.tag) } },
                                leadingContent = {
                                    Icon(
                                        painter = painterResource(Ionicons.LanguageOutline),
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(20.dp),
                                    )
                                },
                                headlineContent = { Text(opt.label) },
                                trailingContent = {
                                    if (selected) {
                                        Icon(
                                            painter = painterResource(Ionicons.CheckmarkCircle),
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(18.dp),
                                        )
                                    }
                                },
                            )
                            if (index != options.lastIndex) {
                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
                            }
                        }
                    }
                }
            }
        }
    }
}

private enum class SyncSettingsPage {
    Main,
    OfficialServer,
    WebDavService,
    WebDavFolderPair,
    WebDavRemoteFolderPicker,
    WebDavManual,
}

private const val WEBDAV_MANUAL_URL = "https://zhixu.app/docs/webdav/"

private data class WebDavRemoteFolder(
    val name: String,
    val path: String,
)

private data class WebDavPropfindEntry(
    val href: String,
    val isDir: Boolean,
)

private fun parseWebDavPropfind(xml: String): List<WebDavPropfindEntry> {
    val trimmed = xml.trimStart()
    if (trimmed.isBlank() || !trimmed.startsWith("<")) return emptyList()

    val parser = org.xmlpull.v1.XmlPullParserFactory.newInstance().newPullParser()
    parser.setInput(xml.reader())

    val out = ArrayList<WebDavPropfindEntry>()
    var href: String? = null
    var isDir: Boolean? = null

    fun flush() {
        val h = href ?: return
        out += WebDavPropfindEntry(href = h, isDir = isDir == true)
        href = null
        isDir = null
    }

    fun tagName(): String = parser.name.orEmpty().substringAfterLast(':').lowercase()

    var event = parser.eventType
    while (event != org.xmlpull.v1.XmlPullParser.END_DOCUMENT) {
        when (event) {
            org.xmlpull.v1.XmlPullParser.START_TAG -> {
                when (tagName()) {
                    "response" -> {
                        href = null
                        isDir = null
                    }
                    "href" -> href = parser.nextText()
                    "collection" -> isDir = true
                }
            }

            org.xmlpull.v1.XmlPullParser.END_TAG -> {
                if (tagName() == "response") flush()
            }
        }
        event = parser.next()
    }

    return out
}

// Convert a DAV:href into a path relative to the requested WebDAV base path.
// If the href is an absolute path outside the base path, ignore it to avoid accidentally treating
// the whole WebDAV root as the current folder.
private fun normalizeWebDavHrefToRelativePath(href: String, basePath: String): String? {
    val decoded = Uri.decode(href)
    val path = Uri.parse(decoded).path ?: decoded
    val trimmedBase = Uri.decode(basePath).trimEnd('/')
    val trimmedPath = path.trimEnd('/')
    return when {
        trimmedPath == trimmedBase -> ""
        trimmedPath.startsWith("$trimmedBase/") -> trimmedPath.removePrefix("$trimmedBase/").trimStart('/')
        else -> {
            if (trimmedPath.startsWith("/")) null else trimmedPath.trimStart('/')
        }
    }
}

@Composable
private fun WebDavAccountDialog(
    prefs: SyncPreferences,
    savedConfig: WebDavConfig,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var baseUrl by remember { mutableStateOf(savedConfig.baseUrl) }
    var username by remember { mutableStateOf(savedConfig.username) }
    var password by remember { mutableStateOf(savedConfig.password) }
    var showPassword by remember { mutableStateOf(false) }
    var connecting by remember { mutableStateOf(false) }
    var statusText by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        modifier = ZhixuDialogDefaults.modifier(),
        onDismissRequest = { if (!connecting) onDismiss() },
        properties = ZhixuDialogDefaults.properties,
        title = { Text(stringResource(R.string.webdav_account_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                ZhixuTextField(
                    value = baseUrl,
                    onValueChange = { baseUrl = it },
                    enabled = !connecting,
                    label = { Text(stringResource(R.string.webdav_base_url)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                ZhixuTextField(
                    value = username,
                    onValueChange = { username = it },
                    enabled = !connecting,
                    label = { Text(stringResource(R.string.webdav_username)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                ZhixuTextField(
                    value = password,
                    onValueChange = { password = it },
                    enabled = !connecting,
                    label = { Text(stringResource(R.string.webdav_password)) },
                    singleLine = true,
                    visualTransformation =
                        if (showPassword) {
                            androidx.compose.ui.text.input.VisualTransformation.None
                        } else {
                            androidx.compose.ui.text.input.PasswordVisualTransformation()
                        },
                    trailingIcon = {
                        ZhixuIconButton(onClick = { showPassword = !showPassword }) {
                            Icon(
                                painter = painterResource(if (showPassword) Ionicons.EyeOffOutline else Ionicons.EyeOutline),
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )

                if (connecting) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        Text(text = stringResource(R.string.webdav_testing), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                    }
                } else if (!statusText.isNullOrBlank()) {
                    Text(text = statusText!!, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                }
            }
        },
        confirmButton = {
            Button(
                enabled = !connecting,
                onClick = {
                    scope.launch {
                        connecting = true
                        statusText = null

                        val trimmedUrl = baseUrl.trim()
                        val trimmedUser = username.trim()
                        val probe =
                            savedConfig.copy(
                                enabled = true,
                                baseUrl = trimmedUrl,
                                username = trimmedUser,
                                password = password,
                                remoteRoot = "/",
                            )
                        val res = WebDavClient.testConnection(probe)
                        if (res.success) {
                            prefs.saveWebDavConfig(
                                savedConfig.copy(
                                    enabled = true,
                                    baseUrl = trimmedUrl,
                                    username = trimmedUser,
                                    password = password,
                                ),
                            )
                            Toast.makeText(context, context.getString(R.string.webdav_saved), Toast.LENGTH_SHORT).show()
                            onDismiss()
                        } else {
                            statusText = context.getString(R.string.webdav_test_fail, res.statusCode, res.message)
                        }
                        connecting = false
                    }
                },
            ) { Text(stringResource(R.string.webdav_account_connect)) }
        },
        dismissButton = {
            TextButton(enabled = !connecting, onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

@Composable
private fun WebDavAutomationDialog(
    prefs: SyncPreferences,
    settings: WebDavAutomationSettings,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var intervalText by remember { mutableStateOf(settings.intervalMinutes.toString()) }
    var retryCountText by remember { mutableStateOf(settings.retryCount.toString()) }
    var retryIntervalText by remember { mutableStateOf(settings.retryIntervalSeconds.toString()) }
    var saving by remember { mutableStateOf(false) }

    fun parsedOrDefault(text: String, default: Int): Int = text.trim().toIntOrNull() ?: default

    AlertDialog(
        modifier = ZhixuDialogDefaults.modifier(),
        onDismissRequest = { if (!saving) onDismiss() },
        properties = ZhixuDialogDefaults.properties,
        title = { Text(stringResource(R.string.webdav_automation_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                ZhixuTextField(
                    value = intervalText,
                    onValueChange = { intervalText = it },
                    enabled = !saving,
                    label = { Text(stringResource(R.string.webdav_automation_interval_minutes)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
                ZhixuTextField(
                    value = retryCountText,
                    onValueChange = { retryCountText = it },
                    enabled = !saving,
                    label = { Text(stringResource(R.string.webdav_automation_retry_count)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
                ZhixuTextField(
                    value = retryIntervalText,
                    onValueChange = { retryIntervalText = it },
                    enabled = !saving,
                    label = { Text(stringResource(R.string.webdav_automation_retry_interval_seconds)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            Button(
                enabled = !saving,
                onClick = {
                    saving = true
                    scope.launch {
                        val interval = parsedOrDefault(intervalText, settings.intervalMinutes)
                        val retryCount = parsedOrDefault(retryCountText, settings.retryCount)
                        val retryInterval = parsedOrDefault(retryIntervalText, settings.retryIntervalSeconds)
                        prefs.saveWebDavAutomationSettings(
                            WebDavAutomationSettings(
                                intervalMinutes = interval,
                                retryCount = retryCount,
                                retryIntervalSeconds = retryInterval,
                            ),
                        )
                        saving = false
                        Toast.makeText(context, context.getString(R.string.webdav_saved), Toast.LENGTH_SHORT).show()
                        onDismiss()
                    }
                },
            ) { Text(stringResource(R.string.action_save)) }
        },
        dismissButton = {
            TextButton(enabled = !saving, onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SyncSettingsScreen(
    contentPadding: PaddingValues,
    vaultRootUri: Uri?,
    repository: VaultRepository,
    accountState: AccountState,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val accountPrefsForDialogs = remember(context) { AccountPreferences(context.applicationContext) }
    val prefs = remember(context) { SyncPreferences(context.applicationContext) }
    val vaultSyncPrefs = remember(context) { VaultSyncPreferences(context.applicationContext) }
    val vaultStorageLocation by vaultSyncPrefs.config.map { it.location }.collectAsState(initial = VaultStorageLocation.LOCAL)
    val saved by
        prefs.webDavConfig.collectAsState(
            initial =
                WebDavConfig(
                    enabled = false,
                    baseUrl = "",
                    username = "",
                    password = "",
                    remoteRoot = "/",
                    includeIndexSqlite = true,
                    conflictStrategy = app.zhixu.data.WebDavConflictStrategy.KEEP_BOTH,
                ),
        )
    val savedWebDavPairName by prefs.webDavPairName.collectAsState(initial = "")
    val webDavAutomation by prefs.webDavAutomationSettings.collectAsState(initial = WebDavAutomationSettings.DEFAULT)
    val webDavAutoSyncEnabled by prefs.webDavAutoSyncEnabled.collectAsState(initial = false)
    val webDavRemoteRootConfirmed by prefs.webDavRemoteRootConfirmed.collectAsState(initial = false)
    val officialSyncEnabled by prefs.officialSyncEnabled.collectAsState(initial = false)
    val syncServerRunningCount by SyncServerSyncRuntime.runningCount.collectAsState(initial = 0)
    val syncServerSyncing = syncServerRunningCount > 0

    var page by rememberSaveable { mutableStateOf(SyncSettingsPage.Main) }

    var showWebDavAccountDialog by remember { mutableStateOf(false) }
    var showWebDavAutomationDialog by remember { mutableStateOf(false) }

    var enabled by remember { mutableStateOf(saved.enabled) }
    var baseUrl by remember { mutableStateOf(saved.baseUrl) }
    var username by remember { mutableStateOf(saved.username) }
    var password by remember { mutableStateOf(saved.password) }
    var remoteRoot by remember { mutableStateOf(saved.remoteRoot) }
    var includeIndexSqlite by remember { mutableStateOf(saved.includeIndexSqlite) }
    var conflictStrategy by remember { mutableStateOf(saved.conflictStrategy) }

    fun normalizeRemoteFolderPath(raw: String): String {
        val cleaned = raw.trim().replace('\\', '/')
        if (cleaned.isBlank() || cleaned == "/") return "/"
        val withSlash = if (cleaned.startsWith("/")) cleaned else "/$cleaned"
        return withSlash.trimEnd('/').ifBlank { "/" }
    }

    var remoteFolderPickerPath by rememberSaveable { mutableStateOf("/") }
    var remoteFolderPickerLoading by remember { mutableStateOf(false) }
    var remoteFolderPickerError by remember { mutableStateOf<String?>(null) }
    var remoteFolderPickerFolders by remember { mutableStateOf<List<WebDavRemoteFolder>>(emptyList()) }
    var remoteFolderPickerReloadKey by remember { mutableStateOf(0) }

    var webDavPairName by remember { mutableStateOf(savedWebDavPairName) }

    var showConflictStrategyDialog by remember { mutableStateOf(false) }

    var lastWebDavSummaryLoading by remember { mutableStateOf(false) }
    var lastWebDavSummary by remember { mutableStateOf<String?>(null) }

    var lastSyncServerSummaryLoading by remember { mutableStateOf(false) }
    var lastSyncServerSummary by remember { mutableStateOf<String?>(null) }
    var lastSyncServerSummaryEndedAtMs by remember { mutableStateOf(0L) }

    var showSyncServerCloudLogs by remember { mutableStateOf(false) }
    var showSyncServerStorage by remember { mutableStateOf(false) }
    var showSyncServerAdvanced by remember { mutableStateOf(false) }

    // Legacy (removed entry points) - kept only so existing dialog code compiles; not reachable from Sync Panel.
    var webDavStatus by remember { mutableStateOf<String?>(null) }
    var webDavSyncing by remember { mutableStateOf(false) }
    var webDavPreviewLoading by remember { mutableStateOf(false) }
    var webDavPreviewPlan by remember { mutableStateOf<WebDavSyncPlan?>(null) }
    var showWebDavPreview by remember { mutableStateOf(false) }
    var showWebDavConflictCenter by remember { mutableStateOf(false) }
    var showWebDavLogs by remember { mutableStateOf(false) }

    var webDavAutoSyncStatus by remember { mutableStateOf<String?>(null) }

    androidx.compose.runtime.LaunchedEffect(
        saved.enabled,
        saved.baseUrl,
        saved.username,
        saved.password,
        saved.remoteRoot,
        saved.conflictStrategy,
    ) {
        enabled = saved.enabled
        baseUrl = saved.baseUrl
        username = saved.username
        password = saved.password
        remoteRoot = saved.remoteRoot
        conflictStrategy = saved.conflictStrategy
    }

    androidx.compose.runtime.LaunchedEffect(saved.includeIndexSqlite) {
        includeIndexSqlite = saved.includeIndexSqlite
    }

    androidx.compose.runtime.LaunchedEffect(savedWebDavPairName) {
        webDavPairName = savedWebDavPairName
    }

    // Safety: if the remote folder isn't explicitly selected, auto-sync must stay off.
    androidx.compose.runtime.LaunchedEffect(webDavRemoteRootConfirmed, webDavAutoSyncEnabled) {
        if (!webDavRemoteRootConfirmed && webDavAutoSyncEnabled) {
            prefs.setWebDavAutoSyncEnabled(false)
        }
    }

    fun currentConfig(): WebDavConfig {
        return WebDavConfig(
            enabled = enabled,
            baseUrl = baseUrl,
            username = username,
            password = password,
            remoteRoot = remoteRoot,
            includeIndexSqlite = includeIndexSqlite,
            conflictStrategy = conflictStrategy,
        )
    }

    fun save() {
        scope.launch {
            prefs.setWebDavPairName(webDavPairName)
            prefs.saveWebDavConfig(currentConfig())
            Toast.makeText(context, context.getString(R.string.webdav_saved), Toast.LENGTH_SHORT).show()
        }
    }

    fun formatEpochMs(ms: Long): String {
        if (ms <= 0L) return "-"
        return runCatching {
            Instant
                .ofEpochMilli(ms)
                .atZone(ZoneId.systemDefault())
                .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))
        }.getOrElse { ms.toString() }
    }

    suspend fun reloadWebDavLastSummary() {
        val root = vaultRootUri ?: return
        lastWebDavSummaryLoading = true
        lastWebDavSummary =
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
        lastWebDavSummaryLoading = false
    }

    suspend fun reloadSyncServerLastSummary() {
        val root = vaultRootUri ?: run {
            lastSyncServerSummary = null
            lastSyncServerSummaryEndedAtMs = 0L
            return
        }
        lastSyncServerSummaryLoading = true
        try {
            val (text, endedAt) =
                withContext(Dispatchers.IO) {
                    val uri = repository.resolveVaultFileUri(root, ".zhixu/sync/server_last_summary.json") ?: return@withContext (null to 0L)
                    val raw = runCatching { repository.readText(uri) }.getOrNull().orEmpty().trim()
                    if (raw.isBlank()) return@withContext (null to 0L)
                    val obj = runCatching { JSONObject(raw) }.getOrNull() ?: return@withContext (raw to 0L)

                    val recordBaseUrl = obj.optString("baseUrl").orEmpty().trim()
                    if (recordBaseUrl.isNotBlank() && !recordBaseUrl.equals(OfficialSync.BASE_URL, ignoreCase = true)) {
                        // Ignore records from other sync-server targets (e.g., third-party service).
                        return@withContext (null to 0L)
                    }

                    val ok = obj.optBoolean("ok", false)
                    val endedAt = obj.optLong("endedAt", 0L)
                    if (!ok) {
                        val error = obj.optString("error").ifBlank { "-" }
                        return@withContext (context.getString(R.string.official_sync_failed, error) to endedAt)
                    }

                    val uploaded = obj.optInt("uploaded", 0)
                    val downloaded = obj.optInt("downloaded", 0)
                    val deletedRemote = obj.optInt("deletedRemote", 0)
                    val deletedLocal = obj.optInt("deletedLocal", 0)
                    val conflicts = obj.optInt("conflicts", 0)
                    val failed = obj.optInt("failed", 0)
                    (context.getString(R.string.official_sync_ok, uploaded, downloaded, deletedRemote, deletedLocal, conflicts, failed) to endedAt)
                }
            lastSyncServerSummary = text
            lastSyncServerSummaryEndedAtMs = endedAt
        } finally {
            lastSyncServerSummaryLoading = false
        }
    }

    androidx.compose.runtime.LaunchedEffect(page, vaultRootUri, syncServerRunningCount) {
        if (page != SyncSettingsPage.OfficialServer) return@LaunchedEffect
        if (vaultRootUri == null) return@LaunchedEffect
        if (syncServerRunningCount > 0) return@LaunchedEffect
        runCatching { reloadSyncServerLastSummary() }
    }

    suspend fun reloadWebDavAutoSyncStatus(config: WebDavConfig, automation: WebDavAutomationSettings) {
        val root = vaultRootUri ?: run {
            webDavAutoSyncStatus = context.getString(R.string.webdav_autosync_status_no_vault)
            return
        }
        if (!config.enabled) {
            webDavAutoSyncStatus = context.getString(R.string.webdav_autosync_status_disabled)
            return
        }
        if (!webDavAutoSyncEnabled) {
            webDavAutoSyncStatus = context.getString(R.string.webdav_autosync_status_disabled)
            return
        }
        val baseUrl = config.baseUrl.trim()
        val remoteRoot = config.remoteRoot.trim().ifBlank { "/" }
        if (baseUrl.isBlank()) {
            webDavAutoSyncStatus = context.getString(R.string.webdav_autosync_status_empty_url)
            return
        }
        if (!baseUrl.startsWith("http://") && !baseUrl.startsWith("https://")) {
            webDavAutoSyncStatus = context.getString(R.string.webdav_autosync_status_invalid_url)
            return
        }
        val key = "$baseUrl|$remoteRoot|$root"
        val store = WebDavAutoSyncStateStore(context.applicationContext)
        val state = store.get(key)
        val intervalMs = (automation.intervalMinutes.toLong() * 60_000L).coerceAtLeast(60_000L)
        val nextAllowed = state.lastAttemptedAtMs + intervalMs
        val base =
            context.getString(
                R.string.webdav_autosync_status_fmt,
                formatEpochMs(state.lastSucceededAtMs),
                formatEpochMs(state.lastAttemptedAtMs),
                formatEpochMs(nextAllowed),
            )
        val errorPart =
            if (state.lastError.isNullOrBlank()) {
                ""
            } else {
                "\n" + context.getString(R.string.webdav_autosync_status_error_fmt, state.lastError!!)
            }
        webDavAutoSyncStatus = base + errorPart
    }

    androidx.compose.runtime.LaunchedEffect(
        vaultRootUri,
        saved.baseUrl,
        saved.remoteRoot,
        saved.enabled,
        webDavAutomation.intervalMinutes,
        webDavAutomation.retryCount,
        webDavAutomation.retryIntervalSeconds,
    ) {
        reloadWebDavLastSummary()
        reloadWebDavAutoSyncStatus(saved, webDavAutomation)
    }

    val dividerColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)

    fun popPage() {
        page =
            when (page) {
                SyncSettingsPage.Main -> SyncSettingsPage.Main
                SyncSettingsPage.OfficialServer -> SyncSettingsPage.Main
                SyncSettingsPage.WebDavService -> SyncSettingsPage.Main
                SyncSettingsPage.WebDavFolderPair -> SyncSettingsPage.WebDavService
                SyncSettingsPage.WebDavRemoteFolderPicker -> SyncSettingsPage.WebDavFolderPair
                SyncSettingsPage.WebDavManual -> SyncSettingsPage.WebDavService
            }
    }

    // WebDAV user manual lives on the website so it can be updated without app releases.
    if (page == SyncSettingsPage.WebDavManual) {
        WebPageScreen(
            contentPadding = contentPadding,
            titleRes = R.string.webdav_manual_title,
            url = WEBDAV_MANUAL_URL,
            onBack = ::popPage,
        )
        return
    }

    BackHandler(enabled = page != SyncSettingsPage.Main) {
        popPage()
    }

    // Each page gets its own scroll state. Otherwise, switching pages reuses the same LazyColumn
    // scroll position and can land "past the end" of the new page's content (looks like a blank page).
    val mainListState = rememberLazyListState()
    val officialServerListState = rememberLazyListState()
    val webDavServiceListState = rememberLazyListState()
    val webDavFolderPairListState = rememberLazyListState()
    val webDavRemoteFolderPickerListState = rememberLazyListState()
    val webDavManualListState = rememberLazyListState()

    androidx.compose.runtime.LaunchedEffect(page) {
        if (page == SyncSettingsPage.WebDavRemoteFolderPicker) {
            remoteFolderPickerPath = normalizeRemoteFolderPath(remoteRoot)
        }
    }

    androidx.compose.runtime.LaunchedEffect(
        page,
        remoteFolderPickerPath,
        remoteFolderPickerReloadKey,
        baseUrl,
        username,
        password,
    ) {
        if (page != SyncSettingsPage.WebDavRemoteFolderPicker) return@LaunchedEffect

        remoteFolderPickerLoading = true
        remoteFolderPickerError = null
        remoteFolderPickerFolders = emptyList()

        val base = baseUrl.trim()
        if (base.isBlank()) {
            remoteFolderPickerError = context.getString(R.string.webdav_remote_folder_picker_error_empty_url)
            remoteFolderPickerLoading = false
            return@LaunchedEffect
        }
        if (!base.startsWith("http://") && !base.startsWith("https://")) {
            remoteFolderPickerError = context.getString(R.string.webdav_remote_folder_picker_error_invalid_url)
            remoteFolderPickerLoading = false
            return@LaunchedEffect
        }

        val baseRootUrl = base.trimEnd('/') + "/"
        val baseRootPath = Uri.parse(baseRootUrl).path ?: "/"
        val current = normalizeRemoteFolderPath(remoteFolderPickerPath)
        val dirUrl =
            if (current == "/") {
                baseRootUrl
            } else {
                WebDavClient.normalizeJoin(base, current).trimEnd('/') + "/"
            }

        val (code, xml) = WebDavClient.propfind(currentConfig(), dirUrl, depth = "1")
        if (code != 207 && code !in 200..299) {
            remoteFolderPickerError = context.getString(R.string.webdav_remote_folder_picker_error_http_fmt, code)
            remoteFolderPickerLoading = false
            return@LaunchedEffect
        }

        val currentRel = current.trim('/').trim()
        val folders =
            parseWebDavPropfind(xml)
                .asSequence()
                .filter { it.isDir }
                .mapNotNull { entry ->
                    val rel = normalizeWebDavHrefToRelativePath(entry.href, baseRootPath) ?: return@mapNotNull null
                    if (rel.isBlank()) return@mapNotNull null
                    if (currentRel.isNotBlank() && rel == currentRel) return@mapNotNull null

                    val (childName, childPath) =
                        if (currentRel.isBlank()) {
                            if (rel.contains('/')) return@mapNotNull null
                            rel to normalizeRemoteFolderPath("/$rel")
                        } else {
                            if (rel.startsWith("$currentRel/")) {
                                val child = rel.removePrefix("$currentRel/").trimStart('/')
                                if (child.isBlank() || child.contains('/')) return@mapNotNull null
                                child to normalizeRemoteFolderPath("/$rel")
                            } else {
                                // Some servers return hrefs relative to the current directory.
                                if (rel.contains('/')) return@mapNotNull null
                                rel to normalizeRemoteFolderPath("$current/$rel")
                            }
                        }
                    WebDavRemoteFolder(name = childName, path = childPath)
                }
                .distinctBy { it.path }
                .sortedBy { it.name.lowercase() }
                .toList()

        remoteFolderPickerFolders = folders
        remoteFolderPickerLoading = false
    }

    if (showConflictStrategyDialog) {
        AlertDialog(
            modifier = ZhixuDialogDefaults.modifier(),
            onDismissRequest = { showConflictStrategyDialog = false },
            properties = ZhixuDialogDefaults.properties,
            title = { Text(stringResource(R.string.webdav_conflict_strategy_title)) },
            text = {
                Column {
                    @Composable
                    fun option(strategy: WebDavConflictStrategy, labelRes: Int) {
                        TextButton(
                            modifier = Modifier.fillMaxWidth(),
                            onClick = {
                                conflictStrategy = strategy
                                showConflictStrategyDialog = false
                            },
                        ) {
                            Text(
                                text = stringResource(labelRes),
                                fontWeight = if (conflictStrategy == strategy) FontWeight.Bold else FontWeight.Normal,
                            )
                        }
                    }
                    option(WebDavConflictStrategy.KEEP_BOTH, R.string.webdav_conflict_strategy_keep_both)
                    option(WebDavConflictStrategy.LOCAL_WINS, R.string.webdav_conflict_strategy_local_wins)
                    option(WebDavConflictStrategy.REMOTE_WINS, R.string.webdav_conflict_strategy_remote_wins)
                    option(WebDavConflictStrategy.ASK_EACH_TIME, R.string.webdav_conflict_strategy_ask_each_time)
                }
            },
            confirmButton = {
                TextButton(onClick = { showConflictStrategyDialog = false }) { Text(stringResource(R.string.action_close)) }
            },
        )
    }

    if (showWebDavAccountDialog) {
        WebDavAccountDialog(
            prefs = prefs,
            savedConfig = saved,
            onDismiss = { showWebDavAccountDialog = false },
        )
    }

    if (showWebDavAutomationDialog) {
        WebDavAutomationDialog(
            prefs = prefs,
            settings = webDavAutomation,
            onDismiss = { showWebDavAutomationDialog = false },
        )
    }

    if (showSyncServerCloudLogs) {
        FullScreenDialog(onDismiss = { showSyncServerCloudLogs = false }) {
            AccountSyncLogsScreen(
                contentPadding = PaddingValues(0.dp),
                accountPrefs = accountPrefsForDialogs,
                onBack = { showSyncServerCloudLogs = false },
            )
        }
    }

    if (showSyncServerStorage) {
        FullScreenDialog(onDismiss = { showSyncServerStorage = false }) {
            AccountStorageScreen(
                contentPadding = PaddingValues(0.dp),
                accountPrefs = accountPrefsForDialogs,
                onBack = { showSyncServerStorage = false },
            )
        }
    }

    if (showWebDavPreview && webDavPreviewPlan != null) {
        val plan = webDavPreviewPlan!!
        FullScreenDialog(onDismiss = { showWebDavPreview = false }) {
            Scaffold(
                contentWindowInsets = WindowInsets(0, 0, 0, 0),
                containerColor = MaterialTheme.colorScheme.background,
                topBar = {
                    Column {
                        ZhixuTopAppBar(
                            containerColor = MaterialTheme.colorScheme.surface,
                            title = { Text(stringResource(R.string.webdav_sync_preview_title), style = MaterialTheme.typography.titleMedium) },
                            navigationIcon = {
                                ZhixuIconButton(onClick = { showWebDavPreview = false }) {
                                    Icon(
                                        painter = painterResource(Ionicons.ArrowBack),
                                        contentDescription = stringResource(R.string.action_back),
                                        modifier = Modifier.size(20.dp),
                                    )
                                }
                            },
                            actions = {
                                TextButton(
                                    enabled = enabled && !webDavSyncing && vaultRootUri != null,
                                    onClick = {
                                        showWebDavPreview = false
                                        val root = vaultRootUri ?: return@TextButton
                                        scope.launch {
                                            webDavSyncing = true
                                            webDavStatus = context.getString(R.string.webdav_syncing)
                                            val cfg = currentConfig()
                                            val engine = WebDavSyncEngine(context, repository)
                                            val res =
                                                runCatching { engine.syncVault(root, cfg) }
                                                    .getOrElse { e ->
                                                        webDavStatus =
                                                            context.getString(
                                                                R.string.webdav_sync_failed,
                                                                e.message ?: e.javaClass.simpleName,
                                                            )
                                                        webDavSyncing = false
                                                        reloadWebDavLastSummary()
                                                        reloadWebDavAutoSyncStatus(cfg, webDavAutomation)
                                                        return@launch
                                                    }
                                            webDavStatus =
                                                context.getString(
                                                    R.string.webdav_sync_ok_v2,
                                                    res.uploaded,
                                                    res.downloaded,
                                                    res.deletedRemote,
                                                    res.deletedLocal,
                                                    res.conflicts,
                                                    res.failed,
                                                )
                                            webDavSyncing = false
                                            reloadWebDavLastSummary()
                                            reloadWebDavAutoSyncStatus(cfg, webDavAutomation)
                                        }
                                    },
                                ) { Text(stringResource(R.string.webdav_sync_now)) }
                            },
                        )
                        HorizontalDivider(color = dividerColor)
                    }
                },
            ) { innerPadding ->
                val ops = plan.operations
                val shown = ops.take(500)
                LazyColumn(
                    modifier =
                        Modifier
                            .padding(innerPadding)
                            .windowInsetsPadding(WindowInsets.navigationBars)
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.background),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    item {
                        Text(
                            text =
                                context.getString(
                                    R.string.webdav_sync_ok_v2,
                                    plan.summary.uploaded,
                                    plan.summary.downloaded,
                                    plan.summary.deletedRemote,
                                    plan.summary.deletedLocal,
                                    plan.summary.conflicts,
                                    plan.summary.failed,
                                ),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    if (ops.isEmpty()) {
                        item { Text("-", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    } else {
                        for (op in shown) {
                            item {
                                val prefix =
                                    when (op.kind) {
                                        WebDavPlannedOpKind.DOWNLOAD -> "↓"
                                        WebDavPlannedOpKind.UPLOAD -> "↑"
                                        WebDavPlannedOpKind.DELETE_REMOTE -> "delR"
                                        WebDavPlannedOpKind.DELETE_LOCAL -> "delL"
                                        WebDavPlannedOpKind.CONFLICT -> "conflict"
                                    }
                                Text(
                                    text = "$prefix ${op.path} (${op.reason})",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        if (ops.size > shown.size) {
                            item { Text("… +${ops.size - shown.size}", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                        }
                    }
                }
            }
        }
    }

    if (showWebDavConflictCenter) {
        FullScreenDialog(onDismiss = { showWebDavConflictCenter = false }) {
            WebDavConflictCenterScreen(
                contentPadding = PaddingValues(0.dp),
                vaultRootUri = vaultRootUri,
                repository = repository,
                webDavConfig = currentConfig(),
                onBack = { showWebDavConflictCenter = false },
            )
        }
    }

    if (showWebDavLogs) {
        FullScreenDialog(onDismiss = { showWebDavLogs = false }) {
            WebDavLogsScreen(
                contentPadding = PaddingValues(0.dp),
                vaultRootUri = vaultRootUri,
                repository = repository,
                onBack = { showWebDavLogs = false },
            )
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            Column {
                ZhixuTopAppBar(
                    containerColor = MaterialTheme.colorScheme.surface,
                    title = {
                        val title =
                            when (page) {
                                SyncSettingsPage.Main -> stringResource(R.string.settings_section_sync)
                                SyncSettingsPage.OfficialServer -> stringResource(R.string.official_sync_title)
                                SyncSettingsPage.WebDavService -> stringResource(R.string.webdav_service_title)
                                SyncSettingsPage.WebDavFolderPair -> stringResource(R.string.webdav_folder_pair_title)
                                SyncSettingsPage.WebDavRemoteFolderPicker -> stringResource(R.string.webdav_remote_folder_picker_title)
                                SyncSettingsPage.WebDavManual -> stringResource(R.string.webdav_manual_title)
                            }
                        Text(title, style = MaterialTheme.typography.titleMedium)
                    },
                    navigationIcon = {
                        val handleBack: () -> Unit = if (page == SyncSettingsPage.Main) onBack else ::popPage
                        ZhixuIconButton(onClick = handleBack) {
                            Icon(
                                painter = painterResource(Ionicons.ArrowBack),
                                contentDescription = stringResource(R.string.action_back),
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    },
                    actions = {
                        when (page) {
                            SyncSettingsPage.WebDavFolderPair -> {
                                TextButton(onClick = ::save) { Text(stringResource(R.string.action_save)) }
                            }

                            SyncSettingsPage.WebDavRemoteFolderPicker -> {
                                TextButton(
                                    enabled = !remoteFolderPickerLoading,
                                    onClick = {
                                        val selected = normalizeRemoteFolderPath(remoteFolderPickerPath)
                                        remoteRoot = selected
                                        scope.launch { prefs.setWebDavRemoteRoot(selected) }
                                        popPage()
                                    },
                                ) { Text(stringResource(R.string.webdav_remote_folder_picker_select)) }
                            }

                            else -> Unit
                        }
                    },
                )
                HorizontalDivider(color = dividerColor)
            }
        },
    ) { innerPadding ->
        val listModifier =
            Modifier
                .padding(contentPadding)
                .padding(innerPadding)
                .windowInsetsPadding(WindowInsets.navigationBars)
                .background(MaterialTheme.colorScheme.background)
                .fillMaxSize()
                .imePadding()
        val listContentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 16.dp)

        @Composable
        fun PageLazyColumn(
            state: LazyListState,
            content: LazyListScope.() -> Unit,
        ) {
            LazyColumn(
                modifier = listModifier,
                state = state,
                contentPadding = listContentPadding,
                verticalArrangement = Arrangement.spacedBy(0.dp),
            ) {
                content()
            }
        }

        when (page) {
            SyncSettingsPage.Main ->
                PageLazyColumn(mainListState) {
                item { Spacer(modifier = Modifier.height(12.dp)) }

                item {
                    Text(
                        text = stringResource(R.string.settings_sync_placeholder),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 13.sp,
                    )
                }

                item { Spacer(modifier = Modifier.height(14.dp)) }
                item { SettingsSectionTitle(text = stringResource(R.string.official_sync_title)) }
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
                                    .clickable { page = SyncSettingsPage.OfficialServer },
                            leadingContent = {
                                Icon(
                                    painter = painterResource(Ionicons.Cloud),
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(20.dp),
                                )
                            },
                            headlineContent = { Text(stringResource(R.string.official_sync_title)) },
                            trailingContent = {
                                Icon(
                                    painter = painterResource(Ionicons.ChevronForward),
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                )
                            },
                        )
                    }
                }

                item { Spacer(modifier = Modifier.height(14.dp)) }
                item { SettingsSectionTitle(text = "WebDAV") }
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
                                    .clickable { page = SyncSettingsPage.WebDavService },
                            leadingContent = {
                                Icon(
                                    painter = painterResource(Ionicons.Cloud),
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(20.dp),
                                )
                            },
                            headlineContent = { Text(stringResource(R.string.webdav_service_title)) },
                            trailingContent = {
                                Icon(
                                    painter = painterResource(Ionicons.ChevronForward),
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                )
                            },
                        )
                    }
                }

            }

            SyncSettingsPage.OfficialServer ->
                PageLazyColumn(officialServerListState) {
                item { SettingsSectionTitle(text = stringResource(R.string.official_sync_title)) }
                item {
                    val officialSyncEnabledEffective =
                        vaultStorageLocation == VaultStorageLocation.OFFICIAL_SERVER ||
                            (vaultStorageLocation == VaultStorageLocation.LOCAL && officialSyncEnabled)
                    val canToggleOfficialSync = vaultStorageLocation == VaultStorageLocation.LOCAL
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        shape = MaterialTheme.shapes.extraLarge,
                    ) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            ListItem(
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            if (!canToggleOfficialSync) return@clickable
                                            val next = !officialSyncEnabled
                                            scope.launch { prefs.setOfficialSyncEnabled(next) }
                                        },
                                headlineContent = { Text(stringResource(R.string.official_sync_enable)) },
                                trailingContent = {
                                    ZhixuSwitch(
                                        checked = officialSyncEnabledEffective,
                                        onCheckedChange = { checked ->
                                            if (!canToggleOfficialSync) return@ZhixuSwitch
                                            scope.launch { prefs.setOfficialSyncEnabled(checked) }
                                        },
                                    )
                                },
                            )
                            HorizontalDivider(color = dividerColor)
                            ListItem(
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            val next = !includeIndexSqlite
                                            includeIndexSqlite = next
                                            scope.launch { prefs.setIncludeIndexSqlite(next) }
                                        },
                                headlineContent = { Text(stringResource(R.string.webdav_include_index_sqlite)) },
                                trailingContent = {
                                    ZhixuSwitch(
                                        checked = includeIndexSqlite,
                                        onCheckedChange = { checked ->
                                            includeIndexSqlite = checked
                                            scope.launch { prefs.setIncludeIndexSqlite(checked) }
                                        },
                                    )
                                },
                            )
                            HorizontalDivider(color = dividerColor)

                            val syncReady = officialSyncEnabledEffective && accountState.isLoggedIn && vaultRootUri != null
                            val syncNotReadyHint =
                                when {
                                    !officialSyncEnabledEffective -> stringResource(R.string.cloud_sync_dialog_official_hint_disabled)
                                    !accountState.isLoggedIn -> stringResource(R.string.official_sync_not_logged_in)
                                    else -> null
                                }

                            val statusText =
                                when {
                                    syncServerSyncing -> stringResource(R.string.cloud_sync_state_syncing)
                                    lastSyncServerSummaryLoading -> stringResource(R.string.common_loading)
                                    !lastSyncServerSummary.isNullOrBlank() -> lastSyncServerSummary!!
                                    !syncNotReadyHint.isNullOrBlank() -> syncNotReadyHint
                                    else -> stringResource(R.string.cloud_sync_dialog_no_record)
                                }
                            val statusTime = formatEpochMs(lastSyncServerSummaryEndedAtMs)

                            ListItem(
                                modifier = Modifier.fillMaxWidth(),
                                leadingContent = {
                                    Icon(
                                        painter = painterResource(Ionicons.Cloud),
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(20.dp),
                                    )
                                },
                                headlineContent = { Text(stringResource(R.string.webdav_sync_panel_last_status_title)) },
                                supportingContent = {
                                    Text(
                                        text = statusText.ifBlank { "-" },
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                },
                                trailingContent = {
                                    Text(
                                        text = statusTime,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                },
                            )

                            HorizontalDivider(color = dividerColor)

                            Button(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
                                enabled = syncReady && !syncServerSyncing,
                                onClick = {
                                    val root = vaultRootUri ?: return@Button
                                    scope.launch {
                                        runCatching {
                                            withContext(Dispatchers.IO) {
                                                VaultAutoSync.maybeSyncVault(
                                                    context = context,
                                                    repository = repository,
                                                    vaultRootUri = root,
                                                    force = true,
                                                )
                                            }
                                        }
                                        runCatching { reloadSyncServerLastSummary() }
                                    }
                                },
                            ) { Text(stringResource(R.string.cloud_sync_dialog_sync_now)) }

                            Row(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                                horizontalArrangement = Arrangement.End,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                TextButton(onClick = { showSyncServerCloudLogs = true }) {
                                    Text(stringResource(R.string.account_sync_logs_title))
                                }
                                TextButton(onClick = { showSyncServerStorage = true }) {
                                    Text(stringResource(R.string.account_storage_title))
                                }
                                TextButton(onClick = { showSyncServerAdvanced = !showSyncServerAdvanced }) {
                                    Text(if (showSyncServerAdvanced) "Hide advanced" else "Advanced")
                                }
                            }

                            if (showSyncServerAdvanced) {
                                HorizontalDivider(color = dividerColor)
                                SyncServerSyncPanel(
                                    vaultRootUri = vaultRootUri,
                                    repository = repository,
                                    baseUrl = OfficialSync.BASE_URL,
                                    token = accountState.token,
                                    includeIndexSqlite = includeIndexSqlite,
                                    syncReady = syncReady,
                                    syncNotReadyHint = syncNotReadyHint,
                                )
                            }
                        }
                    }
                }
            }

            SyncSettingsPage.WebDavFolderPair ->
                PageLazyColumn(webDavFolderPairListState) {
                val localFolderLabel =
                    if (vaultRootUri == null) {
                        "-"
                    } else {
                        val folderName = vaultRootToDocumentFile(context, vaultRootUri)?.name.orEmpty()
                        if (folderName.isNotBlank()) folderName else vaultRootUri.toString()
                    }

                item { Spacer(modifier = Modifier.height(12.dp)) }
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        shape = MaterialTheme.shapes.extraLarge,
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            ListItem(
                                modifier = Modifier.fillMaxWidth(),
                                headlineContent = { Text(stringResource(R.string.webdav_enable)) },
                                trailingContent = {
                                    ZhixuSwitch(
                                        checked = enabled,
                                        onCheckedChange = { checked ->
                                            enabled = checked
                                            scope.launch { prefs.saveWebDavConfig(currentConfig()) }
                                        },
                                    )
                                },
                            )
                            HorizontalDivider(color = dividerColor)
                            ZhixuTextField(
                                value = webDavPairName,
                                onValueChange = { webDavPairName = it },
                                label = { Text(stringResource(R.string.webdav_folder_pair_name)) },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                            )
                            ListItem(
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .clickable { page = SyncSettingsPage.WebDavRemoteFolderPicker },
                                leadingContent = {
                                    Icon(
                                        painter = painterResource(Ionicons.Vault),
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(20.dp),
                                    )
                                },
                                headlineContent = { Text(stringResource(R.string.webdav_folder_pair_remote_folder)) },
                                supportingContent = {
                                    Text(
                                        text = normalizeRemoteFolderPath(remoteRoot),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                },
                                trailingContent = {
                                    Icon(
                                        painter = painterResource(Ionicons.ChevronForward),
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp),
                                    )
                                },
                            )
                            ZhixuTextField(
                                value = localFolderLabel,
                                onValueChange = {},
                                enabled = false,
                                label = { Text(stringResource(R.string.webdav_folder_pair_local_folder)) },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                            )

                            val strategyLabel =
                                when (conflictStrategy) {
                                    WebDavConflictStrategy.KEEP_BOTH -> stringResource(R.string.webdav_conflict_strategy_keep_both)
                                    WebDavConflictStrategy.LOCAL_WINS -> stringResource(R.string.webdav_conflict_strategy_local_wins)
                                    WebDavConflictStrategy.REMOTE_WINS -> stringResource(R.string.webdav_conflict_strategy_remote_wins)
                                    WebDavConflictStrategy.ASK_EACH_TIME -> stringResource(R.string.webdav_conflict_strategy_ask_each_time)
                                }
                            ListItem(
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .clickable { showConflictStrategyDialog = true },
                                headlineContent = { Text(stringResource(R.string.webdav_conflict_strategy_title)) },
                                supportingContent = { Text(strategyLabel, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            )

                            ListItem(
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .clickable { includeIndexSqlite = !includeIndexSqlite },
                                headlineContent = { Text(stringResource(R.string.webdav_include_index_sqlite)) },
                                trailingContent = {
                                    ZhixuSwitch(
                                        checked = includeIndexSqlite,
                                        onCheckedChange = { includeIndexSqlite = it },
                                    )
                                },
                            )
                        }
                    }
                }
            }

            SyncSettingsPage.WebDavRemoteFolderPicker ->
                PageLazyColumn(webDavRemoteFolderPickerListState) {
                val current = normalizeRemoteFolderPath(remoteFolderPickerPath)
                val currentRel = current.trim('/').trim()
                val canGoUp = current != "/"
                val parentPath =
                    if (!canGoUp) {
                        "/"
                    } else {
                        val parts = currentRel.split('/').filter { it.isNotBlank() }
                        if (parts.size <= 1) "/" else "/" + parts.dropLast(1).joinToString("/")
                    }

                item { Spacer(modifier = Modifier.height(12.dp)) }
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        shape = MaterialTheme.shapes.extraLarge,
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text(
                                text = stringResource(R.string.webdav_remote_folder_picker_current_path_fmt, current),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 13.sp,
                            )

                            if (!remoteFolderPickerError.isNullOrBlank()) {
                                Text(
                                    text = remoteFolderPickerError!!,
                                    color = MaterialTheme.colorScheme.error,
                                    fontSize = 13.sp,
                                )
                            }

                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                                TextButton(
                                    enabled = !remoteFolderPickerLoading,
                                    onClick = { remoteFolderPickerReloadKey += 1 },
                                ) { Text(stringResource(R.string.action_refresh)) }

                                Button(
                                    enabled = !remoteFolderPickerLoading,
                                    onClick = {
                                        remoteRoot = current
                                        scope.launch { prefs.setWebDavRemoteRoot(current) }
                                        popPage()
                                    },
                                ) { Text(stringResource(R.string.webdav_remote_folder_picker_select)) }
                            }
                        }
                    }
                }

                item { Spacer(modifier = Modifier.height(12.dp)) }

                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        shape = MaterialTheme.shapes.extraLarge,
                    ) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            if (canGoUp) {
                                ListItem(
                                    modifier =
                                        Modifier
                                            .fillMaxWidth()
                                            .clickable { remoteFolderPickerPath = parentPath },
                                    leadingContent = {
                                        Icon(
                                            painter = painterResource(Ionicons.ChevronBack),
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(20.dp),
                                        )
                                    },
                                    headlineContent = { Text(stringResource(R.string.webdav_remote_folder_picker_parent)) },
                                )
                                HorizontalDivider(color = dividerColor)
                            }

                            if (remoteFolderPickerLoading) {
                                Box(modifier = Modifier.fillMaxWidth().padding(12.dp), contentAlignment = Alignment.Center) {
                                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                                }
                            } else if (remoteFolderPickerFolders.isEmpty()) {
                                Text(
                                    text = stringResource(R.string.webdav_remote_folder_picker_empty),
                                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 13.sp,
                                )
                            } else {
                                remoteFolderPickerFolders.forEachIndexed { index, folder ->
                                    ListItem(
                                        modifier =
                                            Modifier
                                                .fillMaxWidth()
                                                .clickable { remoteFolderPickerPath = folder.path },
                                        leadingContent = {
                                            Icon(
                                                painter = painterResource(Ionicons.Vault),
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.size(20.dp),
                                            )
                                        },
                                        headlineContent = { Text(folder.name) },
                                        supportingContent = { Text(folder.path, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                        trailingContent = {
                                            Icon(
                                                painter = painterResource(Ionicons.ChevronForward),
                                                contentDescription = null,
                                                modifier = Modifier.size(18.dp),
                                            )
                                        },
                                    )
                                    if (index != remoteFolderPickerFolders.lastIndex) {
                                        HorizontalDivider(color = dividerColor)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            SyncSettingsPage.WebDavManual ->
                PageLazyColumn(webDavManualListState) {
                item { Spacer(modifier = Modifier.height(12.dp)) }
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        shape = MaterialTheme.shapes.extraLarge,
                    ) {
                        Text(
                            text = stringResource(R.string.webdav_manual_content),
                            modifier = Modifier.fillMaxWidth().padding(12.dp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 14.sp,
                        )
                    }
                }
            }

            SyncSettingsPage.WebDavService ->
                PageLazyColumn(webDavServiceListState) {
                item { Spacer(modifier = Modifier.height(14.dp)) }

                val trimmedUser = username.trim()
                val trimmedUrl = baseUrl.trim()
                val accountSubtitle =
                    if (trimmedUser.isBlank() && trimmedUrl.isBlank()) {
                        context.getString(R.string.webdav_account_subtitle_empty)
                    } else {
                        context.getString(R.string.webdav_account_subtitle_fmt, trimmedUser.ifBlank { "-" }, trimmedUrl.ifBlank { "-" })
                    }

                val remote = remoteRoot.trim().ifBlank { "/" }
                val pairSubtitle =
                    when {
                        webDavPairName.isNotBlank() && remote != "/" -> "${webDavPairName.trim()} · $remote"
                        webDavPairName.isNotBlank() -> webDavPairName.trim()
                        remote != "/" -> remote
                        else -> context.getString(R.string.webdav_account_subtitle_empty)
                    }

                val baseUrlOk =
                    trimmedUrl.isNotBlank() && (trimmedUrl.startsWith("http://") || trimmedUrl.startsWith("https://"))
                val syncReady =
                    vaultRootUri != null &&
                        saved.enabled &&
                        baseUrlOk &&
                        webDavRemoteRootConfirmed
                val autoSyncNotReadyHint =
                    when {
                        vaultRootUri == null -> context.getString(R.string.webdav_autosync_status_no_vault)
                        !saved.enabled -> context.getString(R.string.cloud_sync_status_not_configured)
                        trimmedUrl.isBlank() -> context.getString(R.string.webdav_autosync_status_empty_url)
                        !baseUrlOk -> context.getString(R.string.webdav_autosync_status_invalid_url)
                        !webDavRemoteRootConfirmed -> context.getString(R.string.webdav_sync_not_ready_select_remote_folder)
                        else -> null
                    }
                val syncPanelNotReadyHint =
                    if (!webDavRemoteRootConfirmed) {
                        context.getString(R.string.webdav_sync_not_ready_select_remote_folder)
                    } else {
                        null
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
                                    .clickable { showWebDavAccountDialog = true },
                            leadingContent = {
                                Icon(
                                    painter = painterResource(Ionicons.User),
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(20.dp),
                                )
                            },
                            headlineContent = { Text(stringResource(R.string.webdav_account_title)) },
                            supportingContent = { Text(accountSubtitle, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            trailingContent = {
                                Icon(
                                    painter = painterResource(Ionicons.ChevronForward),
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                )
                            },
                        )
                        HorizontalDivider(color = dividerColor)
                        ListItem(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .clickable { page = SyncSettingsPage.WebDavFolderPair },
                            leadingContent = {
                                Icon(
                                    painter = painterResource(Ionicons.AddCircleOutline),
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(20.dp),
                                )
                            },
                            headlineContent = { Text(stringResource(R.string.webdav_folder_pair_title)) },
                            supportingContent = { Text(pairSubtitle, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            trailingContent = {
                                Icon(
                                    painter = painterResource(Ionicons.ChevronForward),
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                )
                            },
                        )
                        HorizontalDivider(color = dividerColor)
                        ListItem(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .clickable { showWebDavAutomationDialog = true },
                            leadingContent = {
                                Icon(
                                    painter = painterResource(Ionicons.TimeOutline),
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(20.dp),
                                )
                            },
                            headlineContent = { Text(stringResource(R.string.webdav_automation_title)) },
                            supportingContent = {
                                val subtitle =
                                    stringResource(
                                        R.string.webdav_automation_subtitle_fmt,
                                        webDavAutomation.intervalMinutes,
                                        webDavAutomation.retryCount,
                                        webDavAutomation.retryIntervalSeconds,
                                    )
                                Text(subtitle, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            },
                            trailingContent = {
                                Icon(
                                    painter = painterResource(Ionicons.ChevronForward),
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                )
                            },
                        )
                        HorizontalDivider(color = dividerColor)
                        ListItem(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        val next = !webDavAutoSyncEnabled
                                        if (next && !syncReady) {
                                            Toast.makeText(context, autoSyncNotReadyHint.orEmpty().ifBlank { "-" }, Toast.LENGTH_SHORT).show()
                                            return@clickable
                                        }
                                        scope.launch { prefs.setWebDavAutoSyncEnabled(next) }
                                    },
                            leadingContent = {
                                Icon(
                                    painter = painterResource(Ionicons.Workflow),
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(20.dp),
                                )
                            },
                            headlineContent = { Text(stringResource(R.string.webdav_sync_panel_autosync_title)) },
                            supportingContent = {
                                Text(
                                    text =
                                        if (!syncReady && !autoSyncNotReadyHint.isNullOrBlank()) {
                                            autoSyncNotReadyHint
                                        } else if (webDavAutoSyncEnabled) {
                                            stringResource(R.string.webdav_sync_panel_autosync_enabled)
                                        } else {
                                            stringResource(R.string.webdav_sync_panel_autosync_disabled)
                                        },
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            },
                            trailingContent = {
                                ZhixuSwitch(
                                    checked = webDavAutoSyncEnabled,
                                    onCheckedChange = { checked ->
                                        if (checked && !syncReady) {
                                            Toast.makeText(context, autoSyncNotReadyHint.orEmpty().ifBlank { "-" }, Toast.LENGTH_SHORT).show()
                                            return@ZhixuSwitch
                                        }
                                        scope.launch { prefs.setWebDavAutoSyncEnabled(checked) }
                                    },
                                    enabled = syncReady || webDavAutoSyncEnabled,
                                )
                            },
                        )
                        HorizontalDivider(color = dividerColor)
                        WebDavSyncPanel(
                            vaultRootUri = vaultRootUri,
                            repository = repository,
                            webDavConfig = currentConfig(),
                            legacyLastSummaryText = lastWebDavSummary,
                            syncReady = syncReady,
                            syncNotReadyHint = syncPanelNotReadyHint,
                        )
                        HorizontalDivider(color = dividerColor)
                        ListItem(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .clickable { page = SyncSettingsPage.WebDavManual },
                            leadingContent = {
                                Icon(
                                    painter = painterResource(Ionicons.HelpCircleOutline),
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(20.dp),
                                )
                            },
                            headlineContent = { Text(stringResource(R.string.webdav_manual_title)) },
                            supportingContent = {
                                val intro =
                                    stringResource(R.string.webdav_manual_content)
                                        .lineSequence()
                                        .firstOrNull()
                                        .orEmpty()
                                Text(intro, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            },
                            trailingContent = {
                                Icon(
                                    painter = painterResource(Ionicons.ChevronForward),
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                )
                            },
                        )
                    }
                }

            }
    }
}
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProSettingsScreen(
    contentPadding: PaddingValues,
    proPrefs: ProPreferences,
    isProEnabled: Boolean,
    onBack: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val dividerColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
    val pagerState = rememberPagerState(initialPage = 0, pageCount = { 2 })
    var enlargedImageRes by remember { mutableStateOf<Int?>(null) }

    val enlarged = enlargedImageRes
    if (enlarged != null) {
        Dialog(
            onDismissRequest = { enlargedImageRes = null },
            properties = DialogProperties(usePlatformDefaultWidth = false),
        ) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.background,
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    ZhixuIconButton(
                        onClick = { enlargedImageRes = null },
                        modifier = Modifier.align(Alignment.TopEnd).padding(8.dp),
                    ) {
                        Icon(
                            painter = painterResource(Ionicons.Close),
                            contentDescription = stringResource(R.string.action_close),
                            modifier = Modifier.size(20.dp),
                        )
                    }

                    androidx.compose.foundation.Image(
                        painter = painterResource(enlarged),
                        contentDescription = stringResource(R.string.pro_payment_code_content_desc),
                        modifier =
                            Modifier
                                .align(Alignment.Center)
                                .padding(24.dp)
                                .fillMaxWidth()
                                .aspectRatio(1f)
                                .clip(RoundedCornerShape(12.dp)),
                        contentScale = ContentScale.Fit,
                    )
                }
            }
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            Column {
                ZhixuTopAppBar(
                    containerColor = MaterialTheme.colorScheme.surface,
                    title = { Text(stringResource(R.string.settings_pro_title), style = MaterialTheme.typography.titleMedium) },
                    navigationIcon = {
                        ZhixuIconButton(onClick = onBack) {
                            Icon(
                                painter = painterResource(Ionicons.ArrowBack),
                                contentDescription = stringResource(R.string.action_back),
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    },
                )
                HorizontalDivider(color = dividerColor)
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
                    .fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(0.dp),
        ) {
            item { Spacer(modifier = Modifier.height(12.dp)) }
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = MaterialTheme.shapes.extraLarge,
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            text = stringResource(R.string.pro_payment_price),
                            style = MaterialTheme.typography.headlineLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            textAlign = TextAlign.Center,
                        )

                        Text(
                            text = stringResource(R.string.pro_payment_line_1),
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.Center,
                        )
                        Text(
                            text = stringResource(R.string.pro_payment_line_2),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                        )
                        Text(
                            text = stringResource(R.string.pro_payment_line_3),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                        )

                        Spacer(modifier = Modifier.height(2.dp))

                        Text(
                            text = stringResource(R.string.pro_payment_line_4),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                        )

                        TabRow(
                            selectedTabIndex = pagerState.currentPage,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Tab(
                                selected = pagerState.currentPage == 0,
                                onClick = { scope.launch { pagerState.animateScrollToPage(0) } },
                                text = { Text(stringResource(R.string.pro_payment_alipay)) },
                            )
                            Tab(
                                selected = pagerState.currentPage == 1,
                                onClick = { scope.launch { pagerState.animateScrollToPage(1) } },
                                text = { Text(stringResource(R.string.pro_payment_wechat)) },
                            )
                        }

                        Text(
                            text = stringResource(R.string.pro_payment_line_5),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            textAlign = TextAlign.Center,
                        )

                        HorizontalPager(
                            state = pagerState,
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .height(180.dp),
                        ) { page ->
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center,
                            ) {
                                val imageRes = if (page == 0) R.drawable.qr_alipay else R.drawable.qr_wechat
                                androidx.compose.foundation.Image(
                                    painter = painterResource(imageRes),
                                    contentDescription = stringResource(R.string.pro_payment_code_content_desc),
                                    modifier =
                                        Modifier
                                            .size(160.dp)
                                            .clip(RoundedCornerShape(10.dp))
                                            .clickable { enlargedImageRes = imageRes },
                                    contentScale = ContentScale.Fit,
                                )
                            }
                        }

                        Text(
                            text = stringResource(R.string.pro_payment_line_6),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                            textAlign = TextAlign.Center,
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                text = stringResource(R.string.pro_payment_line_7),
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.weight(1f),
                            )
                            ZhixuSwitch(
                                checked = isProEnabled,
                                onCheckedChange = { checked -> scope.launch { proPrefs.setProEnabled(checked) } },
                            )
                        }
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(14.dp)) }

            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = MaterialTheme.shapes.extraLarge,
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        ListItem(
                            modifier = Modifier.fillMaxWidth(),
                            leadingContent = {
                                Icon(
                                    painter = painterResource(R.drawable.ic_hero_sparkles),
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(20.dp),
                                )
                            },
                            headlineContent = { Text(stringResource(R.string.pro_feature_ai_title)) },
                            supportingContent = { Text(stringResource(R.string.pro_feature_ai_desc), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        )

                        HorizontalDivider(color = dividerColor)
                        ListItem(
                            modifier = Modifier.fillMaxWidth(),
                            leadingContent = {
                                Icon(
                                    painter = painterResource(Ionicons.ExtensionPuzzleOutline),
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(20.dp),
                                )
                            },
                            headlineContent = { Text(stringResource(R.string.pro_feature_workshop_title)) },
                            supportingContent = { Text(stringResource(R.string.pro_feature_workshop_desc), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        )

                        HorizontalDivider(color = dividerColor)
                        ListItem(
                            modifier = Modifier.fillMaxWidth(),
                            leadingContent = {
                                Icon(
                                    painter = painterResource(R.drawable.ic_lucide_cloud_sync),
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(20.dp),
                                )
                            },
                            headlineContent = { Text(stringResource(R.string.pro_feature_sync_title)) },
                            supportingContent = { Text(stringResource(R.string.pro_feature_sync_desc), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        )

                        HorizontalDivider(color = dividerColor)
                        ListItem(
                            modifier = Modifier.fillMaxWidth(),
                            leadingContent = {
                                Icon(
                                    painter = painterResource(R.drawable.ic_lucide_brush),
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(20.dp),
                                )
                            },
                            headlineContent = { Text(stringResource(R.string.pro_feature_drawing_title)) },
                            supportingContent = { Text(stringResource(R.string.pro_feature_drawing_desc), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        )
                    }
                }
            }
        }
    }
}

private enum class AboutOverlayPage {
    Main,
    Terms,
    Privacy,
    License,
}

@Composable
private fun AboutOverlayScreen(
    contentPadding: PaddingValues,
    onBack: () -> Unit,
) {
    var page by remember { mutableStateOf(AboutOverlayPage.Main) }

    BackHandler(enabled = page != AboutOverlayPage.Main) {
        page = AboutOverlayPage.Main
    }

    when (page) {
        AboutOverlayPage.Main ->
            AboutScreen(
                contentPadding = contentPadding,
                onBack = onBack,
                onOpenTermsOfUse = { page = AboutOverlayPage.Terms },
                onOpenPrivacyPolicy = { page = AboutOverlayPage.Privacy },
                onOpenOpenSourceLicense = { page = AboutOverlayPage.License },
            )

        AboutOverlayPage.Terms ->
            TermsOfUseScreen(
                contentPadding = contentPadding,
                onBack = { page = AboutOverlayPage.Main },
            )

        AboutOverlayPage.Privacy ->
            PrivacyPolicyScreen(
                contentPadding = contentPadding,
                onBack = { page = AboutOverlayPage.Main },
            )

        AboutOverlayPage.License ->
            OpenSourceLicenseScreen(
                contentPadding = contentPadding,
                onBack = { page = AboutOverlayPage.Main },
            )
    }
}

private enum class PermissionsOverlayPage {
    Main,
    Pomodoro,
    Notifications,
    DailyReminder,
    ReminderSound,
    ReminderVibration,
    ReminderPopup,
}

@Composable
private fun PermissionsOverlayScreen(
    contentPadding: PaddingValues,
    onBack: () -> Unit,
) {
    var page by remember { mutableStateOf(PermissionsOverlayPage.Main) }

    BackHandler(enabled = page != PermissionsOverlayPage.Main) {
        page = PermissionsOverlayPage.Main
    }

    when (page) {
        PermissionsOverlayPage.Main ->
            PermissionsSettingsScreen(
                contentPadding = contentPadding,
                onBack = onBack,
                onOpenPomodoroSettings = { page = PermissionsOverlayPage.Pomodoro },
                onOpenNotificationSettings = { page = PermissionsOverlayPage.Notifications },
                onOpenDailyReminder = { page = PermissionsOverlayPage.DailyReminder },
                onOpenReminderSound = { page = PermissionsOverlayPage.ReminderSound },
                onOpenReminderVibration = { page = PermissionsOverlayPage.ReminderVibration },
                onOpenReminderPopup = { page = PermissionsOverlayPage.ReminderPopup },
            )

        PermissionsOverlayPage.Pomodoro ->
            PomodoroSettingsScreen(
                contentPadding = contentPadding,
                onBack = { page = PermissionsOverlayPage.Main },
            )

        PermissionsOverlayPage.Notifications ->
            NotificationSettingsScreen(
                contentPadding = contentPadding,
                onBack = { page = PermissionsOverlayPage.Main },
                onOpenDailyReminder = { page = PermissionsOverlayPage.DailyReminder },
                onOpenReminderSound = { page = PermissionsOverlayPage.ReminderSound },
                onOpenReminderVibration = { page = PermissionsOverlayPage.ReminderVibration },
                onOpenReminderPopup = { page = PermissionsOverlayPage.ReminderPopup },
            )

        PermissionsOverlayPage.DailyReminder ->
            DailyReminderSettingsScreen(
                contentPadding = contentPadding,
                onBack = { page = PermissionsOverlayPage.Main },
            )

        PermissionsOverlayPage.ReminderSound ->
            ReminderSoundSettingsScreen(
                contentPadding = contentPadding,
                onBack = { page = PermissionsOverlayPage.Main },
            )

        PermissionsOverlayPage.ReminderVibration ->
            ReminderVibrationSettingsScreen(
                contentPadding = contentPadding,
                onBack = { page = PermissionsOverlayPage.Main },
            )

        PermissionsOverlayPage.ReminderPopup ->
            ReminderPopupSettingsScreen(
                contentPadding = contentPadding,
                onBack = { page = PermissionsOverlayPage.Main },
            )
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

@Composable
private fun SettingsRow(
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

@Composable
private fun SpaceDialog(
    vaultRootUri: Uri?,
    onDismiss: () -> Unit,
    vaultPrefs: VaultPreferences,
    vaultSyncPrefs: VaultSyncPreferences,
    repository: VaultRepository,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val folderLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.OpenDocumentTree(),
        ) { uri: Uri? ->
            if (uri == null) return@rememberLauncherForActivityResult
            val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            runCatching { context.contentResolver.takePersistableUriPermission(uri, flags) }
            scope.launch {
                runCatching { repository.ensureVaultStructure(uri) }
                    .onFailure { e ->
                        Toast.makeText(context, e.message ?: e.javaClass.simpleName, Toast.LENGTH_SHORT).show()
                        return@launch
                    }
                vaultPrefs.setVaultRootUri(uri.toString())
                vaultSyncPrefs.setLocation(VaultStorageLocation.LOCAL)
                Toast.makeText(context, context.getString(R.string.webdav_saved), Toast.LENGTH_SHORT).show()
            }
        }

    AlertDialog(
        modifier = ZhixuDialogDefaults.modifier(),
        onDismissRequest = onDismiss,
        properties = ZhixuDialogDefaults.properties,
        title = { Text(stringResource(R.string.settings_section_vault)) },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_close)) }
        },
        text = {
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .heightIn(max = 520.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                SettingsSectionTitle(text = stringResource(R.string.settings_section_vault))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = MaterialTheme.shapes.extraLarge,
                ) {
                    SettingsRow(
                        iconRes = Ionicons.Vault,
                        title = stringResource(R.string.settings_change_vault),
                        subtitle =
                            if (vaultRootUri == null) {
                                stringResource(R.string.settings_vault_not_selected)
                            } else {
                                stringResource(R.string.settings_vault_selected)
                            },
                        onClick = { folderLauncher.launch(null) },
                    )
                }
            }
        },
    )
}

@Composable
private fun UiSettingsDialog(
    uiPrefs: UiPreferences,
    uiSettings: UiSettings,
    onDismiss: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    AlertDialog(
        modifier = ZhixuDialogDefaults.modifier(),
        onDismissRequest = onDismiss,
        properties = ZhixuDialogDefaults.properties,
        title = { Text(stringResource(R.string.settings_placeholder_ui)) },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_close)) }
        },
        text = {
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .heightIn(max = 560.dp)
                        .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = MaterialTheme.shapes.extraLarge,
                ) {
                    @Composable
                    fun ModeCard(
                        mode: UiThemeMode,
                        label: String,
                        icon: androidx.compose.ui.graphics.vector.ImageVector,
                    ) {
                        val selected = uiSettings.themeMode == mode
                        val borderColor =
                            if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
                        val tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        Surface(
                            modifier =
                                Modifier
                                    .size(width = 96.dp, height = 76.dp)
                                    .clickable { scope.launch { uiPrefs.setThemeMode(mode) } },
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(2.dp, borderColor),
                            color = MaterialTheme.colorScheme.surface,
                        ) {
                            Column(
                                modifier = Modifier.fillMaxSize().padding(10.dp),
                                verticalArrangement = Arrangement.Center,
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                Icon(
                                    imageVector = icon,
                                    contentDescription = null,
                                    tint = tint,
                                    modifier = Modifier.size(28.dp),
                                )
                                Spacer(Modifier.height(8.dp))
                                Text(text = label, color = tint, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }

                    SettingsSectionTitle(text = stringResource(R.string.settings_ui_theme_title))
                    Row(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState())
                                .padding(start = 12.dp, end = 12.dp, bottom = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        ModeCard(UiThemeMode.SYSTEM, stringResource(R.string.settings_ui_theme_system), Icons.Outlined.Android)
                        ModeCard(UiThemeMode.LIGHT, stringResource(R.string.settings_ui_theme_light), Icons.Outlined.LightMode)
                        ModeCard(UiThemeMode.DARK, stringResource(R.string.settings_ui_theme_dark), Icons.Outlined.DarkMode)
                    }
                }

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = MaterialTheme.shapes.extraLarge,
                ) {
                    ListItem(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .clickable { scope.launch { uiPrefs.setStrictDocListPreview(!uiSettings.strictDocListPreview) } },
                        leadingContent = {
                            Icon(
                                painter = painterResource(Ionicons.EyeOutline),
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp),
                            )
                        },
                        headlineContent = { Text(stringResource(R.string.settings_ui_strict_preview_title)) },
                        supportingContent = {
                            Text(
                                stringResource(R.string.settings_ui_strict_preview_subtitle),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        },
                        trailingContent = {
                            ZhixuSwitch(
                                checked = uiSettings.strictDocListPreview,
                                onCheckedChange = { checked -> scope.launch { uiPrefs.setStrictDocListPreview(checked) } },
                            )
                        },
                    )
                }

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SettingsSectionTitle(text = stringResource(R.string.settings_language_title))

                    data class LanguageOption(val tag: String, val label: String)
                    val options =
                        listOf(
                            LanguageOption("", context.getString(R.string.settings_language_system)),
                            LanguageOption("zh-CN", context.getString(R.string.settings_language_zh_cn)),
                            LanguageOption("en", context.getString(R.string.settings_language_en)),
                        )
                    val selectedTag = uiSettings.languageTag.trim()
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        shape = MaterialTheme.shapes.extraLarge,
                    ) {
                        options.forEachIndexed { index, opt ->
                            val selected = selectedTag == opt.tag || (opt.tag.isBlank() && selectedTag.isBlank())
                            ListItem(
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .clickable { scope.launch { uiPrefs.setLanguageTag(opt.tag) } },
                                leadingContent = {
                                    Icon(
                                        painter = painterResource(Ionicons.LanguageOutline),
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(20.dp),
                                    )
                                },
                                headlineContent = { Text(opt.label) },
                                trailingContent = {
                                    if (selected) {
                                        Icon(
                                            painter = painterResource(Ionicons.CheckmarkCircle),
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(18.dp),
                                        )
                                    }
                                },
                            )
                            if (index != options.lastIndex) {
                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
                            }
                        }
                    }
                }
            }
        },
    )
}

@Composable
private fun uiSettingsSummaryText(context: android.content.Context, uiSettings: UiSettings): String {
    val themeLabel =
        when (uiSettings.themeMode) {
            UiThemeMode.SYSTEM -> context.getString(R.string.settings_ui_theme_system)
            UiThemeMode.LIGHT -> context.getString(R.string.settings_ui_theme_light)
            UiThemeMode.DARK -> context.getString(R.string.settings_ui_theme_dark)
        }

    val languageLabel =
        when (uiSettings.languageTag.trim()) {
            "zh-CN" -> context.getString(R.string.settings_language_zh_cn)
            "en" -> context.getString(R.string.settings_language_en)
            else -> context.getString(R.string.settings_language_system)
        }

    return "$themeLabel · $languageLabel"
}

@Composable
private fun FullScreenDialog(
    onDismiss: () -> Unit,
    content: @Composable () -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background,
        ) {
            content()
        }
    }
}

@Composable
private fun SyncDialog(
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs = remember(context) { SyncPreferences(context.applicationContext) }
    val saved by
        prefs.webDavConfig.collectAsState(
            initial =
                WebDavConfig(
                    enabled = false,
                    baseUrl = "",
                    username = "",
                    password = "",
                    remoteRoot = "/",
                    includeIndexSqlite = true,
                    conflictStrategy = app.zhixu.data.WebDavConflictStrategy.KEEP_BOTH,
                ),
        )

    var enabled by remember { mutableStateOf(saved.enabled) }
    var baseUrl by remember { mutableStateOf(saved.baseUrl) }
    var username by remember { mutableStateOf(saved.username) }
    var password by remember { mutableStateOf(saved.password) }
    var remoteRoot by remember { mutableStateOf(saved.remoteRoot) }
    var includeIndexSqlite by remember { mutableStateOf(saved.includeIndexSqlite) }
    var conflictStrategy by remember { mutableStateOf(saved.conflictStrategy) }
    var showPassword by remember { mutableStateOf(false) }

    var testStatus by remember { mutableStateOf<String?>(null) }
    var testing by remember { mutableStateOf(false) }

    androidx.compose.runtime.LaunchedEffect(saved) {
        enabled = saved.enabled
        baseUrl = saved.baseUrl
        username = saved.username
        password = saved.password
        remoteRoot = saved.remoteRoot
        includeIndexSqlite = saved.includeIndexSqlite
        conflictStrategy = saved.conflictStrategy
    }

    fun currentConfig(): WebDavConfig {
        return WebDavConfig(
            enabled = enabled,
            baseUrl = baseUrl,
            username = username,
            password = password,
            remoteRoot = remoteRoot,
            includeIndexSqlite = includeIndexSqlite,
            conflictStrategy = conflictStrategy,
        )
    }

    AlertDialog(
        modifier = ZhixuDialogDefaults.modifier(),
        onDismissRequest = onDismiss,
        properties = ZhixuDialogDefaults.properties,
        title = { Text(stringResource(R.string.settings_section_sync)) },
        confirmButton = {
            TextButton(
                enabled = !testing,
                onClick = {
                    scope.launch {
                        prefs.saveWebDavConfig(currentConfig())
                        Toast.makeText(context, context.getString(R.string.webdav_saved), Toast.LENGTH_SHORT).show()
                        onDismiss()
                    }
                },
            ) { Text(stringResource(R.string.action_save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth().heightIn(max = 560.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                SettingsSectionTitle(text = "WebDAV")
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = MaterialTheme.shapes.extraLarge,
                ) {
                    ListItem(
                        modifier = Modifier.fillMaxWidth(),
                        headlineContent = { Text(stringResource(R.string.webdav_enable)) },
                        supportingContent = { Text(stringResource(R.string.settings_sync_subtitle), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        leadingContent = {
                            Icon(
                                painter = painterResource(Ionicons.Sync),
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp),
                            )
                        },
                        trailingContent = {
                            ZhixuSwitch(
                                checked = enabled,
                                onCheckedChange = { checked -> enabled = checked },
                            )
                        },
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        ZhixuTextField(
                            value = baseUrl,
                            onValueChange = { baseUrl = it },
                            enabled = enabled,
                            label = { Text(stringResource(R.string.webdav_base_url)) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        ZhixuTextField(
                            value = remoteRoot,
                            onValueChange = { remoteRoot = it },
                            enabled = enabled,
                            label = { Text(stringResource(R.string.webdav_remote_root)) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        ZhixuTextField(
                            value = username,
                            onValueChange = { username = it },
                            enabled = enabled,
                            label = { Text(stringResource(R.string.webdav_username)) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        ZhixuTextField(
                            value = password,
                            onValueChange = { password = it },
                            enabled = enabled,
                            label = { Text(stringResource(R.string.webdav_password)) },
                            singleLine = true,
                            visualTransformation =
                                if (showPassword) {
                                    androidx.compose.ui.text.input.VisualTransformation.None
                                } else {
                                    androidx.compose.ui.text.input.PasswordVisualTransformation()
                                },
                            trailingIcon = {
                                ZhixuIconButton(onClick = { showPassword = !showPassword }) {
                                    Icon(
                                        painter = painterResource(if (showPassword) Ionicons.EyeOffOutline else Ionicons.EyeOutline),
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp),
                                    )
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        ListItem(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .clickable(enabled = enabled) { includeIndexSqlite = !includeIndexSqlite },
                            headlineContent = { Text(stringResource(R.string.webdav_include_index_sqlite)) },
                            trailingContent = {
                                ZhixuSwitch(
                                    checked = includeIndexSqlite,
                                    onCheckedChange = { includeIndexSqlite = it },
                                    enabled = enabled,
                                )
                            },
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            TextButton(
                                enabled = enabled && !testing,
                                onClick = {
                                    scope.launch {
                                        testing = true
                                        testStatus = null
                                        val cfg = currentConfig()
                                        prefs.saveWebDavConfig(cfg)
                                        val res = WebDavClient.testConnection(cfg)
                                        testStatus =
                                            if (res.success) {
                                                context.getString(R.string.webdav_test_ok, res.statusCode)
                                            } else {
                                                context.getString(R.string.webdav_test_fail, res.statusCode, res.message)
                                            }
                                        testing = false
                                    }
                                },
                            ) { Text(stringResource(R.string.webdav_test)) }

                            if (testing) {
                                Text(
                                    text = stringResource(R.string.webdav_testing),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 13.sp,
                                )
                            } else if (!testStatus.isNullOrBlank()) {
                                Text(
                                    text = testStatus!!,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 13.sp,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }
            }
        },
    )
}
