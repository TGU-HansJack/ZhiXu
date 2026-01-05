package app.zhixu.pomodoro

import android.os.SystemClock
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class PomodoroMode {
    Focus,
    ShortBreak,
    LongBreak,
}

data class PomodoroTimerState(
    val mode: PomodoroMode = PomodoroMode.Focus,
    val isRunning: Boolean = false,
    val totalSeconds: Int = 25 * 60,
    val remainingSeconds: Int = 25 * 60,
    val completedFocusInSet: Int = 0,
    val settings: PomodoroSettings = PomodoroSettings(),
)

sealed interface PomodoroEvent {
    data class Finished(val mode: PomodoroMode) : PomodoroEvent
}

class PomodoroViewModel(
    private val prefs: PomodoroPreferences,
    private val stats: PomodoroStatsRepository,
) : ViewModel() {
    private val completedFocusInSet = MutableStateFlow(0)
    private val mode = MutableStateFlow(PomodoroMode.Focus)
    private val isRunning = MutableStateFlow(false)
    private val remainingSeconds = MutableStateFlow(25 * 60)

    private var ticker: Job? = null

    private val events = MutableSharedFlow<PomodoroEvent>(extraBufferCapacity = 8)
    val eventFlow = events.asSharedFlow()

    val state: StateFlow<PomodoroTimerState> =
        combine(
            prefs.settings,
            mode,
            isRunning,
            remainingSeconds,
            completedFocusInSet,
        ) { settings, mode, running, remaining, completedInSet ->
            val total = durationSeconds(mode, settings)
            val safeRemaining =
                if (!running && (remaining <= 0 || remaining > total)) {
                    total
                } else {
                    remaining.coerceIn(0, total)
                }
            PomodoroTimerState(
                mode = mode,
                isRunning = running,
                totalSeconds = total,
                remainingSeconds = safeRemaining,
                completedFocusInSet = completedInSet,
                settings = settings,
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000),
            initialValue = PomodoroTimerState(),
        )

    fun start() {
        if (isRunning.value) return
        isRunning.value = true
        ticker?.cancel()
        ticker =
            viewModelScope.launch {
                val startRemaining = remainingSeconds.value.coerceAtLeast(0)
                val endAtMs = SystemClock.elapsedRealtime() + startRemaining * 1000L
                while (true) {
                    val now = SystemClock.elapsedRealtime()
                    val left = ((endAtMs - now) / 1000L).toInt()
                    remainingSeconds.value = left.coerceAtLeast(0)
                    if (left <= 0) break
                    delay(250)
                }
                isRunning.value = false
                onFinished()
            }
    }

    fun pause() {
        isRunning.value = false
        ticker?.cancel()
        ticker = null
    }

    fun reset() {
        pause()
        remainingSeconds.value = durationSeconds(mode.value, state.value.settings)
    }

    fun skip() {
        pause()
        val next =
            when (mode.value) {
                PomodoroMode.Focus -> PomodoroMode.ShortBreak
                PomodoroMode.ShortBreak, PomodoroMode.LongBreak -> PomodoroMode.Focus
            }
        mode.value = next
        remainingSeconds.value = durationSeconds(next, state.value.settings)
    }

    private suspend fun onFinished() {
        val finishedMode = mode.value
        if (finishedMode == PomodoroMode.Focus) {
            val sessionSeconds = durationSeconds(PomodoroMode.Focus, state.value.settings).toLong()
            runCatching { stats.recordFocusSession(durationSeconds = sessionSeconds) }
            val nextCompleted = completedFocusInSet.value + 1
            completedFocusInSet.value = nextCompleted
            val longEvery = state.value.settings.longBreakEvery.coerceAtLeast(1)
            val nextMode = if (nextCompleted % longEvery == 0) PomodoroMode.LongBreak else PomodoroMode.ShortBreak
            mode.value = nextMode
            remainingSeconds.value = durationSeconds(nextMode, state.value.settings)
        } else {
            mode.value = PomodoroMode.Focus
            remainingSeconds.value = durationSeconds(PomodoroMode.Focus, state.value.settings)
        }
        events.tryEmit(PomodoroEvent.Finished(finishedMode))
    }

    private fun durationSeconds(mode: PomodoroMode, settings: PomodoroSettings): Int =
        when (mode) {
            PomodoroMode.Focus -> settings.focusMinutes
            PomodoroMode.ShortBreak -> settings.shortBreakMinutes
            PomodoroMode.LongBreak -> settings.longBreakMinutes
        }.coerceIn(1, 24 * 60)
            .times(60)
}

