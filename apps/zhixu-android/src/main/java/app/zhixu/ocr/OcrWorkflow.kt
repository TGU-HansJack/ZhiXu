package app.zhixu.ocr

import android.content.Context
import android.net.Uri
import app.zhixu.ai.AiOcrPostProcessor
import app.zhixu.data.AiPreferences
import app.zhixu.data.UiDoc
import app.zhixu.data.VaultRepository
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
    data class RunResult(
        val doc: UiDoc,
        val ocrTextEmpty: Boolean,
        val aiApplied: Boolean,
        val todosCreated: Int,
        val ocrError: String?,
    )

    suspend fun recognizeToNewNote(
        vaultRootUri: Uri,
        sourceImageUri: Uri,
    ): RunResult = withContext(Dispatchers.IO) {
        repository.ensureVaultStructure(vaultRootUri)
        val imported = repository.importOcrImage(vaultRootUri, sourceImageUri)

        val engine = engineProvider(vaultRootUri)
        val recognizeAttempt = runCatching { engine.recognize(imported.localFile) }
        val ocrError = recognizeAttempt.exceptionOrNull()?.message ?: recognizeAttempt.exceptionOrNull()?.javaClass?.simpleName
        val result =
            recognizeAttempt.getOrElse {
                OcrResult(
                    engine = "error",
                    error = ocrError,
                    elapsedMs = null,
                )
            }
        val ocrText = result.text.trim()
        val ocrTextEmpty = ocrText.isBlank()

        val ai = aiPostProcessor?.process(ocrText)
        val aiTitle = ai?.title?.trim().orEmpty()
        val title = aiTitle.ifBlank { buildDefaultTitle(result.text) }
        val created = repository.createDoc(vaultRootUri, title)
        val markdown =
            if (ai?.markdown != null) {
                val aiMarkdown = ai.markdown.replace("```", "").trim()
                buildString {
                    append("# ").append(created.baseName).append("\n\n")
                    append("![](").append(imported.vaultRelativePath).append(")\n\n")
                    append("## AI整理\n\n")
                    append(aiMarkdown).append("\n\n")
                    append("## OCR原文\n\n")
                    if (ocrTextEmpty) append("_（无识别结果）_\n") else append("```\n").append(ocrText).append("\n```\n")
                }
            } else {
                OcrMarkdown.toMarkdown(title = created.baseName, imageVaultRelativePath = imported.vaultRelativePath, result = result)
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
            ocrError = result.error ?: ocrError,
        )
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
