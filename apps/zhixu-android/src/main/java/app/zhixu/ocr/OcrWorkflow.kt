package app.zhixu.ocr

import android.content.Context
import android.net.Uri
import app.zhixu.ai.AiOcrPostProcessor
import app.zhixu.data.AiPreferences
import app.zhixu.data.UiDoc
import app.zhixu.data.VaultRepository
import app.zhixu.ocr.ppocrv5.PpOcrV5OcrEngine
import app.zhixu.sync.VaultAutoSync
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

class OcrWorkflow(
    private val context: Context,
    private val repository: VaultRepository,
    private val engineProvider: (vaultRootUri: Uri) -> OcrEngine,
    private val aiPostProcessor: AiOcrPostProcessor? = null,
    private val aiPrefs: AiPreferences? = null,
) {
    data class RecognizeResult(
        val imported: OcrImportedImage,
        val result: OcrResult,
        val ocrError: String?,
    )

    data class RunResult(
        val doc: UiDoc,
        val ocrTextEmpty: Boolean,
        val aiApplied: Boolean,
        val todosCreated: Int,
        val ocrError: String?,
    )

    suspend fun warmUp(vaultRootUri: Uri) = withContext(Dispatchers.IO) {
        val engine = runCatching { engineProvider(vaultRootUri) }.getOrNull()
        val ppocr = engine as? PpOcrV5OcrEngine ?: return@withContext
        runCatching { ppocr.warmUp() }
    }

    suspend fun recognizeImage(
        vaultRootUri: Uri,
        sourceImageUri: Uri,
    ): RecognizeResult = withContext(Dispatchers.IO) {
        repository.ensureVaultStructure(vaultRootUri)
        val imported = repository.importOcrImage(vaultRootUri, sourceImageUri)

        val engine = engineProvider(vaultRootUri)
        val recognizeAttempt = runCatching { engine.recognize(imported.localFile) }
        val attemptError = recognizeAttempt.exceptionOrNull()?.message ?: recognizeAttempt.exceptionOrNull()?.javaClass?.simpleName
        val result =
            recognizeAttempt.getOrElse {
                OcrResult(
                    engine = "error",
                    error = attemptError,
                    elapsedMs = null,
                )
            }
        val ocrError = result.error ?: attemptError

        RecognizeResult(
            imported = imported,
            result = result,
            ocrError = ocrError,
        )
    }

    suspend fun createNoteFromRecognize(
        vaultRootUri: Uri,
        recognize: RecognizeResult,
        editedOcrText: String? = null,
    ): RunResult = withContext(Dispatchers.IO) {
        val ocrText = (editedOcrText ?: recognize.result.text).trim()
        val ocrTextEmpty = ocrText.isBlank()

        val ai = aiPostProcessor?.process(ocrText)
        val aiTitle = ai?.title?.trim().orEmpty()
        val title = aiTitle.ifBlank { buildDefaultTitle(ocrText) }
        val created = repository.createDoc(vaultRootUri, title)

        val markdown =
            if (ai?.markdown != null) {
                val aiMarkdown = ai.markdown.replace("```", "").trim()
                buildString {
                    append("# ").append(created.baseName).append("\n\n")
                    append("![](").append(recognize.imported.vaultRelativePath).append(")\n\n")
                    append("## AI整理\n\n")
                    append(aiMarkdown).append("\n\n")
                    append("## OCR原文\n\n")
                    if (ocrTextEmpty) append("_（无识别结果）_\n") else append("```\n").append(ocrText).append("\n```\n")
                }
            } else {
                buildString {
                    append("# ").append(created.baseName).append("\n\n")
                    append("![](").append(recognize.imported.vaultRelativePath).append(")\n\n")
                    append("## OCR\n\n")
                    if (ocrTextEmpty) {
                        append("_（无识别结果）_\n")
                    } else {
                        append("```\n")
                        append(ocrText).append("\n")
                        append("```\n")
                    }

                    if (recognize.result.engine.isNotBlank() || recognize.result.elapsedMs != null) {
                        append("\n---\n")
                        append("- engine: ").append(if (recognize.result.engine.isBlank()) "-" else recognize.result.engine).append("\n")
                        append("- elapsedMs: ").append(recognize.result.elapsedMs?.toString() ?: "-").append("\n")
                        append("- blocks: ").append(recognize.result.blocks.size).append("\n")
                    }

                    val err = recognize.ocrError?.trim().orEmpty()
                    if (err.isNotBlank()) {
                        if (!endsWith("\n")) append("\n")
                        if (!contains("\n---\n")) append("\n---\n")
                        append("- error: ").append(err.replace("\n", " ")).append("\n")
                    }
                }
            }
        repository.writeText(created.uri, markdown)
        runCatching { repository.indexDocUri(created.uri) }

        val prefs = aiPrefs
        var todosCreated = 0
        if (ai != null && prefs != null) {
            val state = prefs.state.first()
            if (state.aiEnabled && state.ocrMode == AiPreferences.OcrMode.OCR_PLUS_AI && state.ocrCreateTodos) {
                for (todo in ai.todos.take(20)) {
                    val ok = runCatching { repository.addTaskToInbox(rootUri = vaultRootUri, title = todo) }.getOrDefault(false)
                    if (ok) todosCreated += 1
                }
            }
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

        RunResult(
            doc = created,
            ocrTextEmpty = ocrTextEmpty,
            aiApplied = ai?.markdown != null,
            todosCreated = todosCreated,
            ocrError = recognize.ocrError,
        )
    }

    suspend fun recognizeToNewNote(
        vaultRootUri: Uri,
        sourceImageUri: Uri,
    ): RunResult = withContext(Dispatchers.IO) {
        val recognize = recognizeImage(vaultRootUri = vaultRootUri, sourceImageUri = sourceImageUri)
        createNoteFromRecognize(vaultRootUri = vaultRootUri, recognize = recognize)
    }

    private fun buildDefaultTitle(text: String): String {
        val firstLine =
            text.lineSequence()
                .map { it.trim() }
                .firstOrNull { it.isNotBlank() }
                ?.take(32)
                .orEmpty()
        if (firstLine.isNotBlank()) return "OCR - $firstLine"
        val ts = DateTimeFormatter.ofPattern("yyyyMMdd-HHmm").format(LocalDateTime.now())
        return "OCR-$ts"
    }
}
