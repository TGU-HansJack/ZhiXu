package app.zhixu.ui.screens

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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.zhixu.R
import app.zhixu.data.AiPreferences
import app.zhixu.ui.Ionicons
import app.zhixu.ui.ZhixuTopBarIconSize
import app.zhixu.ui.components.ZhixuDialogDefaults
import app.zhixu.ui.components.ZhixuIconButton
import app.zhixu.ui.components.ZhixuPasswordToggleIconButton
import app.zhixu.ui.components.ZhixuSwitch
import app.zhixu.ui.components.ZhixuTextField
import app.zhixu.ui.components.ZhixuTopAppBar
import kotlinx.coroutines.launch

private enum class AiEditField {
    BaseUrl,
    Model,
    ApiKey,
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiSettingsScreen(
    contentPadding: PaddingValues,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs = remember(context) { AiPreferences(context.applicationContext) }

    val state by
        prefs.state.collectAsState(
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

    var editField by remember { mutableStateOf<AiEditField?>(null) }
    var editValue by rememberSaveable { mutableStateOf("") }
    var showApiKey by remember { mutableStateOf(false) }
    var showOcrModeDialog by remember { mutableStateOf(false) }

    val dividerColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            Column {
                ZhixuTopAppBar(
                    containerColor = MaterialTheme.colorScheme.surface,
                    title = { Text(stringResource(R.string.settings_ai_title), style = MaterialTheme.typography.titleMedium) },
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
                    .padding(contentPadding)
                    .padding(innerPadding)
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .background(MaterialTheme.colorScheme.background)
                    .fillMaxSize()
                    .imePadding(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(0.dp),
        ) {
            item { Spacer(modifier = Modifier.height(12.dp)) }

            item { SectionTitle(text = "开关") }
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = MaterialTheme.shapes.extraLarge,
                ) {
                    ToggleRow(
                        iconRes = R.drawable.ic_hi_sparkles_outline,
                        title = "启用 AI",
                        subtitle = "开启 AI 相关功能（OCR 后处理等）",
                        checked = state.aiEnabled,
                        onCheckedChange = { checked -> scope.launch { prefs.setAiEnabled(checked) } },
                    )
                    HorizontalDivider(color = dividerColor)
                    ToggleRow(
                        iconRes = Ionicons.ImageOutline,
                        title = "启用 OCR",
                        subtitle = "拍照/导入图片 OCR",
                        checked = state.ocrEnabled,
                        onCheckedChange = { checked -> scope.launch { prefs.setOcrEnabled(checked) } },
                    )
                }
            }

            item { Spacer(modifier = Modifier.height(14.dp)) }

            item { SectionTitle(text = "OCR") }
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = MaterialTheme.shapes.extraLarge,
                ) {
                    NavRow(
                        iconRes = Ionicons.SettingsOutline,
                        title = "OCR 后处理",
                        subtitle =
                            when (state.ocrMode) {
                                AiPreferences.OcrMode.OCR_ONLY -> "仅 OCR"
                                AiPreferences.OcrMode.OCR_PLUS_AI -> "OCR + AI 整理"
                            },
                        enabled = state.ocrEnabled && state.aiEnabled,
                        onClick = { showOcrModeDialog = true },
                    )
                    HorizontalDivider(color = dividerColor)
                    ToggleRow(
                        iconRes = Ionicons.CheckmarkCircle,
                        title = "自动提取待办",
                        subtitle = "在 OCR+AI 模式下，从内容中提取待办",
                        checked = state.ocrCreateTodos,
                        enabled = state.ocrEnabled && state.aiEnabled && state.ocrMode == AiPreferences.OcrMode.OCR_PLUS_AI,
                        onCheckedChange = { checked -> scope.launch { prefs.setOcrCreateTodos(checked) } },
                    )
                }
            }

            item { Spacer(modifier = Modifier.height(14.dp)) }

            item { SectionTitle(text = "模型") }
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = MaterialTheme.shapes.extraLarge,
                ) {
                    NavRow(
                        iconRes = Ionicons.CloudDownloadOutline,
                        title = "Base URL",
                        subtitle = state.baseUrl.trim().ifBlank { "-" },
                        enabled = state.aiEnabled,
                        onClick = {
                            editField = AiEditField.BaseUrl
                            editValue = state.baseUrl
                            showApiKey = false
                        },
                    )
                    HorizontalDivider(color = dividerColor)
                    NavRow(
                        iconRes = Ionicons.TextOutline,
                        title = "Model",
                        subtitle = state.model.trim().ifBlank { "gpt-4o-mini" },
                        enabled = state.aiEnabled,
                        onClick = {
                            editField = AiEditField.Model
                            editValue = state.model
                            showApiKey = false
                        },
                    )
                    HorizontalDivider(color = dividerColor)
                    NavRow(
                        iconRes = Ionicons.SettingsOutline,
                        title = "API Key",
                        subtitle = if (state.apiKey.isBlank()) "未设置" else "已设置",
                        enabled = state.aiEnabled,
                        onClick = {
                            editField = AiEditField.ApiKey
                            editValue = state.apiKey
                            showApiKey = false
                        },
                    )
                    HorizontalDivider(color = dividerColor)
                    ToggleRow(
                        iconRes = Ionicons.SettingsOutline,
                        title = "调试模式",
                        subtitle = "失败时保留错误信息（不建议常开）",
                        checked = state.debugEnabled,
                        enabled = state.aiEnabled,
                        onCheckedChange = { checked -> scope.launch { prefs.setDebugEnabled(checked) } },
                    )
                }
            }
        }
    }

    if (showOcrModeDialog) {
        AlertDialog(
            modifier = ZhixuDialogDefaults.modifier(),
            onDismissRequest = { showOcrModeDialog = false },
            properties = ZhixuDialogDefaults.properties,
            title = { Text("OCR 后处理") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OcrModeOption(
                        label = "仅 OCR（默认）",
                        selected = state.ocrMode == AiPreferences.OcrMode.OCR_ONLY,
                        enabled = state.aiEnabled && state.ocrEnabled,
                        onClick = { scope.launch { prefs.setOcrMode(AiPreferences.OcrMode.OCR_ONLY) } },
                    )
                    OcrModeOption(
                        label = "OCR + AI 整理",
                        selected = state.ocrMode == AiPreferences.OcrMode.OCR_PLUS_AI,
                        enabled = state.aiEnabled && state.ocrEnabled,
                        onClick = { scope.launch { prefs.setOcrMode(AiPreferences.OcrMode.OCR_PLUS_AI) } },
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showOcrModeDialog = false }) {
                    Text(stringResource(R.string.action_close))
                }
            },
        )
    }

    val field = editField
    if (field != null) {
        val title =
            when (field) {
                AiEditField.BaseUrl -> "Base URL"
                AiEditField.Model -> "Model"
                AiEditField.ApiKey -> "API Key"
            }
        val placeholder =
            when (field) {
                AiEditField.BaseUrl -> "https://api.openai.com/v1"
                AiEditField.Model -> "例如：gpt-4o-mini"
                AiEditField.ApiKey -> ""
            }
        val label = title
        val isPassword = field == AiEditField.ApiKey

        AlertDialog(
            modifier = ZhixuDialogDefaults.modifier(),
            onDismissRequest = { editField = null },
            properties = ZhixuDialogDefaults.properties,
            title = { Text(title) },
            text = {
                ZhixuTextField(
                    value = editValue,
                    onValueChange = { editValue = it },
                    label = { Text(label) },
                    placeholder = { Text(placeholder, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    singleLine = true,
                    visualTransformation = if (isPassword && !showApiKey) PasswordVisualTransformation() else VisualTransformation.None,
                    trailingIcon =
                        if (!isPassword) {
                            null
                        } else {
                            {
                                ZhixuPasswordToggleIconButton(
                                    show = showApiKey,
                                    enabled = true,
                                    onClick = { showApiKey = !showApiKey },
                                )
                            }
                        },
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val value = editValue
                        scope.launch {
                            when (field) {
                                AiEditField.BaseUrl -> prefs.setBaseUrl(value)
                                AiEditField.Model -> prefs.setModel(value)
                                AiEditField.ApiKey -> prefs.setApiKey(value)
                            }
                            editField = null
                        }
                    },
                ) { Text(stringResource(R.string.action_save)) }
            },
            dismissButton = {
                TextButton(onClick = { editField = null }) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontSize = 13.sp,
        modifier = Modifier.padding(horizontal = 4.dp, vertical = 6.dp),
    )
}

@Composable
private fun ToggleRow(
    iconRes: Int,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true,
) {
    val alpha = if (enabled) 1f else 0.4f
    ListItem(
        modifier = Modifier.fillMaxWidth().alpha(alpha),
        leadingContent = {
            Icon(
                painter = painterResource(iconRes),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
        },
        headlineContent = { Text(title) },
        supportingContent = { Text(subtitle, maxLines = 2, overflow = TextOverflow.Ellipsis) },
        trailingContent = { ZhixuSwitch(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled) },
    )
}

@Composable
private fun NavRow(
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

@Composable
private fun OcrModeOption(
    label: String,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val alpha = if (enabled) 1f else 0.4f
    androidx.compose.foundation.layout.Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .alpha(alpha)
                .clickable(enabled = enabled, onClick = onClick)
                .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        RadioButton(selected = selected, onClick = null, enabled = enabled)
        Text(label, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}
