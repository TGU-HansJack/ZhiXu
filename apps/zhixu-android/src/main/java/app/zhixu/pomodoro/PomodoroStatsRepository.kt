package app.zhixu.pomodoro

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

data class PomodoroDayStat(
    val focusSeconds: Long,
    val focusSessions: Int,
)

class PomodoroStatsRepository(
    context: Context,
) {
    private val dataStore = context.applicationContext.pomodoroDataStore

    private val focusSecondsPrefix = "dayFocusSeconds:"
    private val focusSessionsPrefix = "dayFocusSessions:"

    val dayStats: Flow<Map<LocalDate, PomodoroDayStat>> =
        dataStore.data.map { prefs ->
            val focusSecondsByDay = HashMap<LocalDate, Long>()
            val focusSessionsByDay = HashMap<LocalDate, Int>()

            for ((key, value) in prefs.asMap()) {
                val name = key.name
                if (name.startsWith(focusSecondsPrefix)) {
                    val dateStr = name.removePrefix(focusSecondsPrefix)
                    val day = runCatching { LocalDate.parse(dateStr) }.getOrNull() ?: continue
                    val seconds = (value as? Long) ?: continue
                    focusSecondsByDay[day] = seconds
                } else if (name.startsWith(focusSessionsPrefix)) {
                    val dateStr = name.removePrefix(focusSessionsPrefix)
                    val day = runCatching { LocalDate.parse(dateStr) }.getOrNull() ?: continue
                    val sessions = (value as? Int) ?: continue
                    focusSessionsByDay[day] = sessions
                }
            }

            val out = HashMap<LocalDate, PomodoroDayStat>()
            for ((day, seconds) in focusSecondsByDay) {
                out[day] = PomodoroDayStat(focusSeconds = seconds, focusSessions = focusSessionsByDay[day] ?: 0)
            }
            for ((day, sessions) in focusSessionsByDay) {
                if (out.containsKey(day)) continue
                out[day] = PomodoroDayStat(focusSeconds = 0L, focusSessions = sessions)
            }
            out
        }

    suspend fun recordFocusSession(
        durationSeconds: Long,
        endedAt: Instant = Instant.now(),
    ) {
        if (durationSeconds <= 0) return
        val day = endedAt.atZone(ZoneId.systemDefault()).toLocalDate()
        val dateStr = day.toString()
        val focusSecondsKey = longPreferencesKey(focusSecondsPrefix + dateStr)
        val focusSessionsKey = intPreferencesKey(focusSessionsPrefix + dateStr)
        dataStore.edit { prefs ->
            val beforeSeconds = prefs[focusSecondsKey] ?: 0L
            val beforeSessions = prefs[focusSessionsKey] ?: 0
            prefs[focusSecondsKey] = beforeSeconds + durationSeconds
            prefs[focusSessionsKey] = beforeSessions + 1
        }
    }
}
