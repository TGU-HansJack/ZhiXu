package app.zhixu.ui

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SheetState
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.zhixu.data.UiDoc
import app.zhixu.data.VaultRepository
import app.zhixu.data.AiPreferences
import app.zhixu.ocr.OcrWorkflow
import app.zhixu.sync.VaultAutoSync
import app.zhixu.ui.components.ZhixuIconButton
import app.zhixu.ui.screens.NewDocScreen
import app.zhixu.ui.screens.TaskComposer
import coil.compose.AsyncImage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
internal fun TodoComposerSheet(
    vaultRootUri: Uri?,
    repository: VaultRepository,
    onBack: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ZhixuIconButton(onClick = onBack) {
                androidx.compose.material3.Icon(
                    painter = painterResource(Ionicons.ArrowBack),
                    contentDescription = "返回",
                    modifier = Modifier.size(22.dp),
                )
            }
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "新建待办",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
            ZhixuIconButton(onClick = onClose) {
                androidx.compose.material3.Icon(
                    painter = painterResource(Ionicons.Close),
                    contentDescription = "关闭",
                    modifier = Modifier.size(22.dp),
                )
            }
        }

        TaskComposer(
            onSubmit = { draft ->
                val root = vaultRootUri
                if (root == null) {
                    android.widget.Toast.makeText(context, "未选择资料库", android.widget.Toast.LENGTH_SHORT).show()
                    return@TaskComposer
                }
                scope.launch {
                    val ok =
                        repository.addTaskToInbox(
                            rootUri = root,
                            title = draft.title.trim(),
                            dueDate = draft.dueDate,
                            dueTime = draft.dueTime,
                            remindAt = draft.remindAt,
                            remindPersistent = draft.remindPersistent,
                            tags = draft.tags,
                            priority = draft.priority,
                            repeat = draft.repeat,
                        )
                    if (!ok) {
                        android.widget.Toast.makeText(context, "添加失败", android.widget.Toast.LENGTH_SHORT).show()
                        return@launch
                    }

                    runCatching {
                        VaultAutoSync.maybeUploadInbox(
                            context = context,
                            repository = repository,
                            vaultRootUri = root,
                            force = true,
                        )
                    }
                    android.widget.Toast.makeText(context, "已添加", android.widget.Toast.LENGTH_SHORT).show()
                    onClose()
                }
            },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun NoteComposerSheet(
    sheetState: SheetState,
    vaultRootUri: Uri?,
    repository: VaultRepository,
    onBack: () -> Unit,
    onCreated: (app.zhixu.data.UiDoc) -> Unit,
    modifier: Modifier = Modifier,
) {
    val isExpanded = sheetState.currentValue == SheetValue.Expanded || sheetState.targetValue == SheetValue.Expanded
    val heightFraction =
        animateFloatAsState(
            targetValue = if (isExpanded) 1f else 0.66f,
            label = "note_sheet_height_fraction",
        ).value

    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .fillMaxHeight(heightFraction),
    ) {
        val root = vaultRootUri
        if (root == null) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("未选择资料库", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            return@Box
        }

        NewDocScreen(
            vaultRootUri = root,
            repository = repository,
            onCreated = onCreated,
            onBack = onBack,
        )
    }
}

@Composable
internal fun OcrComposerSheet(
    vaultRootUri: Uri?,
    repository: VaultRepository,
    workflow: OcrWorkflow,
    onBack: () -> Unit,
    onClose: () -> Unit,
    onCreated: (UiDoc) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val aiPrefs = remember(context) { AiPreferences(context.applicationContext) }
    val aiState by aiPrefs.state.collectAsState(
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

    var busy by remember { mutableStateOf(false) }
    var recognizeResult by remember { mutableStateOf<OcrWorkflow.RecognizeResult?>(null) }
    var editedText by remember { mutableStateOf("") }

    LaunchedEffect(vaultRootUri, aiState.ocrEnabled) {
        val root = vaultRootUri ?: return@LaunchedEffect
        if (!aiState.ocrEnabled) return@LaunchedEffect
        runCatching { workflow.warmUp(vaultRootUri = root) }
    }

    val pickLauncher =
        rememberLauncherForActivityResult(contract = ActivityResultContracts.OpenDocument()) { picked: Uri? ->
            if (picked == null) return@rememberLauncherForActivityResult
            if (busy) return@rememberLauncherForActivityResult
            if (!aiState.ocrEnabled) {
                android.widget.Toast.makeText(context, "OCR 未启用（到「我的」→「AI 设置」开启）", android.widget.Toast.LENGTH_SHORT).show()
                return@rememberLauncherForActivityResult
            }
            val root = vaultRootUri
            if (root == null) {
                android.widget.Toast.makeText(context, "未选择资料库", android.widget.Toast.LENGTH_SHORT).show()
                return@rememberLauncherForActivityResult
            }
            runCatching {
                context.contentResolver.takePersistableUriPermission(picked, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            scope.launch {
                busy = true
                try {
                    val r = workflow.recognizeImage(vaultRootUri = root, sourceImageUri = picked)
                    recognizeResult = r
                    editedText = r.result.text
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    android.widget.Toast.makeText(
                        context,
                        "OCR失败：${e.message ?: e.javaClass.simpleName}",
                        android.widget.Toast.LENGTH_SHORT,
                    ).show()
                } finally {
                    busy = false
                }
            }
        }

    val hasPreview = recognizeResult != null
    val backHandler: () -> Unit =
        if (hasPreview) {
            {
                recognizeResult = null
                editedText = ""
            }
        } else {
            onBack
        }

    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ZhixuIconButton(onClick = backHandler) {
                androidx.compose.material3.Icon(
                    painter = painterResource(Ionicons.ArrowBack),
                    contentDescription = "返回",
                    modifier = Modifier.size(22.dp),
                )
            }
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = if (hasPreview) "OCR结果" else "OCR识图",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
            ZhixuIconButton(onClick = onClose) {
                androidx.compose.material3.Icon(
                    painter = painterResource(Ionicons.Close),
                    contentDescription = "关闭",
                    modifier = Modifier.size(22.dp),
                )
            }
        }

        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            val preview = recognizeResult
            if (preview == null) {
                Text(
                    text = if (busy) "识别中…" else "请选择一张图片进行识别",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                androidx.compose.material3.Button(
                    onClick = { pickLauncher.launch(arrayOf("image/*")) },
                    enabled = !busy,
                ) {
                    Text(text = if (busy) "处理中" else "选择图片")
                }
            } else {
                AsyncImage(
                    model = preview.imported.localFile,
                    contentDescription = null,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .heightIn(max = 240.dp)
                            .clip(RoundedCornerShape(10.dp)),
                )

                val err = preview.ocrError?.trim().orEmpty()
                if (err.isNotBlank()) {
                    Text(text = "OCR异常：$err", color = MaterialTheme.colorScheme.error)
                } else if (preview.result.elapsedMs != null) {
                    Text(text = "识别完成，耗时 ${preview.result.elapsedMs}ms", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }

                OutlinedTextField(
                    value = editedText,
                    onValueChange = { editedText = it },
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = 160.dp),
                    placeholder = { Text("识别文本（可编辑）") },
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    androidx.compose.material3.OutlinedButton(
                        onClick = {
                            recognizeResult = null
                            editedText = ""
                        },
                        enabled = !busy,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("重选")
                    }
                    androidx.compose.material3.Button(
                        onClick = {
                            val root = vaultRootUri
                            if (root == null) {
                                android.widget.Toast.makeText(context, "未选择资料库", android.widget.Toast.LENGTH_SHORT).show()
                            } else if (!busy) {
                                scope.launch {
                                    busy = true
                                    try {
                                        val runResult =
                                            workflow.createNoteFromRecognize(
                                                vaultRootUri = root,
                                                recognize = preview,
                                                editedOcrText = editedText,
                                            )

                                        if (runResult.ocrTextEmpty) {
                                            android.widget.Toast.makeText(context, "未识别到文字（已生成空白笔记）", android.widget.Toast.LENGTH_SHORT).show()
                                        } else if (aiState.aiEnabled && aiState.ocrMode == AiPreferences.OcrMode.OCR_PLUS_AI && runResult.aiApplied) {
                                            val msg =
                                                if (runResult.todosCreated > 0) {
                                                    "OCR+AI 完成，已写入待办：${runResult.todosCreated}条"
                                                } else {
                                                    "OCR+AI 完成"
                                                }
                                            android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_SHORT).show()
                                        } else if (aiState.aiEnabled && aiState.ocrMode == AiPreferences.OcrMode.OCR_PLUS_AI && (aiState.apiKey.isBlank() || aiState.model.isBlank())) {
                                            android.widget.Toast.makeText(context, "已生成 OCR 笔记；AI 未配置 model/apiKey", android.widget.Toast.LENGTH_SHORT).show()
                                        } else if (!runResult.ocrError.isNullOrBlank()) {
                                            android.widget.Toast.makeText(context, "OCR异常：${runResult.ocrError}", android.widget.Toast.LENGTH_SHORT).show()
                                        }

                                        onCreated(runResult.doc)
                                    } catch (e: CancellationException) {
                                        throw e
                                    } catch (e: Exception) {
                                        android.widget.Toast.makeText(
                                            context,
                                            "生成笔记失败：${e.message ?: e.javaClass.simpleName}",
                                            android.widget.Toast.LENGTH_SHORT,
                                        ).show()
                                    } finally {
                                        busy = false
                                    }
                                }
                            }
                        },
                        enabled = !busy,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(text = if (busy) "生成中…" else "生成笔记")
                    }
                }

                val canGenerateTodos =
                    aiState.aiEnabled &&
                        aiState.ocrMode == AiPreferences.OcrMode.OCR_PLUS_AI &&
                        aiState.ocrCreateTodos
                if (canGenerateTodos) {
                    androidx.compose.material3.Button(
                        onClick = {
                            val root = vaultRootUri
                            if (root == null) {
                                android.widget.Toast.makeText(context, "未选择资料库", android.widget.Toast.LENGTH_SHORT).show()
                            } else if (aiState.apiKey.isBlank() || aiState.model.isBlank()) {
                                android.widget.Toast.makeText(context, "AI 未配置 model/apiKey", android.widget.Toast.LENGTH_SHORT).show()
                            } else if (!busy) {
                                scope.launch {
                                    busy = true
                                    try {
                                        val result = workflow.generateTodosFromOcrText(vaultRootUri = root, editedOcrText = editedText)
                                        val msg =
                                            result.reason
                                                ?: when {
                                                    result.created > 0 && result.failed == 0 -> "已生成待办：${result.created}条"
                                                    result.created > 0 -> "已生成待办：${result.created}条（失败：${result.failed}/${result.total}）"
                                                    else -> "未生成待办"
                                                }
                                        android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_SHORT).show()
                                    } catch (e: CancellationException) {
                                        throw e
                                    } catch (e: Exception) {
                                        android.widget.Toast.makeText(
                                            context,
                                            "生成待办失败：${e.message ?: e.javaClass.simpleName}",
                                            android.widget.Toast.LENGTH_SHORT,
                                        ).show()
                                    } finally {
                                        busy = false
                                    }
                                }
                            }
                        },
                        enabled = !busy,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(text = if (busy) "生成中…" else "生成待办")
                    }
                }
            }
        }
    }
}
