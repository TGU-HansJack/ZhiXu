package app.zhixu.ui.components.calendar

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.zhixu.ui.Ionicons
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
        verticalArrangement = Arrangement.spacedBy(0.dp),
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
 * 月份导航栏 - 极简设计
 * 左侧月份，右侧导航箭头
 */
@Composable
private fun MonthNavigationBar(
    currentMonth: YearMonth,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        // 月份标题
        Text(
            text = "${currentMonth.monthValue}月",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface
        )

        // 导航箭头
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                painter = painterResource(Ionicons.ChevronBack),
                contentDescription = "上个月",
                modifier = Modifier
                    .size(20.dp)
                    .clickable { onPreviousMonth() },
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Icon(
                painter = painterResource(Ionicons.ChevronForward),
                contentDescription = "下个月",
                modifier = Modifier
                    .size(20.dp)
                    .clickable { onNextMonth() },
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * 星期标题行 - 从周一开始
 */
@Composable
private fun WeekdayHeader(modifier: Modifier = Modifier) {
    val weekdays = listOf("一", "二", "三", "四", "五", "六", "日")

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 0.dp),
        horizontalArrangement = Arrangement.spacedBy(0.dp)
    ) {
        weekdays.forEachIndexed { index, weekday ->
            Box(
                modifier = Modifier.weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = weekday,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Normal,
                    color = if (index >= 5) {
                        // 周六日用较浅颜色
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }
        }
    }
}

/**
 * 日期网格（固定6行）- 紧凑布局
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
        verticalArrangement = Arrangement.spacedBy(0.dp)
    ) {
        dayModels.forEach { week ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(0.dp)
            ) {
                week.forEach { dayModel ->
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .aspectRatio(1.4f)
                    ) {
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
 * 固定6行7列（42个单元格），周一为第一天
 */
private fun buildMonthDays(
    month: YearMonth,
    today: LocalDate
): List<List<DayModel?>> {
    val firstDayOfMonth = month.atDay(1)
    val lastDayOfMonth = month.atEndOfMonth()

    // 计算第一天是星期几（周一=0, 周二=1, ..., 周日=6）
    val firstDayOfWeek = (firstDayOfMonth.dayOfWeek.value - 1) % 7

    // 构建42个单元格（6行 × 7列）
    val cells = mutableListOf<DayModel?>()
    val totalCells = 42

    for (i in 0 until totalCells) {
        val dayOfMonth = i - firstDayOfWeek + 1
        if (dayOfMonth < 1 || dayOfMonth > lastDayOfMonth.dayOfMonth) {
            cells.add(null)
        } else {
            val date = month.atDay(dayOfMonth)
            cells.add(createDayModel(date, true, today))
        }
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
