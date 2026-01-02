package app.zhixu.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import app.zhixu.R
import app.zhixu.plugins.InstalledPlugin
import app.zhixu.plugins.PluginManifest
import app.zhixu.plugins.PluginRepository
import app.zhixu.ui.Ionicons
import app.zhixu.ui.ZhixuTopBarIconSize
import app.zhixu.ui.components.MarkdownPreview
import app.zhixu.ui.components.ZhixuDialogDefaults
import app.zhixu.ui.components.ZhixuIconButton
import app.zhixu.ui.components.ZhixuPasswordToggleIconButton
import app.zhixu.ui.components.ZhixuTextField
import app.zhixu.ui.components.ZhixuSwitch
import app.zhixu.ui.components.ZhixuTopAppBar
import kotlinx.coroutines.launch
import org.json.JSONObject

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkshopScreen(
    contentPadding: PaddingValues,
    vaultRootUri: Uri?,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val pluginRepo = remember(context) { PluginRepository(context) }

    var installed by remember { mutableStateOf<List<InstalledPlugin>>(emptyList()) }
    var showGitDialog by remember { mutableStateOf(false) }
    var gitUrl by remember { mutableStateOf("") }
    var searchText by remember { mutableStateOf("") }
    var showInstallMenu by remember { mutableStateOf(false) }

    var detailsPlugin by remember { mutableStateOf<InstalledPlugin?>(null) }
    var detailsReadme by remember { mutableStateOf<String?>(null) }
    var detailsLoading by remember { mutableStateOf(false) }

    var officialLoading by remember { mutableStateOf(false) }
    var officialError by remember { mutableStateOf<String?>(null) }
    var officialPlugins by remember { mutableStateOf<List<PluginManifest>>(emptyList()) }

    var configSaving by remember { mutableStateOf(false) }
    var showSettingsDialog by remember { mutableStateOf(false) }
    var settingsPlugin by remember { mutableStateOf<InstalledPlugin?>(null) }
    var settingsConfig by remember { mutableStateOf(JSONObject()) }
    var updatingPluginId by remember { mutableStateOf<String?>(null) }

    suspend fun refresh() {
        val root = vaultRootUri ?: return
        installed = pluginRepo.listInstalled(root)
    }

    LaunchedEffect(vaultRootUri) {
        if (vaultRootUri != null) refresh()
    }

    suspend fun refreshOfficial() {
        officialLoading = true
        officialError = null
        val ids =
            pluginRepo.listOfficialPluginIds()
                .getOrElse {
                    officialLoading = false
                    officialError = it.message ?: "Failed to load"
                    officialPlugins = emptyList()
                    return
                }
        val manifests = ids.mapNotNull { pluginRepo.fetchOfficialManifest(it) }
        officialPlugins = manifests.sortedBy { (it.name ?: it.id).lowercase() }
        officialLoading = false
    }

    LaunchedEffect(Unit) {
        refreshOfficial()
    }

    LaunchedEffect(detailsPlugin, vaultRootUri) {
        val plugin = detailsPlugin ?: return@LaunchedEffect
        val root = vaultRootUri ?: return@LaunchedEffect
        detailsLoading = true
        detailsReadme = pluginRepo.readPluginReadme(root, plugin.manifest.id)
        detailsLoading = false
    }

    if (showSettingsDialog && settingsPlugin != null) {
        val plugin = settingsPlugin!!
        PluginSettingsDialog(
            title = plugin.manifest.name ?: plugin.manifest.id,
            config = settingsConfig,
            saving = configSaving,
            onChange = { next -> settingsConfig = next },
            onDismiss = { showSettingsDialog = false },
            onSave = {
                val root = vaultRootUri ?: return@PluginSettingsDialog
                configSaving = true
                scope.launch {
                    val ok = pluginRepo.writePluginConfig(root, plugin.manifest.id, settingsConfig)
                    configSaving = false
                    snackbarHostState.showSnackbar(
                        if (ok) context.getString(R.string.plugin_saved) else context.getString(R.string.plugin_save_failed),
                    )
                    showSettingsDialog = false
                }
            },
        )
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
            val root = vaultRootUri ?: return@rememberLauncherForActivityResult
            scope.launch {
                val result = pluginRepo.installFromLocalFolder(root, uri)
                snackbarHostState.showSnackbar(result.message)
                refresh()
            }
        }

    if (showGitDialog) {
        AlertDialog(
            modifier = ZhixuDialogDefaults.modifier(),
            onDismissRequest = { showGitDialog = false },
            properties = ZhixuDialogDefaults.properties,
            title = { Text(stringResource(R.string.workshop_install_from_git)) },
            text = {
                ZhixuTextField(
                    value = gitUrl,
                    onValueChange = { gitUrl = it },
                    singleLine = true,
                    label = { Text(stringResource(R.string.workshop_git_url)) },
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                TextButton(
                    enabled = gitUrl.isNotBlank() && vaultRootUri != null,
                    onClick = {
                        val root = vaultRootUri ?: return@TextButton
                        val url = gitUrl.trim()
                        showGitDialog = false
                        scope.launch {
                            val result = pluginRepo.installFromGitUrl(root, url)
                            snackbarHostState.showSnackbar(result.message)
                            refresh()
                        }
                    },
                ) { Text(stringResource(R.string.workshop_install)) }
            },
            dismissButton = {
                TextButton(onClick = { showGitDialog = false }) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }

    if (detailsPlugin != null) {
        val plugin = detailsPlugin!!
        AlertDialog(
            modifier = ZhixuDialogDefaults.modifier(),
            onDismissRequest = { detailsPlugin = null },
            properties = ZhixuDialogDefaults.properties,
            title = { Text(plugin.manifest.name ?: plugin.manifest.id) },
            text = {
                Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    val id = plugin.manifest.id
                    val version = plugin.manifest.version?.let { "v$it" }.orEmpty()
                    val desc = plugin.manifest.description.orEmpty()
                    val infoStyle = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Light)
                    Text(text = "ID: $id", color = MaterialTheme.colorScheme.onSurfaceVariant, style = infoStyle)
                    if (version.isNotBlank()) Text(text = version, color = MaterialTheme.colorScheme.onSurfaceVariant, style = infoStyle)
                    if (desc.isNotBlank()) Text(text = desc, color = MaterialTheme.colorScheme.onSurfaceVariant, style = infoStyle)
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                    when {
                        detailsLoading -> Text(text = "Loading…", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        detailsReadme.isNullOrBlank() ->
                            Text(text = stringResource(R.string.workshop_plugin_no_readme), color = MaterialTheme.colorScheme.onSurfaceVariant)

                        else ->
                            MarkdownPreview(
                                modifier = Modifier.fillMaxWidth().height(360.dp),
                                markdown = detailsReadme.orEmpty(),
                            )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { detailsPlugin = null }) { Text(stringResource(R.string.action_back)) }
            },
        )
    }

    Scaffold(
        contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0, 0, 0, 0),
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
            Column {
                ZhixuTopAppBar(
                    containerColor = MaterialTheme.colorScheme.surface,
                    title = { Text(stringResource(R.string.workshop_title), style = MaterialTheme.typography.titleMedium) },
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
            modifier = Modifier
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

            item { Spacer(modifier = Modifier.height(12.dp)) }

            item {
                ZhixuTextField(
                    value = searchText,
                    onValueChange = { searchText = it },
                    singleLine = true,
                    placeholder = { Text(stringResource(R.string.workshop_search_hint)) },
                    leadingIcon = { Icon(painter = painterResource(app.zhixu.ui.Ionicons.Search), contentDescription = null) },
                    shape = MaterialTheme.shapes.extraLarge,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.workshop_section_installed),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f),
                    )

                    ZhixuIconButton(onClick = { scope.launch { refresh() } }) {
                        Icon(painter = painterResource(Ionicons.SyncOutline), contentDescription = stringResource(R.string.workshop_refresh))
                    }
                    Box {
                        ZhixuIconButton(onClick = { showInstallMenu = true }) {
                            Icon(
                                painter = painterResource(Ionicons.ArrowDownCircleOutline),
                                contentDescription = stringResource(R.string.workshop_section_install),
                            )
                        }
                        DropdownMenu(
                            expanded = showInstallMenu,
                            onDismissRequest = { showInstallMenu = false },
                        ) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.workshop_install_from_git)) },
                                onClick = {
                                    showInstallMenu = false
                                    showGitDialog = true
                                },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.workshop_install_from_folder)) },
                                onClick = {
                                    showInstallMenu = false
                                    folderLauncher.launch(null)
                                },
                            )
                        }
                    }
                }
            }

            val filtered =
                installed
                    .filter { p ->
                        val q = searchText.trim()
                        if (q.isBlank()) true
                        else {
                            val t = (p.manifest.name ?: p.manifest.id)
                            t.contains(q, ignoreCase = true) || p.manifest.id.contains(q, ignoreCase = true)
                        }
                    }
                    .distinctBy { it.manifest.id }

            if (filtered.isEmpty()) {
                item { Text(stringResource(R.string.workshop_no_plugins)) }
            } else {
                items(filtered.size, key = { "installed:${filtered[it].manifest.id}" }) { idx ->
                    val plugin = filtered[idx]
                    PluginRow(
                        plugin = plugin,
                        showDivider = idx != filtered.lastIndex,
                        onToggle = { enabled ->
                            val root = vaultRootUri ?: return@PluginRow
                            scope.launch {
                                pluginRepo.setEnabled(root, plugin.manifest.id, enabled)
                                refresh()
                            }
                        },
                        onRemove = {
                            val root = vaultRootUri ?: return@PluginRow
                            scope.launch {
                                val ok = pluginRepo.removePlugin(root, plugin.manifest.id)
                                snackbarHostState.showSnackbar(
                                    if (ok) context.getString(R.string.workshop_removed, plugin.manifest.id)
                                    else context.getString(R.string.workshop_remove_failed, plugin.manifest.id),
                                )
                                refresh()
                            }
                        },
                        onViewDetails = {
                            detailsReadme = null
                            detailsPlugin = plugin
                        },
                        onSettings = {
                            val root = vaultRootUri ?: return@PluginRow
                            scope.launch {
                                val cfg = pluginRepo.readPluginConfig(root, plugin.manifest.id) ?: JSONObject()
                                settingsPlugin = plugin
                                settingsConfig = cfg
                                showSettingsDialog = true
                            }
                        },
                        onUpdate = {
                            val root = vaultRootUri ?: return@PluginRow
                            val id = plugin.manifest.id
                            if (updatingPluginId != null) return@PluginRow
                            updatingPluginId = id
                            scope.launch {
                                try {
                                    val result = pluginRepo.updatePlugin(root, id)
                                    snackbarHostState.showSnackbar(result.message)
                                    refresh()
                                } finally {
                                    if (updatingPluginId == id) updatingPluginId = null
                                }
                            }
                        },
                        updating = updatingPluginId == plugin.manifest.id,
                    )
                }
            }

            item { Spacer(modifier = Modifier.height(18.dp)) }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.workshop_section_official),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f),
                    )
                    ZhixuIconButton(enabled = !officialLoading, onClick = { scope.launch { refreshOfficial() } }) {
                        Icon(painter = painterResource(Ionicons.SyncOutline), contentDescription = stringResource(R.string.workshop_refresh))
                    }
                }
            }

            val installedIds = installed.map { it.manifest.id }.toSet()
            when {
                officialLoading -> {
                    item { Text(stringResource(R.string.workshop_official_loading)) }
                }

                officialError != null -> {
                    item { Text(stringResource(R.string.workshop_official_failed, officialError ?: "")) }
                }

                officialPlugins.isEmpty() -> {
                    item { Text(stringResource(R.string.workshop_official_empty)) }
                }

                else -> {
                    val officialUnique = officialPlugins.distinctBy { it.id }
                    items(officialUnique.size, key = { "official:${officialUnique[it].id}" }) { idx ->
                        val m = officialUnique[idx]
                        OfficialPluginRow(
                            manifest = m,
                            installed = installedIds.contains(m.id),
                            showDivider = idx != officialUnique.lastIndex,
                            onInstall = {
                                val root = vaultRootUri ?: return@OfficialPluginRow
                                scope.launch {
                                    val result = pluginRepo.installFromOfficial(root, m.id)
                                    snackbarHostState.showSnackbar(result.message)
                                    refresh()
                                }
                            },
                        )
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(24.dp)) }
        }
    }
}

@Composable
private fun PluginRow(
    plugin: InstalledPlugin,
    showDivider: Boolean,
    onToggle: (Boolean) -> Unit,
    onRemove: () -> Unit,
    onViewDetails: () -> Unit,
    onSettings: (() -> Unit)?,
    onUpdate: (() -> Unit)?,
    updating: Boolean,
) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = plugin.manifest.name ?: plugin.manifest.id,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )

            val meta =
                listOfNotNull(
                    plugin.manifest.version?.takeIf { it.isNotBlank() }?.let { "版本: $it" },
                    "ID: ${plugin.manifest.id}",
                ).joinToString(" · ")
            Text(
                text = meta,
                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Light),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        val desc = plugin.manifest.description.orEmpty().ifBlank { plugin.manifest.id }
        if (desc.isNotBlank()) {
            Text(
                modifier = Modifier.padding(top = 6.dp),
                text = desc,
                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Light),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                ZhixuIconButton(onClick = onViewDetails) {
                    Icon(
                        painter = painterResource(Ionicons.InformationCircleOutline),
                        contentDescription = stringResource(R.string.workshop_view_details),
                    )
                }

                if (onUpdate != null) {
                    ZhixuIconButton(enabled = !updating, onClick = onUpdate) {
                        Icon(
                            painter =
                                painterResource(
                                    if (updating) Ionicons.HourglassOutline else Ionicons.CloudDownloadOutline,
                                ),
                            contentDescription = stringResource(R.string.workshop_update),
                        )
                    }
                }

                if (onSettings != null) {
                    ZhixuIconButton(onClick = onSettings) {
                        Icon(
                            painter = painterResource(Ionicons.SettingsOutline),
                            contentDescription = stringResource(R.string.action_settings),
                        )
                    }
                }

                ZhixuIconButton(onClick = onRemove) {
                    Icon(
                        painter = painterResource(Ionicons.TrashOutline),
                        contentDescription = stringResource(R.string.workshop_remove),
                    )
                }
            }

            ZhixuSwitch(checked = plugin.enabled, onCheckedChange = onToggle)
        }

        if (showDivider) {
            HorizontalDivider(
                modifier = Modifier.padding(top = 12.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.8f),
            )
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun PluginSettingsDialog(
    title: String,
    config: JSONObject,
    saving: Boolean,
    onChange: (JSONObject) -> Unit,
    onDismiss: () -> Unit,
    onSave: () -> Unit,
) {
    var showPasswords by remember { mutableStateOf(false) }

    AlertDialog(
        modifier = ZhixuDialogDefaults.modifier(),
        onDismissRequest = { if (!saving) onDismiss() },
        properties = ZhixuDialogDefaults.properties,
        title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        text = {
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .heightIn(max = 560.dp)
                        .imePadding()
                        .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
                JsonObjectForm(
                    root = config,
                    obj = config,
                    path = emptyList(),
                    showPasswords = showPasswords,
                    onToggleShowPasswords = { showPasswords = !showPasswords },
                    onChange = onChange,
                )
            }
        },
        confirmButton = {
            Button(enabled = !saving, onClick = onSave) { Text(stringResource(R.string.plugin_save)) }
        },
        dismissButton = {
            TextButton(enabled = !saving, onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

@Composable
private fun JsonObjectForm(
    root: JSONObject,
    obj: JSONObject,
    path: List<String>,
    showPasswords: Boolean,
    onToggleShowPasswords: () -> Unit,
    onChange: (JSONObject) -> Unit,
) {
    val keys =
        remember(obj.toString()) {
            val out = ArrayList<String>()
            val it = obj.keys()
            while (it.hasNext()) out += it.next()
            out.sorted()
        }

    if (keys.isEmpty()) {
        Text(text = "{}", color = MaterialTheme.colorScheme.onSurfaceVariant)
        return
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        for ((idx, k) in keys.withIndex()) {
            val v = obj.opt(k)
            val nextPath = path + k
            when (v) {
                is JSONObject -> {
                    Card(
                        shape = MaterialTheme.shapes.large,
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(text = k, style = MaterialTheme.typography.titleSmall)
                            JsonObjectForm(
                                root = root,
                                obj = v,
                                path = nextPath,
                                showPasswords = showPasswords,
                                onToggleShowPasswords = onToggleShowPasswords,
                                onChange = onChange,
                            )
                        }
                    }
                }

                is Boolean -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                            Text(text = prettyConfigLabel(path, k), style = MaterialTheme.typography.titleMedium)
                            prettyConfigHint(path, k)?.let { hint ->
                                Text(text = hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        ZhixuSwitch(checked = v, onCheckedChange = { checked -> onChange(configWithPathValue(root, nextPath, checked)) })
                    }
                }

                is Number -> {
                    var text by remember(v) { mutableStateOf(v.toString()) }
                    ConfigField(
                        label = prettyConfigLabel(path, k),
                        hint = prettyConfigHint(path, k),
                    ) {
                        ZhixuTextField(
                            value = text,
                            onValueChange = { next ->
                                text = next
                                val parsed = next.trim().toDoubleOrNull()
                                if (parsed != null) onChange(configWithPathValue(root, nextPath, parsed))
                            },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            placeholder = { Text(k) },
                        )
                    }
                }

                else -> {
                    val isPassword = k.contains("password", ignoreCase = true)
                    val raw = v?.toString().orEmpty()
                    var text by remember(raw) { mutableStateOf(raw) }

                    ConfigField(
                        label = prettyConfigLabel(path, k),
                        hint = prettyConfigHint(path, k),
                    ) {
                        ZhixuTextField(
                            value = text,
                            onValueChange = { next ->
                                text = next
                                onChange(configWithPathValue(root, nextPath, next))
                            },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            placeholder = { Text(k) },
                            visualTransformation =
                                if (isPassword && !showPasswords) PasswordVisualTransformation() else VisualTransformation.None,
                            trailingIcon =
                                if (isPassword) {
                                    {
                                        ZhixuPasswordToggleIconButton(
                                            show = showPasswords,
                                            onClick = onToggleShowPasswords,
                                        )
                                    }
                                } else {
                                    null
                                },
                        )
                    }
                }
            }

            if (idx != keys.lastIndex) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            }
        }
    }
}

@Composable
private fun ConfigField(
    label: String,
    hint: String?,
    content: @Composable () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(text = label, style = MaterialTheme.typography.titleMedium)
        if (!hint.isNullOrBlank()) {
            Text(text = hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        content()
    }
}

private fun prettyConfigLabel(path: List<String>, key: String): String {
    if (path.isNotEmpty()) return key
    return when (key) {
        "endpoint", "url", "apiUrl" -> "接口 URL"
        "username", "user", "userName" -> "用户名"
        "password", "pass", "passwd" -> "密码"
        "blogId" -> "默认博客ID"
        "useCurrentTime" -> "总是使用当前时间作为发布时间"
        "publishTimeOffsetHours" -> "发布偏移（小时）"
        "syncTimeOffsetHours" -> "同步偏移（小时）"
        else -> key
    }
}

private fun prettyConfigHint(path: List<String>, key: String): String? {
    if (path.isNotEmpty()) return null
    return when (key) {
        "endpoint", "url", "apiUrl" -> "填写接口地址，例如：https://your-site.com/xmlrpc.php"
        "username", "user", "userName" -> "用于登录的用户名，确保有发布权限"
        "password", "pass", "passwd" -> "对应用户名的密码（将以安全方式保存）"
        "blogId" -> "如果有多个博客，请设置默认发布的博客ID"
        "useCurrentTime" -> "启用后发布时忽略文章原有时间，直接使用当前时间"
        "publishTimeOffsetHours" -> "调整发布到服务器的时间偏移，例如 +8"
        "syncTimeOffsetHours" -> "同步发布时间时的时间偏移，例如 +8"
        else -> null
    }
}

private fun configWithPathValue(
    root: JSONObject,
    path: List<String>,
    value: Any?,
): JSONObject {
    val out = JSONObject(root.toString())
    if (path.isEmpty()) return out

    var cur = out
    for (i in 0 until path.size - 1) {
        val k = path[i]
        val next = cur.optJSONObject(k) ?: JSONObject().also { cur.put(k, it) }
        cur = next
    }
    val last = path.last()
    when (value) {
        null -> cur.remove(last)
        else -> cur.put(last, value)
    }
    return out
}

@Composable
private fun OfficialPluginRow(
    manifest: PluginManifest,
    installed: Boolean,
    showDivider: Boolean,
    onInstall: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(
                modifier = Modifier.weight(1f).padding(end = 10.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = manifest.name ?: manifest.id,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )

                val meta =
                    listOfNotNull(
                        manifest.version?.takeIf { it.isNotBlank() }?.let { "版本: $it" },
                        "ID: ${manifest.id}",
                    ).joinToString(" · ")
                Text(
                    text = meta,
                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Light),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            ZhixuIconButton(enabled = !installed, onClick = onInstall) {
                Icon(
                    painter =
                        painterResource(
                            if (installed) Ionicons.CheckmarkCircle else Ionicons.ArrowDownCircleOutline,
                        ),
                    contentDescription = if (installed) stringResource(R.string.workshop_installed) else stringResource(R.string.workshop_install),
                )
            }
        }

        val desc = manifest.description.orEmpty().ifBlank { manifest.id }
        if (desc.isNotBlank()) {
            Text(
                modifier = Modifier.padding(top = 6.dp),
                text = desc,
                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Light),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }

        if (showDivider) {
            HorizontalDivider(
                modifier = Modifier.padding(top = 12.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.8f),
            )
        }
    }
}
