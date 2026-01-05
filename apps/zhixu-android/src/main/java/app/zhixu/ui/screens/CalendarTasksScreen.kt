package app.zhixu.ui.screens

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.zhixu.data.UiTask
import app.zhixu.data.VaultRepository
import app.zhixu.ui.components.calendar.CalendarGrid
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun CalendarTasksScreen(
    contentPadding: PaddingValues,
    vaultRootUri: Uri?,
    repository: VaultRepository,
    isActive: Boolean,
    onOpenDoc: (String, String?, Int?) -> Unit,
) {
    val dividerColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
    val timeFormatter = remember { DateTimeFormatter.ofPattern("HH:mm") }

    var selectedDate by remember { mutableStateOf(LocalDate.now()) }
    var currentMonth by remember(selectedDate) { mutableStateOf(YearMonth.from(selectedDate)) }

    var tasks by remember(vaultRootUri) { mutableStateOf<List<UiTask>>(emptyList()) }
    var indexReady by remember(vaultRootUri) { mutableStateOf<Boolean?>(null) }

    suspend fun refresh() {
        val root = vaultRootUri ?: return
        val (ready, dayTasks) =
            withContext(Dispatchers.IO) {
                val r = runCatching { repository.hasAnyIndexedDocs() }.getOrDefault(false)
                if (!r) {
                    false to emptyList()
                } else {
                    true to repository.getTasksDueOn(day = selectedDate, limit = 500)
                }
            }
        indexReady = ready
        tasks = dayTasks
    }

    LaunchedEffect(isActive, vaultRootUri, selectedDate) {
        if (!isActive) return@LaunchedEffect
        if (vaultRootUri == null) return@LaunchedEffect
        refresh()
    }

    LazyColumn(
        modifier =
            Modifier
                .padding(contentPadding)
                .background(MaterialTheme.colorScheme.background)
                .fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(0.dp),
    ) {
        item {
            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp)) {
                CalendarGrid(
                    currentMonth = currentMonth,
                    selectedDate = selectedDate,
                    onMonthChange = { currentMonth = it },
                    onDateSelect = { next ->
                        selectedDate = next
                        currentMonth = YearMonth.from(next)
                    },
                )
            }
            HorizontalDivider(color = dividerColor)
        }

        item {
            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
                Text(
                    text = "${selectedDate.monthValue}月${selectedDate.dayOfMonth}日",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(4.dp))
                val sub =
                    when (indexReady) {
                        false -> "索引未建立，任务列表为空"
                        null -> ""
                        true -> "共 ${tasks.size} 项"
                    }
                if (sub.isNotBlank()) {
                    Text(
                        text = sub,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        if (indexReady == false) {
            item {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(24.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "请先在“任务”页构建索引后再查看日历任务",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            return@LazyColumn
        }

        if (tasks.isEmpty()) {
            item {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(24.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(text = "当天没有到期任务", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            return@LazyColumn
        }

        items(
            items = tasks,
            key = { "${it.docUri}:${it.lineIndex}:${it.taskId}" },
        ) { task ->
            val dueLabel =
                task.dueEpochMillis?.let { dueMs ->
                    val dt = Instant.ofEpochMilli(dueMs).atZone(ZoneId.systemDefault()).toLocalDateTime()
                    timeFormatter.format(dt)
                }
            TaskSimpleRow(
                task = task,
                trailingLabel = dueLabel,
                onOpen = { onOpenDoc(task.docUri.toString(), null, task.lineIndex) },
            )
            HorizontalDivider(color = dividerColor)
        }
    }
}

@Composable
private fun TaskSimpleRow(
    task: UiTask,
    trailingLabel: String?,
    onOpen: () -> Unit,
) {
    Column(
        modifier =
            Modifier
                .clickable(onClick = onOpen)
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        Text(
            text = task.title.orEmpty(),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(2.dp))
        Column(modifier = Modifier.fillMaxWidth()) {
            val metaParts = buildList {
                add(task.docName.orEmpty())
                task.priority?.let { add("P$it") }
                if (!trailingLabel.isNullOrBlank()) add(trailingLabel)
            }.filter { it.isNotBlank() }
            Text(
                text = metaParts.joinToString(" · "),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
