package app.zhixu.ui.screens

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ArrowForward
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SheetState
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import app.zhixu.ui.components.ZhixuIconButton
import app.zhixu.ui.components.ZhixuTextField
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import app.zhixu.R
import app.zhixu.ui.Ionicons
import app.zhixu.ui.components.RefreshStatusBanner
import app.zhixu.data.UiTask
import app.zhixu.data.VaultIndexRepository
import app.zhixu.data.VaultRepository
import app.zhixu.sync.VaultAutoSync
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import kotlin.math.max

data class TaskKey(
    val docUri: String,
    val lineIndex: Int,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TasksScreen(
    contentPadding: PaddingValues,
    vaultRootUri: Uri?,
    repository: VaultRepository,
    isActive: Boolean,
    onOpenDoc: (String, String?, Int?) -> Unit,
    selectedTasks: Set<TaskKey>,
    onToggleTaskSelection: (TaskKey) -> Unit,
    onClearTaskSelection: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val initialCacheEntry = remember(vaultRootUri) { TasksScreenCache.get(vaultRootUri) }
    var tasks by
        remember(vaultRootUri) {
            mutableStateOf(initialCacheEntry?.tasks ?: emptyList())
        }
    var completed by
        remember(vaultRootUri) {
            mutableStateOf(initialCacheEntry?.completed ?: emptyList())
        }
    var showCompleted by remember { mutableStateOf(false) }
    var addJob by remember { mutableStateOf<Job?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }
    val dueFormatter = remember { DateTimeFormatter.ofPattern("MM-dd HH:mm") }
    var lastRefreshAtMs by remember { mutableStateOf(0L) }
    var lastRefreshBannerAtMs by remember { mutableStateOf(0L) }
    var isPullRefreshing by remember(vaultRootUri) { mutableStateOf(false) }
    var cacheUpdatedAtMs by remember(vaultRootUri) { mutableStateOf(initialCacheEntry?.updatedAtMs ?: 0L) }
    var indexReady by remember(vaultRootUri) { mutableStateOf<Boolean?>(null) }
    var indexBuilding by remember(vaultRootUri) { mutableStateOf(false) }

    suspend fun refresh(force: Boolean, showUpdatedBanner: Boolean) {
        val root = vaultRootUri ?: return
        val now = android.os.SystemClock.uptimeMillis()
        if (!force && now - lastRefreshAtMs in 0..500) return

        isPullRefreshing = true
        val (ready, nextTasks, nextCompleted) =
            try {
                withContext(Dispatchers.IO) {
                    val isReady = runCatching { repository.hasAnyIndexedDocs() }.getOrDefault(false)
                    if (!isReady) {
                        Triple(false, emptyList(), emptyList())
                    } else {
                        val t = repository.getAllTasks(status = VaultIndexRepository.TaskStatusFilter.Undone)
                        val c = repository.getRecentCompletedTasks(limit = 50)
                        Triple(true, t, c)
                    }
                }
            } finally {
                isPullRefreshing = false
            }

        indexReady = ready
        tasks = nextTasks
        completed = nextCompleted
        val updatedAtMs = System.currentTimeMillis()
        cacheUpdatedAtMs = updatedAtMs
        TasksScreenCache.put(root, TasksScreenCache.Entry(tasks = nextTasks, completed = nextCompleted, updatedAtMs = updatedAtMs))
        lastRefreshAtMs = android.os.SystemClock.uptimeMillis()
        if (showUpdatedBanner) lastRefreshBannerAtMs = android.os.SystemClock.uptimeMillis()
    }

    fun requestIndexBuild() {
        val root = vaultRootUri ?: return
        if (indexBuilding) return
        scope.launch {
            indexBuilding = true
            runCatching {
                withContext(Dispatchers.IO) { repository.rebuildIndex(root) }
            }
            indexBuilding = false
            refresh(force = true, showUpdatedBanner = false)
        }
    }

    fun submitTaskDraft(draft: TaskDraft) {
        val root = vaultRootUri ?: return
        val title = draft.title.trim()
        if (title.isBlank()) return
        addJob?.cancel()
        addJob =
            scope.launch {
                val ok =
                    repository.addTaskToInbox(
                        rootUri = root,
                        title = title,
                        dueDate = draft.dueDate,
                        dueTime = draft.dueTime,
                        tags = draft.tags,
                        priority = draft.priority,
                    )
                if (!ok) {
                    snackbarHostState.showSnackbar("添加失败")
                    return@launch
                }

                runCatching {
                    VaultAutoSync.maybeUploadInbox(
                        context = context,
                        repository = repository,
                        vaultRootUri = root,
                        force = true,
                    )
                }
                refresh(force = true, showUpdatedBanner = false)
            }
    }

    LaunchedEffect(vaultRootUri, isActive) {
        if (!isActive) return@LaunchedEffect
        if (vaultRootUri == null) return@LaunchedEffect
        indexReady = withContext(Dispatchers.IO) { runCatching { repository.hasAnyIndexedDocs() }.getOrDefault(false) }
        val cacheAgeMs = if (cacheUpdatedAtMs > 0L) (System.currentTimeMillis() - cacheUpdatedAtMs) else Long.MAX_VALUE
        if (indexReady == true && (tasks.isEmpty() || cacheAgeMs > 3 * 60 * 1000L)) {
            refresh(force = tasks.isEmpty(), showUpdatedBanner = false)
        }
    }

    LaunchedEffect(vaultRootUri, isActive) {
        if (!isActive) return@LaunchedEffect
        if (vaultRootUri == null) return@LaunchedEffect
        repository.indexChanges.collect {
            refresh(force = false, showUpdatedBanner = false)
        }
    }


    LaunchedEffect(vaultRootUri, isActive, indexReady) {
        if (!isActive) return@LaunchedEffect
        val root = vaultRootUri ?: return@LaunchedEffect
        if (indexReady != false) return@LaunchedEffect
        while (currentCoroutineContext().isActive) {
            indexBuilding = repository.isIndexBuildInProgress()
            val ready = withContext(Dispatchers.IO) { runCatching { repository.hasAnyIndexedDocs() }.getOrDefault(false) }
            indexReady = ready
            if (ready) {
                refresh(force = true, showUpdatedBanner = false)
                return@LaunchedEffect
            }
            delay(2_000)
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
    ) { padding ->
        androidx.compose.foundation.layout.Column(
            modifier = Modifier
                .padding(contentPadding)
                .padding(padding)
                .fillMaxSize(),
        ) {
            val pullState = rememberPullToRefreshState()
            PullToRefreshBox(
                isRefreshing = isActive && isPullRefreshing,
                onRefresh = { if (isActive) scope.launch { refresh(force = true, showUpdatedBanner = true) } },
                state = pullState,
                modifier = Modifier.fillMaxSize(),
                indicator = {},
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    val selectionMode = selectedTasks.isNotEmpty()
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(0.dp),
                    ) {
                        if (vaultRootUri == null) {
                            item { Text(stringResource(R.string.settings_vault_not_selected), modifier = Modifier.padding(16.dp)) }
                            return@LazyColumn
                        }

                        if (indexReady == false) {
                            item {
                                androidx.compose.foundation.layout.Column(
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                     Text(
                                         text = if (indexBuilding || repository.isIndexBuildInProgress()) "索引构建中…" else "任务索引未就绪（将在后台空闲时自动构建）",
                                         color = MaterialTheme.colorScheme.onSurfaceVariant,
                                         style = MaterialTheme.typography.bodySmall,
                                     )
                                     TextButton(
                                         onClick = { requestIndexBuild() },
                                         enabled = !(indexBuilding || repository.isIndexBuildInProgress()),
                                         contentPadding = PaddingValues(0.dp),
                                     ) {
                                         Text("立即构建索引")
                                     }
                                     HorizontalDivider(thickness = 0.5.dp)
                                 }
                             }
                        }

                        item {
                            TaskComposer(
                                onSubmit = { draft -> submitTaskDraft(draft) },
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }

                        item { HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), thickness = 0.5.dp) }

                        if (tasks.isEmpty()) {
                            item { Text(stringResource(R.string.tasks_empty), modifier = Modifier.padding(16.dp)) }
                        }
                        items(tasks, key = { it.taskId ?: "${it.docUri}:${it.lineIndex}" }) { task ->
                            val dueEpochMillis = remember(task.dueEpochMillis) { task.dueEpochMillis?.takeIf { it > 0L } }
                            val dueLabel = remember(dueEpochMillis) { dueEpochMillis?.let(::formatDueLabel) }
                            val key = remember(task.docUri, task.lineIndex) { TaskKey(task.docUri.toString(), task.lineIndex) }
                            val selected = key in selectedTasks
                            TaskRow(
                                task = task,
                                dueLabel = dueLabel,
                                onToggle = {
                                    if (selectionMode) {
                                        onToggleTaskSelection(key)
                                    } else {
                                        scope.launch {
                                            repository.toggleTask(task.docUri, task.lineIndex)
                                            runCatching {
                                                VaultAutoSync.maybeUploadDoc(
                                                    context = context,
                                                    repository = repository,
                                                    vaultRootUri = vaultRootUri,
                                                    docUri = task.docUri,
                                                    force = false,
                                                )
                                            }
                                            refresh(force = true, showUpdatedBanner = false)
                                        }
                                    }
                                },
                                selectionMode = selectionMode,
                                selected = selected,
                                onOpen = {
                                    if (selectionMode) onToggleTaskSelection(key) else onOpenDoc(task.docUri.toString(), null, task.lineIndex)
                                },
                                onLongPress = { onToggleTaskSelection(key) },
                                dimmed = false,
                            )
                            HorizontalDivider(thickness = 0.5.dp)
                        }

                        item {
                            val count = completed.size
                            Row(
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .clickable { showCompleted = !showCompleted }
                                        .heightIn(min = 40.dp)
                                        .padding(start = 0.dp, end = 6.dp, top = 4.dp, bottom = 4.dp),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(
                                    painter = painterResource(if (showCompleted) Ionicons.ChevronDown else Ionicons.ChevronForward),
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Text(stringResource(R.string.tasks_completed))
                                Text(text = count.toString(), color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }

                        if (showCompleted) {
                            items(completed, key = { "c:${it.docUri}:${it.lineIndex}:${it.taskId}" }) { task ->
                                val dueEpochMillis = remember(task.dueEpochMillis) { task.dueEpochMillis?.takeIf { it > 0L } }
                                val key = remember(task.docUri, task.lineIndex) { TaskKey(task.docUri.toString(), task.lineIndex) }
                                val selected = key in selectedTasks
                                TaskRow(
                                    task = task,
                                    dueLabel =
                                        dueEpochMillis?.let { dueFormatter.format(Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault())) },
                                    onToggle = {
                                        if (selectionMode) {
                                            onToggleTaskSelection(key)
                                        } else {
                                            scope.launch {
                                                repository.toggleTask(task.docUri, task.lineIndex)
                                                runCatching {
                                                    VaultAutoSync.maybeUploadDoc(
                                                        context = context,
                                                        repository = repository,
                                                        vaultRootUri = vaultRootUri,
                                                        docUri = task.docUri,
                                                        force = false,
                                                    )
                                                }
                                                refresh(force = true, showUpdatedBanner = false)
                                            }
                                        }
                                    },
                                    selectionMode = selectionMode,
                                    selected = selected,
                                    onOpen = {
                                        if (selectionMode) onToggleTaskSelection(key) else onOpenDoc(task.docUri.toString(), null, task.lineIndex)
                                    },
                                    onLongPress = { onToggleTaskSelection(key) },
                                    dimmed = true,
                                )
                                HorizontalDivider(thickness = 0.5.dp)
                            }
                        }
                    }

                    RefreshStatusBanner(
                        isRefreshing = isPullRefreshing,
                        lastRefreshedAtMs = lastRefreshBannerAtMs,
                        modifier = Modifier.align(Alignment.TopCenter).padding(top = 10.dp),
                    )
                }
            }
        }
    }
}

private object TasksScreenCache {
    private const val MaxEntries = 4
    private val lock = Any()
    private val cache =
        object : LinkedHashMap<String, Entry>(MaxEntries, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Entry>?): Boolean = size > MaxEntries
        }

    data class Entry(
        val tasks: List<UiTask>,
        val completed: List<UiTask>,
        val updatedAtMs: Long,
    )

    fun get(rootUri: Uri?): Entry? {
        val key = rootUri?.toString().orEmpty()
        if (key.isBlank()) return null
        return synchronized(lock) { cache[key] }
    }

    fun put(
        rootUri: Uri,
        entry: Entry,
    ) {
        synchronized(lock) { cache[rootUri.toString()] = entry }
    }
}

internal data class TaskDraft(
    val title: String,
    val dueDate: LocalDate?,
    val dueTime: LocalTime?,
    val tags: List<String>,
    val priority: Int?,
)

private enum class TimeRange(val label: String, val defaultTime: LocalTime?) {
    Morning("上午", LocalTime.of(9, 0)),
    Afternoon("下午", LocalTime.of(14, 0)),
    Evening("晚上", LocalTime.of(19, 0)),
    Night("深夜", LocalTime.of(22, 0)),
    AllDay("全天", null),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TaskComposer(
    onSubmit: (TaskDraft) -> Unit,
    modifier: Modifier = Modifier,
) {
    var text by remember { mutableStateOf("") }
    var dueDate by remember { mutableStateOf<LocalDate?>(null) }
    var timeRange by remember { mutableStateOf<TimeRange?>(null) }
    var tags by remember { mutableStateOf<List<String>>(emptyList()) }
    var priority by remember { mutableStateOf<Int?>(null) }
    var showDateSheet by remember { mutableStateOf(false) }
    var showPrioritySheet by remember { mutableStateOf(false) }
    var showTagSheet by remember { mutableStateOf(false) }

    val currentOnSubmit by rememberUpdatedState(onSubmit)
    fun submit() {
        val (cleanTitle, detected) = extractTaskDraft(text)
        val finalTitle = cleanTitle.trim()
        if (finalTitle.isBlank()) return

        val finalDate = dueDate ?: detected.dueDate
        val finalRange = timeRange ?: detected.timeRange
        val finalTime = finalRange?.defaultTime
        val finalPriority = priority ?: detected.priority
        val finalTags = (tags + detected.tags).map { it.trim() }.filter { it.isNotBlank() }.distinct()

        currentOnSubmit(
            TaskDraft(
                title = finalTitle,
                dueDate = finalDate,
                dueTime = finalTime,
                tags = finalTags,
                priority = finalPriority,
            ),
        )

        text = ""
        dueDate = null
        timeRange = null
        tags = emptyList()
        priority = null
    }

    ZhixuTextField(
        value = text,
        onValueChange = { text = it },
        modifier = modifier.padding(horizontal = 16.dp),
        singleLine = true,
        placeholder = { Text(stringResource(R.string.task_input_hint)) },
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { submit() }),
        trailingIcon = {
            ZhixuIconButton(
                onClick = { submit() },
                enabled = text.isNotBlank(),
            ) {
                Icon(
                    imageVector = Icons.Outlined.ArrowForward,
                    contentDescription = stringResource(R.string.task_input_add),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        },
        interactionSource = remember { MutableInteractionSource() },
    )

    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(top = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            ZhixuIconButton(
                onClick = { showDateSheet = true },
                modifier = Modifier.size(36.dp),
            ) {
                Icon(
                    painter = painterResource(Ionicons.CalendarOutline),
                    contentDescription = stringResource(R.string.task_input_date),
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f),
                )
            }
            ZhixuIconButton(
                onClick = { showPrioritySheet = true },
                modifier = Modifier.size(36.dp),
            ) {
                Icon(
                    painter = painterResource(Ionicons.FlagOutline),
                    contentDescription = stringResource(R.string.task_input_priority),
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f),
                )
            }
            ZhixuIconButton(
                onClick = { showTagSheet = true },
                modifier = Modifier.size(36.dp),
            ) {
                Icon(
                    painter = painterResource(Ionicons.PricetagsOutline),
                    contentDescription = stringResource(R.string.task_input_tags),
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f),
                )
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            if (dueDate != null || timeRange != null) {
                AssistChip(
                    onClick = { showDateSheet = true },
                    label = { Text(buildDueChipLabel(dueDate, timeRange)) },
                    colors = AssistChipDefaults.assistChipColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                )
            }
            if (priority != null) {
                AssistChip(
                    onClick = { showPrioritySheet = true },
                    label = { Text("P${priority}") },
                    colors = AssistChipDefaults.assistChipColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                )
            }
            if (tags.isNotEmpty()) {
                AssistChip(
                    onClick = { showTagSheet = true },
                    label = { Text(tags.joinToString(" ")) },
                    colors = AssistChipDefaults.assistChipColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
                )
            }
        }
    }

    if (showDateSheet) {
        TaskDateSheet(
            initialDate = dueDate,
            initialRange = timeRange,
            onDismiss = { showDateSheet = false },
            onConfirm = { date, range ->
                dueDate = date
                timeRange = range
                showDateSheet = false
            },
            onClear = {
                dueDate = null
                timeRange = null
                showDateSheet = false
            },
        )
    }

    if (showPrioritySheet) {
        TaskPrioritySheet(
            initial = priority,
            onDismiss = { showPrioritySheet = false },
            onConfirm = {
                priority = it
                showPrioritySheet = false
            },
            onClear = {
                priority = null
                showPrioritySheet = false
            },
        )
    }

    if (showTagSheet) {
        TaskTagSheet(
            initialTags = tags,
            onDismiss = { showTagSheet = false },
            onConfirm = {
                tags = it
                showTagSheet = false
            },
            onClear = {
                tags = emptyList()
                showTagSheet = false
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TaskDateSheet(
    initialDate: LocalDate?,
    initialRange: TimeRange?,
    onDismiss: () -> Unit,
    onConfirm: (LocalDate?, TimeRange?) -> Unit,
    onClear: () -> Unit,
) {
    val state: SheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var tab by remember { mutableIntStateOf(0) }
    val pickerState = rememberDatePickerState(initialSelectedDateMillis = initialDate?.atStartOfDay(ZoneId.systemDefault())?.toInstant()?.toEpochMilli())
    var range by remember { mutableStateOf(initialRange) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = state,
    ) {
        TabRow(selectedTabIndex = tab) {
            Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text(stringResource(R.string.task_input_date)) })
            Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text(stringResource(R.string.task_input_time_range)) })
        }

        if (tab == 0) {
            DatePicker(state = pickerState)
        } else {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                for (r in TimeRange.entries) {
                    FilterChip(
                        selected = range == r,
                        onClick = { range = if (range == r) null else r },
                        label = { Text(r.label) },
                    )
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Button(
                modifier = Modifier.weight(1f),
                onClick = { onClear() },
            ) { Text(stringResource(R.string.task_input_clear)) }

            Button(
                modifier = Modifier.weight(1f),
                onClick = {
                    val millis = pickerState.selectedDateMillis
                    val date =
                        millis?.let {
                            Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDate()
                        }
                    onConfirm(date, range)
                },
            ) { Text(stringResource(R.string.task_input_confirm)) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TaskPrioritySheet(
    initial: Int?,
    onDismiss: () -> Unit,
    onConfirm: (Int?) -> Unit,
    onClear: () -> Unit,
) {
    val state: SheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var p by remember { mutableStateOf(initial) }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = state) {
        Text(stringResource(R.string.task_input_priority), modifier = Modifier.padding(16.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            for (i in 1..4) {
                FilterChip(selected = p == i, onClick = { p = if (p == i) null else i }, label = { Text("P$i") })
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Button(modifier = Modifier.weight(1f), onClick = onClear) { Text(stringResource(R.string.task_input_clear)) }
            Button(modifier = Modifier.weight(1f), onClick = { onConfirm(p) }) { Text(stringResource(R.string.task_input_confirm)) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TaskTagSheet(
    initialTags: List<String>,
    onDismiss: () -> Unit,
    onConfirm: (List<String>) -> Unit,
    onClear: () -> Unit,
) {
    val state: SheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var tagText by remember { mutableStateOf("") }
    var tags by remember { mutableStateOf(initialTags) }

    fun addTag() {
        val t = tagText.trim()
        if (t.isBlank()) return
        tags = (tags + t).distinct()
        tagText = ""
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = state) {
        Text(stringResource(R.string.task_input_tags), modifier = Modifier.padding(16.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ZhixuTextField(
                value = tagText,
                onValueChange = { tagText = it },
                modifier = Modifier.weight(1f),
                singleLine = true,
                placeholder = { Text("#work") },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { addTag() }),
            )
            Button(onClick = { addTag() }) { Text("+") }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            for (t in tags) {
                AssistChip(
                    onClick = { tags = tags.filterNot { it == t } },
                    label = { Text(t) },
                )
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Button(modifier = Modifier.weight(1f), onClick = onClear) { Text(stringResource(R.string.task_input_clear)) }
            Button(modifier = Modifier.weight(1f), onClick = { onConfirm(tags) }) { Text(stringResource(R.string.task_input_confirm)) }
        }
    }
}

private data class ParsedDraft(
    val dueDate: LocalDate? = null,
    val timeRange: TimeRange? = null,
    val tags: List<String> = emptyList(),
    val priority: Int? = null,
)

private fun extractTaskDraft(raw: String): Pair<String, ParsedDraft> {
    var text = raw.trim()
    if (text.isBlank()) return "" to ParsedDraft()

    var date: LocalDate? = null
    var range: TimeRange? = null
    val tags = ArrayList<String>()
    var priority: Int? = null

    val now = LocalDate.now()

    fun stripToken(token: String) {
        text = text.replace(token, " ").replace(Regex("""\s{2,}"""), " ").trim()
    }

    // tags: #xxx or @xxx
    Regex("""(^|\s)[#@]([\p{L}\p{N}_-]{1,20})""").findAll(text).forEach { m ->
        val t = m.groupValues[2]
        if (t.isNotBlank()) tags += t
    }
    text = text.replace(Regex("""[#@][\p{L}\p{N}_-]{1,20}"""), " ").replace(Regex("""\s{2,}"""), " ").trim()

    // priority: p1..p4 or P1..P4
    Regex("""\b[pP]([1-4])\b""").find(text)?.let { m ->
        priority = m.groupValues[1].toIntOrNull()
        stripToken(m.value)
    }

    // relative day
    when {
        text.contains("后天") -> {
            date = now.plusDays(2); stripToken("后天")
        }
        text.contains("明天") -> {
            date = now.plusDays(1); stripToken("明天")
        }
        text.contains("今天") || text.contains("今日") -> {
            date = now; stripToken("今天"); stripToken("今日")
        }
    }

    // time range words
    val rangeMap = listOf(
        "上午" to TimeRange.Morning,
        "早上" to TimeRange.Morning,
        "明早" to TimeRange.Morning,
        "下午" to TimeRange.Afternoon,
        "晚上" to TimeRange.Evening,
        "今晚" to TimeRange.Evening,
        "夜里" to TimeRange.Night,
        "深夜" to TimeRange.Night,
        "全天" to TimeRange.AllDay,
    )
    for ((token, r) in rangeMap) {
        if (text.contains(token)) {
            range = r
            stripToken(token)
            break
        }
    }

    // date patterns: 2025-12-26 / 2025/12/26
    Regex("""\b(20\d{2})[-/\.](\d{1,2})[-/\.](\d{1,2})\b""").find(text)?.let { m ->
        val y = m.groupValues[1].toInt()
        val mo = m.groupValues[2].toInt()
        val d = m.groupValues[3].toInt()
        runCatching { LocalDate.of(y, mo, d) }.onSuccess { date = it; stripToken(m.value) }
    }

    // date patterns: 12月26日
    Regex("""\b(\d{1,2})月(\d{1,2})日\b""").find(text)?.let { m ->
        val mo = m.groupValues[1].toInt()
        val d = m.groupValues[2].toInt()
        val y = now.year
        runCatching { LocalDate.of(y, mo, d) }.onSuccess { date = it; stripToken(m.value) }
    }

    // date patterns: 12/26 or 12-26
    Regex("""\b(\d{1,2})[-/](\d{1,2})\b""").find(text)?.let { m ->
        val mo = m.groupValues[1].toInt()
        val d = m.groupValues[2].toInt()
        val y = now.year
        runCatching { LocalDate.of(y, mo, d) }.onSuccess { date = it; stripToken(m.value) }
    }

    return text to ParsedDraft(dueDate = date, timeRange = range, tags = tags, priority = priority)
}

private fun buildDueChipLabel(date: LocalDate?, range: TimeRange?): String {
    if (date == null && range == null) return ""
    val parts = ArrayList<String>(2)
    if (date != null) parts += "${date.monthValue}/${date.dayOfMonth}"
    if (range != null && range != TimeRange.AllDay) parts += range.label
    return parts.joinToString(" ")
}

@Composable
private fun TaskRow(
    task: UiTask,
    dueLabel: String?,
    selectionMode: Boolean,
    selected: Boolean,
    onToggle: () -> Unit,
    onOpen: () -> Unit,
    onLongPress: () -> Unit,
    dimmed: Boolean,
) {
    val titleColor = if (dimmed) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
    val metaColor = MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(min = 40.dp)
                .background(if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.08f) else Color.Transparent)
                .combinedClickable(
                    onClick = onOpen,
                    onLongClick = onLongPress,
                )
                .padding(start = 0.dp, end = 12.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ZhixuIconButton(
            onClick = onToggle,
            modifier = Modifier.size(40.dp),
        ) {
            Icon(
                painter =
                    painterResource(
                        if (selectionMode) {
                            if (selected) Ionicons.CheckmarkCircle else Ionicons.SquareOutline
                        } else {
                            if (task.checked) Ionicons.CheckboxOutline else Ionicons.SquareOutline
                        },
                    ),
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint =
                    when {
                        selectionMode && selected -> MaterialTheme.colorScheme.primary
                        selectionMode -> metaColor
                        task.checked -> metaColor
                        else -> MaterialTheme.colorScheme.onSurface
                    },
            )
        }
        Spacer(modifier = Modifier.width(6.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = task.title,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = titleColor,
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                text = task.docName,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = metaColor.copy(alpha = 0.7f),
                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Light),
            )
        }
        if (!dueLabel.isNullOrBlank()) {
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = dueLabel,
                color = if (dimmed) metaColor else MaterialTheme.colorScheme.primary,
                maxLines = 1,
                style = MaterialTheme.typography.labelMedium,
            )
        }
    }
}

private fun formatDueLabel(dueEpochMillis: Long): String {
    val zone = ZoneId.systemDefault()
    val today = LocalDate.now(zone)
    val due = Instant.ofEpochMilli(dueEpochMillis).atZone(zone).toLocalDate()
    val days = ChronoUnit.DAYS.between(today, due).toInt()
    return when (days) {
        0 -> "今天"
        1 -> "明天"
        2 -> "后天"
        in 3..6 -> {
            val dow = due.dayOfWeek.value // 1..7
            "周" + "一二三四五六日"[dow - 1]
        }
        else -> {
            val zdt = Instant.ofEpochMilli(dueEpochMillis).atZone(zone)
            "${zdt.year}/${zdt.monthValue}/${zdt.dayOfMonth}"
        }
    }
}
