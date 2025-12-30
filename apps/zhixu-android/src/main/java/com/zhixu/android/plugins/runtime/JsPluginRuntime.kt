package com.zhixu.android.plugins.runtime

import android.content.Context
import android.net.Uri
import android.util.Log
import com.zhixu.android.BuildConfig
import com.zhixu.android.plugins.PluginManifest
import com.zhixu.android.plugins.PluginRepository
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import org.mozilla.javascript.ClassShutter
import org.mozilla.javascript.ContextFactory
import org.mozilla.javascript.Function
import org.mozilla.javascript.NativeArray
import org.mozilla.javascript.NativeObject
import org.mozilla.javascript.RhinoException
import org.mozilla.javascript.Scriptable
import org.mozilla.javascript.ScriptableObject

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

        val factory =
            object : ContextFactory() {
                override fun hasFeature(cx: org.mozilla.javascript.Context, featureIndex: Int): Boolean =
                    when (featureIndex) {
                        org.mozilla.javascript.Context.FEATURE_STRICT_VARS -> true
                        org.mozilla.javascript.Context.FEATURE_STRICT_EVAL -> true
                        org.mozilla.javascript.Context.FEATURE_WARNING_AS_ERROR -> false
                        else -> super.hasFeature(cx, featureIndex)
                    }
            }

            val cx = factory.enterContext()
        try {
            cx.optimizationLevel = -1
            cx.languageVersion = org.mozilla.javascript.Context.VERSION_ES6
            cx.setClassShutter(ClassShutter { _: String? -> false })

            val scope = cx.initStandardObjects()
            val module = cx.newObject(scope)
            val exports = cx.newObject(scope)
            ScriptableObject.putProperty(module, "exports", exports)
            ScriptableObject.putProperty(scope, "module", module)
            ScriptableObject.putProperty(scope, "exports", exports)

            val api = PluginApi(pluginId = pluginId, http = http)
            ScriptableObject.putProperty(scope, "api", org.mozilla.javascript.Context.javaToJS(api, scope))

            cx.evaluateString(scope, entryText, entryName, 1, null)

            val outExports = ScriptableObject.getProperty(module, "exports") as? Scriptable
                ?: return PluginActionResult(false, "Invalid module.exports in $entryName")
            val actions = ScriptableObject.getProperty(outExports, "actions") as? Scriptable
                ?: return PluginActionResult(false, "Missing module.exports.actions in $entryName")
            val fn = ScriptableObject.getProperty(actions, actionId) as? Function
                ?: return PluginActionResult(false, "Missing action '$actionId' in $entryName")

            val jsCtx = buildEditorContext(cx, scope, manifest, ctx, cfg)
            val result = fn.call(cx, scope, actions, arrayOf(jsCtx))
            return parseResult(result)
        } catch (e: RhinoException) {
            val where = e.sourceName()?.let { "$it:${e.lineNumber()}" } ?: "script"
            val msg = e.details() ?: e.message ?: e.javaClass.simpleName
            return PluginActionResult(false, "Plugin error ($where): $msg")
        } catch (e: Throwable) {
            return PluginActionResult(false, "Plugin error: ${e.message ?: e.javaClass.simpleName}")
        } finally {
            org.mozilla.javascript.Context.exit()
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
        cx: org.mozilla.javascript.Context,
        scope: Scriptable,
        manifest: PluginManifest,
        ctx: EditorActionContext,
        cfg: JSONObject?,
    ): Scriptable {
        val root = NativeObject()

        val note = NativeObject()
        note.put("docUri", note, ctx.docUri.toString())
        note.put("title", note, ctx.title)
        note.put("fileName", note, ctx.fileName)
        note.put("text", note, ctx.text)
        root.put("note", root, note)

        val plugin = NativeObject()
        plugin.put("id", plugin, manifest.id)
        plugin.put("name", plugin, manifest.name ?: manifest.id)
        plugin.put("version", plugin, manifest.version ?: "")
        root.put("plugin", root, plugin)

        val app = NativeObject()
        app.put("platform", app, "android")
        app.put("versionName", app, BuildConfig.VERSION_NAME)
        root.put("app", root, app)

        root.put("config", root, jsonToJs(cx, scope, cfg ?: JSONObject()))
        return root
    }

    private fun parseResult(v: Any?): PluginActionResult {
        return when (v) {
            null -> PluginActionResult(ok = true, message = "OK")
            is String -> PluginActionResult(ok = true, message = v)
            is Scriptable -> {
                val ok = ScriptableObject.getProperty(v, "ok").let { it == true || it?.toString() == "true" }
                val message = ScriptableObject.getProperty(v, "message")?.toString()?.takeIf { it.isNotBlank() }
                    ?: if (ok) "OK" else "Failed"
                val setText = ScriptableObject.getProperty(v, "setText")?.toString()?.takeIf { it.isNotBlank() }
                PluginActionResult(ok = ok, message = message, setText = setText)
            }
            else -> PluginActionResult(ok = true, message = v.toString())
        }
    }

    private fun jsonToJs(cx: org.mozilla.javascript.Context, scope: Scriptable, value: Any?): Any? {
        return when (value) {
            null -> null
            is JSONObject -> {
                val obj = NativeObject()
                val it = value.keys()
                while (it.hasNext()) {
                    val k = it.next()
                    val v = value.opt(k)
                    obj.put(k, obj, jsonToJs(cx, scope, v))
                }
                obj
            }
            is JSONArray -> {
                val arr = NativeArray(value.length().toLong())
                for (i in 0 until value.length()) {
                    arr.put(i, arr, jsonToJs(cx, scope, value.opt(i)))
                }
                arr
            }
            is Boolean, is Int, is Long, is Double, is Float, is String -> value
            else -> value.toString()
        }
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
