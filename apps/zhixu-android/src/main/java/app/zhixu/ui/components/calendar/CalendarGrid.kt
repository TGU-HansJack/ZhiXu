package app.zhixu.ui.components.calendar

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import app.zhixu.ui.Ionicons
import app.zhixu.ui.components.ZhixuIconButton
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth

/**
 * 日历网格组件
 * 包含月份导航和6x7日期网格
 */
@Composable
fun CalendarGrid(
    currentMonth: YearMonth,
    selectedDate: LocalDate?,
    onMonthChange: (YearMonth) -> Unit,
    onDateSelect: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
) {
    val today = remember { LocalDate.now() }
    val scope = rememberCoroutineScope()
    val pagerState = rememberPagerState(initialPage = 1, pageCount = { 3 })

    LaunchedEffect(pagerState.settledPage) {
        when (pagerState.settledPage) {
            0 -> {
                onMonthChange(currentMonth.minusMonths(1))
                pagerState.scrollToPage(1)
            }
            2 -> {
                onMonthChange(currentMonth.plusMonths(1))
                pagerState.scrollToPage(1)
            }
        }
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        // 月份导航栏
        MonthNavigationBar(
            currentMonth = currentMonth,
            onPreviousMonth = {
                scope.launch { pagerState.scrollToPage(1) }
                onMonthChange(currentMonth.minusMonths(1))
            },
            onNextMonth = {
                scope.launch { pagerState.scrollToPage(1) }
                onMonthChange(currentMonth.plusMonths(1))
            },
            onToday = {
                scope.launch { pagerState.scrollToPage(1) }
                onMonthChange(YearMonth.from(today))
            }
        )

        // 星期标题行
        WeekdayHeader()

        HorizontalPager(
            state = pagerState,
            beyondViewportPageCount = 1,
        ) { page ->
            val month = currentMonth.plusMonths((page - 1).toLong())
            val dayModels = remember(month) { buildMonthDays(month, today) }
            DayGrid(
                dayModels = dayModels,
                selectedDate = selectedDate,
                onDateSelect = onDateSelect,
            )
        }
    }
}

/**
 * 月份导航栏
 */
@Composable
private fun MonthNavigationBar(
    currentMonth: YearMonth,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onToday: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = "${currentMonth.year} 年 ${currentMonth.monthValue} 月",
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.weight(1f)
        )

        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            ZhixuIconButton(onClick = onPreviousMonth, modifier = Modifier.size(36.dp)) {
                Icon(
                    painter = painterResource(Ionicons.ChevronBack),
                    contentDescription = "上个月"
                )
            }
            ZhixuIconButton(onClick = onToday, modifier = Modifier.size(36.dp)) {
                Icon(
                    painter = painterResource(Ionicons.RadioOff),
                    contentDescription = "今天"
                )
            }
            ZhixuIconButton(onClick = onNextMonth, modifier = Modifier.size(36.dp)) {
                Icon(
                    painter = painterResource(Ionicons.ChevronForward),
                    contentDescription = "下个月"
                )
            }
        }
    }
}

/**
 * 星期标题行
 */
@Composable
private fun WeekdayHeader(modifier: Modifier = Modifier) {
    val weekdays = listOf("日", "一", "二", "三", "四", "五", "六")

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        weekdays.forEach { weekday ->
            Box(
                modifier = Modifier.weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = weekday,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/**
 * 日期网格（固定6行）
 */
@Composable
private fun DayGrid(
    dayModels: List<List<DayModel?>>,
    selectedDate: LocalDate?,
    onDateSelect: (LocalDate) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        dayModels.forEach { week ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                week.forEach { dayModel ->
                    Box(modifier = Modifier.weight(1f)) {
                        CalendarDayCell(
                            day = dayModel,
                            isSelected = dayModel?.date == selectedDate,
                            onClick = if (dayModel != null) {
                                { onDateSelect(dayModel.date) }
                            } else null
                        )
                    }
                }
            }
        }
    }
}

/**
 * 构建月份的日期数据
 * 固定6行7列（42个单元格）
 */
private fun buildMonthDays(
    month: YearMonth,
    today: LocalDate
): List<List<DayModel?>> {
    val firstDayOfMonth = month.atDay(1)
    val lastDayOfMonth = month.atEndOfMonth()

    // 计算第一天是星期几（0=周日, 1=周一, ..., 6=周六）
    val firstDayOfWeek = firstDayOfMonth.dayOfWeek.value % 7

    // 构建42个单元格（6行 × 7列）
    val cells = mutableListOf<DayModel?>()
    val totalCells = 42

    for (i in 0 until totalCells) {
        val dayOfMonth = i - firstDayOfWeek + 1
        val date = when {
            dayOfMonth < 1 -> {
                // 上个月的日期
                val prevMonth = month.minusMonths(1)
                prevMonth.atEndOfMonth().minusDays((-dayOfMonth).toLong())
            }
            dayOfMonth > lastDayOfMonth.dayOfMonth -> {
                // 下个月的日期
                val nextMonth = month.plusMonths(1)
                nextMonth.atDay(dayOfMonth - lastDayOfMonth.dayOfMonth)
            }
            else -> {
                // 当前月的日期
                month.atDay(dayOfMonth)
            }
        }

        val isCurrentMonth = date.year == month.year && date.monthValue == month.monthValue
        val dayModel = if (isCurrentMonth || i < firstDayOfWeek || dayOfMonth > lastDayOfMonth.dayOfMonth) {
            createDayModel(date, isCurrentMonth, today)
        } else {
            null
        }

        cells.add(dayModel)
    }

    // 分成6行
    return cells.chunked(7)
}

/**
 * 创建单个日期的数据模型
 */
private fun createDayModel(
    date: LocalDate,
    isCurrentMonth: Boolean,
    today: LocalDate
): DayModel {
    val lunarDate = LunarCalendarCalculator.solarToLunar(date)
    val solarTerm = LunarCalendarCalculator.getSolarTerm(date)
    val holiday = HolidayProvider.getHoliday(date)
    val lunarHoliday = HolidayProvider.getLunarHoliday(lunarDate)
    val workDayStatus = HolidayProvider.getWorkDayStatus(date, date.year)

    return DayModel(
        date = date,
        lunarDate = lunarDate,
        solarTerm = solarTerm,
        holiday = holiday,
        lunarHoliday = lunarHoliday,
        workDayStatus = workDayStatus,
        isCurrentMonth = isCurrentMonth,
        isToday = date == today
    )
}
