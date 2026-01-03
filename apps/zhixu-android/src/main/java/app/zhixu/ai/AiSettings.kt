package app.zhixu.ai

import org.json.JSONObject

enum class AiUseMode {
    Auto,
    ManualOnly,
    Disabled,
}

enum class AiQualityPolicy {
    HighQuality,
    Balanced,
    LowCost,
}

enum class AiProviderType {
    OpenAICompatible,
    AzureOpenAI,
    Anthropic,
    Gemini,
    Ollama,
}

enum class OcrEngineType {
    PaddleOcr,
    MlKit,
}

data class AiScenarioModelRef(
    val useGlobalProvider: Boolean,
    val provider: AiProviderType,
    val model: String,
)

data class GlobalAiSettings(
    val useMode: AiUseMode,
    val quality: AiQualityPolicy,
)

data class OcrAiSettings(
    val enabled: Boolean,
    val engine: OcrEngineType,
    val useAiEnhance: Boolean,
    val autoMarkdown: Boolean,
    val autoTitle: Boolean,
    val extractTasks: Boolean,
    val cleanupWhitespace: Boolean,
    val aiModel: AiScenarioModelRef,
)

data class NoteAiSettings(
    val pasteEnhance: Boolean,
    val autoTitle: Boolean,
    val autoSplitOrSummarize: Boolean,
    val aiModel: AiScenarioModelRef,
)

data class TaskAiSettings(
    val extractTasks: Boolean,
    val parseDates: Boolean,
    val autoTodayUpcoming: Boolean,
    val aiModel: AiScenarioModelRef,
)

data class OpenAiCompatibleConfig(
    val baseUrl: String,
    val apiKey: String,
    val defaultModel: String,
)

data class AzureOpenAiConfig(
    val endpoint: String,
    val apiKey: String,
    val deployment: String,
    val apiVersion: String,
)

data class AnthropicConfig(
    val apiKey: String,
    val defaultModel: String,
)

data class GeminiConfig(
    val apiKey: String,
    val defaultModel: String,
)

data class OllamaConfig(
    val baseUrl: String,
    val defaultModel: String,
)

data class AiModelSettings(
    val provider: AiProviderType,
    val openAiCompatible: OpenAiCompatibleConfig,
    val azureOpenAi: AzureOpenAiConfig,
    val anthropic: AnthropicConfig,
    val gemini: GeminiConfig,
    val ollama: OllamaConfig,
)

data class AiDebugSettings(
    val keepOriginal: Boolean,
    val allowUndo: Boolean,
    val neverUploadAttachments: Boolean,
    val showPrompt: Boolean,
    val showRawOutput: Boolean,
    val logCalls: Boolean,
    val fallbackToNoAiOnFailure: Boolean,
)

data class AiSettings(
    val enabled: Boolean,
    val global: GlobalAiSettings,
    val ocr: OcrAiSettings,
    val note: NoteAiSettings,
    val task: TaskAiSettings,
    val model: AiModelSettings,
    val debug: AiDebugSettings,
) {
    fun toJson(): JSONObject =
        JSONObject()
            .put("schema", 1)
            .put("enabled", enabled)
            .put(
                "global",
                JSONObject()
                    .put("useMode", global.useMode.name)
                    .put("quality", global.quality.name),
            )
            .put(
                "ocr",
                JSONObject()
                    .put("enabled", ocr.enabled)
                    .put("engine", ocr.engine.name)
                    .put("useAiEnhance", ocr.useAiEnhance)
                    .put("autoMarkdown", ocr.autoMarkdown)
                    .put("autoTitle", ocr.autoTitle)
                    .put("extractTasks", ocr.extractTasks)
                    .put("cleanupWhitespace", ocr.cleanupWhitespace)
                    .put("aiModel", ocr.aiModel.toJson()),
            )
            .put(
                "note",
                JSONObject()
                    .put("pasteEnhance", note.pasteEnhance)
                    .put("autoTitle", note.autoTitle)
                    .put("autoSplitOrSummarize", note.autoSplitOrSummarize)
                    .put("aiModel", note.aiModel.toJson()),
            )
            .put(
                "task",
                JSONObject()
                    .put("extractTasks", task.extractTasks)
                    .put("parseDates", task.parseDates)
                    .put("autoTodayUpcoming", task.autoTodayUpcoming)
                    .put("aiModel", task.aiModel.toJson()),
            )
            .put(
                "model",
                JSONObject()
                    .put("provider", model.provider.name)
                    .put(
                        "openAiCompatible",
                        JSONObject()
                            .put("baseUrl", model.openAiCompatible.baseUrl)
                            .put("apiKey", model.openAiCompatible.apiKey)
                            .put("defaultModel", model.openAiCompatible.defaultModel),
                    )
                    .put(
                        "azureOpenAi",
                        JSONObject()
                            .put("endpoint", model.azureOpenAi.endpoint)
                            .put("apiKey", model.azureOpenAi.apiKey)
                            .put("deployment", model.azureOpenAi.deployment)
                            .put("apiVersion", model.azureOpenAi.apiVersion),
                    )
                    .put(
                        "anthropic",
                        JSONObject()
                            .put("apiKey", model.anthropic.apiKey)
                            .put("defaultModel", model.anthropic.defaultModel),
                    )
                    .put(
                        "gemini",
                        JSONObject()
                            .put("apiKey", model.gemini.apiKey)
                            .put("defaultModel", model.gemini.defaultModel),
                    )
                    .put(
                        "ollama",
                        JSONObject()
                            .put("baseUrl", model.ollama.baseUrl)
                            .put("defaultModel", model.ollama.defaultModel),
                    ),
            )
            .put(
                "debug",
                JSONObject()
                    .put("keepOriginal", debug.keepOriginal)
                    .put("allowUndo", debug.allowUndo)
                    .put("neverUploadAttachments", debug.neverUploadAttachments)
                    .put("showPrompt", debug.showPrompt)
                    .put("showRawOutput", debug.showRawOutput)
                    .put("logCalls", debug.logCalls)
                    .put("fallbackToNoAiOnFailure", debug.fallbackToNoAiOnFailure),
            )

    companion object {
        fun default(): AiSettings =
            AiSettings(
                enabled = false,
                global =
                    GlobalAiSettings(
                        useMode = AiUseMode.Auto,
                        quality = AiQualityPolicy.Balanced,
                    ),
                ocr =
                    OcrAiSettings(
                        enabled = true,
                        engine = OcrEngineType.PaddleOcr,
                        useAiEnhance = false,
                        autoMarkdown = true,
                        autoTitle = true,
                        extractTasks = true,
                        cleanupWhitespace = true,
                        aiModel = AiScenarioModelRef(useGlobalProvider = true, provider = AiProviderType.OpenAICompatible, model = "gpt-4o-mini"),
                    ),
                note =
                    NoteAiSettings(
                        pasteEnhance = false,
                        autoTitle = false,
                        autoSplitOrSummarize = false,
                        aiModel = AiScenarioModelRef(useGlobalProvider = true, provider = AiProviderType.OpenAICompatible, model = "gpt-4o-mini"),
                    ),
                task =
                    TaskAiSettings(
                        extractTasks = false,
                        parseDates = false,
                        autoTodayUpcoming = false,
                        aiModel = AiScenarioModelRef(useGlobalProvider = true, provider = AiProviderType.OpenAICompatible, model = "gpt-4o-mini"),
                    ),
                model =
                    AiModelSettings(
                        provider = AiProviderType.OpenAICompatible,
                        openAiCompatible =
                            OpenAiCompatibleConfig(
                                baseUrl = "https://api.openai.com/v1",
                                apiKey = "",
                                defaultModel = "gpt-4o-mini",
                            ),
                        azureOpenAi =
                            AzureOpenAiConfig(
                                endpoint = "",
                                apiKey = "",
                                deployment = "",
                                apiVersion = "2024-06-01",
                            ),
                        anthropic =
                            AnthropicConfig(
                                apiKey = "",
                                defaultModel = "claude-3-5-sonnet-latest",
                            ),
                        gemini =
                            GeminiConfig(
                                apiKey = "",
                                defaultModel = "gemini-1.5-flash",
                            ),
                        ollama =
                            OllamaConfig(
                                baseUrl = "http://127.0.0.1:11434",
                                defaultModel = "qwen2.5:7b",
                            ),
                    ),
                debug =
                    AiDebugSettings(
                        keepOriginal = true,
                        allowUndo = true,
                        neverUploadAttachments = true,
                        showPrompt = false,
                        showRawOutput = false,
                        logCalls = false,
                        fallbackToNoAiOnFailure = true,
                    ),
            )

        fun fromJson(raw: String?): AiSettings {
            val base = default()
            val text = raw?.trim().orEmpty()
            if (text.isBlank()) return base
            val obj = runCatching { JSONObject(text) }.getOrNull() ?: return base
            val schema = obj.optInt("schema", 0)
            if (schema <= 0) return base

            fun parseUseMode(v: String?): AiUseMode =
                runCatching { AiUseMode.valueOf(v ?: "") }.getOrDefault(base.global.useMode)
            fun parseQuality(v: String?): AiQualityPolicy =
                runCatching { AiQualityPolicy.valueOf(v ?: "") }.getOrDefault(base.global.quality)
            fun parseProvider(v: String?): AiProviderType =
                runCatching { AiProviderType.valueOf(v ?: "") }.getOrDefault(base.model.provider)
            fun parseOcrEngine(v: String?): OcrEngineType =
                runCatching { OcrEngineType.valueOf(v ?: "") }.getOrDefault(base.ocr.engine)

            val globalObj = obj.optJSONObject("global") ?: JSONObject()
            val ocrObj = obj.optJSONObject("ocr") ?: JSONObject()
            val noteObj = obj.optJSONObject("note") ?: JSONObject()
            val taskObj = obj.optJSONObject("task") ?: JSONObject()
            val modelObj = obj.optJSONObject("model") ?: JSONObject()
            val debugObj = obj.optJSONObject("debug") ?: JSONObject()

            val global =
                GlobalAiSettings(
                    useMode = parseUseMode(globalObj.optString("useMode", base.global.useMode.name)),
                    quality = parseQuality(globalObj.optString("quality", base.global.quality.name)),
                )

            val model =
                AiModelSettings(
                    provider = parseProvider(modelObj.optString("provider", base.model.provider.name)),
                    openAiCompatible =
                        OpenAiCompatibleConfig(
                            baseUrl = modelObj.optJSONObject("openAiCompatible")?.optString("baseUrl", base.model.openAiCompatible.baseUrl)
                                ?: base.model.openAiCompatible.baseUrl,
                            apiKey = modelObj.optJSONObject("openAiCompatible")?.optString("apiKey", base.model.openAiCompatible.apiKey)
                                ?: base.model.openAiCompatible.apiKey,
                            defaultModel = modelObj.optJSONObject("openAiCompatible")?.optString("defaultModel", base.model.openAiCompatible.defaultModel)
                                ?: base.model.openAiCompatible.defaultModel,
                        ),
                    azureOpenAi =
                        AzureOpenAiConfig(
                            endpoint = modelObj.optJSONObject("azureOpenAi")?.optString("endpoint", base.model.azureOpenAi.endpoint)
                                ?: base.model.azureOpenAi.endpoint,
                            apiKey = modelObj.optJSONObject("azureOpenAi")?.optString("apiKey", base.model.azureOpenAi.apiKey)
                                ?: base.model.azureOpenAi.apiKey,
                            deployment = modelObj.optJSONObject("azureOpenAi")?.optString("deployment", base.model.azureOpenAi.deployment)
                                ?: base.model.azureOpenAi.deployment,
                            apiVersion = modelObj.optJSONObject("azureOpenAi")?.optString("apiVersion", base.model.azureOpenAi.apiVersion)
                                ?: base.model.azureOpenAi.apiVersion,
                        ),
                    anthropic =
                        AnthropicConfig(
                            apiKey = modelObj.optJSONObject("anthropic")?.optString("apiKey", base.model.anthropic.apiKey)
                                ?: base.model.anthropic.apiKey,
                            defaultModel = modelObj.optJSONObject("anthropic")?.optString("defaultModel", base.model.anthropic.defaultModel)
                                ?: base.model.anthropic.defaultModel,
                        ),
                    gemini =
                        GeminiConfig(
                            apiKey = modelObj.optJSONObject("gemini")?.optString("apiKey", base.model.gemini.apiKey)
                                ?: base.model.gemini.apiKey,
                            defaultModel = modelObj.optJSONObject("gemini")?.optString("defaultModel", base.model.gemini.defaultModel)
                                ?: base.model.gemini.defaultModel,
                        ),
                    ollama =
                        OllamaConfig(
                            baseUrl = modelObj.optJSONObject("ollama")?.optString("baseUrl", base.model.ollama.baseUrl)
                                ?: base.model.ollama.baseUrl,
                            defaultModel = modelObj.optJSONObject("ollama")?.optString("defaultModel", base.model.ollama.defaultModel)
                                ?: base.model.ollama.defaultModel,
                        ),
                )

            fun parseScenario(obj: JSONObject?, fallback: AiScenarioModelRef): AiScenarioModelRef {
                val o = obj ?: JSONObject()
                val useGlobalProvider = o.optBoolean("useGlobalProvider", fallback.useGlobalProvider)
                val provider = runCatching { AiProviderType.valueOf(o.optString("provider", fallback.provider.name)) }.getOrDefault(fallback.provider)
                val modelStr = o.optString("model", fallback.model)
                return AiScenarioModelRef(useGlobalProvider = useGlobalProvider, provider = provider, model = modelStr)
            }

            val ocr =
                OcrAiSettings(
                    enabled = ocrObj.optBoolean("enabled", base.ocr.enabled),
                    engine = parseOcrEngine(ocrObj.optString("engine", base.ocr.engine.name)),
                    useAiEnhance = ocrObj.optBoolean("useAiEnhance", base.ocr.useAiEnhance),
                    autoMarkdown = ocrObj.optBoolean("autoMarkdown", base.ocr.autoMarkdown),
                    autoTitle = ocrObj.optBoolean("autoTitle", base.ocr.autoTitle),
                    extractTasks = ocrObj.optBoolean("extractTasks", base.ocr.extractTasks),
                    cleanupWhitespace = ocrObj.optBoolean("cleanupWhitespace", base.ocr.cleanupWhitespace),
                    aiModel = parseScenario(ocrObj.optJSONObject("aiModel"), base.ocr.aiModel),
                )

            val note =
                NoteAiSettings(
                    pasteEnhance = noteObj.optBoolean("pasteEnhance", base.note.pasteEnhance),
                    autoTitle = noteObj.optBoolean("autoTitle", base.note.autoTitle),
                    autoSplitOrSummarize = noteObj.optBoolean("autoSplitOrSummarize", base.note.autoSplitOrSummarize),
                    aiModel = parseScenario(noteObj.optJSONObject("aiModel"), base.note.aiModel),
                )

            val task =
                TaskAiSettings(
                    extractTasks = taskObj.optBoolean("extractTasks", base.task.extractTasks),
                    parseDates = taskObj.optBoolean("parseDates", base.task.parseDates),
                    autoTodayUpcoming = taskObj.optBoolean("autoTodayUpcoming", base.task.autoTodayUpcoming),
                    aiModel = parseScenario(taskObj.optJSONObject("aiModel"), base.task.aiModel),
                )

            val debug =
                AiDebugSettings(
                    keepOriginal = debugObj.optBoolean("keepOriginal", base.debug.keepOriginal),
                    allowUndo = debugObj.optBoolean("allowUndo", base.debug.allowUndo),
                    neverUploadAttachments = debugObj.optBoolean("neverUploadAttachments", base.debug.neverUploadAttachments),
                    showPrompt = debugObj.optBoolean("showPrompt", base.debug.showPrompt),
                    showRawOutput = debugObj.optBoolean("showRawOutput", base.debug.showRawOutput),
                    logCalls = debugObj.optBoolean("logCalls", base.debug.logCalls),
                    fallbackToNoAiOnFailure = debugObj.optBoolean("fallbackToNoAiOnFailure", base.debug.fallbackToNoAiOnFailure),
                )

            return AiSettings(
                enabled = obj.optBoolean("enabled", base.enabled),
                global = global,
                ocr = ocr,
                note = note,
                task = task,
                model = model,
                debug = debug,
            )
        }
    }
}

private fun AiScenarioModelRef.toJson(): JSONObject =
    JSONObject()
        .put("useGlobalProvider", useGlobalProvider)
        .put("provider", provider.name)
        .put("model", model)
