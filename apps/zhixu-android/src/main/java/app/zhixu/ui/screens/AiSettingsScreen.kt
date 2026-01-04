package app.zhixu.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import app.zhixu.data.AiPreferences
import app.zhixu.ui.Ionicons
import app.zhixu.ui.ZhixuTopBarIconSize
import app.zhixu.ui.components.ZhixuIconButton
import app.zhixu.ui.components.ZhixuSwitch
import app.zhixu.ui.components.ZhixuTextField
import app.zhixu.ui.components.ZhixuTopAppBar
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiSettingsScreen(
    contentPadding: PaddingValues,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val prefs = remember(context) { AiPreferences(context.applicationContext) }
    val scope = rememberCoroutineScope()

    val stateOrNull by prefs.state.map { it as AiPreferences.State? }.collectAsState(initial = null)

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            Column {
                ZhixuTopAppBar(
                    containerColor = MaterialTheme.colorScheme.surface,
                    title = { Text("AI 设置", style = MaterialTheme.typography.titleMedium) },
                    navigationIcon = {
                        val isRtl = LocalLayoutDirection.current == LayoutDirection.Rtl
                        ZhixuIconButton(onClick = onBack) {
                            Icon(
                                painter = painterResource(if (isRtl) Ionicons.ArrowForward else Ionicons.ArrowBack),
                                contentDescription = null,
                                modifier = Modifier.size(ZhixuTopBarIconSize),
                            )
                        }
                    },
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
        },
    ) { innerPadding ->
        val state = stateOrNull
        if (state == null) {
            Box(
                modifier =
                    Modifier
                        .padding(innerPadding)
                        .fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        var baseUrlDraft by rememberSaveable { mutableStateOf("") }
        var modelDraft by rememberSaveable { mutableStateOf("") }
        var apiKeyDraft by rememberSaveable { mutableStateOf("") }

        var lastSyncedBaseUrl by remember { mutableStateOf<String?>(null) }
        var lastSyncedModel by remember { mutableStateOf<String?>(null) }
        var lastSyncedApiKey by remember { mutableStateOf<String?>(null) }

        LaunchedEffect(state.baseUrl) {
            if (lastSyncedBaseUrl == null || baseUrlDraft == lastSyncedBaseUrl) baseUrlDraft = state.baseUrl
            lastSyncedBaseUrl = state.baseUrl
        }
        LaunchedEffect(state.model) {
            if (lastSyncedModel == null || modelDraft == lastSyncedModel) modelDraft = state.model
            lastSyncedModel = state.model
        }
        LaunchedEffect(state.apiKey) {
            if (lastSyncedApiKey == null || apiKeyDraft == lastSyncedApiKey) apiKeyDraft = state.apiKey
            lastSyncedApiKey = state.apiKey
        }

        var baseUrlSaveJob by remember { mutableStateOf<Job?>(null) }
        var modelSaveJob by remember { mutableStateOf<Job?>(null) }
        var apiKeySaveJob by remember { mutableStateOf<Job?>(null) }

        LazyColumn(
            modifier =
                Modifier
                    .padding(innerPadding)
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .background(MaterialTheme.colorScheme.background)
                    .fillMaxSize()
                    .imePadding(),
            contentPadding =
                PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = 12.dp,
                    bottom = 16.dp + contentPadding.calculateBottomPadding(),
                ),
            verticalArrangement = Arrangement.spacedBy(0.dp),
        ) {
            item {
                OutlinedCard(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        SectionTitle("AI 总开关")
                        SettingRow(
                            title = "启用 AI",
                            subtitle = "仅处理文本，不上传图片",
                            checked = state.aiEnabled,
                            onCheckedChange = { enabled -> scope.launch { prefs.setAiEnabled(enabled) } },
                        )
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(12.dp)) }

            item {
                OutlinedCard(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        SectionTitle("OCR 场景")
                        SettingRow(
                            title = "启用 OCR",
                            subtitle = "OCR 引擎：PP-OCRv5（ncnn）",
                            checked = state.ocrEnabled,
                            onCheckedChange = { enabled -> scope.launch { prefs.setOcrEnabled(enabled) } },
                        )

                        Text("OCR 模式", style = MaterialTheme.typography.titleSmall)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioOption(
                                label = "仅 OCR",
                                selected = state.ocrMode == AiPreferences.OcrMode.OCR_ONLY,
                                onClick = { scope.launch { prefs.setOcrMode(AiPreferences.OcrMode.OCR_ONLY) } },
                                modifier = Modifier.weight(1f),
                            )
                            RadioOption(
                                label = "OCR + AI 整理",
                                selected = state.ocrMode == AiPreferences.OcrMode.OCR_PLUS_AI,
                                onClick = { scope.launch { prefs.setOcrMode(AiPreferences.OcrMode.OCR_PLUS_AI) } },
                                modifier = Modifier.weight(1f),
                            )
                        }

                        SettingRow(
                            title = "自动写入待办到 Inbox",
                            subtitle = "从 OCR 文本中抽取待办（可关）",
                            checked = state.ocrCreateTodos,
                            enabled = state.aiEnabled && state.ocrMode == AiPreferences.OcrMode.OCR_PLUS_AI,
                            onCheckedChange = { enabled -> scope.launch { prefs.setOcrCreateTodos(enabled) } },
                        )
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(12.dp)) }

            item {
                OutlinedCard(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        SectionTitle("功能开关")
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.Top,
                        ) {
                            ToggleTile(
                                title = "笔记 AI",
                                subtitle = "用于笔记整理/标题生成",
                                checked = state.noteAiEnabled,
                                enabled = state.aiEnabled,
                                onCheckedChange = { enabled -> scope.launch { prefs.setNoteAiEnabled(enabled) } },
                                modifier = Modifier.weight(1f),
                            )
                            ToggleTile(
                                title = "待办 AI",
                                subtitle = "用于待办拆解/补全",
                                checked = state.todoAiEnabled,
                                enabled = state.aiEnabled,
                                onCheckedChange = { enabled -> scope.launch { prefs.setTodoAiEnabled(enabled) } },
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(12.dp)) }

            item {
                OutlinedCard(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        SectionTitle("模型 / 隐私 / 调试")

                        ZhixuTextField(
                            value = baseUrlDraft,
                            onValueChange = { v ->
                                baseUrlDraft = v
                                baseUrlSaveJob?.cancel()
                                baseUrlSaveJob =
                                    scope.launch {
                                        delay(400)
                                        prefs.setBaseUrl(v)
                                    }
                            },
                            label = { Text("Base URL（OpenAI 兼容）") },
                            placeholder = { Text("https://api.openai.com/v1", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )

                        ZhixuTextField(
                            value = modelDraft,
                            onValueChange = { v ->
                                modelDraft = v
                                modelSaveJob?.cancel()
                                modelSaveJob =
                                    scope.launch {
                                        delay(400)
                                        prefs.setModel(v)
                                    }
                            },
                            label = { Text("Model") },
                            placeholder = { Text("例如：gpt-4o-mini") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )

                        ZhixuTextField(
                            value = apiKeyDraft,
                            onValueChange = { v ->
                                apiKeyDraft = v
                                apiKeySaveJob?.cancel()
                                apiKeySaveJob =
                                    scope.launch {
                                        delay(400)
                                        prefs.setApiKey(v)
                                    }
                            },
                            label = { Text("API Key") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )

                        SettingRow(
                            title = "调试模式",
                            subtitle = "失败时保留错误信息（不建议常开）",
                            checked = state.debugEnabled,
                            onCheckedChange = { enabled -> scope.launch { prefs.setDebugEnabled(enabled) } },
                        )
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(12.dp)) }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text = text, style = MaterialTheme.typography.titleMedium)
}

@Composable
private fun SettingRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        ZhixuSwitch(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled)
    }
}

@Composable
private fun RadioOption(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null)
        Text(label, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun ToggleTile(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            ZhixuSwitch(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled)
        }
        Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
