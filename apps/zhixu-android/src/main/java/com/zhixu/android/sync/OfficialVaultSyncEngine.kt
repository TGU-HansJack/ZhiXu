package com.zhixu.android.sync

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.zhixu.android.data.VaultRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest
import kotlin.math.abs

data class OfficialVaultSyncSummary(
    val uploaded: Int,
    val downloaded: Int,
    val deletedRemote: Int,
    val deletedLocal: Int,
    val conflicts: Int,
    val failed: Int,
)

internal data class OfficialRemoteEntry(
    val path: String,
    val updatedAt: Long,
    val mtimeMs: Long,
    val size: Long,
    val sha256: String,
    val deleted: Boolean,
)

internal data class OfficialLocalEntry(
    val path: String,
    val file: DocumentFile,
    val size: Long,
    val lastModifiedEpochMs: Long,
)

class OfficialVaultSyncEngine(
    private val context: Context,
    private val repository: VaultRepository,
) {
    suspend fun syncVault(
        rootUri: Uri,
        baseUrl: String,
        token: String,
        includeIndexSqlite: Boolean,
    ): OfficialVaultSyncSummary = withContext(Dispatchers.IO) {
        require(token.isNotBlank()) { "Not logged in" }

        repository.ensureVaultStructure(rootUri)
        if (includeIndexSqlite) {
            repository.exportIndexSqlite(rootUri)
        }

        val root = DocumentFile.fromTreeUri(context, rootUri) ?: error("Invalid vault root Uri")

        val logFile = ensureLocalFile(root, ".zhixu/sync/log.jsonl")
        val conflictsFile = ensureLocalFile(root, ".zhixu/sync/conflicts.jsonl")
        val stateFile = ensureLocalFile(root, ".zhixu/sync/official_state.json")
        val logger = SyncLogger(logFile?.uri, conflictsFile?.uri)
        logger.logEvent("start", mapOf("engine" to "official", "includeIndexSqlite" to includeIndexSqlite.toString()))

        runCatching {
            val prevPaths = readStatePaths(stateFile?.uri)
            val localFiles = listLocalFiles(root, includeIndexSqlite = includeIndexSqlite).filter { shouldSyncPath(it.path) }
            val localByPath = localFiles.associateBy { it.path }

            val manifestResult = SyncServerClient.vaultManifest(baseUrl, token)
            val remote = manifestResult.value ?: error(manifestResult.errorMessage ?: "Failed to fetch manifest")
            val remoteEntries = remote.files.map {
                OfficialRemoteEntry(
                    path = it.path,
                    updatedAt = it.updatedAt,
                    mtimeMs = it.mtimeMs,
                    size = it.size,
                    sha256 = it.sha256,
                    deleted = it.deleted,
                )
            }.filter { shouldSyncPath(it.path) }
            val remoteByPath = remoteEntries.associateBy { it.path }

            var uploaded = 0
            var downloaded = 0
            var deletedRemote = 0
            var deletedLocal = 0
            var conflicts = 0
            var failed = 0

            // Apply remote deletions (tombstones), but keep local changes by re-uploading.
            for (remoteEntry in remoteEntries) {
                if (!remoteEntry.deleted) continue
                val local = localByPath[remoteEntry.path] ?: continue
                val localBytes = repository.readBytes(local.file.uri) ?: ByteArray(0)
                val sameAsDeleted =
                    remoteEntry.sha256.isNotBlank() &&
                        remoteEntry.size >= 0 &&
                        remoteEntry.size == local.size &&
                        localBytes.isNotEmpty() &&
                        sha256Hex(localBytes) == remoteEntry.sha256

                if (prevPaths.contains(remoteEntry.path) && sameAsDeleted) {
                    val ok = runCatching { local.file.delete() }.getOrDefault(false)
                    if (ok) deletedLocal++ else failed++
                } else {
                    val ok = uploadFile(baseUrl, token, local)
                    if (ok) {
                        uploaded++
                        conflicts++
                        logger.conflict(remoteEntry.path, "remote_deleted")
                    } else {
                        failed++
                    }
                }
            }

            // Handle remote-only files: download new files, or propagate local deletions.
            for (remoteEntry in remoteEntries) {
                if (remoteEntry.deleted) continue
                if (localByPath.containsKey(remoteEntry.path)) continue
                val ok =
                    if (prevPaths.contains(remoteEntry.path)) {
                        // It existed last sync but now missing locally -> treat as local deletion.
                        deleteRemote(baseUrl, token, remoteEntry.path)
                            .also { if (it) deletedRemote++ else failed++ }
                    } else {
                        downloadFile(root, baseUrl, token, remoteEntry.path)
                            .also { if (it) downloaded++ else failed++ }
                    }
                if (!ok) {
                    // already counted
                }
            }

            // Upload local-only files.
            for (local in localFiles) {
                if (remoteByPath.containsKey(local.path)) continue
                val ok = uploadFile(baseUrl, token, local)
                if (ok) uploaded++ else failed++
            }

            // Resolve mismatches: download remote as conflict, then upload local.
            for (local in localFiles) {
                val remoteEntry = remoteByPath[local.path] ?: continue
                if (remoteEntry.deleted) continue
                if (sameContent(local, remoteEntry)) continue

                val okConflict =
                    try {
                        downloadConflictFile(root, baseUrl, token, local.path)
                    } catch (_: Throwable) {
                        false
                    }
                if (okConflict) {
                    conflicts++
                    logger.conflict(local.path, "download_remote_conflict")
                } else {
                    failed++
                    continue
                }

                val okUpload = uploadFile(baseUrl, token, local)
                if (okUpload) uploaded++ else failed++
            }

            if (!includeIndexSqlite) {
                runCatching { repository.rebuildIndex(rootUri) }
            }

            // Save state after sync.
            runCatching {
                val currentPaths =
                    listLocalFiles(root, includeIndexSqlite = includeIndexSqlite)
                        .map { it.path }
                        .filter { shouldSyncPath(it) }
                        .sorted()
                writeStatePaths(stateFile?.uri, currentPaths)
            }

            OfficialVaultSyncSummary(
                uploaded = uploaded,
                downloaded = downloaded,
                deletedRemote = deletedRemote,
                deletedLocal = deletedLocal,
                conflicts = conflicts,
                failed = failed,
            )
        }.fold(
            onSuccess = { summary ->
                logger.logEvent(
                    "end",
                    mapOf(
                        "ok" to "true",
                        "uploaded" to summary.uploaded.toString(),
                        "downloaded" to summary.downloaded.toString(),
                        "deletedRemote" to summary.deletedRemote.toString(),
                        "deletedLocal" to summary.deletedLocal.toString(),
                        "conflicts" to summary.conflicts.toString(),
                        "failed" to summary.failed.toString(),
                    ),
                )
                summary
            },
            onFailure = { e ->
                logger.logEvent("end", mapOf("ok" to "false", "error" to (e.message ?: e.javaClass.simpleName)))
                throw e
            },
        )
    }

    private inner class SyncLogger(
        private val logUri: Uri?,
        private val conflictsUri: Uri?,
    ) {
        suspend fun logEvent(event: String, fields: Map<String, String>) {
            val uri = logUri ?: return
            val now = System.currentTimeMillis()
            val payload = LinkedHashMap<String, String>(fields.size + 2)
            payload["ts"] = now.toString()
            payload["event"] = event
            payload.putAll(fields)
            val line = payload.entries.joinToString(prefix = "{", postfix = "}") { (k, v) ->
                "\"${escapeJson(k)}\":\"${escapeJson(v)}\""
            }
            runCatching { repository.appendText(uri, "$line\n") }
        }

        suspend fun conflict(path: String, reason: String) {
            val uri = conflictsUri ?: return
            val now = System.currentTimeMillis()
            val line =
                "{" +
                    "\"ts\":\"$now\"," +
                    "\"path\":\"${escapeJson(path)}\"," +
                    "\"reason\":\"${escapeJson(reason)}\"" +
                    "}"
            runCatching { repository.appendText(uri, "$line\n") }
        }
    }

    private fun shouldSyncPath(path: String): Boolean {
        val p = path.trimStart('/')
        if (p.equals(".zhixu/sync/log.jsonl", ignoreCase = true)) return false
        if (p.equals(".zhixu/sync/conflicts.jsonl", ignoreCase = true)) return false
        if (p.equals(".zhixu/sync/official_state.json", ignoreCase = true)) return false
        return true
    }

    private fun escapeJson(raw: String): String {
        val out = StringBuilder(raw.length + 16)
        for (ch in raw) {
            when (ch) {
                '\\' -> out.append("\\\\")
                '"' -> out.append("\\\"")
                '\n' -> out.append("\\n")
                '\r' -> out.append("\\r")
                '\t' -> out.append("\\t")
                else -> out.append(ch)
            }
        }
        return out.toString()
    }

    private fun listLocalFiles(root: DocumentFile, includeIndexSqlite: Boolean): List<OfficialLocalEntry> {
        val out = ArrayList<OfficialLocalEntry>()

        fun walk(dir: DocumentFile, prefix: String) {
            for (child in dir.listFiles()) {
                val name = child.name ?: continue
                val path = if (prefix.isBlank()) name else "$prefix/$name"
                if (child.isDirectory) {
                    walk(child, path)
                } else if (child.isFile) {
                    out +=
                        OfficialLocalEntry(
                            path = path,
                            file = child,
                            size = child.length(),
                            lastModifiedEpochMs = child.lastModified(),
                        )
                }
            }
        }

        val docs = root.findFile("docs")?.takeIf { it.isDirectory }
        val attachments = root.findFile("attachments")?.takeIf { it.isDirectory }
        val zhixu = root.findFile(".zhixu")?.takeIf { it.isDirectory }

        if (docs != null) walk(docs, "docs")
        if (attachments != null) walk(attachments, "attachments")
        if (zhixu != null) {
            val settings = zhixu.findFile("settings.json")?.takeIf { it.isFile }
            if (settings != null) {
                out += OfficialLocalEntry(".zhixu/settings.json", settings, settings.length(), settings.lastModified())
            }
            val sync = zhixu.findFile("sync")?.takeIf { it.isDirectory }
            if (sync != null) walk(sync, ".zhixu/sync")
            if (includeIndexSqlite) {
                val index = zhixu.findFile("index.sqlite")?.takeIf { it.isFile }
                if (index != null) {
                    out += OfficialLocalEntry(".zhixu/index.sqlite", index, index.length(), index.lastModified())
                }
            }
        }

        return out
    }

    private suspend fun sameContent(local: OfficialLocalEntry, remote: OfficialRemoteEntry): Boolean {
        if (remote.size >= 0 && remote.size != local.size) return false
        if (remote.mtimeMs > 0 && local.lastModifiedEpochMs > 0 && abs(remote.mtimeMs - local.lastModifiedEpochMs) < 2_000) return true
        if (remote.sha256.isBlank()) return false
        val bytes = repository.readBytes(local.file.uri) ?: return false
        return sha256Hex(bytes) == remote.sha256
    }

    private suspend fun uploadFile(
        baseUrl: String,
        token: String,
        local: OfficialLocalEntry,
    ): Boolean {
        val bytes = repository.readBytes(local.file.uri) ?: return false
        val r = SyncServerClient.uploadVaultFile(baseUrl, token, local.path, local.lastModifiedEpochMs, bytes)
        return r.ok
    }

    private suspend fun deleteRemote(
        baseUrl: String,
        token: String,
        path: String,
    ): Boolean {
        val r = SyncServerClient.deleteVaultFile(baseUrl, token, path)
        return r.ok
    }

    private suspend fun downloadFile(
        root: DocumentFile,
        baseUrl: String,
        token: String,
        path: String,
    ): Boolean {
        val r = SyncServerClient.downloadVaultFile(baseUrl, token, path)
        val bytes = r.value?.bytes ?: return false
        val dest = ensureLocalFile(root, path) ?: return false
        repository.writeBytes(dest.uri, bytes)
        return true
    }

    private suspend fun downloadConflictFile(
        root: DocumentFile,
        baseUrl: String,
        token: String,
        path: String,
    ): Boolean {
        val r = SyncServerClient.downloadVaultFile(baseUrl, token, path)
        val bytes = r.value?.bytes ?: return false
        val parentPath = path.substringBeforeLast('/', missingDelimiterValue = "")
        val fileName = path.substringAfterLast('/')
        val conflictName = "conflict ${System.currentTimeMillis()} $fileName"
        val destPath = if (parentPath.isBlank()) conflictName else "$parentPath/$conflictName"
        val dest = ensureLocalFile(root, destPath) ?: return false
        repository.writeBytes(dest.uri, bytes)
        return true
    }

    private fun ensureLocalFile(root: DocumentFile, path: String): DocumentFile? {
        val parts = path.split('/').filter { it.isNotBlank() }
        if (parts.isEmpty()) return null
        var dir = root
        for (i in 0 until parts.size - 1) {
            val name = parts[i]
            dir = dir.findFile(name) ?: dir.createDirectory(name) ?: return null
        }
        val fileName = parts.last()
        return dir.findFile(fileName) ?: dir.createFile("application/octet-stream", fileName)
    }

    private fun sha256Hex(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
        val sb = StringBuilder(digest.size * 2)
        for (b in digest) sb.append(((b.toInt() and 0xff) + 0x100).toString(16).substring(1))
        return sb.toString()
    }

    private suspend fun readStatePaths(stateUri: Uri?): Set<String> {
        val uri = stateUri ?: return emptySet()
        val text = runCatching { repository.readText(uri) }.getOrNull().orEmpty()
        val obj = runCatching { JSONObject(text) }.getOrNull() ?: return emptySet()
        val arr = obj.optJSONArray("paths") ?: JSONArray()
        val out = HashSet<String>(arr.length())
        for (i in 0 until arr.length()) {
            val p = arr.optString(i).orEmpty()
            if (p.isNotBlank()) out += p
        }
        return out
    }

    private suspend fun writeStatePaths(stateUri: Uri?, paths: List<String>) {
        val uri = stateUri ?: return
        val obj =
            JSONObject()
                .put("version", 1)
                .put("savedAt", System.currentTimeMillis())
                .put("paths", JSONArray(paths))
        runCatching { repository.writeText(uri, obj.toString()) }
    }
}
