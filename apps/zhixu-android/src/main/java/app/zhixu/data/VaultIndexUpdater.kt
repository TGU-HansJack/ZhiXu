package app.zhixu.data

import android.content.Context
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.DocumentsContract
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.LinkedHashSet

class VaultIndexUpdater(
    context: Context,
    private val repository: VaultRepository,
) {
    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    @Volatile private var canRunHeavyWork: Boolean = true
    @Volatile private var vaultRootUri: Uri? = null
    @Volatile private var docsDirUri: Uri? = null
    @Volatile private var pendingObserverChange: Boolean = false
    @Volatile private var pendingSyncChanges: Boolean = false
    @Volatile private var lastIndexSignalUptimeMs: Long = 0L

    private var observer: ContentObserver? = null
    private var registerJob: Job? = null
    private var pollJob: Job? = null
    private var refreshJob: Job? = null
    private var scheduleJob: Job? = null
    private var coldStartJob: Job? = null
    private var syncProcessJob: Job? = null

    private val _isUpdating = MutableStateFlow(false)
    val isUpdating: StateFlow<Boolean> = _isUpdating.asStateFlow()

    private data class PendingSyncChange(
        val rootUri: Uri,
        val upserts: LinkedHashSet<String> = LinkedHashSet(),
        val deletes: LinkedHashSet<String> = LinkedHashSet(),
    )

    private val pendingSyncLock = Any()
    private val pendingSyncByRoot = LinkedHashMap<String, PendingSyncChange>()

    init {
        scope.launch {
            repository.indexChanges.collect {
                lastIndexSignalUptimeMs = SystemClock.uptimeMillis()
            }
        }

        // Sync engines should only report "local filesystem changed" events; indexing is coordinated here.
        scope.launch {
            repository.fileChanges.collect { batch ->
                val currentRoot = vaultRootUri ?: return@collect
                if (batch.rootUri.toString() != currentRoot.toString()) return@collect

                synchronized(pendingSyncLock) {
                    val key = batch.rootUri.toString()
                    val pending =
                        pendingSyncByRoot.getOrPut(key) {
                            PendingSyncChange(rootUri = batch.rootUri)
                        }
                    pending.upserts.addAll(batch.upsertPaths)
                    pending.deletes.addAll(batch.deletePaths)
                }
                scheduleProcessSyncChanges(debounceMs = 350)
            }
        }
    }

    fun setCanRunHeavyWork(canRun: Boolean) {
        val prev = canRunHeavyWork
        canRunHeavyWork = canRun
        if (!prev && canRun && pendingObserverChange) {
            pendingObserverChange = false
            scheduleRefresh(force = false, debounceMs = 600)
        }
        if (!prev && canRun && pendingSyncChanges) {
            pendingSyncChanges = false
            scheduleProcessSyncChanges(debounceMs = 250)
        }
    }

    fun setVaultRootUri(root: Uri?) {
        if (root?.toString() == vaultRootUri?.toString()) return
        stop()
        vaultRootUri = root
        if (root == null) return
        start(root)
    }

    fun requestForceRefresh() {
        scheduleRefresh(force = true, debounceMs = 0)
    }

    fun requestRefresh() {
        scheduleRefresh(force = false, debounceMs = 0)
    }

    private fun start(root: Uri) {
        coldStartJob =
            scope.launch {
                // Avoid blocking docs UI on cold start: build in background after first UI settles.
                delay(800)
                while (!canRunHeavyWork) delay(300)
                val has = withContext(Dispatchers.IO) { repository.hasAnyIndexedDocs() }
                if (!has) scheduleRefresh(force = true, debounceMs = 0)
            }

        scope.launch {
            val dirUri = withContext(Dispatchers.IO) { repository.getDocsDirUri(root) }
            docsDirUri = dirUri
            if (dirUri == null) return@launch
            registerObserver(dirUri)
        }
    }

    private fun stop() {
        coldStartJob?.cancel()
        coldStartJob = null
        scheduleJob?.cancel()
        scheduleJob = null
        refreshJob?.cancel()
        refreshJob = null
        pollJob?.cancel()
        pollJob = null
        registerJob?.cancel()
        registerJob = null
        syncProcessJob?.cancel()
        syncProcessJob = null
        synchronized(pendingSyncLock) { pendingSyncByRoot.clear() }
        pendingSyncChanges = false

        val resolver = appContext.contentResolver
        val existing = observer
        if (existing != null) {
            observer = null
            scope.launch(Dispatchers.IO) { runCatching { resolver.unregisterContentObserver(existing) } }
        }
        docsDirUri = null
    }

    private fun scheduleProcessSyncChanges(debounceMs: Long) {
        syncProcessJob?.cancel()
        syncProcessJob =
            scope.launch {
                if (debounceMs > 0) delay(debounceMs)
                processPendingSyncChanges()
            }
    }

    private fun allAncestorDirs(path: String): Set<String> {
        val cleaned = path.trim().trimStart('/').replace('\\', '/')
        val out = LinkedHashSet<String>()
        out.add("") // Always refresh root so newly-created intermediate directories can appear.
        var idx = cleaned.indexOf('/')
        while (idx >= 0) {
            out.add(cleaned.substring(0, idx + 1))
            idx = cleaned.indexOf('/', idx + 1)
        }
        return out
    }

    private suspend fun processPendingSyncChanges() {
        if (!canRunHeavyWork) {
            pendingSyncChanges = true
            return
        }

        // Avoid running incremental sync indexing concurrently with a full rebuild.
        if (refreshJob?.isActive == true) {
            pendingSyncChanges = true
            scheduleProcessSyncChanges(debounceMs = 800)
            return
        }

        val pending: List<PendingSyncChange> =
            synchronized(pendingSyncLock) {
                if (pendingSyncByRoot.isEmpty()) return
                val out = pendingSyncByRoot.values.toList()
                pendingSyncByRoot.clear()
                out
            }

        // If we have explicit sync change events, prefer handling them incrementally and avoid
        // triggering an extra full rebuild that may have been scheduled by filesystem observers.
        scheduleJob?.cancel()
        scheduleJob = null
        pendingObserverChange = false

        val startMs = SystemClock.uptimeMillis()
        try {
            _isUpdating.value = true
            var needsFallbackRebuildAny = false
            withContext(Dispatchers.IO) {
                for (entry in pending) {
                    val root = entry.rootUri

                    // Ensure any later rebuild uses a fresh filesystem listing.
                    repository.invalidateDocListCache(root)

                    // Incremental index maintenance: delete first, then upsert.
                    for (path in entry.deletes) {
                        repository.deleteIndexedVaultFileByRelativePath(rootUri = root, relativePath = path)
                    }

                    var needsFallbackRebuild = false
                    for (path in entry.upserts) {
                        val ok = repository.indexVaultFileByRelativePath(rootUri = root, relativePath = path)
                        if (!ok) {
                            // If we couldn't resolve/index an expected doc path, fall back to a full rebuild as a safety net.
                            val cleaned = path.trim().trimStart('/').replace('\\', '/')
                            val lower = cleaned.lowercase()
                            val isInternal = lower == ".zhixu" || lower.startsWith(".zhixu/")
                            val isIndexable = lower.endsWith(".md") || (lower.endsWith(".zhixu") && lower.length > ".zhixu".length)
                            if (!isInternal && isIndexable) needsFallbackRebuild = true
                        }
                    }

                    // Keep the directory tree cache roughly in-sync (best-effort, directory-local refresh only).
                    val dirsToRefresh = LinkedHashSet<String>()
                    for (path in entry.upserts) dirsToRefresh.addAll(allAncestorDirs(path))
                    for (path in entry.deletes) dirsToRefresh.addAll(allAncestorDirs(path))
                    for (dir in dirsToRefresh) {
                        repository.refreshDirIndexForDirectory(
                            rootUri = root,
                            parentRelativePath = dir.takeIf { it.isNotBlank() },
                        )
                    }

                    if (needsFallbackRebuild) needsFallbackRebuildAny = true
                }
            }
            if (needsFallbackRebuildAny) {
                // Best-effort safety net: if we couldn't resolve/index some expected paths, do a full rebuild.
                scheduleRefresh(force = false, debounceMs = 0)
            }
        } finally {
            _isUpdating.value = false
            val elapsed = SystemClock.uptimeMillis() - startMs
            if (elapsed < 250) delay(250 - elapsed)
        }
    }

    private fun registerObserver(dirUri: Uri) {
        val docId = runCatching { DocumentsContract.getDocumentId(dirUri) }.getOrNull() ?: return
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(dirUri, docId)
        val resolver = appContext.contentResolver
        val obs =
            object : ContentObserver(Handler(Looper.getMainLooper())) {
                override fun onChange(selfChange: Boolean) {
                    val root = vaultRootUri ?: return
                    val now = SystemClock.uptimeMillis()

                    // Always invalidate the cached doc listing on any observed filesystem change.
                    // Even if we decide to skip scheduling a rebuild (e.g., self-triggered updates),
                    // we still want subsequent rebuilds to re-scan the directory and avoid stale paths.
                    repository.invalidateDocListCache(root)

                    val recentIndexSignal = now - lastIndexSignalUptimeMs
                    if (recentIndexSignal in 0..1_500L) return
                    if (!canRunHeavyWork) {
                        pendingObserverChange = true
                        return
                    }
                    scheduleRefresh(force = false, debounceMs = 600)
                }

                override fun onChange(selfChange: Boolean, uri: Uri?) {
                    onChange(selfChange)
                }
            }
        observer = obs

        registerJob?.cancel()
        registerJob =
            scope.launch(Dispatchers.IO) {
                val ok = runCatching { resolver.registerContentObserver(childrenUri, true, obs) }.isSuccess
                if (!ok) startPollingFallback(dirUri)
            }
    }

    private fun startPollingFallback(dirUri: Uri) {
        pollJob?.cancel()
        pollJob =
            scope.launch {
                val root = vaultRootUri ?: return@launch
                var lastDirModified = withContext(Dispatchers.IO) { repository.getDocumentLastModified(dirUri) }
                var ticks = 0
                while (true) {
                    delay(30_000)
                    ticks++
                    val modified = withContext(Dispatchers.IO) { repository.getDocumentLastModified(dirUri) }
                    if (modified > 0L && modified != lastDirModified) {
                        lastDirModified = modified
                        repository.invalidateDocListCache(root)
                        scheduleRefresh(force = false, debounceMs = 200)
                        continue
                    }

                    if (modified <= 0L && ticks % 10 == 0) {
                        repository.invalidateDocListCache(root)
                        scheduleRefresh(force = true, debounceMs = 0)
                    }
                }
            }
    }

    private fun scheduleRefresh(force: Boolean, debounceMs: Long) {
        scheduleJob?.cancel()
        scheduleJob =
            scope.launch {
                if (debounceMs > 0) delay(debounceMs)
                requestRefresh(force)
            }
    }

    private fun requestRefresh(force: Boolean) {
        val root = vaultRootUri ?: return
        if (!canRunHeavyWork) {
            scheduleRefresh(force = force, debounceMs = 500)
            return
        }
        refreshJob?.cancel()
        refreshJob =
            scope.launch {
                val startMs = SystemClock.uptimeMillis()
                try {
                    _isUpdating.value = true
                    withContext(Dispatchers.IO) { repository.rebuildIndex(rootUri = root, forceScan = force) }
                } finally {
                    _isUpdating.value = false
                    val elapsed = SystemClock.uptimeMillis() - startMs
                    if (elapsed < 250) delay(250 - elapsed)
                }
            }
    }
}
