package com.zhixu.android.plugins.typecho

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

data class TypechoXmlRpcConfig(
    val endpointUrl: String,
    val username: String,
    val password: String,
    val blogId: String,
    val defaultCategories: List<String> = emptyList(),
    val defaultTags: List<String> = emptyList(),
)

data class TypechoPublishPayload(
    val title: String,
    val markdown: String,
    val categories: List<String> = emptyList(),
    val tags: List<String> = emptyList(),
    val slug: String? = null,
    val publish: Boolean = true,
    val postId: String? = null,
)

data class TypechoPublishResult(
    val postId: String?,
    val ok: Boolean,
    val message: String,
)

class TypechoXmlRpcClient(
    private val http: OkHttpClient = OkHttpClient(),
) {
    fun publish(config: TypechoXmlRpcConfig, payload: TypechoPublishPayload): TypechoPublishResult {
        val endpoint = config.endpointUrl.trim()
        if (endpoint.isBlank()) return TypechoPublishResult(null, false, "Missing endpoint URL")
        if (config.username.isBlank()) return TypechoPublishResult(null, false, "Missing username")
        if (config.password.isBlank()) return TypechoPublishResult(null, false, "Missing password")

        val contentStruct = LinkedHashMap<String, Any?>()
        contentStruct["title"] = payload.title
        contentStruct["description"] = payload.markdown
        val categories = if (payload.categories.isNotEmpty()) payload.categories else config.defaultCategories
        val tags = if (payload.tags.isNotEmpty()) payload.tags else config.defaultTags
        if (categories.isNotEmpty()) contentStruct["categories"] = categories
        if (tags.isNotEmpty()) contentStruct["mt_keywords"] = tags.joinToString(",")
        if (!payload.slug.isNullOrBlank()) contentStruct["wp_slug"] = payload.slug

        val xml =
            if (payload.postId.isNullOrBlank()) {
                XmlRpc.buildCall(
                    method = "metaWeblog.newPost",
                    params = listOf(
                        config.blogId,
                        config.username,
                        config.password,
                        contentStruct,
                        payload.publish,
                    ),
                )
            } else {
                XmlRpc.buildCall(
                    method = "metaWeblog.editPost",
                    params = listOf(
                        payload.postId,
                        config.username,
                        config.password,
                        contentStruct,
                        payload.publish,
                    ),
                )
            }

        val body = xml.toRequestBody("text/xml; charset=utf-8".toMediaType())
        val request = Request.Builder().url(endpoint).post(body).build()
        val response = runCatching { http.newCall(request).execute() }.getOrElse { e ->
            return TypechoPublishResult(null, false, "Request failed: ${e.message ?: e.javaClass.simpleName}")
        }
        response.use { resp ->
            val code = resp.code
            val text = runCatching { resp.body?.string().orEmpty() }.getOrDefault("")
            if (!resp.isSuccessful) return TypechoPublishResult(null, false, "HTTP $code")

            val parsed = XmlRpc.parseResponse(text)
            if (parsed.fault != null) {
                val msg = "Fault ${parsed.fault.code}: ${parsed.fault.message}"
                return TypechoPublishResult(null, false, msg)
            }

            return if (payload.postId.isNullOrBlank()) {
                TypechoPublishResult(parsed.value?.toString(), true, "Published")
            } else {
                TypechoPublishResult(payload.postId, true, "Updated")
            }
        }
    }
}

private object XmlRpc {
    data class Fault(val code: Int?, val message: String?)
    data class Response(val value: Any?, val fault: Fault?)

    fun buildCall(method: String, params: List<Any?>): String {
        val xml =
            buildString {
                append("""<?xml version="1.0"?>""")
                append("<methodCall>")
                append("<methodName>")
                append(escape(method))
                append("</methodName>")
                append("<params>")
                for (p in params) {
                    append("<param><value>")
                    append(encodeValue(p))
                    append("</value></param>")
                }
                append("</params>")
                append("</methodCall>")
            }
        return xml
    }

    fun parseResponse(text: String): Response {
        val faultCode = Regex("""<name>\s*faultCode\s*</name>\s*<value>\s*<(?:int|i4)>(-?\d+)</""")
            .find(text)?.groupValues?.getOrNull(1)?.toIntOrNull()
        val faultString = Regex("""<name>\s*faultString\s*</name>\s*<value>\s*<string>([\s\S]*?)</string>""")
            .find(text)?.groupValues?.getOrNull(1)?.let(::unescape)
        if (text.contains("<fault>", ignoreCase = true)) {
            return Response(value = null, fault = Fault(code = faultCode, message = faultString))
        }

        val stringVal = Regex("""<string>([\s\S]*?)</string>""").find(text)?.groupValues?.getOrNull(1)?.let(::unescape)
        if (stringVal != null) return Response(value = stringVal, fault = null)
        val intVal = Regex("""<(?:int|i4)>(-?\d+)</(?:int|i4)>""").find(text)?.groupValues?.getOrNull(1)?.toLongOrNull()
        if (intVal != null) return Response(value = intVal, fault = null)
        val boolVal = Regex("""<boolean>\s*([01])\s*</boolean>""").find(text)?.groupValues?.getOrNull(1)?.trim()
        if (boolVal != null) return Response(value = (boolVal == "1"), fault = null)
        return Response(value = null, fault = null)
    }

    private fun encodeValue(v: Any?): String =
        when (v) {
            null -> "<nil/>"
            is String -> "<string>${escape(v)}</string>"
            is Boolean -> "<boolean>${if (v) 1 else 0}</boolean>"
            is Int -> "<int>$v</int>"
            is Long -> "<int>$v</int>"
            is Double -> "<double>$v</double>"
            is Float -> "<double>$v</double>"
            is List<*> -> "<array><data>${v.joinToString(separator = "") { "<value>${encodeValue(it)}</value>" }}</data></array>"
            is Map<*, *> -> {
                val members =
                    v.entries.joinToString(separator = "") { (k, vv) ->
                        val key = k?.toString().orEmpty()
                        "<member><name>${escape(key)}</name><value>${encodeValue(vv)}</value></member>"
                    }
                "<struct>$members</struct>"
            }
            else -> "<string>${escape(v.toString())}</string>"
        }

    private fun escape(s: String): String =
        s.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&#39;")

    private fun unescape(s: String): String =
        s.replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .replace("&amp;", "&")
}
