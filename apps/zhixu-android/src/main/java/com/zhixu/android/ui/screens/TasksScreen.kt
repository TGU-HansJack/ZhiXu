package com.zhixu.android.ui.screens

import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ArrowForward
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.Label
import androidx.compose.material.icons.outlined.Today
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SheetState
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.zhixu.android.R
import com.zhixu.android.data.UiTask
import com.zhixu.android.data.VaultIndexRepository
import com.zhixu.android.data.VaultRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import kotlin.math.max

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TasksScreen(
    contentPadding: PaddingValues,
    vaultRootUri: Uri?,
    repository: VaultRepository,
    isActive: Boolean,
    onOpenDoc: (String, String?, Int?) -> Unit,
) {
    val scope = rememberCoroutineScope()
    var tasks by
        remember(vaultRootUri) {
            mutableStateOf(TasksScreenCache.get(vaultRootUri)?.tasks ?: emptyList())
        }
    var completed by
        remember(vaultRootUri) {
            mutableStateOf(TasksScreenCache.get(vaultRootUri)?.completed ?: emptyList())
        }
    var showCompleted by remember { mutableStateOf(false) }
    var addJob by remember { mutableStateOf<Job?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }
    val dueFormatter = remember { DateTimeFormatter.ofPattern("MM-dd HH:mm") }
    var lastRefreshAtMs by remember { mutableStateOf(0L) }

    suspend fun refresh(force: Boolean) {
        val root = vaultRootUri ?: return
        val now = android.os.SystemClock.uptimeMillis()
        if (!force && now - lastRefreshAtMs in 0..500) return

        val (nextTasks, nextCompleted) =
            withContext(Dispatchers.IO) {
                runCatching { repository.ensureIndexBuilt(root) }
                val t = repository.getAllTasks(status = VaultIndexRepository.TaskStatusFilter.Undone)
                val c = repository.getRecentCompletedTasks(limit = 50)
                t to c
            }

        tasks = nextTasks
        completed = nextCompleted
        TasksScreenCache.put(root, TasksScreenCache.Entry(tasks = nextTasks, completed = nextCompleted))
        lastRefreshAtMs = android.os.SystemClock.uptimeMillis()
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
                refresh(force = true)
            }
    }

    LaunchedEffect(vaultRootUri, isActive) {
        if (!isActive) return@LaunchedEffect
        refresh(force = false)
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
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(0.dp),
            ) {
                if (vaultRootUri == null) {
                    item { Text(stringResource(R.string.settings_vault_not_selected), modifier = Modifier.padding(16.dp)) }
                    return@LazyColumn
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
                items(tasks, key = { "${it.docUri}:${it.lineIndex}:${it.taskId}" }) { task ->
                    TaskRow(
                        task = task,
                        dueLabel = task.dueEpochMillis?.let(::formatDueLabel),
                        onToggle = {
                            scope.launch {
                                repository.toggleTask(task.docUri, task.lineIndex)
                                refresh(force = true)
                            }
                        },
                        onOpen = { onOpenDoc(task.docUri.toString(), null, task.lineIndex) },
                        dimmed = false,
                    )
                    HorizontalDivider(thickness = 0.5.dp)
                }

                item {
                    val count = completed.size
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showCompleted = !showCompleted }
                            .padding(vertical = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Icon(
                            imageVector = if (showCompleted) Icons.Outlined.ExpandMore else Icons.Outlined.ChevronRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(stringResource(R.string.tasks_completed))
                        Text(text = count.toString(), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }

                if (showCompleted) {
                    items(completed, key = { "c:${it.docUri}:${it.lineIndex}:${it.taskId}" }) { task ->
                        TaskRow(
                            task = task,
                            dueLabel = task.dueEpochMillis?.let { dueFormatter.format(Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault())) },
                            onToggle = {
                                scope.launch {
                                    repository.toggleTask(task.docUri, task.lineIndex)
                                    refresh(force = true)
                                }
                            },
                            onOpen = { onOpenDoc(task.docUri.toString(), null, task.lineIndex) },
                            dimmed = true,
                        )
                        HorizontalDivider(thickness = 0.5.dp)
                    }
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

internal suspend fun warmTasksCache(
    rootUri: Uri,
    repository: VaultRepository,
) {
    val key = rootUri.toString()
    if (key.isBlank()) return
    if (TasksScreenCache.get(rootUri) != null) return

    val (tasks, completed) =
        withContext(Dispatchers.IO) {
            runCatching { repository.ensureIndexBuilt(rootUri) }
            val t = repository.getAllTasks(status = VaultIndexRepository.TaskStatusFilter.Undone)
            val c = repository.getRecentCompletedTasks(limit = 50)
            t to c
        }

    TasksScreenCache.put(rootUri, TasksScreenCache.Entry(tasks = tasks, completed = completed))
}

private data class TaskDraft(
    val title: String,
    val dueDate: LocalDate?,
    val dueTime: LocalTime?,
    val timeRange: TimeRange?,
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
private fun TaskComposer(
    onSubmit: (TaskDraft) -> Unit,
    modifier: Modifier = Modifier,
) {
    var text by remember { mutableStateOf("") }
    var expanded by remember { mutableStateOf(false) }
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
                timeRange = finalRange,
                tags = finalTags,
                priority = finalPriority,
            ),
        )

        text = ""
        dueDate = null
        timeRange = null
        tags = emptyList()
        priority = null
        expanded = false
    }

    OutlinedTextField(
        value = text,
        onValueChange = {
            text = it
            if (!expanded) expanded = true
        },
        modifier = modifier.heightIn(min = 56.dp),
        singleLine = true,
        placeholder = { Text(stringResource(R.string.task_input_hint)) },
        colors = TextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
            disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
        ),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { submit() }),
        trailingIcon = {
            IconButton(
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

    AnimatedVisibility(visible = expanded) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                IconButton(onClick = { showDateSheet = true }) {
                    Icon(
                        imageVector = Icons.Outlined.Today,
                        contentDescription = stringResource(R.string.task_input_date),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f),
                    )
                }
                IconButton(onClick = { showPrioritySheet = true }) {
                    Icon(
                        imageVector = Icons.Outlined.Flag,
                        contentDescription = stringResource(R.string.task_input_priority),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f),
                    )
                }
                IconButton(onClick = { showTagSheet = true }) {
                    Icon(
                        imageVector = Icons.Outlined.Label,
                        contentDescription = stringResource(R.string.task_input_tags),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f),
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
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
            OutlinedTextField(
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
    onToggle: () -> Unit,
    onOpen: () -> Unit,
    dimmed: Boolean,
) {
    val titleColor = if (dimmed) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
    val metaColor = MaterialTheme.colorScheme.onSurfaceVariant
    ListItem(
        modifier = Modifier.clickable(onClick = onOpen),
        headlineContent = { Text(task.title, maxLines = 1, overflow = TextOverflow.Ellipsis, color = titleColor) },
        supportingContent = {
            Text(task.docName, maxLines = 1, overflow = TextOverflow.Ellipsis, color = metaColor)
        },
        leadingContent = {
            IconButton(onClick = onToggle) {
                Icon(
                    imageVector = if (task.checked) Icons.Outlined.CheckCircle else Icons.Outlined.RadioButtonUnchecked,
                    contentDescription = null,
                    tint = if (task.checked) metaColor else MaterialTheme.colorScheme.onSurface,
                )
            }
        },
        trailingContent = {
            if (!dueLabel.isNullOrBlank()) {
                Text(
                    text = dueLabel,
                    color = if (dimmed) metaColor else MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                )
            }
        },
    )
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
