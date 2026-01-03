package app.zhixu.sync

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import app.zhixu.data.VaultRepository
import app.zhixu.data.vaultRootToDocumentFile
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

        val root = vaultRootToDocumentFile(context, rootUri) ?: error("Invalid vault root Uri")

        val logFile = ensureLocalFile(root, ".zhixu/sync/log.jsonl")
        val conflictsFile = ensureLocalFile(root, ".zhixu/sync/conflicts.jsonl")
        val logger = SyncLogger(logFile?.uri, conflictsFile?.uri)
        logger.logEvent("start", mapOf("engine" to "official", "includeIndexSqlite" to includeIndexSqlite.toString()))

        runCatching {
            val store = OfficialVaultSyncStateStore(context, repository)
            var state = store.load(rootUri)

            fun isProbablySame(local: OfficialLocalEntry, st: OfficialVaultSyncFileState): Boolean {
                val mtimeClose = local.lastModifiedEpochMs > 0 && st.localMtimeMs > 0 && abs(local.lastModifiedEpochMs - st.localMtimeMs) < 2_000
                return local.size == st.size && mtimeClose
            }

            suspend fun localSha256(local: OfficialLocalEntry): String {
                val bytes = repository.readBytes(local.file.uri) ?: return ""
                return sha256Hex(bytes)
            }

            suspend fun ensureConflictArtifact(path: String, kind: String, bytes: ByteArray) {
                val safePath = path.trimStart('/').replace('\\', '/')
                val now = System.currentTimeMillis()
                val baseDir = ".zhixu/conflicts/$safePath"
                val name = "$now-$kind"
                val destPath = "$baseDir/$name"
                val dest = ensureLocalFile(root, destPath) ?: return
                repository.writeBytes(dest.uri, bytes)
            }

            suspend fun applyRemote(path: String, remoteRev: Long, remoteDeleted: Boolean): Boolean {
                if (!shouldSyncPath(path, includeIndexSqlite = includeIndexSqlite)) return true

                val localFilesNow =
                    listLocalFiles(root, includeIndexSqlite = includeIndexSqlite)
                        .filter { shouldSyncPath(it.path, includeIndexSqlite = includeIndexSqlite) }
                val localByPathNow = localFilesNow.associateBy { it.path }
                val local = localByPathNow[path]
                val st = state.files[path]

                val localDirty =
                    when {
                        local == null -> st != null && !st.deleted
                        st == null -> true
                        st.deleted -> true
                        isProbablySame(local, st) -> false
                        st.sha256.isBlank() -> true
                        else -> localSha256(local) != st.sha256
                    }

                if (!localDirty) {
                    if (remoteDeleted) {
                        val ok = local?.file?.delete() ?: true
                        if (ok) {
                            state = state.withDeleted(path, remoteRev)
                            return true
                        }
                        return false
                    }

                    val download = SyncServerClient.downloadVaultFileV2(baseUrl, token, path)
                    val bytes = download.value?.bytes ?: return false
                    val dest = ensureLocalFile(root, path) ?: return false
                    repository.writeBytes(dest.uri, bytes)
                    val localMtime = repository.getDocumentLastModified(dest.uri)
                    val sha = download.value.sha256
                    state = state.withUploaded(path, rev = remoteRev, sha256 = sha, size = bytes.size.toLong(), localMtimeMs = localMtime)
                    return true
                }

                // Conflict: try best-effort text 3-way merge; otherwise keep both by writing remote under .zhixu/conflicts/.
                if (remoteDeleted) {
                    val localBytes = local?.let { repository.readBytes(it.file.uri) } ?: ByteArray(0)
                    if (localBytes.isNotEmpty()) ensureConflictArtifact(path, "local", localBytes)
                    state = state.withDeleted(path, remoteRev)
                    return true
                }

                val remote = SyncServerClient.downloadVaultFileV2(baseUrl, token, path).value ?: return false
                val localBytes = local?.let { repository.readBytes(it.file.uri) } ?: ByteArray(0)
                if (local == null) {
                    val dest = ensureLocalFile(root, path) ?: return false
                    repository.writeBytes(dest.uri, remote.bytes)
                    val localMtime = repository.getDocumentLastModified(dest.uri)
                    state = state.withUploaded(path, rev = remoteRev, sha256 = remote.sha256, size = remote.bytes.size.toLong(), localMtimeMs = localMtime)
                    return true
                }

                ensureConflictArtifact(path, "remote-r${remote.rev}", remote.bytes)
                logger.conflict(path, "remote_changed")

                // Without server-side history, resolve by keeping both:
                // - write remote under .zhixu/conflicts/
                // - upload local to remote (local wins)
                state = state.withUploaded(path, rev = remote.rev, sha256 = "", size = localBytes.size.toLong(), localMtimeMs = local.lastModifiedEpochMs)
                if (localBytes.isNotEmpty()) {
                    val put =
                        SyncServerClient.uploadVaultFileV2(
                            baseUrl = baseUrl,
                            token = token,
                            path = path,
                            mtimeMs = local.lastModifiedEpochMs.takeIf { it > 0L } ?: System.currentTimeMillis(),
                            bytes = localBytes,
                            baseRev = remote.rev,
                        )
                    if (put.ok && put.value != null) {
                        val localMtime = repository.getDocumentLastModified(local.file.uri).takeIf { it > 0L } ?: local.lastModifiedEpochMs
                        state =
                            state.withUploaded(
                                path,
                                rev = put.value.rev,
                                sha256 = put.value.sha256,
                                size = localBytes.size.toLong(),
                                localMtimeMs = localMtime,
                            )
                    }
                }
                return true
            }

            var uploaded = 0
            var downloaded = 0
            var deletedRemote = 0
            var deletedLocal = 0
            var conflicts = 0
            var failed = 0

            // Pull remote changes (snapshot on first run, then incremental).
            var since = state.serverCursor
            while (true) {
                val r = SyncServerClient.vaultChangesV2(baseUrl, token, since = since, limit = 2000)
                val v = r.value ?: error(r.errorMessage ?: "Failed to pull changes")
                if (v.snapshot) {
                    for (c in v.changes) {
                        val ok = applyRemote(c.path, c.rev, c.deleted)
                        if (ok) {
                            if (c.deleted) deletedLocal++ else downloaded++
                        } else {
                            failed++
                        }
                    }
                    since = v.cursor
                    state = state.copy(serverCursor = since)
                    break
                } else {
                    for (c in v.changes) {
                        val ok = applyRemote(c.path, c.rev, c.deleted)
                        if (ok) {
                            if (c.deleted) deletedLocal++ else downloaded++
                        } else {
                            failed++
                        }
                        since = maxOf(since, c.changeId)
                    }
                    if (!v.hasMore) {
                        state = state.copy(serverCursor = since)
                        break
                    }
                }
            }

            // Push local changes (including brand-new files).
            val localFiles =
                listLocalFiles(root, includeIndexSqlite = includeIndexSqlite)
                    .filter { shouldSyncPath(it.path, includeIndexSqlite = includeIndexSqlite) }
            val localByPath = localFiles.associateBy { it.path }
            for (local in localFiles) {
                val st = state.files[local.path]
                if (st != null && !st.deleted && isProbablySame(local, st)) continue

                val bytes = repository.readBytes(local.file.uri)
                if (bytes == null) {
                    failed++
                    continue
                }
                val sha = sha256Hex(bytes)
                if (st != null && !st.deleted && st.sha256.isNotBlank() && st.sha256 == sha) continue

                val baseRev = st?.baseRev ?: 0L
                val put = SyncServerClient.uploadVaultFileV2(baseUrl, token, local.path, local.lastModifiedEpochMs, bytes, baseRev = baseRev)
                if (put.ok && put.value != null) {
                    uploaded++
                    state = state.withUploaded(local.path, rev = put.value.rev, sha256 = put.value.sha256, size = bytes.size.toLong(), localMtimeMs = local.lastModifiedEpochMs)
                } else if (put.statusCode == 409) {
                    conflicts++
                    logger.conflict(local.path, "rev_conflict")
                    // Resolve via pulling latest and trying merge in applyRemote (which also uploads merged when possible).
                    val latest = SyncServerClient.downloadVaultFileV2(baseUrl, token, local.path).value
                    if (latest != null) {
                        val ok = applyRemote(local.path, latest.rev, remoteDeleted = false)
                        if (!ok) failed++
                    } else {
                        failed++
                    }
                } else {
                    failed++
                }
            }

            // Propagate local deletions.
            for ((path, st) in state.files) {
                if (st.deleted) continue
                if (!shouldSyncPath(path, includeIndexSqlite = includeIndexSqlite)) continue
                if (localByPath.containsKey(path)) continue
                val del = SyncServerClient.deleteVaultFileV2(baseUrl, token, path, baseRev = st.baseRev)
                if (del.ok && del.value != null) {
                    deletedRemote++
                    state = state.withDeleted(path, del.value.rev)
                } else if (del.statusCode == 409) {
                    conflicts++
                    logger.conflict(path, "delete_rev_conflict")
                } else {
                    failed++
                }
            }

            if (!includeIndexSqlite) {
                runCatching { repository.rebuildIndex(rootUri) }
            }

            runCatching { store.save(rootUri, state) }

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

    private fun shouldSyncPath(path: String, includeIndexSqlite: Boolean): Boolean {
        val p = path.trimStart('/')
        val name = p.substringAfterLast('/', missingDelimiterValue = p)
        if (name.startsWith("conflict ", ignoreCase = true)) return false
        if (p.equals(".zhixu/index.sqlite", ignoreCase = true)) return includeIndexSqlite
        if (p.equals(".zhixu", ignoreCase = true)) return false
        if (p.startsWith(".zhixu/", ignoreCase = true)) return false
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
                    if (path.equals(".zhixu", ignoreCase = true)) {
                        if (includeIndexSqlite) {
                            val idx = child.findFile("index.sqlite")
                            if (idx?.isFile == true) {
                                out +=
                                    OfficialLocalEntry(
                                        path = ".zhixu/index.sqlite",
                                        file = idx,
                                        size = idx.length(),
                                        lastModifiedEpochMs = idx.lastModified(),
                                    )
                            }
                        }
                        continue
                    }
                    if (path.startsWith(".zhixu/", ignoreCase = true)) continue
                    if (path.equals(".zhixu/sync", ignoreCase = true)) continue
                    if (path.startsWith(".zhixu/sync/", ignoreCase = true)) continue
                    if (path.equals(".zhixu/conflicts", ignoreCase = true)) continue
                    if (path.startsWith(".zhixu/conflicts/", ignoreCase = true)) continue
                    if (path.equals(".zhixu/history", ignoreCase = true)) continue
                    if (path.startsWith(".zhixu/history/", ignoreCase = true)) continue
                    walk(child, path)
                } else if (child.isFile) {
                    if (!includeIndexSqlite && path.equals(".zhixu/index.sqlite", ignoreCase = true)) continue
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

        walk(root, "")

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
        if (root.uri.scheme.equals("file", ignoreCase = true)) {
            val base = root.uri.path?.let { java.io.File(it) } ?: return null
            var dir = base
            for (i in 0 until parts.size - 1) {
                dir = java.io.File(dir, parts[i])
            }
            dir.mkdirs()
            val file = java.io.File(dir, parts.last())
            runCatching { if (!file.exists()) file.createNewFile() }
            return DocumentFile.fromFile(file)
        }
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
