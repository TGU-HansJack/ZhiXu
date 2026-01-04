package app.zhixu.ai

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

class AiClient(
    private val httpClient: OkHttpClient = OkHttpClient(),
) {
    data class Config(
        val enabled: Boolean,
        val baseUrl: String,
        val apiKey: String,
        val model: String,
        val debug: Boolean,
    )

    suspend fun chatJson(
        config: Config,
        systemPrompt: String,
        userPrompt: String,
        temperature: Double = 0.2,
    ): JSONObject? = withContext(Dispatchers.IO) {
        if (!config.enabled) return@withContext null
        val base = config.baseUrl.trim().trimEnd('/')
        if (base.isBlank()) return@withContext null
        if (config.apiKey.isBlank()) return@withContext null
        val model = config.model.trim().ifBlank { return@withContext null }

        val url = "$base/chat/completions"
        val payload =
            JSONObject()
                .put("model", model)
                .put("temperature", temperature)
                .put(
                    "messages",
                    JSONArray()
                        .put(JSONObject().put("role", "system").put("content", systemPrompt))
                        .put(JSONObject().put("role", "user").put("content", userPrompt)),
                )
        val body = payload.toString().toRequestBody("application/json".toMediaType())
        val request =
            Request.Builder()
                .url(url)
                .post(body)
                .header("Authorization", "Bearer ${config.apiKey}")
                .header("Accept", "application/json")
                .build()

        httpClient.newCall(request).execute().use { resp ->
            val raw = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) {
                if (config.debug) {
                    return@withContext JSONObject().put("error", "HTTP ${resp.code}").put("body", raw)
                }
                return@withContext null
            }
            val obj = runCatching { JSONObject(raw) }.getOrNull() ?: return@withContext null
            val content =
                obj.optJSONArray("choices")
                    ?.optJSONObject(0)
                    ?.optJSONObject("message")
                    ?.optString("content")
                    .orEmpty()
                    .trim()
            if (content.isBlank()) return@withContext null
            runCatching { JSONObject(content) }.getOrNull()
        }
    }
}

