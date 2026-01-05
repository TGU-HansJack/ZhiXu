package app.zhixu.ui.screens

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.view.WindowManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.SkipNext
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import app.zhixu.pomodoro.PomodoroMode
import app.zhixu.pomodoro.PomodoroPreferences
import app.zhixu.pomodoro.PomodoroService
import app.zhixu.pomodoro.PomodoroStatTime
import app.zhixu.pomodoro.PomodoroStatsRepository
import app.zhixu.pomodoro.PomodoroTimerSnapshot
import app.zhixu.pomodoro.PomodoroTimerStateStore
import java.time.LocalDate
import java.time.YearMonth
import kotlinx.coroutines.delay

@Composable
fun PomodoroScreen(
    contentPadding: PaddingValues,
) {
    val context = LocalContext.current
    val prefs = remember(context) { PomodoroPreferences(context.applicationContext) }
    val timerStore = remember(context) { PomodoroTimerStateStore(context.applicationContext) }
    val statsRepo = remember(context) { PomodoroStatsRepository(context.applicationContext) }
    val settings by prefs.settings.collectAsState(initial = app.zhixu.pomodoro.PomodoroSettings())
    val snapshotRaw by timerStore.snapshot.collectAsState(initial = PomodoroTimerSnapshot())
    val snapshot = snapshotRaw.copy(totalFocusCount = settings.longBreakEvery.coerceAtLeast(1))

    val dividerColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
    val haptic = LocalHapticFeedback.current

    var showBrandTitle by rememberSaveable { mutableStateOf(true) }
    LaunchedEffect(Unit) {
        delay(1500)
        showBrandTitle = false
    }

    var nowTickMs by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(snapshot.isRunning, snapshot.alarmRinging) {
        while (snapshot.isRunning || snapshot.alarmRinging) {
            nowTickMs = System.currentTimeMillis()
            delay(250)
        }
    }

    val view = LocalView.current
    LaunchedEffect(settings.aodEnabled, snapshot.isRunning, snapshot.alarmRinging) {
        view.keepScreenOn = settings.aodEnabled && (snapshot.isRunning || snapshot.alarmRinging)
    }

    DisposableEffect(settings.aodEnabled, settings.secureAod, snapshot.isRunning, snapshot.alarmRinging) {
        val activity = context as? android.app.Activity
        val window = activity?.window
        val enabled = settings.aodEnabled && settings.secureAod && (snapshot.isRunning || snapshot.alarmRinging)
        if (window != null) {
            if (enabled) window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
            else window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
        onDispose {
            if (window != null && enabled) window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
    }

    val requestNotifications =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { _ -> }

    LazyColumn(
        modifier =
            Modifier
                .padding(contentPadding)
                .background(MaterialTheme.colorScheme.background)
                .fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(0.dp),
    ) {
        item {
            val remaining = computeRemainingSeconds(snapshot, nowTickMs)
            val total = durationSeconds(snapshot.mode, settings).coerceAtLeast(1)
            PomodoroTimerCard(
                mode = snapshot.mode,
                remainingSeconds = remaining,
                totalSeconds = total,
                completedFocusInSet = ((snapshot.currentFocusCount - 1).coerceAtLeast(0)),
                totalFocusCount = snapshot.totalFocusCount,
                isRunning = snapshot.isRunning,
                canUndoReset = snapshot.lastResetSnapshot != null,
                singleProgressBar = settings.singleProgressBar,
                showBrandTitle = showBrandTitle,
                onSetMode = { m -> PomodoroService.startSetMode(context, m) },
                onToggle = {
                    ensureNotificationPermission(context, requestNotifications)
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    PomodoroService.startAction(context, PomodoroService.Actions.TOGGLE)
                },
                onReset = { PomodoroService.startAction(context, PomodoroService.Actions.RESET) },
                onUndoReset = { PomodoroService.startAction(context, PomodoroService.Actions.UNDO_RESET) },
                onSkip = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    PomodoroService.startAction(context, PomodoroService.Actions.SKIP)
                },
                modifier = Modifier.fillMaxWidth().padding(16.dp),
            )
            HorizontalDivider(color = dividerColor)
        }

        item {
            PomodoroStatsSection(
                statsRepo = statsRepo,
                modifier = Modifier.fillMaxWidth().padding(16.dp),
            )
        }
    }

    if (snapshot.alarmRinging) {
        AlertDialog(
            onDismissRequest = { PomodoroService.startAction(context, PomodoroService.Actions.STOP_ALARM) },
            title = { Text("计时完成") },
            text = { Text("点击停止铃声后进入下一阶段") },
            confirmButton = {
                TextButton(onClick = { PomodoroService.startAction(context, PomodoroService.Actions.STOP_ALARM) }) {
                    Text("停止")
                }
            },
        )
    }
}

@Composable
private fun PomodoroTimerCard(
    mode: PomodoroMode,
    remainingSeconds: Int,
    totalSeconds: Int,
    completedFocusInSet: Int,
    totalFocusCount: Int,
    isRunning: Boolean,
    singleProgressBar: Boolean,
    showBrandTitle: Boolean,
    onSetMode: (PomodoroMode) -> Unit,
    onReset: () -> Unit,
    onToggle: () -> Unit,
    onSkip: () -> Unit,
    canUndoReset: Boolean,
    onUndoReset: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val focus = mode == PomodoroMode.Focus
    val containerColor by androidx.compose.animation.animateColorAsState(
        targetValue =
            if (focus) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.tertiaryContainer,
        animationSpec = androidx.compose.animation.core.tween(220),
    )
    val onContainerColor by androidx.compose.animation.animateColorAsState(
        targetValue =
            if (focus) MaterialTheme.colorScheme.onPrimaryContainer
            else MaterialTheme.colorScheme.onTertiaryContainer,
        animationSpec = androidx.compose.animation.core.tween(220),
    )

    val titleText by remember(mode, showBrandTitle) {
        derivedStateOf {
            if (showBrandTitle) {
                "Tomato"
            } else {
                when (mode) {
                    PomodoroMode.Focus -> "专注"
                    PomodoroMode.ShortBreak -> "短休息"
                    PomodoroMode.LongBreak -> "长休息"
                }
            }
        }
    }

    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.large,
        color = containerColor,
        tonalElevation = 1.dp,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column {
                    Text(
                        text = titleText,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = onContainerColor,
                    )
                    Text(
                        text =
                            if (mode == PomodoroMode.Focus) {
                                "本轮：$completedFocusInSet / $totalFocusCount"
                            } else {
                                "下一步：专注"
                            },
                        style = MaterialTheme.typography.bodySmall,
                        color = onContainerColor.copy(alpha = 0.8f),
                    )
                }
                val progress =
                    if (totalSeconds <= 0) {
                        0f
                    } else {
                        val sessionProgress = (totalSeconds - remainingSeconds).toFloat() / totalSeconds.toFloat()
                        if (singleProgressBar || mode != PomodoroMode.Focus) {
                            sessionProgress
                        } else {
                            ((completedFocusInSet.toFloat() + sessionProgress) / totalFocusCount.toFloat()).coerceIn(0f, 1f)
                        }
                    }
                Box(contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(
                        progress = { progress.coerceIn(0f, 1f) },
                        modifier = Modifier.size(56.dp),
                        color = onContainerColor,
                        trackColor = onContainerColor.copy(alpha = 0.18f),
                    )
                    Text(text = "${(progress * 100).toInt()}%", style = MaterialTheme.typography.labelSmall, color = onContainerColor)
                }
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = mode == PomodoroMode.Focus,
                    onClick = { onSetMode(PomodoroMode.Focus) },
                    label = { Text("专注") },
                    enabled = !isRunning,
                )
                FilterChip(
                    selected = mode == PomodoroMode.ShortBreak,
                    onClick = { onSetMode(PomodoroMode.ShortBreak) },
                    label = { Text("短休") },
                    enabled = !isRunning,
                )
                FilterChip(
                    selected = mode == PomodoroMode.LongBreak,
                    onClick = { onSetMode(PomodoroMode.LongBreak) },
                    label = { Text("长休") },
                    enabled = !isRunning,
                )
            }

            if (!singleProgressBar && mode == PomodoroMode.Focus) {
                PomodoroCycleBar(
                    completed = completedFocusInSet.coerceIn(0, totalFocusCount.coerceAtLeast(1)),
                    total = totalFocusCount.coerceAtLeast(1),
                    tint = onContainerColor,
                    trackTint = onContainerColor.copy(alpha = 0.18f),
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                AssistChip(
                    onClick = {},
                    enabled = false,
                    label = { Text(if (mode == PomodoroMode.Focus) "单一进度条" else "休息阶段") },
                )
            }

            Text(
                text = formatSeconds(remainingSeconds),
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.SemiBold,
                color = onContainerColor,
            )

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    modifier = Modifier.weight(1f),
                    onClick = onToggle,
                    colors =
                        ButtonDefaults.buttonColors(
                            containerColor = onContainerColor.copy(alpha = 0.12f),
                            contentColor = onContainerColor,
                        ),
                ) {
                    Icon(
                        imageVector = if (isRunning) Icons.Outlined.Pause else Icons.Outlined.PlayArrow,
                        contentDescription = null,
                    )
                    Spacer(Modifier.size(8.dp))
                    Text(text = if (isRunning) "暂停" else "开始")
                }
                OutlinedButton(
                    modifier = Modifier.weight(1f),
                    onClick = onReset,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = onContainerColor),
                ) {
                    Icon(imageVector = Icons.Outlined.Stop, contentDescription = null)
                    Spacer(Modifier.size(8.dp))
                    Text(text = "重置")
                }
                OutlinedButton(
                    onClick = onSkip,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = onContainerColor),
                ) {
                    Icon(imageVector = Icons.Outlined.SkipNext, contentDescription = null)
                }
            }
            if (canUndoReset) {
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = onUndoReset, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.outlinedButtonColors(contentColor = onContainerColor)) {
                    Text("撤销重置")
                }
            }
        }
    }
}

@Composable
private fun PomodoroCycleBar(
    completed: Int,
    total: Int,
    tint: androidx.compose.ui.graphics.Color,
    trackTint: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier,
) {
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        for (i in 0 until total) {
            Box(
                modifier =
                    Modifier
                        .weight(1f)
                        .height(10.dp)
                        .background(
                            if (i < completed) tint else trackTint,
                            MaterialTheme.shapes.extraSmall,
                        ),
            )
        }
    }
}

@Composable
private fun PomodoroStatsSection(
    statsRepo: PomodoroStatsRepository,
    modifier: Modifier = Modifier,
) {
    val days = remember { (0..6).map { LocalDate.now().minusDays((6 - it).toLong()) } }
    val weekStats by statsRepo.getLastNDaysStats(days = 7).collectAsState(initial = emptyList())
    val allTimeFocus by statsRepo.allTimeTotalFocusSeconds().collectAsState(initial = 0L)
    val weekAvg by statsRepo.getLastNDaysAverageTimes(days = 7).collectAsState(initial = null)
    val monthAvg by statsRepo.getLastNDaysAverageTimes(days = 30).collectAsState(initial = null)
    val yearAvg by statsRepo.getLastNDaysAverageTimes(days = 365).collectAsState(initial = null)
    val monthStats by statsRepo.getLastNDaysStats(days = 35).collectAsState(initial = emptyList())
    val yearStats by statsRepo.getLastNDaysStats(days = 371).collectAsState(initial = emptyList())

    val minutes = weekStats.map { it.totalFocusSeconds() / 60L }
    val max = minutes.maxOrNull()?.coerceAtLeast(1L) ?: 1L

    val todayStat by statsRepo.todayStat().collectAsState(initial = null)
    val todayMinutes = ((todayStat?.totalFocusSeconds() ?: 0L) / 60L)
    val todayBreakMinutes = ((todayStat?.breakSeconds ?: 0L) / 60L)
    val weekMinutes = minutes.sum()

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text(text = "统计", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatCard(title = "今日", value = "${todayMinutes} 分钟专注 · ${todayBreakMinutes} 分钟休息", modifier = Modifier.weight(1f))
            StatCard(title = "近 7 天", value = "${weekMinutes} 分钟", modifier = Modifier.weight(1f))
        }
        StatCard(title = "累计专注", value = "${(allTimeFocus / 60L)} 分钟", modifier = Modifier.fillMaxWidth())

        var range by remember { mutableStateOf(StatsRange.Week) }

        StatsSummaryRow(
            weekStats = weekStats,
            monthStats = monthStats,
            yearStats = yearStats,
            current = range,
            onSelect = { range = it },
        )

        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 1.dp,
        ) {
            Column(modifier = Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                when (range) {
                    StatsRange.Week -> {
                        Text(text = "近 7 天专注分钟", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                        Row(
                            modifier = Modifier.fillMaxWidth().height(92.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.Bottom,
                        ) {
                            for (i in minutes.indices) {
                                val m = minutes[i]
                                val ratio = (m.toFloat() / max.toFloat()).coerceIn(0f, 1f)
                                Column(
                                    modifier = Modifier.weight(1f),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Bottom,
                                ) {
                                    Box(
                                        modifier =
                                            Modifier
                                                .fillMaxWidth()
                                                .height((72f * ratio).dp)
                                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.75f), MaterialTheme.shapes.small),
                                    )
                                    Spacer(Modifier.height(6.dp))
                                    Text(
                                        text = weekStats.getOrNull(i)?.day?.dayOfMonth?.toString().orEmpty(),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                        BreakdownCard(avg = weekAvg, title = "近 7 天平均分布")
                    }

                    StatsRange.Month -> {
                        val ym = YearMonth.now()
                        Text(text = "${ym.monthValue} 月", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                        MonthCalendarHeatmap(
                            yearMonth = ym,
                            stats = monthStats,
                        )
                        BreakdownCard(avg = monthAvg, title = "近 30 天平均分布")
                    }

                    StatsRange.Year -> {
                        Text(text = "近 1 年专注热力", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                        YearHeatmap(stats = yearStats)
                        BreakdownCard(avg = yearAvg, title = "近 365 天平均分布")
                    }
                }
            }
        }
    }
}

private enum class StatsRange { Week, Month, Year }

@Composable
private fun StatsSummaryRow(
    weekStats: List<app.zhixu.pomodoro.PomodoroStat>,
    monthStats: List<app.zhixu.pomodoro.PomodoroStat>,
    yearStats: List<app.zhixu.pomodoro.PomodoroStat>,
    current: StatsRange,
    onSelect: (StatsRange) -> Unit,
) {
    fun sumMinutes(stats: List<app.zhixu.pomodoro.PomodoroStat>): Long =
        stats.sumOf { it.totalFocusSeconds() } / 60L
    val w = remember(weekStats) { sumMinutes(weekStats) }
    val m = remember(monthStats) { sumMinutes(monthStats.takeLast(30)) }
    val y = remember(yearStats) { sumMinutes(yearStats.takeLast(365)) }

    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        SummaryCard(
            title = "近 7 天",
            value = "${w} 分钟",
            selected = current == StatsRange.Week,
            onClick = { onSelect(StatsRange.Week) },
            miniValues = weekStats.map { it.totalFocusSeconds() / 60L },
            modifier = Modifier.weight(1f),
        )
        SummaryCard(
            title = "近 30 天",
            value = "${m} 分钟",
            selected = current == StatsRange.Month,
            onClick = { onSelect(StatsRange.Month) },
            miniValues = monthStats.takeLast(14).map { it.totalFocusSeconds() / 60L },
            modifier = Modifier.weight(1f),
        )
        SummaryCard(
            title = "近 365 天",
            value = "${y} 分钟",
            selected = current == StatsRange.Year,
            onClick = { onSelect(StatsRange.Year) },
            miniValues = yearStats.takeLast(14).map { it.totalFocusSeconds() / 60L },
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun SummaryCard(
    title: String,
    value: String,
    selected: Boolean,
    onClick: () -> Unit,
    miniValues: List<Long>,
    modifier: Modifier = Modifier,
) {
    val border = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.7f) else MaterialTheme.colorScheme.outlineVariant
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.large,
        tonalElevation = 1.dp,
        color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(1.dp, border),
        onClick = onClick,
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(text = title, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(text = value, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, maxLines = 1)
            MiniBarChart(values = miniValues, modifier = Modifier.fillMaxWidth().height(24.dp))
        }
    }
}

@Composable
private fun MiniBarChart(
    values: List<Long>,
    modifier: Modifier = Modifier,
) {
    val safe = values.takeLast(14)
    val max = (safe.maxOrNull() ?: 0L).coerceAtLeast(1L)
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.Bottom) {
        for (v in safe) {
            val ratio = (v.toFloat() / max.toFloat()).coerceIn(0f, 1f)
            Box(
                modifier =
                    Modifier
                        .weight(1f)
                        .height((ratio * 24f).dp.coerceAtLeast(3.dp))
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.65f), RoundedCornerShape(3.dp)),
            )
        }
    }
}

@Composable
private fun BreakdownCard(
    avg: PomodoroStatTime?,
    title: String,
) {
    val a = avg ?: return
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(text = title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        BreakdownRow(label = "00-06", seconds = a.focusQ1Seconds)
        BreakdownRow(label = "06-12", seconds = a.focusQ2Seconds)
        BreakdownRow(label = "12-18", seconds = a.focusQ3Seconds)
        BreakdownRow(label = "18-24", seconds = a.focusQ4Seconds)
        BreakdownRow(label = "休息", seconds = a.breakSeconds, tint = MaterialTheme.colorScheme.tertiary)
    }
}

@Composable
private fun BreakdownRow(
    label: String,
    seconds: Long,
    tint: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.primary,
) {
    val minutes = (seconds / 60L).coerceAtLeast(0L)
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(text = label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(text = "${minutes}m", style = MaterialTheme.typography.bodySmall, color = tint)
    }
}

@Composable
private fun MonthCalendarHeatmap(
    yearMonth: YearMonth,
    stats: List<app.zhixu.pomodoro.PomodoroStat>,
) {
    val first = yearMonth.atDay(1)
    val lastDay = yearMonth.lengthOfMonth()
    val startOffset = ((first.dayOfWeek.value + 6) % 7) // Monday=0
    val days = (1..lastDay).map { yearMonth.atDay(it) }
    val statMap = remember(stats) { stats.associateBy { it.day } }
    val values = days.map { (statMap[it]?.totalFocusSeconds() ?: 0L) / 60L }
    val max = values.maxOrNull()?.coerceAtLeast(1L) ?: 1L
    val cells = List(startOffset) { null } + days + List(((7 - (startOffset + lastDay) % 7) % 7)) { null }
    val rows = cells.chunked(7)

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        for (row in rows) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                for (d in row) {
                    val m = if (d == null) 0L else ((statMap[d]?.totalFocusSeconds() ?: 0L) / 60L)
                    val ratio = if (d == null) 0f else (m.toFloat() / max.toFloat()).coerceIn(0f, 1f)
                    Box(
                        modifier =
                            Modifier
                                .weight(1f)
                                .height(22.dp)
                                .background(
                                    if (d == null) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                                    else MaterialTheme.colorScheme.primary.copy(alpha = 0.15f + ratio * 0.75f),
                                    MaterialTheme.shapes.small,
                                ),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (d != null) {
                            Text(text = d.dayOfMonth.toString(), style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun YearHeatmap(
    stats: List<app.zhixu.pomodoro.PomodoroStat>,
) {
    val map = remember(stats) { stats.associateBy { it.day } }
    val end = LocalDate.now()
    val start = end.minusDays(364)
    val startOffset = ((start.dayOfWeek.value + 6) % 7)
    val days = (0..364).map { start.plusDays(it.toLong()) }
    val values = days.map { (map[it]?.totalFocusSeconds() ?: 0L) / 60L }
    val max = values.maxOrNull()?.coerceAtLeast(1L) ?: 1L

    val paddedDays = List(startOffset) { null } + days
    val weeks = paddedDays.chunked(7)

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        for (week in weeks.takeLast(18)) { // keep UI compact: last ~18 weeks visible
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                for (d in week) {
                    val m = if (d == null) 0L else ((map[d]?.totalFocusSeconds() ?: 0L) / 60L)
                    val ratio = if (d == null) 0f else (m.toFloat() / max.toFloat()).coerceIn(0f, 1f)
                    Box(
                        modifier =
                            Modifier
                                .weight(1f)
                                .height(10.dp)
                                .background(
                                    if (d == null) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)
                                    else MaterialTheme.colorScheme.primary.copy(alpha = 0.12f + ratio * 0.78f),
                                    MaterialTheme.shapes.extraSmall,
                                ),
                    )
                }
            }
        }
        Text(
            text = "仅显示最近 18 周（完整数据已记录）",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun StatCard(
    title: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(text = title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(text = value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        }
    }
}

private fun formatSeconds(totalSeconds: Int): String {
    val safe = totalSeconds.coerceAtLeast(0)
    val m = safe / 60
    val s = safe % 60
    return "%02d:%02d".format(m, s)
}

private fun computeRemainingSeconds(snapshot: PomodoroTimerSnapshot, nowEpochMs: Long): Int {
    if (!snapshot.isRunning || snapshot.endAtEpochMs <= 0L) return snapshot.remainingSeconds
    return ((snapshot.endAtEpochMs - nowEpochMs) / 1000L).toInt().coerceAtLeast(0)
}

private fun durationSeconds(mode: PomodoroMode, settings: app.zhixu.pomodoro.PomodoroSettings): Int =
    when (mode) {
        PomodoroMode.Focus -> settings.focusMinutes
        PomodoroMode.ShortBreak -> settings.shortBreakMinutes
        PomodoroMode.LongBreak -> settings.longBreakMinutes
    }.coerceIn(1, 24 * 60) * 60

private fun ensureNotificationPermission(
    context: Context,
    launcher: androidx.activity.result.ActivityResultLauncher<String>,
) {
    if (Build.VERSION.SDK_INT < 33) return
    if (ContextCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) return
    launcher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
}
