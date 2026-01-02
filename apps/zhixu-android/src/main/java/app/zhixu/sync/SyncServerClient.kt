package app.zhixu.sync

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.io.InterruptedIOException
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

data class SyncServerMe(
    val userId: Long,
    val username: String,
    val plan: SyncServerPlan? = null,
)

data class SyncServerPlan(
    val code: String,
    val name: String,
    val storageBytes: Long,
    val priceCnyYear: Int,
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

data class VaultChangeEntry(
    val changeId: Long,
    val path: String,
    val rev: Long,
    val updatedAt: Long,
    val mtimeMs: Long,
    val size: Long,
    val sha256: String,
    val deleted: Boolean,
)

data class VaultChangesV2(
    val serverTime: Long,
    val cursor: Long,
    val snapshot: Boolean,
    val changes: List<VaultChangeEntry>,
    val nextSince: Long,
    val hasMore: Boolean,
)

data class VaultFileDownloadV2(
    val bytes: ByteArray,
    val rev: Long,
    val mtimeMs: Long,
    val size: Long,
    val sha256: String,
)

data class VaultPutResultV2(
    val path: String,
    val rev: Long,
    val changeId: Long,
    val sha256: String,
    val size: Long,
    val mtimeMs: Long,
)

data class VaultDeleteResultV2(
    val path: String,
    val rev: Long,
    val changeId: Long,
    val deleted: Boolean,
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
        safeResult {
            val req = Request.Builder().url(normalizeJoin(baseUrl, "/health")).get().build()
            client.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) SyncServerResult(ok = true, value = Unit, statusCode = resp.code)
                else SyncServerResult(ok = false, errorMessage = "HTTP ${resp.code}", statusCode = resp.code)
            }
        }
    }

    suspend fun register(
        baseUrl: String,
        username: String,
        password: String,
    ): SyncServerResult<Long> = withContext(Dispatchers.IO) {
        safeResult {
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
                if (!resp.isSuccessful) return@use SyncServerResult(false, errorMessage = text.ifBlank { "HTTP ${resp.code}" }, statusCode = resp.code)
                val userId = runCatching { JSONObject(text).optLong("userId", 0L) }.getOrDefault(0L)
                SyncServerResult(ok = true, value = userId, statusCode = resp.code)
            }
        }
    }

    suspend fun login(
        baseUrl: String,
        username: String,
        password: String,
    ): SyncServerResult<String> = withContext(Dispatchers.IO) {
        safeResult {
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
                if (!resp.isSuccessful) return@use SyncServerResult(false, errorMessage = text.ifBlank { "HTTP ${resp.code}" }, statusCode = resp.code)
                val token = runCatching { JSONObject(text).optString("token").orEmpty() }.getOrDefault("")
                if (token.isBlank()) return@use SyncServerResult(false, errorMessage = "Missing token", statusCode = resp.code)
                SyncServerResult(ok = true, value = token, statusCode = resp.code)
            }
        }
    }

    suspend fun me(
        baseUrl: String,
        token: String,
    ): SyncServerResult<SyncServerMe> = withContext(Dispatchers.IO) {
        safeResult {
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
                if (!resp.isSuccessful) return@use SyncServerResult(false, errorMessage = text.ifBlank { "HTTP ${resp.code}" }, statusCode = resp.code)
                val obj = runCatching { JSONObject(text) }.getOrNull() ?: return@use SyncServerResult(false, errorMessage = "Invalid response", statusCode = resp.code)
                val userId = obj.optLong("userId", 0L)
                val username = obj.optString("username").orEmpty()
                val planObj = obj.optJSONObject("plan")
                val plan =
                    planObj?.let {
                        SyncServerPlan(
                            code = it.optString("code").orEmpty(),
                            name = it.optString("name").orEmpty(),
                            storageBytes = it.optLong("storageBytes", 0L).coerceAtLeast(0L),
                            priceCnyYear = it.optInt("priceCnyYear", 0).coerceAtLeast(0),
                        )
                    }
                SyncServerResult(ok = true, value = SyncServerMe(userId = userId, username = username, plan = plan), statusCode = resp.code)
            }
        }
    }

    suspend fun listPlans(
        baseUrl: String,
    ): SyncServerResult<List<SyncServerPlan>> = withContext(Dispatchers.IO) {
        safeResult {
            val url = normalizeJoin(baseUrl, "/api/plans")
            val req =
                Request.Builder()
                    .url(url)
                    .get()
                    .header("User-Agent", "Zhixu-Android")
                    .build()
            client.newCall(req).execute().use { resp ->
                val text = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) return@use SyncServerResult(false, errorMessage = text.ifBlank { "HTTP ${resp.code}" }, statusCode = resp.code)
                val arr = runCatching { JSONObject(text).optJSONArray("plans") ?: JSONArray() }.getOrNull() ?: JSONArray()
                val out = ArrayList<SyncServerPlan>(arr.length())
                for (i in 0 until arr.length()) {
                    val item = arr.optJSONObject(i) ?: continue
                    val code = item.optString("code").orEmpty()
                    if (code.isBlank()) continue
                    out +=
                        SyncServerPlan(
                            code = code,
                            name = item.optString("name").orEmpty(),
                            storageBytes = item.optLong("storageBytes", 0L).coerceAtLeast(0L),
                            priceCnyYear = item.optInt("priceCnyYear", 0).coerceAtLeast(0),
                        )
                }
                SyncServerResult(ok = true, value = out, statusCode = resp.code)
            }
        }
    }

    suspend fun setSubscriptionPlan(
        baseUrl: String,
        token: String,
        planCode: String,
    ): SyncServerResult<SyncServerPlan> = withContext(Dispatchers.IO) {
        safeResult {
            val url = normalizeJoin(baseUrl, "/api/account/subscription")
            val body =
                JSONObject()
                    .put("planCode", planCode)
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
                if (!resp.isSuccessful) return@use SyncServerResult(false, errorMessage = text.ifBlank { "HTTP ${resp.code}" }, statusCode = resp.code)
                val obj = runCatching { JSONObject(text) }.getOrNull() ?: return@use SyncServerResult(false, errorMessage = "Invalid response", statusCode = resp.code)
                val planObj = obj.optJSONObject("plan") ?: JSONObject()
                val code = planObj.optString("code").orEmpty().ifBlank { planCode }
                val plan =
                    SyncServerPlan(
                        code = code,
                        name = planObj.optString("name").orEmpty(),
                        storageBytes = planObj.optLong("storageBytes", 0L).coerceAtLeast(0L),
                        priceCnyYear = planObj.optInt("priceCnyYear", 0).coerceAtLeast(0),
                    )
                SyncServerResult(true, value = plan, statusCode = resp.code)
            }
        }
    }

    suspend fun vaultManifest(
        baseUrl: String,
        token: String,
    ): SyncServerResult<VaultManifest> = withContext(Dispatchers.IO) {
        safeResult {
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
                if (!resp.isSuccessful) return@use SyncServerResult(false, errorMessage = text.ifBlank { "HTTP ${resp.code}" }, statusCode = resp.code)
                val obj = runCatching { JSONObject(text) }.getOrNull() ?: return@use SyncServerResult(false, errorMessage = "Invalid response", statusCode = resp.code)
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
    }

    suspend fun downloadVaultFile(
        baseUrl: String,
        token: String,
        path: String,
    ): SyncServerResult<VaultFileDownload> = withContext(Dispatchers.IO) {
        safeResult {
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
                    return@use SyncServerResult(false, errorMessage = msg, statusCode = resp.code)
                }
                val mtimeMs = resp.header("X-Zhixu-Mtime-Ms")?.toLongOrNull() ?: 0L
                val size = resp.header("X-Zhixu-Size")?.toLongOrNull() ?: bytes.size.toLong()
                val sha = resp.header("X-Zhixu-Sha256").orEmpty()
                SyncServerResult(ok = true, value = VaultFileDownload(bytes = bytes, mtimeMs = mtimeMs, size = size, sha256 = sha), statusCode = resp.code)
            }
        }
    }

    suspend fun uploadVaultFile(
        baseUrl: String,
        token: String,
        path: String,
        mtimeMs: Long,
        bytes: ByteArray,
    ): SyncServerResult<Unit> = withContext(Dispatchers.IO) {
        safeResult {
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
    }

    suspend fun deleteVaultFile(
        baseUrl: String,
        token: String,
        path: String,
    ): SyncServerResult<Unit> = withContext(Dispatchers.IO) {
        safeResult {
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
    }

    suspend fun vaultChangesV2(
        baseUrl: String,
        token: String,
        since: Long,
        limit: Int = 2000,
    ): SyncServerResult<VaultChangesV2> = withContext(Dispatchers.IO) {
        safeResult {
            val safeSince = since.coerceAtLeast(0L)
            val safeLimit = limit.coerceIn(1, 5000)
            val url = normalizeJoin(baseUrl, "/api/v2/vault/changes?since=${encodeQuery(safeSince.toString())}&limit=${encodeQuery(safeLimit.toString())}")
            val req =
                Request.Builder()
                    .url(url)
                    .get()
                    .header("Authorization", "Bearer $token")
                    .header("User-Agent", "Zhixu-Android")
                    .build()
            client.newCall(req).execute().use { resp ->
                val text = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) return@use SyncServerResult(false, errorMessage = text.ifBlank { "HTTP ${resp.code}" }, statusCode = resp.code)
                val obj = runCatching { JSONObject(text) }.getOrNull() ?: return@use SyncServerResult(false, errorMessage = "Invalid response", statusCode = resp.code)
                val serverTime = obj.optLong("serverTime", 0L)
                val cursor = obj.optLong("cursor", 0L)
                val snapshot = obj.optBoolean("snapshot", false)
                val hasMore = obj.optBoolean("hasMore", false)
                val nextSince = obj.optLong("nextSince", safeSince)
                val arr = obj.optJSONArray("changes") ?: JSONArray()
                val changes = ArrayList<VaultChangeEntry>(arr.length())
                for (i in 0 until arr.length()) {
                    val item = arr.optJSONObject(i) ?: continue
                    val path = item.optString("path").orEmpty()
                    if (path.isBlank()) continue
                    changes +=
                        VaultChangeEntry(
                            changeId = item.optLong("changeId", 0L),
                            path = path,
                            rev = item.optLong("rev", 0L),
                            updatedAt = item.optLong("updatedAt", 0L),
                            mtimeMs = item.optLong("mtimeMs", 0L),
                            size = item.optLong("size", 0L),
                            sha256 = item.optString("sha256").orEmpty(),
                            deleted = item.optBoolean("deleted", false),
                        )
                }
                val effectiveNextSince = if (snapshot) safeSince else nextSince
                SyncServerResult(
                    ok = true,
                    value = VaultChangesV2(serverTime = serverTime, cursor = cursor, snapshot = snapshot, changes = changes, nextSince = effectiveNextSince, hasMore = hasMore),
                    statusCode = resp.code,
                )
            }
        }
    }

    suspend fun downloadVaultFileV2(
        baseUrl: String,
        token: String,
        path: String,
    ): SyncServerResult<VaultFileDownloadV2> = withContext(Dispatchers.IO) {
        safeResult {
            val url = normalizeJoin(baseUrl, "/api/v2/vault/file?path=${encodeQuery(path)}")
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
                    return@use SyncServerResult(false, errorMessage = msg, statusCode = resp.code)
                }
                val rev = resp.header("X-Zhixu-Rev")?.toLongOrNull() ?: 0L
                val mtimeMs = resp.header("X-Zhixu-Mtime-Ms")?.toLongOrNull() ?: 0L
                val size = resp.header("X-Zhixu-Size")?.toLongOrNull() ?: bytes.size.toLong()
                val sha = resp.header("X-Zhixu-Sha256").orEmpty()
                SyncServerResult(ok = true, value = VaultFileDownloadV2(bytes = bytes, rev = rev, mtimeMs = mtimeMs, size = size, sha256 = sha), statusCode = resp.code)
            }
        }
    }

    suspend fun uploadVaultFileV2(
        baseUrl: String,
        token: String,
        path: String,
        mtimeMs: Long,
        bytes: ByteArray,
        baseRev: Long,
    ): SyncServerResult<VaultPutResultV2> = withContext(Dispatchers.IO) {
        safeResult {
            val url =
                normalizeJoin(
                    baseUrl,
                    "/api/v2/vault/file?path=${encodeQuery(path)}&mtimeMs=${encodeQuery(mtimeMs.coerceAtLeast(0L).toString())}&baseRev=${encodeQuery(baseRev.coerceAtLeast(0L).toString())}",
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
                if (!resp.isSuccessful) return@use SyncServerResult(false, errorMessage = text.ifBlank { "HTTP ${resp.code}" }, statusCode = resp.code)
                val obj = runCatching { JSONObject(text) }.getOrNull() ?: return@use SyncServerResult(false, errorMessage = "Invalid response", statusCode = resp.code)
                SyncServerResult(
                    ok = true,
                    value =
                        VaultPutResultV2(
                            path = obj.optString("path").orEmpty().ifBlank { path },
                            rev = obj.optLong("rev", 0L),
                            changeId = obj.optLong("changeId", 0L),
                            sha256 = obj.optString("sha256").orEmpty(),
                            size = obj.optLong("size", bytes.size.toLong()),
                            mtimeMs = obj.optLong("mtimeMs", mtimeMs),
                        ),
                    statusCode = resp.code,
                )
            }
        }
    }

    suspend fun deleteVaultFileV2(
        baseUrl: String,
        token: String,
        path: String,
        baseRev: Long,
    ): SyncServerResult<VaultDeleteResultV2> = withContext(Dispatchers.IO) {
        safeResult {
            val url = normalizeJoin(baseUrl, "/api/v2/vault/file?path=${encodeQuery(path)}&baseRev=${encodeQuery(baseRev.coerceAtLeast(0L).toString())}")
            val req =
                Request.Builder()
                    .url(url)
                    .delete()
                    .header("Authorization", "Bearer $token")
                    .header("User-Agent", "Zhixu-Android")
                    .build()
            client.newCall(req).execute().use { resp ->
                val text = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) return@use SyncServerResult(false, errorMessage = text.ifBlank { "HTTP ${resp.code}" }, statusCode = resp.code)
                val obj = runCatching { JSONObject(text) }.getOrNull() ?: return@use SyncServerResult(false, errorMessage = "Invalid response", statusCode = resp.code)
                SyncServerResult(
                    ok = true,
                    value =
                        VaultDeleteResultV2(
                            path = obj.optString("path").orEmpty().ifBlank { path },
                            rev = obj.optLong("rev", baseRev + 1),
                            changeId = obj.optLong("changeId", 0L),
                            deleted = obj.optBoolean("deleted", true),
                        ),
                    statusCode = resp.code,
                )
            }
        }
    }

    private fun normalizeJoin(baseUrl: String, path: String): String {
        val left = baseUrl.trimEnd('/')
        val right = path.trim()
        return if (right.startsWith("/")) "$left$right" else "$left/$right"
    }

    private fun encodeQuery(value: String): String =
        URLEncoder.encode(value, StandardCharsets.UTF_8.name())

    private inline fun <T> safeResult(block: () -> SyncServerResult<T>): SyncServerResult<T> {
        return try {
            block()
        } catch (e: Throwable) {
            val (code, msg) =
                when (e) {
                    is UnknownHostException,
                    is ConnectException,
                    is SocketTimeoutException,
                    is InterruptedIOException,
                    -> 0 to "NETWORK_UNREACHABLE"
                    else -> -1 to ("${e.javaClass.simpleName}: ${e.message}".trimEnd(':', ' '))
                }
            SyncServerResult(ok = false, errorMessage = msg, statusCode = code)
        }
    }
}
