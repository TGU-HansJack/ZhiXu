package app.zhixu.ai

import app.zhixu.data.AiPreferences
import kotlinx.coroutines.flow.first

class AiOcrPostProcessor(
    private val prefs: AiPreferences,
    private val client: AiClient = AiClient(),
) {
    data class Result(
        val title: String?,
        val markdown: String?,
        val todos: List<String>,
    )

    suspend fun process(ocrText: String): Result? {
        val text = ocrText.trim()
        if (text.isBlank()) return null

        val state = prefs.state.first()
        if (!state.aiEnabled) return null
        if (state.ocrMode != AiPreferences.OcrMode.OCR_PLUS_AI) return null

        val cfg =
            AiClient.Config(
                enabled = state.aiEnabled,
                baseUrl = state.baseUrl,
                apiKey = state.apiKey,
                model = state.model,
                debug = state.debugEnabled,
            )

        val systemPrompt =
            """
你是一个“文本整理器”，只处理用户给出的 OCR 文本。
规则：
1) 只基于 OCR 文本工作，不要请求或假设图片内容；不要输出任何会导致上传图片的建议。
2) 输出必须是严格 JSON（不要 Markdown），字段：
   - title: string（<= 50 字）
   - markdown: string（整理后的 Markdown，不要包含 ```）
   - todos: string[]（从文本中抽取可执行待办，最多 20 条）
3) 如果无法确定标题，用“OCR 笔记”。
""".trimIndent()

        val userPrompt =
            """
OCR 文本如下（可能有错字、换行）：

$text
""".trimIndent()

        val json = client.chatJson(config = cfg, systemPrompt = systemPrompt, userPrompt = userPrompt) ?: return null
        val title = json.optString("title").takeIf { it.isNotBlank() }
        val markdown = json.optString("markdown").takeIf { it.isNotBlank() }
        val todos =
            buildList {
                val arr = json.optJSONArray("todos") ?: return@buildList
                for (i in 0 until arr.length()) {
                    val t = arr.optString(i).trim()
                    if (t.isNotBlank()) add(t)
                }
            }
        return Result(title = title, markdown = markdown, todos = todos)
    }
}

