package app.zhixu.sync

import android.content.Context
import android.net.Uri
import app.zhixu.data.VaultRepository
import app.zhixu.data.WebDavAutomationSettings
import app.zhixu.data.WebDavConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

object WebDavAutoSync {
    private val lock = Mutex()

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
        val baseUrl = config.baseUrl.trim()
        val remoteRoot = config.remoteRoot.trim().ifBlank { "/" }
        if (baseUrl.isBlank()) return
        if (!baseUrl.startsWith("http://") && !baseUrl.startsWith("https://")) return

        val key = "$baseUrl|$remoteRoot|$root"
        val intervalMs = (automation.intervalMinutes.toLong() * 60_000L).coerceAtLeast(60_000L)
        val retryCount = automation.retryCount.coerceAtLeast(0)
        val retryIntervalMs = (automation.retryIntervalSeconds.toLong() * 1000L).coerceAtLeast(1_000L)
        val attemptStartedAtMs = System.currentTimeMillis()
        val stateStore = WebDavAutoSyncStateStore(context.applicationContext)
        val shouldRun =
            lock.withLock {
                val prev = stateStore.get(key)
                val lastAttempt = prev.lastAttemptedAtMs
                if (!force && attemptStartedAtMs - lastAttempt in 0..intervalMs) {
                    false
                } else {
                    stateStore.set(key, prev.copy(lastAttemptedAtMs = attemptStartedAtMs))
                    true
                }
            }
        if (!shouldRun) return

        val result = runCatching {
            var lastError: Throwable? = null
            for (attempt in 0..retryCount) {
                val attemptResult =
                    runCatching {
                        withContext(Dispatchers.IO) {
                            WebDavSyncEngine(context, repository).syncVault(
                                rootUri = root,
                                config =
                                    config.copy(
                                        enabled = true,
                                        baseUrl = baseUrl,
                                        username = config.username.trim(),
                                        remoteRoot = remoteRoot,
                                    ),
                            )
                        }
                    }
                attemptResult.onSuccess { return@runCatching it }
                lastError = attemptResult.exceptionOrNull()
                if (attempt < retryCount) delay(retryIntervalMs)
            }
            throw lastError ?: IllegalStateException("Sync failed")
        }
        val attemptEndedAtMs = System.currentTimeMillis()

        lock.withLock {
            val prev = stateStore.get(key)
            if (result.isSuccess) {
                stateStore.set(
                    key,
                    prev.copy(
                        lastSucceededAtMs = attemptEndedAtMs,
                        consecutiveFailures = 0,
                        lastError = null,
                    ),
                )
            } else {
                val e = result.exceptionOrNull()
                stateStore.set(
                    key,
                    prev.copy(
                        consecutiveFailures = (prev.consecutiveFailures + 1).coerceAtMost(20),
                        lastError = (e?.message ?: e?.javaClass?.simpleName).orEmpty().ifBlank { "Sync failed" },
                    ),
                )
            }
        }

        result.getOrThrow()
    }
}
