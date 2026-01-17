package app.zhixu.sync

import android.content.Context
import android.net.Uri
import app.zhixu.core.tasks.Ulid
import app.zhixu.data.VaultRepository
import app.zhixu.data.WebDavConfig
import app.zhixu.data.WebDavConflictStrategy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

enum class WebDavSyncTaskTrigger {
    MANUAL,
    AUTO,
}

enum class WebDavSyncTaskOpState {
    PENDING,
    SKIPPED,
    DONE,
    FAILED,
}

data class WebDavSyncTaskOp(
    val kind: WebDavPlannedOpKind,
    val path: String,
    val reason: String,
    val state: WebDavSyncTaskOpState = WebDavSyncTaskOpState.PENDING,
    val error: String? = null,
    // Only for CONFLICT ops. Null means unresolved.
    val resolution: WebDavConflictStrategy? = null,
)

data class WebDavSyncTaskRun(
    val startedAtMs: Long,
    val endedAtMs: Long,
    val summary: WebDavSyncSummary?,
    val error: String?,
)

data class WebDavSyncTask(
    val id: String,
    val trigger: WebDavSyncTaskTrigger,
    val createdAtMs: Long,
    val baseUrl: String,
    val remoteRoot: String,
    val includeIndexSqlite: Boolean,
    val operations: List<WebDavSyncTaskOp>,
    val run: WebDavSyncTaskRun? = null,
)

data class WebDavSyncTaskStoreState(
    val current: WebDavSyncTask?,
    val history: List<WebDavSyncTask>,
) {
    companion object {
        val EMPTY = WebDavSyncTaskStoreState(current = null, history = emptyList())
    }
}

class WebDavSyncTaskStore(
    private val repository: VaultRepository,
) {
    private val lock = Mutex()

    private val relativePath = ".zhixu/sync/webdav_tasks.json"
    private val mimeType = "application/json"
    private val maxHistory = 20

    suspend fun load(rootUri: Uri): WebDavSyncTaskStoreState =
        lock.withLock {
            withContext(Dispatchers.IO) {
                val uri = repository.resolveVaultFileUri(rootUri, relativePath) ?: return@withContext WebDavSyncTaskStoreState.EMPTY
                val raw = runCatching { repository.readText(uri) }.getOrNull().orEmpty().trim()
                if (raw.isBlank()) return@withContext WebDavSyncTaskStoreState.EMPTY
                parseState(raw)
            }
        }

    suspend fun update(rootUri: Uri, transform: (WebDavSyncTaskStoreState) -> WebDavSyncTaskStoreState): WebDavSyncTaskStoreState =
        lock.withLock {
            withContext(Dispatchers.IO) {
                val prev = runCatching { loadUnlocked(rootUri) }.getOrDefault(WebDavSyncTaskStoreState.EMPTY)
                val next = transform(prev).normalize()
                saveUnlocked(rootUri, next)
                next
            }
        }

    private suspend fun loadUnlocked(rootUri: Uri): WebDavSyncTaskStoreState {
        val uri = repository.resolveVaultFileUri(rootUri, relativePath) ?: return WebDavSyncTaskStoreState.EMPTY
        val raw = runCatching { repository.readText(uri) }.getOrNull().orEmpty().trim()
        if (raw.isBlank()) return WebDavSyncTaskStoreState.EMPTY
        return parseState(raw)
    }

    private suspend fun saveUnlocked(rootUri: Uri, state: WebDavSyncTaskStoreState) {
        repository.ensureVaultStructure(rootUri)
        val uri = repository.ensureVaultFile(rootUri, relativePath, mimeType)
        repository.writeText(uri, serializeState(state))
    }

    private fun WebDavSyncTaskStoreState.normalize(): WebDavSyncTaskStoreState {
        val historyTrimmed = history.take(maxHistory)
        return copy(history = historyTrimmed)
    }

    private fun parseState(raw: String): WebDavSyncTaskStoreState {
        val obj = runCatching { JSONObject(raw) }.getOrNull() ?: return WebDavSyncTaskStoreState.EMPTY
        val version = obj.optInt("version", 0)
        if (version != 1) {
            // Best-effort forward compatibility: attempt parse anyway.
        }

        val current = obj.optJSONObject("current")?.let(::parseTask)
        val history =
            (obj.optJSONArray("history") ?: JSONArray()).let { arr ->
                buildList {
                    for (i in 0 until arr.length()) {
                        val t = arr.optJSONObject(i) ?: continue
                        add(parseTask(t))
                    }
                }
            }

        return WebDavSyncTaskStoreState(current = current, history = history)
    }

    private fun serializeState(state: WebDavSyncTaskStoreState): String {
        val obj = JSONObject().put("version", 1)
        if (state.current != null) obj.put("current", serializeTask(state.current))
        val historyArr = JSONArray()
        for (t in state.history.take(maxHistory)) historyArr.put(serializeTask(t))
        obj.put("history", historyArr)
        return obj.toString()
    }

    private fun parseTask(obj: JSONObject): WebDavSyncTask {
        val id = obj.optString("id").orEmpty().trim().ifBlank { Ulid.next() }
        val trigger = WebDavSyncTaskTrigger.valueOf(obj.optString("trigger").orEmpty().trim().ifBlank { WebDavSyncTaskTrigger.MANUAL.name })
        val createdAtMs = obj.optLong("createdAtMs", 0L).coerceAtLeast(0L)
        val baseUrl = obj.optString("baseUrl").orEmpty()
        val remoteRoot = obj.optString("remoteRoot").orEmpty().ifBlank { "/" }
        val includeIndexSqlite = obj.optBoolean("includeIndexSqlite", true)
        val ops =
            (obj.optJSONArray("operations") ?: JSONArray()).let { arr ->
                buildList {
                    for (i in 0 until arr.length()) {
                        val o = arr.optJSONObject(i) ?: continue
                        parseOp(o)?.let { add(it) }
                    }
                }
            }
        val run =
            obj.optJSONObject("run")?.let { runObj ->
                WebDavSyncTaskRun(
                    startedAtMs = runObj.optLong("startedAtMs", 0L).coerceAtLeast(0L),
                    endedAtMs = runObj.optLong("endedAtMs", 0L).coerceAtLeast(0L),
                    summary = runObj.optJSONObject("summary")?.let { parseSummary(it) },
                    error = runObj.optString("error").orEmpty().trim().ifBlank { null },
                )
            }
        return WebDavSyncTask(
            id = id,
            trigger = trigger,
            createdAtMs = createdAtMs,
            baseUrl = baseUrl,
            remoteRoot = remoteRoot,
            includeIndexSqlite = includeIndexSqlite,
            operations = ops,
            run = run,
        )
    }

    private fun serializeTask(task: WebDavSyncTask): JSONObject {
        val obj =
            JSONObject()
                .put("id", task.id)
                .put("trigger", task.trigger.name)
                .put("createdAtMs", task.createdAtMs.coerceAtLeast(0L))
                .put("baseUrl", task.baseUrl)
                .put("remoteRoot", task.remoteRoot)
                .put("includeIndexSqlite", task.includeIndexSqlite)
        val opsArr = JSONArray()
        for (op in task.operations) opsArr.put(serializeOp(op))
        obj.put("operations", opsArr)
        if (task.run != null) {
            obj.put("run", serializeRun(task.run))
        }
        return obj
    }

    private fun parseOp(obj: JSONObject): WebDavSyncTaskOp? {
        val kindRaw = obj.optString("kind").orEmpty().trim().uppercase()
        val kind = WebDavPlannedOpKind.entries.firstOrNull { it.name == kindRaw } ?: return null
        val path = obj.optString("path").orEmpty().trim().trimStart('/').replace('\\', '/')
        if (path.isBlank()) return null
        val reason = obj.optString("reason").orEmpty()
        val stateRaw = obj.optString("state").orEmpty().trim().uppercase()
        val state = WebDavSyncTaskOpState.entries.firstOrNull { it.name == stateRaw } ?: WebDavSyncTaskOpState.PENDING
        val error = obj.optString("error").orEmpty().trim().ifBlank { null }
        val resolution =
            obj.optString("resolution").orEmpty().trim().uppercase().ifBlank { null }?.let { s ->
                WebDavConflictStrategy.entries.firstOrNull { it.name == s }
            }?.takeIf { it != WebDavConflictStrategy.ASK_EACH_TIME }
        return WebDavSyncTaskOp(
            kind = kind,
            path = path,
            reason = reason,
            state = state,
            error = error,
            resolution = resolution,
        )
    }

    private fun serializeOp(op: WebDavSyncTaskOp): JSONObject {
        val obj =
            JSONObject()
                .put("kind", op.kind.name)
                .put("path", op.path)
                .put("reason", op.reason)
                .put("state", op.state.name)
        if (!op.error.isNullOrBlank()) obj.put("error", op.error.take(500))
        if (op.kind == WebDavPlannedOpKind.CONFLICT && op.resolution != null && op.resolution != WebDavConflictStrategy.ASK_EACH_TIME) {
            obj.put("resolution", op.resolution.name)
        }
        return obj
    }

    private fun parseSummary(obj: JSONObject): WebDavSyncSummary =
        WebDavSyncSummary(
            uploaded = obj.optInt("uploaded", 0).coerceAtLeast(0),
            downloaded = obj.optInt("downloaded", 0).coerceAtLeast(0),
            deletedRemote = obj.optInt("deletedRemote", 0).coerceAtLeast(0),
            deletedLocal = obj.optInt("deletedLocal", 0).coerceAtLeast(0),
            conflicts = obj.optInt("conflicts", 0).coerceAtLeast(0),
            failed = obj.optInt("failed", 0).coerceAtLeast(0),
        )

    private fun serializeRun(run: WebDavSyncTaskRun): JSONObject {
        val obj =
            JSONObject()
                .put("startedAtMs", run.startedAtMs.coerceAtLeast(0L))
                .put("endedAtMs", run.endedAtMs.coerceAtLeast(0L))
        if (run.summary != null) {
            obj.put(
                "summary",
                JSONObject()
                    .put("uploaded", run.summary.uploaded)
                    .put("downloaded", run.summary.downloaded)
                    .put("deletedRemote", run.summary.deletedRemote)
                    .put("deletedLocal", run.summary.deletedLocal)
                    .put("conflicts", run.summary.conflicts)
                    .put("failed", run.summary.failed),
            )
        }
        if (!run.error.isNullOrBlank()) obj.put("error", run.error.take(500))
        return obj
    }
}

class WebDavSyncTaskManager(
    private val context: Context,
    private val repository: VaultRepository,
) {
    private val store = WebDavSyncTaskStore(repository)

    suspend fun load(rootUri: Uri): WebDavSyncTaskStoreState = store.load(rootUri)

    suspend fun generateTask(
        rootUri: Uri,
        config: WebDavConfig,
        trigger: WebDavSyncTaskTrigger,
        onlyPaths: Set<String>? = null,
    ): WebDavSyncTask? {
        val normalizedPaths =
            onlyPaths
                ?.map { it.trim().trimStart('/').replace('\\', '/') }
                ?.filter { it.isNotBlank() }
                ?.toSet()
                ?.takeIf { it.isNotEmpty() }

        return store.update(rootUri) { state ->
            if (state.current != null) return@update state
            state
        }.current ?: run {
            val engine = WebDavSyncEngine(context, repository)
            val planConfig = config.copy(conflictStrategy = WebDavConflictStrategy.ASK_EACH_TIME)
            val plan =
                if (normalizedPaths == null) {
                    engine.planVault(rootUri, planConfig)
                } else {
                    engine.planVaultPaths(rootUri, planConfig, normalizedPaths)
                }
            val taskId = Ulid.next()
            val createdAt = System.currentTimeMillis()
            val task =
                WebDavSyncTask(
                    id = taskId,
                    trigger = trigger,
                    createdAtMs = createdAt,
                    baseUrl = config.baseUrl.trim(),
                    remoteRoot = config.remoteRoot.trim().ifBlank { "/" },
                    includeIndexSqlite = config.includeIndexSqlite,
                    operations =
                        plan.operations.map { op ->
                            WebDavSyncTaskOp(
                                kind = op.kind,
                                path = op.path.trim().trimStart('/').replace('\\', '/'),
                                reason = op.reason,
                                state = WebDavSyncTaskOpState.PENDING,
                                resolution = null,
                            )
                        },
                )
            store.update(rootUri) { s ->
                if (s.current != null) s else s.copy(current = task)
            }.current
        }
    }

    suspend fun updateCurrentTask(rootUri: Uri, transform: (WebDavSyncTask) -> WebDavSyncTask): WebDavSyncTaskStoreState =
        store.update(rootUri) { state ->
            val cur = state.current ?: return@update state
            state.copy(current = transform(cur))
        }

    suspend fun discardCurrentTask(rootUri: Uri, reason: String = "discarded"): WebDavSyncTaskStoreState =
        store.update(rootUri) { state ->
            val cur = state.current ?: return@update state
            val endedAt = System.currentTimeMillis()
            val archived =
                cur.copy(
                    run =
                        WebDavSyncTaskRun(
                            startedAtMs = cur.run?.startedAtMs ?: endedAt,
                            endedAtMs = endedAt,
                            summary = cur.run?.summary,
                            error = reason,
                        ),
                )
            state.copy(current = null, history = listOf(archived) + state.history)
        }

    data class ExecuteResult(
        val updatedState: WebDavSyncTaskStoreState,
        val ranTask: WebDavSyncTask,
    )

    suspend fun executeCurrentTask(rootUri: Uri, config: WebDavConfig): ExecuteResult {
        val initial = store.load(rootUri)
        val task = initial.current ?: error("No current task")

        // Hard safety: refuse to execute if config changed since task was generated.
        val baseUrlNow = config.baseUrl.trim()
        val remoteRootNow = config.remoteRoot.trim().ifBlank { "/" }
        if (task.baseUrl != baseUrlNow || task.remoteRoot != remoteRootNow || task.includeIndexSqlite != config.includeIndexSqlite) {
            error("WebDAV config changed; regenerate task")
        }

        val opsResolved =
            task.operations.map { op ->
                if (op.state == WebDavSyncTaskOpState.SKIPPED) return@map op
                if (op.kind == WebDavPlannedOpKind.CONFLICT) {
                    val r = op.resolution
                    require(r != null && r != WebDavConflictStrategy.ASK_EACH_TIME) { "Unresolved conflict: ${op.path}" }
                }
                op.copy(state = WebDavSyncTaskOpState.PENDING, error = null)
            }

        val toExecute = opsResolved.filter { it.state != WebDavSyncTaskOpState.SKIPPED }
        val expected =
            toExecute.map { op ->
                WebDavPlannedOp(
                    kind = op.kind,
                    path = op.path,
                    reason = op.reason,
                    strategy = null,
                )
            }
        val conflictOverrides =
            toExecute
                .filter { it.kind == WebDavPlannedOpKind.CONFLICT }
                .associate { it.path to (it.resolution ?: WebDavConflictStrategy.ASK_EACH_TIME) }

        val engine = WebDavSyncEngine(context, repository)
        val observed = ArrayList<WebDavSyncObservedOpResult>()

        val startedAt = System.currentTimeMillis()
        val stateRunning =
            store.update(rootUri) { s ->
                val cur = s.current ?: return@update s
                if (cur.id != task.id) return@update s
                s.copy(
                    current =
                        cur.copy(
                            operations = opsResolved,
                            run =
                                WebDavSyncTaskRun(
                                    startedAtMs = startedAt,
                                    endedAtMs = 0L,
                                    summary = null,
                                    error = null,
                                ),
                        ),
                )
            }

        var summary: WebDavSyncSummary?
        var errorText: String?
        try {
            summary =
                engine.syncVaultWithExpectedPlan(
                    rootUri = rootUri,
                    config =
                        config.copy(
                            // Ensure conflicts are never silently resolved.
                            conflictStrategy = WebDavConflictStrategy.ASK_EACH_TIME,
                        ),
                    expectedOperations = expected,
                    conflictStrategyOverrides = conflictOverrides,
                    observer = { result -> observed += result },
                )
            errorText = null
        } catch (e: Throwable) {
            summary = null
            errorText = e.message ?: e.javaClass.simpleName
        }
        val endedAt = System.currentTimeMillis()

        val resultsByKey = observed.associateBy({ "${it.op.kind.name}|${it.op.path}" }, { it })
        val finalOps =
            opsResolved.map { op ->
                if (op.state == WebDavSyncTaskOpState.SKIPPED) return@map op
                val key = "${op.kind.name}|${op.path}"
                val res = resultsByKey[key]
                if (res == null) op
                else if (res.ok) op.copy(state = WebDavSyncTaskOpState.DONE, error = null)
                else op.copy(state = WebDavSyncTaskOpState.FAILED, error = res.error)
            }

        val finishedTask =
            task.copy(
                operations = finalOps,
                run =
                    WebDavSyncTaskRun(
                        startedAtMs = startedAt,
                        endedAtMs = endedAt,
                        summary = summary,
                        error = errorText,
                    ),
            )

        val updated =
            store.update(rootUri) { s ->
                val cur = s.current
                if (cur == null || cur.id != task.id) return@update s
                s.copy(current = null, history = listOf(finishedTask) + s.history)
            }

        val ran = updated.history.firstOrNull { it.id == finishedTask.id } ?: finishedTask
        return ExecuteResult(updatedState = updated, ranTask = ran)
    }

    companion object {
        fun formatEpochMs(ms: Long): String {
            if (ms <= 0L) return "-"
            return runCatching {
                Instant
                    .ofEpochMilli(ms)
                    .atZone(ZoneId.systemDefault())
                    .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))
            }.getOrElse { ms.toString() }
        }
    }
}
