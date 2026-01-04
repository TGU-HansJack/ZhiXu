package app.zhixu.ui.components.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import app.zhixu.ui.components.ZhixuDialogDefaults
import java.time.LocalDate
import java.time.YearMonth

/**
 * 日期选择器对话框
 * 仿滴答清单风格，支持日期和时间段两个Tab
 */
@Composable
fun DatePickerDialog(
    initialDate: LocalDate = LocalDate.now(),
    onDateSelected: (LocalDate) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    showTimePeriodTab: Boolean = true,
) {
    var selectedTabIndex by remember { mutableIntStateOf(0) }
    var currentMonth by remember { mutableStateOf(YearMonth.from(initialDate)) }
    var selectedDate by remember { mutableStateOf<LocalDate?>(initialDate) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = ZhixuDialogDefaults.properties,
    ) {
        Surface(
            modifier = modifier
                .fillMaxWidth()
                .padding(horizontal = ZhixuDialogDefaults.edgePadding, vertical = 24.dp),
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surface,
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                // 顶部 Tab 栏（如果需要）
                if (showTimePeriodTab) {
                    TabRow(
                        selectedTabIndex = selectedTabIndex,
                        containerColor = Color.Transparent,
                        indicator = { tabPositions ->
                            TabRowDefaults.SecondaryIndicator(
                                modifier = Modifier.tabIndicatorOffset(tabPositions[selectedTabIndex]),
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    ) {
                        Tab(
                            selected = selectedTabIndex == 0,
                            onClick = { selectedTabIndex = 0 },
                            text = { Text("日期") }
                        )
                        Tab(
                            selected = selectedTabIndex == 1,
                            onClick = { selectedTabIndex = 1 },
                            text = { Text("时间段") }
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                }

                // 内容区域
                when (selectedTabIndex) {
                    0 -> {
                        // 日期选择视图
                        Column(modifier = Modifier.padding(16.dp)) {
                            CalendarGrid(
                                currentMonth = currentMonth,
                                selectedDate = selectedDate,
                                onMonthChange = { currentMonth = it },
                                onDateSelect = { selectedDate = it }
                            )
                        }
                    }
                    1 -> {
                        // 时间段选择视图
                        TimePeriodView(
                            selectedDate = selectedDate,
                            onDateSelect = { selectedDate = it },
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                // 底部按钮
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("取消")
                    }
                    TextButton(
                        onClick = {
                            selectedDate?.let { onDateSelected(it) }
                            onDismiss()
                        },
                        enabled = selectedDate != null
                    ) {
                        Text("确定")
                    }
                }
            }
        }
    }
}

/**
 * 时间段选择视图（快捷日期选择）
 */
@Composable
private fun TimePeriodView(
    selectedDate: LocalDate?,
    onDateSelect: (LocalDate) -> Unit,
    modifier: Modifier = Modifier
) {
    val today = LocalDate.now()

    // 快捷日期选项
    val quickOptions = listOf(
        "今天" to today,
        "明天" to today.plusDays(1),
        "后天" to today.plusDays(2),
        "一周后" to today.plusWeeks(1),
        "一个月后" to today.plusMonths(1),
        "三个月后" to today.plusMonths(3),
    )

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = "快捷选择",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        quickOptions.forEach { (label, date) ->
            TimePeriodOption(
                label = label,
                date = date,
                isSelected = selectedDate == date,
                onClick = { onDateSelect(date) }
            )
        }
    }
}

/**
 * 时间段选项
 */
@Composable
private fun TimePeriodOption(
    label: String,
    date: LocalDate,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .background(
                color = if (isSelected) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    Color.Transparent
                }
            )
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                color = if (isSelected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurface
                }
            )
            Text(
                text = "${date.monthValue}/${date.dayOfMonth}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * 简化版日期选择器（仅日期，无Tab）
 */
@Composable
fun SimpleDatePickerDialog(
    initialDate: LocalDate = LocalDate.now(),
    onDateSelected: (LocalDate) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var currentMonth by remember { mutableStateOf(YearMonth.from(initialDate)) }
    var selectedDate by remember { mutableStateOf<LocalDate?>(initialDate) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = ZhixuDialogDefaults.properties,
    ) {
        Surface(
            modifier = modifier
                .fillMaxWidth()
                .padding(horizontal = ZhixuDialogDefaults.edgePadding, vertical = 24.dp),
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surface,
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                // 日历网格
                Column(modifier = Modifier.padding(16.dp)) {
                    CalendarGrid(
                        currentMonth = currentMonth,
                        selectedDate = selectedDate,
                        onMonthChange = { currentMonth = it },
                        onDateSelect = { selectedDate = it }
                    )
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                // 底部按钮
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("取消")
                    }
                    TextButton(
                        onClick = {
                            selectedDate?.let { onDateSelected(it) }
                            onDismiss()
                        },
                        enabled = selectedDate != null
                    ) {
                        Text("确定")
                    }
                }
            }
        }
    }
}
