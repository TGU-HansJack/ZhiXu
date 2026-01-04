package app.zhixu.ui.components.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 日历单元格组件
 * 仿滴答清单风格
 */
@Composable
fun CalendarDayCell(
    day: DayModel?,
    isSelected: Boolean,
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .aspectRatio(1f)
            .clip(CircleShape)
            .then(
                if (day != null && onClick != null) {
                    Modifier.clickable { onClick() }
                } else {
                    Modifier
                }
            )
            .background(
                color = when {
                    isSelected -> MaterialTheme.colorScheme.primary
                    day?.isToday == true -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                    else -> Color.Transparent
                },
                shape = CircleShape
            )
            .padding(vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        if (day != null) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                // 日期数字
                Text(
                    text = day.date.dayOfMonth.toString(),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (day.isToday || isSelected) FontWeight.Bold else FontWeight.Normal,
                    color = when {
                        isSelected -> MaterialTheme.colorScheme.onPrimary
                        !day.isCurrentMonth -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.25f)
                        day.isToday -> MaterialTheme.colorScheme.primary
                        else -> MaterialTheme.colorScheme.onSurface
                    }
                )

                // 农历/节日/节气
                Text(
                    text = day.displayText,
                    fontSize = 10.sp,
                    color = when {
                        isSelected -> MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.85f)
                        !day.isCurrentMonth -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f)
                        day.textColorType == DayTextColorType.LEGAL_HOLIDAY -> MaterialTheme.colorScheme.error
                        day.textColorType == DayTextColorType.LUNAR_HOLIDAY -> MaterialTheme.colorScheme.tertiary
                        day.textColorType == DayTextColorType.SOLAR_TERM -> MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
                        else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    }
                )

                // 调休标记（休/班）
                if (day.workDayStatus != WorkDayStatus.NONE) {
                    WorkDayBadge(
                        workDayStatus = day.workDayStatus,
                        isSelected = isSelected
                    )
                }
            }
        }
    }
}

/**
 * 调休标记组件
 */
@Composable
private fun WorkDayBadge(
    workDayStatus: WorkDayStatus,
    isSelected: Boolean,
    modifier: Modifier = Modifier
) {
    val (text, bgColor) = when (workDayStatus) {
        WorkDayStatus.REST -> "休" to Color(0xFF4CAF50) // 绿色
        WorkDayStatus.WORK -> "班" to Color(0xFFF44336) // 红色
        else -> return
    }

    Box(
        modifier = modifier
            .size(14.dp)
            .background(
                color = if (isSelected) MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.9f) else bgColor,
                shape = CircleShape
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            fontSize = 8.sp,
            fontWeight = FontWeight.Bold,
            color = if (isSelected) MaterialTheme.colorScheme.primary else Color.White
        )
    }
}

/**
 * 预览用的示例组件
 */
@Composable
fun CalendarDayCellPreview() {
    MaterialTheme {
        Surface {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // 普通日期
                CalendarDayCell(
                    day = DayModel(
                        date = java.time.LocalDate.of(2026, 1, 4),
                        lunarDate = LunarDate(2025, 12, 6),
                        isCurrentMonth = true,
                        isToday = false
                    ),
                    isSelected = false,
                    modifier = Modifier.size(56.dp)
                )

                // 选中状态
                CalendarDayCell(
                    day = DayModel(
                        date = java.time.LocalDate.of(2026, 1, 4),
                        lunarDate = LunarDate(2025, 12, 6),
                        isCurrentMonth = true,
                        isToday = false
                    ),
                    isSelected = true,
                    modifier = Modifier.size(56.dp)
                )

                // 今天
                CalendarDayCell(
                    day = DayModel(
                        date = java.time.LocalDate.of(2026, 1, 4),
                        lunarDate = LunarDate(2025, 12, 6),
                        isCurrentMonth = true,
                        isToday = true
                    ),
                    isSelected = false,
                    modifier = Modifier.size(56.dp)
                )

                // 带节日
                CalendarDayCell(
                    day = DayModel(
                        date = java.time.LocalDate.of(2026, 1, 1),
                        lunarDate = LunarDate(2025, 12, 3),
                        holiday = Holiday(
                            "元旦",
                            HolidayType.LEGAL_HOLIDAY,
                            java.time.MonthDay.of(1, 1)
                        ),
                        isCurrentMonth = true,
                        isToday = false,
                        workDayStatus = WorkDayStatus.REST
                    ),
                    isSelected = false,
                    modifier = Modifier.size(56.dp)
                )

                // 调休工作日
                CalendarDayCell(
                    day = DayModel(
                        date = java.time.LocalDate.of(2026, 1, 24),
                        lunarDate = LunarDate(2025, 12, 26),
                        isCurrentMonth = true,
                        isToday = false,
                        workDayStatus = WorkDayStatus.WORK
                    ),
                    isSelected = false,
                    modifier = Modifier.size(56.dp)
                )
            }
        }
    }
}
