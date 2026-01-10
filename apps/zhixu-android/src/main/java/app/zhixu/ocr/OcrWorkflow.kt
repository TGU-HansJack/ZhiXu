package app.zhixu.ocr

import android.content.Context
import android.net.Uri
import app.zhixu.ai.AiOcrPostProcessor
import app.zhixu.core.tasks.TaskSmartParseResult
import app.zhixu.core.tasks.TaskSmartParser
import app.zhixu.data.AiPreferences
import app.zhixu.data.UiDoc
import app.zhixu.data.VaultRepository
import app.zhixu.ocr.ppocrv5.PpOcrV5OcrEngine
import app.zhixu.sync.VaultAutoSync
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.time.LocalTime
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
                val now = LocalDateTime.now()
                for (todo in ai.todos.take(20)) {
                    val ok =
                        runCatching {
                            val parsed = TaskSmartParser.parse(todo, now)
                            val resolved = resolveReminder(parsed, now)
                            repository.addTaskToInbox(
                                rootUri = vaultRootUri,
                                title = parsed.cleanedTitle.ifBlank { todo }.trim(),
                                dueDate = resolved.dueDate,
                                dueTime = resolved.dueTime,
                                remindAt = resolved.remindAt,
                                remindPersistent = parsed.remindPersistent,
                                tags = parsed.tags,
                                priority = parsed.priority,
                                repeat = parsed.repeat,
                            )
                        }.getOrDefault(false)
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

    suspend fun generateTodosFromOcrText(
        vaultRootUri: Uri,
        editedOcrText: String,
        limit: Int = 20,
    ): GenerateTodosResult = withContext(Dispatchers.IO) {
        val ocrText = editedOcrText.trim()
        if (ocrText.isBlank()) {
            return@withContext GenerateTodosResult(created = 0, total = 0, failed = 0, reason = "OCR 文本为空")
        }

        val prefs = aiPrefs
        if (prefs != null) {
            val state = prefs.state.first()
            if (!state.aiEnabled) {
                return@withContext GenerateTodosResult(created = 0, total = 0, failed = 0, reason = "AI 未启用")
            }
            if (state.ocrMode != AiPreferences.OcrMode.OCR_PLUS_AI) {
                return@withContext GenerateTodosResult(created = 0, total = 0, failed = 0, reason = "未开启 OCR+AI 整理")
            }
            if (state.apiKey.isBlank() || state.model.isBlank()) {
                return@withContext GenerateTodosResult(created = 0, total = 0, failed = 0, reason = "AI 未配置 model/apiKey")
            }
        }

        val post = aiPostProcessor ?: return@withContext GenerateTodosResult(created = 0, total = 0, failed = 0, reason = "AI 后处理未初始化")
        val ai = post.process(ocrText) ?: return@withContext GenerateTodosResult(created = 0, total = 0, failed = 0, reason = "AI 请求失败或未返回结果")
        val todos = ai.todos.take(limit)
        if (todos.isEmpty()) {
            return@withContext GenerateTodosResult(created = 0, total = 0, failed = 0, reason = "AI 未提取到待办")
        }

        val now = LocalDateTime.now()
        var created = 0
        var failed = 0
        for (todo in todos) {
            val ok =
                runCatching {
                    val parsed = TaskSmartParser.parse(todo, now)
                    val resolved = resolveReminder(parsed, now)
                    repository.addTaskToInbox(
                        rootUri = vaultRootUri,
                        title = parsed.cleanedTitle.ifBlank { todo }.trim(),
                        dueDate = resolved.dueDate,
                        dueTime = resolved.dueTime,
                        remindAt = resolved.remindAt,
                        remindPersistent = parsed.remindPersistent,
                        tags = parsed.tags,
                        priority = parsed.priority,
                        repeat = parsed.repeat,
                    )
                }.getOrDefault(false)
            if (ok) created += 1 else failed += 1
        }

        GenerateTodosResult(created = created, total = todos.size, failed = failed, reason = null)
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

    data class ReminderResolved(
        val dueDate: java.time.LocalDate?,
        val dueTime: java.time.LocalTime?,
        val remindAt: java.time.LocalDateTime?,
    )

    data class GenerateTodosResult(
        val created: Int,
        val total: Int,
        val failed: Int,
        val reason: String?,
    )

    private fun resolveReminder(parsed: app.zhixu.core.tasks.TaskSmartParseResult, now: java.time.LocalDateTime): ReminderResolved {
        var dueDate = parsed.dueDate
        var dueTime = if (parsed.allDay) null else parsed.dueTime
        val remindAt =
            when (val spec = parsed.remind) {
                null -> null
                is TaskSmartParseResult.ReminderSpec.FromNow -> {
                    val dt = spec.delta.addTo(now)
                    if (dueDate == null && dueTime == null) {
                        dueDate = dt.toLocalDate()
                        dueTime = dt.toLocalTime()
                    }
                    dt.takeIf { it.isAfter(now) }
                }
                TaskSmartParseResult.ReminderSpec.AtDue -> {
                    val baseDate = dueDate ?: return ReminderResolved(dueDate, dueTime, null)
                    val baseTime = dueTime ?: LocalTime.of(9, 0)
                    baseDate.atTime(baseTime).takeIf { it.isAfter(now) }
                }
                is TaskSmartParseResult.ReminderSpec.OffsetBefore -> {
                    val baseDate = dueDate ?: return ReminderResolved(dueDate, dueTime, null)
                    val baseTime = dueTime ?: LocalTime.of(9, 0)
                    spec.delta.subtractFrom(baseDate.atTime(baseTime)).takeIf { it.isAfter(now) }
                }
                is TaskSmartParseResult.ReminderSpec.OffsetAfter -> {
                    val baseDate = dueDate ?: return ReminderResolved(dueDate, dueTime, null)
                    val baseTime = dueTime ?: LocalTime.of(9, 0)
                    spec.delta.addTo(baseDate.atTime(baseTime)).takeIf { it.isAfter(now) }
                }
            }

        return ReminderResolved(
            dueDate = dueDate,
            dueTime = dueTime,
            remindAt = remindAt,
        )
    }
}
