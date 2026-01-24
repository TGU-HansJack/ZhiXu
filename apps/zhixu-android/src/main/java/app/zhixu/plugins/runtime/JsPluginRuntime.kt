package app.zhixu.plugins.runtime

import android.content.Context
import android.net.Uri
import android.os.Build
import android.util.Log
import app.zhixu.BuildConfig
import app.zhixu.data.AppLogRepository
import app.zhixu.data.vaultRootToDocumentFile
import app.zhixu.plugins.PluginManifest
import app.zhixu.plugins.PluginRepository
import androidx.documentfile.provider.DocumentFile
import com.eclipsesource.v8.JavaCallback
import com.eclipsesource.v8.JavaVoidCallback
import com.eclipsesource.v8.V8
import com.eclipsesource.v8.V8Array
import com.eclipsesource.v8.V8Function
import com.eclipsesource.v8.V8Object
import com.eclipsesource.v8.V8ResultUndefined
import com.eclipsesource.v8.V8ScriptCompilationException
import com.eclipsesource.v8.V8ScriptExecutionException
import com.eclipsesource.v8.V8Value
import com.eclipsesource.v8.utils.V8ObjectUtils
import kotlinx.coroutines.CancellationException
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

data class PluginActionResult(
    val ok: Boolean,
    val message: String,
    val setText: String? = null,
)

data class PluginCallResult(
    val ok: Boolean,
    val message: String,
    val data: Any? = null,
)

data class EditorActionContext(
    val docUri: Uri,
    val title: String,
    val fileName: String,
    val text: String,
)

class JsPluginRuntime(
    private val appContext: Context,
    private val pluginRepo: PluginRepository,
    private val http: OkHttpClient = OkHttpClient(),
    private val appLogs: AppLogRepository? = null,
    private val debugLogging: Boolean = false,
) {
    suspend fun runEditorAction(
        rootUri: Uri,
        pluginId: String,
        actionId: String,
        ctx: EditorActionContext,
    ): PluginActionResult {
        val manifest = pluginRepo.readPluginManifest(rootUri, pluginId) ?: return PluginActionResult(false, "Plugin manifest not found: $pluginId")
        val entryNames = entryCandidates(manifest)
        val (entryName, entryText) =
            entryNames.firstNotNullOfOrNull { name ->
                pluginRepo.readPluginFileText(rootUri, pluginId, name)?.let { name to it }
            } ?: return PluginActionResult(false, "Entry script not found: ${entryNames.joinToString()}")

        val cfg = pluginRepo.readPluginConfig(rootUri, pluginId)

        val v8 =
            try {
                V8.createV8Runtime()
            } catch (e: Throwable) {
                if (e is CancellationException) throw e
                val msg = buildRuntimeUnavailableMessage(e)
                return PluginActionResult(false, "Plugin runtime unavailable: $msg")
            }
        try {
            V8Object(v8).useV8 { module ->
                V8Object(v8).useV8 { exports ->
                    module.add("exports", exports)
                    v8.add("module", module)
                    v8.add("exports", exports)

                    V8Object(v8).useV8 { api ->
                        registerApi(
                            v8,
                            api,
                            appContext = appContext,
                            rootUri = rootUri,
                            pluginId = pluginId,
                            http = http,
                            appLogs = appLogs,
                            debugLogging = debugLogging,
                        )
                        v8.add("api", api)

                        v8.executeVoidScript(entryText, entryName, 1)

                        module.getObject("exports").useV8 { outExports ->
                            if (outExports.isUndefined) {
                                return PluginActionResult(false, "Invalid module.exports in $entryName")
                            }

                            outExports.getObject("actions").useV8 { actions ->
                                if (actions.isUndefined) {
                                    return PluginActionResult(false, "Missing module.exports.actions in $entryName")
                                }

                                val fnAny =
                                    try {
                                        actions.get(actionId)
                                    } catch (_: V8ResultUndefined) {
                                        V8.getUndefined()
                                    }

                                (fnAny as? V8Function)?.useV8 { fn ->
                                    buildEditorContext(v8, manifest, ctx, cfg).useV8 { jsCtx ->
                                        V8Array(v8).useV8 { params ->
                                            params.push(jsCtx)
                                            val result = fn.call(actions, params)
                                            return parseResult(result)
                                        }
                                    }
                                } ?: run {
                                    releaseIfV8Value(fnAny)
                                    return PluginActionResult(false, "Missing action '$actionId' in $entryName")
                                }
                            }
                        }
                    }
                }
            }
        } catch (e: V8ScriptExecutionException) {
            val where = e.fileName?.takeIf { it.isNotBlank() }?.let { "$it:${e.lineNumber}" } ?: "script"
            val msg = e.message?.takeIf { it.isNotBlank() } ?: e.javaClass.simpleName
            return PluginActionResult(false, "Plugin error ($where): $msg")
        } catch (e: V8ScriptCompilationException) {
            val where = e.fileName?.takeIf { it.isNotBlank() }?.let { "$it:${e.lineNumber}" } ?: "script"
            val msg = e.message?.takeIf { it.isNotBlank() } ?: e.javaClass.simpleName
            return PluginActionResult(false, "Plugin error ($where): $msg")
        } catch (e: Throwable) {
            if (e is CancellationException) throw e
            return PluginActionResult(false, "Plugin error: ${e.message ?: e.javaClass.simpleName}")
        } finally {
            runCatching { v8.close() }
        }
    }

    suspend fun runAction(
        rootUri: Uri,
        pluginId: String,
        actionId: String,
        input: JSONObject? = null,
    ): PluginCallResult {
        val manifest = pluginRepo.readPluginManifest(rootUri, pluginId) ?: return PluginCallResult(false, "Plugin manifest not found: $pluginId")
        val entryNames = entryCandidates(manifest)
        val (entryName, entryText) =
            entryNames.firstNotNullOfOrNull { name ->
                pluginRepo.readPluginFileText(rootUri, pluginId, name)?.let { name to it }
            } ?: return PluginCallResult(false, "Entry script not found: ${entryNames.joinToString()}")

        val cfg = pluginRepo.readPluginConfig(rootUri, pluginId)

        val v8 =
            try {
                V8.createV8Runtime()
            } catch (e: Throwable) {
                if (e is CancellationException) throw e
                val msg = buildRuntimeUnavailableMessage(e)
                return PluginCallResult(false, "Plugin runtime unavailable: $msg")
            }

        try {
            V8Object(v8).useV8 { module ->
                V8Object(v8).useV8 { exports ->
                    module.add("exports", exports)
                    v8.add("module", module)
                    v8.add("exports", exports)

                    V8Object(v8).useV8 { api ->
                        registerApi(
                            v8,
                            api,
                            appContext = appContext,
                            rootUri = rootUri,
                            pluginId = pluginId,
                            http = http,
                            appLogs = appLogs,
                            debugLogging = debugLogging,
                        )
                        v8.add("api", api)

                        v8.executeVoidScript(entryText, entryName, 1)

                        module.getObject("exports").useV8 { outExports ->
                            if (outExports.isUndefined) return PluginCallResult(false, "Invalid module.exports in $entryName")

                            outExports.getObject("actions").useV8 { actions ->
                                if (actions.isUndefined) return PluginCallResult(false, "Missing module.exports.actions in $entryName")

                                val fnAny =
                                    try {
                                        actions.get(actionId)
                                    } catch (_: V8ResultUndefined) {
                                        V8.getUndefined()
                                    }

                                (fnAny as? V8Function)?.useV8 { fn ->
                                    buildActionContext(v8, manifest, rootUri = rootUri, input = input, cfg = cfg).useV8 { jsCtx ->
                                        V8Array(v8).useV8 { params ->
                                            params.push(jsCtx)
                                            params.push(api)
                                            val inputVal = jsonToV8Value(v8, input ?: JSONObject())
                                            pushV8Value(params, inputVal)
                                            val result = fn.call(actions, params)
                                            return parseCallResult(result)
                                        }
                                    }
                                } ?: run {
                                    releaseIfV8Value(fnAny)
                                    return PluginCallResult(false, "Missing action '$actionId' in $entryName")
                                }
                            }
                        }
                    }
                }
            }
        } catch (e: V8ScriptCompilationException) {
            val where = e.fileName?.takeIf { it.isNotBlank() }?.let { "$it:${e.lineNumber}" } ?: "script"
            val msg = e.message?.takeIf { it.isNotBlank() } ?: e.javaClass.simpleName
            return PluginCallResult(false, "Plugin error ($where): $msg")
        } catch (e: V8ScriptExecutionException) {
            val where = e.fileName?.takeIf { it.isNotBlank() }?.let { "$it:${e.lineNumber}" } ?: "script"
            val msg = e.message?.takeIf { it.isNotBlank() } ?: e.javaClass.simpleName
            return PluginCallResult(false, "Plugin error ($where): $msg")
        } catch (e: Throwable) {
            if (e is CancellationException) throw e
            return PluginCallResult(false, "Plugin error: ${e.message ?: e.javaClass.simpleName}")
        } finally {
            runCatching { v8.close() }
        }
    }

    private fun buildRuntimeUnavailableMessage(e: Throwable): String {
        val raw = e.message?.takeIf { it.isNotBlank() } ?: e.javaClass.simpleName
        val abis = Build.SUPPORTED_ABIS?.joinToString() ?: "unknown"
        val looksLikeMissingJ2v8 =
            e is UnsatisfiedLinkError ||
                raw.contains("J2v8 native library not", ignoreCase = true) ||
                raw.contains("native library not loaded", ignoreCase = true)

        if (!looksLikeMissingJ2v8) return raw

        val hint =
            "（设备 ABI=$abis）。当前构建只包含 arm64-v8a 的 J2V8 原生库，x86/x86_64 模拟器无法运行插件；" +
                "请使用 arm64-v8a 真机或 arm64 系统镜像的模拟器。"

        return raw + hint
    }

    private fun entryCandidates(manifest: PluginManifest): List<String> {
        val entry = manifest.entry?.trim().orEmpty()
        val out = ArrayList<String>(3)
        if (entry.isNotBlank()) {
            out += entry
            if (!entry.endsWith(".js", ignoreCase = true)) out += "$entry.js"
        }
        out += "main.js"
        return out.distinct()
    }

    private fun buildEditorContext(
        v8: V8,
        manifest: PluginManifest,
        ctx: EditorActionContext,
        cfg: JSONObject?,
    ): V8Object {
        val root = V8Object(v8)

        V8Object(v8).useV8 { note ->
            note.add("docUri", ctx.docUri.toString())
            note.add("title", ctx.title)
            note.add("fileName", ctx.fileName)
            note.add("text", ctx.text)
            root.add("note", note)
        }

        V8Object(v8).useV8 { plugin ->
            plugin.add("id", manifest.id)
            plugin.add("name", manifest.name ?: manifest.id)
            plugin.add("version", manifest.version ?: "")
            root.add("plugin", plugin)
        }

        V8Object(v8).useV8 { app ->
            app.add("platform", "android")
            app.add("versionName", BuildConfig.VERSION_NAME)
            root.add("app", app)
        }

        val configValue = jsonToV8Value(v8, cfg ?: JSONObject())
        addV8Value(root, "config", configValue)

        return root
    }

    private fun parseResult(v: Any?): PluginActionResult {
        return when (v) {
            null -> PluginActionResult(ok = true, message = "OK")
            is String -> PluginActionResult(ok = true, message = v)
            is V8Value ->
                when {
                    v.isUndefined -> PluginActionResult(ok = true, message = "OK")
                    v is V8Object -> parseResultObject(v)
                    else -> PluginActionResult(ok = true, message = v.toString())
                }.also { if (!v.isUndefined) v.close() }
            else -> PluginActionResult(ok = true, message = v.toString())
        }
    }

    private fun parseResultObject(obj: V8Object): PluginActionResult {
        val ok =
            if (!obj.contains("ok")) {
                true
            } else {
                val raw = obj.get("ok")
                try {
                    when (val v = coerceJsValue(raw)) {
                        is Boolean -> v
                        is Number -> v.toInt() != 0
                        is String -> v.equals("true", ignoreCase = true)
                        else -> v != null
                    }
                } finally {
                    releaseIfV8Value(raw)
                }
            }

        val message =
            if (!obj.contains("message")) {
                if (ok) "OK" else "Failed"
            } else {
                val raw = obj.get("message")
                try {
                    coerceJsValue(raw)?.toString()?.takeIf { it.isNotBlank() } ?: if (ok) "OK" else "Failed"
                } finally {
                    releaseIfV8Value(raw)
                }
            }

        val setText =
            if (!obj.contains("setText")) {
                null
            } else {
                val raw = obj.get("setText")
                try {
                    coerceJsValue(raw)?.toString()?.takeIf { it.isNotBlank() }
                } finally {
                    releaseIfV8Value(raw)
                }
            }

        return PluginActionResult(ok = ok, message = message, setText = setText)
    }

    private fun buildActionContext(
        v8: V8,
        manifest: PluginManifest,
        rootUri: Uri,
        input: JSONObject?,
        cfg: JSONObject?,
    ): V8Object {
        val root = V8Object(v8)

        V8Object(v8).useV8 { plugin ->
            plugin.add("id", manifest.id)
            plugin.add("name", manifest.name ?: manifest.id)
            plugin.add("version", manifest.version ?: "")
            root.add("plugin", plugin)
        }

        V8Object(v8).useV8 { app ->
            app.add("platform", "android")
            app.add("versionName", BuildConfig.VERSION_NAME)
            root.add("app", app)
        }

        V8Object(v8).useV8 { vault ->
            vault.add("rootUri", rootUri.toString())
            root.add("vault", vault)
        }

        val configValue = jsonToV8Value(v8, cfg ?: JSONObject())
        addV8Value(root, "config", configValue)

        val inputValue = jsonToV8Value(v8, input ?: JSONObject())
        addV8Value(root, "input", inputValue)

        return root
    }

    private fun parseCallResult(v: Any?): PluginCallResult {
        return when (v) {
            null -> PluginCallResult(ok = true, message = "OK")
            is String -> PluginCallResult(ok = true, message = v)
            is V8Value -> {
                val res =
                    when {
                        v.isUndefined -> PluginCallResult(ok = true, message = "OK")
                        else -> {
                            val any = coerceJsValue(v)
                            when (any) {
                                is Map<*, *> -> {
                                    val okRaw = any["ok"]
                                    val ok =
                                        when (okRaw) {
                                            null -> true
                                            is Boolean -> okRaw
                                            is Number -> okRaw.toInt() != 0
                                            is String -> okRaw.equals("true", ignoreCase = true)
                                            else -> true
                                        }
                                    val msgRaw = any["message"]
                                    val msg =
                                        when {
                                            msgRaw == null -> if (ok) "OK" else "Failed"
                                            else -> msgRaw.toString().takeIf { it.isNotBlank() } ?: if (ok) "OK" else "Failed"
                                        }
                                    PluginCallResult(ok = ok, message = msg, data = any)
                                }
                                else -> PluginCallResult(ok = true, message = any?.toString() ?: "OK", data = any)
                            }
                        }
                    }
                if (!v.isUndefined) v.close()
                res
            }
            else -> PluginCallResult(ok = true, message = v.toString(), data = v)
        }
    }
}

private class PluginApi(
    private val pluginId: String,
    private val http: OkHttpClient,
    private val appLogs: AppLogRepository?,
    private val debugLogging: Boolean,
) {
    fun log(message: Any?) {
        val text = message?.toString() ?: "null"
        Log.d("ZhixuPlugin", "[$pluginId] $text")
        if (debugLogging) {
            appLogs?.appendBlocking(AppLogRepository.Kind.Plugin, "[$pluginId] $text")
        }
    }

    fun http(
        method: String,
        url: String,
        body: String? = null,
        contentType: String? = null,
    ): String {
        val m = method.trim().uppercase().ifBlank { "GET" }
        val u = url.trim()
        require(u.isNotBlank()) { "Missing url" }

        val reqBody =
            if (body == null) {
                null
            } else {
                val mt = contentType?.trim()?.takeIf { it.isNotBlank() }?.toMediaTypeOrNull()
                body.toRequestBody(mt)
            }

        val request =
            Request.Builder()
                .url(u)
                .method(m, reqBody)
                .build()

        http.newCall(request).execute().use { resp ->
            val text = runCatching { resp.body?.string().orEmpty() }.getOrDefault("")
            if (!resp.isSuccessful) {
                throw IllegalStateException("HTTP ${resp.code}")
            }
            return text
        }
    }
}

private class VaultBridge(
    private val appContext: Context,
    private val rootUri: Uri,
) {
    private fun normalizeRelPath(path: String): String {
        val cleaned = path.trim().replace('\\', '/').trimStart('/')
        if (cleaned.isBlank()) return ""
        val parts = cleaned.split('/').filter { it.isNotBlank() }
        require(parts.none { it == "." || it == ".." }) { "Invalid path" }
        return parts.joinToString("/")
    }

    private fun isInternalPath(relativePath: String): Boolean {
        val p = relativePath.trimStart('/').replace('\\', '/')
        if (p.equals(".zhixu", ignoreCase = true)) return true
        if (p.startsWith(".zhixu/", ignoreCase = true)) return true
        return false
    }

    private fun mimeTypeForName(name: String): String {
        val lower = name.trim().lowercase()
        return when {
            lower.endsWith(".json") -> "application/json"
            lower.endsWith(".md") -> "text/markdown"
            lower.endsWith(".txt") -> "text/plain"
            else -> "application/octet-stream"
        }
    }

    private fun isMarkdownFile(name: String?, mimeType: String?): Boolean {
        val n = name?.lowercase().orEmpty()
        val m = mimeType?.lowercase().orEmpty()
        if (n.endsWith(".md")) return true
        if (m == "text/markdown" || m == "text/x-markdown") return true
        return false
    }

    private fun resolveDocFile(relPath: String): DocumentFile? {
        val rel = normalizeRelPath(relPath)
        if (rel.isBlank()) return vaultRootToDocumentFile(appContext, rootUri)

        val root = vaultRootToDocumentFile(appContext, rootUri) ?: return null
        var current: DocumentFile = root
        val parts = rel.split('/').filter { it.isNotBlank() }
        for (i in parts.indices) {
            val name = parts[i]
            val next = current.findFile(name) ?: return null
            if (i < parts.lastIndex && !next.isDirectory) return null
            current = next
        }
        return current
    }

    private fun ensureFile(relPath: String): Uri {
        val rel = normalizeRelPath(relPath)
        require(rel.isNotBlank()) { "Missing path" }
        require(!isInternalPath(rel) || rel.startsWith(".zhixu/", ignoreCase = true) || rel.equals(".zhixu", ignoreCase = true)) {
            "Access denied"
        }

        if (rootUri.scheme.equals("file", ignoreCase = true)) {
            val rootPath = rootUri.path ?: error("Invalid file root uri")
            val file = File(rootPath, rel)
            file.parentFile?.mkdirs()
            if (!file.exists()) file.createNewFile()
            return Uri.fromFile(file)
        }

        val root = vaultRootToDocumentFile(appContext, rootUri) ?: error("Invalid vault root uri")
        val parts = rel.split('/').filter { it.isNotBlank() }
        val fileName = parts.last()
        val dirParts = parts.dropLast(1)

        var current: DocumentFile = root
        for (part in dirParts) {
            val next = current.findFile(part) ?: current.createDirectory(part)
            requireNotNull(next) { "Failed to create directory: $part" }
            require(next.isDirectory) { "Expected directory but got file: $part" }
            current = next
        }
        val existing = current.findFile(fileName)
        if (existing != null) {
            require(existing.isFile) { "Expected file but got directory: $rel" }
            return existing.uri
        }
        val created = current.createFile(mimeTypeForName(fileName), fileName) ?: error("Failed to create file: $rel")
        return created.uri
    }

    private fun resolveFileUri(relPath: String): Uri? {
        val rel = normalizeRelPath(relPath)
        if (rel.isBlank()) return null
        if (rootUri.scheme.equals("file", ignoreCase = true)) {
            val rootPath = rootUri.path ?: return null
            val file = File(rootPath, rel)
            if (!file.exists()) return null
            return Uri.fromFile(file)
        }
        return resolveDocFile(rel)?.uri
    }

    private fun readText(uri: Uri): String {
        if (uri.scheme.equals("file", ignoreCase = true)) {
            val path = uri.path ?: return ""
            return runCatching { File(path).readText(Charsets.UTF_8) }.getOrDefault("")
        }
        val resolver = appContext.contentResolver
        return runCatching {
            resolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) }.orEmpty()
        }.getOrDefault("")
    }

    private fun writeText(uri: Uri, text: String): Boolean {
        if (uri.scheme.equals("file", ignoreCase = true)) {
            val path = uri.path ?: return false
            return runCatching {
                File(path).writeText(text, Charsets.UTF_8)
                true
            }.getOrDefault(false)
        }
        val resolver = appContext.contentResolver
        return runCatching {
            resolver.openOutputStream(uri, "wt")?.use { out ->
                out.write(text.toByteArray(Charsets.UTF_8))
                out.flush()
            } ?: return@runCatching false
            true
        }.getOrDefault(false)
    }

    fun listDir(relPath: String?, includeNonMarkdownFiles: Boolean, includeHidden: Boolean, includeInternal: Boolean): JSONArray {
        val parent = normalizeRelPath(relPath.orEmpty())
        if (!includeInternal && parent.isNotBlank() && isInternalPath(parent)) return JSONArray()

        val dir = resolveDocFile(parent) ?: return JSONArray()
        if (!dir.isDirectory) return JSONArray()

        val children =
            dir.listFiles()
                .asSequence()
                .filter { child ->
                    val name = child.name ?: return@filter false
                    if (!includeHidden && name.startsWith(".")) return@filter false
                    true
                }
                .sortedWith(
                    compareByDescending<DocumentFile> { it.isDirectory }
                        .thenBy { it.name?.lowercase().orEmpty() },
                )
                .toList()

        val out = JSONArray()
        for (child in children) {
            val name = child.name ?: continue
            val isDir = child.isDirectory
            val childRel =
                if (parent.isBlank()) {
                    if (isDir) "$name/" else name
                } else {
                    val base = "$parent/$name"
                    if (isDir) "$base/" else base
                }
            if (!includeInternal && isInternalPath(childRel)) continue
            if (!isDir && !includeNonMarkdownFiles && !isMarkdownFile(name, child.type)) continue
            out.put(
                JSONObject()
                    .put("relativePath", childRel)
                    .put("name", name)
                    .put("uri", child.uri.toString())
                    .put("isDirectory", isDir)
                    .put("lastModified", child.lastModified())
                    .put("size", child.length()),
            )
        }
        return out
    }

    fun walkFiles(options: Map<*, *>?): JSONArray {
        val includeNonMarkdownFiles = (options?.get("includeNonMarkdownFiles") as? Boolean) ?: false
        val includeHidden = (options?.get("includeHidden") as? Boolean) ?: false
        val includeInternal = (options?.get("includeInternal") as? Boolean) ?: false
        val maxEntries = ((options?.get("maxEntries") as? Number)?.toInt() ?: 6000).coerceIn(1, 100_000)
        val startDir = normalizeRelPath(options?.get("startDir")?.toString().orEmpty())

        val root = resolveDocFile(startDir) ?: return JSONArray()
        if (!root.isDirectory) return JSONArray()

        data class StackEntry(val dir: DocumentFile, val prefix: String)
        val stack = ArrayList<StackEntry>()
        stack.add(StackEntry(dir = root, prefix = startDir.takeIf { it.isNotBlank() }?.trimEnd('/')?.plus("/") ?: ""))

        val out = JSONArray()
        while (stack.isNotEmpty() && out.length() < maxEntries) {
            val (dir, prefix) = stack.removeAt(stack.lastIndex)
            val children = dir.listFiles()
            for (child in children) {
                val name = child.name ?: continue
                if (!includeHidden && name.startsWith(".")) continue
                if (child.isDirectory) {
                    val rel = "$prefix$name/"
                    if (!includeInternal && isInternalPath(rel)) continue
                    stack.add(StackEntry(dir = child, prefix = rel))
                } else if (child.isFile) {
                    val rel = "$prefix$name"
                    if (!includeInternal && isInternalPath(rel)) continue
                    if (!includeNonMarkdownFiles && !isMarkdownFile(name, child.type)) continue
                    out.put(
                        JSONObject()
                            .put("relativePath", rel)
                            .put("name", name)
                            .put("uri", child.uri.toString())
                            .put("isDirectory", false)
                            .put("lastModified", child.lastModified())
                            .put("size", child.length()),
                    )
                    if (out.length() >= maxEntries) break
                }
            }
        }
        return out
    }

    fun readTextRelPath(relPath: String): String {
        val uri = resolveFileUri(relPath) ?: throw IllegalStateException("Not found: $relPath")
        return readText(uri)
    }

    fun writeTextRelPath(relPath: String, text: String): Boolean {
        val uri = ensureFile(relPath)
        return writeText(uri, text)
    }

    fun readTextUri(uriString: String): String {
        val uri = Uri.parse(uriString.trim())
        return readText(uri)
    }

    fun writeTextUri(uriString: String, text: String): Boolean {
        val uri = Uri.parse(uriString.trim())
        return writeText(uri, text)
    }
}

private fun registerApi(
    v8: V8,
    api: V8Object,
    appContext: Context,
    rootUri: Uri,
    pluginId: String,
    http: OkHttpClient,
    appLogs: AppLogRepository?,
    debugLogging: Boolean,
) {
    val impl = PluginApi(pluginId = pluginId, http = http, appLogs = appLogs, debugLogging = debugLogging)
    val vaultImpl = VaultBridge(appContext = appContext, rootUri = rootUri)

    api.registerJavaMethod(
        JavaVoidCallback { _, parameters ->
            val msg = readV8Param(parameters, 0)
            impl.log(msg)
        },
        "log",
    )

    api.registerJavaMethod(
        JavaCallback { _, parameters ->
            val method = readV8Param(parameters, 0)?.toString().orEmpty()
            val url = readV8Param(parameters, 1)?.toString().orEmpty()
            val body = readV8Param(parameters, 2)?.toString()
            val contentType = readV8Param(parameters, 3)?.toString()
            try {
                impl.http(method = method, url = url, body = body, contentType = contentType)
            } catch (e: Throwable) {
                val msg = e.message?.takeIf { it.isNotBlank() } ?: e.javaClass.simpleName
                v8.executeVoidScript("throw new Error(${JSONObject.quote(msg)})")
                V8.getUndefined()
            }
        },
        "http",
    )

    V8Object(v8).useV8 { vault ->
        vault.registerJavaMethod(
            JavaCallback { _, parameters ->
                val relPath = readV8Param(parameters, 0)?.toString()
                val includeNonMarkdown = (readV8Param(parameters, 1) as? Boolean) ?: false
                val includeHidden = (readV8Param(parameters, 2) as? Boolean) ?: false
                val includeInternal = (readV8Param(parameters, 3) as? Boolean) ?: false
                try {
                    jsonToV8Value(v8, vaultImpl.listDir(relPath, includeNonMarkdown, includeHidden, includeInternal))
                } catch (e: Throwable) {
                    val msg = e.message?.takeIf { it.isNotBlank() } ?: e.javaClass.simpleName
                    v8.executeVoidScript("throw new Error(${JSONObject.quote(msg)})")
                    V8.getUndefined()
                }
            },
            "listDir",
        )

        vault.registerJavaMethod(
            JavaCallback { _, parameters ->
                val opts = readV8Param(parameters, 0) as? Map<*, *>
                try {
                    jsonToV8Value(v8, vaultImpl.walkFiles(opts))
                } catch (e: Throwable) {
                    val msg = e.message?.takeIf { it.isNotBlank() } ?: e.javaClass.simpleName
                    v8.executeVoidScript("throw new Error(${JSONObject.quote(msg)})")
                    V8.getUndefined()
                }
            },
            "walkFiles",
        )

        vault.registerJavaMethod(
            JavaCallback { _, parameters ->
                val relPath = readV8Param(parameters, 0)?.toString().orEmpty()
                try {
                    vaultImpl.readTextRelPath(relPath)
                } catch (e: Throwable) {
                    val msg = e.message?.takeIf { it.isNotBlank() } ?: e.javaClass.simpleName
                    v8.executeVoidScript("throw new Error(${JSONObject.quote(msg)})")
                    V8.getUndefined()
                }
            },
            "readText",
        )

        vault.registerJavaMethod(
            JavaCallback { _, parameters ->
                val relPath = readV8Param(parameters, 0)?.toString().orEmpty()
                val text = readV8Param(parameters, 1)?.toString().orEmpty()
                try {
                    vaultImpl.writeTextRelPath(relPath, text)
                } catch (e: Throwable) {
                    val msg = e.message?.takeIf { it.isNotBlank() } ?: e.javaClass.simpleName
                    v8.executeVoidScript("throw new Error(${JSONObject.quote(msg)})")
                    V8.getUndefined()
                }
            },
            "writeText",
        )

        vault.registerJavaMethod(
            JavaCallback { _, parameters ->
                val uri = readV8Param(parameters, 0)?.toString().orEmpty()
                try {
                    vaultImpl.readTextUri(uri)
                } catch (e: Throwable) {
                    val msg = e.message?.takeIf { it.isNotBlank() } ?: e.javaClass.simpleName
                    v8.executeVoidScript("throw new Error(${JSONObject.quote(msg)})")
                    V8.getUndefined()
                }
            },
            "readTextUri",
        )

        vault.registerJavaMethod(
            JavaCallback { _, parameters ->
                val uri = readV8Param(parameters, 0)?.toString().orEmpty()
                val text = readV8Param(parameters, 1)?.toString().orEmpty()
                try {
                    vaultImpl.writeTextUri(uri, text)
                } catch (e: Throwable) {
                    val msg = e.message?.takeIf { it.isNotBlank() } ?: e.javaClass.simpleName
                    v8.executeVoidScript("throw new Error(${JSONObject.quote(msg)})")
                    V8.getUndefined()
                }
            },
            "writeTextUri",
        )

        api.add("vault", vault)
    }
}

private fun readV8Param(parameters: V8Array?, index: Int): Any? {
    if (parameters == null || index < 0 || index >= parameters.length()) return null
    return when (parameters.getType(index)) {
        V8Value.UNDEFINED, V8Value.NULL -> null
        else -> {
            val raw = parameters.get(index)
            try {
                coerceJsValue(raw)
            } finally {
                releaseIfV8Value(raw)
            }
        }
    }
}

private fun coerceJsValue(v: Any?): Any? {
    if (v == null) return null
    if (v is V8Value) {
        if (v.isUndefined) return null
        return V8ObjectUtils.getValue(v)
    }
    if (v == JSONObject.NULL) return null
    return v
}

private fun jsonToV8Value(v8: V8, value: Any?): Any? {
    return when (value) {
        null, JSONObject.NULL -> null
        is JSONObject -> {
            val obj = V8Object(v8)
            val it = value.keys()
            while (it.hasNext()) {
                val k = it.next()
                addV8Value(obj, k, jsonToV8Value(v8, value.opt(k)))
            }
            obj
        }
        is JSONArray -> {
            val arr = V8Array(v8)
            for (i in 0 until value.length()) {
                pushV8Value(arr, jsonToV8Value(v8, value.opt(i)))
            }
            arr
        }
        is Boolean, is Int, is Long, is Double, is Float, is String -> value
        else -> value.toString()
    }
}

private fun addV8Value(obj: V8Object, key: String, value: Any?) {
    when (value) {
        null -> obj.addUndefined(key)
        is Int -> obj.add(key, value)
        is Long -> obj.add(key, value.toDouble())
        is Double -> obj.add(key, value)
        is Float -> obj.add(key, value.toDouble())
        is Boolean -> obj.add(key, value)
        is String -> obj.add(key, value)
        is V8Value -> {
            if (value.isUndefined) {
                obj.addUndefined(key)
            } else {
                obj.add(key, value)
            }
            releaseIfV8Value(value)
        }
        else -> obj.add(key, value.toString())
    }
}

private fun pushV8Value(arr: V8Array, value: Any?) {
    when (value) {
        null -> arr.pushUndefined()
        is Int -> arr.push(value)
        is Long -> arr.push(value)
        is Double -> arr.push(value)
        is Float -> arr.push(value)
        is Boolean -> arr.push(value)
        is String -> arr.push(value)
        is V8Value -> {
            if (value.isUndefined) {
                arr.pushUndefined()
            } else {
                arr.push(value)
            }
            releaseIfV8Value(value)
        }
        else -> arr.push(value.toString())
    }
}

private inline fun <T : V8Value, R> T.useV8(block: (T) -> R): R {
    try {
        return block(this)
    } finally {
        if (!isUndefined) {
            close()
        }
    }
}

private fun releaseIfV8Value(v: Any?) {
    if (v !is V8Value) return
    if (v.isUndefined) return
    if (v.isReleased) return
    v.close()
}
