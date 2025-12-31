package com.zhixu.android.plugins.runtime

import android.content.Context
import android.net.Uri
import android.util.Log
import com.zhixu.android.BuildConfig
import com.zhixu.android.plugins.PluginManifest
import com.zhixu.android.plugins.PluginRepository
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

data class PluginActionResult(
    val ok: Boolean,
    val message: String,
    val setText: String? = null,
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
                val msg = e.message?.takeIf { it.isNotBlank() } ?: e.javaClass.simpleName
                return PluginActionResult(false, "Plugin runtime unavailable: $msg")
            }
        try {
            V8Object(v8).useV8 { module ->
                V8Object(v8).useV8 { exports ->
                    module.add("exports", exports)
                    v8.add("module", module)
                    v8.add("exports", exports)

                    V8Object(v8).useV8 { api ->
                        registerApi(api, pluginId = pluginId, http = http)
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
}

private class PluginApi(
    private val pluginId: String,
    private val http: OkHttpClient,
) {
    fun log(message: Any?) {
        Log.d("ZhixuPlugin", "[$pluginId] ${message?.toString() ?: "null"}")
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

private fun registerApi(
    api: V8Object,
    pluginId: String,
    http: OkHttpClient,
) {
    val impl = PluginApi(pluginId = pluginId, http = http)

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
            impl.http(method = method, url = url, body = body, contentType = contentType)
        },
        "http",
    )
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
