package app.zhixu.pomodoro

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

data class PomodoroSettings(
    val focusMinutes: Int = 25,
    val shortBreakMinutes: Int = 5,
    val longBreakMinutes: Int = 15,
    val longBreakEvery: Int = 4,
    val soundEnabled: Boolean = true,
    val vibrationEnabled: Boolean = true,
    val ringtoneUri: String = "",
)

class PomodoroPreferences(
    context: Context,
) {
    private val dataStore = context.applicationContext.pomodoroDataStore

    private val focusMinutesKey = intPreferencesKey("focusMinutes")
    private val shortBreakMinutesKey = intPreferencesKey("shortBreakMinutes")
    private val longBreakMinutesKey = intPreferencesKey("longBreakMinutes")
    private val longBreakEveryKey = intPreferencesKey("longBreakEvery")
    private val soundEnabledKey = booleanPreferencesKey("soundEnabled")
    private val vibrationEnabledKey = booleanPreferencesKey("vibrationEnabled")
    private val ringtoneUriKey = stringPreferencesKey("ringtoneUri")

    val settings: Flow<PomodoroSettings> =
        dataStore.data.map { prefs ->
            PomodoroSettings(
                focusMinutes = (prefs[focusMinutesKey] ?: 25).coerceIn(1, 180),
                shortBreakMinutes = (prefs[shortBreakMinutesKey] ?: 5).coerceIn(1, 60),
                longBreakMinutes = (prefs[longBreakMinutesKey] ?: 15).coerceIn(1, 180),
                longBreakEvery = (prefs[longBreakEveryKey] ?: 4).coerceIn(1, 12),
                soundEnabled = prefs[soundEnabledKey] ?: true,
                vibrationEnabled = prefs[vibrationEnabledKey] ?: true,
                ringtoneUri = prefs[ringtoneUriKey].orEmpty(),
            )
        }

    suspend fun updateFocusMinutes(minutes: Int) {
        dataStore.edit { it[focusMinutesKey] = minutes.coerceIn(1, 180) }
    }

    suspend fun updateShortBreakMinutes(minutes: Int) {
        dataStore.edit { it[shortBreakMinutesKey] = minutes.coerceIn(1, 60) }
    }

    suspend fun updateLongBreakMinutes(minutes: Int) {
        dataStore.edit { it[longBreakMinutesKey] = minutes.coerceIn(1, 180) }
    }

    suspend fun updateLongBreakEvery(count: Int) {
        dataStore.edit { it[longBreakEveryKey] = count.coerceIn(1, 12) }
    }

    suspend fun updateSoundEnabled(enabled: Boolean) {
        dataStore.edit { it[soundEnabledKey] = enabled }
    }

    suspend fun updateVibrationEnabled(enabled: Boolean) {
        dataStore.edit { it[vibrationEnabledKey] = enabled }
    }

    suspend fun updateRingtoneUri(uri: String) {
        dataStore.edit { it[ringtoneUriKey] = uri }
    }
}
