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
    private val lastSyncedAt = HashMap<String, Long>()
    private const val minIntervalMs: Long = 60_000

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
        if (baseUrl.isBlank() || config.username.trim().isBlank() || config.password.isBlank()) return

        val key = "$baseUrl|$remoteRoot|$root"
        val now = System.currentTimeMillis()
        val shouldRun =
            lock.withLock {
                val last = lastSyncedAt[key] ?: 0L
                if (!force && now - last in 0..minIntervalMs) {
                    false
                } else {
                    lastSyncedAt[key] = now
                    true
                }
            }
        if (!shouldRun) return

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
}

