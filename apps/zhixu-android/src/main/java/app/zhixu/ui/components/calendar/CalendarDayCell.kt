package app.zhixu.ui.components.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
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
import java.time.LocalDate

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
    val today = androidx.compose.runtime.remember { LocalDate.now() }
    val isPastSelected = isSelected && day?.date?.isBefore(today) == true
    val selectedBackgroundColor = if (isPastSelected) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
    val selectedTextColor = if (isPastSelected) MaterialTheme.colorScheme.onError else MaterialTheme.colorScheme.onPrimary

    Box(
        modifier = modifier
            .aspectRatio(1f)
            .then(
                if (day != null && onClick != null) {
                    Modifier.clickable { onClick() }
                } else {
                    Modifier
                }
            )
            .padding(1.dp),
    ) {
        if (day != null) {
            val lunarMonthFirstDayHighlight =
                !isSelected &&
                    day.textColorType == DayTextColorType.NORMAL &&
                    day.lunarDate.day == 1

            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(4.dp)
                        .clip(CircleShape)
                        .background(
                            color = when {
                                isSelected -> selectedBackgroundColor
                                day.isToday -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                                else -> Color.Transparent
                            },
                            shape = CircleShape,
                        )
                        .padding(vertical = 3.dp),
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(1.dp),
                ) {
                    Text(
                        text = day.date.dayOfMonth.toString(),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = if (day.isToday || isSelected) FontWeight.Bold else FontWeight.Normal,
                        color = when {
                            isSelected -> selectedTextColor
                            !day.isCurrentMonth -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.25f)
                            day.isToday -> MaterialTheme.colorScheme.primary
                            else -> MaterialTheme.colorScheme.onSurface
                        },
                    )

                    if (day.displayText.isNotBlank()) {
                        Text(
                            text = day.displayText,
                            fontSize = 9.sp,
                            color = when {
                                isSelected -> selectedTextColor.copy(alpha = 0.85f)
                                !day.isCurrentMonth -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f)
                                day.textColorType == DayTextColorType.LEGAL_HOLIDAY -> MaterialTheme.colorScheme.error
                                day.textColorType == DayTextColorType.LUNAR_HOLIDAY -> MaterialTheme.colorScheme.tertiary
                                day.textColorType == DayTextColorType.SOLAR_TERM -> MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
                                lunarMonthFirstDayHighlight -> Color(0xFFFFC107)
                                else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                            },
                        )
                    }
                }
            }

            if (day.workDayStatus != WorkDayStatus.NONE) {
                WorkDayBadge(
                    workDayStatus = day.workDayStatus,
                    isSelected = isSelected,
                    selectedContainerColor = selectedTextColor.copy(alpha = 0.9f),
                    selectedContentColor = selectedBackgroundColor,
                    modifier =
                        Modifier
                            .align(Alignment.TopEnd)
                            .offset(x = (-1).dp, y = 1.dp),
                )
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
    selectedContainerColor: Color,
    selectedContentColor: Color,
    modifier: Modifier = Modifier
) {
    val (text, bgColor) = when (workDayStatus) {
        WorkDayStatus.REST -> "休" to Color(0xFF4CAF50) // 绿色
        WorkDayStatus.WORK -> "班" to Color(0xFFF44336) // 红色
        else -> return
    }

    Box(
        modifier = modifier
            .size(11.dp)
            .background(
                color = if (isSelected) selectedContainerColor else bgColor,
                shape = CircleShape
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            fontSize = 6.sp,
            fontWeight = FontWeight.Bold,
            color = if (isSelected) selectedContentColor else Color.White
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
