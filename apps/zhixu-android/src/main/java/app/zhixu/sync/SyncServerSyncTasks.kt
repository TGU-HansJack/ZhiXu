package app.zhixu.sync

import android.content.Context
import android.net.Uri
import app.zhixu.core.tasks.Ulid
import app.zhixu.data.VaultRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

enum class SyncServerSyncTaskTrigger {
    MANUAL,
    AUTO,
}

enum class SyncServerSyncTaskOpState {
    PENDING,
    SKIPPED,
    DONE,
    FAILED,
}

data class SyncServerSyncTaskOp(
    val kind: WebDavPlannedOpKind,
    val path: String,
    val reason: String,
    val state: SyncServerSyncTaskOpState = SyncServerSyncTaskOpState.PENDING,
    val error: String? = null,
)

data class SyncServerSyncTaskRun(
    val startedAtMs: Long,
    val endedAtMs: Long,
    val summary: OfficialVaultSyncSummary?,
    val error: String?,
)

data class SyncServerSyncTask(
    val id: String,
    val trigger: SyncServerSyncTaskTrigger,
    val createdAtMs: Long,
    val baseUrl: String,
    val includeIndexSqlite: Boolean,
    val operations: List<SyncServerSyncTaskOp>,
    val run: SyncServerSyncTaskRun? = null,
)

data class SyncServerSyncTaskStoreState(
    val current: SyncServerSyncTask?,
    val history: List<SyncServerSyncTask>,
) {
    companion object {
        val EMPTY = SyncServerSyncTaskStoreState(current = null, history = emptyList())
    }
}

class SyncServerSyncTaskStore(
    private val repository: VaultRepository,
) {
    private val lock = Mutex()

    private val relativePath = ".zhixu/sync/server_tasks.json"
    private val mimeType = "application/json"
    private val maxHistory = 20

    suspend fun load(rootUri: Uri): SyncServerSyncTaskStoreState =
        lock.withLock {
            withContext(Dispatchers.IO) {
                val uri = repository.resolveVaultFileUri(rootUri, relativePath) ?: return@withContext SyncServerSyncTaskStoreState.EMPTY
                val raw = runCatching { repository.readText(uri) }.getOrNull().orEmpty().trim()
                if (raw.isBlank()) return@withContext SyncServerSyncTaskStoreState.EMPTY
                parseState(raw)
            }
        }

    suspend fun update(
        rootUri: Uri,
        transform: (SyncServerSyncTaskStoreState) -> SyncServerSyncTaskStoreState,
    ): SyncServerSyncTaskStoreState =
        lock.withLock {
            withContext(Dispatchers.IO) {
                val prev = runCatching { loadUnlocked(rootUri) }.getOrDefault(SyncServerSyncTaskStoreState.EMPTY)
                val next = transform(prev).normalize()
                saveUnlocked(rootUri, next)
                next
            }
        }

    private suspend fun loadUnlocked(rootUri: Uri): SyncServerSyncTaskStoreState {
        val uri = repository.resolveVaultFileUri(rootUri, relativePath) ?: return SyncServerSyncTaskStoreState.EMPTY
        val raw = runCatching { repository.readText(uri) }.getOrNull().orEmpty().trim()
        if (raw.isBlank()) return SyncServerSyncTaskStoreState.EMPTY
        return parseState(raw)
    }

    private suspend fun saveUnlocked(rootUri: Uri, state: SyncServerSyncTaskStoreState) {
        repository.ensureVaultStructure(rootUri)
        val uri = repository.ensureVaultFile(rootUri, relativePath, mimeType)
        repository.writeText(uri, serializeState(state))
    }

    private fun SyncServerSyncTaskStoreState.normalize(): SyncServerSyncTaskStoreState {
        val historyTrimmed = history.take(maxHistory)
        return copy(history = historyTrimmed)
    }

    private fun parseState(raw: String): SyncServerSyncTaskStoreState {
        val obj = runCatching { JSONObject(raw) }.getOrNull() ?: return SyncServerSyncTaskStoreState.EMPTY
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

        return SyncServerSyncTaskStoreState(current = current, history = history)
    }

    private fun serializeState(state: SyncServerSyncTaskStoreState): String {
        val obj = JSONObject().put("version", 1)
        if (state.current != null) obj.put("current", serializeTask(state.current))
        val historyArr = JSONArray()
        for (t in state.history.take(maxHistory)) historyArr.put(serializeTask(t))
        obj.put("history", historyArr)
        return obj.toString()
    }

    private fun parseTask(obj: JSONObject): SyncServerSyncTask {
        val id = obj.optString("id").orEmpty().trim().ifBlank { Ulid.next() }
        val trigger = SyncServerSyncTaskTrigger.valueOf(obj.optString("trigger").orEmpty().trim().ifBlank { SyncServerSyncTaskTrigger.MANUAL.name })
        val createdAtMs = obj.optLong("createdAtMs", 0L).coerceAtLeast(0L)
        val baseUrl = obj.optString("baseUrl").orEmpty()
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
                SyncServerSyncTaskRun(
                    startedAtMs = runObj.optLong("startedAtMs", 0L).coerceAtLeast(0L),
                    endedAtMs = runObj.optLong("endedAtMs", 0L).coerceAtLeast(0L),
                    summary = runObj.optJSONObject("summary")?.let { parseSummary(it) },
                    error = runObj.optString("error").orEmpty().trim().ifBlank { null },
                )
            }

        return SyncServerSyncTask(
            id = id,
            trigger = trigger,
            createdAtMs = createdAtMs,
            baseUrl = baseUrl,
            includeIndexSqlite = includeIndexSqlite,
            operations = ops,
            run = run,
        )
    }

    private fun serializeTask(task: SyncServerSyncTask): JSONObject {
        val obj =
            JSONObject()
                .put("id", task.id)
                .put("trigger", task.trigger.name)
                .put("createdAtMs", task.createdAtMs.coerceAtLeast(0L))
                .put("baseUrl", task.baseUrl)
                .put("includeIndexSqlite", task.includeIndexSqlite)
        val opsArr = JSONArray()
        for (op in task.operations) opsArr.put(serializeOp(op))
        obj.put("operations", opsArr)
        if (task.run != null) obj.put("run", serializeRun(task.run))
        return obj
    }

    private fun parseOp(obj: JSONObject): SyncServerSyncTaskOp? {
        val kindRaw = obj.optString("kind").orEmpty().trim().uppercase()
        val kind = WebDavPlannedOpKind.entries.firstOrNull { it.name == kindRaw } ?: return null
        val path = obj.optString("path").orEmpty().trim().trimStart('/').replace('\\', '/')
        if (path.isBlank()) return null
        val reason = obj.optString("reason").orEmpty()
        val stateRaw = obj.optString("state").orEmpty().trim().uppercase()
        val state = SyncServerSyncTaskOpState.entries.firstOrNull { it.name == stateRaw } ?: SyncServerSyncTaskOpState.PENDING
        val error = obj.optString("error").orEmpty().trim().ifBlank { null }
        return SyncServerSyncTaskOp(kind = kind, path = path, reason = reason, state = state, error = error)
    }

    private fun serializeOp(op: SyncServerSyncTaskOp): JSONObject {
        val obj =
            JSONObject()
                .put("kind", op.kind.name)
                .put("path", op.path)
                .put("reason", op.reason)
                .put("state", op.state.name)
        if (!op.error.isNullOrBlank()) obj.put("error", op.error.take(500))
        return obj
    }

    private fun parseSummary(obj: JSONObject): OfficialVaultSyncSummary =
        OfficialVaultSyncSummary(
            uploaded = obj.optInt("uploaded", 0).coerceAtLeast(0),
            downloaded = obj.optInt("downloaded", 0).coerceAtLeast(0),
            deletedRemote = obj.optInt("deletedRemote", 0).coerceAtLeast(0),
            deletedLocal = obj.optInt("deletedLocal", 0).coerceAtLeast(0),
            conflicts = obj.optInt("conflicts", 0).coerceAtLeast(0),
            failed = obj.optInt("failed", 0).coerceAtLeast(0),
        )

    private fun serializeRun(run: SyncServerSyncTaskRun): JSONObject {
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

class SyncServerSyncTaskManager(
    private val context: Context,
    private val repository: VaultRepository,
) {
    private val store = SyncServerSyncTaskStore(repository)

    suspend fun load(rootUri: Uri): SyncServerSyncTaskStoreState = store.load(rootUri)

    suspend fun generateTask(
        rootUri: Uri,
        baseUrl: String,
        token: String,
        includeIndexSqlite: Boolean,
        trigger: SyncServerSyncTaskTrigger,
    ): SyncServerSyncTask? {
        return store.update(rootUri) { state ->
            if (state.current != null) return@update state
            state
        }.current ?: run {
            val engine = OfficialVaultSyncEngine(context, repository)
            val plan = engine.planVault(rootUri, baseUrl, token, includeIndexSqlite)
            val taskId = Ulid.next()
            val createdAt = System.currentTimeMillis()
            val task =
                SyncServerSyncTask(
                    id = taskId,
                    trigger = trigger,
                    createdAtMs = createdAt,
                    baseUrl = baseUrl.trim(),
                    includeIndexSqlite = includeIndexSqlite,
                    operations =
                        plan.operations.map { op ->
                            SyncServerSyncTaskOp(
                                kind = op.kind,
                                path = op.path.trim().trimStart('/').replace('\\', '/'),
                                reason = op.reason,
                                state = SyncServerSyncTaskOpState.PENDING,
                                error = null,
                            )
                        },
                )
            store.update(rootUri) { s ->
                if (s.current != null) s else s.copy(current = task)
            }.current
        }
    }

    suspend fun discardCurrentTask(rootUri: Uri, reason: String = "discarded"): SyncServerSyncTaskStoreState =
        store.update(rootUri) { state ->
            val cur = state.current ?: return@update state
            val endedAt = System.currentTimeMillis()
            val archived =
                cur.copy(
                    run =
                        SyncServerSyncTaskRun(
                            startedAtMs = cur.run?.startedAtMs ?: endedAt,
                            endedAtMs = endedAt,
                            summary = cur.run?.summary,
                            error = reason,
                        ),
                )
            state.copy(current = null, history = listOf(archived) + state.history)
        }

    data class ExecuteResult(
        val updatedState: SyncServerSyncTaskStoreState,
        val ranTask: SyncServerSyncTask,
    )

    suspend fun executeCurrentTask(
        rootUri: Uri,
        baseUrl: String,
        token: String,
        includeIndexSqlite: Boolean,
    ): ExecuteResult {
        val initial = store.load(rootUri)
        val task = initial.current ?: error("No current task")

        val baseUrlNow = baseUrl.trim()
        if (task.baseUrl != baseUrlNow || task.includeIndexSqlite != includeIndexSqlite) {
            error("Sync server config changed; regenerate task")
        }

        val lease = SyncServerSyncRuntime.begin()
        try {
            val opsResolved =
                task.operations.map { op ->
                    if (op.state == SyncServerSyncTaskOpState.SKIPPED) return@map op
                    op.copy(state = SyncServerSyncTaskOpState.PENDING, error = null)
                }
            val toExecute = opsResolved.filter { it.state != SyncServerSyncTaskOpState.SKIPPED }
            val expected =
                toExecute.map { op ->
                    SyncServerPlannedOp(
                        kind = op.kind,
                        path = op.path,
                        reason = op.reason,
                    )
                }

            val engine = OfficialVaultSyncEngine(context, repository)
            val observed = ArrayList<SyncServerSyncObservedOpResult>()

            val startedAt = System.currentTimeMillis()
            store.update(rootUri) { s ->
                val cur = s.current ?: return@update s
                if (cur.id != task.id) return@update s
                s.copy(
                    current =
                        cur.copy(
                            operations = opsResolved,
                            run =
                                SyncServerSyncTaskRun(
                                    startedAtMs = startedAt,
                                    endedAtMs = 0L,
                                    summary = null,
                                    error = null,
                                ),
                        ),
                )
            }

            var summary: OfficialVaultSyncSummary?
            var errorText: String?
            try {
                summary =
                    engine.syncVaultWithExpectedPlan(
                        rootUri = rootUri,
                        baseUrl = baseUrlNow,
                        token = token,
                        includeIndexSqlite = includeIndexSqlite,
                        expectedOperations = expected,
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
                    if (op.state == SyncServerSyncTaskOpState.SKIPPED) return@map op
                    val key = "${op.kind.name}|${op.path}"
                    val res = resultsByKey[key]
                    when {
                        res != null && res.state == SyncServerSyncTaskOpState.DONE -> op.copy(state = SyncServerSyncTaskOpState.DONE, error = null)
                        res != null && res.state == SyncServerSyncTaskOpState.SKIPPED -> op.copy(state = SyncServerSyncTaskOpState.SKIPPED, error = res.error)
                        res != null -> op.copy(state = SyncServerSyncTaskOpState.FAILED, error = res.error)
                        !errorText.isNullOrBlank() -> op.copy(state = SyncServerSyncTaskOpState.FAILED, error = errorText)
                        else -> op
                    }
                }

            val finishedTask =
                task.copy(
                    operations = finalOps,
                    run =
                        SyncServerSyncTaskRun(
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
        } finally {
            lease.close()
        }
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
