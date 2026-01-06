package app.zhixu.ui.screens

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import android.provider.Settings
import android.widget.NumberPicker
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import app.zhixu.pomodoro.PomodoroPreferences
import app.zhixu.pomodoro.PomodoroService
import app.zhixu.pomodoro.PomodoroSettings
import app.zhixu.pomodoro.PomodoroStatsRepository
import app.zhixu.pomodoro.PomodoroTimerStateStore
import app.zhixu.ui.Ionicons
import app.zhixu.ui.ZhixuTopBarIconSize
import app.zhixu.ui.components.ZhixuIconButton
import app.zhixu.ui.components.ZhixuTopAppBar
import kotlinx.coroutines.launch

private enum class PomodoroSettingsPage {
    Main,
    Timer,
    Alarm,
    About,
}

private enum class NumberTarget {
    FocusMinutes,
    ShortBreakMinutes,
    LongBreakMinutes,
    LongBreakEvery,
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PomodoroSettingsScreen(
    contentPadding: PaddingValues,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val prefs = remember(context) { PomodoroPreferences(context.applicationContext) }
    val settings by prefs.settings.collectAsState(initial = PomodoroSettings())
    val statsRepo = remember(context) { PomodoroStatsRepository(context.applicationContext) }
    val timerStore = remember(context) { PomodoroTimerStateStore(context.applicationContext) }
    val timerSnapshot by timerStore.snapshot.collectAsState(initial = app.zhixu.pomodoro.PomodoroTimerSnapshot())

    var page by remember { mutableStateOf(PomodoroSettingsPage.Main) }
    var numberTarget by remember { mutableStateOf<NumberTarget?>(null) }
    var numberValue by remember { mutableIntStateOf(0) }
    var showResetStatsDialog by remember { mutableStateOf(false) }
    var showResetTimerDialog by remember { mutableStateOf(false) }

    val dividerColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)

    BackHandler(enabled = page != PomodoroSettingsPage.Main) {
        page = PomodoroSettingsPage.Main
    }

    fun back() {
        if (page == PomodoroSettingsPage.Main) onBack() else page = PomodoroSettingsPage.Main
    }

    val ringtonePicker =
        rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            @Suppress("DEPRECATION")
            val uri: Uri? = result.data?.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
            scope.launch { prefs.updateRingtoneUri(uri?.toString().orEmpty()) }
        }

    fun openRingtonePicker() {
        val existing = runCatching { settings.ringtoneUri.takeIf { it.isNotBlank() }?.let(Uri::parse) }.getOrNull()
        val intent =
            Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
                putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALARM)
                putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
                putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, true)
                putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, existing)
            }
        ringtonePicker.launch(intent)
    }

    fun openNumberPicker(target: NumberTarget, initial: Int) {
        numberTarget = target
        numberValue = initial
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            Column {
                ZhixuTopAppBar(
                    containerColor = MaterialTheme.colorScheme.surface,
                    title = {
                        Text(
                            text =
                                when (page) {
                                    PomodoroSettingsPage.Main -> "番茄设置"
                                    PomodoroSettingsPage.Timer -> "计时设置"
                                    PomodoroSettingsPage.Alarm -> "响铃设置"
                                    PomodoroSettingsPage.About -> "关于"
                                },
                            style = MaterialTheme.typography.titleMedium,
                        )
                    },
                    navigationIcon = {
                        ZhixuIconButton(onClick = ::back) {
                            Icon(
                                painter = painterResource(Ionicons.ArrowBack),
                                contentDescription = null,
                                modifier = Modifier.size(ZhixuTopBarIconSize),
                            )
                        }
                    },
                )
                HorizontalDivider(color = dividerColor)
            }
        },
    ) { inner ->
        AnimatedContent(
            targetState = page,
            transitionSpec = {
                slideInHorizontally(initialOffsetX = { it })
                    .togetherWith(slideOutHorizontally(targetOffsetX = { -it / 4 }))
            },
        ) { current ->
            when (current) {
                PomodoroSettingsPage.Main ->
                    PomodoroSettingsMainPage(
                        contentPadding = contentPadding,
                        innerPadding = inner,
                        dividerColor = dividerColor,
                        onOpenTimer = { page = PomodoroSettingsPage.Timer },
                        onOpenAlarm = { page = PomodoroSettingsPage.Alarm },
                        onOpenAbout = { page = PomodoroSettingsPage.About },
                        onResetStats = { showResetStatsDialog = true },
                        onResetTimer = { showResetTimerDialog = true },
                    )

                PomodoroSettingsPage.Timer ->
                    PomodoroTimerSettingsPage(
                        contentPadding = contentPadding,
                        innerPadding = inner,
                        dividerColor = dividerColor,
                        settings = settings,
                        timerRunning = timerSnapshot.isRunning,
                        onPickNumber = ::openNumberPicker,
                        onToggleAutostart = { scope.launch { prefs.updateAutostartNextSession(it) } },
                        onToggleDnd = { checked ->
                            if (checked) requestDndPermissionIfNeeded(context)
                            scope.launch { prefs.updateDndEnabled(checked) }
                        },
                        onToggleSingleProgress = { scope.launch { prefs.updateSingleProgressBar(it) } },
                        onToggleAod = { scope.launch { prefs.updateAodEnabled(it) } },
                        onToggleSecureAod = { scope.launch { prefs.updateSecureAod(it) } },
                    )

                PomodoroSettingsPage.Alarm ->
                    PomodoroAlarmSettingsPage(
                        contentPadding = contentPadding,
                        innerPadding = inner,
                        dividerColor = dividerColor,
                        settings = settings,
                        onPickRingtone = ::openRingtonePicker,
                        onToggleAlarm = { scope.launch { prefs.updateAlarmEnabled(it) } },
                        onToggleVibrate = { scope.launch { prefs.updateVibrateEnabled(it) } },
                        onToggleMediaVolume = { scope.launch { prefs.updateMediaVolumeForAlarm(it) } },
                    )

                PomodoroSettingsPage.About ->
                    PomodoroAboutPage(
                        contentPadding = contentPadding,
                        innerPadding = inner,
                        dividerColor = dividerColor,
                    )
            }
        }
    }

    val target = numberTarget
    if (target != null) {
        val (title, min, max, apply) =
            when (target) {
                NumberTarget.FocusMinutes -> Quad("专注时长", 1, 180) { v -> prefs.updateFocusMinutes(v) }
                NumberTarget.ShortBreakMinutes -> Quad("短休息", 1, 60) { v -> prefs.updateShortBreakMinutes(v) }
                NumberTarget.LongBreakMinutes -> Quad("长休息", 1, 180) { v -> prefs.updateLongBreakMinutes(v) }
                NumberTarget.LongBreakEvery -> Quad("每隔几个番茄长休息", 1, 12) { v -> prefs.updateLongBreakEvery(v) }
            }
        AlertDialog(
            onDismissRequest = { numberTarget = null },
            title = { Text(title) },
            text = {
                AndroidView(
                    factory = { ctx ->
                        NumberPicker(ctx).apply {
                            minValue = min
                            maxValue = max
                            wrapSelectorWheel = true
                            value = numberValue.coerceIn(min, max)
                            setOnValueChangedListener { _, _, new -> numberValue = new }
                        }
                    },
                    update = { it.value = numberValue.coerceIn(min, max) },
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val v = numberValue.coerceIn(min, max)
                        scope.launch { apply(v) }
                        numberTarget = null
                    },
                ) { Text("确定") }
            },
            dismissButton = { TextButton(onClick = { numberTarget = null }) { Text("取消") } },
        )
    }

    if (showResetStatsDialog) {
        AlertDialog(
            onDismissRequest = { showResetStatsDialog = false },
            title = { Text("清除统计数据？") },
            text = { Text("此操作会清除番茄统计历史（无法恢复）。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showResetStatsDialog = false
                        scope.launch { statsRepo.deleteAllStats() }
                    },
                ) { Text("清除") }
            },
            dismissButton = { TextButton(onClick = { showResetStatsDialog = false }) { Text("取消") } },
        )
    }

    if (showResetTimerDialog) {
        AlertDialog(
            onDismissRequest = { showResetTimerDialog = false },
            title = { Text("重置计时状态？") },
            text = { Text("会停止当前计时，并清除“撤销重置”的记录。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showResetTimerDialog = false
                        PomodoroService.startAction(context, PomodoroService.Actions.CLEAR_STATE)
                    },
                ) { Text("重置") }
            },
            dismissButton = { TextButton(onClick = { showResetTimerDialog = false }) { Text("取消") } },
        )
    }
}

@Composable
private fun PomodoroSettingsMainPage(
    contentPadding: PaddingValues,
    innerPadding: PaddingValues,
    dividerColor: androidx.compose.ui.graphics.Color,
    onOpenTimer: () -> Unit,
    onOpenAlarm: () -> Unit,
    onOpenAbout: () -> Unit,
    onResetStats: () -> Unit,
    onResetTimer: () -> Unit,
) {
    LazyColumn(
        modifier =
            Modifier
                .padding(innerPadding)
                .windowInsetsPadding(WindowInsets.navigationBars)
                .background(MaterialTheme.colorScheme.background)
                .fillMaxSize(),
        contentPadding =
            PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = 4.dp,
                bottom = 16.dp + contentPadding.calculateBottomPadding(),
            ),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = MaterialTheme.shapes.extraLarge,
            ) {
                Column {
                    ListItem(
                        leadingContent = { Icon(painter = painterResource(Ionicons.TimeOutline), contentDescription = null) },
                        headlineContent = { Text("计时") },
                        supportingContent = { Text("时长、自动开始、勿扰、常亮等") },
                        trailingContent = { Icon(painter = painterResource(Ionicons.ChevronForward), contentDescription = null) },
                        modifier = Modifier.fillMaxWidth().clickable(onClick = onOpenTimer),
                    )
                    HorizontalDivider(color = dividerColor)
                    ListItem(
                        leadingContent = { Icon(painter = painterResource(Ionicons.NotificationsOutline), contentDescription = null) },
                        headlineContent = { Text("响铃") },
                        supportingContent = { Text("铃声、震动、媒体音量") },
                        trailingContent = { Icon(painter = painterResource(Ionicons.ChevronForward), contentDescription = null) },
                        modifier = Modifier.fillMaxWidth().clickable(onClick = onOpenAlarm),
                    )
                    HorizontalDivider(color = dividerColor)
                    ListItem(
                        leadingContent = { Icon(painter = painterResource(Ionicons.HelpCircleOutline), contentDescription = null) },
                        headlineContent = { Text("关于") },
                        supportingContent = { Text("Tomato 项目来源与作者") },
                        trailingContent = { Icon(painter = painterResource(Ionicons.ChevronForward), contentDescription = null) },
                        modifier = Modifier.fillMaxWidth().clickable(onClick = onOpenAbout),
                    )
                }
            }
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = MaterialTheme.shapes.extraLarge,
            ) {
                Column {
                    ListItem(
                        leadingContent = { Icon(painter = painterResource(Ionicons.TrashOutline), contentDescription = null) },
                        headlineContent = { Text("清除统计数据") },
                        supportingContent = { Text("删除历史专注/休息统计") },
                        modifier = Modifier.fillMaxWidth().clickable(onClick = onResetStats),
                    )
                    HorizontalDivider(color = dividerColor)
                    ListItem(
                        leadingContent = { Icon(painter = painterResource(Ionicons.RefreshOutline), contentDescription = null) },
                        headlineContent = { Text("重置计时状态") },
                        supportingContent = { Text("停止计时并清除撤销记录") },
                        modifier = Modifier.fillMaxWidth().clickable(onClick = onResetTimer),
                    )
                }
            }
        }
    }
}

@Composable
private fun PomodoroTimerSettingsPage(
    contentPadding: PaddingValues,
    innerPadding: PaddingValues,
    dividerColor: androidx.compose.ui.graphics.Color,
    settings: PomodoroSettings,
    timerRunning: Boolean,
    onPickNumber: (NumberTarget, Int) -> Unit,
    onToggleAutostart: (Boolean) -> Unit,
    onToggleDnd: (Boolean) -> Unit,
    onToggleSingleProgress: (Boolean) -> Unit,
    onToggleAod: (Boolean) -> Unit,
    onToggleSecureAod: (Boolean) -> Unit,
) {
    LazyColumn(
        modifier =
            Modifier
                .padding(innerPadding)
                .windowInsetsPadding(WindowInsets.navigationBars)
                .background(MaterialTheme.colorScheme.background)
                .fillMaxSize(),
        contentPadding =
            PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = 4.dp,
                bottom = 16.dp + contentPadding.calculateBottomPadding(),
            ),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = MaterialTheme.shapes.extraLarge,
            ) {
                Column {
                    ListItem(
                        headlineContent = { Text("专注") },
                        supportingContent = { Text("${settings.focusMinutes} 分钟") },
                        trailingContent = { Icon(painter = painterResource(Ionicons.ChevronForward), contentDescription = null) },
                        modifier = Modifier.fillMaxWidth().clickable { onPickNumber(NumberTarget.FocusMinutes, settings.focusMinutes) },
                    )
                    HorizontalDivider(color = dividerColor)
                    ListItem(
                        headlineContent = { Text("短休息") },
                        supportingContent = { Text("${settings.shortBreakMinutes} 分钟") },
                        trailingContent = { Icon(painter = painterResource(Ionicons.ChevronForward), contentDescription = null) },
                        modifier = Modifier.fillMaxWidth().clickable { onPickNumber(NumberTarget.ShortBreakMinutes, settings.shortBreakMinutes) },
                    )
                    HorizontalDivider(color = dividerColor)
                    ListItem(
                        headlineContent = { Text("长休息") },
                        supportingContent = { Text("${settings.longBreakMinutes} 分钟") },
                        trailingContent = { Icon(painter = painterResource(Ionicons.ChevronForward), contentDescription = null) },
                        modifier = Modifier.fillMaxWidth().clickable { onPickNumber(NumberTarget.LongBreakMinutes, settings.longBreakMinutes) },
                    )
                    HorizontalDivider(color = dividerColor)
                    ListItem(
                        headlineContent = { Text("每隔几个番茄长休息") },
                        supportingContent = { Text("${settings.longBreakEvery} 次") },
                        trailingContent = { Icon(painter = painterResource(Ionicons.ChevronForward), contentDescription = null) },
                        modifier = Modifier.fillMaxWidth().clickable { onPickNumber(NumberTarget.LongBreakEvery, settings.longBreakEvery) },
                    )
                }
            }
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = MaterialTheme.shapes.extraLarge,
            ) {
                Column {
                    ListItem(
                        headlineContent = { Text("自动开始下一阶段") },
                        trailingContent = { Switch(checked = settings.autostartNextSession, onCheckedChange = onToggleAutostart) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    HorizontalDivider(color = dividerColor)
                    ListItem(
                        headlineContent = { Text("勿扰模式（仅专注时）") },
                        supportingContent = {
                            val nm = LocalContext.current.getSystemService(NotificationManager::class.java)
                            val granted = nm?.isNotificationPolicyAccessGranted == true
                            val msg =
                                if (!settings.dndEnabled) "关闭"
                                else if (granted) "开启"
                                else "需要授权"
                            Text(msg)
                        },
                        trailingContent = { Switch(checked = settings.dndEnabled, enabled = !timerRunning, onCheckedChange = onToggleDnd) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    HorizontalDivider(color = dividerColor)
                    ListItem(
                        headlineContent = { Text("单一进度条") },
                        supportingContent = { Text(if (settings.singleProgressBar) "显示单段进度" else "显示本轮进度") },
                        trailingContent = { Switch(checked = settings.singleProgressBar, onCheckedChange = onToggleSingleProgress) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    HorizontalDivider(color = dividerColor)
                    ListItem(
                        headlineContent = { Text("计时时保持屏幕常亮") },
                        trailingContent = { Switch(checked = settings.aodEnabled, onCheckedChange = onToggleAod) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    HorizontalDivider(color = dividerColor)
                    ListItem(
                        headlineContent = { Text("常亮界面防截图（Secure）") },
                        supportingContent = { Text("开启后会禁止系统截图/录屏") },
                        trailingContent = { Switch(checked = settings.secureAod, enabled = settings.aodEnabled, onCheckedChange = onToggleSecureAod) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

@Composable
private fun PomodoroAlarmSettingsPage(
    contentPadding: PaddingValues,
    innerPadding: PaddingValues,
    dividerColor: androidx.compose.ui.graphics.Color,
    settings: PomodoroSettings,
    onPickRingtone: () -> Unit,
    onToggleAlarm: (Boolean) -> Unit,
    onToggleVibrate: (Boolean) -> Unit,
    onToggleMediaVolume: (Boolean) -> Unit,
) {
    val context = LocalContext.current
    LazyColumn(
        modifier =
            Modifier
                .padding(innerPadding)
                .windowInsetsPadding(WindowInsets.navigationBars)
                .background(MaterialTheme.colorScheme.background)
                .fillMaxSize(),
        contentPadding =
            PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = 4.dp,
                bottom = 16.dp + contentPadding.calculateBottomPadding(),
            ),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = MaterialTheme.shapes.extraLarge,
            ) {
                Column {
                    ListItem(
                        headlineContent = { Text("响铃") },
                        trailingContent = { Switch(checked = settings.alarmEnabled, onCheckedChange = onToggleAlarm) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    HorizontalDivider(color = dividerColor)
                    ListItem(
                        headlineContent = { Text("震动") },
                        trailingContent = { Switch(checked = settings.vibrateEnabled, onCheckedChange = onToggleVibrate) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    HorizontalDivider(color = dividerColor)
                    ListItem(
                        headlineContent = { Text("使用媒体音量播放") },
                        supportingContent = { Text("可选") },
                        trailingContent = { Switch(checked = settings.mediaVolumeForAlarm, onCheckedChange = onToggleMediaVolume) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }

        item {
            val name =
                if (settings.ringtoneUri.isBlank()) {
                    "系统默认"
                } else {
                    runCatching {
                        val uri = Uri.parse(settings.ringtoneUri)
                        RingtoneManager.getRingtone(context, uri)?.getTitle(context)
                    }.getOrNull().orEmpty().ifBlank { "已选择" }
                }
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = MaterialTheme.shapes.extraLarge,
            ) {
                ListItem(
                    headlineContent = { Text("铃声") },
                    supportingContent = { Text(name) },
                    trailingContent = { Icon(painter = painterResource(Ionicons.ChevronForward), contentDescription = null) },
                    modifier = Modifier.fillMaxWidth().clickable(onClick = onPickRingtone),
                )
            }
        }
    }
}

@Composable
private fun PomodoroAboutPage(
    contentPadding: PaddingValues,
    innerPadding: PaddingValues,
    dividerColor: androidx.compose.ui.graphics.Color,
) {
    val context = LocalContext.current
    LazyColumn(
        modifier =
            Modifier
                .padding(innerPadding)
                .windowInsetsPadding(WindowInsets.navigationBars)
                .background(MaterialTheme.colorScheme.background)
                .fillMaxSize(),
        contentPadding =
            PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = 4.dp,
                bottom = 16.dp + contentPadding.calculateBottomPadding(),
            ),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = MaterialTheme.shapes.extraLarge,
            ) {
                ListItem(
                    headlineContent = { Text("Tomato") },
                    supportingContent = { Text("作者：nsh07（GitHub）") },
                    trailingContent = { Icon(painter = painterResource(Ionicons.ChevronForward), contentDescription = null) },
                    modifier =
                        Modifier.fillMaxWidth().clickable {
                            val url = "https://github.com/nsh07/Tomato"
                            runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                        },
                )
            }
        }
        item {
            Text(
                text = "说明：番茄功能为独立实现（clean-room），用于对齐交互与布局风格，未直接拷贝 Tomato 的 GPL 源码。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
        }
    }
}

private data class Quad(
    val title: String,
    val min: Int,
    val max: Int,
    val apply: suspend (Int) -> Unit,
)

private fun requestDndPermissionIfNeeded(context: Context) {
    val nm = context.getSystemService(NotificationManager::class.java) ?: return
    if (nm.isNotificationPolicyAccessGranted) return
    runCatching {
        context.startActivity(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
