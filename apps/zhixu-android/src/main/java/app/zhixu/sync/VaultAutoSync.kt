package app.zhixu.sync

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import app.zhixu.data.SyncPreferences
import app.zhixu.data.VaultRepository
import app.zhixu.data.vaultRootToDocumentFile
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
        // New vault layout: Inbox.md at vault root. Keep best-effort backwards compat.
        val paths = listOf("Inbox.md", "docs/Inbox.md")
        for (p in paths) {
            if (repository.resolveVaultFileUri(root, p) != null) {
                maybeUploadPath(context, repository, root, p, force = force)
                return
            }
        }
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
                resolveDeleteConflictV2(context, repository, root, auth.baseUrl, auth.token, relPath, baseRev)
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
                val key = "${auth.baseUrl}|${vaultRootUri}|$relativePath"
                val last = lastSyncedAt[key] ?: 0L
                if (!force && now - last in 0..minIntervalMs) {
                    false
                } else {
                    lastSyncedAt[key] = now
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
                resolveUploadConflictV2(
                    context = context,
                    repository = repository,
                    rootUri = vaultRootUri,
                    baseUrl = auth.baseUrl,
                    token = auth.token,
                    path = relativePath,
                    localBytes = bytes,
                    localMtimeMs = mtimeMs,
                )
            }
        }
    }

    private suspend fun resolveUploadConflictV2(
        context: Context,
        repository: VaultRepository,
        rootUri: Uri,
        baseUrl: String,
        token: String,
        path: String,
        localBytes: ByteArray,
        localMtimeMs: Long,
    ) {
        repository.ensureVaultStructure(rootUri)
        val root = vaultRootToDocumentFile(context, rootUri) ?: return

        val store = OfficialVaultSyncStateStore(context, repository)
        var state = store.load(rootUri)
        val st = state.files[path]

        val remote = SyncServerClient.downloadVaultFileV2(baseUrl, token, path).value ?: return

        suspend fun ensureConflictArtifact(kind: String, bytes: ByteArray) {
            if (bytes.isEmpty()) return
            val safePath = path.trimStart('/').replace('\\', '/')
            val now = System.currentTimeMillis()
            val baseDir = ".zhixu/conflicts/$safePath"
            val name = "$now-$kind"
            val dest = ensureLocalFile(root, "$baseDir/$name") ?: return
            repository.writeBytes(dest.uri, bytes)
        }

        ensureConflictArtifact("remote-r${remote.rev}", remote.bytes)
        ensureConflictArtifact("local", localBytes)

        // Without server-side history, resolve by keeping both:
        // - keep remote under .zhixu/conflicts/
        // - upload local to remote (local wins)
        state = state.withUploaded(path, rev = remote.rev, sha256 = "", size = localBytes.size.toLong(), localMtimeMs = localMtimeMs)
        store.save(rootUri, state)

        val put =
            SyncServerClient.uploadVaultFileV2(
                baseUrl = baseUrl,
                token = token,
                path = path,
                mtimeMs = localMtimeMs.takeIf { it > 0L } ?: System.currentTimeMillis(),
                bytes = localBytes,
                baseRev = remote.rev,
            )
        if (put.ok && put.value != null) {
            val dest = ensureLocalFile(root, path) ?: return
            repository.writeBytes(dest.uri, localBytes)
            val finalMtime = repository.getDocumentLastModified(dest.uri).takeIf { it > 0L } ?: localMtimeMs
            state = state.withUploaded(path, rev = put.value.rev, sha256 = put.value.sha256, size = localBytes.size.toLong(), localMtimeMs = finalMtime)
            store.save(rootUri, state)
            return
        }

        // Fallback: accept remote as source of truth.
        val dest = ensureLocalFile(root, path) ?: return
        repository.writeBytes(dest.uri, remote.bytes)
        val finalMtime = repository.getDocumentLastModified(dest.uri).takeIf { it > 0L } ?: System.currentTimeMillis()
        state = state.withUploaded(path, rev = remote.rev, sha256 = remote.sha256, size = remote.bytes.size.toLong(), localMtimeMs = finalMtime)
        store.save(rootUri, state)
    }

    private suspend fun resolveDeleteConflictV2(
        context: Context,
        repository: VaultRepository,
        rootUri: Uri,
        baseUrl: String,
        token: String,
        path: String,
        baseRev: Long,
    ) {
        repository.ensureVaultStructure(rootUri)
        val root = vaultRootToDocumentFile(context, rootUri) ?: return

        suspend fun ensureConflictArtifact(kind: String, bytes: ByteArray) {
            if (bytes.isEmpty()) return
            val safePath = path.trimStart('/').replace('\\', '/')
            val now = System.currentTimeMillis()
            val baseDir = ".zhixu/conflicts/$safePath"
            val name = "$now-$kind"
            val dest = ensureLocalFile(root, "$baseDir/$name") ?: return
            repository.writeBytes(dest.uri, bytes)
        }

        val remote = SyncServerClient.downloadVaultFileV2(baseUrl, token, path).value
        if (remote != null) {
            ensureConflictArtifact("remote_before_delete", remote.bytes)
            val store = OfficialVaultSyncStateStore(context, repository)
            val state = store.load(rootUri)
            val r = SyncServerClient.deleteVaultFileV2(baseUrl, token, path, baseRev = remote.rev)
            if (r.ok) {
                store.save(rootUri, state.withDeleted(path, r.value?.rev ?: (remote.rev + 1)))
                return
            }
        }

        // Worst case: fallback to full sync to resolve via conflict artifacts.
        val includeIndexSqlite = SyncPreferences(context.applicationContext).includeIndexSqlite.first()
        OfficialVaultSyncEngine(context, repository).syncVault(rootUri, baseUrl, token, includeIndexSqlite)
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
