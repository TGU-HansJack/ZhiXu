package app.zhixu.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

data class DailyReminderSettings(
    val enabled: Boolean,
    val timeHHmm: String,
    /**
     * Bitmask of selected weekdays (Mon..Sun): bit0 = Mon ... bit6 = Sun
     */
    val weekdayMask: Int,
    val popupEnabled: Boolean,
    val vibrationEnabled: Boolean,
    val soundEnabled: Boolean,
) {
    companion object {
        const val DefaultTime = "09:00"
        const val DefaultWeekdayMask = 0b1111111
    }
}

class NotificationPreferences(
    private val context: Context,
) {
    private val dailyEnabledKey = booleanPreferencesKey("daily_reminder_enabled")
    private val dailyTimeKey = stringPreferencesKey("daily_reminder_time_hhmm")
    private val dailyWeekdayMaskKey = intPreferencesKey("daily_reminder_weekday_mask")
    private val dailyPopupKey = booleanPreferencesKey("daily_reminder_popup_enabled")
    private val dailyVibrationKey = booleanPreferencesKey("daily_reminder_vibration_enabled")
    private val dailySoundKey = booleanPreferencesKey("daily_reminder_sound_enabled")

    val dailyReminder: Flow<DailyReminderSettings> =
        context.dataStore.data.map { prefs ->
            DailyReminderSettings(
                enabled = prefs[dailyEnabledKey] ?: false,
                timeHHmm = prefs[dailyTimeKey] ?: DailyReminderSettings.DefaultTime,
                weekdayMask = prefs[dailyWeekdayMaskKey] ?: DailyReminderSettings.DefaultWeekdayMask,
                popupEnabled = prefs[dailyPopupKey] ?: true,
                vibrationEnabled = prefs[dailyVibrationKey] ?: true,
                soundEnabled = prefs[dailySoundKey] ?: true,
            )
        }

    suspend fun setDailyEnabled(enabled: Boolean) {
        context.dataStore.edit { it[dailyEnabledKey] = enabled }
    }

    suspend fun setDailyTimeHHmm(hhmm: String) {
        context.dataStore.edit { it[dailyTimeKey] = hhmm.trim() }
    }

    suspend fun setDailyWeekdayMask(mask: Int) {
        context.dataStore.edit { it[dailyWeekdayMaskKey] = mask }
    }

    suspend fun setDailyPopupEnabled(enabled: Boolean) {
        context.dataStore.edit { it[dailyPopupKey] = enabled }
    }

    suspend fun setDailyVibrationEnabled(enabled: Boolean) {
        context.dataStore.edit { it[dailyVibrationKey] = enabled }
    }

    suspend fun setDailySoundEnabled(enabled: Boolean) {
        context.dataStore.edit { it[dailySoundKey] = enabled }
    }
}

