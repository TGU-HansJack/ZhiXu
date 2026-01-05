package app.zhixu.pomodoro

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.media.AudioAttributes
import android.media.Ringtone
import android.media.RingtoneManager
import android.os.Build
import android.os.IBinder
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Instant

class PomodoroService : Service() {
    object Actions {
        const val TOGGLE = "app.zhixu.pomodoro.action.TOGGLE"
        const val RESET = "app.zhixu.pomodoro.action.RESET"
        const val UNDO_RESET = "app.zhixu.pomodoro.action.UNDO_RESET"
        const val SKIP = "app.zhixu.pomodoro.action.SKIP"
        const val STOP_ALARM = "app.zhixu.pomodoro.action.STOP_ALARM"
        const val SET_MODE = "app.zhixu.pomodoro.action.SET_MODE"
        const val CLEAR_STATE = "app.zhixu.pomodoro.action.CLEAR_STATE"
    }

    companion object {
        const val NOTIFICATION_CHANNEL_ID: String = "pomodoro_timer"
        const val NOTIFICATION_ID: Int = 2001
        const val EXTRA_MODE: String = "mode"

        fun startAction(context: android.content.Context, action: String) {
            val intent = Intent(context, PomodoroService::class.java).apply { this.action = action }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun startSetMode(context: android.content.Context, mode: PomodoroMode) {
            val intent =
                Intent(context, PomodoroService::class.java).apply {
                    action = Actions.SET_MODE
                    putExtra(EXTRA_MODE, mode.wire)
                }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
    }

    private val job: Job = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.Main.immediate + job)
    private val lock = Mutex()

    private lateinit var timerStore: PomodoroTimerStateStore
    private lateinit var prefs: PomodoroPreferences
    private lateinit var stats: PomodoroStatsRepository

    private var ticker: Job? = null
    private var alarmStopper: Job? = null
    private var ringtone: Ringtone? = null

    private val notificationManagerCompat by lazy { NotificationManagerCompat.from(this) }
    private val notificationManagerService by lazy { getSystemService(NotificationManager::class.java) }
    private val vibrator by lazy { getSystemService(Vibrator::class.java) }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        timerStore = PomodoroTimerStateStore(applicationContext)
        prefs = PomodoroPreferences(applicationContext)
        stats = PomodoroStatsRepository(applicationContext)
        scope.launch(Dispatchers.IO) { runCatching { stats.ensureContinuousHistory() } }
    }

    override fun onDestroy() {
        ticker?.cancel()
        alarmStopper?.cancel()
        stopAlarmInternal()
        job.cancel()
        super.onDestroy()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        scope.launch {
            lock.withLock {
                when (action) {
                    Actions.TOGGLE -> toggle()
                    Actions.RESET -> reset()
                    Actions.UNDO_RESET -> undoReset()
                    Actions.SKIP -> skip(fromButton = true)
                    Actions.STOP_ALARM -> stopAlarm()
                    Actions.SET_MODE -> setMode(intent?.getStringExtra(EXTRA_MODE).orEmpty())
                    Actions.CLEAR_STATE -> clearState()
                    else -> ensureForeground()
                }
            }
        }
        return START_STICKY
    }

    private suspend fun ensureForeground() {
        val snap = timerStore.snapshot.first()
        val settings = prefs.settings.first()
        val totalFocusCount = settings.longBreakEvery.coerceAtLeast(1)
        val withTotal = snap.copy(totalFocusCount = totalFocusCount)
        startForeground(NOTIFICATION_ID, buildNotification(withTotal, settings))
        if (withTotal.isRunning) startTickerIfNeeded()
    }

    private suspend fun toggle() {
        val settings = prefs.settings.first()
        val totalFocusCount = settings.longBreakEvery.coerceAtLeast(1)
        val snap0 = timerStore.snapshot.first().copy(totalFocusCount = totalFocusCount)

        if (snap0.alarmRinging) {
            stopAlarm()
            return
        }

        val nowMs = System.currentTimeMillis()
        val next =
            if (snap0.isRunning) {
                val remaining = remainingSeconds(snap0, nowMs)
                maybeRecordElapsed(snap0, settings, nowMs)
                applyDnd(enabled = false)
                snap0.copy(
                    isRunning = false,
                    endAtEpochMs = 0L,
                    startedAtEpochMs = 0L,
                    remainingSeconds = remaining,
                )
            } else {
                val remaining = snap0.remainingSeconds.coerceAtLeast(1)
                val endAt = nowMs + remaining * 1000L
                if (snap0.mode == PomodoroMode.Focus && settings.dndEnabled) applyDnd(enabled = true)
                snap0.copy(
                    isRunning = true,
                    startedAtEpochMs = nowMs,
                    endAtEpochMs = endAt,
                    remainingSeconds = remaining,
                )
            }

        timerStore.updateSnapshot(next, totalFocusCount = totalFocusCount)
        ensureForeground()
        if (next.isRunning) startTickerIfNeeded() else stopTicker()
    }

    private suspend fun reset() {
        val settings = prefs.settings.first()
        val totalFocusCount = settings.longBreakEvery.coerceAtLeast(1)
        val snap0 = timerStore.snapshot.first().copy(totalFocusCount = totalFocusCount)
        val nowMs = System.currentTimeMillis()

        if (snap0.isRunning) {
            maybeRecordElapsed(snap0, settings, nowMs)
        }
        stopTicker()
        stopAlarmInternal()
        applyDnd(enabled = false)

        val resetTo = PomodoroMode.Focus
        val resetRemaining = durationSeconds(resetTo, settings)
        val nextMode = PomodoroMode.ShortBreak

        val next =
            PomodoroTimerSnapshot(
                mode = resetTo,
                nextMode = nextMode,
                isRunning = false,
                endAtEpochMs = 0L,
                remainingSeconds = resetRemaining,
                startedAtEpochMs = 0L,
                currentFocusCount = 1,
                totalFocusCount = totalFocusCount,
                alarmRinging = false,
                lastResetSnapshot = snap0.copy(isRunning = false, endAtEpochMs = 0L, startedAtEpochMs = 0L),
            )
        timerStore.updateSnapshot(next, totalFocusCount = totalFocusCount)
        ensureForeground()
    }

    private suspend fun undoReset() {
        val settings = prefs.settings.first()
        val totalFocusCount = settings.longBreakEvery.coerceAtLeast(1)
        val snap = timerStore.snapshot.first().copy(totalFocusCount = totalFocusCount)
        val prev = snap.lastResetSnapshot ?: return
        timerStore.updateSnapshot(prev.copy(lastResetSnapshot = null), totalFocusCount = totalFocusCount)
        ensureForeground()
    }

    private suspend fun skip(fromButton: Boolean) {
        val settings = prefs.settings.first()
        val totalFocusCount = settings.longBreakEvery.coerceAtLeast(1)
        val snap0 = timerStore.snapshot.first().copy(totalFocusCount = totalFocusCount)
        val nowMs = System.currentTimeMillis()

        if (snap0.isRunning) {
            maybeRecordElapsed(snap0, settings, nowMs)
        }
        stopTicker()
        stopAlarmInternal()
        applyDnd(enabled = false)

        val (nextMode, nextFocusCount) = computeNextModeAndCount(snap0, totalFocusCount)
        val remaining = durationSeconds(nextMode, settings)
        val after =
            snap0.copy(
                mode = nextMode,
                nextMode = computeNextModeAndCount(snap0.copy(mode = nextMode, currentFocusCount = nextFocusCount), totalFocusCount).first,
                currentFocusCount = nextFocusCount,
                isRunning = false,
                endAtEpochMs = 0L,
                startedAtEpochMs = 0L,
                remainingSeconds = remaining,
                alarmRinging = false,
                lastResetSnapshot = snap0.lastResetSnapshot,
            )
        timerStore.updateSnapshot(after, totalFocusCount = totalFocusCount)
        ensureForeground()
    }

    private suspend fun setMode(modeWire: String) {
        val settings = prefs.settings.first()
        val totalFocusCount = settings.longBreakEvery.coerceAtLeast(1)
        val snap0 = timerStore.snapshot.first().copy(totalFocusCount = totalFocusCount)

        if (snap0.isRunning) {
            maybeRecordElapsed(snap0, settings, System.currentTimeMillis())
        }
        stopTicker()
        stopAlarmInternal()
        applyDnd(enabled = false)

        val nextMode = PomodoroMode.fromWire(modeWire) ?: PomodoroMode.Focus
        val remaining = durationSeconds(nextMode, settings)
        val next =
            snap0.copy(
                mode = nextMode,
                nextMode = computeNextModeAndCount(snap0.copy(mode = nextMode), totalFocusCount).first,
                isRunning = false,
                endAtEpochMs = 0L,
                startedAtEpochMs = 0L,
                remainingSeconds = remaining,
                alarmRinging = false,
            )
        timerStore.updateSnapshot(next, totalFocusCount = totalFocusCount)
        ensureForeground()
    }

    private suspend fun clearState() {
        val settings = prefs.settings.first()
        stopTicker()
        stopAlarmInternal()
        applyDnd(enabled = false)
        timerStore.resetToDefaults(settings)
        ensureForeground()
    }

    private suspend fun onFinished() {
        val settings = prefs.settings.first()
        val totalFocusCount = settings.longBreakEvery.coerceAtLeast(1)
        val snap0 = timerStore.snapshot.first().copy(totalFocusCount = totalFocusCount)
        val nowMs = System.currentTimeMillis()

        maybeRecordElapsed(snap0, settings, nowMs, forceFullSession = true)
        stopTicker()
        applyDnd(enabled = false)

        val (nextMode, nextFocusCount) = computeNextModeAndCount(snap0, totalFocusCount)
        val nextRemaining = durationSeconds(nextMode, settings)
        val next =
            snap0.copy(
                mode = nextMode,
                nextMode = computeNextModeAndCount(snap0.copy(mode = nextMode, currentFocusCount = nextFocusCount), totalFocusCount).first,
                currentFocusCount = nextFocusCount,
                isRunning = false,
                endAtEpochMs = 0L,
                startedAtEpochMs = 0L,
                remainingSeconds = nextRemaining,
                alarmRinging = settings.alarmEnabled || settings.vibrateEnabled,
            )
        timerStore.updateSnapshot(next, totalFocusCount = totalFocusCount)

        if (next.alarmRinging) {
            startAlarm(settings)
        } else if (settings.autostartNextSession) {
            toggle()
            return
        }

        ensureForeground()
    }

    private suspend fun stopAlarm() {
        val settings = prefs.settings.first()
        val totalFocusCount = settings.longBreakEvery.coerceAtLeast(1)
        val snap0 = timerStore.snapshot.first().copy(totalFocusCount = totalFocusCount)
        stopAlarmInternal()
        alarmStopper?.cancel()
        alarmStopper = null
        val next = snap0.copy(alarmRinging = false)
        timerStore.updateSnapshot(next, totalFocusCount = totalFocusCount)
        ensureForeground()
        if (settings.autostartNextSession) {
            toggle()
        }
    }

    private fun startTickerIfNeeded() {
        if (ticker?.isActive == true) return
        ticker =
            scope.launch {
                while (true) {
                    delay(250)
                    val settings = prefs.settings.first()
                    val totalFocusCount = settings.longBreakEvery.coerceAtLeast(1)
                    val snap = timerStore.snapshot.first().copy(totalFocusCount = totalFocusCount)
                    if (!snap.isRunning) return@launch
                    val now = System.currentTimeMillis()
                    val remaining = remainingSeconds(snap, now)
                    if (remaining <= 0) {
                        onFinished()
                        return@launch
                    }
                    startForeground(NOTIFICATION_ID, buildNotification(snap, settings))
                }
            }
    }

    private fun stopTicker() {
        ticker?.cancel()
        ticker = null
    }

    private fun remainingSeconds(snap: PomodoroTimerSnapshot, nowEpochMs: Long): Int {
        if (!snap.isRunning || snap.endAtEpochMs <= 0L) return snap.remainingSeconds
        return ((snap.endAtEpochMs - nowEpochMs) / 1000L).toInt().coerceAtLeast(0)
    }

    private fun durationSeconds(mode: PomodoroMode, settings: PomodoroSettings): Int =
        when (mode) {
            PomodoroMode.Focus -> settings.focusMinutes
            PomodoroMode.ShortBreak -> settings.shortBreakMinutes
            PomodoroMode.LongBreak -> settings.longBreakMinutes
        }.coerceIn(1, 24 * 60) * 60

    private fun computeNextModeAndCount(
        snap: PomodoroTimerSnapshot,
        totalFocusCount: Int,
    ): Pair<PomodoroMode, Int> {
        return when (snap.mode) {
            PomodoroMode.Focus -> {
                val nextCount = (snap.currentFocusCount + 1).coerceAtLeast(1)
                val isLong = snap.currentFocusCount % totalFocusCount == 0
                (if (isLong) PomodoroMode.LongBreak else PomodoroMode.ShortBreak) to nextCount
            }
            PomodoroMode.ShortBreak, PomodoroMode.LongBreak -> PomodoroMode.Focus to snap.currentFocusCount
        }
    }

    private suspend fun maybeRecordElapsed(
        snap: PomodoroTimerSnapshot,
        settings: PomodoroSettings,
        nowEpochMs: Long,
        forceFullSession: Boolean = false,
    ) {
        val total = durationSeconds(snap.mode, settings)
        val remaining = remainingSeconds(snap, nowEpochMs)
        val elapsed =
            if (forceFullSession) total
            else (total - remaining).coerceIn(0, total)
        if (elapsed <= 0) return
        val endedAt = Instant.ofEpochMilli(nowEpochMs)
        when (snap.mode) {
            PomodoroMode.Focus -> stats.addFocusSeconds(elapsed.toLong(), endedAt = endedAt)
            PomodoroMode.ShortBreak, PomodoroMode.LongBreak -> stats.addBreakSeconds(elapsed.toLong(), endedAt = endedAt)
        }
    }

    private fun buildNotification(snap: PomodoroTimerSnapshot, settings: PomodoroSettings): Notification {
        val now = System.currentTimeMillis()
        val remaining = remainingSeconds(snap, now)
        val total = durationSeconds(snap.mode, settings).coerceAtLeast(1)
        val sessionProgress = ((total - remaining).toFloat() / total.toFloat()).coerceIn(0f, 1f)
        val completedFocusInSet = (snap.currentFocusCount - 1).coerceAtLeast(0)
        val progress =
            if (settings.singleProgressBar || snap.mode != PomodoroMode.Focus) {
                sessionProgress
            } else {
                ((completedFocusInSet.toFloat() + sessionProgress) / snap.totalFocusCount.coerceAtLeast(1).toFloat()).coerceIn(0f, 1f)
            }

        val title =
            when (snap.mode) {
                PomodoroMode.Focus -> "专注"
                PomodoroMode.ShortBreak -> "短休息"
                PomodoroMode.LongBreak -> "长休息"
            }
        val content = "${formatSeconds(remaining)}  ·  下一步：${when (snap.nextMode) {
            PomodoroMode.Focus -> "专注"
            PomodoroMode.ShortBreak -> "短休息"
            PomodoroMode.LongBreak -> "长休息"
        }}"

        val mainIntent =
            packageManager.getLaunchIntentForPackage(packageName)?.let { launch ->
                PendingIntent.getActivity(
                    this,
                    0,
                    launch,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                )
            }

        val builder =
            NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                .setContentTitle(title)
                .setContentText(content)
                .setContentIntent(mainIntent)
                .setOngoing(snap.isRunning)
                .setOnlyAlertOnce(true)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setSilent(true)
                .setProgress(1000, (progress * 1000).toInt(), false)

        if (snap.alarmRinging) {
            builder.addAction(
                NotificationCompat.Action.Builder(
                    0,
                    "停止",
                    pendingServiceIntent(Actions.STOP_ALARM, 10),
                ).build(),
            )
        } else {
            builder.addAction(
                NotificationCompat.Action.Builder(
                    0,
                    if (snap.isRunning) "暂停" else "开始",
                    pendingServiceIntent(Actions.TOGGLE, 1),
                ).build(),
            )
            builder.addAction(
                NotificationCompat.Action.Builder(
                    0,
                    "重置",
                    pendingServiceIntent(Actions.RESET, 2),
                ).build(),
            )
            builder.addAction(
                NotificationCompat.Action.Builder(
                    0,
                    "跳过",
                    pendingServiceIntent(Actions.SKIP, 3),
                ).build(),
            )
            if (snap.lastResetSnapshot != null) {
                builder.addAction(
                    NotificationCompat.Action.Builder(
                        0,
                        "撤销重置",
                        pendingServiceIntent(Actions.UNDO_RESET, 4),
                    ).build(),
                )
            }
        }

        return builder.build()
    }

    private fun pendingServiceIntent(action: String, requestCode: Int): PendingIntent {
        val intent = Intent(this, PomodoroService::class.java).apply { this.action = action }
        return PendingIntent.getService(
            this,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun startAlarm(settings: PomodoroSettings) {
        stopAlarmInternal()

        if (settings.vibrateEnabled) {
            runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator?.vibrate(VibrationEffect.createOneShot(700L, VibrationEffect.DEFAULT_AMPLITUDE))
                } else {
                    @Suppress("DEPRECATION")
                    vibrator?.vibrate(700L)
                }
            }
        }

        if (settings.alarmEnabled) {
            val uri =
                runCatching { settings.ringtoneUri.takeIf { it.isNotBlank() }?.let(android.net.Uri::parse) }.getOrNull()
                    ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ringtone =
                runCatching {
                    RingtoneManager.getRingtone(this, uri)?.apply {
                        if (Build.VERSION.SDK_INT >= 21) {
                            audioAttributes =
                                AudioAttributes.Builder()
                                    .setUsage(if (settings.mediaVolumeForAlarm) AudioAttributes.USAGE_MEDIA else AudioAttributes.USAGE_ALARM)
                                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                                    .build()
                        }
                        isLooping = true
                        play()
                    }
                }.getOrNull()
        }

        alarmStopper?.cancel()
        alarmStopper =
            scope.launch {
                delay(60_000L)
                lock.withLock { stopAlarm() }
            }
    }

    private fun stopAlarmInternal() {
        runCatching { ringtone?.stop() }
        ringtone = null
    }

    private fun applyDnd(enabled: Boolean) {
        if (!enabled) {
            runCatching { notificationManagerService.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_ALL) }
            return
        }
        runCatching {
            if (notificationManagerService.isNotificationPolicyAccessGranted) {
                notificationManagerService.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_PRIORITY)
            }
        }
    }

    private fun formatSeconds(totalSeconds: Int): String {
        val safe = totalSeconds.coerceAtLeast(0)
        val m = safe / 60
        val s = safe % 60
        return "%02d:%02d".format(m, s)
    }
}
