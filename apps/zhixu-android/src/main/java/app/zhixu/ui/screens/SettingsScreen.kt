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
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Android
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material3.AlertDialog
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
import app.zhixu.data.WebDavConfig
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
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.pow

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    contentPadding: PaddingValues,
    vaultRootUri: Uri?,
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
        initial = AccountState(token = "", username = "", userId = 0L, email = "", avatarUri = ""),
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
                    SettingsRow(
                        iconRes = Ionicons.User,
                        title = stringResource(R.string.account_manage_title),
                        subtitle = accountSubtitle,
                        onClick = { showAccountDialog = true },
                    )

                    HorizontalDivider(color = dividerColor)
                    SettingsRow(
                        iconRes = Ionicons.PricetagsOutline,
                        title = stringResource(R.string.settings_pro_title),
                        subtitle = proSubtitle,
                        onClick = { showProDialog = true },
                    )

                    HorizontalDivider(color = dividerColor)
                    SettingsRow(
                        iconRes = Ionicons.DocumentOutline,
                        title = stringResource(R.string.editor_title),
                        subtitle = stringResource(R.string.settings_placeholder_coming_soon),
                        onClick = { toast(context.getString(R.string.settings_placeholder_coming_soon)) },
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
                        iconRes = Ionicons.Sync,
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

                    HorizontalDivider(color = dividerColor)
                    SettingsRow(
                        iconRes = Ionicons.HelpCircleOutline,
                        title = stringResource(R.string.settings_placeholder_about),
                        subtitle = stringResource(R.string.settings_about_subtitle),
                        onClick = { showAboutDialog = true },
                    )
                }
            }

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
        }
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
                                maxLines = 2,
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SyncSettingsScreen(
    contentPadding: PaddingValues,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs = remember(context) { SyncPreferences(context.applicationContext) }
    val saved by prefs.webDavConfig.collectAsState(initial = WebDavConfig(false, "", "", "", "/", true))

    var enabled by remember { mutableStateOf(saved.enabled) }
    var showConfig by remember { mutableStateOf(false) }
    var baseUrl by remember { mutableStateOf(saved.baseUrl) }
    var username by remember { mutableStateOf(saved.username) }
    var password by remember { mutableStateOf(saved.password) }
    var remoteRoot by remember { mutableStateOf(saved.remoteRoot) }
    var includeIndexSqlite by remember { mutableStateOf(saved.includeIndexSqlite) }
    var showPassword by remember { mutableStateOf(false) }

    var testStatus by remember { mutableStateOf<String?>(null) }
    var testing by remember { mutableStateOf(false) }

    androidx.compose.runtime.LaunchedEffect(saved) {
        enabled = saved.enabled
        if (!saved.enabled) showConfig = false
        baseUrl = saved.baseUrl
        username = saved.username
        password = saved.password
        remoteRoot = saved.remoteRoot
        includeIndexSqlite = saved.includeIndexSqlite
    }

    fun currentConfig(): WebDavConfig {
        return WebDavConfig(
            enabled = enabled,
            baseUrl = baseUrl,
            username = username,
            password = password,
            remoteRoot = remoteRoot,
            includeIndexSqlite = includeIndexSqlite,
        )
    }

    fun save() {
        scope.launch {
            prefs.saveWebDavConfig(currentConfig())
            Toast.makeText(context, context.getString(R.string.webdav_saved), Toast.LENGTH_SHORT).show()
        }
    }

    val dividerColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)

    BackHandler(enabled = showConfig) {
        showConfig = false
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            Column {
                ZhixuTopAppBar(
                    containerColor = MaterialTheme.colorScheme.surface,
                    title = { Text(stringResource(R.string.settings_section_sync), style = MaterialTheme.typography.titleMedium) },
                    navigationIcon = {
                        ZhixuIconButton(onClick = onBack) {
                            Icon(
                                painter = painterResource(Ionicons.ArrowBack),
                                contentDescription = stringResource(R.string.action_back),
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    },
                    actions = {
                        TextButton(enabled = !testing, onClick = ::save) { Text(stringResource(R.string.action_save)) }
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
                                .clickable {
                                    if (!enabled) enabled = true
                                    showConfig = true
                                },
                        headlineContent = { Text(stringResource(R.string.webdav_enable)) },
                        supportingContent = { Text(stringResource(R.string.webdav_tap_to_config), maxLines = 2, overflow = TextOverflow.Ellipsis) },
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
                                onCheckedChange = { checked ->
                                    enabled = checked
                                    if (!checked) showConfig = false
                                },
                            )
                        },
                    )
                    AnimatedVisibility(visible = enabled && showConfig, enter = fadeIn(), exit = fadeOut()) {
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
            item { SettingsSectionTitle(text = stringResource(R.string.settings_pro_title)) }
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = MaterialTheme.shapes.extraLarge,
                ) {
                    ListItem(
                        modifier = Modifier.fillMaxWidth(),
                        leadingContent = {
                            Icon(
                                painter = painterResource(Ionicons.PricetagsOutline),
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp),
                            )
                        },
                        headlineContent = { Text(stringResource(R.string.settings_pro_enable_title)) },
                        supportingContent = { Text(stringResource(R.string.settings_pro_enable_subtitle), maxLines = 2, overflow = TextOverflow.Ellipsis) },
                        trailingContent = {
                            ZhixuSwitch(
                                checked = isProEnabled,
                                onCheckedChange = { checked -> scope.launch { proPrefs.setProEnabled(checked) } },
                            )
                        },
                    )
                }
            }

            item { Spacer(modifier = Modifier.height(14.dp)) }

            item { SettingsSectionTitle(text = stringResource(R.string.pro_payment_section_title)) }
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
                                maxLines = 2,
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
    val saved by prefs.webDavConfig.collectAsState(initial = WebDavConfig(false, "", "", "", "/", true))

    var enabled by remember { mutableStateOf(saved.enabled) }
    var baseUrl by remember { mutableStateOf(saved.baseUrl) }
    var username by remember { mutableStateOf(saved.username) }
    var password by remember { mutableStateOf(saved.password) }
    var remoteRoot by remember { mutableStateOf(saved.remoteRoot) }
    var includeIndexSqlite by remember { mutableStateOf(saved.includeIndexSqlite) }
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
    }

    fun currentConfig(): WebDavConfig {
        return WebDavConfig(
            enabled = enabled,
            baseUrl = baseUrl,
            username = username,
            password = password,
            remoteRoot = remoteRoot,
            includeIndexSqlite = includeIndexSqlite,
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
                        supportingContent = { Text(stringResource(R.string.settings_sync_subtitle), maxLines = 2, overflow = TextOverflow.Ellipsis) },
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
