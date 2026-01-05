package app.zhixu.ui.components.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.HoverInteraction
import androidx.compose.foundation.interaction.Interaction
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.flow.collect
import java.time.LocalDate

/**
 * 日历单元格组件 - 极简设计
 * 仿滴答清单/iOS原生日历风格
 */
@Composable
fun CalendarDayCell(
    day: DayModel?,
    isSelected: Boolean,
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val today = remember { LocalDate.now() }
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsStateCompat()
    val isHovered by interactionSource.collectIsHoveredAsStateCompat()

    val selectedCircleColor =
        if (!isSelected || day == null) {
            null
        } else if (!day.date.isBefore(today)) {
            Color(0xFF1E88E5)
        } else {
            Color(0xFFE53935)
        }

    Box(
        modifier = modifier
            .fillMaxSize()
            .then(
                if (day != null && onClick != null) {
                    Modifier
                        .hoverable(interactionSource)
                        .clickable(
                            interactionSource = interactionSource,
                            indication = null,
                        ) { onClick() }
                } else {
                    Modifier
                }
            ),
        contentAlignment = Alignment.Center
    ) {
        if (day != null) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(0.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                val showCircleShadow = onClick != null && (isPressed || isHovered)
                val circleElevation = if (showCircleShadow) 6.dp else 0.dp

                val lunarText = day.getLunarDisplayText()

                Box(
                    modifier = Modifier
                        .size(30.dp)
                        .shadow(circleElevation, CircleShape, clip = false)
                        .clip(CircleShape)
                        .background(
                            color = if (isSelected) {
                                selectedCircleColor ?: Color.Transparent
                            } else {
                                Color.Transparent
                            },
                            shape = CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Text(
                            text = day.date.dayOfMonth.toString(),
                            fontSize = 13.sp,
                            lineHeight = 13.sp,
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                            color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurface,
                            textAlign = TextAlign.Center
                        )
                        if (lunarText.isNotBlank()) {
                            Text(
                                text = lunarText,
                                fontSize = 8.sp,
                                lineHeight = 8.sp,
                                fontWeight = FontWeight.Normal,
                                maxLines = 1,
                                overflow = TextOverflow.Clip,
                                color = if (isSelected) {
                                    Color.White.copy(alpha = 0.75f)
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f)
                                },
                                textAlign = TextAlign.Center,
                            )
                        }
                    }
                }
            }

            // 休/班 标记 - 右上角
            if (day.workDayStatus != WorkDayStatus.NONE && day.isCurrentMonth) {
                WorkDayBadge(
                    workDayStatus = day.workDayStatus,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .offset(x = (-2).dp, y = 2.dp)
                )
            }
        }
    }
}

/**
 * 调休标记组件 - 小圆形徽章
 */
@Composable
private fun WorkDayBadge(
    workDayStatus: WorkDayStatus,
    modifier: Modifier = Modifier
) {
    val (text, bgColor) = when (workDayStatus) {
        WorkDayStatus.REST -> "休" to Color(0xFF4CAF50) // 绿色
        WorkDayStatus.WORK -> "班" to Color(0xFFE53935) // 红色
        else -> return
    }

    Box(
        modifier = modifier
            .size(11.dp)
            .background(color = bgColor, shape = CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            fontSize = 6.sp,
            lineHeight = 6.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(bottom = 0.5.dp),
        )
    }
}

/**
 * 预览用的示例组件
 */
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

@Composable
fun CalendarDayCellPreview() {
    MaterialTheme {
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
