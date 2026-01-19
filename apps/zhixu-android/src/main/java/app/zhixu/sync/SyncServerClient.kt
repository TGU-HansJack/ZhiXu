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
    val email: String = "",
    val avatar: SyncServerAvatarInfo? = null,
    val storage: SyncServerStorageInfo? = null,
)

data class SyncServerAvatarInfo(
    val mime: String,
    val updatedAtMs: Long,
    val hasAvatar: Boolean,
)

data class SyncServerStorageInfo(
    val usedBytes: Long,
    val limitBytes: Long,
)

data class SyncServerAvatarDownload(
    val bytes: ByteArray,
    val mime: String,
    val updatedAtMs: Long,
)

data class SyncServerAvatarUploadResult(
    val mime: String,
    val updatedAtMs: Long,
)

data class SyncServerStorageStats(
    val usedBytes: Long,
    val limitBytes: Long,
    val remainingBytes: Long,
    val fileCount: Long,
    val deletedCount: Long,
    val lastUpdatedAtMs: Long,
)

data class SyncServerStorageExport(
    val filename: String,
    val bytes: ByteArray,
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

    suspend fun sendEmailCode(
        baseUrl: String,
        email: String,
        purpose: String = "register",
    ): SyncServerResult<Unit> = withContext(Dispatchers.IO) {
        safeResult {
            val url = normalizeJoin(baseUrl, "/api/auth/email/code")
            val body =
                JSONObject()
                    .put("email", email.trim())
                    .put("purpose", purpose.trim())
                    .toString()
                    .toRequestBody("application/json; charset=utf-8".toMediaType())
            val req = Request.Builder().url(url).post(body).header("User-Agent", "Zhixu-Android").build()
            client.newCall(req).execute().use { resp ->
                val text = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) {
                    val parsed =
                        runCatching {
                            val obj = JSONObject(text)
                            obj.optString("error").orEmpty().ifBlank { obj.optString("message").orEmpty() }
                        }.getOrNull()
                    return@use SyncServerResult(
                        ok = false,
                        errorMessage = parsed?.ifBlank { text }.orEmpty().ifBlank { "HTTP ${resp.code}" },
                        statusCode = resp.code,
                    )
                }
                SyncServerResult(ok = true, value = Unit, statusCode = resp.code)
            }
        }
    }

    suspend fun register(
        baseUrl: String,
        username: String,
        password: String,
        email: String = "",
        emailCode: String = "",
    ): SyncServerResult<Long> = withContext(Dispatchers.IO) {
        safeResult {
            val url = normalizeJoin(baseUrl, "/api/auth/register")
            val json =
                JSONObject()
                    .put("username", username)
                    .put("password", password)
                    .also { if (email.isNotBlank()) it.put("email", email) }
                    .also { if (emailCode.isNotBlank()) it.put("emailCode", emailCode) }
            val body = json.toString().toRequestBody("application/json; charset=utf-8".toMediaType())
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
        deviceName: String = "",
    ): SyncServerResult<String> = withContext(Dispatchers.IO) {
        safeResult {
            val url = normalizeJoin(baseUrl, "/api/auth/login")
            val body =
                JSONObject()
                    .put("username", username)
                    .put("password", password)
                    .toString()
                    .toRequestBody("application/json; charset=utf-8".toMediaType())
            val req =
                Request.Builder()
                    .url(url)
                    .post(body)
                    .header("User-Agent", "Zhixu-Android")
                    .also { b ->
                        val dn = deviceName.trim()
                        if (dn.isNotBlank()) b.header("X-Zhixu-Device-Name", dn.take(128))
                    }.build()
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
                val email = obj.optString("email").orEmpty()

                val avatarObj = obj.optJSONObject("avatar")
                val avatar =
                    avatarObj?.let {
                        SyncServerAvatarInfo(
                            mime = it.optString("mime").orEmpty(),
                            updatedAtMs = it.optLong("updatedAtMs", 0L).coerceAtLeast(0L),
                            hasAvatar = it.optBoolean("hasAvatar", false),
                        )
                    }

                val storageObj = obj.optJSONObject("storage")
                val storage =
                    storageObj?.let {
                        SyncServerStorageInfo(
                            usedBytes = it.optLong("usedBytes", 0L).coerceAtLeast(0L),
                            limitBytes = it.optLong("limitBytes", 0L).coerceAtLeast(0L),
                        )
                    }

                SyncServerResult(
                    ok = true,
                    value = SyncServerMe(userId = userId, username = username, email = email, avatar = avatar, storage = storage),
                    statusCode = resp.code,
                )
            }
        }
    }

    suspend fun downloadAvatar(
        baseUrl: String,
        token: String,
    ): SyncServerResult<SyncServerAvatarDownload> = withContext(Dispatchers.IO) {
        safeResult {
            val url = normalizeJoin(baseUrl, "/api/account/avatar")
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
                    val text = runCatching { bytes.toString(Charsets.UTF_8) }.getOrDefault("")
                    return@use SyncServerResult(false, errorMessage = text.ifBlank { "HTTP ${resp.code}" }, statusCode = resp.code)
                }
                val mime = resp.header("Content-Type").orEmpty()
                val updatedAtMs = resp.header("X-Zhixu-Avatar-Updated-At-Ms")?.toLongOrNull() ?: 0L
                SyncServerResult(
                    ok = true,
                    value = SyncServerAvatarDownload(bytes = bytes, mime = mime, updatedAtMs = updatedAtMs),
                    statusCode = resp.code,
                )
            }
        }
    }

    suspend fun uploadAvatar(
        baseUrl: String,
        token: String,
        mime: String,
        bytes: ByteArray,
    ): SyncServerResult<SyncServerAvatarUploadResult> = withContext(Dispatchers.IO) {
        safeResult {
            val url = normalizeJoin(baseUrl, "/api/account/avatar")
            val safeMime = mime.trim().ifBlank { "application/octet-stream" }
            val body = bytes.toRequestBody("application/octet-stream".toMediaType())
            val req =
                Request.Builder()
                    .url(url)
                    .put(body)
                    .header("Authorization", "Bearer $token")
                    .header("User-Agent", "Zhixu-Android")
                    .header("X-Zhixu-Avatar-Mime", safeMime.take(128))
                    .build()
            client.newCall(req).execute().use { resp ->
                val text = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) return@use SyncServerResult(false, errorMessage = text.ifBlank { "HTTP ${resp.code}" }, statusCode = resp.code)
                val obj = runCatching { JSONObject(text) }.getOrNull() ?: return@use SyncServerResult(false, errorMessage = "Invalid response", statusCode = resp.code)
                val out =
                    SyncServerAvatarUploadResult(
                        mime = obj.optString("mime").orEmpty().ifBlank { mime },
                        updatedAtMs = obj.optLong("updatedAtMs", 0L).coerceAtLeast(0L),
                    )
                SyncServerResult(ok = true, value = out, statusCode = resp.code)
            }
        }
    }

    suspend fun deleteAvatar(
        baseUrl: String,
        token: String,
    ): SyncServerResult<Unit> = withContext(Dispatchers.IO) {
        safeResult {
            val url = normalizeJoin(baseUrl, "/api/account/avatar")
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
                SyncServerResult(ok = true, value = Unit, statusCode = resp.code)
            }
        }
    }

    suspend fun storageStats(
        baseUrl: String,
        token: String,
    ): SyncServerResult<SyncServerStorageStats> = withContext(Dispatchers.IO) {
        safeResult {
            val url = normalizeJoin(baseUrl, "/api/storage/stats")
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
                SyncServerResult(
                    ok = true,
                    value =
                        SyncServerStorageStats(
                            usedBytes = obj.optLong("usedBytes", 0L).coerceAtLeast(0L),
                            limitBytes = obj.optLong("limitBytes", 0L).coerceAtLeast(0L),
                            remainingBytes = obj.optLong("remainingBytes", 0L).coerceAtLeast(0L),
                            fileCount = obj.optLong("fileCount", 0L).coerceAtLeast(0L),
                            deletedCount = obj.optLong("deletedCount", 0L).coerceAtLeast(0L),
                            lastUpdatedAtMs = obj.optLong("lastUpdatedAtMs", 0L).coerceAtLeast(0L),
                        ),
                    statusCode = resp.code,
                )
            }
        }
    }

    suspend fun exportStorageZip(
        baseUrl: String,
        token: String,
    ): SyncServerResult<SyncServerStorageExport> = withContext(Dispatchers.IO) {
        safeResult {
            val url = normalizeJoin(baseUrl, "/api/storage/export")
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
                    val text = runCatching { bytes.toString(Charsets.UTF_8) }.getOrDefault("")
                    return@use SyncServerResult(false, errorMessage = text.ifBlank { "HTTP ${resp.code}" }, statusCode = resp.code)
                }

                val cd = resp.header("Content-Disposition").orEmpty()
                val filename =
                    cd.split(';')
                        .map { it.trim() }
                        .firstOrNull { it.startsWith("filename=", ignoreCase = true) }
                        ?.substringAfter('=')
                        ?.trim()
                        ?.trim('"')
                        ?.takeIf { it.isNotBlank() }
                        ?: "zhixu-vault.zip"

                SyncServerResult(
                    ok = true,
                    value = SyncServerStorageExport(filename = filename, bytes = bytes),
                    statusCode = resp.code,
                )
            }
        }
    }

    suspend fun changePassword(
        baseUrl: String,
        token: String,
        currentPassword: String,
        newPassword: String,
    ): SyncServerResult<Unit> = withContext(Dispatchers.IO) {
        safeResult {
            val url = normalizeJoin(baseUrl, "/api/account/password")
            val body =
                JSONObject()
                    .put("currentPassword", currentPassword)
                    .put("newPassword", newPassword)
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
                SyncServerResult(ok = true, value = Unit, statusCode = resp.code)
            }
        }
    }

    data class AccountSession(
        val sessionId: String,
        val name: String,
        val client: String,
        val lastSeenText: String,
        val ip: String,
        val location: String,
        val isCurrent: Boolean,
    )

    data class AccountSyncLog(
        val id: Long,
        val createdAtMs: Long,
        val action: String,
        val path: String,
        val ip: String,
        val client: String,
        val sessionId: String,
        val deviceName: String,
        val sizeBytes: Long,
    )

    suspend fun listSessions(
        baseUrl: String,
        token: String,
    ): SyncServerResult<List<AccountSession>> = withContext(Dispatchers.IO) {
        safeResult {
            val url = normalizeJoin(baseUrl, "/api/account/sessions")
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
                val arr = runCatching { JSONObject(text).optJSONArray("sessions") ?: JSONArray() }.getOrNull() ?: JSONArray()
                val out = ArrayList<AccountSession>(arr.length())
                for (i in 0 until arr.length()) {
                    val item = arr.optJSONObject(i) ?: continue
                    val sessionId = item.optString("sessionId").orEmpty().ifBlank { item.optString("id").orEmpty() }
                    if (sessionId.isBlank()) continue
                    out +=
                        AccountSession(
                            sessionId = sessionId,
                            name = item.optString("name").orEmpty().ifBlank { item.optString("deviceName").orEmpty() },
                            client = item.optString("client").orEmpty().ifBlank { item.optString("platform").orEmpty() },
                            lastSeenText = item.optString("lastSeenText").orEmpty().ifBlank { item.optString("lastSeenAt").orEmpty() },
                            ip = item.optString("ip").orEmpty(),
                            location = item.optString("location").orEmpty(),
                            isCurrent = item.optBoolean("current", false) || item.optBoolean("isCurrent", false),
                        )
                }
                SyncServerResult(ok = true, value = out, statusCode = resp.code)
            }
        }
    }

    suspend fun listSyncLogs(
        baseUrl: String,
        token: String,
        limit: Int = 100,
    ): SyncServerResult<List<AccountSyncLog>> = withContext(Dispatchers.IO) {
        safeResult {
            val safeLimit = limit.coerceIn(1, 500)
            val url = normalizeJoin(baseUrl, "/api/account/sync/logs?limit=$safeLimit")
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
                val arr = runCatching { JSONObject(text).optJSONArray("logs") ?: JSONArray() }.getOrNull() ?: JSONArray()
                val out = ArrayList<AccountSyncLog>(arr.length())
                for (i in 0 until arr.length()) {
                    val item = arr.optJSONObject(i) ?: continue
                    val id = item.optLong("id", 0L)
                    if (id <= 0L) continue
                    out +=
                        AccountSyncLog(
                            id = id,
                            createdAtMs = item.optLong("createdAtMs", 0L),
                            action = item.optString("action").orEmpty(),
                            path = item.optString("path").orEmpty(),
                            ip = item.optString("ip").orEmpty(),
                            client = item.optString("client").orEmpty(),
                            sessionId = item.optString("sessionId").orEmpty(),
                            deviceName = item.optString("deviceName").orEmpty(),
                            sizeBytes = item.optLong("sizeBytes", 0L),
                        )
                }
                SyncServerResult(ok = true, value = out, statusCode = resp.code)
            }
        }
    }

    suspend fun revokeSession(
        baseUrl: String,
        token: String,
        sessionId: String,
    ): SyncServerResult<Unit> = withContext(Dispatchers.IO) {
        safeResult {
            val url = normalizeJoin(baseUrl, "/api/account/sessions/revoke")
            val body =
                JSONObject()
                    .put("sessionId", sessionId)
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
                SyncServerResult(ok = true, value = Unit, statusCode = resp.code)
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
