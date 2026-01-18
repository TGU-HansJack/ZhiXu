package app.zhixu.sync

import android.content.Context
import android.net.Uri
import android.net.ConnectivityManager
import app.zhixu.data.SyncPreferences
import app.zhixu.data.VaultRepository
import app.zhixu.data.WebDavAutomationSettings
import app.zhixu.data.WebDavConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

private class AutoSyncBlockedByConflicts : IllegalStateException("Sync blocked by conflicts")

object WebDavAutoSync {
    private val lock = Mutex()
    private const val AUTO_SYNC_PAUSE_AFTER_FAILURES = 10
    private const val MIN_BACKOFF_MS = 5L * 60_000L
    private const val MID_BACKOFF_MS = 15L * 60_000L
    private const val MAX_BACKOFF_MS = 60L * 60_000L

    suspend fun maybeSyncVault(
        context: Context,
        repository: VaultRepository,
        vaultRootUri: Uri?,
        config: WebDavConfig,
        automation: WebDavAutomationSettings = WebDavAutomationSettings.DEFAULT,
        force: Boolean = false,
    ) {
        val root = vaultRootUri ?: return
        if (!config.enabled) return
        val syncPrefs = SyncPreferences(context.applicationContext)
        val remoteRootConfirmed = runCatching { syncPrefs.webDavRemoteRootConfirmed.first() }.getOrDefault(false)
        if (!remoteRootConfirmed) {
            // If auto-sync is enabled but the sync target isn't explicitly chosen, force it off.
            runCatching { syncPrefs.setWebDavAutoSyncEnabled(false) }
            return
        }
        val baseUrl = config.baseUrl.trim()
        val remoteRoot = config.remoteRoot.trim().ifBlank { "/" }
        if (baseUrl.isBlank()) return
        if (!baseUrl.startsWith("http://") && !baseUrl.startsWith("https://")) return

        val key = "$baseUrl|$remoteRoot|$root"
        val userIntervalMs = (automation.intervalMinutes.toLong() * 60_000L).coerceAtLeast(60_000L)
        val cm = context.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        if (cm?.activeNetwork == null) return
        // Network-aware throttling: be more conservative on metered networks.
        val networkMinIntervalMs = if (cm.isActiveNetworkMetered) MAX_BACKOFF_MS else 0L
        val intervalMs = maxOf(userIntervalMs, networkMinIntervalMs)
        val retryCount = automation.retryCount.coerceAtLeast(0)
        val retryIntervalMs = (automation.retryIntervalSeconds.toLong() * 1000L).coerceAtLeast(1_000L)
        val attemptStartedAtMs = System.currentTimeMillis()
        val stateStore = WebDavAutoSyncStateStore(context.applicationContext)
        val shouldRun =
            lock.withLock {
                val prev = stateStore.get(key)
                val lastAttempt = prev.lastAttemptedAtMs
                if (!force && prev.consecutiveFailures >= AUTO_SYNC_PAUSE_AFTER_FAILURES) return@withLock false

                val backoffMs =
                    when (prev.consecutiveFailures) {
                        0 -> 0L
                        1 -> MIN_BACKOFF_MS
                        2 -> MID_BACKOFF_MS
                        else -> MAX_BACKOFF_MS
                    }
                val effectiveIntervalMs = maxOf(intervalMs, backoffMs)
                if (!force && attemptStartedAtMs - lastAttempt in 0..effectiveIntervalMs) return@withLock false

                stateStore.set(key, prev.copy(lastAttemptedAtMs = attemptStartedAtMs))
                true
            }
        if (!shouldRun) return

        val normalizedConfig =
            config.copy(
                enabled = true,
                baseUrl = baseUrl,
                username = config.username.trim(),
                remoteRoot = remoteRoot,
            )

        val manager = WebDavSyncTaskManager(context, repository)

        // Respect the single-current-task rule: auto-sync never overwrites/refreshes an existing task.
        val existingTask = runCatching { manager.load(root).current }.getOrNull()
        if (existingTask != null) return

        var success = false
        var blockedByConflicts = false
        var lastError: Throwable? = null

        for (attempt in 0..retryCount) {
            val attemptResult =
                runCatching {
                    withContext(Dispatchers.IO) {
                        manager.generateTask(
                            rootUri = root,
                            config = normalizedConfig,
                            trigger = WebDavSyncTaskTrigger.AUTO,
                            onlyPaths = null,
                        )
                        val current = manager.load(root).current ?: return@withContext
                        val hasUnresolvedConflicts =
                            current.operations.any { op ->
                                if (op.kind != WebDavPlannedOpKind.CONFLICT) return@any false
                                val effective = op.resolution ?: normalizedConfig.conflictStrategy
                                effective == app.zhixu.data.WebDavConflictStrategy.ASK_EACH_TIME
                            }
                        if (hasUnresolvedConflicts) {
                            throw AutoSyncBlockedByConflicts()
                        }
                        val ran = manager.executeCurrentTask(root, normalizedConfig).ranTask
                        val ok = ran.run?.error.isNullOrBlank() && ran.operations.none { it.state == WebDavSyncTaskOpState.FAILED }
                        if (!ok) {
                            throw IllegalStateException(ran.run?.error ?: "Sync failed")
                        }
                    }
                }

            if (attemptResult.isSuccess) {
                success = true
                break
            }

            lastError = attemptResult.exceptionOrNull()
            if (lastError is AutoSyncBlockedByConflicts) {
                blockedByConflicts = true
                break
            }
            if (attempt < retryCount) delay(retryIntervalMs)
        }
        val attemptEndedAtMs = System.currentTimeMillis()

        var disableAutoSync = false
        lock.withLock {
            val prev = stateStore.get(key)
            if (success) {
                stateStore.set(
                    key,
                    prev.copy(
                        lastSucceededAtMs = attemptEndedAtMs,
                        consecutiveFailures = 0,
                        lastError = null,
                    ),
                )
            } else {
                if (blockedByConflicts) {
                    // Conflicts should surface to the user via the task; avoid treating it as a failure storm.
                    val e = lastError ?: AutoSyncBlockedByConflicts()
                    stateStore.set(
                        key,
                        prev.copy(
                            lastError = (e.message ?: e.javaClass.simpleName).orEmpty().ifBlank { "Sync blocked by conflicts" },
                        ),
                    )
                } else {
                    val e = lastError ?: IllegalStateException("Sync failed")
                    val nextFailures = (prev.consecutiveFailures + 1).coerceAtMost(20)
                    if (prev.consecutiveFailures < AUTO_SYNC_PAUSE_AFTER_FAILURES && nextFailures >= AUTO_SYNC_PAUSE_AFTER_FAILURES) {
                        disableAutoSync = true
                    }
                    stateStore.set(
                        key,
                        prev.copy(
                            consecutiveFailures = nextFailures,
                            lastError =
                                if (disableAutoSync) {
                                    "Auto-sync paused after $nextFailures consecutive failures"
                                } else {
                                    (e.message ?: e.javaClass.simpleName).orEmpty().ifBlank { "Sync failed" }
                                },
                        ),
                    )
                }
            }
        }

        if (disableAutoSync) {
            runCatching { syncPrefs.setWebDavAutoSyncEnabled(false) }
        }

        if (blockedByConflicts) return
        if (!success) throw (lastError ?: IllegalStateException("Sync failed"))
    }
}
