package app.zhixu.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import app.zhixu.data.DailyContrib
import app.zhixu.data.UiDoc
import app.zhixu.data.UiTask
import app.zhixu.data.VaultRepository
import app.zhixu.ui.Ionicons
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth

@Composable
fun ContribCalendarDialog(
    repository: VaultRepository,
    onOpenDoc: (String, String?, Int?) -> Unit,
    onDismiss: () -> Unit,
    initialMonth: YearMonth = YearMonth.now(),
) {
    var currentMonth by remember { mutableStateOf(initialMonth) }
    var contribPerDay by remember { mutableStateOf<Map<LocalDate, DailyContrib>>(emptyMap()) }

    LaunchedEffect(currentMonth.year) {
        contribPerDay = runCatching { repository.getDailyContribForYear(currentMonth.year) }.getOrElse { emptyMap() }
    }

    val maxTotal =
        remember(contribPerDay) {
            contribPerDay.values.maxOfOrNull { it.total } ?: 0
        }

    fun levelFor(v: Int): Int {
        if (v <= 0) return 0
        if (maxTotal <= 1) return 1
        return (1 + ((v - 1) * 3 / (maxTotal - 1))).coerceIn(1, 4)
    }

    val dotColors =
        listOf(
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
            MaterialTheme.colorScheme.primary.copy(alpha = 0.25f),
            MaterialTheme.colorScheme.primary.copy(alpha = 0.45f),
            MaterialTheme.colorScheme.primary.copy(alpha = 0.70f),
            MaterialTheme.colorScheme.primary.copy(alpha = 0.92f),
        )

    val weekdays = listOf("日", "一", "二", "三", "四", "五", "六")

    val firstOfMonth = currentMonth.atDay(1)
    val leadingBlanks = firstOfMonth.dayOfWeek.value % 7 // Sunday -> 0
    val daysInMonth = currentMonth.lengthOfMonth()

    var detailsDate by remember { mutableStateOf<LocalDate?>(null) }
    var detailsLoading by remember { mutableStateOf(false) }
    var detailsDocs by remember { mutableStateOf<List<UiDoc>>(emptyList()) }
    var detailsTasks by remember { mutableStateOf<List<UiTask>>(emptyList()) }
    val scope = rememberCoroutineScope()

    fun openDetails(date: LocalDate) {
        detailsDate = date
        detailsLoading = true
        detailsDocs = emptyList()
        detailsTasks = emptyList()
        scope.launch {
            val docs = repository.getDocsCreatedOn(date)
            val tasks = repository.getTasksCreatedOn(date)
            detailsDocs = docs
            detailsTasks = tasks
            detailsLoading = false
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = ZhixuDialogDefaults.properties,
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth().padding(horizontal = ZhixuDialogDefaults.edgePadding, vertical = 24.dp),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
        ) {
            Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "${currentMonth.year} 年 ${currentMonth.monthValue} 月",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f),
                    )
                    ZhixuIconButton(onClick = { currentMonth = currentMonth.minusMonths(1) }) {
                        Icon(painter = painterResource(Ionicons.ChevronBack), contentDescription = "Prev month")
                    }
                    ZhixuIconButton(onClick = { currentMonth = YearMonth.now() }) {
                        Icon(painter = painterResource(Ionicons.RadioOff), contentDescription = "Today")
                    }
                    ZhixuIconButton(onClick = { currentMonth = currentMonth.plusMonths(1) }) {
                        Icon(painter = painterResource(Ionicons.ChevronForward), contentDescription = "Next month")
                    }
                }

                Spacer(Modifier.size(10.dp))

                Row(modifier = Modifier.fillMaxWidth()) {
                    for (w in weekdays) {
                        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                            Text(text = w, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }

                Spacer(Modifier.size(6.dp))

                val totalCells = 42
                val dayNumbers =
                    (0 until totalCells).map { idx ->
                        val day = idx - leadingBlanks + 1
                        day.takeIf { it in 1..daysInMonth }
                    }

                for (week in 0 until 6) {
                    Row(modifier = Modifier.fillMaxWidth()) {
                        for (dow in 0 until 7) {
                            val idx = week * 7 + dow
                            val dayNum = dayNumbers.getOrNull(idx)
                            val date = dayNum?.let { currentMonth.atDay(it) }
                            val total = date?.let { contribPerDay[it]?.total } ?: 0
                            val level = levelFor(total)
                            Box(
                                modifier =
                                    Modifier
                                        .weight(1f)
                                        .padding(vertical = 6.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .let { m ->
                                            if (date != null) m.clickable { openDetails(date) } else m
                                        },
                                contentAlignment = Alignment.Center,
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        text = dayNum?.toString().orEmpty(),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = if (dayNum == null) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.25f) else MaterialTheme.colorScheme.onSurface,
                                    )
                                    Spacer(Modifier.size(6.dp))
                                    Box(
                                        modifier =
                                            Modifier
                                                .size(6.dp)
                                                .background(color = dotColors[level], shape = CircleShape),
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(Modifier.size(8.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text("关闭") }
                }
            }
        }
    }

    val d = detailsDate
    if (d != null) {
        AlertDialog(
            modifier = ZhixuDialogDefaults.modifier(),
            onDismissRequest = { detailsDate = null },
            properties = ZhixuDialogDefaults.properties,
            confirmButton = { TextButton(onClick = { detailsDate = null }) { Text("关闭") } },
            title = { Text(text = "${d.year}-${d.monthValue.toString().padStart(2, '0')}-${d.dayOfMonth.toString().padStart(2, '0')}") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (detailsLoading) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.size(10.dp))
                            Text("加载中…", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        return@Column
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("文档", style = MaterialTheme.typography.labelLarge)
                        if (detailsDocs.isEmpty()) {
                            Text("无", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        } else {
                            for (doc in detailsDocs.take(12)) {
                                val label = doc.baseName.ifBlank { doc.name }
                                Text(
                                    text = "• $label",
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier =
                                        Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                onOpenDoc(doc.uri.toString(), null, null)
                                                detailsDate = null
                                                onDismiss()
                                            },
                                )
                            }
                            if (detailsDocs.size > 12) {
                                Text("… 还有 ${detailsDocs.size - 12} 条", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("任务", style = MaterialTheme.typography.labelLarge)
                        if (detailsTasks.isEmpty()) {
                            Text("无", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        } else {
                            for (task in detailsTasks.take(12)) {
                                Row(
                                    modifier =
                                        Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                onOpenDoc(task.docUri.toString(), null, task.lineIndex)
                                                detailsDate = null
                                                onDismiss()
                                            },
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    Box(
                                        modifier =
                                            Modifier
                                                .size(8.dp)
                                                .background(
                                                    color =
                                                        if (task.checked) MaterialTheme.colorScheme.primary.copy(alpha = 0.9f)
                                                        else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f),
                                                    shape = CircleShape,
                                                ),
                                    )
                                    Text(
                                        text = task.title,
                                        style = MaterialTheme.typography.bodySmall,
                                        modifier = Modifier.weight(1f),
                                    )
                                    Text(
                                        text = if (task.checked) "已完成" else "未完成",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                            if (detailsTasks.size > 12) {
                                Text("… 还有 ${detailsTasks.size - 12} 条", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            },
        )
    }
}
