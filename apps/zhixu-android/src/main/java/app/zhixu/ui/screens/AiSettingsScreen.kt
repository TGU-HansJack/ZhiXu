package app.zhixu.ui.screens

import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import app.zhixu.ai.AiPreferences
import app.zhixu.ai.AiProviderType
import app.zhixu.ai.AiQualityPolicy
import app.zhixu.ai.AiSettings
import app.zhixu.ai.AiUseMode
import app.zhixu.ai.AiScenarioModelRef
import app.zhixu.ai.OcrEngineType
import app.zhixu.data.VaultRepository
import app.zhixu.ocr.PaddleOcrModelManager
import app.zhixu.ui.Ionicons
import app.zhixu.ui.ZhixuTopBarIconSize
import app.zhixu.ui.components.ZhixuIconButton
import app.zhixu.ui.components.ZhixuSwitch
import app.zhixu.ui.components.ZhixuTextField
import app.zhixu.ui.components.ZhixuTopAppBar
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiSettingsScreen(
    contentPadding: PaddingValues,
    vaultRootUri: Uri?,
    repository: VaultRepository,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val prefs = remember(context) { AiPreferences(context.applicationContext) }
    val ai by prefs.settings.collectAsState(initial = AiSettings.default())

    var draft by remember(ai) { mutableStateOf(ai) }

    fun update(next: AiSettings) {
        draft = next
        scope.launch { prefs.setSettings(next) }
    }

    val modelManager = remember(context, repository) { PaddleOcrModelManager(context.applicationContext, repository) }

    val scroll = rememberScrollState()

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
            Column {
                ZhixuTopAppBar(
                    containerColor = MaterialTheme.colorScheme.surface,
                    title = { Text("AI 设置", style = MaterialTheme.typography.titleMedium) },
                    navigationIcon = {
                        ZhixuIconButton(onClick = onBack) {
                            Icon(
                                painter = painterResource(Ionicons.ArrowBack),
                                contentDescription = null,
                                modifier = Modifier.size(ZhixuTopBarIconSize),
                            )
                        }
                    },
                    actions = {
                        TextButton(
                            onClick = { update(AiSettings.default()) },
                        ) { Text("重置") }
                    },
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
        },
    ) { inner ->
        Column(
            modifier =
                Modifier
                    .padding(inner)
                    .padding(contentPadding)
                    .fillMaxSize()
                    .verticalScroll(scroll)
                    .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            SectionTitle("AI 总开关（系统级）")
            ToggleRow(
                title = "启用 AI 功能",
                checked = draft.enabled,
                onCheckedChange = { update(draft.copy(enabled = it)) },
            )
            Text(
                text = "关闭后：不调用任何 AI；OCR 仍可纯文本识别；编辑/笔记/任务相关 AI 全部禁用。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            SectionTitle("AI 使用策略（全局）")
            RadioGroup(
                title = "AI 使用模式",
                options =
                    listOf(
                        AiUseMode.Auto to "自动（推荐）",
                        AiUseMode.ManualOnly to "仅手动触发",
                        AiUseMode.Disabled to "完全关闭",
                    ),
                selected = draft.global.useMode,
                onSelect = { update(draft.copy(global = draft.global.copy(useMode = it))) },
            )
            RadioGroup(
                title = "AI 性能策略",
                options =
                    listOf(
                        AiQualityPolicy.HighQuality to "高质量",
                        AiQualityPolicy.Balanced to "均衡",
                        AiQualityPolicy.LowCost to "省流量 / 低性能",
                    ),
                selected = draft.global.quality,
                onSelect = { update(draft.copy(global = draft.global.copy(quality = it))) },
            )

            SectionTitle("OCR 场景配置")
            ToggleRow(
                title = "启用 OCR 功能",
                checked = draft.ocr.enabled,
                onCheckedChange = { update(draft.copy(ocr = draft.ocr.copy(enabled = it))) },
            )
            RadioGroup(
                title = "OCR 引擎选择",
                options =
                    listOf(
                        OcrEngineType.PaddleOcr to "PaddleOCR（本地）",
                        OcrEngineType.MlKit to "ML Kit（回退）",
                    ),
                selected = draft.ocr.engine,
                onSelect = { update(draft.copy(ocr = draft.ocr.copy(engine = it))) },
            )

            ToggleRow(
                title = "OCR 后使用 AI 整理文本",
                checked = draft.ocr.useAiEnhance,
                enabled = draft.enabled && draft.global.useMode != AiUseMode.Disabled,
                onCheckedChange = { update(draft.copy(ocr = draft.ocr.copy(useAiEnhance = it))) },
            )
            ToggleRow(
                title = "自动生成 Markdown 结构",
                checked = draft.ocr.autoMarkdown,
                enabled = draft.ocr.useAiEnhance,
                onCheckedChange = { update(draft.copy(ocr = draft.ocr.copy(autoMarkdown = it))) },
            )
            ToggleRow(
                title = "自动生成标题",
                checked = draft.ocr.autoTitle,
                enabled = draft.ocr.useAiEnhance,
                onCheckedChange = { update(draft.copy(ocr = draft.ocr.copy(autoTitle = it))) },
            )
            ToggleRow(
                title = "从 OCR 内容中识别待办事项",
                checked = draft.ocr.extractTasks,
                enabled = draft.ocr.useAiEnhance,
                onCheckedChange = { update(draft.copy(ocr = draft.ocr.copy(extractTasks = it))) },
            )
            ToggleRow(
                title = "自动清理乱码 / 换行",
                checked = draft.ocr.cleanupWhitespace,
                onCheckedChange = { update(draft.copy(ocr = draft.ocr.copy(cleanupWhitespace = it))) },
            )

            ScenarioModelEditor(
                title = "OCR-AI 模型配置（按场景）",
                enabled = draft.ocr.useAiEnhance,
                value = draft.ocr.aiModel,
                onChange = { update(draft.copy(ocr = draft.ocr.copy(aiModel = it))) },
            )

            if (vaultRootUri != null) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "OCR 模型缓存目录：.zhixu/ocr/.models/",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(
                        onClick = {
                            scope.launch {
                                runCatching { modelManager.forceRedownload(vaultRootUri) }
                                    .onSuccess { snackbarHostState.showSnackbar("模型已重新下载") }
                                    .onFailure { snackbarHostState.showSnackbar(it.message ?: "模型下载失败") }
                            }
                        },
                    ) { Text("重新下载") }
                }
            } else {
                Text(
                    text = "未选择 Vault：无法下载/缓存 OCR 模型。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            SectionTitle("笔记 AI 配置（非 OCR）")
            ToggleRow(
                title = "粘贴文本时使用 AI 整理",
                checked = draft.note.pasteEnhance,
                enabled = draft.enabled && draft.global.useMode != AiUseMode.Disabled,
                onCheckedChange = { update(draft.copy(note = draft.note.copy(pasteEnhance = it))) },
            )
            ToggleRow(
                title = "新建笔记时生成标题",
                checked = draft.note.autoTitle,
                enabled = draft.enabled && draft.global.useMode != AiUseMode.Disabled,
                onCheckedChange = { update(draft.copy(note = draft.note.copy(autoTitle = it))) },
            )
            ToggleRow(
                title = "长文自动拆段 / 总结",
                checked = draft.note.autoSplitOrSummarize,
                enabled = draft.enabled && draft.global.useMode != AiUseMode.Disabled,
                onCheckedChange = { update(draft.copy(note = draft.note.copy(autoSplitOrSummarize = it))) },
            )
            ScenarioModelEditor(
                title = "笔记-AI 模型配置（按场景）",
                enabled = draft.enabled && draft.global.useMode != AiUseMode.Disabled,
                value = draft.note.aiModel,
                onChange = { update(draft.copy(note = draft.note.copy(aiModel = it))) },
            )

            SectionTitle("待办 / 任务 AI 配置")
            ToggleRow(
                title = "从文本中识别任务",
                checked = draft.task.extractTasks,
                enabled = draft.enabled && draft.global.useMode != AiUseMode.Disabled,
                onCheckedChange = { update(draft.copy(task = draft.task.copy(extractTasks = it))) },
            )
            ToggleRow(
                title = "自动识别时间（今天 / 明天 / 下周）",
                checked = draft.task.parseDates,
                enabled = draft.enabled && draft.global.useMode != AiUseMode.Disabled,
                onCheckedChange = { update(draft.copy(task = draft.task.copy(parseDates = it))) },
            )
            ToggleRow(
                title = "自动加入 Today / Upcoming",
                checked = draft.task.autoTodayUpcoming,
                enabled = draft.enabled && draft.global.useMode != AiUseMode.Disabled,
                onCheckedChange = { update(draft.copy(task = draft.task.copy(autoTodayUpcoming = it))) },
            )
            ScenarioModelEditor(
                title = "任务-AI 模型配置（按场景）",
                enabled = draft.enabled && draft.global.useMode != AiUseMode.Disabled,
                value = draft.task.aiModel,
                onChange = { update(draft.copy(task = draft.task.copy(aiModel = it))) },
            )

            SectionTitle("AI 模型与隐私（通用）")
            ToggleRow(
                title = "始终保留原始内容",
                checked = draft.debug.keepOriginal,
                onCheckedChange = { update(draft.copy(debug = draft.debug.copy(keepOriginal = it))) },
            )
            ToggleRow(
                title = "AI 结果可撤销",
                checked = draft.debug.allowUndo,
                onCheckedChange = { update(draft.copy(debug = draft.debug.copy(allowUndo = it))) },
            )
            ToggleRow(
                title = "不上传附件 / 图片（仅处理文本）",
                checked = draft.debug.neverUploadAttachments,
                onCheckedChange = { update(draft.copy(debug = draft.debug.copy(neverUploadAttachments = it))) },
            )

            SectionTitle("AI 提供商（通用）")
            RadioGroup(
                title = "默认提供商",
                options =
                    listOf(
                        AiProviderType.OpenAICompatible to "OpenAI Compatible（推荐）",
                        AiProviderType.AzureOpenAI to "Azure OpenAI",
                        AiProviderType.Anthropic to "Anthropic Claude",
                        AiProviderType.Gemini to "Google Gemini",
                        AiProviderType.Ollama to "Ollama（本地）",
                    ),
                selected = draft.model.provider,
                onSelect = { update(draft.copy(model = draft.model.copy(provider = it))) },
            )

            ProviderConfigEditor(
                provider = draft.model.provider,
                settings = draft,
                onChange = ::update,
            )

            AdvancedSection(
                title = "高级 / 调试（可折叠）",
            ) {
                ToggleRow(
                    title = "显示 AI Prompt",
                    checked = draft.debug.showPrompt,
                    onCheckedChange = { update(draft.copy(debug = draft.debug.copy(showPrompt = it))) },
                )
                ToggleRow(
                    title = "显示 AI 原始输出",
                    checked = draft.debug.showRawOutput,
                    onCheckedChange = { update(draft.copy(debug = draft.debug.copy(showRawOutput = it))) },
                )
                ToggleRow(
                    title = "记录 AI 调用日志",
                    checked = draft.debug.logCalls,
                    onCheckedChange = { update(draft.copy(debug = draft.debug.copy(logCalls = it))) },
                )
                ToggleRow(
                    title = "失败自动回退为无 AI 流程",
                    checked = draft.debug.fallbackToNoAiOnFailure,
                    onCheckedChange = { update(draft.copy(debug = draft.debug.copy(fallbackToNoAiOnFailure = it))) },
                )
                Text(
                    text = "说明：目前仅保存配置；具体 AI 功能会按场景读取对应字段。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }

    // Keep screen draft in sync when settings change elsewhere.
    LaunchedEffect(ai) {
        if (ai != draft) draft = ai
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text = text, style = MaterialTheme.typography.titleMedium)
}

@Composable
private fun ToggleRow(
    title: String,
    checked: Boolean,
    enabled: Boolean = true,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(text = title, modifier = Modifier.weight(1f))
        ZhixuSwitch(checked = checked, enabled = enabled, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun <T> RadioGroup(
    title: String,
    options: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(text = title, style = MaterialTheme.typography.titleSmall)
        options.forEach { (value, label) ->
            RadioRow(
                label = label,
                selected = value == selected,
                onClick = { onSelect(value) },
            )
        }
    }
}

@Composable
private fun RadioRow(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(
            painter = painterResource(if (selected) Ionicons.CheckmarkCircle else Ionicons.RadioOff),
            contentDescription = null,
            tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp),
        )
        TextButton(onClick = onClick, contentPadding = PaddingValues(0.dp)) {
            Text(text = label)
        }
    }
}

@Composable
private fun AdvancedSection(
    title: String,
    content: @Composable () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(text = title, style = MaterialTheme.typography.titleSmall)
            TextButton(onClick = { expanded = !expanded }) { Text(if (expanded) "收起" else "展开") }
        }
        if (expanded) {
            content()
        }
    }
}

@Composable
private fun ProviderConfigEditor(
    provider: AiProviderType,
    settings: AiSettings,
    onChange: (AiSettings) -> Unit,
) {
    when (provider) {
        AiProviderType.OpenAICompatible -> {
            Text("OpenAI Compatible", style = MaterialTheme.typography.titleSmall)
            ZhixuTextField(
                value = settings.model.openAiCompatible.baseUrl,
                onValueChange = { onChange(settings.copy(model = settings.model.copy(openAiCompatible = settings.model.openAiCompatible.copy(baseUrl = it)))) },
                label = { Text("Base URL") },
                modifier = Modifier.fillMaxWidth(),
            )
            ZhixuTextField(
                value = settings.model.openAiCompatible.apiKey,
                onValueChange = { onChange(settings.copy(model = settings.model.copy(openAiCompatible = settings.model.openAiCompatible.copy(apiKey = it)))) },
                label = { Text("API Key") },
                modifier = Modifier.fillMaxWidth(),
            )
            ZhixuTextField(
                value = settings.model.openAiCompatible.defaultModel,
                onValueChange = { onChange(settings.copy(model = settings.model.copy(openAiCompatible = settings.model.openAiCompatible.copy(defaultModel = it)))) },
                label = { Text("Default Model") },
                modifier = Modifier.fillMaxWidth(),
            )
        }

        AiProviderType.AzureOpenAI -> {
            Text("Azure OpenAI", style = MaterialTheme.typography.titleSmall)
            ZhixuTextField(
                value = settings.model.azureOpenAi.endpoint,
                onValueChange = { onChange(settings.copy(model = settings.model.copy(azureOpenAi = settings.model.azureOpenAi.copy(endpoint = it)))) },
                label = { Text("Endpoint（https://{resource}.openai.azure.com）") },
                modifier = Modifier.fillMaxWidth(),
            )
            ZhixuTextField(
                value = settings.model.azureOpenAi.apiKey,
                onValueChange = { onChange(settings.copy(model = settings.model.copy(azureOpenAi = settings.model.azureOpenAi.copy(apiKey = it)))) },
                label = { Text("API Key") },
                modifier = Modifier.fillMaxWidth(),
            )
            ZhixuTextField(
                value = settings.model.azureOpenAi.deployment,
                onValueChange = { onChange(settings.copy(model = settings.model.copy(azureOpenAi = settings.model.azureOpenAi.copy(deployment = it)))) },
                label = { Text("Deployment") },
                modifier = Modifier.fillMaxWidth(),
            )
            ZhixuTextField(
                value = settings.model.azureOpenAi.apiVersion,
                onValueChange = { onChange(settings.copy(model = settings.model.copy(azureOpenAi = settings.model.azureOpenAi.copy(apiVersion = it)))) },
                label = { Text("API Version") },
                modifier = Modifier.fillMaxWidth(),
            )
        }

        AiProviderType.Anthropic -> {
            Text("Anthropic", style = MaterialTheme.typography.titleSmall)
            ZhixuTextField(
                value = settings.model.anthropic.apiKey,
                onValueChange = { onChange(settings.copy(model = settings.model.copy(anthropic = settings.model.anthropic.copy(apiKey = it)))) },
                label = { Text("API Key") },
                modifier = Modifier.fillMaxWidth(),
            )
            ZhixuTextField(
                value = settings.model.anthropic.defaultModel,
                onValueChange = { onChange(settings.copy(model = settings.model.copy(anthropic = settings.model.anthropic.copy(defaultModel = it)))) },
                label = { Text("Default Model") },
                modifier = Modifier.fillMaxWidth(),
            )
        }

        AiProviderType.Gemini -> {
            Text("Gemini", style = MaterialTheme.typography.titleSmall)
            ZhixuTextField(
                value = settings.model.gemini.apiKey,
                onValueChange = { onChange(settings.copy(model = settings.model.copy(gemini = settings.model.gemini.copy(apiKey = it)))) },
                label = { Text("API Key") },
                modifier = Modifier.fillMaxWidth(),
            )
            ZhixuTextField(
                value = settings.model.gemini.defaultModel,
                onValueChange = { onChange(settings.copy(model = settings.model.copy(gemini = settings.model.gemini.copy(defaultModel = it)))) },
                label = { Text("Default Model") },
                modifier = Modifier.fillMaxWidth(),
            )
        }

        AiProviderType.Ollama -> {
            Text("Ollama（本地）", style = MaterialTheme.typography.titleSmall)
            ZhixuTextField(
                value = settings.model.ollama.baseUrl,
                onValueChange = { onChange(settings.copy(model = settings.model.copy(ollama = settings.model.ollama.copy(baseUrl = it)))) },
                label = { Text("Base URL") },
                modifier = Modifier.fillMaxWidth(),
            )
            ZhixuTextField(
                value = settings.model.ollama.defaultModel,
                onValueChange = { onChange(settings.copy(model = settings.model.copy(ollama = settings.model.ollama.copy(defaultModel = it)))) },
                label = { Text("Default Model") },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
    Spacer(Modifier.size(6.dp))
}

@Composable
private fun ScenarioModelEditor(
    title: String,
    enabled: Boolean,
    value: AiScenarioModelRef,
    onChange: (AiScenarioModelRef) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(text = title, style = MaterialTheme.typography.titleSmall)
        ToggleRow(
            title = "使用全局提供商",
            checked = value.useGlobalProvider,
            enabled = enabled,
            onCheckedChange = { onChange(value.copy(useGlobalProvider = it)) },
        )
        if (!value.useGlobalProvider) {
            RadioGroup(
                title = "提供商",
                options =
                    listOf(
                        AiProviderType.OpenAICompatible to "OpenAI Compatible",
                        AiProviderType.AzureOpenAI to "Azure OpenAI",
                        AiProviderType.Anthropic to "Anthropic",
                        AiProviderType.Gemini to "Gemini",
                        AiProviderType.Ollama to "Ollama",
                    ),
                selected = value.provider,
                onSelect = { onChange(value.copy(provider = it)) },
            )
        }
        ZhixuTextField(
            value = value.model,
            onValueChange = { onChange(value.copy(model = it)) },
            enabled = enabled,
            label = { Text("Model（场景默认）") },
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            text = "说明：场景可选择使用全局提供商，也可单独指定提供商与模型；仅用于该场景的 AI 调用。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
