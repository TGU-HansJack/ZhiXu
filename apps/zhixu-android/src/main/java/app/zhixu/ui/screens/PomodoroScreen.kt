package app.zhixu.ui.screens

import android.content.Context
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.SkipNext
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import app.zhixu.pomodoro.PomodoroDayStat
import app.zhixu.pomodoro.PomodoroEvent
import app.zhixu.pomodoro.PomodoroMode
import app.zhixu.pomodoro.PomodoroPreferences
import app.zhixu.pomodoro.PomodoroStatsRepository
import app.zhixu.pomodoro.PomodoroViewModel
import java.time.LocalDate
import kotlinx.coroutines.delay

@Composable
fun PomodoroScreen(
    contentPadding: PaddingValues,
) {
    val context = LocalContext.current
    val prefs = remember(context) { PomodoroPreferences(context.applicationContext) }
    val statsRepo = remember(context) { PomodoroStatsRepository(context.applicationContext) }
    val vm: PomodoroViewModel =
        viewModel(
            factory =
                object : ViewModelProvider.Factory {
                    @Suppress("UNCHECKED_CAST")
                    override fun <T : ViewModel> create(modelClass: Class<T>): T {
                        return PomodoroViewModel(prefs = prefs, stats = statsRepo) as T
                    }
                },
        )

    val state by vm.state.collectAsState()
    val dayStats by statsRepo.dayStats.collectAsState(initial = emptyMap())
    val dividerColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
    val currentSettings by rememberUpdatedState(state.settings)

    LaunchedEffect(vm) {
        vm.eventFlow.collect { event ->
            when (event) {
                is PomodoroEvent.Finished -> {
                    playPomodoroAlarm(context, currentSettings.soundEnabled, currentSettings.vibrationEnabled, currentSettings.ringtoneUri)
                }
            }
        }
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
            PomodoroTimerCard(
                mode = state.mode,
                remainingSeconds = state.remainingSeconds,
                totalSeconds = state.totalSeconds,
                completedFocusInSet = state.completedFocusInSet,
                isRunning = state.isRunning,
                onStart = vm::start,
                onPause = vm::pause,
                onReset = vm::reset,
                onSkip = vm::skip,
                modifier = Modifier.fillMaxWidth().padding(16.dp),
            )
            HorizontalDivider(color = dividerColor)
        }

        item {
            PomodoroStatsSection(
                dayStats = dayStats,
                modifier = Modifier.fillMaxWidth().padding(16.dp),
            )
        }
    }
}

@Composable
private fun PomodoroTimerCard(
    mode: PomodoroMode,
    remainingSeconds: Int,
    totalSeconds: Int,
    completedFocusInSet: Int,
    isRunning: Boolean,
    onStart: () -> Unit,
    onPause: () -> Unit,
    onReset: () -> Unit,
    onSkip: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column {
                    Text(
                        text =
                            when (mode) {
                                PomodoroMode.Focus -> "专注"
                                PomodoroMode.ShortBreak -> "短休息"
                                PomodoroMode.LongBreak -> "长休息"
                            },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = "本轮已完成 $completedFocusInSet 个番茄",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                val progress =
                    if (totalSeconds <= 0) 0f else (totalSeconds - remainingSeconds).toFloat() / totalSeconds.toFloat()
                Box(contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(progress = { progress.coerceIn(0f, 1f) }, modifier = Modifier.size(56.dp))
                    Text(text = "${(progress * 100).toInt()}%", style = MaterialTheme.typography.labelSmall)
                }
            }

            Text(
                text = formatSeconds(remainingSeconds),
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.SemiBold,
            )

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    modifier = Modifier.weight(1f),
                    onClick = { if (isRunning) onPause() else onStart() },
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
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onSurface),
                ) {
                    Icon(imageVector = Icons.Outlined.Stop, contentDescription = null)
                    Spacer(Modifier.size(8.dp))
                    Text(text = "重置")
                }
                OutlinedButton(
                    onClick = onSkip,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onSurface),
                ) {
                    Icon(imageVector = Icons.Outlined.SkipNext, contentDescription = null)
                }
            }
        }
    }
}

@Composable
private fun PomodoroStatsSection(
    dayStats: Map<LocalDate, PomodoroDayStat>,
    modifier: Modifier = Modifier,
) {
    val today = remember { LocalDate.now() }
    val days = remember(today) { (0..6).map { today.minusDays((6 - it).toLong()) } }
    val minutes = days.map { (dayStats[it]?.focusSeconds ?: 0L) / 60L }
    val max = minutes.maxOrNull()?.coerceAtLeast(1L) ?: 1L

    val todayStat = dayStats[today]
    val todayMinutes = (todayStat?.focusSeconds ?: 0L) / 60L
    val todaySessions = todayStat?.focusSessions ?: 0
    val weekMinutes = minutes.sum()

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text(text = "统计", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatCard(title = "今日", value = "${todayMinutes} 分钟 / $todaySessions 次", modifier = Modifier.weight(1f))
            StatCard(title = "近 7 天", value = "${weekMinutes} 分钟", modifier = Modifier.weight(1f))
        }

        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 1.dp,
        ) {
            Column(modifier = Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(text = "近 7 天专注分钟", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Row(
                    modifier = Modifier.fillMaxWidth().height(92.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.Bottom,
                ) {
                    for (i in days.indices) {
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
                            Text(text = days[i].dayOfMonth.toString(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
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

private suspend fun playPomodoroAlarm(
    context: Context,
    soundEnabled: Boolean,
    vibrationEnabled: Boolean,
    ringtoneUri: String,
) {
    if (vibrationEnabled) {
        val vibrator = context.getSystemService(Vibrator::class.java)
        if (vibrator != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createOneShot(500L, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(500L)
            }
        }
    }

    if (!soundEnabled) return
    val uri =
        runCatching {
            if (ringtoneUri.isNotBlank()) Uri.parse(ringtoneUri) else null
        }.getOrNull()
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

    val ringtone = runCatching { RingtoneManager.getRingtone(context, uri) }.getOrNull()
    ringtone?.play()
    delay(3500)
    ringtone?.stop()
}
