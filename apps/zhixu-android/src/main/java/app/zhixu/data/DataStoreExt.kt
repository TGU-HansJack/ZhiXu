package app.zhixu.data

import android.content.Context
import androidx.datastore.preferences.preferencesDataStore

internal val Context.dataStore by preferencesDataStore(name = "zhixu_prefs")

