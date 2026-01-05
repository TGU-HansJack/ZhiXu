package app.zhixu.ui.screens

import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import android.widget.NumberPicker
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import app.zhixu.pomodoro.PomodoroPreferences
import app.zhixu.ui.Ionicons
import app.zhixu.ui.ZhixuTopBarIconSize
import app.zhixu.ui.components.ZhixuIconButton
import app.zhixu.ui.components.ZhixuTopAppBar
import kotlinx.coroutines.launch

private enum class PomodoroNumberTarget {
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
    val settings by prefs.settings.collectAsState(initial = app.zhixu.pomodoro.PomodoroSettings())
    val dividerColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)

    var numberTarget by remember { mutableStateOf<PomodoroNumberTarget?>(null) }
    var numberValue by remember { mutableIntStateOf(0) }

    val ringtonePicker =
        rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            val uri: Uri? = result.data?.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
            scope.launch { prefs.updateRingtoneUri(uri?.toString().orEmpty()) }
        }

    fun openNumberPicker(target: PomodoroNumberTarget, initial: Int) {
        numberTarget = target
        numberValue = initial
    }

    fun openRingtonePicker() {
        val existing = runCatching { settings.ringtoneUri.takeIf { it.isNotBlank() }?.let(Uri::parse) }.getOrNull()
        val intent =
            Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
                putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_NOTIFICATION)
                putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
                putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, true)
                putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, existing)
            }
        ringtonePicker.launch(intent)
    }

    fun openTomatoProjectPage() {
        val url = "https://github.com/nsh07/Tomato"
        runCatching {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            Column {
                ZhixuTopAppBar(
                    containerColor = MaterialTheme.colorScheme.surface,
                    title = { Text(text = "番茄设置", style = MaterialTheme.typography.titleMedium) },
                    navigationIcon = {
                        ZhixuIconButton(onClick = onBack) {
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
        Column(
            modifier =
                Modifier
                    .padding(inner)
                    .padding(contentPadding)
                    .background(MaterialTheme.colorScheme.background)
                    .fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(0.dp),
        ) {
            Text(
                text = "计时设置",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            )
            ListItem(
                headlineContent = { Text("专注时长") },
                supportingContent = { Text("${settings.focusMinutes} 分钟") },
                modifier =
                    Modifier.fillMaxWidth().clickable { openNumberPicker(PomodoroNumberTarget.FocusMinutes, settings.focusMinutes) },
            )
            HorizontalDivider(color = dividerColor, modifier = Modifier.padding(horizontal = 16.dp))
            ListItem(
                headlineContent = { Text("短休息") },
                supportingContent = { Text("${settings.shortBreakMinutes} 分钟") },
                modifier =
                    Modifier.fillMaxWidth().clickable { openNumberPicker(PomodoroNumberTarget.ShortBreakMinutes, settings.shortBreakMinutes) },
            )
            HorizontalDivider(color = dividerColor, modifier = Modifier.padding(horizontal = 16.dp))
            ListItem(
                headlineContent = { Text("长休息") },
                supportingContent = { Text("${settings.longBreakMinutes} 分钟") },
                modifier = Modifier.fillMaxWidth().clickable { openNumberPicker(PomodoroNumberTarget.LongBreakMinutes, settings.longBreakMinutes) },
            )
            HorizontalDivider(color = dividerColor, modifier = Modifier.padding(horizontal = 16.dp))
            ListItem(
                headlineContent = { Text("每隔几个番茄长休息") },
                supportingContent = { Text("${settings.longBreakEvery} 次") },
                modifier = Modifier.fillMaxWidth().clickable { openNumberPicker(PomodoroNumberTarget.LongBreakEvery, settings.longBreakEvery) },
            )

            HorizontalDivider(color = dividerColor, modifier = Modifier.padding(horizontal = 16.dp))

            Text(
                text = "响铃设置",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            )
            ListItem(
                headlineContent = { Text("响铃") },
                supportingContent = { Text(if (settings.soundEnabled) "开启" else "关闭") },
                trailingContent = {
                    Switch(
                        checked = settings.soundEnabled,
                        onCheckedChange = { checked -> scope.launch { prefs.updateSoundEnabled(checked) } },
                    )
                },
                modifier = Modifier.fillMaxWidth(),
            )
            ListItem(
                headlineContent = { Text("震动") },
                supportingContent = { Text(if (settings.vibrationEnabled) "开启" else "关闭") },
                trailingContent = {
                    Switch(
                        checked = settings.vibrationEnabled,
                        onCheckedChange = { checked -> scope.launch { prefs.updateVibrationEnabled(checked) } },
                    )
                },
                modifier = Modifier.fillMaxWidth(),
            )
            ListItem(
                headlineContent = { Text("铃声") },
                supportingContent = {
                    val name =
                        if (settings.ringtoneUri.isBlank()) {
                            "系统默认"
                        } else {
                            runCatching {
                                val uri = Uri.parse(settings.ringtoneUri)
                                RingtoneManager.getRingtone(context, uri)?.getTitle(context)
                            }.getOrNull().orEmpty().ifBlank { "已选择" }
                        }
                    Text(name)
                },
                trailingContent = {
                    Icon(painter = painterResource(Ionicons.ChevronForward), contentDescription = null)
                },
                modifier =
                    Modifier.fillMaxWidth().clickable(onClick = ::openRingtonePicker),
            )

            HorizontalDivider(color = dividerColor, modifier = Modifier.padding(horizontal = 16.dp))

            Text(
                text = "项目作者",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            )
            ListItem(
                headlineContent = { Text("Tomato") },
                supportingContent = { Text("作者：nsh07（GitHub）") },
                trailingContent = { Icon(painter = painterResource(Ionicons.ChevronForward), contentDescription = null) },
                modifier =
                    Modifier.fillMaxWidth().clickable(onClick = ::openTomatoProjectPage),
            )
        }
    }

    when (numberTarget) {
        null -> Unit
        else -> {
            val target = numberTarget ?: return@PomodoroSettingsScreen
            val (title, min, max) =
                when (target) {
                    PomodoroNumberTarget.FocusMinutes -> Quad("专注时长", 1, 180)
                    PomodoroNumberTarget.ShortBreakMinutes -> Quad("短休息", 1, 60)
                    PomodoroNumberTarget.LongBreakMinutes -> Quad("长休息", 1, 180)
                    PomodoroNumberTarget.LongBreakEvery -> Quad("长休息间隔", 1, 12)
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
                        update = { picker ->
                            picker.value = numberValue.coerceIn(min, max)
                        },
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            val v = numberValue.coerceIn(min, max)
                            scope.launch {
                                when (target) {
                                    PomodoroNumberTarget.FocusMinutes -> prefs.updateFocusMinutes(v)
                                    PomodoroNumberTarget.ShortBreakMinutes -> prefs.updateShortBreakMinutes(v)
                                    PomodoroNumberTarget.LongBreakMinutes -> prefs.updateLongBreakMinutes(v)
                                    PomodoroNumberTarget.LongBreakEvery -> prefs.updateLongBreakEvery(v)
                                }
                            }
                            numberTarget = null
                        },
                    ) {
                        Text("确定")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { numberTarget = null }) { Text("取消") }
                },
            )
        }
    }
}

private data class Quad(val title: String, val min: Int, val max: Int)
