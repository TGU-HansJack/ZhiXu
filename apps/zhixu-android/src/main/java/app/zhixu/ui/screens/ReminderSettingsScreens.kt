package app.zhixu.ui.screens

import android.app.TimePickerDialog
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.zhixu.R
import app.zhixu.data.DailyReminderSettings
import app.zhixu.data.NotificationPreferences
import app.zhixu.reminders.DailyReminderWorker
import app.zhixu.ui.Ionicons
import app.zhixu.ui.ZhixuTopBarIconSize
import app.zhixu.ui.components.ZhixuIconButton
import app.zhixu.ui.components.ZhixuTopAppBar
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DailyReminderSettingsScreen(
    contentPadding: PaddingValues,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs = remember(context) { NotificationPreferences(context.applicationContext) }
    val settings by prefs.dailyReminder.collectAsState(
        initial =
            DailyReminderSettings(
                enabled = false,
                timeHHmm = DailyReminderSettings.DefaultTime,
                weekdayMask = DailyReminderSettings.DefaultWeekdayMask,
                popupEnabled = true,
                vibrationEnabled = true,
                soundEnabled = true,
            ),
    )

    fun parseHHmm(hhmm: String): Pair<Int, Int> {
        val parts = hhmm.trim().split(":")
        val h = parts.getOrNull(0)?.toIntOrNull() ?: 9
        val m = parts.getOrNull(1)?.toIntOrNull() ?: 0
        return h.coerceIn(0, 23) to m.coerceIn(0, 59)
    }

    fun formatHHmm(h: Int, m: Int): String = "${h.toString().padStart(2, '0')}:${m.toString().padStart(2, '0')}"

    fun toggleDay(mask: Int, bitIndex: Int): Int {
        val next = mask xor (1 shl bitIndex)
        return if (next == 0) mask else next
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            Column {
                ZhixuTopAppBar(
                    containerColor = MaterialTheme.colorScheme.surface,
                    title = { Text(stringResource(R.string.reminder_daily_title), style = MaterialTheme.typography.titleMedium) },
                    navigationIcon = {
                        ZhixuIconButton(onClick = onBack) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                                contentDescription = stringResource(R.string.action_back),
                                modifier = Modifier.size(ZhixuTopBarIconSize),
                            )
                        }
                    },
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
        },
    ) { innerPadding ->
        LazyColumn(
            modifier =
                Modifier
                    .padding(innerPadding)
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(0.dp),
        ) {
            item { Spacer(modifier = Modifier.height(12.dp)) }

            item {
                Text(
                    text = stringResource(R.string.settings_notifications_section_status),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 6.dp),
                )
            }

            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = MaterialTheme.shapes.extraLarge,
                ) {
                    ListItemRow(
                        title = stringResource(R.string.reminder_daily_enabled),
                        subtitle = stringResource(R.string.reminder_daily_enabled_subtitle),
                        trailing = {
                            Switch(
                                checked = settings.enabled,
                                onCheckedChange = { checked ->
                                    scope.launch {
                                        prefs.setDailyEnabled(checked)
                                        val next = settings.copy(enabled = checked)
                                        DailyReminderWorker.scheduleNext(context.applicationContext, next)
                                    }
                                },
                            )
                        },
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
                    ListItemRow(
                        title = stringResource(R.string.reminder_daily_time),
                        subtitle = settings.timeHHmm,
                        enabled = settings.enabled,
                        onClick = {
                            val (h0, m0) = parseHHmm(settings.timeHHmm)
                            TimePickerDialog(
                                context,
                                { _, h, m ->
                                    val nextTime = formatHHmm(h, m)
                                    scope.launch {
                                        prefs.setDailyTimeHHmm(nextTime)
                                        DailyReminderWorker.scheduleNext(context.applicationContext, settings.copy(timeHHmm = nextTime))
                                    }
                                },
                                h0,
                                m0,
                                true,
                            ).show()
                        },
                    )
                }
            }

            item { Spacer(modifier = Modifier.height(14.dp)) }

            item {
                Text(
                    text = stringResource(R.string.reminder_daily_days),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 6.dp),
                )
            }

            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = MaterialTheme.shapes.extraLarge,
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        val days =
                            listOf(
                                0 to stringResource(R.string.weekday_mon),
                                1 to stringResource(R.string.weekday_tue),
                                2 to stringResource(R.string.weekday_wed),
                                3 to stringResource(R.string.weekday_thu),
                                4 to stringResource(R.string.weekday_fri),
                                5 to stringResource(R.string.weekday_sat),
                                6 to stringResource(R.string.weekday_sun),
                            )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            for ((idx, label) in days.take(4)) {
                                val selected = ((settings.weekdayMask shr idx) and 1) == 1
                                FilterChip(
                                    selected = selected,
                                    onClick = {
                                        if (!settings.enabled) return@FilterChip
                                        val nextMask = toggleDay(settings.weekdayMask, idx)
                                        scope.launch {
                                            prefs.setDailyWeekdayMask(nextMask)
                                            DailyReminderWorker.scheduleNext(context.applicationContext, settings.copy(weekdayMask = nextMask))
                                        }
                                    },
                                    enabled = settings.enabled,
                                    label = { Text(label, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal) },
                                )
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            for ((idx, label) in days.drop(4)) {
                                val selected = ((settings.weekdayMask shr idx) and 1) == 1
                                FilterChip(
                                    selected = selected,
                                    onClick = {
                                        if (!settings.enabled) return@FilterChip
                                        val nextMask = toggleDay(settings.weekdayMask, idx)
                                        scope.launch {
                                            prefs.setDailyWeekdayMask(nextMask)
                                            DailyReminderWorker.scheduleNext(context.applicationContext, settings.copy(weekdayMask = nextMask))
                                        }
                                    },
                                    enabled = settings.enabled,
                                    label = { Text(label, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal) },
                                )
                            }
                        }
                        Text(
                            text = stringResource(R.string.reminder_daily_days_hint),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReminderSoundSettingsScreen(
    contentPadding: PaddingValues,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs = remember(context) { NotificationPreferences(context.applicationContext) }
    val settings by prefs.dailyReminder.collectAsState(
        initial =
            DailyReminderSettings(
                enabled = false,
                timeHHmm = DailyReminderSettings.DefaultTime,
                weekdayMask = DailyReminderSettings.DefaultWeekdayMask,
                popupEnabled = true,
                vibrationEnabled = true,
                soundEnabled = true,
            ),
    )

    SimpleToggleSettingsScreen(
        title = stringResource(R.string.reminder_sound_title),
        rowTitle = stringResource(R.string.reminder_sound_enabled),
        rowSubtitle = stringResource(if (settings.soundEnabled) R.string.reminder_state_on else R.string.reminder_state_off),
        checked = settings.soundEnabled,
        onBack = onBack,
        onToggle = { checked ->
            scope.launch {
                prefs.setDailySoundEnabled(checked)
                DailyReminderWorker.scheduleNext(context.applicationContext, settings.copy(soundEnabled = checked))
            }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReminderVibrationSettingsScreen(
    contentPadding: PaddingValues,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs = remember(context) { NotificationPreferences(context.applicationContext) }
    val settings by prefs.dailyReminder.collectAsState(
        initial =
            DailyReminderSettings(
                enabled = false,
                timeHHmm = DailyReminderSettings.DefaultTime,
                weekdayMask = DailyReminderSettings.DefaultWeekdayMask,
                popupEnabled = true,
                vibrationEnabled = true,
                soundEnabled = true,
            ),
    )

    SimpleToggleSettingsScreen(
        title = stringResource(R.string.reminder_vibration_title),
        rowTitle = stringResource(R.string.reminder_vibration_enabled),
        rowSubtitle = stringResource(if (settings.vibrationEnabled) R.string.reminder_state_on else R.string.reminder_state_off),
        checked = settings.vibrationEnabled,
        onBack = onBack,
        onToggle = { checked ->
            scope.launch {
                prefs.setDailyVibrationEnabled(checked)
                DailyReminderWorker.scheduleNext(context.applicationContext, settings.copy(vibrationEnabled = checked))
            }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReminderPopupSettingsScreen(
    contentPadding: PaddingValues,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs = remember(context) { NotificationPreferences(context.applicationContext) }
    val settings by prefs.dailyReminder.collectAsState(
        initial =
            DailyReminderSettings(
                enabled = false,
                timeHHmm = DailyReminderSettings.DefaultTime,
                weekdayMask = DailyReminderSettings.DefaultWeekdayMask,
                popupEnabled = true,
                vibrationEnabled = true,
                soundEnabled = true,
            ),
    )

    SimpleToggleSettingsScreen(
        title = stringResource(R.string.reminder_popup_title),
        rowTitle = stringResource(R.string.reminder_popup_enabled),
        rowSubtitle = stringResource(if (settings.popupEnabled) R.string.reminder_state_on else R.string.reminder_state_off),
        checked = settings.popupEnabled,
        onBack = onBack,
        onToggle = { checked ->
            scope.launch {
                prefs.setDailyPopupEnabled(checked)
                DailyReminderWorker.scheduleNext(context.applicationContext, settings.copy(popupEnabled = checked))
            }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SimpleToggleSettingsScreen(
    title: String,
    rowTitle: String,
    rowSubtitle: String,
    checked: Boolean,
    onBack: () -> Unit,
    onToggle: (Boolean) -> Unit,
) {
    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            Column {
                ZhixuTopAppBar(
                    containerColor = MaterialTheme.colorScheme.surface,
                    title = { Text(title, style = MaterialTheme.typography.titleMedium) },
                    navigationIcon = {
                        ZhixuIconButton(onClick = onBack) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                                contentDescription = stringResource(R.string.action_back),
                                modifier = Modifier.size(ZhixuTopBarIconSize),
                            )
                        }
                    },
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
        },
    ) { innerPadding ->
        LazyColumn(
            modifier =
                Modifier
                    .padding(innerPadding)
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(0.dp),
        ) {
            item { Spacer(modifier = Modifier.height(12.dp)) }
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = MaterialTheme.shapes.extraLarge,
                ) {
                    ListItemRow(
                        title = rowTitle,
                        subtitle = rowSubtitle,
                        trailing = { Switch(checked = checked, onCheckedChange = onToggle) },
                    )
                }
            }
        }
    }
}

@Composable
private fun ListItemRow(
    title: String,
    subtitle: String,
    enabled: Boolean = true,
    onClick: (() -> Unit)? = null,
    trailing: @Composable (() -> Unit)? = null,
) {
    val alpha = if (enabled) 1f else 0.4f
    androidx.compose.material3.ListItem(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 6.dp)
                .alpha(alpha)
                .let { m -> if (onClick != null) m.clickable(enabled = enabled, onClick = onClick) else m },
        leadingContent = {
            Icon(
                painter = painterResource(Ionicons.NotificationsOutline),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
        },
        headlineContent = { Text(title) },
        supportingContent = { Text(subtitle) },
        trailingContent = trailing,
    )
}
