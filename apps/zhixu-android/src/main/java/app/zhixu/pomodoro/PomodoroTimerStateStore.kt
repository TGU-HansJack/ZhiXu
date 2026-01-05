package app.zhixu.pomodoro

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

data class PomodoroTimerSnapshot(
    val mode: PomodoroMode = PomodoroMode.Focus,
    val nextMode: PomodoroMode = PomodoroMode.ShortBreak,
    val isRunning: Boolean = false,
    val endAtEpochMs: Long = 0L,
    val remainingSeconds: Int = 25 * 60,
    val startedAtEpochMs: Long = 0L,
    val currentFocusCount: Int = 1,
    val totalFocusCount: Int = 4,
    val alarmRinging: Boolean = false,
    val lastResetSnapshot: PomodoroTimerSnapshot? = null,
)

class PomodoroTimerStateStore(
    context: Context,
) {
    private val dataStore: DataStore<Preferences> = context.applicationContext.pomodoroDataStore

    private val modeKey = stringPreferencesKey("timerMode")
    private val nextModeKey = stringPreferencesKey("nextTimerMode")
    private val isRunningKey = booleanPreferencesKey("timerRunning")
    private val endAtEpochMsKey = longPreferencesKey("endAtEpochMs")
    private val remainingSecondsKey = intPreferencesKey("remainingSeconds")
    private val startedAtEpochMsKey = longPreferencesKey("startedAtEpochMs")
    private val currentFocusCountKey = intPreferencesKey("currentFocusCount")
    private val alarmRingingKey = booleanPreferencesKey("alarmRinging")

    private val resetModeKey = stringPreferencesKey("reset.timerMode")
    private val resetNextModeKey = stringPreferencesKey("reset.nextTimerMode")
    private val resetIsRunningKey = booleanPreferencesKey("reset.timerRunning")
    private val resetEndAtEpochMsKey = longPreferencesKey("reset.endAtEpochMs")
    private val resetRemainingSecondsKey = intPreferencesKey("reset.remainingSeconds")
    private val resetStartedAtEpochMsKey = longPreferencesKey("reset.startedAtEpochMs")
    private val resetCurrentFocusCountKey = intPreferencesKey("reset.currentFocusCount")
    private val resetAlarmRingingKey = booleanPreferencesKey("reset.alarmRinging")

    val snapshot: Flow<PomodoroTimerSnapshot> =
        dataStore.data.map { prefs ->
            val mode = PomodoroMode.fromWire(prefs[modeKey].orEmpty()) ?: PomodoroMode.Focus
            val nextMode = PomodoroMode.fromWire(prefs[nextModeKey].orEmpty()) ?: PomodoroMode.ShortBreak
            val resetMode = PomodoroMode.fromWire(prefs[resetModeKey].orEmpty())
            val resetNextMode = PomodoroMode.fromWire(prefs[resetNextModeKey].orEmpty())

            val resetSnapshot =
                if (resetMode == null || resetNextMode == null) {
                    null
                } else {
                    PomodoroTimerSnapshot(
                        mode = resetMode,
                        nextMode = resetNextMode,
                        isRunning = prefs[resetIsRunningKey] ?: false,
                        endAtEpochMs = prefs[resetEndAtEpochMsKey] ?: 0L,
                        remainingSeconds = prefs[resetRemainingSecondsKey] ?: 0,
                        startedAtEpochMs = prefs[resetStartedAtEpochMsKey] ?: 0L,
                        currentFocusCount = (prefs[resetCurrentFocusCountKey] ?: 1).coerceAtLeast(1),
                        totalFocusCount = 0,
                        alarmRinging = prefs[resetAlarmRingingKey] ?: false,
                        lastResetSnapshot = null,
                    )
                }

            PomodoroTimerSnapshot(
                mode = mode,
                nextMode = nextMode,
                isRunning = prefs[isRunningKey] ?: false,
                endAtEpochMs = prefs[endAtEpochMsKey] ?: 0L,
                remainingSeconds = (prefs[remainingSecondsKey] ?: 25 * 60).coerceAtLeast(0),
                startedAtEpochMs = prefs[startedAtEpochMsKey] ?: 0L,
                currentFocusCount = (prefs[currentFocusCountKey] ?: 1).coerceAtLeast(1),
                totalFocusCount = 0,
                alarmRinging = prefs[alarmRingingKey] ?: false,
                lastResetSnapshot = resetSnapshot,
            )
        }

    suspend fun updateSnapshot(
        snapshot: PomodoroTimerSnapshot,
        totalFocusCount: Int,
    ) {
        dataStore.edit { prefs ->
            prefs[modeKey] = snapshot.mode.wire
            prefs[nextModeKey] = snapshot.nextMode.wire
            prefs[isRunningKey] = snapshot.isRunning
            prefs[endAtEpochMsKey] = snapshot.endAtEpochMs
            prefs[remainingSecondsKey] = snapshot.remainingSeconds
            prefs[startedAtEpochMsKey] = snapshot.startedAtEpochMs
            prefs[currentFocusCountKey] = snapshot.currentFocusCount.coerceAtLeast(1)
            prefs[alarmRingingKey] = snapshot.alarmRinging

            val reset = snapshot.lastResetSnapshot
            if (reset == null) {
                prefs.remove(resetModeKey)
                prefs.remove(resetNextModeKey)
                prefs.remove(resetIsRunningKey)
                prefs.remove(resetEndAtEpochMsKey)
                prefs.remove(resetRemainingSecondsKey)
                prefs.remove(resetStartedAtEpochMsKey)
                prefs.remove(resetCurrentFocusCountKey)
                prefs.remove(resetAlarmRingingKey)
            } else {
                prefs[resetModeKey] = reset.mode.wire
                prefs[resetNextModeKey] = reset.nextMode.wire
                prefs[resetIsRunningKey] = reset.isRunning
                prefs[resetEndAtEpochMsKey] = reset.endAtEpochMs
                prefs[resetRemainingSecondsKey] = reset.remainingSeconds
                prefs[resetStartedAtEpochMsKey] = reset.startedAtEpochMs
                prefs[resetCurrentFocusCountKey] = reset.currentFocusCount.coerceAtLeast(1)
                prefs[resetAlarmRingingKey] = reset.alarmRinging
            }
        }
    }

    suspend fun resetToDefaults(
        settings: PomodoroSettings,
    ) {
        val focusSeconds = (settings.focusMinutes.coerceIn(1, 180) * 60)
        val next =
            PomodoroTimerSnapshot(
                mode = PomodoroMode.Focus,
                nextMode = PomodoroMode.ShortBreak,
                isRunning = false,
                endAtEpochMs = 0L,
                remainingSeconds = focusSeconds,
                startedAtEpochMs = 0L,
                currentFocusCount = 1,
                totalFocusCount = settings.longBreakEvery.coerceAtLeast(1),
                alarmRinging = false,
                lastResetSnapshot = null,
            )
        updateSnapshot(next, totalFocusCount = settings.longBreakEvery.coerceAtLeast(1))
    }
}
