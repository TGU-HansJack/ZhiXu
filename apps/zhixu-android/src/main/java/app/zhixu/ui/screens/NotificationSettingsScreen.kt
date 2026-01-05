package app.zhixu.ui.screens

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import app.zhixu.R
import app.zhixu.reminders.TaskReminderWorker
import app.zhixu.ui.Ionicons
import app.zhixu.ui.ZhixuTopBarIconSize
import app.zhixu.ui.components.ZhixuIconButton
import app.zhixu.ui.components.ZhixuTopAppBar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationSettingsScreen(
    contentPadding: PaddingValues,
    onBack: () -> Unit,
    onOpenDailyReminder: () -> Unit,
    onOpenReminderSound: () -> Unit,
    onOpenReminderVibration: () -> Unit,
    onOpenReminderPopup: () -> Unit,
) {
    val context = LocalContext.current
    var refreshTick by remember { mutableIntStateOf(0) }

    val permissionLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.RequestPermission(),
            onResult = { refreshTick++ },
        )

    LaunchedEffect(Unit) {
        runCatching { ensureTaskReminderChannel(context.applicationContext) }
    }

    fun isPostNotificationsGranted(): Boolean {
        if (Build.VERSION.SDK_INT < 33) return true
        return ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    }

    fun openAppNotificationSettings() {
        val appContext = context.applicationContext
        val intent =
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, appContext.packageName)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }.onFailure {
            val fallback =
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                    .setData(Uri.fromParts("package", appContext.packageName, null))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            runCatching { context.startActivity(fallback) }.onFailure {
                Toast.makeText(context, "无法打开系统设置", Toast.LENGTH_SHORT).show()
            }
        }
    }

    val appNotificationsEnabled = remember(refreshTick) { NotificationManagerCompat.from(context).areNotificationsEnabled() }
    val postNotificationsGranted = remember(refreshTick) { isPostNotificationsGranted() }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            Column {
                ZhixuTopAppBar(
                    containerColor = MaterialTheme.colorScheme.surface,
                    title = { Text(stringResource(R.string.settings_notifications_title), style = MaterialTheme.typography.titleMedium) },
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
                    .background(MaterialTheme.colorScheme.background)
                    .fillMaxSize()
                    .imePadding(),
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
                    SettingsRow(
                        iconRes = Ionicons.NotificationsOutline,
                        title = stringResource(R.string.settings_notifications_row_permission),
                        subtitle =
                            when {
                                !appNotificationsEnabled -> stringResource(R.string.settings_notifications_state_off)
                                postNotificationsGranted -> stringResource(R.string.settings_notifications_state_on)
                                else -> stringResource(R.string.settings_notifications_state_need_permission)
                            },
                        onClick = {
                            if (!appNotificationsEnabled || !postNotificationsGranted) {
                                openAppNotificationSettings()
                                return@SettingsRow
                            }
                            Toast.makeText(context, "已具备通知权限", Toast.LENGTH_SHORT).show()
                            refreshTick++
                        },
                    )
                }
            }

            item { Spacer(modifier = Modifier.height(14.dp)) }

            item {
                Text(
                    text = stringResource(R.string.settings_notifications_section_reminders),
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
                    SettingsRow(
                        iconRes = Ionicons.TodayOutline,
                        title = stringResource(R.string.reminder_daily_title),
                        subtitle = stringResource(R.string.reminder_daily_subtitle),
                        onClick = onOpenDailyReminder,
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
                    SettingsRow(
                        iconRes = Ionicons.SettingsOutline,
                        title = stringResource(R.string.reminder_sound_title),
                        subtitle = stringResource(R.string.reminder_sound_subtitle),
                        onClick = onOpenReminderSound,
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
                    SettingsRow(
                        iconRes = Ionicons.RefreshOutline,
                        title = stringResource(R.string.reminder_vibration_title),
                        subtitle = stringResource(R.string.reminder_vibration_subtitle),
                        onClick = onOpenReminderVibration,
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
                    SettingsRow(
                        iconRes = Ionicons.NotificationsOutline,
                        title = stringResource(R.string.reminder_popup_title),
                        subtitle = stringResource(R.string.reminder_popup_subtitle),
                        onClick = onOpenReminderPopup,
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingsRow(
    iconRes: Int,
    title: String,
    subtitle: String,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val alpha = if (enabled) 1f else 0.4f
    ListItem(
        modifier =
            Modifier
                .fillMaxWidth()
                .alpha(alpha)
                .clickable(enabled = enabled, onClick = onClick),
        leadingContent = {
            Icon(
                painter = painterResource(iconRes),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
        },
        headlineContent = { Text(title) },
        supportingContent = { Text(subtitle, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        trailingContent = {
            Icon(
                painter = painterResource(Ionicons.ChevronForward),
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
        },
    )
}

private fun ensureTaskReminderChannel(context: Context) {
    if (Build.VERSION.SDK_INT < 26) return
    val mgr = context.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
    val existing = mgr.getNotificationChannel(TaskReminderWorker.CHANNEL_ID)
    if (existing != null) return
    mgr.createNotificationChannel(
        android.app.NotificationChannel(
            TaskReminderWorker.CHANNEL_ID,
            context.getString(R.string.reminder_channel_name),
            android.app.NotificationManager.IMPORTANCE_HIGH,
        ),
    )
}
