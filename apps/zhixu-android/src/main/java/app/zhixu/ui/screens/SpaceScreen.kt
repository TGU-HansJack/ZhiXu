package app.zhixu.ui.screens

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.zhixu.R
import app.zhixu.data.VaultRepository
import app.zhixu.data.SearchResult
import app.zhixu.ui.DocListMutation
import app.zhixu.ui.Heroicons
import app.zhixu.ui.Ionicons
import app.zhixu.ui.components.VaultSearchDialog
import app.zhixu.ui.components.ZhixuIconButton
import app.zhixu.ui.components.ZhixuTextField
import app.zhixu.ui.components.VaultDrawer
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun SpaceScreen(
    contentPadding: PaddingValues,
    vaultRootUri: Uri?,
    repository: VaultRepository,
    isActive: Boolean,
    refreshToken: Long,
    mutation: DocListMutation?,
    onDocListMutated: (DocListMutation) -> Unit,
    onOpenDoc: (String, Int?) -> Unit,
    onChangeVault: () -> Unit,
    selectedEntryUris: Set<String>,
    onToggleEntrySelection: (String) -> Unit,
    onClearEntrySelection: () -> Unit,
) {
    val root = vaultRootUri
    if (root == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text = stringResource(R.string.workshop_no_vault),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.clickable(onClick = onChangeVault),
            )
        }
        return
    }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var isTreeExpanded by remember { mutableStateOf(false) }
    var activeDirectoryRelativePath by remember { mutableStateOf<String?>(null) }
    var showNewFolderDialog by remember { mutableStateOf(false) }
    var newFolderName by remember { mutableStateOf("") }
    var showSearchSheet by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var searchResults by remember { mutableStateOf<List<SearchResult>>(emptyList()) }
    var searchJob by remember { mutableStateOf<Job?>(null) }
    val highlightBg = MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)

    fun clearSearch() {
        searchQuery = ""
        searchResults = emptyList()
        searchJob?.cancel()
        searchJob = null
    }

    fun updateSearchQuery(newQuery: String) {
        searchQuery = newQuery
        searchJob?.cancel()
        searchJob =
            scope.launch {
                delay(200)
                if (searchQuery.isBlank()) {
                    searchResults = emptyList()
                    return@launch
                }
                searchResults =
                    withContext(Dispatchers.IO) {
                        repository.search(rootUri = null, query = searchQuery).take(50)
                    }
            }
    }

    val uploadLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.OpenMultipleDocuments(),
        ) { uris ->
            if (uris.isEmpty()) return@rememberLauncherForActivityResult
            scope.launch {
                runCatching {
                    val created =
                        repository.importFiles(
                            rootUri = root,
                            targetDirRelativePath = activeDirectoryRelativePath,
                            sourceUris = uris,
                        )
                    val any = created.firstOrNull() ?: return@runCatching
                    onDocListMutated(DocListMutation.EntryChanged(any))
                }.onFailure {
                    Toast.makeText(context, it.message ?: context.getString(R.string.common_failed), Toast.LENGTH_SHORT).show()
                }
            }
        }


    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(contentPadding),
    ) {
        SpaceTopToolbar(
            isExpanded = isTreeExpanded,
            onUpload = {
                uploadLauncher.launch(arrayOf("*/*"))
            },
            onNewFolder = {
                newFolderName = ""
                showNewFolderDialog = true
            },
            onToggleExpanded = {
                isTreeExpanded = !isTreeExpanded
            },
            onSearch = {
                showSearchSheet = true
            },
        )

        if (showSearchSheet) {
            VaultSearchDialog(
                query = searchQuery,
                onQueryChange = ::updateSearchQuery,
                results = searchResults,
                highlightBg = highlightBg,
                onDismiss = {
                    showSearchSheet = false
                    clearSearch()
                },
                onOpenResult = { uriStr, lineIndex ->
                    showSearchSheet = false
                    onOpenDoc(uriStr, lineIndex)
                    clearSearch()
                },
            )
        }

        if (showNewFolderDialog) {
            AlertDialog(
                onDismissRequest = { showNewFolderDialog = false },
                title = { Text(stringResource(R.string.action_new_folder)) },
                text = {
                    ZhixuTextField(
                        value = newFolderName,
                        onValueChange = { newFolderName = it },
                        singleLine = true,
                        placeholder = { Text(stringResource(R.string.action_new_folder)) },
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            val input =
                                newFolderName
                                    .trim()
                                    .replace('/', '_')
                                    .replace('\\', '_')
                                    .ifBlank { return@TextButton }
                            showNewFolderDialog = false
                            scope.launch {
                                runCatching {
                                    val dirUri =
                                        repository.createDirectory(
                                            rootUri = root,
                                            parentRelativePath = activeDirectoryRelativePath,
                                            name = input,
                                        ) ?: return@runCatching
                                    onDocListMutated(DocListMutation.EntryChanged(dirUri))
                                }.onFailure {
                                    Toast.makeText(context, it.message ?: context.getString(R.string.common_failed), Toast.LENGTH_SHORT).show()
                                }
                            }
                        },
                        enabled = newFolderName.trim().isNotBlank(),
                    ) { Text(stringResource(R.string.action_create)) }
                },
                dismissButton = {
                    TextButton(onClick = { showNewFolderDialog = false }) { Text(stringResource(R.string.action_cancel)) }
                },
            )
        }

        VaultDrawer(
            vaultRootUri = root,
            repository = repository,
            onOpenDoc = { uriStr -> onOpenDoc(uriStr, null) },
            onCloseDrawer = {},
            isActive = isActive,
            refreshToken = refreshToken,
            mutation = mutation,
            onDocListMutated = onDocListMutated,
            enableMultiSelect = true,
            selectedEntryUris = selectedEntryUris,
            onToggleEntrySelection = onToggleEntrySelection,
            sheetWidth = Dp.Unspecified,
            contentPadding = PaddingValues(0.dp),
            useSystemInsets = false,
            showHeader = false,
            itemMinHeight = 40.dp,
            itemTextStyle = MaterialTheme.typography.bodyMedium,
            itemIconSize = 20.dp,
            itemChevronSize = 18.dp,
            allDirsExpanded = isTreeExpanded,
            modifier = Modifier.fillMaxWidth().weight(1f),
            activeDirectoryRelativePath = activeDirectoryRelativePath,
            onActiveDirectoryChange = { activeDirectoryRelativePath = it },
        )
    }
}

@Composable
private fun SpaceTopToolbar(
    isExpanded: Boolean,
    onUpload: () -> Unit,
    onNewFolder: () -> Unit,
    onToggleExpanded: () -> Unit,
    onSearch: () -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val buttonSize = 32.dp
                val iconSize = 20.dp

                ZhixuIconButton(onClick = onUpload, modifier = Modifier.size(buttonSize)) {
                    Icon(
                        painter = painterResource(Heroicons.ArrowUpTray),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(iconSize),
                    )
                }
                ZhixuIconButton(onClick = onNewFolder, modifier = Modifier.size(buttonSize)) {
                    Icon(
                        painter = painterResource(Heroicons.FolderPlus),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(iconSize),
                    )
                }
                ZhixuIconButton(onClick = onToggleExpanded, modifier = Modifier.size(buttonSize)) {
                    Icon(
                        painter =
                            painterResource(
                                if (isExpanded) Ionicons.ChevronCollapseOutline else Ionicons.ChevronExpandOutline,
                            ),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(iconSize),
                    )
                }

                Spacer(modifier = Modifier.weight(1f))

                ZhixuIconButton(onClick = onSearch, modifier = Modifier.size(buttonSize)) {
                    Icon(
                        painter = painterResource(Ionicons.Search),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(iconSize),
                    )
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f))
        }
    }
}
