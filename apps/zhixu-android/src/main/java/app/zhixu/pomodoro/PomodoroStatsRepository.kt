package app.zhixu.pomodoro

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

data class PomodoroStat(
    val day: LocalDate,
    val focusQ1Seconds: Long,
    val focusQ2Seconds: Long,
    val focusQ3Seconds: Long,
    val focusQ4Seconds: Long,
    val breakSeconds: Long,
) {
    fun totalFocusSeconds(): Long = focusQ1Seconds + focusQ2Seconds + focusQ3Seconds + focusQ4Seconds
}

data class PomodoroStatTime(
    val focusQ1Seconds: Long,
    val focusQ2Seconds: Long,
    val focusQ3Seconds: Long,
    val focusQ4Seconds: Long,
    val breakSeconds: Long,
)

class PomodoroStatsRepository(
    context: Context,
) {
    private val dataStore: DataStore<Preferences> = context.applicationContext.pomodoroDataStore
    private val zone = ZoneId.systemDefault()

    private val lastStatDayKey = stringPreferencesKey("stats.lastDay")
    private val allTimeFocusSecondsKey = longPreferencesKey("stats.allTimeFocusSeconds")
    private val allTimeBreakSecondsKey = longPreferencesKey("stats.allTimeBreakSeconds")

    fun todayStat(now: Instant = Instant.now()): Flow<PomodoroStat?> {
        val day = now.atZone(zone).toLocalDate()
        return dataStore.data.map { prefs -> readDayStat(prefs, day) }
    }

    fun allTimeTotalFocusSeconds(): Flow<Long> =
        dataStore.data.map { prefs -> prefs[allTimeFocusSecondsKey] ?: 0L }

    fun getLastNDaysStats(now: Instant = Instant.now(), days: Int): Flow<List<PomodoroStat>> {
        val n = days.coerceIn(1, 3650)
        val today = now.atZone(zone).toLocalDate()
        val range = (0 until n).map { today.minusDays(it.toLong()) }.reversed()
        return dataStore.data.map { prefs ->
            range.map { day ->
                readDayStat(prefs, day) ?: PomodoroStat(day, 0, 0, 0, 0, 0)
            }
        }
    }

    fun getLastNDaysAverageTimes(now: Instant = Instant.now(), days: Int): Flow<PomodoroStatTime?> {
        val n = days.coerceIn(1, 3650)
        val today = now.atZone(zone).toLocalDate()
        val range = (0 until n).map { today.minusDays(it.toLong()) }
        return dataStore.data.map { prefs ->
            val stats = range.mapNotNull { readDayStat(prefs, it) }.filter { it.totalFocusSeconds() > 0 }
            if (stats.isEmpty()) return@map null
            PomodoroStatTime(
                focusQ1Seconds = stats.map { it.focusQ1Seconds }.average().toLong(),
                focusQ2Seconds = stats.map { it.focusQ2Seconds }.average().toLong(),
                focusQ3Seconds = stats.map { it.focusQ3Seconds }.average().toLong(),
                focusQ4Seconds = stats.map { it.focusQ4Seconds }.average().toLong(),
                breakSeconds = stats.map { it.breakSeconds }.average().toLong(),
            )
        }
    }

    suspend fun ensureContinuousHistory(now: Instant = Instant.now()) {
        withContext(Dispatchers.IO) {
            dataStore.edit { prefs ->
                val today = now.atZone(zone).toLocalDate()
                val lastRaw = prefs[lastStatDayKey]?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
                if (lastRaw == null) {
                    prefs[lastStatDayKey] = today.toString()
                    return@edit
                }
                val last = lastRaw
                if (last.isAfter(today)) {
                    prefs[lastStatDayKey] = today.toString()
                    return@edit
                }
                var cursor: LocalDate = last
                while (ChronoUnit.DAYS.between(cursor, today) > 0) {
                    cursor = cursor.plusDays(1)
                    // Touch day keys (no-op if already present) to keep history continuous.
                    writeDayStat(
                        prefs = prefs,
                        stat = PomodoroStat(cursor, 0, 0, 0, 0, 0),
                        onlyIfMissing = true,
                    )
                }
                prefs[lastStatDayKey] = today.toString()
            }
        }
    }

    suspend fun addFocusSeconds(
        durationSeconds: Long,
        endedAt: Instant = Instant.now(),
    ) {
        if (durationSeconds <= 0) return
        withContext(Dispatchers.IO) {
            ensureContinuousHistory(endedAt)
            dataStore.edit { prefs ->
                val day = endedAt.atZone(zone).toLocalDate()
                val time = endedAt.atZone(zone).toLocalTime()
                val quarter = quarterOfDay(time)
                val before = readDayStat(prefs, day) ?: PomodoroStat(day, 0, 0, 0, 0, 0)
                val after =
                    when (quarter) {
                        1 -> before.copy(focusQ1Seconds = before.focusQ1Seconds + durationSeconds)
                        2 -> before.copy(focusQ2Seconds = before.focusQ2Seconds + durationSeconds)
                        3 -> before.copy(focusQ3Seconds = before.focusQ3Seconds + durationSeconds)
                        else -> before.copy(focusQ4Seconds = before.focusQ4Seconds + durationSeconds)
                    }
                writeDayStat(prefs, after, onlyIfMissing = false)
                prefs[allTimeFocusSecondsKey] = (prefs[allTimeFocusSecondsKey] ?: 0L) + durationSeconds
            }
        }
    }

    suspend fun addBreakSeconds(
        durationSeconds: Long,
        endedAt: Instant = Instant.now(),
    ) {
        if (durationSeconds <= 0) return
        withContext(Dispatchers.IO) {
            ensureContinuousHistory(endedAt)
            dataStore.edit { prefs ->
                val day = endedAt.atZone(zone).toLocalDate()
                val before = readDayStat(prefs, day) ?: PomodoroStat(day, 0, 0, 0, 0, 0)
                val after = before.copy(breakSeconds = before.breakSeconds + durationSeconds)
                writeDayStat(prefs, after, onlyIfMissing = false)
                prefs[allTimeBreakSecondsKey] = (prefs[allTimeBreakSecondsKey] ?: 0L) + durationSeconds
            }
        }
    }

    suspend fun deleteAllStats() {
        withContext(Dispatchers.IO) {
            dataStore.edit { prefs ->
                val keysToRemove = prefs.asMap().keys.filter { it.name.startsWith("stats.day.") }
                for (key in keysToRemove) prefs.remove(key)
                prefs[allTimeFocusSecondsKey] = 0L
                prefs[allTimeBreakSecondsKey] = 0L
                prefs.remove(lastStatDayKey)
            }
        }
    }

    private fun quarterOfDay(time: LocalTime): Int {
        val seconds = time.toSecondOfDay()
        val quarter = 24 * 60 * 60 / 4
        return when (seconds) {
            in 0 until quarter -> 1
            in quarter until quarter * 2 -> 2
            in quarter * 2 until quarter * 3 -> 3
            else -> 4
        }
    }

    private fun dayKey(day: LocalDate, field: String): Preferences.Key<Long> =
        longPreferencesKey("stats.day.${day}:$field")

    private fun readDayStat(prefs: Preferences, day: LocalDate): PomodoroStat? {
        val q1 = prefs[dayKey(day, "focusQ1")] ?: 0L
        val q2 = prefs[dayKey(day, "focusQ2")] ?: 0L
        val q3 = prefs[dayKey(day, "focusQ3")] ?: 0L
        val q4 = prefs[dayKey(day, "focusQ4")] ?: 0L
        val b = prefs[dayKey(day, "break")] ?: 0L
        val any = (q1 or q2 or q3 or q4 or b) != 0L
        if (!any) return null
        return PomodoroStat(day = day, focusQ1Seconds = q1, focusQ2Seconds = q2, focusQ3Seconds = q3, focusQ4Seconds = q4, breakSeconds = b)
    }

    private fun writeDayStat(
        prefs: MutablePreferences,
        stat: PomodoroStat,
        onlyIfMissing: Boolean,
    ) {
        val existing = if (onlyIfMissing) readDayStatFromMutable(prefs, stat.day) else null
        if (onlyIfMissing && existing != null) return
        prefs[dayKey(stat.day, "focusQ1")] = stat.focusQ1Seconds
        prefs[dayKey(stat.day, "focusQ2")] = stat.focusQ2Seconds
        prefs[dayKey(stat.day, "focusQ3")] = stat.focusQ3Seconds
        prefs[dayKey(stat.day, "focusQ4")] = stat.focusQ4Seconds
        prefs[dayKey(stat.day, "break")] = stat.breakSeconds
    }

    private fun readDayStatFromMutable(prefs: MutablePreferences, day: LocalDate): PomodoroStat? {
        val q1 = prefs[dayKey(day, "focusQ1")] ?: 0L
        val q2 = prefs[dayKey(day, "focusQ2")] ?: 0L
        val q3 = prefs[dayKey(day, "focusQ3")] ?: 0L
        val q4 = prefs[dayKey(day, "focusQ4")] ?: 0L
        val b = prefs[dayKey(day, "break")] ?: 0L
        val any = (q1 or q2 or q3 or q4 or b) != 0L
        if (!any) return null
        return PomodoroStat(day = day, focusQ1Seconds = q1, focusQ2Seconds = q2, focusQ3Seconds = q3, focusQ4Seconds = q4, breakSeconds = b)
    }
}
