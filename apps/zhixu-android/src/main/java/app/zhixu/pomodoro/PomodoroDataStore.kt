package app.zhixu.pomodoro

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore

internal val Context.pomodoroDataStore: DataStore<Preferences> by preferencesDataStore(name = "pomodoro")

