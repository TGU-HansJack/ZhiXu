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
import androidx.compose.material3.Checkbox
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
import app.zhixu.data.VaultTreeEntry
import app.zhixu.data.SearchResult
import app.zhixu.ui.DocListMutation
import app.zhixu.ui.Heroicons
import app.zhixu.ui.Ionicons
import app.zhixu.ui.components.VaultSearchDialog
import app.zhixu.ui.components.ZhixuIconButton
import app.zhixu.ui.components.ZhixuTextField
import app.zhixu.ui.components.VaultDrawer
import app.zhixu.ui.components.calendar.SimpleDatePickerDialog
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.ZoneId

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
    var showFilterDialog by remember { mutableStateOf(false) }
    var filter by remember { mutableStateOf(SpaceFilter()) }
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
            onFilter = {
                showFilterDialog = true
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

        if (showFilterDialog) {
            var draftTypes by remember { mutableStateOf(filter.types) }
            var draftStart by remember { mutableStateOf(filter.startDate) }
            var draftEnd by remember { mutableStateOf(filter.endDate) }
            var showStartPicker by remember { mutableStateOf(false) }
            var showEndPicker by remember { mutableStateOf(false) }

            fun toggleType(t: SpaceFileType) {
                draftTypes = if (t in draftTypes) draftTypes - t else draftTypes + t
                if (draftTypes.isEmpty()) draftTypes = SpaceFileType.entries.toSet()
            }

            if (showStartPicker) {
                SimpleDatePickerDialog(
                    initialDate = draftStart ?: LocalDate.now(),
                    onDateSelected = { picked ->
                        draftStart = picked
                        if (draftEnd != null && picked.isAfter(draftEnd)) draftEnd = picked
                    },
                    onDismiss = { showStartPicker = false },
                )
            }
            if (showEndPicker) {
                SimpleDatePickerDialog(
                    initialDate = draftEnd ?: draftStart ?: LocalDate.now(),
                    onDateSelected = { picked ->
                        draftEnd = picked
                        if (draftStart != null && picked.isBefore(draftStart)) draftStart = picked
                    },
                    onDismiss = { showEndPicker = false },
                )
            }

            AlertDialog(
                onDismissRequest = { showFilterDialog = false },
                title = { Text(stringResource(R.string.space_filter_title)) },
                text = {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = stringResource(R.string.space_filter_type),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.labelMedium,
                        )
                        SpaceFilterCheckRow(
                            checked = SpaceFileType.Markdown in draftTypes,
                            label = stringResource(R.string.space_filter_type_markdown),
                            onToggle = { toggleType(SpaceFileType.Markdown) },
                        )
                        SpaceFilterCheckRow(
                            checked = SpaceFileType.Pdf in draftTypes,
                            label = stringResource(R.string.space_filter_type_pdf),
                            onToggle = { toggleType(SpaceFileType.Pdf) },
                        )
                        SpaceFilterCheckRow(
                            checked = SpaceFileType.Image in draftTypes,
                            label = stringResource(R.string.space_filter_type_image),
                            onToggle = { toggleType(SpaceFileType.Image) },
                        )
                        SpaceFilterCheckRow(
                            checked = SpaceFileType.Other in draftTypes,
                            label = stringResource(R.string.space_filter_type_other),
                            onToggle = { toggleType(SpaceFileType.Other) },
                        )

                        Spacer(modifier = Modifier.size(12.dp))
                        Text(
                            text = stringResource(R.string.space_filter_time),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.labelMedium,
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            TextButton(onClick = { showStartPicker = true }) {
                                Text(
                                    text = draftStart?.toString() ?: stringResource(R.string.space_filter_start),
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                            }
                            Spacer(modifier = Modifier.weight(1f))
                            TextButton(onClick = { showEndPicker = true }) {
                                Text(
                                    text = draftEnd?.toString() ?: stringResource(R.string.space_filter_end),
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            filter = SpaceFilter(types = draftTypes, startDate = draftStart, endDate = draftEnd)
                            showFilterDialog = false
                        },
                    ) { Text(stringResource(R.string.space_filter_apply)) }
                },
                dismissButton = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TextButton(
                            onClick = {
                                draftTypes = SpaceFileType.entries.toSet()
                                draftStart = null
                                draftEnd = null
                            },
                        ) { Text(stringResource(R.string.space_filter_clear)) }
                        TextButton(onClick = { showFilterDialog = false }) { Text(stringResource(R.string.action_cancel)) }
                    }
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
            entryFilter = { entry -> filter.matches(entry, ZoneId.systemDefault()) },
        )
    }
}

@Composable
private fun SpaceTopToolbar(
    isExpanded: Boolean,
    onUpload: () -> Unit,
    onNewFolder: () -> Unit,
    onFilter: () -> Unit,
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
                ZhixuIconButton(onClick = onFilter, modifier = Modifier.size(buttonSize)) {
                    Icon(
                        painter = painterResource(Heroicons.Funnel),
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

@Composable
private fun SpaceFilterCheckRow(
    checked: Boolean,
    label: String,
    onToggle: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onToggle)
                .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = checked, onCheckedChange = { onToggle() })
        Spacer(modifier = Modifier.size(6.dp))
        Text(text = label, style = MaterialTheme.typography.bodyMedium)
    }
}

private enum class SpaceFileType {
    Markdown,
    Pdf,
    Image,
    Other,
}

private data class SpaceFilter(
    val types: Set<SpaceFileType> = SpaceFileType.entries.toSet(),
    val startDate: LocalDate? = null,
    val endDate: LocalDate? = null,
) {
    fun matches(entry: VaultTreeEntry, zoneId: ZoneId): Boolean {
        if (entry.isDirectory) return true

        val ext = entry.name.substringAfterLast('.', missingDelimiterValue = "").lowercase()
        val type =
            when {
                ext == "md" -> SpaceFileType.Markdown
                ext == "pdf" -> SpaceFileType.Pdf
                ext in setOf("png", "jpg", "jpeg", "webp", "gif", "bmp", "heic", "heif", "tif", "tiff", "svg", "avif") ->
                    SpaceFileType.Image
                else -> SpaceFileType.Other
            }
        if (type !in types) return false

        if (startDate == null && endDate == null) return true
        val last = entry.lastModified
        if (last <= 0L) return true

        val startMs =
            startDate
                ?.atStartOfDay(zoneId)
                ?.toInstant()
                ?.toEpochMilli()
                ?: Long.MIN_VALUE
        val endMsExclusive =
            endDate
                ?.plusDays(1)
                ?.atStartOfDay(zoneId)
                ?.toInstant()
                ?.toEpochMilli()
                ?: Long.MAX_VALUE

        return last in startMs until endMsExclusive
    }
}
