package app.zhixu.ui.screens

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.zhixu.R
import app.zhixu.data.VaultRepository
import app.zhixu.data.SearchResult
import app.zhixu.sync.VaultAutoSync
import app.zhixu.ui.DocListMutation
import app.zhixu.ui.components.RefreshStatusBanner
import app.zhixu.ui.components.VaultSearchDialog
import app.zhixu.ui.components.ZhixuTextField
import app.zhixu.ui.components.VaultDrawer
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpaceScreen(
    contentPadding: PaddingValues,
    vaultRootUri: Uri?,
    repository: VaultRepository,
    isActive: Boolean,
    searchRequestToken: Long,
    uploadRequestToken: Long,
    newFolderRequestToken: Long,
    allDirsExpanded: Boolean,
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
        Box(
            modifier =
                Modifier
                    .padding(contentPadding)
                    .background(MaterialTheme.colorScheme.background)
                    .fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
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
    var activeDirectoryRelativePath by remember { mutableStateOf<String?>(null) }
    var showNewFolderDialog by remember { mutableStateOf(false) }
    var newFolderName by remember { mutableStateOf("") }
    var showSearchSheet by remember { mutableStateOf(false) }
    var pendingOpenSearch by remember { mutableStateOf(false) }
    var pendingOpenUpload by remember { mutableStateOf(false) }
    var pendingOpenNewFolder by remember { mutableStateOf(false) }
    var lastSearchToken by remember { mutableLongStateOf(searchRequestToken) }
    var lastUploadToken by remember { mutableLongStateOf(uploadRequestToken) }
    var lastNewFolderToken by remember { mutableLongStateOf(newFolderRequestToken) }
    var searchQuery by remember { mutableStateOf("") }
    var searchResults by remember { mutableStateOf<List<SearchResult>>(emptyList()) }
    var searchJob by remember { mutableStateOf<Job?>(null) }
    val highlightBg = MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
    val listBg = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.70f)
    val pullState = rememberPullToRefreshState()
    var isPullRefreshing by remember(root) { mutableStateOf(false) }
    var lastRefreshBannerAtMs by remember(root) { mutableStateOf(0L) }
    var manualRefreshToken by remember(root) { mutableLongStateOf(0L) }

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
                    created.forEach { createdUri ->
                        runCatching {
                            VaultAutoSync.maybeUploadDoc(
                                context = context,
                                repository = repository,
                                vaultRootUri = root,
                                docUri = createdUri,
                                force = true,
                            )
                        }
                    }
                }.onFailure {
                    Toast.makeText(context, it.message ?: context.getString(R.string.common_failed), Toast.LENGTH_SHORT).show()
                }
            }
        }

    androidx.compose.runtime.LaunchedEffect(searchRequestToken) {
        if (searchRequestToken == lastSearchToken) return@LaunchedEffect
        lastSearchToken = searchRequestToken
        pendingOpenSearch = true
    }

    androidx.compose.runtime.LaunchedEffect(isActive, pendingOpenSearch) {
        if (!isActive || !pendingOpenSearch) return@LaunchedEffect
        pendingOpenSearch = false
        showSearchSheet = true
    }

    androidx.compose.runtime.LaunchedEffect(uploadRequestToken) {
        if (uploadRequestToken == lastUploadToken) return@LaunchedEffect
        lastUploadToken = uploadRequestToken
        pendingOpenUpload = true
    }

    androidx.compose.runtime.LaunchedEffect(isActive, pendingOpenUpload) {
        if (!isActive || !pendingOpenUpload) return@LaunchedEffect
        pendingOpenUpload = false
        uploadLauncher.launch(arrayOf("*/*"))
    }

    androidx.compose.runtime.LaunchedEffect(newFolderRequestToken) {
        if (newFolderRequestToken == lastNewFolderToken) return@LaunchedEffect
        lastNewFolderToken = newFolderRequestToken
        pendingOpenNewFolder = true
    }

    androidx.compose.runtime.LaunchedEffect(isActive, pendingOpenNewFolder) {
        if (!isActive || !pendingOpenNewFolder) return@LaunchedEffect
        pendingOpenNewFolder = false
        newFolderName = ""
        showNewFolderDialog = true
    }


    PullToRefreshBox(
        isRefreshing = isActive && isPullRefreshing,
        onRefresh = {
            if (!isActive || isPullRefreshing) return@PullToRefreshBox
            isPullRefreshing = true
            manualRefreshToken += 1L
        },
        state = pullState,
        modifier =
            Modifier
                .fillMaxSize()
                .padding(contentPadding)
                .background(MaterialTheme.colorScheme.background),
        indicator = {},
    ) {
        Box(modifier = Modifier.fillMaxSize().background(listBg)) {
            VaultDrawer(
                vaultRootUri = root,
                repository = repository,
                onOpenDoc = { uriStr -> onOpenDoc(uriStr, null) },
                onCloseDrawer = {},
                isActive = isActive,
                refreshToken = refreshToken,
                mutation = mutation,
                manualRefreshToken = manualRefreshToken,
                onManualRefreshComplete = { success ->
                    isPullRefreshing = false
                    if (success) lastRefreshBannerAtMs = android.os.SystemClock.uptimeMillis()
                },
                onDocListMutated = onDocListMutated,
                enableMultiSelect = false,
                sheetWidth = Dp.Unspecified,
                drawerContainerColor = Color.Transparent,
                contentPadding = PaddingValues(0.dp),
                useSystemInsets = false,
                showHeader = false,
                itemMinHeight = 40.dp,
                itemTextStyle = MaterialTheme.typography.bodyMedium,
                itemIconSize = 20.dp,
                itemChevronSize = 18.dp,
                listContentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
                listItemSpacing = 10.dp,
                useDocumentListStyle = true,
                documentItemPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                itemShape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
                allDirsExpanded = allDirsExpanded,
                showEntryIcons = false,
                showEntryActions = false,
                highlightActiveDirectory = false,
                modifier = Modifier.fillMaxSize(),
                activeDirectoryRelativePath = activeDirectoryRelativePath,
                onActiveDirectoryChange = { activeDirectoryRelativePath = it },
            )

            RefreshStatusBanner(
                isRefreshing = isPullRefreshing,
                lastRefreshedAtMs = lastRefreshBannerAtMs,
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 10.dp),
            )
        }
    }

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
}
