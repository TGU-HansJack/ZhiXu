package app.zhixu.ui.screens

import android.net.Uri
import android.widget.NumberPicker
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.outlined.AccessTime
import androidx.compose.material.icons.outlined.Keyboard
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.HoverInteraction
import androidx.compose.foundation.interaction.Interaction
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ArrowForward
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.DateRange
import androidx.compose.material.icons.outlined.NightsStay
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SheetState
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.style.TextAlign
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
import androidx.compose.ui.platform.LocalConfiguration
import app.zhixu.R
import app.zhixu.ui.Ionicons
import app.zhixu.ui.components.ZhixuCompactDragHandle
import app.zhixu.ui.components.RefreshStatusBanner
import app.zhixu.ui.components.calendar.CalendarGrid
import app.zhixu.data.UiTask
import app.zhixu.data.VaultIndexRepository
import app.zhixu.data.VaultRepository
import app.zhixu.sync.VaultAutoSync
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.time.DayOfWeek
import kotlin.math.max
import androidx.compose.foundation.shape.RoundedCornerShape

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
    var dueTime by remember { mutableStateOf<LocalTime?>(null) }
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
        val finalTime =
            when (finalRange) {
                TimeRange.AllDay -> null
                null -> dueTime
                else -> dueTime ?: finalRange.defaultTime
            }
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
        dueTime = null
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
                .padding(start = 16.dp, end = 16.dp, top = 6.dp),
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
            initialTime = dueTime,
            onDismiss = { showDateSheet = false },
            onConfirm = { date, range, time ->
                dueDate = date
                timeRange = range
                dueTime = time
                showDateSheet = false
            },
            onClear = {
                dueDate = null
                timeRange = null
                dueTime = null
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
    initialTime: LocalTime?,
    onDismiss: () -> Unit,
    onConfirm: (LocalDate?, TimeRange?, LocalTime?) -> Unit,
    onClear: () -> Unit,
) {
    val state: SheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var tab by remember { mutableIntStateOf(0) }
    val fallbackDate = remember { initialDate ?: LocalDate.now() }
    var currentMonth by remember { mutableStateOf(YearMonth.from(fallbackDate)) }
    var selectedDate by remember { mutableStateOf<LocalDate?>(initialDate) }
    var range by remember { mutableStateOf(initialRange) }
    var dueTime by remember { mutableStateOf(initialTime) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = state,
        dragHandle = { ZhixuCompactDragHandle() },
    ) {
        val context = LocalContext.current
        val screenHeight = LocalConfiguration.current.screenHeightDp.dp
        val contentMinHeight = screenHeight * 0.7f + 24.dp
        fun confirmSelection() {
            onConfirm(selectedDate, range, dueTime)
        }

        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(contentMinHeight),
        ) {
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ZhixuIconButton(onClick = onDismiss, modifier = Modifier.size(44.dp)) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = stringResource(R.string.task_input_clear),
                        modifier = Modifier.size(26.dp),
                    )
                }

                Row(
                    modifier = Modifier.padding(start = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    UnderlineTabLabel(
                        text = stringResource(R.string.task_input_date),
                        selected = tab == 0,
                        onClick = { tab = 0 },
                    )
                    UnderlineTabLabel(
                        text = stringResource(R.string.task_input_time_range),
                        selected = tab == 1,
                        onClick = { tab = 1 },
                    )
                }

                Spacer(modifier = Modifier.weight(1f))

                ZhixuIconButton(onClick = { confirmSelection() }, modifier = Modifier.size(44.dp)) {
                    Icon(
                        imageVector = Icons.Rounded.Check,
                        contentDescription = stringResource(R.string.task_input_confirm),
                        modifier = Modifier.size(26.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }

            Box(modifier = Modifier.weight(1f, fill = true)) {
                if (tab == 0) {
                    Column(
                        modifier =
                            Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState()),
                    ) {
                        TaskDateTab(
                            currentMonth = currentMonth,
                            selectedDate = selectedDate,
                            range = range,
                            dueTime = dueTime,
                            onMonthChange = { currentMonth = it },
                            onDateSelect = {
                                selectedDate = it
                                currentMonth = YearMonth.from(it)
                            },
                            onPickToday = {
                                range = null
                                dueTime = null
                                val date = LocalDate.now()
                                selectedDate = date
                                currentMonth = YearMonth.from(date)
                            },
                            onPickTomorrow = {
                                range = null
                                dueTime = null
                                val date = LocalDate.now().plusDays(1)
                                selectedDate = date
                                currentMonth = YearMonth.from(date)
                            },
                            onPickNextMonday = {
                                range = null
                                dueTime = null
                                val today = LocalDate.now()
                                val delta =
                                    ((DayOfWeek.MONDAY.value - today.dayOfWeek.value + 7) % 7).let { if (it == 0) 7 else it }
                                val date = today.plusDays(delta.toLong())
                                selectedDate = date
                                currentMonth = YearMonth.from(date)
                            },
                            onPickTonight = {
                                range = TimeRange.Evening
                                dueTime = TimeRange.Evening.defaultTime
                                val date = LocalDate.now()
                                selectedDate = date
                                currentMonth = YearMonth.from(date)
                            },
                            onTimeClick = { time ->
                                range = null
                                dueTime = time
                            },
                            onReminderClick = {
                                android.widget.Toast.makeText(context, "提醒：敬请期待", android.widget.Toast.LENGTH_SHORT).show()
                            },
                            onRepeatClick = {
                                android.widget.Toast.makeText(context, "重复：敬请期待", android.widget.Toast.LENGTH_SHORT).show()
                            },
                            onClear = onClear,
                        )
                    }
                } else {
                    Column(
                        modifier =
                            Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState()),
                    ) {
                        TaskTimeRangeTab(
                            selectedDate = selectedDate,
                            range = range,
                            onChangeRange = { next ->
                                range = next
                                dueTime = next?.defaultTime
                            },
                            onGoToDate = { tab = 0 },
                            onReminderClick = {
                                android.widget.Toast.makeText(context, "提醒：敬请期待", android.widget.Toast.LENGTH_SHORT).show()
                            },
                            onRepeatClick = {
                                android.widget.Toast.makeText(context, "重复：敬请期待", android.widget.Toast.LENGTH_SHORT).show()
                            },
                            onClear = onClear,
                        )
                    }
                }
            }

            // Clear button is rendered inside tab content (under repeat) to avoid overlay/layer issues.
        }
    }
}

@Composable
private fun TaskDateTab(
    currentMonth: YearMonth,
    selectedDate: LocalDate?,
    range: TimeRange?,
    dueTime: LocalTime?,
    onMonthChange: (YearMonth) -> Unit,
    onDateSelect: (LocalDate) -> Unit,
    onPickToday: () -> Unit,
    onPickTomorrow: () -> Unit,
    onPickNextMonday: () -> Unit,
    onPickTonight: () -> Unit,
    onTimeClick: (LocalTime) -> Unit,
    onReminderClick: () -> Unit,
    onRepeatClick: () -> Unit,
    onClear: () -> Unit,
) {
    var showTimeDialog by remember { mutableStateOf(false) }
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        TaskQuickPick(label = "今天", icon = painterResource(Ionicons.TodayOutline), onClick = onPickToday)
        TaskQuickPick(label = "明天", icon = painterResource(Ionicons.SunnyOutline), onClick = onPickTomorrow)
        TaskQuickPick(label = "下周一", icon = painterResource(R.drawable.ic_lucide_calendar_1), onClick = onPickNextMonday)
        TaskQuickPick(label = "今天晚上", icon = painterResource(R.drawable.ic_lucide_moon_star), onClick = onPickTonight)
    }

    CalendarGrid(
        currentMonth = currentMonth,
        selectedDate = selectedDate,
        onMonthChange = onMonthChange,
        onDateSelect = onDateSelect,
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
    )

    fun formatTime(time: LocalTime): String =
        "${time.hour.toString().padStart(2, '0')}:${time.minute.toString().padStart(2, '0')}"

    val rangeText =
        when {
            dueTime != null -> formatTime(dueTime)
            range == null -> "无"
            range == TimeRange.AllDay -> "全天"
            else -> range.label
        }
    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.18f),
    ) {
        Column {
            TaskSheetRow(
                title = "时间",
                value = rangeText,
                onClick = { showTimeDialog = true },
            )
            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            TaskSheetRow(title = "提醒", value = "无", onClick = onReminderClick)
            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            TaskSheetRow(title = "重复", value = "无", onClick = onRepeatClick)
        }
    }

    if (selectedDate != null) {
        OutlinedButton(
            onClick = onClear,
            shape = RoundedCornerShape(12.dp),
            colors =
                ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.error,
                ),
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp),
        ) {
            Text(text = stringResource(R.string.task_input_clear), style = MaterialTheme.typography.bodyMedium)
        }
    }

    Spacer(modifier = Modifier.height(12.dp))

    if (showTimeDialog) {
        val fallback =
            remember(dueTime, range) {
                val base = dueTime ?: range?.defaultTime ?: LocalTime.now()
                val snappedMinute = ((base.minute + 2) / 5) * 5 % 60
                base.withMinute(snappedMinute)
            }
        TaskTimePickerDialog(
            initial = fallback,
            onDismiss = { showTimeDialog = false },
            onConfirm = { time ->
                onTimeClick(time)
                showTimeDialog = false
            },
        )
    }
}

@Composable
private fun TaskQuickPick(
    label: String,
    icon: androidx.compose.ui.graphics.painter.Painter,
    onClick: () -> Unit,
) {
    Column(
        modifier =
            Modifier
                .width(78.dp)
                .clickable(onClick = onClick)
                .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Surface(
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
            modifier = Modifier.size(50.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    painter = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(26.dp),
                )
            }
        }
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun TaskTimeRangeTab(
    selectedDate: LocalDate?,
    range: TimeRange?,
    onChangeRange: (TimeRange?) -> Unit,
    onGoToDate: () -> Unit,
    onReminderClick: () -> Unit,
    onRepeatClick: () -> Unit,
    onClear: () -> Unit,
) {
    val date = selectedDate
    val dateText =
        if (date == null) {
            "无"
        } else {
            "${date.monthValue}月${date.dayOfMonth}日，${weekdayLabel(date.dayOfWeek)}"
        }
    val rangeText = when (range) {
        null -> "无"
        TimeRange.AllDay -> "全天"
        else -> range.label
    }

    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Surface(
            modifier =
                Modifier
                    .weight(1f)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onGoToDate,
                    ),
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f),
        ) {
            Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(text = stringResource(R.string.task_input_date), style = MaterialTheme.typography.bodyMedium)
                Text(text = dateText, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodyMedium)
            }
        }
        Surface(
            modifier = Modifier.weight(1f),
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f),
        ) {
            Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(text = stringResource(R.string.task_input_time_range), style = MaterialTheme.typography.bodyMedium)
                Text(text = rangeText, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodyMedium)
                if (range != null && range != TimeRange.AllDay) {
                    Text(
                        text = "持续时间：1小时",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Light),
                    )
                }
            }
        }
    }

    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.18f),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text = "全天", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            Switch(
                checked = range == TimeRange.AllDay,
                onCheckedChange = { checked ->
                    onChangeRange(if (checked) TimeRange.AllDay else null)
                },
            )
        }
    }

    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        for (r in TimeRange.entries) {
            if (r == TimeRange.AllDay) continue
            FilterChip(
                selected = range == r,
                onClick = { onChangeRange(if (range == r) null else r) },
                label = { Text(r.label, style = MaterialTheme.typography.bodyMedium) },
            )
        }
    }

    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.18f),
    ) {
        Column {
            TaskSheetRow(title = "提醒", value = "无", onClick = onReminderClick)
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f), modifier = Modifier.padding(horizontal = 16.dp))
            TaskSheetRow(title = "重复", value = "无", onClick = onRepeatClick)
        }
    }

    if (selectedDate != null) {
        OutlinedButton(
            onClick = onClear,
            shape = RoundedCornerShape(12.dp),
            colors =
                ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.error,
                ),
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp),
        ) {
            Text(text = stringResource(R.string.task_input_clear), style = MaterialTheme.typography.bodyMedium)
        }
        Spacer(modifier = Modifier.height(12.dp))
    }

    Spacer(modifier = Modifier.height(12.dp))
}

private enum class TimePickerStyle { Dial, Wheel }

private enum class DialPhase { Hour, Minute }

@Composable
private fun TaskTimePickerDialog(
    initial: LocalTime,
    onDismiss: () -> Unit,
    onConfirm: (LocalTime) -> Unit,
) {
    var style by remember { mutableStateOf(TimePickerStyle.Dial) }
    var phase by remember { mutableStateOf(DialPhase.Hour) }
    var hour by remember { mutableIntStateOf(initial.hour) }
    var minute by remember { mutableIntStateOf((initial.minute / 5) * 5) }

    fun formattedHour(): String = hour.toString().padStart(2, '0')
    fun formattedMinute(): String = minute.toString().padStart(2, '0')

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
        ) {
            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 16.dp)) {
                Text(
                    text = "时间",
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface,
                )

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val activeColor = MaterialTheme.colorScheme.primary
                    val inactiveColor = activeColor.copy(alpha = 0.55f)

                    Text(
                        text = formattedHour(),
                        modifier =
                            Modifier.clickable(enabled = style == TimePickerStyle.Dial) {
                                phase = DialPhase.Hour
                            },
                        color = if (phase == DialPhase.Hour && style == TimePickerStyle.Dial) activeColor else inactiveColor,
                        style = MaterialTheme.typography.displayMedium.copy(fontWeight = FontWeight.Bold),
                    )
                    Text(
                        text = " : ",
                        color = inactiveColor,
                        style = MaterialTheme.typography.displayMedium.copy(fontWeight = FontWeight.Bold),
                    )
                    Text(
                        text = formattedMinute(),
                        modifier =
                            Modifier.clickable(enabled = style == TimePickerStyle.Dial) {
                                phase = DialPhase.Minute
                            },
                        color = if (phase == DialPhase.Minute && style == TimePickerStyle.Dial) activeColor else inactiveColor,
                        style = MaterialTheme.typography.displayMedium.copy(fontWeight = FontWeight.Bold),
                    )
                }

                Spacer(modifier = Modifier.height(18.dp))

                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center,
                ) {
                    when (style) {
                        TimePickerStyle.Dial -> {
                            AnimatedContent(
                                targetState = phase,
                                label = "dial-phase",
                                transitionSpec = {
                                    val exit = fadeOut(tween(160)) + scaleOut(tween(160))
                                    val enter =
                                        fadeIn(tween(220, delayMillis = 40)) +
                                            scaleIn(
                                                spring(
                                                    dampingRatio = Spring.DampingRatioMediumBouncy,
                                                    stiffness = Spring.StiffnessLow,
                                                ),
                                            )
                                    enter togetherWith exit
                                },
                            ) { target ->
                                when (target) {
                                    DialPhase.Hour ->
                                        DialHourPicker(
                                            selected = hour,
                                            onSelect = { next ->
                                                hour = next.coerceIn(0, 23)
                                                phase = DialPhase.Minute
                                            },
                                            size = 300.dp,
                                        )

                                    DialPhase.Minute ->
                                        DialMinutePicker(
                                            selected = minute,
                                            onSelect = { next ->
                                                minute = (next / 5).coerceIn(0, 11) * 5
                                            },
                                            size = 300.dp,
                                        )
                                }
                            }
                        }

                        TimePickerStyle.Wheel -> {
                            WheelTimePicker(
                                hour = hour,
                                minute = minute,
                                onHourChange = { hour = it.coerceIn(0, 23) },
                                onMinuteChange = { minute = (it / 5).coerceIn(0, 11) * 5 },
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ZhixuIconButton(
                        onClick = {
                            style = if (style == TimePickerStyle.Dial) TimePickerStyle.Wheel else TimePickerStyle.Dial
                            if (style == TimePickerStyle.Dial) phase = DialPhase.Hour
                        },
                        modifier = Modifier.size(44.dp),
                    ) {
                        Icon(
                            imageVector = if (style == TimePickerStyle.Dial) Icons.Outlined.Keyboard else Icons.Outlined.AccessTime,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                            modifier = Modifier.size(22.dp),
                        )
                    }

                    Spacer(modifier = Modifier.weight(1f))

                    TextButton(onClick = onDismiss) {
                        Text(text = "取消", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    TextButton(
                        onClick = { onConfirm(LocalTime.of(hour, minute)) },
                    ) {
                        Text(text = "确定", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
    }
}

@Composable
private fun DialHourPicker(
    selected: Int,
    onSelect: (Int) -> Unit,
    size: Dp,
) {
    val density = LocalDensity.current
    BoxWithConstraints(modifier = Modifier.size(size)) {
        val pxSize = with(density) { size.toPx() }
        val center = Offset(pxSize / 2f, pxSize / 2f)
        val outerRadius = pxSize * 0.40f
        val innerRadius = pxSize * 0.27f
        val itemSizePx = with(density) { 48.dp.toPx() }

        val bgColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.20f)
        val primary = MaterialTheme.colorScheme.primary

        fun angleFor(index: Int): Double = (Math.PI / 6.0) * index - Math.PI / 2.0

        val innerRing = remember(pxSize) {
            (0 until 12).map { i ->
                val value = if (i == 0) 12 else i
                val angle = angleFor(i)
                val x = center.x + (innerRadius * kotlin.math.cos(angle)).toFloat()
                val y = center.y + (innerRadius * kotlin.math.sin(angle)).toFloat()
                value to Offset(x, y)
            }
        }
        val outerRing = remember(pxSize) {
            (0 until 12).map { i ->
                val value = if (i == 0) 0 else 12 + i
                val angle = angleFor(i)
                val x = center.x + (outerRadius * kotlin.math.cos(angle)).toFloat()
                val y = center.y + (outerRadius * kotlin.math.sin(angle)).toFloat()
                value to Offset(x, y)
            }
        }

        val selectedOffset =
            (innerRing + outerRing)
                .firstOrNull { it.first == selected }
                ?.second

        Canvas(modifier = Modifier.matchParentSize()) {
            drawCircle(color = bgColor, radius = minOf(this.size.width, this.size.height) / 2f)
            if (selectedOffset != null) {
                drawLine(
                    color = primary,
                    start = center,
                    end = selectedOffset,
                    strokeWidth = 2.dp.toPx(),
                    cap = StrokeCap.Round,
                )
            }
            drawCircle(color = primary, radius = 4.dp.toPx(), center = center)
        }

        fun hourLabel(value: Int): String = if (value == 0) "00" else value.toString()

        (innerRing + outerRing).forEach { (value, position) ->
            val isSelected = value == selected
            Box(
                modifier =
                    Modifier
                        .offset {
                            val x = (position.x - itemSizePx / 2f).toInt()
                            val y = (position.y - itemSizePx / 2f).toInt()
                            IntOffset(x, y)
                        }
                        .size(with(density) { itemSizePx.toDp() })
                        .clickable { onSelect(value) },
                contentAlignment = Alignment.Center,
            ) {
                if (isSelected) {
                    Box(modifier = Modifier.size(52.dp).background(primary, CircleShape))
                }
                Text(
                    text = hourLabel(value),
                    color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                    style =
                        MaterialTheme.typography.titleLarge.copy(
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                        ),
                )
            }
        }
    }
}

@Composable
private fun DialMinutePicker(
    selected: Int,
    onSelect: (Int) -> Unit,
    size: Dp,
) {
    val density = LocalDensity.current
    BoxWithConstraints(modifier = Modifier.size(size)) {
        val pxSize = with(density) { size.toPx() }
        val center = Offset(pxSize / 2f, pxSize / 2f)
        val radius = pxSize * 0.40f
        val itemSizePx = with(density) { 48.dp.toPx() }

        val bgColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.20f)
        val primary = MaterialTheme.colorScheme.primary

        fun angleFor(index: Int): Double = (Math.PI / 6.0) * index - Math.PI / 2.0

        val ring = remember(pxSize) {
            (0 until 12).map { i ->
                val value = i * 5
                val angle = angleFor(i)
                val x = center.x + (radius * kotlin.math.cos(angle)).toFloat()
                val y = center.y + (radius * kotlin.math.sin(angle)).toFloat()
                value to Offset(x, y)
            }
        }

        val selectedOffset = ring.firstOrNull { it.first == selected }?.second

        Canvas(modifier = Modifier.matchParentSize()) {
            drawCircle(color = bgColor, radius = minOf(this.size.width, this.size.height) / 2f)
            if (selectedOffset != null) {
                drawLine(
                    color = primary,
                    start = center,
                    end = selectedOffset,
                    strokeWidth = 2.dp.toPx(),
                    cap = StrokeCap.Round,
                )
            }
            drawCircle(color = primary, radius = 4.dp.toPx(), center = center)
        }

        ring.forEach { (value, position) ->
            val isSelected = value == selected
            Box(
                modifier =
                    Modifier
                        .offset {
                            val x = (position.x - itemSizePx / 2f).toInt()
                            val y = (position.y - itemSizePx / 2f).toInt()
                            IntOffset(x, y)
                        }
                        .size(with(density) { itemSizePx.toDp() })
                        .clickable { onSelect(value) },
                contentAlignment = Alignment.Center,
            ) {
                if (isSelected) {
                    Box(modifier = Modifier.size(52.dp).background(primary, CircleShape))
                }
                Text(
                    text = value.toString().padStart(2, '0'),
                    color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                    style =
                        MaterialTheme.typography.titleLarge.copy(
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                        ),
                )
            }
        }
    }
}

@Composable
private fun WheelTimePicker(
    hour: Int,
    minute: Int,
    onHourChange: (Int) -> Unit,
    onMinuteChange: (Int) -> Unit,
) {
    val minuteValues = remember { Array(12) { i -> (i * 5).toString().padStart(2, '0') } }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AndroidView(
            factory = { context ->
                NumberPicker(context).apply {
                    minValue = 0
                    maxValue = 23
                    wrapSelectorWheel = true
                    setFormatter { value -> value.toString().padStart(2, '0') }
                    setOnValueChangedListener { _, _, newVal -> onHourChange(newVal) }
                }
            },
            update = { picker ->
                if (picker.value != hour) picker.value = hour
            },
            modifier = Modifier.width(120.dp),
        )
        Text(
            text = ":",
            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 6.dp),
        )
        AndroidView(
            factory = { context ->
                NumberPicker(context).apply {
                    minValue = 0
                    maxValue = 11
                    displayedValues = minuteValues
                    wrapSelectorWheel = true
                    setOnValueChangedListener { _, _, newVal -> onMinuteChange(newVal * 5) }
                }
            },
            update = { picker ->
                val idx = (minute / 5).coerceIn(0, 11)
                if (picker.value != idx) picker.value = idx
            },
            modifier = Modifier.width(120.dp),
        )
    }
}

@Composable
private fun TaskSheetRow(
    title: String,
    value: String,
    onClick: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onClick,
                )
                .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = title, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Text(text = value, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
        Spacer(modifier = Modifier.width(6.dp))
        Icon(
            imageVector = Icons.Outlined.ArrowForward,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            modifier = Modifier.size(18.dp),
        )
    }
}

@Composable
private fun UnderlineTabLabel(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsStateCompat()
    val isHovered by interactionSource.collectIsHoveredAsStateCompat()
    val showUnderline = selected || isPressed || isHovered
    val underlineColor = MaterialTheme.colorScheme.primary
    var layout by remember { mutableStateOf<TextLayoutResult?>(null) }

    Box(
        modifier =
            modifier
                .height(44.dp)
                .hoverable(interactionSource)
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    onClick = onClick,
                )
                .padding(horizontal = 2.dp)
                .drawBehind {
                    if (!showUnderline) return@drawBehind
                    val strokeWidth = 3.dp.toPx()
                    val lineY =
                        layout?.let {
                            val textOffsetY = (size.height - it.size.height.toFloat()) / 2f
                            textOffsetY + it.getLineBottom(0) + 2.dp.toPx()
                        }
                            ?.coerceAtMost(size.height - strokeWidth / 2f)
                            ?: (size.height - strokeWidth / 2f)
                    drawLine(
                        color = underlineColor,
                        start = Offset(0f, lineY),
                        end = Offset(size.width, lineY),
                        strokeWidth = strokeWidth,
                        cap = StrokeCap.Round,
                    )
                },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            onTextLayout = { layout = it },
        )
    }
}

@Composable
private fun MutableInteractionSource.collectIsPressedAsStateCompat(): State<Boolean> {
    val isPressed = remember { mutableStateOf(false) }
    LaunchedEffect(this) {
        interactions.collect { interaction ->
            when (interaction) {
                is PressInteraction.Press -> isPressed.value = true
                is PressInteraction.Release, is PressInteraction.Cancel -> isPressed.value = false
            }
        }
    }
    return isPressed
}

@Composable
private fun MutableInteractionSource.collectIsHoveredAsStateCompat(): State<Boolean> {
    val isHovered = remember { mutableStateOf(false) }
    LaunchedEffect(this) {
        interactions.collect { interaction: Interaction ->
            when (interaction) {
                is HoverInteraction.Enter -> isHovered.value = true
                is HoverInteraction.Exit -> isHovered.value = false
            }
        }
    }
    return isHovered
}

private fun weekdayLabel(day: DayOfWeek): String =
    when (day) {
        DayOfWeek.MONDAY -> "周一"
        DayOfWeek.TUESDAY -> "周二"
        DayOfWeek.WEDNESDAY -> "周三"
        DayOfWeek.THURSDAY -> "周四"
        DayOfWeek.FRIDAY -> "周五"
        DayOfWeek.SATURDAY -> "周六"
        DayOfWeek.SUNDAY -> "周日"
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
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = state,
        dragHandle = { ZhixuCompactDragHandle() },
    ) {
        Text(stringResource(R.string.task_input_priority), modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp))
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

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = state,
        dragHandle = { ZhixuCompactDragHandle() },
    ) {
        Text(stringResource(R.string.task_input_tags), modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp))
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
