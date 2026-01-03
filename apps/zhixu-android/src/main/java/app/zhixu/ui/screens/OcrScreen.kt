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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import app.zhixu.data.UiDoc
import app.zhixu.data.VaultRepository
import app.zhixu.ocr.MlKitOcrEngine
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

    var attachmentUri by remember { mutableStateOf<Uri?>(null) }
    var attachmentRelativePath by remember { mutableStateOf<String?>(null) }
    var previewBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var recognizing by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<OcrResult?>(null) }

    val pickLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
            if (uri == null) return@rememberLauncherForActivityResult
            scope.launch {
                recognizing = true
                result = null
                previewBitmap = null
                attachmentUri = null
                attachmentRelativePath = null
                try {
                    val ts = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss").format(LocalDateTime.now())
                    val imported =
                        repository.importAttachmentFromUri(
                            rootUri = vaultRootUri,
                            sourceUri = uri,
                            suggestedBaseName = "ocr_$ts",
                        )
                    attachmentUri = imported.uri
                    attachmentRelativePath = imported.relativePath

                    val bmp = withContext(Dispatchers.IO) { decodeBitmap(context, imported.uri, maxDim = 2200) }
                    previewBitmap = bmp
                    if (bmp == null) {
                        snackbarHostState.showSnackbar("无法读取图片")
                        return@launch
                    }

                    val engine = MlKitOcrEngine()
                    val ocr = engine.recognize(bmp)
                    result = ocr
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
                        attachmentUri = null
                        attachmentRelativePath = null
                        previewBitmap = null
                        result = null
                    },
                    enabled = !recognizing && (attachmentUri != null || result != null),
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
                    text = r.fullText,
                    style = MaterialTheme.typography.bodyMedium,
                )

                Spacer(Modifier.height(6.dp))

                val rel = attachmentRelativePath
                OutlinedButton(
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    enabled = !recognizing && rel != null,
                    onClick = {
                        scope.launch {
                            val relPath = rel ?: return@launch
                            val titleDate = DateTimeFormatter.ofPattern("yyyy-MM-dd").format(LocalDateTime.now())
                            val noteTitle = "图片笔记 · $titleDate"
                            val markdown =
                                buildString {
                                    append("# ")
                                    append(noteTitle)
                                    append("\n\n")
                                    append("![](")
                                    append(relPath)
                                    append(")\n\n---\n\n")
                                    append(r.fullText.trim())
                                    append("\n")
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
                            val aUri = attachmentUri
                            if (aUri != null) {
                                runCatching {
                                    VaultAutoSync.maybeUploadDoc(
                                        context = context,
                                        repository = repository,
                                        vaultRootUri = vaultRootUri,
                                        docUri = aUri,
                                        force = true,
                                    )
                                }
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

