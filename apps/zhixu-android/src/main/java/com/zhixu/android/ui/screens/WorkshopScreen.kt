package com.zhixu.android.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.zhixu.android.R
import com.zhixu.android.plugins.InstalledPlugin
import com.zhixu.android.plugins.PluginRepository
import kotlinx.coroutines.launch
import org.json.JSONArray
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

    var showTypechoSettings by remember { mutableStateOf(false) }
    var typechoEndpoint by remember { mutableStateOf("") }
    var typechoUsername by remember { mutableStateOf("") }
    var typechoPassword by remember { mutableStateOf("") }
    var typechoBlogId by remember { mutableStateOf("1") }
    var typechoDefaultCategories by remember { mutableStateOf("") }
    var typechoDefaultTags by remember { mutableStateOf("") }
    var typechoShowPassword by remember { mutableStateOf(false) }
    var typechoSaving by remember { mutableStateOf(false) }

    suspend fun refresh() {
        val root = vaultRootUri ?: return
        installed = pluginRepo.listInstalled(root)
    }

    LaunchedEffect(vaultRootUri) {
        if (vaultRootUri != null) refresh()
    }

    fun loadTypechoConfig(json: JSONObject?) {
        if (json == null) return
        typechoEndpoint = json.optString("endpointUrl", typechoEndpoint)
        typechoUsername = json.optString("username", typechoUsername)
        typechoPassword = json.optString("password", typechoPassword)
        typechoBlogId = json.optString("blogId", typechoBlogId).ifBlank { "1" }
        typechoDefaultCategories =
            json.optJSONArray("defaultCategories")?.let { arr ->
                (0 until arr.length()).mapNotNull { idx -> arr.optString(idx).trim().takeIf { it.isNotBlank() } }.joinToString(", ")
            } ?: json.optString("categories", typechoDefaultCategories)
        typechoDefaultTags =
            json.optJSONArray("defaultTags")?.let { arr ->
                (0 until arr.length()).mapNotNull { idx -> arr.optString(idx).trim().takeIf { it.isNotBlank() } }.joinToString(", ")
            } ?: json.optString("tags", typechoDefaultTags)
    }

    fun buildTypechoConfigJson(): JSONObject {
        val cats = typechoDefaultCategories.split(',').map { it.trim() }.filter { it.isNotBlank() }
        val tags = typechoDefaultTags.split(',').map { it.trim() }.filter { it.isNotBlank() }
        return JSONObject()
            .put("endpointUrl", typechoEndpoint.trim())
            .put("username", typechoUsername.trim())
            .put("password", typechoPassword)
            .put("blogId", typechoBlogId.trim().ifBlank { "1" })
            .put("defaultCategories", JSONArray(cats))
            .put("defaultTags", JSONArray(tags))
    }

    if (showTypechoSettings) {
        AlertDialog(
            onDismissRequest = { if (!typechoSaving) showTypechoSettings = false },
            title = { Text(stringResource(R.string.plugin_typecho_settings_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.imePadding()) {
                    OutlinedTextField(
                        value = typechoEndpoint,
                        onValueChange = { typechoEndpoint = it },
                        label = { Text(stringResource(R.string.plugin_typecho_endpoint)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = typechoUsername,
                        onValueChange = { typechoUsername = it },
                        label = { Text(stringResource(R.string.plugin_typecho_username)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = typechoPassword,
                        onValueChange = { typechoPassword = it },
                        label = { Text(stringResource(R.string.plugin_typecho_password)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        visualTransformation = if (typechoShowPassword) VisualTransformation.None else PasswordVisualTransformation(),
                    )
                    TextButton(onClick = { typechoShowPassword = !typechoShowPassword }) {
                        Text(if (typechoShowPassword) stringResource(R.string.plugin_hide_password) else stringResource(R.string.plugin_show_password))
                    }
                    OutlinedTextField(
                        value = typechoBlogId,
                        onValueChange = { typechoBlogId = it },
                        label = { Text(stringResource(R.string.plugin_typecho_blog_id)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = typechoDefaultCategories,
                        onValueChange = { typechoDefaultCategories = it },
                        label = { Text(stringResource(R.string.plugin_typecho_default_categories)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = typechoDefaultTags,
                        onValueChange = { typechoDefaultTags = it },
                        label = { Text(stringResource(R.string.plugin_typecho_default_tags)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            confirmButton = {
                TextButton(
                    enabled = !typechoSaving,
                    onClick = {
                        val root = vaultRootUri ?: return@TextButton
                        typechoSaving = true
                        scope.launch {
                            val ok =
                                pluginRepo.writePluginConfig(
                                    rootUri = root,
                                    pluginId = "typecho-xmlrpc-publisher",
                                    json = buildTypechoConfigJson(),
                                )
                            typechoSaving = false
                            snackbarHostState.showSnackbar(
                                if (ok) context.getString(R.string.plugin_saved) else context.getString(R.string.plugin_save_failed),
                            )
                            showTypechoSettings = false
                        }
                    },
                ) { Text(stringResource(R.string.plugin_save)) }
            },
            dismissButton = {
                TextButton(
                    enabled = !typechoSaving,
                    onClick = { showTypechoSettings = false },
                ) { Text(stringResource(R.string.action_cancel)) }
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
            onDismissRequest = { showGitDialog = false },
            title = { Text(stringResource(R.string.workshop_install_from_git)) },
            text = {
                OutlinedTextField(
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

    Scaffold(
        contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0, 0, 0, 0),
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
            TopAppBar(
                windowInsets = TopAppBarDefaults.windowInsets,
                title = { Text(stringResource(R.string.workshop_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                        )
                    }
                },
                actions = {
                    IconButton(
                        enabled = vaultRootUri != null,
                        onClick = { scope.launch { refresh() } },
                    ) {
                        Icon(imageVector = Icons.Outlined.Refresh, contentDescription = stringResource(R.string.action_refresh))
                    }
                },
            )
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .padding(contentPadding)
                .padding(innerPadding)
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
                OutlinedTextField(
                    value = searchText,
                    onValueChange = { searchText = it },
                    singleLine = true,
                    placeholder = { Text(stringResource(R.string.workshop_search_hint)) },
                    leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                    shape = MaterialTheme.shapes.extraLarge,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            item {
                Text(text = stringResource(R.string.workshop_section_installed), style = MaterialTheme.typography.titleMedium)
            }

            val filtered =
                installed.filter { p ->
                    val q = searchText.trim()
                    if (q.isBlank()) true
                    else {
                        val t = (p.manifest.name ?: p.manifest.id)
                        t.contains(q, ignoreCase = true) || p.manifest.id.contains(q, ignoreCase = true)
                    }
                }

            if (filtered.isEmpty()) {
                item { Text(stringResource(R.string.workshop_no_plugins)) }
            } else {
                items(filtered.size, key = { filtered[it].manifest.id }) { idx ->
                    val plugin = filtered[idx]
                    PluginRow(
                        plugin = plugin,
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
                        onSettings =
                            if (plugin.manifest.id == "typecho-xmlrpc-publisher") {
                                {
                                    val root = vaultRootUri ?: return@PluginRow
                                    scope.launch {
                                        val cfg = pluginRepo.readPluginConfig(root, "typecho-xmlrpc-publisher")
                                        loadTypechoConfig(cfg)
                                        showTypechoSettings = true
                                    }
                                }
                            } else {
                                null
                            },
                    )
                }
            }

            item { HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp)) }

            item {
                Text(text = stringResource(R.string.workshop_section_install), style = MaterialTheme.typography.titleMedium)
            }

            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    FilledTonalButton(
                        modifier = Modifier.weight(1f),
                        onClick = { showGitDialog = true },
                    ) { Text(stringResource(R.string.workshop_install_from_git)) }
                    FilledTonalButton(
                        modifier = Modifier.weight(1f),
                        onClick = { folderLauncher.launch(null) },
                    ) { Text(stringResource(R.string.workshop_install_from_folder)) }
                }
            }

            item { HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp)) }

            item {
                Text(text = stringResource(R.string.workshop_section_bundled), style = MaterialTheme.typography.titleMedium)
            }
            item {
                BundledPluginRow(
                    title = stringResource(R.string.bundled_typecho_name),
                    desc = stringResource(R.string.bundled_typecho_desc),
                    onInstall = {
                        val root = vaultRootUri ?: return@BundledPluginRow
                        scope.launch {
                            val result = pluginRepo.installBundledPlugin(root, "typecho-xmlrpc-publisher")
                            snackbarHostState.showSnackbar(result.message)
                            refresh()
                        }
                    },
                )
            }

            item { Spacer(modifier = Modifier.height(24.dp)) }
        }
    }
}

@Composable
private fun PluginRow(
    plugin: InstalledPlugin,
    onToggle: (Boolean) -> Unit,
    onRemove: () -> Unit,
    onSettings: (() -> Unit)?,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface,
            ),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.18f)),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = plugin.manifest.name ?: plugin.manifest.id,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                TextButton(enabled = onSettings != null, onClick = { onSettings?.invoke() }) {
                    Text(stringResource(R.string.workshop_view_details))
                }
            }

            val desc = plugin.manifest.description.orEmpty().ifBlank { plugin.manifest.id }
            Text(
                text = desc,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = plugin.manifest.version?.let { "v$it" } ?: "",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = onRemove) { Text(stringResource(R.string.workshop_remove)) }
                    Switch(checked = plugin.enabled, onCheckedChange = onToggle)
                }
            }
        }
    }
}

@Composable
private fun BundledPluginRow(
    title: String,
    desc: String,
    onInstall: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface,
            ),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.18f)),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                TextButton(onClick = onInstall) { Text(stringResource(R.string.workshop_install)) }
            }
            Text(
                text = desc,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
