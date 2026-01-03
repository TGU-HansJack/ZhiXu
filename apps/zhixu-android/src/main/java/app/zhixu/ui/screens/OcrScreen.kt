package app.zhixu.ui.screens

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import app.zhixu.ai.AiPreferences
import app.zhixu.ai.AiService
import app.zhixu.ai.AiUseMode
import app.zhixu.ai.OcrEngineType
import app.zhixu.data.UiDoc
import app.zhixu.data.VaultRepository
import app.zhixu.ocr.MlKitOcrEngine
import app.zhixu.ocr.PaddleOcrEngine
import app.zhixu.ocr.PaddleOcrModelManager
import app.zhixu.ocr.OcrResult
import app.zhixu.sync.VaultAutoSync
import app.zhixu.ui.Ionicons
import app.zhixu.ui.ZhixuTopBarIconSize
import app.zhixu.ui.components.ZhixuIconButton
import app.zhixu.ui.components.ZhixuTopAppBar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlin.math.max

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OcrScreen(
    vaultRootUri: Uri,
    repository: VaultRepository,
    onBack: () -> Unit,
    onCreated: (UiDoc) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    val aiPrefs = remember(context) { AiPreferences(context.applicationContext) }
    val aiSettings by aiPrefs.settings.collectAsState(initial = app.zhixu.ai.AiSettings.default())
    val aiService = remember { AiService() }

    val modelManager = remember(context, repository) { PaddleOcrModelManager(context.applicationContext, repository) }
    val paddleEngine = remember(vaultRootUri, repository, modelManager) { PaddleOcrEngine(repository = repository, modelManager = modelManager, vaultRootUri = vaultRootUri) }
    val mlKitEngine = remember { MlKitOcrEngine() }

    var ocrImageUri by remember { mutableStateOf<Uri?>(null) }
    var ocrImageRelativePath by remember { mutableStateOf<String?>(null) }
    var previewBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var recognizing by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<OcrResult?>(null) }
    var enhancedMarkdown by remember { mutableStateOf<String?>(null) }
    var aiDebugPrompt by remember { mutableStateOf<String?>(null) }
    var aiDebugRaw by remember { mutableStateOf<String?>(null) }

    val pickLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
            if (uri == null) return@rememberLauncherForActivityResult
            scope.launch {
                recognizing = true
                result = null
                previewBitmap = null
                ocrImageUri = null
                ocrImageRelativePath = null
                enhancedMarkdown = null
                aiDebugPrompt = null
                aiDebugRaw = null
                try {
                    val ts = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss").format(LocalDateTime.now())
                    val imported =
                        repository.importOcrImageFromUri(
                            rootUri = vaultRootUri,
                            sourceUri = uri,
                            suggestedBaseName = "ocr_$ts",
                        )
                    ocrImageUri = imported.uri
                    ocrImageRelativePath = imported.relativePath

                    val bmp = withContext(Dispatchers.IO) { decodeBitmap(context, imported.uri, maxDim = 2200) }
                    previewBitmap = bmp
                    if (bmp == null) {
                        snackbarHostState.showSnackbar("无法读取图片")
                        return@launch
                    }

                    val engine =
                        when (aiSettings.ocr.engine) {
                            OcrEngineType.PaddleOcr -> paddleEngine
                            OcrEngineType.MlKit -> mlKitEngine
                        }
                    val ocr = engine.recognize(bmp)
                    result = ocr

                    val shouldAi =
                        aiSettings.enabled &&
                            aiSettings.global.useMode == AiUseMode.Auto &&
                            aiSettings.ocr.enabled &&
                            aiSettings.ocr.useAiEnhance
                    if (shouldAi) {
                        val enhanced =
                            runCatching { aiService.enhanceOcrToMarkdown(ocr.fullText, aiSettings, vaultRootUri, repository) }
                                .getOrNull()
                        enhancedMarkdown = enhanced?.markdown
                        aiDebugPrompt = enhanced?.debug?.prompt?.takeIf { it.isNotBlank() }
                        aiDebugRaw = enhanced?.debug?.rawResponse?.takeIf { it.isNotBlank() }
                    }
                } catch (e: Exception) {
                    snackbarHostState.showSnackbar(e.message ?: "OCR失败")
                } finally {
                    recognizing = false
                }
            }
        }

    fun startPick() {
        pickLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
            Column {
                ZhixuTopAppBar(
                    containerColor = MaterialTheme.colorScheme.surface,
                    title = { Text("OCR识图", style = MaterialTheme.typography.titleMedium) },
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
                        TextButton(onClick = ::startPick, enabled = !recognizing) { Text("选择图片") }
                    },
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
        },
    ) { padding ->
        val scrollState = rememberScrollState()
        Column(
            modifier =
                Modifier
                    .padding(padding)
                    .padding(16.dp)
                    .verticalScroll(scrollState),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(onClick = ::startPick, enabled = !recognizing) { Text("从相册选择") }
                OutlinedButton(
                    onClick = {
                        ocrImageUri = null
                        ocrImageRelativePath = null
                        previewBitmap = null
                        result = null
                        enhancedMarkdown = null
                        aiDebugPrompt = null
                        aiDebugRaw = null
                    },
                    enabled = !recognizing && (ocrImageUri != null || result != null),
                ) {
                    Text("清空")
                }
            }

            if (recognizing) {
                Text("识别中...", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            val r = result
            if (r != null) {
                Text(
                    text = "已识别：${r.blocks.size} 行",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = enhancedMarkdown?.takeIf { it.isNotBlank() } ?: r.fullText,
                    style = MaterialTheme.typography.bodyMedium,
                )

                Spacer(Modifier.height(6.dp))

                val canManualAi =
                    aiSettings.enabled &&
                        aiSettings.global.useMode == AiUseMode.ManualOnly &&
                        aiSettings.ocr.enabled &&
                        aiSettings.ocr.useAiEnhance &&
                        !recognizing
                if (canManualAi && enhancedMarkdown.isNullOrBlank()) {
                    OutlinedButton(
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        onClick = {
                            scope.launch {
                                val enhanced =
                                    runCatching { aiService.enhanceOcrToMarkdown(r.fullText, aiSettings, vaultRootUri, repository) }
                                        .getOrNull()
                                enhancedMarkdown = enhanced?.markdown
                                aiDebugPrompt = enhanced?.debug?.prompt?.takeIf { it.isNotBlank() }
                                aiDebugRaw = enhanced?.debug?.rawResponse?.takeIf { it.isNotBlank() }
                                if (enhancedMarkdown.isNullOrBlank()) {
                                    snackbarHostState.showSnackbar("AI 整理失败（已回退为原始 OCR）")
                                }
                            }
                        },
                    ) {
                        Text("AI 整理为 Markdown")
                    }
                    Spacer(Modifier.height(6.dp))
                }

                val prompt = aiDebugPrompt
                val raw = aiDebugRaw
                if (!prompt.isNullOrBlank()) {
                    Text("Prompt（调试）", style = MaterialTheme.typography.titleSmall)
                    Text(prompt, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(6.dp))
                }
                if (!raw.isNullOrBlank()) {
                    Text("Raw Output（调试）", style = MaterialTheme.typography.titleSmall)
                    Text(raw, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(6.dp))
                }

                OutlinedButton(
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    enabled = !recognizing,
                    onClick = {
                        scope.launch {
                            val titleDate = DateTimeFormatter.ofPattern("yyyy-MM-dd").format(LocalDateTime.now())
                            val noteTitle = "图片识别 · $titleDate"

                            val baseText =
                                (enhancedMarkdown?.takeIf { it.isNotBlank() } ?: r.fullText)
                                    .let { if (aiSettings.ocr.cleanupWhitespace) normalizeOcrText(it) else it }
                            val markdown =
                                buildString {
                                    append("# ")
                                    append(noteTitle)
                                    append("\n\n")
                                    append(baseText.trim())
                                    append("\n")

                                    if (aiSettings.debug.keepOriginal && enhancedMarkdown?.isNotBlank() == true) {
                                        append("\n---\n\n")
                                        append("<details><summary>原始 OCR</summary>\n\n")
                                        append("```\n")
                                        append(r.fullText.trim())
                                        append("\n```\n")
                                        append("</details>\n")
                                    }
                                }

                            val created =
                                runCatching {
                                    repository.createDocWithContent(
                                        rootUri = vaultRootUri,
                                        fileName = noteTitle,
                                        content = markdown,
                                    )
                                }.getOrElse { e ->
                                    snackbarHostState.showSnackbar(e.message ?: "生成笔记失败")
                                    return@launch
                                }

                            runCatching {
                                VaultAutoSync.maybeUploadDoc(
                                    context = context,
                                    repository = repository,
                                    vaultRootUri = vaultRootUri,
                                    docUri = created.uri,
                                    force = true,
                                )
                            }
                            onCreated(created)
                        }
                    },
                ) {
                    Text("生成笔记并进入编辑")
                }
            } else {
                Text(
                    text = "选择一张图片开始识别。",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private fun normalizeOcrText(text: String): String {
    val lines =
        text
            .replace('\r', '\n')
            .split('\n')
            .map { it.trim() }
            .filter { it.isNotBlank() }
    return lines.joinToString("\n")
}

private fun decodeBitmap(context: android.content.Context, uri: Uri, maxDim: Int): Bitmap? {
    val resolver = context.contentResolver
    resolver.openInputStream(uri)?.use { input ->
        val bytes = input.readBytes()
        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
        val w = max(1, opts.outWidth)
        val h = max(1, opts.outHeight)
        var sample = 1
        while (w / sample > maxDim || h / sample > maxDim) sample *= 2
        val decodeOpts =
            BitmapFactory.Options().apply {
                inJustDecodeBounds = false
                inSampleSize = sample.coerceAtLeast(1)
            }
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, decodeOpts)
    }
    return null
}
