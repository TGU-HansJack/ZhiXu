package app.zhixu.sync

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import app.zhixu.data.VaultFileChangeSource
import app.zhixu.data.VaultRepository
import app.zhixu.data.WebDavClient
import app.zhixu.data.WebDavConfig
import app.zhixu.data.WebDavConflictStrategy
import app.zhixu.data.vaultRootToDocumentFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.json.JSONArray
import org.json.JSONObject
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

data class WebDavSyncSummary(
    val uploaded: Int,
    val downloaded: Int,
    val deletedRemote: Int,
    val deletedLocal: Int,
    val conflicts: Int,
    val failed: Int,
)

enum class WebDavPlannedOpKind {
    DOWNLOAD,
    UPLOAD,
    DELETE_REMOTE,
    DELETE_LOCAL,
    CONFLICT,
}

data class WebDavPlannedOp(
    val kind: WebDavPlannedOpKind,
    val path: String,
    val reason: String,
    val strategy: String? = null,
)

data class WebDavSyncPlan(
    val generatedAtMs: Long,
    val remoteRootUrl: String,
    val dryRun: Boolean,
    val operations: List<WebDavPlannedOp>,
    val summary: WebDavSyncSummary,
)

data class WebDavSyncObservedOpResult(
    val op: WebDavPlannedOp,
    val ok: Boolean,
    val error: String?,
)

private const val WEB_DAV_UNRESOLVED_CONFLICTS_PATH = ".zhixu/sync/webdav_unresolved_conflicts.json"

internal data class RemoteEntry(
    val path: String,
    val isDir: Boolean,
    val size: Long?,
    val lastModifiedEpochMs: Long?,
    val etag: String?,
)

internal data class LocalEntry(
    val path: String,
    val file: DocumentFile,
    val size: Long,
    val lastModifiedEpochMs: Long,
)

class WebDavSyncEngine(
    private val context: Context,
    private val repository: VaultRepository,
) {
    suspend fun syncVault(rootUri: Uri, config: WebDavConfig): WebDavSyncSummary =
        syncVaultInternal(
            rootUri = rootUri,
            config = config,
            dryRun = false,
            onlyPaths = null,
            expectedOperations = null,
            conflictStrategyOverrides = emptyMap(),
            observer = null,
        ).summary

    suspend fun syncVaultPaths(rootUri: Uri, config: WebDavConfig, onlyPaths: Set<String>): WebDavSyncSummary =
        syncVaultInternal(
            rootUri = rootUri,
            config = config,
            dryRun = false,
            onlyPaths = onlyPaths,
            expectedOperations = null,
            conflictStrategyOverrides = emptyMap(),
            observer = null,
        ).summary

    suspend fun planVault(rootUri: Uri, config: WebDavConfig): WebDavSyncPlan =
        syncVaultInternal(
            rootUri = rootUri,
            config = config,
            dryRun = true,
            onlyPaths = null,
            expectedOperations = null,
            conflictStrategyOverrides = emptyMap(),
            observer = null,
        )

    suspend fun planVaultPaths(rootUri: Uri, config: WebDavConfig, onlyPaths: Set<String>): WebDavSyncPlan =
        syncVaultInternal(
            rootUri = rootUri,
            config = config,
            dryRun = true,
            onlyPaths = onlyPaths,
            expectedOperations = null,
            conflictStrategyOverrides = emptyMap(),
            observer = null,
        )

    suspend fun syncVaultWithExpectedPlan(
        rootUri: Uri,
        config: WebDavConfig,
        expectedOperations: List<WebDavPlannedOp>,
        conflictStrategyOverrides: Map<String, WebDavConflictStrategy> = emptyMap(),
        observer: ((WebDavSyncObservedOpResult) -> Unit)? = null,
    ): WebDavSyncSummary {
        val normalizedExpected =
            expectedOperations
                .map { op ->
                    op.copy(
                        path = op.path.trim().trimStart('/').replace('\\', '/'),
                    )
                }
                .filter { it.path.isNotBlank() }
        // Task semantics: if the task declares no operations, the engine must not infer any.
        // This prevents "empty expected plan" from triggering a full scan that can discover conflicts.
        if (normalizedExpected.isEmpty()) {
            val startedAtMs = System.currentTimeMillis()
            val endedAtMs = startedAtMs
            val summary =
                WebDavSyncSummary(
                    uploaded = 0,
                    downloaded = 0,
                    deletedRemote = 0,
                    deletedLocal = 0,
                    conflicts = 0,
                    failed = 0,
                )
            // Best-effort: update `webdav_last_summary.json` so UIs won't keep showing a stale previous run.
            withContext(Dispatchers.IO) {
                val root = vaultRootToDocumentFile(context, rootUri) ?: return@withContext
                val remoteRootUrl =
                    runCatching {
                        WebDavClient
                            .normalizeJoin(config.baseUrl.trim(), config.remoteRoot.trim().ifBlank { "/" })
                            .trimEnd('/') + "/"
                    }.getOrDefault("")
                // Ignore failures: this is only used to avoid a stale UI state when we did no work.
                try {
                    writeLastSummary(root, startedAtMs, endedAtMs, config, summary, error = null)
                } catch (_: Throwable) {
                }
                try {
                    writeLastPlan(root, startedAtMs, endedAtMs, remoteRootUrl, config, emptyList(), summary, error = null)
                } catch (_: Throwable) {
                }
            }
            return summary
        }
        val onlyPaths = normalizedExpected.map { it.path }.toSet()
        return syncVaultInternal(
            rootUri = rootUri,
            config = config,
            dryRun = false,
            onlyPaths = onlyPaths,
            expectedOperations = normalizedExpected,
            conflictStrategyOverrides = conflictStrategyOverrides,
            observer = observer,
        ).summary
    }

    suspend fun listUnresolvedConflicts(rootUri: Uri): List<WebDavUnresolvedConflict> =
        withContext(Dispatchers.IO) {
            readUnresolvedConflicts(rootUri).values.sortedByDescending { it.createdAtMs }
        }

    suspend fun dismissUnresolvedConflict(rootUri: Uri, path: String): Boolean =
        withContext(Dispatchers.IO) {
            val normalized = path.trim().trimStart('/').replace('\\', '/')
            if (normalized.isBlank()) return@withContext false
            val root = vaultRootToDocumentFile(context, rootUri) ?: return@withContext false
            val conflicts = readUnresolvedConflicts(rootUri)
            val removed = conflicts.remove(normalized) != null
            if (removed) {
                writeUnresolvedConflicts(root, conflicts)
            }
            removed
        }

    private suspend fun syncVaultInternal(
        rootUri: Uri,
        config: WebDavConfig,
        dryRun: Boolean,
        onlyPaths: Set<String>?,
        expectedOperations: List<WebDavPlannedOp>?,
        conflictStrategyOverrides: Map<String, WebDavConflictStrategy>,
        observer: ((WebDavSyncObservedOpResult) -> Unit)?,
    ): WebDavSyncPlan =
        withContext(Dispatchers.IO) {
        require(config.enabled) { "WebDAV is disabled" }
        val startedAtMs = System.currentTimeMillis()
        val onlyPathSet =
            onlyPaths
                ?.map { it.trim().trimStart('/').replace('\\', '/') }
                ?.filter { it.isNotBlank() }
                ?.toSet()
                ?.takeIf { it.isNotEmpty() }
        if (!dryRun) {
            repository.ensureVaultStructure(rootUri)
            if (config.includeIndexSqlite) {
                repository.exportIndexSqlite(rootUri)
            }
        }

        val root = vaultRootToDocumentFile(context, rootUri) ?: error("Invalid vault root Uri")
        val planOps = ArrayList<WebDavPlannedOp>()
        val logFile = if (dryRun) null else ensureLocalFile(root, ".zhixu/sync/log.jsonl")
        val conflictsFile = if (dryRun) null else ensureLocalFile(root, ".zhixu/sync/conflicts.jsonl")
        val stateFile = if (dryRun) null else ensureLocalFile(root, ".zhixu/sync/webdav_state.json")
        val stateUri = stateFile?.uri ?: repository.resolveVaultFileUri(rootUri, ".zhixu/sync/webdav_state.json")
        val logger = SyncLogger(logFile?.uri, conflictsFile?.uri)
        if (!dryRun) {
            logger.logEvent("start", mapOf("engine" to "webdav", "includeIndexSqlite" to config.includeIndexSqlite.toString()))
        }

        var remoteRootUrlForPlan = ""
        val summary =
            runCatching {
                fun inScope(path: String): Boolean {
                    val normalized = path.trim().trimStart('/').replace('\\', '/')
                    val only = onlyPathSet ?: return true
                    return normalized in only
                }

                fun normalizeOpPath(raw: String): String = raw.trim().trimStart('/').replace('\\', '/')

                val normalizedExpected =
                    expectedOperations?.map { exp ->
                        exp.copy(path = normalizeOpPath(exp.path))
                    }

                val normalizedConflictOverrides =
                    conflictStrategyOverrides.entries.associate { (k, v) ->
                        normalizeOpPath(k) to v
                    }

                fun conflictStrategyFor(path: String): WebDavConflictStrategy {
                    val key = normalizeOpPath(path)
                    return normalizedConflictOverrides[key] ?: config.conflictStrategy
                }

                var expectedIndex = 0
                // Some passes (e.g., local-only upload) can mutate local/remote maps and make a path
                // appear again in later passes. Treat each path as "handled" once we record an op,
                // to avoid emitting extra ops (e.g., an extra conflict after a successful upload).
                val handledPaths = HashSet<String>()

                fun record(kind: WebDavPlannedOpKind, path: String, reason: String, strategy: WebDavConflictStrategy? = null): WebDavPlannedOp {
                    val op = WebDavPlannedOp(kind = kind, path = normalizeOpPath(path), reason = reason, strategy = strategy?.name)

                    if (!dryRun && normalizedExpected != null) {
                        if (expectedIndex >= normalizedExpected.size) {
                            throw IllegalStateException("Unexpected operation: ${op.kind} ${op.path} (${op.reason})")
                        }
                        val exp = normalizedExpected[expectedIndex]
                        val match = (op.kind == exp.kind && op.path == normalizeOpPath(exp.path) && op.reason == exp.reason)
                        if (!match) {
                            throw IllegalStateException(
                                "Plan mismatch at #$expectedIndex: expected ${exp.kind} ${normalizeOpPath(exp.path)} (${exp.reason}), got ${op.kind} ${op.path} (${op.reason})",
                            )
                        }
                        expectedIndex += 1
                    }

                    handledPaths += op.path
                    planOps += op
                    return op
                }

                fun recordConflict(path: String, reason: String): WebDavPlannedOp {
                    return record(WebDavPlannedOpKind.CONFLICT, path, reason, strategy = conflictStrategyFor(path))
                }

            fun normalizeEtagForCompare(raw: String?): String? {
                val s = raw.orEmpty().trim()
                if (s.isBlank()) return null
                val withoutWeak = s.removePrefix("W/").trim()
                return withoutWeak.trim().trim('"').ifBlank { null }
            }

            fun localChangedSinceBase(local: LocalEntry, base: WebDavStateFileEntry?): Boolean {
                if (base == null) return true
                if (base.localSize != local.size) return true
                val baseMtime = base.localMtimeMs
                if (baseMtime <= 0L || local.lastModifiedEpochMs <= 0L) return false
                return kotlin.math.abs(baseMtime - local.lastModifiedEpochMs) >= 2_000
            }

            fun remoteChangedSinceBase(remote: RemoteEntry, base: WebDavStateFileEntry?): Boolean {
                if (base == null) return true
                val baseEtag = normalizeEtagForCompare(base.remoteEtag)
                val nowEtag = normalizeEtagForCompare(remote.etag)
                if (baseEtag != null || nowEtag != null) {
                    return baseEtag != nowEtag
                }
                val baseSize = base.remoteSize
                val baseMtime = base.remoteMtimeMs
                val nowSize = remote.size
                val nowMtime = remote.lastModifiedEpochMs
                if (baseSize != null && nowSize == null) return true
                if (baseMtime != null && nowMtime == null) return true
                if (baseSize != null && nowSize != null && baseSize != nowSize) return true
                if (baseMtime != null && nowMtime != null && kotlin.math.abs(baseMtime - nowMtime) >= 2_000) return true
                return (baseSize != null || baseMtime != null).not()
            }

            val prevState = readStateFiles(stateUri)
            val localFiles = listLocalFiles(root, includeIndexSqlite = config.includeIndexSqlite).filter { shouldSyncPath(it.path) }
            val localByPath = localFiles.associateBy { it.path }.toMutableMap()
            val remoteRootUrl =
                WebDavClient.normalizeJoin(config.baseUrl.trim(), config.remoteRoot.trim().ifBlank { "/" }).trimEnd('/') + "/"
            remoteRootUrlForPlan = remoteRootUrl
            if (!dryRun) logger.logEvent("remote_root", mapOf("url" to remoteRootUrl))

            if (!dryRun) ensureRemoteRoot(remoteRootUrl, config)
            val ensuredRemoteDirs = HashSet<String>()
            val remoteFiles = listRemoteFiles(remoteRootUrl, config)

            var uploaded = 0
            var downloaded = 0
            var deletedRemote = 0
            var deletedLocal = 0
            var conflicts = 0
            var failed = 0

            // Track successful local filesystem mutations so we can hand them off to the index updater.
            val localUpsertPaths = LinkedHashSet<String>()
            val localDeletePaths = LinkedHashSet<String>()

            val remoteAllByPath = remoteFiles.filter { !it.isDir }.associateBy { it.path }
            val useUnresolvedConflicts = normalizedExpected == null
            val unresolvedConflicts = if (useUnresolvedConflicts) readUnresolvedConflicts(rootUri) else mutableMapOf()
            var unresolvedDirty = false
            val blockUnresolved = useUnresolvedConflicts && config.conflictStrategy == WebDavConflictStrategy.ASK_EACH_TIME

            fun markUnresolved(path: String, reason: String, localArtifactPath: String?, remoteArtifactPath: String?) {
                val normalized = path.trim().trimStart('/').replace('\\', '/')
                if (normalized.isBlank()) return
                val prev = unresolvedConflicts[normalized]
                val createdAtMs = (prev?.createdAtMs ?: 0L).takeIf { it > 0L } ?: System.currentTimeMillis()
                val merged =
                    WebDavUnresolvedConflict(
                        path = normalized,
                        createdAtMs = createdAtMs,
                        reason = prev?.reason?.ifBlank { null } ?: reason,
                        localArtifactPath = localArtifactPath?.ifBlank { null } ?: prev?.localArtifactPath,
                        remoteArtifactPath = remoteArtifactPath?.ifBlank { null } ?: prev?.remoteArtifactPath,
                    )
                if (merged != prev) {
                    unresolvedConflicts[normalized] = merged
                    unresolvedDirty = true
                }
            }

            fun clearUnresolved(path: String) {
                val normalized = path.trim().trimStart('/').replace('\\', '/')
                if (normalized.isBlank()) return
                if (unresolvedConflicts.remove(normalized) != null) {
                    unresolvedDirty = true
                }
            }

            fun shouldSkipBecauseUnresolved(path: String): Boolean {
                if (!blockUnresolved) return false
                val normalized = path.trim().trimStart('/').replace('\\', '/')
                return unresolvedConflicts.containsKey(normalized)
            }

            fun maybeClearUnresolvedAfterSuccess(path: String) {
                if (config.conflictStrategy == WebDavConflictStrategy.ASK_EACH_TIME) return
                clearUnresolved(path)
            }
            val tombstonesPath = ".zhixu/syncmeta/webdav_tombstones.json"
            val tombstoneLoad =
                loadMergedTombstones(
                    rootUri = rootUri,
                    root = root,
                    remoteRootUrl = remoteRootUrl,
                    config = config,
                    remoteExisting = remoteAllByPath[tombstonesPath],
                    writeLocal = !dryRun,
                )
            val tombstones = tombstoneLoad.entries.toMutableMap()
            var tombstonesDirty = tombstoneLoad.dirty
            val tombstoneTtlMs = 30L * 24L * 60L * 60L * 1_000L
            val nowForTtl = System.currentTimeMillis()
            if (tombstones.isNotEmpty()) {
                val before = tombstones.size
                val cutoff = nowForTtl - tombstoneTtlMs
                tombstones.entries.removeIf { (_, v) -> v.deletedAtMs > 0L && v.deletedAtMs < cutoff }
                if (tombstones.size != before) tombstonesDirty = true
            }

            val remoteByPath =
                remoteFiles
                    .filter { !it.isDir }
                    .filter { shouldSyncPath(it.path) }
                    .associateBy { it.path }
                    .toMutableMap()

            suspend fun refreshRemoteFile(path: String): RemoteEntry? {
                val url = buildRemoteUrl(remoteRootUrl, path)
                val (code, xml) = WebDavClient.propfind(config, url, depth = "0")
                if (code != 207 && code !in 200..299) return null
                val rootPath = Uri.parse(remoteRootUrl).path ?: "/"
                val entries =
                    parsePropfind(xml).map { e ->
                        val normalized = normalizeHrefToPath(e.path, rootPath)
                        e.copy(path = normalized)
                    }
                return entries.firstOrNull { it.path == path && !it.isDir }
            }

            suspend fun deleteLocalPath(path: String): Boolean {
                val uri = repository.resolveVaultFileUri(rootUri, path) ?: return true
                return runCatching { repository.deleteEntry(uri) }.getOrDefault(false)
            }

            suspend fun performDownload(path: String): Pair<Boolean, String?> {
                if (dryRun) return true to null
                return try {
                    val ok = downloadFile(root, remoteRootUrl, path, config)
                    if (ok) localUpsertPaths.add(normalizeOpPath(path))
                    ok to null
                } catch (e: Throwable) {
                    logger.fileFailed("download", path, e)
                    false to (e.message ?: e.javaClass.simpleName)
                }
            }

            suspend fun performUpload(local: LocalEntry, ifMatch: String?, ifNoneMatchStar: Boolean): Pair<Boolean, String?> {
                if (dryRun) return true to null
                return try {
                    val ok = uploadFile(remoteRootUrl, local, config, ensuredRemoteDirs, ifMatch = ifMatch, ifNoneMatchStar = ifNoneMatchStar)
                    ok to null
                } catch (e: Throwable) {
                    logger.fileFailed("upload", local.path, e)
                    false to (e.message ?: e.javaClass.simpleName)
                }
            }

            suspend fun performDeleteRemote(path: String, remote: RemoteEntry?): Pair<Boolean, String?> {
                if (dryRun) return true to null
                return try {
                    val url = buildRemoteUrl(remoteRootUrl, path)
                    val code = WebDavClient.delete(config, url, ifMatch = remote?.etag)
                    (code in 200..299 || code == 404) to null
                } catch (e: Throwable) {
                    logger.fileFailed("delete_remote", path, e)
                    false to (e.message ?: e.javaClass.simpleName)
                }
            }

            suspend fun performDeleteLocal(path: String): Pair<Boolean, String?> {
                if (dryRun) return true to null
                return try {
                    val ok = deleteLocalPath(path)
                    if (ok) localDeletePaths.add(normalizeOpPath(path))
                    ok to null
                } catch (e: Throwable) {
                    logger.fileFailed("delete_local", path, e)
                    false to (e.message ?: e.javaClass.simpleName)
                }
            }

            suspend fun planDownload(path: String, reason: String): Boolean {
                val op = record(WebDavPlannedOpKind.DOWNLOAD, path, reason)
                if (dryRun) return true
                val (ok, error) = performDownload(path)
                observer?.invoke(WebDavSyncObservedOpResult(op = op, ok = ok, error = error))
                if (ok) maybeClearUnresolvedAfterSuccess(path)
                return ok
            }

            suspend fun planUpload(local: LocalEntry, ifMatch: String?, ifNoneMatchStar: Boolean, reason: String): Boolean {
                val op = record(WebDavPlannedOpKind.UPLOAD, local.path, reason)
                if (dryRun) return true
                val (ok, error) = performUpload(local, ifMatch = ifMatch, ifNoneMatchStar = ifNoneMatchStar)
                observer?.invoke(WebDavSyncObservedOpResult(op = op, ok = ok, error = error))
                if (ok) maybeClearUnresolvedAfterSuccess(local.path)
                return ok
            }

            suspend fun planDeleteRemote(path: String, remote: RemoteEntry?, reason: String): Boolean {
                val op = record(WebDavPlannedOpKind.DELETE_REMOTE, path, reason)
                if (dryRun) return true
                val (ok, error) = performDeleteRemote(path, remote)
                observer?.invoke(WebDavSyncObservedOpResult(op = op, ok = ok, error = error))
                if (ok) maybeClearUnresolvedAfterSuccess(path)
                return ok
            }

            suspend fun planDeleteLocal(path: String, reason: String): Boolean {
                val op = record(WebDavPlannedOpKind.DELETE_LOCAL, path, reason)
                if (dryRun) return true
                val (ok, error) = performDeleteLocal(path)
                observer?.invoke(WebDavSyncObservedOpResult(op = op, ok = ok, error = error))
                if (ok) maybeClearUnresolvedAfterSuccess(path)
                return ok
            }

            suspend fun planDownloadConflict(path: String, kind: String): ConflictDownloadResult {
                if (dryRun) {
                    val safe = path.trimStart('/').replace('\\', '/')
                    val destPath = ".zhixu/conflicts/$safe/${System.currentTimeMillis()}-$kind"
                    return ConflictDownloadResult(true, destPath)
                }
                return runCatching { downloadConflictFile(root, remoteRootUrl, path, config, kind = kind) }
                    .getOrElse { e ->
                        logger.fileFailed("download_conflict", path, e)
                        ConflictDownloadResult(false, null)
                    }
            }

            suspend fun planSaveLocalConflict(path: String, sourceUri: Uri, kind: String): String? {
                if (dryRun) {
                    val safe = path.trimStart('/').replace('\\', '/')
                    return ".zhixu/conflicts/$safe/${System.currentTimeMillis()}-$kind"
                }
                return runCatching { saveLocalConflictArtifact(root, path, sourceUri, kind = kind) }.getOrNull()
            }

            fun isAfterTombstone(mtimeMs: Long?, tombstone: TombstoneEntry): Boolean {
                val m = mtimeMs ?: return false
                if (m <= 0L) return false
                return m - tombstone.deletedAtMs >= 2_000
            }

            fun clearTombstoneIfRevived(path: String, revived: Boolean) {
                if (!revived) return
                if (tombstones.remove(path) != null) tombstonesDirty = true
            }

            fun touchTombstone(path: String, remote: RemoteEntry?) {
                val now = System.currentTimeMillis()
                val prev = tombstones[path]
                val next =
                    TombstoneEntry(
                        deletedAtMs = maxOf(prev?.deletedAtMs ?: 0L, now),
                        remoteEtag = remote?.etag?.trim()?.ifBlank { null } ?: prev?.remoteEtag,
                        remoteSize = remote?.size ?: prev?.remoteSize,
                        remoteMtimeMs = remote?.lastModifiedEpochMs ?: prev?.remoteMtimeMs,
                    )
                tombstones[path] = next
                tombstonesDirty = true
            }

            // Pass 1: handle remote-only files (download new, or reconcile local deletions safely).
            for ((path, remote) in remoteByPath.toMap()) {
                if (!inScope(path)) continue
                if (shouldSkipBecauseUnresolved(path)) {
                    recordConflict(path, "unresolved_conflict")
                    conflicts += 1
                    continue
                }
                if (localByPath.containsKey(path)) continue
                val base = prevState[path]?.takeIf { it.localSize >= 0L && it.localMtimeMs >= 0L }
                if (base == null) {
                    val tombstone = tombstones[path]
                    if (tombstone != null) {
                        val revived = isAfterTombstone(remote.lastModifiedEpochMs, tombstone)
                        if (!revived) {
                            // Remote still has the file, but we have a tombstone -> propagate deletion to remote (skippable op).
                            val okDelete = planDeleteRemote(path, remote = remote, reason = "tombstoned_remote_delete")
                            if (okDelete) {
                                deletedRemote += 1
                                touchTombstone(path, remote)
                                remoteByPath.remove(path)
                            } else {
                                failed += 1
                            }
                            continue
                        }

                        // Remote changed after local deletion -> conflict (must be explicit in plan/tasks).
                        val conflictOp = recordConflict(path, "tombstoned_remote")
                        conflicts += 1
                        if (dryRun) continue

                        val strategy = conflictStrategyFor(path)
                        val (ok, err) =
                            when (strategy) {
                                WebDavConflictStrategy.REMOTE_WINS -> {
                                    clearTombstoneIfRevived(path, true)
                                    val (downloadOk, downloadErr) = performDownload(path)
                                    if (downloadOk) downloaded++ else failed++
                                    downloadOk to downloadErr
                                }

                                WebDavConflictStrategy.LOCAL_WINS -> {
                                    val (delOk, delErr) = performDeleteRemote(path, remote)
                                    if (delOk) {
                                        deletedRemote += 1
                                        touchTombstone(path, remote)
                                        remoteByPath.remove(path)
                                    } else {
                                        failed += 1
                                    }
                                    delOk to delErr
                                }

                                WebDavConflictStrategy.KEEP_BOTH -> {
                                    val conflictResult = planDownloadConflict(path, kind = "remote-tombstoned")
                                    if (conflictResult.ok) {
                                        logger.conflict(path, conflictResult.conflictPath ?: "", reason = "tombstoned_remote")
                                    }
                                    if (!conflictResult.ok) {
                                        failed += 1
                                        false to "download_conflict_failed"
                                    } else {
                                        val (delOk, delErr) = performDeleteRemote(path, remote)
                                        if (delOk) {
                                            deletedRemote += 1
                                            touchTombstone(path, remote)
                                            remoteByPath.remove(path)
                                        } else {
                                            failed += 1
                                        }
                                        delOk to delErr
                                    }
                                }

                                WebDavConflictStrategy.ASK_EACH_TIME -> {
                                    val conflictResult = planDownloadConflict(path, kind = "remote-tombstoned")
                                    if (conflictResult.ok) {
                                        logger.conflict(path, conflictResult.conflictPath ?: "", reason = "tombstoned_remote")
                                    }
                                    markUnresolved(
                                        path = path,
                                        reason = "tombstoned_remote",
                                        localArtifactPath = null,
                                        remoteArtifactPath = conflictResult.conflictPath,
                                    )
                                    false to "unresolved_conflict"
                                }
                            }
                        observer?.invoke(WebDavSyncObservedOpResult(op = conflictOp, ok = ok, error = err))
                        continue
                    }

                    val ok = planDownload(path, reason = "remote_only")
                    if (ok) downloaded++ else failed++
                    continue
                }

                val remoteChanged = remoteChangedSinceBase(remote, base)
                if (!remoteChanged) {
                    val okDelete = planDeleteRemote(path, remote = remote, reason = "local_deleted")
                    if (okDelete) {
                        deletedRemote += 1
                        touchTombstone(path, remote)
                        remoteByPath.remove(path)
                    } else {
                        failed += 1
                    }
                    continue
                }

                val ok = planDownload(path, reason = "remote_changed_local_missing")
                if (ok) downloaded++ else failed++
            }

            // Pass 2: handle local-only files (upload new, or reconcile remote deletions safely).
            for ((path, local) in localByPath.toMap()) {
                if (!inScope(path)) continue
                if (shouldSkipBecauseUnresolved(path)) {
                    recordConflict(path, "unresolved_conflict")
                    conflicts += 1
                    continue
                }
                if (remoteByPath.containsKey(path)) continue
                val base = prevState[path]?.takeIf { it.localSize >= 0L && it.localMtimeMs >= 0L }

                if (base == null) {
                    val tombstone = tombstones[path]
                    if (tombstone != null) {
                        val revived = isAfterTombstone(local.lastModifiedEpochMs, tombstone)
                        if (!revived) {
                            // Local still has the file, but we have a tombstone -> propagate deletion to local (skippable op).
                            val okDelete = planDeleteLocal(path, reason = "tombstoned_local_delete")
                            if (okDelete) {
                                deletedLocal += 1
                                localByPath.remove(path)
                            } else {
                                failed += 1
                            }
                            continue
                        }

                        // Local changed after remote deletion -> conflict (must be explicit in plan/tasks).
                        val conflictOp = recordConflict(path, "tombstoned_local")
                        conflicts += 1
                        if (dryRun) continue

                        val strategy = conflictStrategyFor(path)
                        val (ok, err) =
                            when (strategy) {
                                WebDavConflictStrategy.LOCAL_WINS -> {
                                    clearTombstoneIfRevived(path, true)
                                    val (uploadOk, uploadErr) = performUpload(local, ifMatch = null, ifNoneMatchStar = true)
                                    if (uploadOk && !dryRun) {
                                        val refreshed = refreshRemoteFile(path)
                                        if (refreshed != null) remoteByPath[path] = refreshed
                                    }
                                    if (uploadOk) uploaded++ else failed++
                                    uploadOk to uploadErr
                                }

                                WebDavConflictStrategy.REMOTE_WINS -> {
                                    val (delOk, delErr) = performDeleteLocal(path)
                                    if (delOk) {
                                        deletedLocal += 1
                                        localByPath.remove(path)
                                    } else {
                                        failed += 1
                                    }
                                    delOk to delErr
                                }

                                WebDavConflictStrategy.KEEP_BOTH -> {
                                    val savedPath = planSaveLocalConflict(path, local.file.uri, kind = "local-tombstoned")
                                    if (!savedPath.isNullOrBlank()) {
                                        logger.conflict(path, savedPath, reason = "tombstoned_local")
                                    }
                                    if (savedPath.isNullOrBlank()) {
                                        failed += 1
                                        false to "save_conflict_failed"
                                    } else {
                                        val (delOk, delErr) = performDeleteLocal(path)
                                        if (delOk) {
                                            deletedLocal += 1
                                            localByPath.remove(path)
                                        } else {
                                            failed += 1
                                        }
                                        delOk to delErr
                                    }
                                }

                                WebDavConflictStrategy.ASK_EACH_TIME -> {
                                    val savedPath = planSaveLocalConflict(path, local.file.uri, kind = "local-tombstoned")
                                    logger.conflict(path, savedPath.orEmpty(), reason = "tombstoned_local")
                                    markUnresolved(
                                        path = path,
                                        reason = "tombstoned_local",
                                        localArtifactPath = savedPath,
                                        remoteArtifactPath = null,
                                    )
                                    false to "unresolved_conflict"
                                }
                            }
                        observer?.invoke(WebDavSyncObservedOpResult(op = conflictOp, ok = ok, error = err))
                        continue
                    }

                    val ok = planUpload(local, ifMatch = null, ifNoneMatchStar = true, reason = "local_only_new")
                    if (ok && !dryRun) {
                        val refreshed = refreshRemoteFile(path)
                        if (refreshed != null) remoteByPath[path] = refreshed
                    }
                    if (ok) uploaded++ else failed++
                    continue
                }

                val localChanged = localChangedSinceBase(local, base)
                if (!localChanged) {
                    // Remote was deleted and local unchanged -> propagate deletion to local.
                    val okDelete = planDeleteLocal(path, reason = "remote_deleted")
                    if (okDelete) {
                        deletedLocal += 1
                        touchTombstone(path, remote = null)
                        localByPath.remove(path)
                    } else {
                        failed++
                    }
                    continue
                }

                // Local changed but remote missing -> treat as conflict; recreate remote from local.
                val conflictOp = recordConflict(path, "local_vs_delete")
                conflicts += 1
                if (dryRun) continue

                val strategy = conflictStrategyFor(path)
                val (ok, err) =
                    when (strategy) {
                        WebDavConflictStrategy.LOCAL_WINS -> {
                            clearTombstoneIfRevived(path, true)
                            val (uploadOk, uploadErr) = performUpload(local, ifMatch = null, ifNoneMatchStar = false)
                            if (uploadOk && !dryRun) {
                                val refreshed = refreshRemoteFile(path)
                                if (refreshed != null) remoteByPath[path] = refreshed
                            }
                            if (uploadOk) uploaded++ else failed++
                            uploadOk to uploadErr
                        }

                        WebDavConflictStrategy.REMOTE_WINS -> {
                            touchTombstone(path, remote = null)
                            val (delOk, delErr) = performDeleteLocal(path)
                            if (delOk) {
                                deletedLocal += 1
                                localByPath.remove(path)
                            } else {
                                failed += 1
                            }
                            delOk to delErr
                        }

                        WebDavConflictStrategy.KEEP_BOTH -> {
                            val savedPath = planSaveLocalConflict(path, local.file.uri, kind = "local-vs-delete")
                            if (!savedPath.isNullOrBlank()) {
                                logger.conflict(path, savedPath, reason = "local_vs_delete")
                            } else {
                                logger.conflict(path, conflictPath = "", reason = "local_vs_delete")
                            }
                            if (savedPath.isNullOrBlank()) {
                                failed += 1
                                false to "save_conflict_failed"
                            } else {
                                touchTombstone(path, remote = null)
                                val (delOk, delErr) = performDeleteLocal(path)
                                if (delOk) {
                                    deletedLocal += 1
                                    localByPath.remove(path)
                                } else {
                                    failed += 1
                                }
                                delOk to delErr
                            }
                        }

                        WebDavConflictStrategy.ASK_EACH_TIME -> {
                            val savedPath = planSaveLocalConflict(path, local.file.uri, kind = "local-vs-delete")
                            logger.conflict(path, savedPath.orEmpty(), reason = "local_vs_delete")
                            markUnresolved(
                                path = path,
                                reason = "local_vs_delete",
                                localArtifactPath = savedPath,
                                remoteArtifactPath = null,
                            )
                            false to "unresolved_conflict"
                        }
                    }
                observer?.invoke(WebDavSyncObservedOpResult(op = conflictOp, ok = ok, error = err))
                continue
            }

            // Pass 3: files existing on both sides (true two-way: only conflict when both changed).
            for ((path, local) in localByPath.toMap()) {
                if (!inScope(path)) continue
                if (handledPaths.contains(path)) continue
                if (shouldSkipBecauseUnresolved(path)) {
                    recordConflict(path, "unresolved_conflict")
                    conflicts += 1
                    continue
                }
                val remote = remoteByPath[path] ?: continue
                val base = prevState[path]?.takeIf { it.localSize >= 0L && it.localMtimeMs >= 0L }
                if (base == null) {
                    val same =
                        remote.size != null &&
                            remote.lastModifiedEpochMs != null &&
                            remote.size == local.size &&
                            kotlin.math.abs(remote.lastModifiedEpochMs - local.lastModifiedEpochMs) < 2_000
                    if (same) continue

                    val conflictOp = recordConflict(path, "both_changed_no_base")
                    conflicts += 1
                    if (dryRun) continue

                    val strategy = conflictStrategyFor(path)
                    val (ok, err) =
                        when (strategy) {
                            WebDavConflictStrategy.REMOTE_WINS -> {
                                val (downloadOk, downloadErr) = performDownload(path)
                                if (downloadOk) downloaded++ else failed++
                                downloadOk to downloadErr
                            }

                            WebDavConflictStrategy.LOCAL_WINS -> {
                                val (uploadOk, uploadErr) = performUpload(local, ifMatch = remote.etag, ifNoneMatchStar = false)
                                if (uploadOk && !dryRun) {
                                    val refreshed = refreshRemoteFile(path)
                                    if (refreshed != null) remoteByPath[path] = refreshed
                                }
                                if (uploadOk) uploaded++ else failed++
                                uploadOk to uploadErr
                            }

                            WebDavConflictStrategy.KEEP_BOTH -> {
                                val conflictResult = planDownloadConflict(path, kind = "remote")
                                if (conflictResult.ok) {
                                    logger.conflict(path, conflictResult.conflictPath ?: "", reason = "both_changed_no_base")
                                }
                                if (!conflictResult.ok) {
                                    failed += 1
                                    false to "download_conflict_failed"
                                } else {
                                    val (uploadOk, uploadErr) = performUpload(local, ifMatch = remote.etag, ifNoneMatchStar = false)
                                    if (uploadOk && !dryRun) {
                                        val refreshed = refreshRemoteFile(path)
                                        if (refreshed != null) remoteByPath[path] = refreshed
                                    }
                                    if (uploadOk) uploaded++ else failed++
                                    uploadOk to uploadErr
                                }
                            }

                            WebDavConflictStrategy.ASK_EACH_TIME -> {
                                val remoteSnap = planDownloadConflict(path, kind = "remote")
                                val localSnap = planSaveLocalConflict(path, local.file.uri, kind = "local")
                                if (remoteSnap.ok || !localSnap.isNullOrBlank()) {
                                    logger.conflict(path, remoteSnap.conflictPath ?: localSnap.orEmpty(), reason = "both_changed_no_base")
                                    markUnresolved(
                                        path = path,
                                        reason = "both_changed_no_base",
                                        localArtifactPath = localSnap,
                                        remoteArtifactPath = remoteSnap.conflictPath,
                                    )
                                } else {
                                    failed += 1
                                }
                                false to "unresolved_conflict"
                            }
                        }
                    observer?.invoke(WebDavSyncObservedOpResult(op = conflictOp, ok = ok, error = err))
                    continue
                }

                val localChanged = localChangedSinceBase(local, base)
                val remoteChanged = remoteChangedSinceBase(remote, base)
                if (!localChanged && !remoteChanged) continue

                if (!localChanged && remoteChanged) {
                    val ok = planDownload(path, reason = "remote_changed")
                    if (ok) downloaded++ else failed++
                    continue
                }

                if (localChanged && !remoteChanged) {
                    val ok = planUpload(local, ifMatch = remote.etag, ifNoneMatchStar = false, reason = "local_changed")
                    if (ok && !dryRun) {
                        val refreshed = refreshRemoteFile(path)
                        if (refreshed != null) remoteByPath[path] = refreshed
                    }
                    if (ok) uploaded++ else failed++
                    continue
                }

                val conflictOp = recordConflict(path, "both_changed")
                conflicts += 1
                if (dryRun) continue

                val strategy = conflictStrategyFor(path)
                val (ok, err) =
                    when (strategy) {
                        WebDavConflictStrategy.REMOTE_WINS -> {
                            val (downloadOk, downloadErr) = performDownload(path)
                            if (downloadOk) downloaded++ else failed++
                            downloadOk to downloadErr
                        }

                        WebDavConflictStrategy.LOCAL_WINS -> {
                            val (uploadOk, uploadErr) = performUpload(local, ifMatch = remote.etag, ifNoneMatchStar = false)
                            if (uploadOk && !dryRun) {
                                val refreshed = refreshRemoteFile(path)
                                if (refreshed != null) remoteByPath[path] = refreshed
                            }
                            if (uploadOk) uploaded++ else failed++
                            uploadOk to uploadErr
                        }

                        WebDavConflictStrategy.KEEP_BOTH -> {
                            val conflictResult = planDownloadConflict(path, kind = "remote")
                            if (conflictResult.ok) {
                                logger.conflict(path, conflictResult.conflictPath ?: "", reason = "both_changed")
                            }
                            if (!conflictResult.ok) {
                                failed += 1
                                false to "download_conflict_failed"
                            } else {
                                val (uploadOk, uploadErr) = performUpload(local, ifMatch = remote.etag, ifNoneMatchStar = false)
                                if (uploadOk && !dryRun) {
                                    val refreshed = refreshRemoteFile(path)
                                    if (refreshed != null) remoteByPath[path] = refreshed
                                }
                                if (uploadOk) uploaded++ else failed++
                                uploadOk to uploadErr
                            }
                        }

                        WebDavConflictStrategy.ASK_EACH_TIME -> {
                            val remoteSnap = planDownloadConflict(path, kind = "remote")
                            val localSnap = planSaveLocalConflict(path, local.file.uri, kind = "local")
                            if (remoteSnap.ok || !localSnap.isNullOrBlank()) {
                                logger.conflict(path, remoteSnap.conflictPath ?: localSnap.orEmpty(), reason = "both_changed")
                                markUnresolved(
                                    path = path,
                                    reason = "both_changed",
                                    localArtifactPath = localSnap,
                                    remoteArtifactPath = remoteSnap.conflictPath,
                                )
                            } else {
                                failed += 1
                            }
                            false to "unresolved_conflict"
                        }
                    }
                observer?.invoke(WebDavSyncObservedOpResult(op = conflictOp, ok = ok, error = err))
            }

            if (!dryRun) {
                // Sync only reports local filesystem mutations; indexing is coordinated by VaultIndexUpdater.
                if (localUpsertPaths.isNotEmpty() || localDeletePaths.isNotEmpty()) {
                    repository.invalidateDocListCache(rootUri)
                    repository.reportFileChanges(
                        rootUri = rootUri,
                        upsertPaths = localUpsertPaths,
                        deletePaths = localDeletePaths,
                        source = VaultFileChangeSource.WEBDAV_SYNC,
                    )
                }
            }

            if (!dryRun) {
                runCatching {
                    if (tombstonesDirty) {
                        persistTombstones(
                            root = root,
                            remoteRootUrl = remoteRootUrl,
                            config = config,
                            ensuredRemoteDirs = ensuredRemoteDirs,
                            remoteExisting = remoteAllByPath[tombstonesPath],
                            tombstones = tombstones,
                        )
                    }
                }

                runCatching {
                    val currentEntries =
                        run {
                            val next = LinkedHashMap<String, WebDavStateFileEntry>()
                            val latestLocal =
                                listLocalFiles(root, includeIndexSqlite = config.includeIndexSqlite)
                                    .filter { shouldSyncPath(it.path) }
                            for (local in latestLocal) {
                                val path = local.path
                                if (blockUnresolved && unresolvedConflicts.containsKey(path)) {
                                    val prev = prevState[path] ?: continue
                                    next[path] = prev
                                    continue
                                }
                                val remote = remoteByPath[path]
                                next[path] =
                                    WebDavStateFileEntry(
                                        localSize = local.size,
                                        localMtimeMs = local.lastModifiedEpochMs,
                                        remoteEtag = normalizeEtagForCompare(remote?.etag),
                                        remoteSize = remote?.size,
                                        remoteMtimeMs = remote?.lastModifiedEpochMs,
                                    )
                            }
                            next
                        }
                    writeStateFiles(stateFile?.uri, currentEntries)
                }

                runCatching {
                    if (unresolvedDirty) {
                        writeUnresolvedConflicts(root, unresolvedConflicts)
                    }
                }
            }

            if (!dryRun && normalizedExpected != null && expectedIndex != normalizedExpected.size) {
                throw IllegalStateException("Plan mismatch: expected ${normalizedExpected.size} ops but got $expectedIndex")
            }

            WebDavSyncSummary(
                uploaded = uploaded,
                downloaded = downloaded,
                deletedRemote = deletedRemote,
                deletedLocal = deletedLocal,
                conflicts = conflicts,
                failed = failed,
            )
        }.fold(
            onSuccess = { summary ->
                if (!dryRun) {
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
                        val endedAtMs = System.currentTimeMillis()
                        writeLastSummary(
                            root = root,
                            startedAtMs = startedAtMs,
                            endedAtMs = endedAtMs,
                            config = config,
                            summary = summary,
                            error = null,
                        )
                        writeLastPlan(
                            root = root,
                            startedAtMs = startedAtMs,
                            endedAtMs = endedAtMs,
                            remoteRootUrl = remoteRootUrlForPlan,
                            config = config,
                            operations = planOps,
                            summary = summary,
                            error = null,
                        )
                    }
                }
                summary
            },
            onFailure = { e ->
                if (!dryRun) {
                    logger.logEvent("end", mapOf("ok" to "false", "error" to (e.message ?: e.javaClass.simpleName)))
                    runCatching {
                        val endedAtMs = System.currentTimeMillis()
                        writeLastSummary(
                            root = root,
                            startedAtMs = startedAtMs,
                            endedAtMs = endedAtMs,
                            config = config,
                            summary = null,
                            error = e.message ?: e.javaClass.simpleName,
                        )
                        writeLastPlan(
                            root = root,
                            startedAtMs = startedAtMs,
                            endedAtMs = endedAtMs,
                            remoteRootUrl = remoteRootUrlForPlan,
                            config = config,
                            operations = planOps,
                            summary = null,
                            error = e.message ?: e.javaClass.simpleName,
                        )
                    }
                }
                throw e
            },
        )

        WebDavSyncPlan(
            generatedAtMs = startedAtMs,
            remoteRootUrl = remoteRootUrlForPlan,
            dryRun = dryRun,
            operations = planOps.toList(),
            summary = summary,
        )
    }

    private data class ConflictDownloadResult(val ok: Boolean, val conflictPath: String?)

    private suspend fun writeLastSummary(
        root: DocumentFile,
        startedAtMs: Long,
        endedAtMs: Long,
        config: WebDavConfig,
        summary: WebDavSyncSummary?,
        error: String?,
    ) {
        val file = ensureLocalFile(root, ".zhixu/sync/webdav_last_summary.json") ?: return
        val baseUrl = config.baseUrl.trim()
        val remoteRoot = config.remoteRoot.trim().ifBlank { "/" }
        val obj =
            JSONObject()
                .put("version", 2)
                .put("engine", "webdav")
                .put("ok", error.isNullOrBlank())
                .put("startedAt", startedAtMs.coerceAtLeast(0L))
                .put("endedAt", endedAtMs.coerceAtLeast(0L))
                .put("durationMs", (endedAtMs - startedAtMs).coerceAtLeast(0L))
                .put("baseUrl", baseUrl)
                .put("remoteRoot", remoteRoot)
                .put("includeIndexSqlite", config.includeIndexSqlite)
                .put("conflictStrategy", config.conflictStrategy.name)
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

    private suspend fun writeLastPlan(
        root: DocumentFile,
        startedAtMs: Long,
        endedAtMs: Long,
        remoteRootUrl: String,
        config: WebDavConfig,
        operations: List<WebDavPlannedOp>,
        summary: WebDavSyncSummary?,
        error: String?,
    ) {
        val file = ensureLocalFile(root, ".zhixu/sync/webdav_last_plan.json") ?: return
        val baseUrl = config.baseUrl.trim()
        val remoteRoot = config.remoteRoot.trim().ifBlank { "/" }
        val maxOps = 2_000
        val truncated = operations.size > maxOps
        val opsArr = JSONArray()
        for (op in operations.take(maxOps)) {
            val o =
                JSONObject()
                    .put("kind", op.kind.name)
                    .put("path", op.path)
                    .put("reason", op.reason)
            if (!op.strategy.isNullOrBlank()) o.put("strategy", op.strategy)
            opsArr.put(o)
        }
        val obj =
            JSONObject()
                .put("version", 1)
                .put("engine", "webdav")
                .put("ok", error.isNullOrBlank())
                .put("startedAt", startedAtMs.coerceAtLeast(0L))
                .put("endedAt", endedAtMs.coerceAtLeast(0L))
                .put("durationMs", (endedAtMs - startedAtMs).coerceAtLeast(0L))
                .put("baseUrl", baseUrl)
                .put("remoteRoot", remoteRoot)
                .put("remoteRootUrl", remoteRootUrl)
                .put("includeIndexSqlite", config.includeIndexSqlite)
                .put("conflictStrategy", config.conflictStrategy.name)
                .put("operationsTotal", operations.size)
                .put("operationsTruncated", truncated)
                .put("operations", opsArr)
        if (summary != null) {
            obj.put("summary", JSONObject().put("uploaded", summary.uploaded).put("downloaded", summary.downloaded))
            obj.getJSONObject("summary").put("deletedRemote", summary.deletedRemote)
            obj.getJSONObject("summary").put("deletedLocal", summary.deletedLocal)
            obj.getJSONObject("summary").put("conflicts", summary.conflicts)
            obj.getJSONObject("summary").put("failed", summary.failed)
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

        suspend fun fileFailed(op: String, path: String, throwable: Throwable) {
            logEvent(
                "file_failed",
                mapOf(
                    "op" to op,
                    "path" to path,
                    "error" to (throwable.message ?: throwable.javaClass.simpleName),
                ),
            )
        }

        suspend fun conflict(path: String, conflictPath: String, reason: String? = null) {
            val uri = conflictsUri ?: return
            val now = System.currentTimeMillis()
            val reasonPart =
                if (reason.isNullOrBlank()) {
                    ""
                } else {
                    ",\"reason\":\"${escapeJson(reason)}\""
                }
            val line =
                "{" +
                    "\"ts\":\"$now\"," +
                    "\"path\":\"${escapeJson(path)}\"," +
                    "\"conflictPath\":\"${escapeJson(conflictPath)}\"" +
                    reasonPart +
                    "}"
            runCatching { repository.appendText(uri, "$line\n") }
        }
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

    private fun listLocalFiles(root: DocumentFile, includeIndexSqlite: Boolean): List<LocalEntry> {
        val out = ArrayList<LocalEntry>()

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
                    out += LocalEntry(
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

    private fun shouldSyncPath(path: String): Boolean {
        val p = path.trimStart('/')
        val name = p.substringAfterLast('/', missingDelimiterValue = p)
        if (name.startsWith("conflict ", ignoreCase = true)) return false
        if (p.equals(".zhixu/syncmeta/webdav_tombstones.json", ignoreCase = true)) return false
        if (p.startsWith(".zhixu/sync/", ignoreCase = true)) return false
        if (p.startsWith(".zhixu/conflicts/", ignoreCase = true)) return false
        if (p.startsWith(".zhixu/history/", ignoreCase = true)) return false
        return true
    }

    private suspend fun ensureRemoteRoot(remoteRootUrl: String, config: WebDavConfig) {
        // Best-effort: attempt MKCOL on the root; ignore common "already exists" codes.
        val code = WebDavClient.mkcol(config, remoteRootUrl)
        if (code == 201 || code == 405 || code == 409) return
        // Some servers require creating parents; try progressively.
        val url = remoteRootUrl.trimEnd('/')
        val idx = url.indexOf("://")
        val pathStart = if (idx >= 0) url.indexOf('/', idx + 3) else -1
        if (pathStart < 0) return
        val base = url.substring(0, pathStart)
        val path = url.substring(pathStart).trim('/')
        var current = base
        for (segment in path.split('/').filter { it.isNotBlank() }) {
            current += "/$segment"
            val c = WebDavClient.mkcol(config, "$current/")
            if (c !in listOf(201, 405)) {
                // ignore
            }
        }
    }

    private suspend fun ensureRemoteDirs(
        remoteRootUrl: String,
        relativeDirPath: String,
        config: WebDavConfig,
        cache: MutableSet<String>,
    ) {
        val parts = relativeDirPath.trim('/').split('/').filter { it.isNotBlank() }
        if (parts.isEmpty()) return
        var current = ""
        for (part in parts) {
            current = if (current.isBlank()) part else "$current/$part"
            if (!cache.add(current)) continue
            val url = buildRemoteUrl(remoteRootUrl, current).ensureTrailingSlash()
            val code = WebDavClient.mkcol(config, url)
            // 201 Created, 405 Method Not Allowed (already exists), 409 Conflict (some servers use this when exists or parent missing).
            if (code !in listOf(201, 405, 409)) {
                // Ignore; a later PUT/GET will surface the real failure.
            }
        }
    }

    private suspend fun listRemoteFiles(remoteRootUrl: String, config: WebDavConfig): List<RemoteEntry> {
        val rootPath = Uri.parse(remoteRootUrl).path ?: "/"
        val toVisit = ArrayDeque<String>()
        val visited = HashSet<String>()
        toVisit.add("")
        val out = ArrayList<RemoteEntry>()

        while (toVisit.isNotEmpty()) {
            val dirRel = toVisit.removeFirst()
            if (!visited.add(dirRel)) continue
            val dirUrl =
                if (dirRel.isBlank()) {
                    remoteRootUrl.ensureTrailingSlash()
                } else {
                    buildRemoteUrl(remoteRootUrl, dirRel).ensureTrailingSlash()
                }

            val (code, xml) = WebDavClient.propfind(config, dirUrl, depth = "1")
            if (code != 207 && code !in 200..299) continue
            val entries = parsePropfind(xml)
            for (entry in entries) {
                val normalizedPath = normalizeHrefToPath(entry.path, rootPath)
                if (normalizedPath.isEmpty()) continue
                val remote = entry.copy(path = normalizedPath)
                out += remote
                if (remote.isDir) {
                    val childDir = normalizedPath.trimEnd('/')
                    // PROPFIND depth=1 includes the directory itself; avoid re-queuing it.
                    if (childDir != dirRel) {
                        toVisit.add(childDir)
                    }
                }
            }
        }

        return out.distinctBy { it.path }
    }

    private fun normalizeHrefToPath(href: String, rootPath: String): String {
        val decoded = Uri.decode(href)
        val path = Uri.parse(decoded).path ?: decoded
        val trimmedRoot = Uri.decode(rootPath).trimEnd('/')
        val trimmedPath = path.trimEnd('/')
        val rel =
            when {
                trimmedPath == trimmedRoot -> ""
                trimmedPath.startsWith("$trimmedRoot/") -> trimmedPath.removePrefix("$trimmedRoot/").trimStart('/')
                // Some servers may return relative hrefs. Accept those, but ignore absolute paths that are outside
                // the requested root to avoid accidentally treating the whole WebDAV root as the current folder.
                else -> if (trimmedPath.startsWith("/")) "" else trimmedPath.trimStart('/')
            }
        return rel
    }

    private fun parsePropfind(xml: String): List<RemoteEntry> {
        val trimmed = xml.trimStart()
        if (trimmed.isBlank() || !trimmed.startsWith("<")) return emptyList()

        val parser = XmlPullParserFactory.newInstance().newPullParser()
        parser.setInput(xml.reader())

        val out = ArrayList<RemoteEntry>()
        var href: String? = null
        var isDir: Boolean? = null
        var size: Long? = null
        var lastModified: Long? = null
        var etag: String? = null

        fun flush() {
            val h = href ?: return
            val d = isDir ?: false
            out += RemoteEntry(path = h, isDir = d, size = size, lastModifiedEpochMs = lastModified, etag = etag?.trim()?.ifBlank { null })
            href = null
            isDir = null
            size = null
            lastModified = null
            etag = null
        }

        fun tagName(): String = parser.name.orEmpty().substringAfterLast(':').lowercase()

        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> {
                    when (tagName()) {
                        "response" -> {
                            href = null; isDir = null; size = null; lastModified = null; etag = null
                        }

                        "href" -> href = parser.nextText()
                        "getcontentlength" -> size = parser.nextText().trim().toLongOrNull()
                        "getlastmodified" -> lastModified = parseHttpDate(parser.nextText())
                        "getetag" -> etag = parser.nextText()
                        "collection" -> isDir = true
                    }
                }

                XmlPullParser.END_TAG -> {
                    if (tagName() == "response") flush()
                }
            }
            event = parser.next()
        }

        return out
    }

    private fun parseHttpDate(text: String): Long? {
        return runCatching {
            ZonedDateTime.parse(text.trim(), DateTimeFormatter.RFC_1123_DATE_TIME).toInstant().toEpochMilli()
        }.getOrNull()
    }

    private suspend fun uploadFile(
        remoteRootUrl: String,
        local: LocalEntry,
        config: WebDavConfig,
        ensuredRemoteDirs: MutableSet<String>,
        ifMatch: String?,
        ifNoneMatchStar: Boolean,
    ): Boolean {
        val parentPath = local.path.substringBeforeLast('/', missingDelimiterValue = "").trim()
        if (parentPath.isNotBlank()) {
            ensureRemoteDirs(remoteRootUrl, parentPath, config, ensuredRemoteDirs)
        }
        val url = buildRemoteUrl(remoteRootUrl, local.path)
        val code =
            WebDavClient.putStream(
                config = config,
                targetUrl = url,
                contentType = local.file.type,
                contentLength = local.size.takeIf { it > 0L },
                openStream = { openLocalInputStream(local.file.uri) },
                ifMatch = ifMatch,
                ifNoneMatchStar = ifNoneMatchStar,
            )
        return code in 200..299 || code == 204
    }

    private suspend fun downloadFile(root: DocumentFile, remoteRootUrl: String, path: String, config: WebDavConfig): Boolean {
        val url = buildRemoteUrl(remoteRootUrl, path)
        var wrote = false
        val code =
            WebDavClient.getToStream(config, url) { input ->
                val dest = ensureLocalFile(root, path) ?: error("Failed to create local file for $path")
                openLocalOutputStream(dest.uri, mode = "wt")?.use { out ->
                    input.copyTo(out)
                    wrote = true
                } ?: error("Failed to open output stream for ${dest.uri}")
            }
        return code in 200..299 && wrote
    }

    private suspend fun downloadConflictFile(
        root: DocumentFile,
        remoteRootUrl: String,
        path: String,
        config: WebDavConfig,
        kind: String,
    ): ConflictDownloadResult {
        val url = buildRemoteUrl(remoteRootUrl, path)
        val safe = path.trimStart('/').replace('\\', '/')
        val suffix = kind.trim().ifBlank { "remote" }
        val destPath = ".zhixu/conflicts/$safe/${System.currentTimeMillis()}-$suffix"
        var wrote = false
        val code =
            WebDavClient.getToStream(config, url) { input ->
                val dest = ensureLocalFile(root, destPath) ?: error("Failed to create conflict file for $destPath")
                openLocalOutputStream(dest.uri, mode = "wt")?.use { out ->
                    input.copyTo(out)
                    wrote = true
                } ?: error("Failed to open output stream for ${dest.uri}")
            }
        return if (code in 200..299 && wrote) ConflictDownloadResult(true, destPath) else ConflictDownloadResult(false, null)
    }

    private suspend fun saveLocalConflictArtifact(
        root: DocumentFile,
        originalPath: String,
        sourceUri: Uri,
        kind: String,
    ): String? {
        val safe = originalPath.trimStart('/').replace('\\', '/')
        val suffix = kind.trim().ifBlank { "local" }
        val destPath = ".zhixu/conflicts/$safe/${System.currentTimeMillis()}-$suffix"
        val dest = ensureLocalFile(root, destPath) ?: return null
        var wrote = false
        openLocalInputStream(sourceUri)?.use { input ->
            openLocalOutputStream(dest.uri, mode = "wt")?.use { out ->
                input.copyTo(out)
                wrote = true
            }
        }
        return destPath.takeIf { wrote }
    }

    private data class TombstoneEntry(
        val deletedAtMs: Long,
        val remoteEtag: String?,
        val remoteSize: Long?,
        val remoteMtimeMs: Long?,
    )

    private data class TombstoneLoadResult(
        val entries: Map<String, TombstoneEntry>,
        val dirty: Boolean,
    )

    private suspend fun loadMergedTombstones(
        rootUri: Uri,
        root: DocumentFile,
        remoteRootUrl: String,
        config: WebDavConfig,
        remoteExisting: RemoteEntry?,
        writeLocal: Boolean,
    ): TombstoneLoadResult {
        val tombstonesPath = ".zhixu/syncmeta/webdav_tombstones.json"
        val localUri =
            if (writeLocal) {
                ensureLocalFile(root, tombstonesPath)?.uri
            } else {
                repository.resolveVaultFileUri(rootUri, tombstonesPath)
            }
        val localText = localUri?.let { runCatching { repository.readText(it) }.getOrNull().orEmpty() }.orEmpty()
        val localMap = parseTombstonesJson(localText)

        val remoteText =
            if (remoteExisting == null) {
                ""
            } else {
                runCatching { downloadRemoteText(remoteRootUrl, config, tombstonesPath) }.getOrNull().orEmpty()
            }
        val remoteMap = parseTombstonesJson(remoteText)
        val merged = mergeTombstoneMaps(localMap, remoteMap)

        if (writeLocal && localUri != null) {
            runCatching {
                val json = tombstonesToJson(merged)
                repository.writeText(localUri, json)
            }
        }

        return TombstoneLoadResult(entries = merged, dirty = merged != remoteMap)
    }

    private suspend fun persistTombstones(
        root: DocumentFile,
        remoteRootUrl: String,
        config: WebDavConfig,
        ensuredRemoteDirs: MutableSet<String>,
        remoteExisting: RemoteEntry?,
        tombstones: Map<String, TombstoneEntry>,
    ) {
        val tombstonesPath = ".zhixu/syncmeta/webdav_tombstones.json"
        val localFile = ensureLocalFile(root, tombstonesPath) ?: return
        val json = tombstonesToJson(tombstones)
        runCatching { repository.writeText(localFile.uri, json) }

        suspend fun attemptUpload(remote: RemoteEntry?): Boolean {
            val localEntry =
                LocalEntry(
                    path = tombstonesPath,
                    file = localFile,
                    size = localFile.length(),
                    lastModifiedEpochMs = localFile.lastModified(),
                )
            return uploadFile(
                remoteRootUrl = remoteRootUrl,
                local = localEntry,
                config = config,
                ensuredRemoteDirs = ensuredRemoteDirs,
                ifMatch = remote?.etag,
                ifNoneMatchStar = remote == null,
            )
        }

        if (attemptUpload(remoteExisting)) return

        val refreshed = refreshRemoteEntry(remoteRootUrl, config, tombstonesPath)
        val remoteText = runCatching { downloadRemoteText(remoteRootUrl, config, tombstonesPath) }.getOrNull().orEmpty()
        val remoteMap = parseTombstonesJson(remoteText)
        val merged = mergeTombstoneMaps(tombstones, remoteMap)
        if (merged != tombstones) {
            runCatching { repository.writeText(localFile.uri, tombstonesToJson(merged)) }
        }
        attemptUpload(refreshed)
    }

    private fun parseTombstonesJson(text: String): Map<String, TombstoneEntry> {
        val raw = text.trim()
        if (raw.isBlank()) return emptyMap()
        val obj = runCatching { JSONObject(raw) }.getOrNull() ?: return emptyMap()
        val stones = obj.optJSONObject("tombstones") ?: JSONObject()
        val it = stones.keys()
        val out = LinkedHashMap<String, TombstoneEntry>()
        while (it.hasNext()) {
            val path = it.next().orEmpty().trim().trimStart('/')
            if (path.isBlank()) continue
            val e = stones.optJSONObject(path) ?: continue
            val deletedAt = e.optLong("deletedAt", 0L).coerceAtLeast(0L)
            if (deletedAt <= 0L) continue
            val remoteEtag = e.optString("remoteEtag").orEmpty().trim().ifBlank { null }
            val remoteSize = if (e.has("remoteSize")) e.optLong("remoteSize").coerceAtLeast(0L) else null
            val remoteMtimeMs = if (e.has("remoteMtimeMs")) e.optLong("remoteMtimeMs").coerceAtLeast(0L) else null
            out[path] =
                TombstoneEntry(
                    deletedAtMs = deletedAt,
                    remoteEtag = remoteEtag,
                    remoteSize = remoteSize,
                    remoteMtimeMs = remoteMtimeMs,
                )
        }
        return out
    }

    private fun mergeTombstoneMaps(
        a: Map<String, TombstoneEntry>,
        b: Map<String, TombstoneEntry>,
    ): Map<String, TombstoneEntry> {
        if (a.isEmpty()) return b
        if (b.isEmpty()) return a
        val out = LinkedHashMap<String, TombstoneEntry>(maxOf(a.size, b.size) + 8)
        for ((path, e) in a) out[path] = e
        for ((path, e) in b) {
            val prev = out[path]
            out[path] = if (prev == null) e else mergeTombstoneEntry(prev, e)
        }
        return out
    }

    private fun mergeTombstoneEntry(a: TombstoneEntry, b: TombstoneEntry): TombstoneEntry {
        if (a.deletedAtMs != b.deletedAtMs) return if (a.deletedAtMs >= b.deletedAtMs) a else b
        return TombstoneEntry(
            deletedAtMs = a.deletedAtMs,
            remoteEtag = a.remoteEtag ?: b.remoteEtag,
            remoteSize = a.remoteSize ?: b.remoteSize,
            remoteMtimeMs = a.remoteMtimeMs ?: b.remoteMtimeMs,
        )
    }

    private fun tombstonesToJson(tombstones: Map<String, TombstoneEntry>): String {
        val stonesObj = JSONObject()
        for ((path, t) in tombstones) {
            val e = JSONObject().put("deletedAt", t.deletedAtMs.coerceAtLeast(0L))
            if (!t.remoteEtag.isNullOrBlank()) e.put("remoteEtag", t.remoteEtag)
            if (t.remoteSize != null) e.put("remoteSize", t.remoteSize)
            if (t.remoteMtimeMs != null) e.put("remoteMtimeMs", t.remoteMtimeMs)
            stonesObj.put(path, e)
        }
        return JSONObject().put("version", 1).put("updatedAt", System.currentTimeMillis()).put("tombstones", stonesObj).toString()
    }

    private suspend fun refreshRemoteEntry(remoteRootUrl: String, config: WebDavConfig, path: String): RemoteEntry? {
        val url = buildRemoteUrl(remoteRootUrl, path)
        val (code, xml) = WebDavClient.propfind(config, url, depth = "0")
        if (code != 207 && code !in 200..299) return null
        val rootPath = Uri.parse(remoteRootUrl).path ?: "/"
        val entries =
            parsePropfind(xml).map { e ->
                val normalized = normalizeHrefToPath(e.path, rootPath)
                e.copy(path = normalized)
            }
        return entries.firstOrNull { it.path == path && !it.isDir }
    }

    private suspend fun downloadRemoteText(remoteRootUrl: String, config: WebDavConfig, path: String): String {
        val url = buildRemoteUrl(remoteRootUrl, path)
        val (code, bytes) = WebDavClient.get(config, url)
        if (code == 404) return ""
        if (code !in 200..299) return ""
        return runCatching { bytes.toString(Charsets.UTF_8) }.getOrDefault("")
    }

    private suspend fun readStatePaths(stateUri: Uri?): Set<String> {
        return readStateFiles(stateUri).keys
    }

    private suspend fun readUnresolvedConflicts(rootUri: Uri): MutableMap<String, WebDavUnresolvedConflict> {
        val uri = repository.resolveVaultFileUri(rootUri, WEB_DAV_UNRESOLVED_CONFLICTS_PATH) ?: return mutableMapOf()
        val text = runCatching { repository.readText(uri) }.getOrNull().orEmpty().trim()
        if (text.isBlank()) return mutableMapOf()
        val obj = runCatching { JSONObject(text) }.getOrNull() ?: return mutableMapOf()
        val conflicts = obj.optJSONObject("conflicts") ?: JSONObject()
        val it = conflicts.keys()
        val out = LinkedHashMap<String, WebDavUnresolvedConflict>()
        while (it.hasNext()) {
            val path = it.next().orEmpty().trim().trimStart('/').replace('\\', '/')
            if (path.isBlank()) continue
            val c = conflicts.optJSONObject(path) ?: continue
            val createdAtMs = c.optLong("createdAtMs", 0L).coerceAtLeast(0L)
            val reason = c.optString("reason").orEmpty().trim().ifBlank { "conflict" }
            val localArtifactPath = c.optString("localArtifactPath").orEmpty().trim().ifBlank { null }
            val remoteArtifactPath = c.optString("remoteArtifactPath").orEmpty().trim().ifBlank { null }
            out[path] =
                WebDavUnresolvedConflict(
                    path = path,
                    createdAtMs = createdAtMs,
                    reason = reason,
                    localArtifactPath = localArtifactPath,
                    remoteArtifactPath = remoteArtifactPath,
                )
        }
        return out
    }

    private suspend fun writeUnresolvedConflicts(root: DocumentFile, conflicts: Map<String, WebDavUnresolvedConflict>) {
        val file = ensureLocalFile(root, WEB_DAV_UNRESOLVED_CONFLICTS_PATH) ?: return
        val conflictsObj = JSONObject()
        for ((path, c) in conflicts) {
            val o =
                JSONObject()
                    .put("createdAtMs", c.createdAtMs.coerceAtLeast(0L))
                    .put("reason", c.reason)
            if (!c.localArtifactPath.isNullOrBlank()) o.put("localArtifactPath", c.localArtifactPath)
            if (!c.remoteArtifactPath.isNullOrBlank()) o.put("remoteArtifactPath", c.remoteArtifactPath)
            conflictsObj.put(path, o)
        }

        val obj =
            JSONObject()
                .put("version", 1)
                .put("savedAt", System.currentTimeMillis())
                .put("conflicts", conflictsObj)
        runCatching { repository.writeText(file.uri, obj.toString()) }
    }

    private data class WebDavStateFileEntry(
        val localSize: Long,
        val localMtimeMs: Long,
        val remoteEtag: String?,
        val remoteSize: Long?,
        val remoteMtimeMs: Long?,
    )

    private suspend fun readStateFiles(stateUri: Uri?): Map<String, WebDavStateFileEntry> {
        val uri = stateUri ?: return emptyMap()
        val text = runCatching { repository.readText(uri) }.getOrNull().orEmpty()
        val obj = runCatching { JSONObject(text) }.getOrNull() ?: return emptyMap()
        val version = obj.optInt("version", 1)

        if (version >= 2) {
            val files = obj.optJSONObject("files") ?: JSONObject()
            val it = files.keys()
            val out = LinkedHashMap<String, WebDavStateFileEntry>()
            while (it.hasNext()) {
                val path = it.next().orEmpty().trim().trimStart('/')
                if (path.isBlank()) continue
                val f = files.optJSONObject(path) ?: continue
                val remoteEtag = f.optString("remoteEtag").orEmpty().trim().ifBlank { null }
                val remoteSize = f.takeIf { it.has("remoteSize") }?.optLong("remoteSize")?.coerceAtLeast(0L)
                val remoteMtimeMs = f.takeIf { it.has("remoteMtimeMs") }?.optLong("remoteMtimeMs")?.coerceAtLeast(0L)
                out[path] =
                    WebDavStateFileEntry(
                        localSize = f.optLong("localSize", 0L).coerceAtLeast(0L),
                        localMtimeMs = f.optLong("localMtimeMs", 0L).coerceAtLeast(0L),
                        remoteEtag = remoteEtag,
                        remoteSize = remoteSize,
                        remoteMtimeMs = remoteMtimeMs,
                    )
            }
            return out
        }

        val arr = obj.optJSONArray("paths") ?: JSONArray()
        val out = LinkedHashMap<String, WebDavStateFileEntry>()
        for (i in 0 until arr.length()) {
            val path = arr.optString(i).orEmpty().trim().trimStart('/')
            if (path.isBlank()) continue
            out[path] =
                WebDavStateFileEntry(
                    localSize = -1L,
                    localMtimeMs = -1L,
                    remoteEtag = null,
                    remoteSize = null,
                    remoteMtimeMs = null,
                )
        }
        return out
    }

    private suspend fun writeStateFiles(stateUri: Uri?, entries: Map<String, WebDavStateFileEntry>) {
        val uri = stateUri ?: return
        val filesObj = JSONObject()
        for ((path, f) in entries) {
            val o =
                JSONObject()
                    .put("localSize", f.localSize)
                    .put("localMtimeMs", f.localMtimeMs)
            if (!f.remoteEtag.isNullOrBlank()) o.put("remoteEtag", f.remoteEtag)
            if (f.remoteSize != null) o.put("remoteSize", f.remoteSize)
            if (f.remoteMtimeMs != null) o.put("remoteMtimeMs", f.remoteMtimeMs)
            filesObj.put(path, o)
        }

        val obj =
            JSONObject()
                .put("version", 2)
                .put("savedAt", System.currentTimeMillis())
                .put("files", filesObj)
        runCatching { repository.writeText(uri, obj.toString()) }
    }

    private fun buildRemoteUrl(remoteRootUrl: String, relativePath: String): String {
        val base = remoteRootUrl.toHttpUrl()
        return base.newBuilder()
            .addPathSegments(relativePath.trimStart('/'))
            .build()
            .toString()
    }

    private fun String.ensureTrailingSlash(): String = if (endsWith("/")) this else "$this/"

    private fun openLocalInputStream(uri: Uri): InputStream? {
        if (uri.scheme.equals("file", ignoreCase = true)) {
            val path = uri.path ?: return null
            return runCatching { FileInputStream(File(path)) }.getOrNull()
        }
        return runCatching { context.contentResolver.openInputStream(uri) }.getOrNull()
    }

    private fun openLocalOutputStream(uri: Uri, mode: String): OutputStream? {
        if (uri.scheme.equals("file", ignoreCase = true)) {
            val path = uri.path ?: return null
            val file = File(path)
            file.parentFile?.mkdirs()
            return runCatching { FileOutputStream(file, false) }.getOrNull()
        }
        return runCatching { context.contentResolver.openOutputStream(uri, mode) }.getOrNull()
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
}
