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
    @Volatile private var lastIndexSignalUptimeMs: Long = 0L

    private var observer: ContentObserver? = null
    private var registerJob: Job? = null
    private var pollJob: Job? = null
    private var refreshJob: Job? = null
    private var scheduleJob: Job? = null
    private var coldStartJob: Job? = null

    private val _isUpdating = MutableStateFlow(false)
    val isUpdating: StateFlow<Boolean> = _isUpdating.asStateFlow()

    init {
        scope.launch {
            repository.indexChanges.collect {
                lastIndexSignalUptimeMs = SystemClock.uptimeMillis()
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

        val resolver = appContext.contentResolver
        val existing = observer
        if (existing != null) {
            observer = null
            scope.launch(Dispatchers.IO) { runCatching { resolver.unregisterContentObserver(existing) } }
        }
        docsDirUri = null
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
                    val recentIndexSignal = now - lastIndexSignalUptimeMs
                    if (recentIndexSignal in 0..1_500L) return

                    repository.invalidateDocListCache(root)
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
