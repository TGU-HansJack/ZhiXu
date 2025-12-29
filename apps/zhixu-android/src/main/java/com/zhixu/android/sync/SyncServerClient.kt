package com.zhixu.android.sync

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

data class SyncServerMe(
    val userId: Long,
    val username: String,
)

data class VaultManifestEntry(
    val path: String,
    val updatedAt: Long,
    val mtimeMs: Long,
    val size: Long,
    val sha256: String,
    val deleted: Boolean,
)

data class VaultManifest(
    val serverTime: Long,
    val files: List<VaultManifestEntry>,
)

data class VaultFileDownload(
    val bytes: ByteArray,
    val mtimeMs: Long,
    val size: Long,
    val sha256: String,
)

data class SyncServerResult<T>(
    val ok: Boolean,
    val value: T? = null,
    val errorMessage: String? = null,
    val statusCode: Int = 0,
)

object SyncServerClient {
    private val client: OkHttpClient =
        OkHttpClient.Builder()
            .followRedirects(true)
            .followSslRedirects(true)
            .build()

    suspend fun health(baseUrl: String): SyncServerResult<Unit> = withContext(Dispatchers.IO) {
        val req = Request.Builder().url(normalizeJoin(baseUrl, "/health")).get().build()
        client.newCall(req).execute().use { resp ->
            if (resp.isSuccessful) SyncServerResult(ok = true, value = Unit, statusCode = resp.code)
            else SyncServerResult(ok = false, errorMessage = "HTTP ${resp.code}", statusCode = resp.code)
        }
    }

    suspend fun register(
        baseUrl: String,
        username: String,
        password: String,
    ): SyncServerResult<Long> = withContext(Dispatchers.IO) {
        val url = normalizeJoin(baseUrl, "/api/auth/register")
        val body =
            JSONObject()
                .put("username", username)
                .put("password", password)
                .toString()
                .toRequestBody("application/json; charset=utf-8".toMediaType())
        val req = Request.Builder().url(url).post(body).header("User-Agent", "Zhixu-Android").build()
        client.newCall(req).execute().use { resp ->
            val text = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) return@withContext SyncServerResult(false, errorMessage = text.ifBlank { "HTTP ${resp.code}" }, statusCode = resp.code)
            val userId = runCatching { JSONObject(text).optLong("userId", 0L) }.getOrDefault(0L)
            SyncServerResult(ok = true, value = userId, statusCode = resp.code)
        }
    }

    suspend fun login(
        baseUrl: String,
        username: String,
        password: String,
    ): SyncServerResult<String> = withContext(Dispatchers.IO) {
        val url = normalizeJoin(baseUrl, "/api/auth/login")
        val body =
            JSONObject()
                .put("username", username)
                .put("password", password)
                .toString()
                .toRequestBody("application/json; charset=utf-8".toMediaType())
        val req = Request.Builder().url(url).post(body).header("User-Agent", "Zhixu-Android").build()
        client.newCall(req).execute().use { resp ->
            val text = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) return@withContext SyncServerResult(false, errorMessage = text.ifBlank { "HTTP ${resp.code}" }, statusCode = resp.code)
            val token = runCatching { JSONObject(text).optString("token").orEmpty() }.getOrDefault("")
            if (token.isBlank()) return@withContext SyncServerResult(false, errorMessage = "Missing token", statusCode = resp.code)
            SyncServerResult(ok = true, value = token, statusCode = resp.code)
        }
    }

    suspend fun me(
        baseUrl: String,
        token: String,
    ): SyncServerResult<SyncServerMe> = withContext(Dispatchers.IO) {
        val url = normalizeJoin(baseUrl, "/api/account/me")
        val req =
            Request.Builder()
                .url(url)
                .get()
                .header("Authorization", "Bearer $token")
                .header("User-Agent", "Zhixu-Android")
                .build()
        client.newCall(req).execute().use { resp ->
            val text = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) return@withContext SyncServerResult(false, errorMessage = text.ifBlank { "HTTP ${resp.code}" }, statusCode = resp.code)
            val obj = runCatching { JSONObject(text) }.getOrNull() ?: return@withContext SyncServerResult(false, errorMessage = "Invalid response", statusCode = resp.code)
            val userId = obj.optLong("userId", 0L)
            val username = obj.optString("username").orEmpty()
            SyncServerResult(ok = true, value = SyncServerMe(userId = userId, username = username), statusCode = resp.code)
        }
    }

    suspend fun listDevices(
        baseUrl: String,
        token: String,
    ): SyncServerResult<List<String>> = withContext(Dispatchers.IO) {
        val url = normalizeJoin(baseUrl, "/api/account/devices")
        val req =
            Request.Builder()
                .url(url)
                .get()
                .header("Authorization", "Bearer $token")
                .header("User-Agent", "Zhixu-Android")
                .build()
        client.newCall(req).execute().use { resp ->
            val text = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) return@withContext SyncServerResult(false, errorMessage = text.ifBlank { "HTTP ${resp.code}" }, statusCode = resp.code)
            val arr = runCatching { JSONObject(text).optJSONArray("devices") ?: JSONArray() }.getOrNull() ?: JSONArray()
            val out = ArrayList<String>(arr.length())
            for (i in 0 until arr.length()) {
                val s = arr.optString(i).orEmpty()
                if (s.isNotBlank()) out += s
            }
            SyncServerResult(ok = true, value = out, statusCode = resp.code)
        }
    }

    suspend fun bindDevice(
        baseUrl: String,
        token: String,
        deviceId: String,
    ): SyncServerResult<Unit> = withContext(Dispatchers.IO) {
        val url = normalizeJoin(baseUrl, "/api/account/devices/bind")
        val body =
            JSONObject()
                .put("deviceId", deviceId)
                .toString()
                .toRequestBody("application/json; charset=utf-8".toMediaType())
        val req =
            Request.Builder()
                .url(url)
                .post(body)
                .header("Authorization", "Bearer $token")
                .header("User-Agent", "Zhixu-Android")
                .build()
        client.newCall(req).execute().use { resp ->
            val text = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) SyncServerResult(false, errorMessage = text.ifBlank { "HTTP ${resp.code}" }, statusCode = resp.code)
            else SyncServerResult(true, value = Unit, statusCode = resp.code)
        }
    }

    suspend fun unbindDevice(
        baseUrl: String,
        token: String,
        deviceId: String,
    ): SyncServerResult<Unit> = withContext(Dispatchers.IO) {
        val url = normalizeJoin(baseUrl, "/api/account/devices/unbind")
        val body =
            JSONObject()
                .put("deviceId", deviceId)
                .toString()
                .toRequestBody("application/json; charset=utf-8".toMediaType())
        val req =
            Request.Builder()
                .url(url)
                .post(body)
                .header("Authorization", "Bearer $token")
                .header("User-Agent", "Zhixu-Android")
                .build()
        client.newCall(req).execute().use { resp ->
            val text = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) SyncServerResult(false, errorMessage = text.ifBlank { "HTTP ${resp.code}" }, statusCode = resp.code)
            else SyncServerResult(true, value = Unit, statusCode = resp.code)
        }
    }

    suspend fun vaultManifest(
        baseUrl: String,
        token: String,
    ): SyncServerResult<VaultManifest> = withContext(Dispatchers.IO) {
        val url = normalizeJoin(baseUrl, "/api/vault/manifest")
        val req =
            Request.Builder()
                .url(url)
                .get()
                .header("Authorization", "Bearer $token")
                .header("User-Agent", "Zhixu-Android")
                .build()
        client.newCall(req).execute().use { resp ->
            val text = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) return@withContext SyncServerResult(false, errorMessage = text.ifBlank { "HTTP ${resp.code}" }, statusCode = resp.code)
            val obj = runCatching { JSONObject(text) }.getOrNull() ?: return@withContext SyncServerResult(false, errorMessage = "Invalid response", statusCode = resp.code)
            val serverTime = obj.optLong("serverTime", 0L)
            val arr = obj.optJSONArray("files") ?: JSONArray()
            val out = ArrayList<VaultManifestEntry>(arr.length())
            for (i in 0 until arr.length()) {
                val item = arr.optJSONObject(i) ?: continue
                val path = item.optString("path").orEmpty()
                if (path.isBlank()) continue
                out +=
                    VaultManifestEntry(
                        path = path,
                        updatedAt = item.optLong("updatedAt", 0L),
                        mtimeMs = item.optLong("mtimeMs", 0L),
                        size = item.optLong("size", -1L),
                        sha256 = item.optString("sha256").orEmpty(),
                        deleted = item.optBoolean("deleted", false),
                    )
            }
            SyncServerResult(ok = true, value = VaultManifest(serverTime = serverTime, files = out), statusCode = resp.code)
        }
    }

    suspend fun downloadVaultFile(
        baseUrl: String,
        token: String,
        path: String,
    ): SyncServerResult<VaultFileDownload> = withContext(Dispatchers.IO) {
        val url = normalizeJoin(baseUrl, "/api/vault/file?path=${encodeQuery(path)}")
        val req =
            Request.Builder()
                .url(url)
                .get()
                .header("Authorization", "Bearer $token")
                .header("User-Agent", "Zhixu-Android")
                .build()
        client.newCall(req).execute().use { resp ->
            val bytes = resp.body?.bytes() ?: ByteArray(0)
            if (!resp.isSuccessful) {
                val msg = bytes.decodeToString().ifBlank { "HTTP ${resp.code}" }
                return@withContext SyncServerResult(false, errorMessage = msg, statusCode = resp.code)
            }
            val mtimeMs = resp.header("X-Zhixu-Mtime-Ms")?.toLongOrNull() ?: 0L
            val size = resp.header("X-Zhixu-Size")?.toLongOrNull() ?: bytes.size.toLong()
            val sha = resp.header("X-Zhixu-Sha256").orEmpty()
            SyncServerResult(ok = true, value = VaultFileDownload(bytes = bytes, mtimeMs = mtimeMs, size = size, sha256 = sha), statusCode = resp.code)
        }
    }

    suspend fun uploadVaultFile(
        baseUrl: String,
        token: String,
        path: String,
        mtimeMs: Long,
        bytes: ByteArray,
    ): SyncServerResult<Unit> = withContext(Dispatchers.IO) {
        val url =
            normalizeJoin(
                baseUrl,
                "/api/vault/file?path=${encodeQuery(path)}&mtimeMs=${encodeQuery(mtimeMs.coerceAtLeast(0L).toString())}",
            )
        val body = bytes.toRequestBody("application/octet-stream".toMediaType())
        val req =
            Request.Builder()
                .url(url)
                .put(body)
                .header("Authorization", "Bearer $token")
                .header("User-Agent", "Zhixu-Android")
                .build()
        client.newCall(req).execute().use { resp ->
            val text = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) SyncServerResult(false, errorMessage = text.ifBlank { "HTTP ${resp.code}" }, statusCode = resp.code)
            else SyncServerResult(true, value = Unit, statusCode = resp.code)
        }
    }

    suspend fun deleteVaultFile(
        baseUrl: String,
        token: String,
        path: String,
    ): SyncServerResult<Unit> = withContext(Dispatchers.IO) {
        val url = normalizeJoin(baseUrl, "/api/vault/file?path=${encodeQuery(path)}")
        val req =
            Request.Builder()
                .url(url)
                .delete()
                .header("Authorization", "Bearer $token")
                .header("User-Agent", "Zhixu-Android")
                .build()
        client.newCall(req).execute().use { resp ->
            val text = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) SyncServerResult(false, errorMessage = text.ifBlank { "HTTP ${resp.code}" }, statusCode = resp.code)
            else SyncServerResult(true, value = Unit, statusCode = resp.code)
        }
    }

    private fun normalizeJoin(baseUrl: String, path: String): String {
        val left = baseUrl.trimEnd('/')
        val right = path.trim()
        return if (right.startsWith("/")) "$left$right" else "$left/$right"
    }

    private fun encodeQuery(value: String): String =
        URLEncoder.encode(value, StandardCharsets.UTF_8.name())
}
