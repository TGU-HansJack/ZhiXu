package app.zhixu.ui.components.calendar

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.time.LocalDate

/**
 * 日历组件使用示例
 *
 * ## 快速开始
 *
 * ### 1. 基本使用 - 简单日期选择器
 * ```kotlin
 * var showDialog by remember { mutableStateOf(false) }
 * var selectedDate by remember { mutableStateOf<LocalDate?>(null) }
 *
 * Button(onClick = { showDialog = true }) {
 *     Text("选择日期")
 * }
 *
 * if (showDialog) {
 *     SimpleDatePickerDialog(
 *         initialDate = selectedDate ?: LocalDate.now(),
 *         onDateSelected = { date ->
 *             selectedDate = date
 *         },
 *         onDismiss = { showDialog = false }
 *     )
 * }
 * ```
 *
 * ### 2. 完整版 - 带时间段选择的日期选择器
 * ```kotlin
 * var showDialog by remember { mutableStateOf(false) }
 * var selectedDate by remember { mutableStateOf<LocalDate?>(null) }
 *
 * Button(onClick = { showDialog = true }) {
 *     Text("选择日期或时间段")
 * }
 *
 * if (showDialog) {
 *     DatePickerDialog(
 *         initialDate = selectedDate ?: LocalDate.now(),
 *         showTimePeriodTab = true,  // 显示"时间段"标签页
 *         onDateSelected = { date ->
 *             selectedDate = date
 *         },
 *         onDismiss = { showDialog = false }
 *     )
 * }
 * ```
 *
 * ### 3. 单独使用日历网格（嵌入到自定义布局）
 * ```kotlin
 * var currentMonth by remember { mutableStateOf(YearMonth.now()) }
 * var selectedDate by remember { mutableStateOf<LocalDate?>(null) }
 *
 * CalendarGrid(
 *     currentMonth = currentMonth,
 *     selectedDate = selectedDate,
 *     onMonthChange = { currentMonth = it },
 *     onDateSelect = { selectedDate = it }
 * )
 * ```
 *
 * ## 组件特性
 *
 * ### ✅ 已实现功能
 * - ✓ 固定6行网格布局（防止月份切换时跳动）
 * - ✓ 农历日期显示（支持1900-2100年）
 * - ✓ 节气显示（24节气）
 * - ✓ 法定节假日标记（元旦、劳动节、国庆节等）
 * - ✓ 农历节日标记（春节、端午、中秋等）
 * - ✓ 调休工作日标记（"休"/"班"角标）
 * - ✓ 今天高亮显示
 * - ✓ 月份快速导航（上月/下月/今天）
 * - ✓ 快捷日期选择（今天、明天、一周后等）
 * - ✓ 遵循 Material Design 3 设计规范
 * - ✓ 自动适配项目主题色
 *
 * ### 🎨 设计亮点
 * - 仿滴答清单风格的日历UI
 * - 圆形选中效果，视觉聚焦性强
 * - 分级文字颜色（主日期/次要信息/节日）
 * - 调休角标醒目但不抢眼
 *
 * ### 📦 组件结构
 * ```
 * calendar/
 * ├── LunarCalendar.kt       // 农历算法和数据模型
 * ├── Holiday.kt             // 节假日数据和工作日状态
 * ├── CalendarDayCell.kt     // 单个日期单元格UI
 * ├── CalendarGrid.kt        // 日历网格和月份导航
 * ├── DatePickerDialog.kt    // 日期选择器对话框
 * └── CalendarUsageExample.kt // 使用示例（本文件）
 * ```
 *
 * ### 🔧 自定义配置
 *
 * #### 添加自定义节假日
 * 编辑 `Holiday.kt` 中的 `HolidayProvider.FIXED_HOLIDAYS`:
 * ```kotlin
 * Holiday("自定义节日", HolidayType.TRADITIONAL, MonthDay.of(6, 1))
 * ```
 *
 * #### 配置调休安排
 * 编辑 `Holiday.kt` 中的 `HolidayProvider.getWorkDayStatus()` 方法：
 * ```kotlin
 * date.monthValue == 1 && date.dayOfMonth == 24 -> WorkDayStatus.WORK
 * date.monthValue == 1 && date.dayOfMonth in 1..3 -> WorkDayStatus.REST
 * ```
 *
 * #### 修改颜色
 * 组件自动使用 MaterialTheme 颜色，也可以在 `CalendarDayCell.kt` 中自定义：
 * ```kotlin
 * // 调休角标颜色
 * WorkDayStatus.REST -> Color(0xFF4CAF50)  // 绿色-休息
 * WorkDayStatus.WORK -> Color(0xFFF44336)  // 红色-上班
 * ```
 */

/**
 * 交互式示例 Composable
 * 可以在应用中临时添加此 Composable 来测试日历组件
 */
@Composable
fun CalendarUsageExampleScreen() {
    var showSimpleDialog by remember { mutableStateOf(false) }
    var showFullDialog by remember { mutableStateOf(false) }
    var selectedDate by remember { mutableStateOf<LocalDate?>(null) }

    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "日历组件测试",
                style = MaterialTheme.typography.headlineMedium
            )

            selectedDate?.let {
                Text(
                    text = "已选择: ${it.year}年${it.monthValue}月${it.dayOfMonth}日",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Button(onClick = { showSimpleDialog = true }) {
                Text("打开简单日期选择器")
            }

            Button(onClick = { showFullDialog = true }) {
                Text("打开完整日期选择器（带时间段）")
            }
        }
    }

    // 简单日期选择器
    if (showSimpleDialog) {
        SimpleDatePickerDialog(
            initialDate = selectedDate ?: LocalDate.now(),
            onDateSelected = { date ->
                selectedDate = date
            },
            onDismiss = { showSimpleDialog = false }
        )
    }

    // 完整日期选择器
    if (showFullDialog) {
        DatePickerDialog(
            initialDate = selectedDate ?: LocalDate.now(),
            showTimePeriodTab = true,
            onDateSelected = { date ->
                selectedDate = date
            },
            onDismiss = { showFullDialog = false }
        )
    }
}
