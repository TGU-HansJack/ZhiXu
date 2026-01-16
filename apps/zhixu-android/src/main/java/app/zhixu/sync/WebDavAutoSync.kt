package app.zhixu.sync

import android.content.Context
import android.net.Uri
import app.zhixu.data.VaultRepository
import app.zhixu.data.WebDavConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

object WebDavAutoSync {
    private val lock = Mutex()
    private const val minIntervalMs: Long = 60_000
    private const val maxBackoffMs: Long = 15 * 60_000

    suspend fun maybeSyncVault(
        context: Context,
        repository: VaultRepository,
        vaultRootUri: Uri?,
        config: WebDavConfig,
        force: Boolean = false,
    ) {
        val root = vaultRootUri ?: return
        if (!config.enabled) return
        val baseUrl = config.baseUrl.trim()
        val remoteRoot = config.remoteRoot.trim().ifBlank { "/" }
        if (baseUrl.isBlank()) return
        if (!baseUrl.startsWith("http://") && !baseUrl.startsWith("https://")) return

        val key = "$baseUrl|$remoteRoot|$root"
        val now = System.currentTimeMillis()
        val stateStore = WebDavAutoSyncStateStore(context.applicationContext)
        val shouldRun =
            lock.withLock {
                val prev = stateStore.get(key)
                val failures = prev.consecutiveFailures.coerceIn(0, 6)
                val backoffMs = (minIntervalMs * (1L shl failures)).coerceAtMost(maxBackoffMs)
                val lastAttempt = prev.lastAttemptedAtMs
                if (!force && now - lastAttempt in 0..backoffMs) {
                    false
                } else {
                    stateStore.set(key, prev.copy(lastAttemptedAtMs = now))
                    true
                }
            }
        if (!shouldRun) return

        val result =
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

        lock.withLock {
            val prev = stateStore.get(key)
            if (result.isSuccess) {
                stateStore.set(
                    key,
                    prev.copy(
                        lastSucceededAtMs = now,
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
