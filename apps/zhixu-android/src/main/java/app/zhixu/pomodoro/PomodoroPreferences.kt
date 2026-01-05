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
    val alarmEnabled: Boolean = true,
    val vibrateEnabled: Boolean = true,
    val dndEnabled: Boolean = false,
    val mediaVolumeForAlarm: Boolean = false,
    val singleProgressBar: Boolean = false,
    val autostartNextSession: Boolean = false,
    val aodEnabled: Boolean = false,
    val secureAod: Boolean = true,
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
    private val alarmEnabledKey = booleanPreferencesKey("alarmEnabled")
    private val vibrateEnabledKey = booleanPreferencesKey("vibrateEnabled")
    private val dndEnabledKey = booleanPreferencesKey("dndEnabled")
    private val mediaVolumeForAlarmKey = booleanPreferencesKey("mediaVolumeForAlarm")
    private val singleProgressBarKey = booleanPreferencesKey("singleProgressBar")
    private val autostartNextSessionKey = booleanPreferencesKey("autostartNextSession")
    private val aodEnabledKey = booleanPreferencesKey("aodEnabled")
    private val secureAodKey = booleanPreferencesKey("secureAod")
    private val ringtoneUriKey = stringPreferencesKey("ringtoneUri")

    val settings: Flow<PomodoroSettings> =
        dataStore.data.map { prefs ->
            PomodoroSettings(
                focusMinutes = (prefs[focusMinutesKey] ?: 25).coerceIn(1, 180),
                shortBreakMinutes = (prefs[shortBreakMinutesKey] ?: 5).coerceIn(1, 60),
                longBreakMinutes = (prefs[longBreakMinutesKey] ?: 15).coerceIn(1, 180),
                longBreakEvery = (prefs[longBreakEveryKey] ?: 4).coerceIn(1, 12),
                alarmEnabled = prefs[alarmEnabledKey] ?: true,
                vibrateEnabled = prefs[vibrateEnabledKey] ?: true,
                dndEnabled = prefs[dndEnabledKey] ?: false,
                mediaVolumeForAlarm = prefs[mediaVolumeForAlarmKey] ?: false,
                singleProgressBar = prefs[singleProgressBarKey] ?: false,
                autostartNextSession = prefs[autostartNextSessionKey] ?: false,
                aodEnabled = prefs[aodEnabledKey] ?: false,
                secureAod = prefs[secureAodKey] ?: true,
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

    suspend fun updateAlarmEnabled(enabled: Boolean) {
        dataStore.edit { it[alarmEnabledKey] = enabled }
    }

    suspend fun updateVibrateEnabled(enabled: Boolean) {
        dataStore.edit { it[vibrateEnabledKey] = enabled }
    }

    suspend fun updateDndEnabled(enabled: Boolean) {
        dataStore.edit { it[dndEnabledKey] = enabled }
    }

    suspend fun updateMediaVolumeForAlarm(enabled: Boolean) {
        dataStore.edit { it[mediaVolumeForAlarmKey] = enabled }
    }

    suspend fun updateSingleProgressBar(enabled: Boolean) {
        dataStore.edit { it[singleProgressBarKey] = enabled }
    }

    suspend fun updateAutostartNextSession(enabled: Boolean) {
        dataStore.edit { it[autostartNextSessionKey] = enabled }
    }

    suspend fun updateAodEnabled(enabled: Boolean) {
        dataStore.edit { it[aodEnabledKey] = enabled }
    }

    suspend fun updateSecureAod(enabled: Boolean) {
        dataStore.edit { it[secureAodKey] = enabled }
    }

    suspend fun updateRingtoneUri(uri: String) {
        dataStore.edit { it[ringtoneUriKey] = uri }
    }
}
