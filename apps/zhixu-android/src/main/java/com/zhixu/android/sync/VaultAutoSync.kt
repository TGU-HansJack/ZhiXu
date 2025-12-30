package com.zhixu.android.sync

import android.content.Context
import android.net.Uri
import com.zhixu.android.data.SyncPreferences
import com.zhixu.android.data.VaultRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

object VaultAutoSync {
    private val lock = Mutex()
    private val lastSyncedAt = HashMap<String, Long>()
    private const val minIntervalMs: Long = 3_000

    private val lastFullSyncedAt = HashMap<String, Long>()
    private const val minFullSyncIntervalMs: Long = 30_000

    suspend fun maybeUploadDoc(
        context: Context,
        repository: VaultRepository,
        vaultRootUri: Uri?,
        docUri: Uri,
        force: Boolean = false,
    ) {
        val root = vaultRootUri ?: return
        val relPath = repository.computeRelativePath(root, docUri) ?: return
        maybeUploadPath(context, repository, root, relPath, force = force)
    }

    suspend fun maybeUploadInbox(
        context: Context,
        repository: VaultRepository,
        vaultRootUri: Uri?,
        force: Boolean = false,
    ) {
        val root = vaultRootUri ?: return
        maybeUploadPath(context, repository, root, "docs/Inbox.md", force = force)
    }

    suspend fun maybeDeleteDoc(
        context: Context,
        repository: VaultRepository,
        vaultRootUri: Uri?,
        docUri: Uri,
    ) {
        val root = vaultRootUri ?: return
        val relPath = repository.computeRelativePath(root, docUri) ?: return
        val auth = resolveSyncServerAuth(context) ?: return
        withContext(Dispatchers.IO) {
            val state = OfficialVaultSyncStateStore(context, repository).load(root)
            val baseRev = state.files[relPath]?.baseRev ?: 0L
            val r = SyncServerClient.deleteVaultFileV2(auth.baseUrl, auth.token, relPath, baseRev = baseRev)
            if (r.ok) {
                val next = state.withDeleted(relPath, r.value?.rev ?: (baseRev + 1))
                OfficialVaultSyncStateStore(context, repository).save(root, next)
            } else if (r.statusCode == 409) {
                // Fallback to full sync to resolve conflicts.
                val includeIndexSqlite = SyncPreferences(context.applicationContext).includeIndexSqlite.first()
                OfficialVaultSyncEngine(context, repository).syncVault(root, auth.baseUrl, auth.token, includeIndexSqlite)
            }
        }
    }

    suspend fun maybeSyncVault(
        context: Context,
        repository: VaultRepository,
        vaultRootUri: Uri?,
        force: Boolean = false,
    ) {
        val root = vaultRootUri ?: return
        val auth = resolveSyncServerAuth(context) ?: return
        val includeIndexSqlite = SyncPreferences(context.applicationContext).includeIndexSqlite.first()

        val key = "${auth.baseUrl}|${root}"
        val now = System.currentTimeMillis()
        val shouldRun =
            lock.withLock {
                val last = lastFullSyncedAt[key] ?: 0L
                if (!force && now - last in 0..minFullSyncIntervalMs) {
                    false
                } else {
                    lastFullSyncedAt[key] = now
                    true
                }
            }
        if (!shouldRun) return

        withContext(Dispatchers.IO) {
            OfficialVaultSyncEngine(context, repository).syncVault(
                rootUri = root,
                baseUrl = auth.baseUrl,
                token = auth.token,
                includeIndexSqlite = includeIndexSqlite,
            )
        }
    }

    private suspend fun maybeUploadPath(
        context: Context,
        repository: VaultRepository,
        vaultRootUri: Uri,
        relativePath: String,
        force: Boolean,
    ) {
        val auth = resolveSyncServerAuth(context) ?: return
        val now = System.currentTimeMillis()
        val shouldRun =
            lock.withLock {
                val last = lastSyncedAt[relativePath] ?: 0L
                if (!force && now - last in 0..minIntervalMs) {
                    false
                } else {
                    lastSyncedAt[relativePath] = now
                    true
                }
            }
        if (!shouldRun) return

        val fileUri = repository.resolveVaultFileUri(vaultRootUri, relativePath) ?: return
        val bytes = withContext(Dispatchers.IO) { repository.readBytes(fileUri) } ?: return
        val mtimeMs = repository.getDocumentLastModified(fileUri).takeIf { it > 0 } ?: now
        withContext(Dispatchers.IO) {
            val store = OfficialVaultSyncStateStore(context, repository)
            val state = store.load(vaultRootUri)
            val baseRev = state.files[relativePath]?.baseRev ?: 0L
            val r = SyncServerClient.uploadVaultFileV2(auth.baseUrl, auth.token, relativePath, mtimeMs, bytes, baseRev = baseRev)
            if (r.ok) {
                val rev = r.value?.rev ?: (baseRev + 1)
                val sha = r.value?.sha256.orEmpty()
                val next = state.withUploaded(relativePath, rev = rev, sha256 = sha, size = bytes.size.toLong(), localMtimeMs = mtimeMs)
                store.save(vaultRootUri, next)
            } else if (r.statusCode == 409) {
                val includeIndexSqlite = SyncPreferences(context.applicationContext).includeIndexSqlite.first()
                OfficialVaultSyncEngine(context, repository).syncVault(vaultRootUri, auth.baseUrl, auth.token, includeIndexSqlite)
            }
        }
    }
}
