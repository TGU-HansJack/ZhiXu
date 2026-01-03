package app.zhixu.ai

import android.net.Uri
import app.zhixu.data.VaultRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

data class AiCallDebug(
    val prompt: String,
    val rawResponse: String,
)

data class AiEnhanceResult(
    val markdown: String,
    val debug: AiCallDebug?,
)

class AiService(
    private val http: OkHttpClient = OkHttpClient(),
) {
    suspend fun enhanceOcrToMarkdown(
        ocrText: String,
        settings: AiSettings,
        vaultRootUri: Uri?,
        repository: VaultRepository?,
    ): AiEnhanceResult? =
        withContext(Dispatchers.IO) {
            if (!settings.enabled) return@withContext null
            if (settings.global.useMode == AiUseMode.Disabled) return@withContext null
            if (!settings.ocr.enabled || !settings.ocr.useAiEnhance) return@withContext null
            if (ocrText.isBlank()) return@withContext null

            val prompt = buildOcrPrompt(ocrText, settings)
            val provider =
                if (settings.ocr.aiModel.useGlobalProvider) settings.model.provider else settings.ocr.aiModel.provider
            val model =
                settings.ocr.aiModel.model.trim().ifBlank {
                    when (provider) {
                        AiProviderType.OpenAICompatible -> settings.model.openAiCompatible.defaultModel
                        AiProviderType.AzureOpenAI -> settings.model.azureOpenAi.deployment
                        AiProviderType.Anthropic -> settings.model.anthropic.defaultModel
                        AiProviderType.Gemini -> settings.model.gemini.defaultModel
                        AiProviderType.Ollama -> settings.model.ollama.defaultModel
                    }
                }

            val (markdown, raw) =
                when (provider) {
                    AiProviderType.OpenAICompatible -> callOpenAiCompatible(prompt, settings.model.openAiCompatible, model)
                    AiProviderType.AzureOpenAI -> callAzureOpenAi(prompt, settings.model.azureOpenAi, model)
                    AiProviderType.Anthropic -> callAnthropic(prompt, settings.model.anthropic, model)
                    AiProviderType.Gemini -> callGemini(prompt, settings.model.gemini, model)
                    AiProviderType.Ollama -> callOllama(prompt, settings.model.ollama, model)
                }

            if (settings.debug.logCalls && vaultRootUri != null && repository != null) {
                runCatching {
                    val dir = repository.ensureVaultDirectory(vaultRootUri, ".zhixu/ai/logs") ?: return@runCatching
                    val file =
                        repository.ensureVaultFile(vaultRootUri, ".zhixu/ai/logs/ai_calls.log", mimeType = "text/plain")
                            ?: return@runCatching
                    val entry =
                        buildString {
                            append("ts=")
                            append(System.currentTimeMillis())
                            append(" provider=")
                            append(provider.name)
                            append(" model=")
                            append(model)
                            append("\n")
                            append("prompt:\n")
                            append(prompt)
                            append("\n\nraw:\n")
                            append(raw)
                            append("\n\n---\n\n")
                        }
                    repository.appendText(file.uri, entry)
                    dir.uri.toString()
                }
            }

            val dbg =
                if (settings.debug.showPrompt || settings.debug.showRawOutput) {
                    AiCallDebug(
                        prompt = if (settings.debug.showPrompt) prompt else "",
                        rawResponse = if (settings.debug.showRawOutput) raw else "",
                    )
                } else {
                    null
                }

            AiEnhanceResult(markdown = markdown.trim(), debug = dbg)
        }

    private fun buildOcrPrompt(ocrText: String, settings: AiSettings): String {
        val sb = StringBuilder()
        sb.append("你是一个中文笔记整理助手。\n\n")
        sb.append("下面是一段通过 OCR 从图片中识别的文字，可能存在：\n")
        sb.append("- 错别字\n- 换行不合理\n- 标点缺失\n- 列表被打散\n\n")
        sb.append("你的任务：\n")
        sb.append("1. 修正明显的 OCR 错误（不要改变原意）\n")
        sb.append("2. 按语义整理段落\n")
        if (settings.ocr.extractTasks) sb.append("3. 如果内容像清单 / 计划 / 待办事项，请输出 Markdown 任务列表（- [ ]）\n")
        if (settings.ocr.autoTitle) sb.append("4. 为内容生成一个简短标题（一级标题）\n")
        sb.append("\n输出要求：\n- 只输出 Markdown\n- 不要解释\n- 不要加多余内容\n\n")
        sb.append("OCR 文本：\n")
        sb.append(ocrText)
        sb.append("\n")
        return sb.toString()
    }

    private fun callOpenAiCompatible(prompt: String, cfg: OpenAiCompatibleConfig, model: String): Pair<String, String> {
        val base = cfg.baseUrl.trimEnd('/')
        val url = "$base/chat/completions"
        val body =
            JSONObject()
                .put("model", model)
                .put(
                    "messages",
                    JSONArray()
                        .put(JSONObject().put("role", "system").put("content", "你是一个中文笔记整理助手"))
                        .put(JSONObject().put("role", "user").put("content", prompt)),
                )
                .toString()

        val req =
            Request.Builder()
                .url(url)
                .header("Authorization", "Bearer ${cfg.apiKey}")
                .post(body.toRequestBody(JSON))
                .build()

        http.newCall(req).execute().use { res ->
            val raw = res.body?.string().orEmpty()
            if (!res.isSuccessful) error("AI调用失败: ${res.code} ${raw.take(300)}")
            val obj = JSONObject(raw)
            val content = obj.optJSONArray("choices")?.optJSONObject(0)?.optJSONObject("message")?.optString("content").orEmpty()
            return content to raw
        }
    }

    private fun callAzureOpenAi(prompt: String, cfg: AzureOpenAiConfig, model: String): Pair<String, String> {
        val endpoint = cfg.endpoint.trimEnd('/')
        val deployment = model.ifBlank { cfg.deployment }
        val url = "$endpoint/openai/deployments/$deployment/chat/completions?api-version=${cfg.apiVersion}"
        val body =
            JSONObject()
                .put(
                    "messages",
                    JSONArray()
                        .put(JSONObject().put("role", "system").put("content", "你是一个中文笔记整理助手"))
                        .put(JSONObject().put("role", "user").put("content", prompt)),
                )
                .toString()
        val req =
            Request.Builder()
                .url(url)
                .header("api-key", cfg.apiKey)
                .post(body.toRequestBody(JSON))
                .build()
        http.newCall(req).execute().use { res ->
            val raw = res.body?.string().orEmpty()
            if (!res.isSuccessful) error("AI调用失败: ${res.code} ${raw.take(300)}")
            val obj = JSONObject(raw)
            val content = obj.optJSONArray("choices")?.optJSONObject(0)?.optJSONObject("message")?.optString("content").orEmpty()
            return content to raw
        }
    }

    private fun callAnthropic(prompt: String, cfg: AnthropicConfig, model: String): Pair<String, String> {
        val url = "https://api.anthropic.com/v1/messages"
        val body =
            JSONObject()
                .put("model", model)
                .put("max_tokens", 2048)
                .put(
                    "messages",
                    JSONArray()
                        .put(
                            JSONObject()
                                .put("role", "user")
                                .put(
                                    "content",
                                    JSONArray().put(JSONObject().put("type", "text").put("text", prompt)),
                                ),
                        ),
                )
                .toString()
        val req =
            Request.Builder()
                .url(url)
                .header("x-api-key", cfg.apiKey)
                .header("anthropic-version", "2023-06-01")
                .post(body.toRequestBody(JSON))
                .build()
        http.newCall(req).execute().use { res ->
            val raw = res.body?.string().orEmpty()
            if (!res.isSuccessful) error("AI调用失败: ${res.code} ${raw.take(300)}")
            val obj = JSONObject(raw)
            val content = obj.optJSONArray("content")?.optJSONObject(0)?.optString("text").orEmpty()
            return content to raw
        }
    }

    private fun callGemini(prompt: String, cfg: GeminiConfig, model: String): Pair<String, String> {
        val m = model.ifBlank { cfg.defaultModel }
        val url = "https://generativelanguage.googleapis.com/v1beta/models/$m:generateContent?key=${cfg.apiKey}"
        val body =
            JSONObject()
                .put(
                    "contents",
                    JSONArray().put(
                        JSONObject().put(
                            "parts",
                            JSONArray().put(JSONObject().put("text", prompt)),
                        ),
                    ),
                )
                .toString()
        val req = Request.Builder().url(url).post(body.toRequestBody(JSON)).build()
        http.newCall(req).execute().use { res ->
            val raw = res.body?.string().orEmpty()
            if (!res.isSuccessful) error("AI调用失败: ${res.code} ${raw.take(300)}")
            val obj = JSONObject(raw)
            val content =
                obj.optJSONArray("candidates")
                    ?.optJSONObject(0)
                    ?.optJSONObject("content")
                    ?.optJSONArray("parts")
                    ?.optJSONObject(0)
                    ?.optString("text")
                    .orEmpty()
            return content to raw
        }
    }

    private fun callOllama(prompt: String, cfg: OllamaConfig, model: String): Pair<String, String> {
        val base = cfg.baseUrl.trimEnd('/')
        val url = "$base/api/chat"
        val body =
            JSONObject()
                .put("model", model.ifBlank { cfg.defaultModel })
                .put("stream", false)
                .put(
                    "messages",
                    JSONArray()
                        .put(JSONObject().put("role", "system").put("content", "你是一个中文笔记整理助手"))
                        .put(JSONObject().put("role", "user").put("content", prompt)),
                )
                .toString()
        val req = Request.Builder().url(url).post(body.toRequestBody(JSON)).build()
        http.newCall(req).execute().use { res ->
            val raw = res.body?.string().orEmpty()
            if (!res.isSuccessful) error("AI调用失败: ${res.code} ${raw.take(300)}")
            val obj = JSONObject(raw)
            val content = obj.optJSONObject("message")?.optString("content").orEmpty()
            return content to raw
        }
    }

    companion object {
        private val JSON = "application/json; charset=utf-8".toMediaType()
    }
}

