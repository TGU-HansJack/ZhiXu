package app.zhixu.sync

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import app.zhixu.data.VaultRepository
import app.zhixu.data.vaultRootToDocumentFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
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

data class SyncServerPlannedOp(
    val kind: WebDavPlannedOpKind,
    val path: String,
    val reason: String,
)

data class SyncServerSyncPlan(
    val generatedAtMs: Long,
    val baseUrl: String,
    val includeIndexSqlite: Boolean,
    val operations: List<SyncServerPlannedOp>,
    val summary: OfficialVaultSyncSummary,
)

data class SyncServerSyncObservedOpResult(
    val op: SyncServerPlannedOp,
    val state: SyncServerSyncTaskOpState,
    val error: String?,
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
    suspend fun planVault(
        rootUri: Uri,
        baseUrl: String,
        token: String,
        includeIndexSqlite: Boolean,
    ): SyncServerSyncPlan = withContext(Dispatchers.IO) {
        val generatedAtMs = System.currentTimeMillis()
        require(token.isNotBlank()) { "Not logged in" }

        repository.ensureVaultStructure(rootUri)
        if (includeIndexSqlite) {
            repository.exportIndexSqlite(rootUri)
        }

        val root = vaultRootToDocumentFile(context, rootUri) ?: error("Invalid vault root Uri")
        val store = OfficialVaultSyncStateStore(context, repository)
        val state = store.load(rootUri)

        val localFiles = listLocalFiles(root, includeIndexSqlite = includeIndexSqlite).filter { shouldSyncPath(it.path) }
        val localByPath = localFiles.associateBy { it.path }

        fun isProbablySame(local: OfficialLocalEntry, st: OfficialVaultSyncFileState): Boolean {
            val mtimeClose = local.lastModifiedEpochMs > 0 && st.localMtimeMs > 0 && abs(local.lastModifiedEpochMs - st.localMtimeMs) < 2_000
            return local.size == st.size && mtimeClose
        }

        suspend fun localSha256(local: OfficialLocalEntry): String {
            val bytes = repository.readBytes(local.file.uri) ?: return ""
            return sha256Hex(bytes)
        }

        val operations = ArrayList<SyncServerPlannedOp>()
        val seenPaths = HashSet<String>()

        var uploaded = 0
        var downloaded = 0
        var deletedRemote = 0
        var deletedLocal = 0
        var conflicts = 0
        var failed = 0

        fun norm(raw: String): String = raw.trim().trimStart('/').replace('\\', '/')

        suspend fun planRemoteChange(change: VaultChangeEntry) {
            val path = norm(change.path)
            if (path.isBlank()) return
            if (!shouldSyncPath(path)) return

            val st = state.files[path]
            if (st != null && st.baseRev >= change.rev && st.deleted == change.deleted) return

            seenPaths.add(path)

            val local = localByPath[path]

            val localDirty =
                when {
                    local == null -> st != null && !st.deleted
                    st == null -> true
                    st.deleted -> true
                    isProbablySame(local, st) -> false
                    st.sha256.isBlank() -> true
                    else -> localSha256(local) != st.sha256
                }

            if (change.deleted) {
                if (local == null) return
                if (localDirty) {
                    conflicts++
                    operations += SyncServerPlannedOp(WebDavPlannedOpKind.CONFLICT, path, "remote deleted vs local changed")
                } else {
                    deletedLocal++
                    operations += SyncServerPlannedOp(WebDavPlannedOpKind.DELETE_LOCAL, path, "remote deleted")
                }
                return
            }

            // Remote file exists/updated.
            if (local == null) {
                downloaded++
                operations += SyncServerPlannedOp(WebDavPlannedOpKind.DOWNLOAD, path, "missing local")
                return
            }

            if (localDirty) {
                conflicts++
                operations += SyncServerPlannedOp(WebDavPlannedOpKind.CONFLICT, path, "remote changed vs local changed")
            } else {
                downloaded++
                operations += SyncServerPlannedOp(WebDavPlannedOpKind.DOWNLOAD, path, "remote changed")
            }
        }

        // Pull remote changes (snapshot on first run, then incremental).
        var since = state.serverCursor
        while (true) {
            val r = SyncServerClient.vaultChangesV2(baseUrl, token, since = since, limit = 2000)
            val v = r.value ?: error(r.errorMessage ?: "Failed to pull changes")
            if (v.snapshot) {
                for (c in v.changes) {
                    runCatching { planRemoteChange(c) }.onFailure { failed++ }
                }
                break
            } else {
                for (c in v.changes) {
                    runCatching { planRemoteChange(c) }.onFailure { failed++ }
                    since = maxOf(since, c.changeId)
                }
                if (!v.hasMore) break
            }
        }

        // Push local changes (including brand-new files).
        for (local in localFiles) {
            if (seenPaths.contains(local.path)) continue
            val st = state.files[local.path]
            if (st != null && !st.deleted && isProbablySame(local, st)) continue

            val bytes = repository.readBytes(local.file.uri)
            if (bytes == null) {
                failed++
                continue
            }
            // Server rejects empty body; also protects against transient truncate windows.
            if (bytes.isEmpty()) continue
            val sha = sha256Hex(bytes)
            if (st != null && !st.deleted && st.sha256.isNotBlank() && st.sha256 == sha) continue

            uploaded++
            operations += SyncServerPlannedOp(WebDavPlannedOpKind.UPLOAD, local.path, "local changed")
        }

        // Propagate local deletions.
        for ((path, st) in state.files) {
            if (seenPaths.contains(path)) continue
            if (st.deleted) continue
            if (!shouldSyncPath(path)) continue
            if (localByPath.containsKey(path)) continue
            deletedRemote++
            operations += SyncServerPlannedOp(WebDavPlannedOpKind.DELETE_REMOTE, path, "missing local")
        }

        SyncServerSyncPlan(
            generatedAtMs = generatedAtMs,
            baseUrl = baseUrl.trim(),
            includeIndexSqlite = includeIndexSqlite,
            operations = operations,
            summary =
                OfficialVaultSyncSummary(
                    uploaded = uploaded,
                    downloaded = downloaded,
                    deletedRemote = deletedRemote,
                    deletedLocal = deletedLocal,
                    conflicts = conflicts,
                    failed = failed,
                ),
        )
    }

    suspend fun syncVaultWithExpectedPlan(
        rootUri: Uri,
        baseUrl: String,
        token: String,
        includeIndexSqlite: Boolean,
        expectedOperations: List<SyncServerPlannedOp>,
        observer: ((SyncServerSyncObservedOpResult) -> Unit)? = null,
    ): OfficialVaultSyncSummary = withContext(Dispatchers.IO) {
        val startedAtMs = System.currentTimeMillis()
        require(token.isNotBlank()) { "Not logged in" }

        fun norm(raw: String): String = raw.trim().trimStart('/').replace('\\', '/')

        val normalizedExpected =
            expectedOperations
                .map { op -> op.copy(path = norm(op.path)) }
                .filter { it.path.isNotBlank() }
                .filter { shouldSyncPath(it.path) }

        // Task semantics: if the task declares no operations, do nothing.
        if (normalizedExpected.isEmpty()) {
            val root = vaultRootToDocumentFile(context, rootUri) ?: return@withContext OfficialVaultSyncSummary(0, 0, 0, 0, 0, 0)
            val endedAtMs = startedAtMs
            val summary = OfficialVaultSyncSummary(0, 0, 0, 0, 0, 0)
            runCatching {
                writeLastSummary(
                    root = root,
                    startedAtMs = startedAtMs,
                    endedAtMs = endedAtMs,
                    baseUrl = baseUrl.trim(),
                    includeIndexSqlite = includeIndexSqlite,
                    summary = summary,
                    error = null,
                )
            }
            return@withContext summary
        }

        repository.ensureVaultStructure(rootUri)
        if (includeIndexSqlite) {
            repository.exportIndexSqlite(rootUri)
        }

        val root = vaultRootToDocumentFile(context, rootUri) ?: error("Invalid vault root Uri")

        val logFile = ensureLocalFile(root, ".zhixu/sync/log.jsonl")
        val conflictsFile = ensureLocalFile(root, ".zhixu/sync/conflicts.jsonl")
        val logger = SyncLogger(logFile?.uri, conflictsFile?.uri)
        logger.logEvent("start", mapOf("engine" to "official_task", "includeIndexSqlite" to includeIndexSqlite.toString()))

        data class RemoteMeta(val rev: Long, val deleted: Boolean)

        suspend fun loadRemoteMeta(paths: Set<String>): Map<String, RemoteMeta> {
            if (paths.isEmpty()) return emptyMap()
            val r = SyncServerClient.vaultChangesV2(baseUrl, token, since = 0L, limit = 5000)
            val v = r.value ?: return emptyMap()
            val out = HashMap<String, RemoteMeta>(paths.size)
            for (c in v.changes) {
                val p = norm(c.path)
                if (!paths.contains(p)) continue
                out[p] = RemoteMeta(rev = c.rev, deleted = c.deleted)
            }
            return out
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

        runCatching {
            val store = OfficialVaultSyncStateStore(context, repository)
            var state = store.load(rootUri)

            val onlyPaths = normalizedExpected.map { it.path }.toSet()
            val localByPath =
                listLocalFiles(root, includeIndexSqlite = includeIndexSqlite)
                    .filter { shouldSyncPath(it.path) && onlyPaths.contains(it.path) }
                    .associateBy { it.path }
                    .toMutableMap()

            val remoteMetaPaths =
                normalizedExpected
                    .filter {
                        it.kind == WebDavPlannedOpKind.DELETE_LOCAL ||
                            it.kind == WebDavPlannedOpKind.DELETE_REMOTE ||
                            it.kind == WebDavPlannedOpKind.CONFLICT
                    }
                    .map { it.path }
                    .toSet()
            val remoteMeta = loadRemoteMeta(remoteMetaPaths)

            var uploaded = 0
            var downloaded = 0
            var deletedRemote = 0
            var deletedLocal = 0
            var conflicts = 0
            var failed = 0

            for (op in normalizedExpected) {
                var opState = SyncServerSyncTaskOpState.FAILED
                var err: String? = null
                try {
                    when (op.kind) {
                        WebDavPlannedOpKind.UPLOAD -> {
                            val local = localByPath[op.path]
                            if (local == null) {
                                err = "Missing local file"
                            } else {
                                val bytes = readBytesForUpload(local.file.uri, maxAttempts = 4)
                                if (bytes == null) {
                                    err = "Failed to read local file"
                                } else if (bytes.isEmpty()) {
                                    // Server rejects empty body; most commonly observed during new-file creation or truncate+rewrite windows.
                                    opState = SyncServerSyncTaskOpState.SKIPPED
                                    err = "Empty local file; skipped upload"
                                } else {
                                    val baseRev = state.files[op.path]?.baseRev ?: 0L
                                    val put =
                                        SyncServerClient.uploadVaultFileV2(
                                            baseUrl = baseUrl,
                                            token = token,
                                            path = op.path,
                                            mtimeMs = repository.getDocumentLastModified(local.file.uri).takeIf { it > 0L } ?: System.currentTimeMillis(),
                                            bytes = bytes,
                                            baseRev = baseRev,
                                        )
                                    if (put.ok && put.value != null) {
                                        uploaded++
                                        val localMtime = repository.getDocumentLastModified(local.file.uri).takeIf { it > 0L } ?: local.lastModifiedEpochMs
                                        state =
                                            state.withUploaded(
                                                op.path,
                                                rev = put.value.rev,
                                                sha256 = put.value.sha256,
                                                size = bytes.size.toLong(),
                                                localMtimeMs = localMtime,
                                            )
                                        opState = SyncServerSyncTaskOpState.DONE
                                    } else if (put.statusCode == 409) {
                                        conflicts++
                                        logger.conflict(op.path, "rev_conflict")
                                        // Best-effort resolve: keep remote as artifact and upload local (local wins).
                                        val latest = SyncServerClient.downloadVaultFileV2(baseUrl, token, op.path)
                                        val remote = latest.value
                                        if (remote != null) {
                                            ensureConflictArtifact(op.path, "remote-r${remote.rev}", remote.bytes)
                                            val retry =
                                                SyncServerClient.uploadVaultFileV2(
                                                    baseUrl = baseUrl,
                                                    token = token,
                                                    path = op.path,
                                                    mtimeMs = repository.getDocumentLastModified(local.file.uri).takeIf { it > 0L } ?: System.currentTimeMillis(),
                                                    bytes = bytes,
                                                    baseRev = remote.rev,
                                                )
                                            if (retry.ok && retry.value != null) {
                                                uploaded++
                                                val localMtime = repository.getDocumentLastModified(local.file.uri).takeIf { it > 0L } ?: local.lastModifiedEpochMs
                                                state =
                                                    state.withUploaded(
                                                        op.path,
                                                        rev = retry.value.rev,
                                                        sha256 = retry.value.sha256,
                                                        size = bytes.size.toLong(),
                                                        localMtimeMs = localMtime,
                                                    )
                                                opState = SyncServerSyncTaskOpState.DONE
                                            } else {
                                                err = retry.errorMessage ?: "Upload conflict"
                                            }
                                        } else {
                                            err = put.errorMessage ?: "Upload conflict"
                                        }
                                    } else {
                                        err = put.errorMessage ?: "Upload failed"
                                    }
                                }
                            }
                        }

                        WebDavPlannedOpKind.DOWNLOAD -> {
                            val r = SyncServerClient.downloadVaultFileV2(baseUrl, token, op.path)
                            val remote = r.value
                            if (remote == null) {
                                err = r.errorMessage ?: "Download failed"
                            } else {
                                val dest = ensureLocalFile(root, op.path)
                                if (dest == null) {
                                    err = "Failed to create local file"
                                } else {
                                    repository.writeBytes(dest.uri, remote.bytes)
                                    val localMtime = repository.getDocumentLastModified(dest.uri)
                                    downloaded++
                                    state =
                                        state.withUploaded(
                                            op.path,
                                            rev = remote.rev,
                                            sha256 = remote.sha256,
                                            size = remote.bytes.size.toLong(),
                                            localMtimeMs = localMtime,
                                        )
                                    localByPath[op.path] =
                                        OfficialLocalEntry(
                                            path = op.path,
                                            file = dest,
                                            size = remote.bytes.size.toLong(),
                                            lastModifiedEpochMs = localMtime,
                                        )
                                    opState = SyncServerSyncTaskOpState.DONE
                                }
                            }
                        }

                        WebDavPlannedOpKind.DELETE_REMOTE -> {
                            val baseRev = state.files[op.path]?.baseRev ?: 0L
                            val del = SyncServerClient.deleteVaultFileV2(baseUrl, token, op.path, baseRev = baseRev)
                            if (del.ok && del.value != null) {
                                deletedRemote++
                                state = state.withDeleted(op.path, rev = del.value.rev)
                                opState = SyncServerSyncTaskOpState.DONE
                            } else if (del.statusCode == 404) {
                                deletedRemote++
                                state = state.withDeleted(op.path, rev = remoteMeta[op.path]?.rev ?: baseRev)
                                opState = SyncServerSyncTaskOpState.DONE
                            } else if (del.statusCode == 409) {
                                conflicts++
                                logger.conflict(op.path, "delete_rev_conflict")
                                val meta = remoteMeta[op.path]
                                when {
                                    meta != null && meta.deleted -> {
                                        deletedRemote++
                                        state = state.withDeleted(op.path, rev = meta.rev)
                                        opState = SyncServerSyncTaskOpState.DONE
                                    }

                                    meta != null -> {
                                        val retry = SyncServerClient.deleteVaultFileV2(baseUrl, token, op.path, baseRev = meta.rev)
                                        when {
                                            retry.ok && retry.value != null -> {
                                                deletedRemote++
                                                state = state.withDeleted(op.path, rev = retry.value.rev)
                                                opState = SyncServerSyncTaskOpState.DONE
                                            }

                                            retry.statusCode == 404 -> {
                                                deletedRemote++
                                                state = state.withDeleted(op.path, rev = meta.rev)
                                                opState = SyncServerSyncTaskOpState.DONE
                                            }

                                            else -> {
                                                err = retry.errorMessage ?: "Delete conflict"
                                            }
                                        }
                                    }

                                    else -> {
                                        err = del.errorMessage ?: "Delete conflict"
                                    }
                                }
                            } else {
                                err = del.errorMessage ?: "Delete remote failed"
                            }
                        }

                        WebDavPlannedOpKind.DELETE_LOCAL -> {
                            val local = localByPath[op.path]
                            val deletedOk = local?.file?.delete() ?: true
                            if (!deletedOk) {
                                err = "Failed to delete local file"
                            } else {
                                localByPath.remove(op.path)

                                val remoteRev = remoteMeta[op.path]?.rev ?: state.files[op.path]?.baseRev ?: 0L
                                deletedLocal++
                                state = state.withDeleted(op.path, rev = remoteRev)
                                opState = SyncServerSyncTaskOpState.DONE
                            }
                        }

                        WebDavPlannedOpKind.CONFLICT -> {
                            conflicts++
                            val local = localByPath[op.path]
                            if (local == null) {
                                err = "Missing local file"
                            } else {
                                val localBytes = readBytesForUpload(local.file.uri, maxAttempts = 4)
                                if (localBytes == null) {
                                    err = "Failed to read local file"
                                } else if (localBytes.isEmpty()) {
                                    // If local is empty, we cannot resolve via "local wins" upload; defer to next sync.
                                    opState = SyncServerSyncTaskOpState.SKIPPED
                                    err = "Empty local file; skipped conflict resolution"
                                } else {
                                    val remoteRes = SyncServerClient.downloadVaultFileV2(baseUrl, token, op.path)
                                    val remote = remoteRes.value
                                    val remoteRev =
                                        if (remote != null) {
                                            remote.rev
                                        } else {
                                            remoteMeta[op.path]?.rev ?: state.files[op.path]?.baseRev ?: 0L
                                        }

                                    if (remote != null) {
                                        ensureConflictArtifact(op.path, "remote-r${remote.rev}", remote.bytes)
                                        logger.conflict(op.path, "remote_changed")
                                    } else {
                                        logger.conflict(op.path, "remote_deleted")
                                    }

                                    val put =
                                        SyncServerClient.uploadVaultFileV2(
                                            baseUrl = baseUrl,
                                            token = token,
                                            path = op.path,
                                            mtimeMs = repository.getDocumentLastModified(local.file.uri).takeIf { it > 0L } ?: System.currentTimeMillis(),
                                            bytes = localBytes,
                                            baseRev = remoteRev,
                                        )
                                    if (put.ok && put.value != null) {
                                        uploaded++
                                        val localMtime = repository.getDocumentLastModified(local.file.uri).takeIf { it > 0L } ?: local.lastModifiedEpochMs
                                        state =
                                            state.withUploaded(
                                                op.path,
                                                rev = put.value.rev,
                                                sha256 = put.value.sha256,
                                                size = localBytes.size.toLong(),
                                                localMtimeMs = localMtime,
                                            )
                                        opState = SyncServerSyncTaskOpState.DONE
                                    } else {
                                        err = put.errorMessage ?: "Conflict resolution failed"
                                    }
                                }
                            }
                        }
                    }
                } catch (e: Throwable) {
                    err = e.message ?: e.javaClass.simpleName
                }

                if (opState == SyncServerSyncTaskOpState.FAILED) {
                    failed++
                }
                observer?.invoke(SyncServerSyncObservedOpResult(op = op, state = opState, error = err))
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
                val endedAtMs = System.currentTimeMillis()
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
                runCatching {
                    writeLastSummary(
                        root = root,
                        startedAtMs = startedAtMs,
                        endedAtMs = endedAtMs,
                        baseUrl = baseUrl,
                        includeIndexSqlite = includeIndexSqlite,
                        summary = summary,
                        error = null,
                    )
                }
                summary
            },
            onFailure = { e ->
                val endedAtMs = System.currentTimeMillis()
                val error = e.message ?: e.javaClass.simpleName
                logger.logEvent("end", mapOf("ok" to "false", "error" to error))
                runCatching {
                    writeLastSummary(
                        root = root,
                        startedAtMs = startedAtMs,
                        endedAtMs = endedAtMs,
                        baseUrl = baseUrl,
                        includeIndexSqlite = includeIndexSqlite,
                        summary = null,
                        error = error,
                    )
                }
                throw e
            },
        )
    }

    suspend fun syncVault(
        rootUri: Uri,
        baseUrl: String,
        token: String,
        includeIndexSqlite: Boolean,
    ): OfficialVaultSyncSummary = withContext(Dispatchers.IO) {
        val startedAtMs = System.currentTimeMillis()
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
                val normalized = path.trim().trimStart('/').replace('\\', '/')
                if (normalized.isBlank()) return true
                if (!shouldSyncPath(normalized)) return true

                val localFilesNow = listLocalFiles(root, includeIndexSqlite = includeIndexSqlite).filter { shouldSyncPath(it.path) }
                val localByPathNow = localFilesNow.associateBy { it.path }
                val local = localByPathNow[normalized]
                val st = state.files[normalized]

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
                            state = state.withDeleted(normalized, remoteRev)
                            return true
                        }
                        return false
                    }

                    val download = SyncServerClient.downloadVaultFileV2(baseUrl, token, normalized)
                    val bytes = download.value?.bytes ?: return false
                    val dest = ensureLocalFile(root, normalized) ?: return false
                    repository.writeBytes(dest.uri, bytes)
                    val localMtime = repository.getDocumentLastModified(dest.uri)
                    val sha = download.value.sha256
                    state =
                        state.withUploaded(
                            normalized,
                            rev = remoteRev,
                            sha256 = sha,
                            size = bytes.size.toLong(),
                            localMtimeMs = localMtime,
                        )
                    return true
                }

                // Conflict: try best-effort text 3-way merge; otherwise keep both by writing remote under .zhixu/conflicts/.
                if (remoteDeleted) {
                    val localBytes = local?.let { repository.readBytes(it.file.uri) } ?: ByteArray(0)
                    if (localBytes.isNotEmpty()) ensureConflictArtifact(normalized, "local", localBytes)
                    state = state.withDeleted(normalized, remoteRev)
                    return true
                }

                val remote = SyncServerClient.downloadVaultFileV2(baseUrl, token, normalized).value ?: return false
                val localBytes = local?.let { repository.readBytes(it.file.uri) } ?: ByteArray(0)
                if (local == null) {
                    val dest = ensureLocalFile(root, normalized) ?: return false
                    repository.writeBytes(dest.uri, remote.bytes)
                    val localMtime = repository.getDocumentLastModified(dest.uri)
                    state =
                        state.withUploaded(
                            normalized,
                            rev = remoteRev,
                            sha256 = remote.sha256,
                            size = remote.bytes.size.toLong(),
                            localMtimeMs = localMtime,
                        )
                    return true
                }

                ensureConflictArtifact(normalized, "remote-r${remote.rev}", remote.bytes)
                logger.conflict(normalized, "remote_changed")

                // Without server-side history, resolve by keeping both:
                // - write remote under .zhixu/conflicts/
                // - upload local to remote (local wins)
                state =
                    state.withUploaded(
                        normalized,
                        rev = remote.rev,
                        sha256 = "",
                        size = localBytes.size.toLong(),
                        localMtimeMs = local.lastModifiedEpochMs,
                    )
                if (localBytes.isNotEmpty()) {
                    val put =
                        SyncServerClient.uploadVaultFileV2(
                            baseUrl = baseUrl,
                            token = token,
                            path = normalized,
                            mtimeMs = local.lastModifiedEpochMs.takeIf { it > 0L } ?: System.currentTimeMillis(),
                            bytes = localBytes,
                            baseRev = remote.rev,
                        )
                    if (put.ok && put.value != null) {
                        val localMtime = repository.getDocumentLastModified(local.file.uri).takeIf { it > 0L } ?: local.lastModifiedEpochMs
                        state =
                            state.withUploaded(
                                normalized,
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
                        val p = c.path.trim().trimStart('/').replace('\\', '/')
                        val st = state.files[p]
                        if (st != null && st.baseRev >= c.rev && st.deleted == c.deleted) {
                            continue
                        }
                        val ok = applyRemote(p, c.rev, c.deleted)
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
                        val p = c.path.trim().trimStart('/').replace('\\', '/')
                        val st = state.files[p]
                        if (st != null && st.baseRev >= c.rev && st.deleted == c.deleted) {
                            since = maxOf(since, c.changeId)
                            continue
                        }
                        val ok = applyRemote(p, c.rev, c.deleted)
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
            val localFiles = listLocalFiles(root, includeIndexSqlite = includeIndexSqlite).filter { shouldSyncPath(it.path) }
            val localByPath = localFiles.associateBy { it.path }
            for (local in localFiles) {
                val st = state.files[local.path]
                if (st != null && !st.deleted && isProbablySame(local, st)) continue

                val bytes = readBytesForUpload(local.file.uri, maxAttempts = 4)
                if (bytes == null) {
                    failed++
                    continue
                }
                // Server rejects empty body; also avoids uploading during truncate+rewrite windows.
                if (bytes.isEmpty()) continue
                val sha = sha256Hex(bytes)
                if (st != null && !st.deleted && st.sha256.isNotBlank() && st.sha256 == sha) continue

                val baseRev = st?.baseRev ?: 0L
                val mtimeMs =
                    repository.getDocumentLastModified(local.file.uri).takeIf { it > 0L }
                        ?: local.lastModifiedEpochMs.takeIf { it > 0L }
                        ?: System.currentTimeMillis()
                val put = SyncServerClient.uploadVaultFileV2(baseUrl, token, local.path, mtimeMs, bytes, baseRev = baseRev)
                if (put.ok && put.value != null) {
                    uploaded++
                    state = state.withUploaded(local.path, rev = put.value.rev, sha256 = put.value.sha256, size = bytes.size.toLong(), localMtimeMs = mtimeMs)
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
            data class DeleteRemoteMeta(val rev: Long, val deleted: Boolean)
            var deleteRemoteMetaCache: Map<String, DeleteRemoteMeta>? = null

            suspend fun getDeleteRemoteMeta(path: String): DeleteRemoteMeta? {
                val cached = deleteRemoteMetaCache
                if (cached != null) return cached[path]
                val res = SyncServerClient.vaultChangesV2(baseUrl, token, since = 0L, limit = 5000)
                val v = res.value
                val map = HashMap<String, DeleteRemoteMeta>(v?.changes?.size ?: 0)
                if (v != null) {
                    for (c in v.changes) {
                        val p = c.path.trim().trimStart('/').replace('\\', '/')
                        if (p.isBlank()) continue
                        map[p] = DeleteRemoteMeta(rev = c.rev, deleted = c.deleted)
                    }
                }
                deleteRemoteMetaCache = map
                return map[path]
            }

            for ((path, st) in state.files) {
                if (st.deleted) continue
                if (!shouldSyncPath(path)) continue
                if (localByPath.containsKey(path)) continue
                val del = SyncServerClient.deleteVaultFileV2(baseUrl, token, path, baseRev = st.baseRev)
                if (del.ok && del.value != null) {
                    deletedRemote++
                    state = state.withDeleted(path, del.value.rev)
                } else if (del.statusCode == 404) {
                    deletedRemote++
                    state = state.withDeleted(path, st.baseRev)
                } else if (del.statusCode == 409) {
                    conflicts++
                    logger.conflict(path, "delete_rev_conflict")
                    // Best-effort resolve: refresh remote rev and retry delete.
                    val meta = getDeleteRemoteMeta(path)
                    if (meta == null) continue
                    if (meta.deleted) {
                        deletedRemote++
                        state = state.withDeleted(path, meta.rev)
                        continue
                    }
                    val retry = SyncServerClient.deleteVaultFileV2(baseUrl, token, path, baseRev = meta.rev)
                    if (retry.ok && retry.value != null) {
                        deletedRemote++
                        state = state.withDeleted(path, retry.value.rev)
                    } else if (retry.statusCode == 404) {
                        deletedRemote++
                        state = state.withDeleted(path, meta.rev)
                    }
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
                runCatching {
                    writeLastSummary(
                        root = root,
                        startedAtMs = startedAtMs,
                        endedAtMs = System.currentTimeMillis(),
                        baseUrl = baseUrl,
                        includeIndexSqlite = includeIndexSqlite,
                        summary = summary,
                        error = null,
                    )
                }
                summary
            },
            onFailure = { e ->
                val error = e.message ?: e.javaClass.simpleName
                logger.logEvent("end", mapOf("ok" to "false", "error" to error))
                runCatching {
                    writeLastSummary(
                        root = root,
                        startedAtMs = startedAtMs,
                        endedAtMs = System.currentTimeMillis(),
                        baseUrl = baseUrl,
                        includeIndexSqlite = includeIndexSqlite,
                        summary = null,
                        error = error,
                    )
                }
                throw e
            },
        )
    }

    private suspend fun writeLastSummary(
        root: DocumentFile,
        startedAtMs: Long,
        endedAtMs: Long,
        baseUrl: String,
        includeIndexSqlite: Boolean,
        summary: OfficialVaultSyncSummary?,
        error: String?,
    ) {
        val file = ensureLocalFile(root, ".zhixu/sync/server_last_summary.json") ?: return
        val obj =
            JSONObject()
                .put("version", 1)
                .put("engine", "sync_server")
                .put("ok", error.isNullOrBlank())
                .put("startedAt", startedAtMs.coerceAtLeast(0L))
                .put("endedAt", endedAtMs.coerceAtLeast(0L))
                .put("durationMs", (endedAtMs - startedAtMs).coerceAtLeast(0L))
                .put("baseUrl", baseUrl.trim())
                .put("includeIndexSqlite", includeIndexSqlite)
        if (summary != null) {
            obj.put("uploaded", summary.uploaded)
            obj.put("downloaded", summary.downloaded)
            obj.put("deletedRemote", summary.deletedRemote)
            obj.put("deletedLocal", summary.deletedLocal)
            obj.put("conflicts", summary.conflicts)
            obj.put("failed", summary.failed)
        }
        if (!error.isNullOrBlank()) {
            obj.put("error", error.take(500))
        }
        runCatching { repository.writeText(file.uri, obj.toString()) }
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
        val name = p.substringAfterLast('/', missingDelimiterValue = p)
        if (name.startsWith("conflict ", ignoreCase = true)) return false
        if (p.equals(".zhixu/sync/log.jsonl", ignoreCase = true)) return false
        if (p.equals(".zhixu/sync/conflicts.jsonl", ignoreCase = true)) return false
        if (p.equals(".zhixu/sync/official_state.json", ignoreCase = true)) return false
        if (p.equals(".zhixu/sync/official_state_v2.json", ignoreCase = true)) return false
        if (p.startsWith(".zhixu/sync/", ignoreCase = true)) return false
        if (p.startsWith(".zhixu/conflicts/", ignoreCase = true)) return false
        if (p.startsWith(".zhixu/history/", ignoreCase = true)) return false
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

    private suspend fun readBytesForUpload(
        uri: Uri,
        maxAttempts: Int = 3,
    ): ByteArray? {
        val attempts = maxAttempts.coerceAtLeast(1)
        var backoffMs = 60L
        repeat(attempts) { attempt ->
            val bytes = repository.readBytes(uri) ?: return null
            if (bytes.isNotEmpty() || attempt == attempts - 1) return bytes
            // Common during truncate+rewrite saves: file is momentarily 0 bytes.
            delay(backoffMs)
            backoffMs = (backoffMs * 2).coerceAtMost(250L)
        }
        return null
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
