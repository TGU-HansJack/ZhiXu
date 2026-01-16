package app.zhixu.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okio.BufferedSink
import okio.source
import java.io.IOException
import java.io.InputStream
import java.util.Base64

data class WebDavTestResult(
    val success: Boolean,
    val statusCode: Int,
    val message: String,
)

object WebDavClient {
    private val client: OkHttpClient =
        OkHttpClient.Builder()
            .followRedirects(true)
            .followSslRedirects(true)
            .build()

    private class StreamRequestBody(
        private val mediaType: okhttp3.MediaType?,
        private val contentLength: Long?,
        private val openStream: () -> InputStream?,
    ) : okhttp3.RequestBody() {
        override fun contentType(): okhttp3.MediaType? = mediaType

        override fun contentLength(): Long {
            val len = contentLength ?: -1L
            return if (len >= 0L) len else -1L
        }

        override fun writeTo(sink: BufferedSink) {
            val input = openStream() ?: throw IOException("Failed to open upload stream")
            input.use { sink.writeAll(it.source()) }
        }
    }

    suspend fun testConnection(config: WebDavConfig): WebDavTestResult = withContext(Dispatchers.IO) {
        val baseUrl = config.baseUrl.trim()
        if (baseUrl.isBlank()) return@withContext WebDavTestResult(false, 0, "Base URL is empty")
        if (!baseUrl.startsWith("http://") && !baseUrl.startsWith("https://")) {
            return@withContext WebDavTestResult(false, 0, "Base URL must start with http(s)://")
        }

        val target = normalizeJoin(baseUrl, config.remoteRoot.trim().ifBlank { "/" })

        // Nutstore + some servers accept OPTIONS on collection; PROPFIND depth 0 is the actual WebDAV probe.
        val options = runCatching { requestMessage(config, "OPTIONS", target, null, headers = emptyMap()) }.getOrNull()
        if (options != null && options.first in 200..299) {
            return@withContext WebDavTestResult(true, options.first, options.second)
        }

        val (code, xml) =
            runCatching { propfind(config, target, depth = "0") }.getOrElse { e ->
                0 to (e.message ?: e.javaClass.simpleName)
            }
        val ok = code in 200..299 || code == 207
        WebDavTestResult(ok, code, xml.take(200).ifBlank { "HTTP $code" })
    }

    suspend fun propfind(config: WebDavConfig, targetUrl: String, depth: String): Pair<Int, String> =
        withContext(Dispatchers.IO) {
            val body =
                """
                <?xml version="1.0" encoding="utf-8" ?>
                <D:propfind xmlns:D="DAV:">
                  <D:prop>
                    <D:resourcetype/>
                    <D:getcontentlength/>
                    <D:getlastmodified/>
                    <D:getetag/>
                  </D:prop>
                </D:propfind>
                """.trimIndent()
            requestBody(
                config = config,
                method = "PROPFIND",
                url = targetUrl,
                body = body.toRequestBody("text/xml; charset=utf-8".toMediaType()),
                headers = mapOf("Depth" to depth),
            )
        }

    suspend fun mkcol(config: WebDavConfig, targetUrl: String): Int = withContext(Dispatchers.IO) {
        requestMessage(config, "MKCOL", targetUrl, body = null, headers = emptyMap()).first
    }

    suspend fun put(
        config: WebDavConfig,
        targetUrl: String,
        contentType: String?,
        content: ByteArray,
        ifMatch: String? = null,
        ifNoneMatchStar: Boolean = false,
    ): Int =
        withContext(Dispatchers.IO) {
            val mediaType = (contentType ?: "application/octet-stream").toMediaType()
            val headers =
                buildMap<String, String> {
                    val m = ifMatch?.trim().orEmpty()
                    if (m.isNotBlank()) put("If-Match", m)
                    if (ifNoneMatchStar) put("If-None-Match", "*")
                }
            requestMessage(
                config = config,
                method = "PUT",
                url = targetUrl,
                body = content.toRequestBody(mediaType),
                headers = headers,
            ).first
        }

    suspend fun putStream(
        config: WebDavConfig,
        targetUrl: String,
        contentType: String?,
        contentLength: Long?,
        openStream: () -> InputStream?,
        ifMatch: String? = null,
        ifNoneMatchStar: Boolean = false,
    ): Int =
        withContext(Dispatchers.IO) {
            val mediaType = (contentType ?: "application/octet-stream").toMediaType()
            val headers =
                buildMap<String, String> {
                    val m = ifMatch?.trim().orEmpty()
                    if (m.isNotBlank()) put("If-Match", m)
                    if (ifNoneMatchStar) put("If-None-Match", "*")
                }
            val body = StreamRequestBody(mediaType = mediaType, contentLength = contentLength, openStream = openStream)
            requestMessage(
                config = config,
                method = "PUT",
                url = targetUrl,
                body = body,
                headers = headers,
            ).first
        }

    suspend fun get(config: WebDavConfig, targetUrl: String): Pair<Int, ByteArray> = withContext(Dispatchers.IO) {
        val request = buildRequest(config, "GET", targetUrl, body = null, headers = emptyMap())
        client.newCall(request).execute().use { resp ->
            val bytes = resp.body?.bytes() ?: ByteArray(0)
            resp.code to bytes
        }
    }

    suspend fun getToStream(
        config: WebDavConfig,
        targetUrl: String,
        sink: (InputStream) -> Unit,
    ): Int =
        withContext(Dispatchers.IO) {
            val request = buildRequest(config, "GET", targetUrl, body = null, headers = emptyMap())
            client.newCall(request).execute().use { resp ->
                val body = resp.body
                if (resp.code !in 200..299 || body == null) {
                    body?.close()
                    return@withContext resp.code
                }
                body.byteStream().use { input -> sink(input) }
                resp.code
            }
        }

    suspend fun delete(config: WebDavConfig, targetUrl: String, ifMatch: String? = null): Int = withContext(Dispatchers.IO) {
        val headers =
            buildMap<String, String> {
                val m = ifMatch?.trim().orEmpty()
                if (m.isNotBlank()) put("If-Match", m)
            }
        requestMessage(config, "DELETE", targetUrl, body = null, headers = headers).first
    }

    fun normalizeJoin(baseUrl: String, remoteRoot: String): String {
        val left = baseUrl.trimEnd('/')
        val right = remoteRoot.trim()
        return if (right.startsWith("/")) "$left$right" else "$left/$right"
    }

    private fun buildRequest(
        config: WebDavConfig,
        method: String,
        url: String,
        body: okhttp3.RequestBody?,
        headers: Map<String, String>,
    ): Request {
        val builder =
            Request.Builder()
                .url(url)
                .method(method, body)
                .header("User-Agent", "Zhixu-Android")
                .header("Accept", "*/*")

        for ((k, v) in headers) builder.header(k, v)

        if (config.username.isNotBlank() || config.password.isNotBlank()) {
            val token = Base64.getEncoder().encodeToString("${config.username}:${config.password}".toByteArray(Charsets.UTF_8))
            builder.header("Authorization", "Basic $token")
        }

        return builder.build()
    }

    private fun requestMessage(
        config: WebDavConfig,
        method: String,
        url: String,
        body: okhttp3.RequestBody?,
        headers: Map<String, String>,
    ): Pair<Int, String> {
        val request = buildRequest(config, method, url, body, headers)
        client.newCall(request).execute().use { resp ->
            resp.body?.close()
            return resp.code to resp.message
        }
    }

    private fun requestBody(
        config: WebDavConfig,
        method: String,
        url: String,
        body: okhttp3.RequestBody?,
        headers: Map<String, String>,
    ): Pair<Int, String> {
        val request = buildRequest(config, method, url, body, headers)
        client.newCall(request).execute().use { resp ->
            val text = resp.body?.string().orEmpty()
            return resp.code to text
        }
    }
}
