package app.zhixu.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import app.zhixu.data.AiPreferences
import app.zhixu.ui.components.ZhixuTopAppBar
import kotlinx.coroutines.launch

@Composable
fun AiSettingsScreen(
    contentPadding: PaddingValues,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val prefs = remember(context) { AiPreferences(context.applicationContext) }
    val state by prefs.state.collectAsState(
        initial =
            AiPreferences.State(
                aiEnabled = false,
                baseUrl = "https://api.openai.com/v1",
                apiKey = "",
                model = "",
                debugEnabled = false,
                ocrEnabled = true,
                ocrMode = AiPreferences.OcrMode.OCR_ONLY,
                ocrCreateTodos = false,
                noteAiEnabled = false,
                todoAiEnabled = false,
            ),
    )
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val divider = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(contentPadding),
    ) {
        ZhixuTopAppBar(
            title = { Text("AI 设置") },
            navigationIcon = {
                TextButton(onClick = onBack) { Text("返回") }
            },
        )
        HorizontalDivider(color = divider)

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                SectionTitle("AI 总开关")
                SettingRow(
                    title = "启用 AI",
                    subtitle = "仅处理文本，不上传图片",
                    checked = state.aiEnabled,
                    onCheckedChange = { enabled -> scope.launch { prefs.setAiEnabled(enabled) } },
                )
            }

            item {
                SectionTitle("OCR 场景")
                SettingRow(
                    title = "启用 OCR",
                    subtitle = "OCR 引擎：PP-OCRv5（ncnn）",
                    checked = state.ocrEnabled,
                    onCheckedChange = { enabled -> scope.launch { prefs.setOcrEnabled(enabled) } },
                )

                Text("OCR 模式", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 8.dp))
                RadioRow(
                    label = "仅 OCR",
                    selected = state.ocrMode == AiPreferences.OcrMode.OCR_ONLY,
                    onClick = { scope.launch { prefs.setOcrMode(AiPreferences.OcrMode.OCR_ONLY) } },
                )
                RadioRow(
                    label = "OCR + AI 整理",
                    selected = state.ocrMode == AiPreferences.OcrMode.OCR_PLUS_AI,
                    onClick = { scope.launch { prefs.setOcrMode(AiPreferences.OcrMode.OCR_PLUS_AI) } },
                )

                SettingRow(
                    title = "自动写入待办到 Inbox",
                    subtitle = "从 OCR 文本中抽取待办（可关）",
                    checked = state.ocrCreateTodos,
                    enabled = state.aiEnabled && state.ocrMode == AiPreferences.OcrMode.OCR_PLUS_AI,
                    onCheckedChange = { enabled -> scope.launch { prefs.setOcrCreateTodos(enabled) } },
                )
            }

            item {
                SectionTitle("笔记 AI 设置")
                SettingRow(
                    title = "启用笔记 AI",
                    subtitle = "（骨架）后续用于笔记整理/标题生成",
                    checked = state.noteAiEnabled,
                    enabled = state.aiEnabled,
                    onCheckedChange = { enabled -> scope.launch { prefs.setNoteAiEnabled(enabled) } },
                )
            }

            item {
                SectionTitle("待办 AI 设置")
                SettingRow(
                    title = "启用待办 AI",
                    subtitle = "（骨架）后续用于待办拆解/补全",
                    checked = state.todoAiEnabled,
                    enabled = state.aiEnabled,
                    onCheckedChange = { enabled -> scope.launch { prefs.setTodoAiEnabled(enabled) } },
                )
            }

            item {
                SectionTitle("模型 / 隐私 / 调试")

                OutlinedTextField(
                    value = state.baseUrl,
                    onValueChange = { v -> scope.launch { prefs.setBaseUrl(v) } },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Base URL（OpenAI 兼容）") },
                    singleLine = true,
                )

                OutlinedTextField(
                    value = state.model,
                    onValueChange = { v -> scope.launch { prefs.setModel(v) } },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Model") },
                    singleLine = true,
                    placeholder = { Text("例如：gpt-4o-mini") },
                )

                OutlinedTextField(
                    value = state.apiKey,
                    onValueChange = { v -> scope.launch { prefs.setApiKey(v) } },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("API Key") },
                    singleLine = true,
                )

                SettingRow(
                    title = "调试模式",
                    subtitle = "失败时保留错误信息（不推荐常开）",
                    checked = state.debugEnabled,
                    onCheckedChange = { enabled -> scope.launch { prefs.setDebugEnabled(enabled) } },
                )
            }
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
    androidx.compose.foundation.layout.Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled)
    }
}

@Composable
private fun RadioRow(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    androidx.compose.foundation.layout.Row(
        modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Text(label, style = MaterialTheme.typography.bodyLarge)
    }
}
