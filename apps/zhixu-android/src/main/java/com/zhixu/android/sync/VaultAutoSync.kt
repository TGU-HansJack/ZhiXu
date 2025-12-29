package com.zhixu.android.sync

import android.content.Context
import android.net.Uri
import com.zhixu.android.data.AccountPreferences
import com.zhixu.android.data.ThirdPartyServiceConfig
import com.zhixu.android.data.ThirdPartyAuthPreferences
import com.zhixu.android.data.VaultRepository
import com.zhixu.android.data.VaultStorageLocation
import com.zhixu.android.data.VaultSyncPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

object VaultAutoSync {
    private val lock = Mutex()
    private val lastSyncedAt = HashMap<String, Long>()
    private const val minIntervalMs: Long = 3_000

    suspend fun maybeUploadDoc(
        context: Context,
        repository: VaultRepository,
        vaultRootUri: Uri?,
        docUri: Uri,
        force: Boolean = false,
    ) {
        val root = vaultRootUri ?: return
        val relPath = computeRelativePath(root, docUri) ?: return
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
        vaultRootUri: Uri?,
        docUri: Uri,
    ) {
        val root = vaultRootUri ?: return
        val relPath = computeRelativePath(root, docUri) ?: return
        val auth = resolveAuth(context) ?: return
        withContext(Dispatchers.IO) {
            SyncServerClient.deleteVaultFile(auth.baseUrl, auth.token, relPath)
        }
    }

    private suspend fun maybeUploadPath(
        context: Context,
        repository: VaultRepository,
        vaultRootUri: Uri,
        relativePath: String,
        force: Boolean,
    ) {
        val auth = resolveAuth(context) ?: return
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

        val fileUri = resolveVaultFileUri(vaultRootUri, relativePath) ?: return
        val bytes = withContext(Dispatchers.IO) { repository.readBytes(fileUri) } ?: return
        val mtimeMs = computeMtimeMs(fileUri) ?: now
        withContext(Dispatchers.IO) {
            SyncServerClient.uploadVaultFile(auth.baseUrl, auth.token, relativePath, mtimeMs, bytes)
        }
    }

    private data class ServerAuth(val baseUrl: String, val token: String)

    private suspend fun resolveAuth(context: Context): ServerAuth? {
        val appContext = context.applicationContext
        val vaultSyncPrefs = VaultSyncPreferences(appContext)
        val config = vaultSyncPrefs.config.first()

        return when (config.location) {
            VaultStorageLocation.LOCAL -> null
            VaultStorageLocation.OFFICIAL_SERVER -> {
                val account = AccountPreferences(appContext).state.first()
                val token = account.token
                if (token.isBlank()) return null
                ServerAuth(baseUrl = OfficialSync.BASE_URL, token = token)
            }
            VaultStorageLocation.THIRD_PARTY_SERVICE -> {
                resolveThirdPartyAuth(appContext, config.thirdParty)
            }
        }
    }

    private suspend fun resolveThirdPartyAuth(context: Context, cfg: ThirdPartyServiceConfig): ServerAuth? {
        val baseUrl = cfg.url.trim()
        val username = cfg.username.trim()
        val password = cfg.password
        if (baseUrl.isBlank() || username.isBlank() || password.isBlank()) return null

        val prefs = ThirdPartyAuthPreferences(context)
        val cached = prefs.state.first()
        if (cached.baseUrl != baseUrl || cached.username != username) {
            prefs.clear()
        }

        val token =
            prefs.state.first().token.ifBlank {
                val login = SyncServerClient.login(baseUrl, username, password)
                val t = login.value.orEmpty()
                if (login.ok && t.isNotBlank()) {
                    prefs.set(baseUrl = baseUrl, username = username, token = t)
                    t
                } else {
                    ""
                }
            }
        if (token.isBlank()) return null
        return ServerAuth(baseUrl = baseUrl, token = token)
    }

    private fun computeRelativePath(vaultRootUri: Uri, docUri: Uri): String? {
        if (!vaultRootUri.scheme.equals("file", ignoreCase = true)) return null
        if (!docUri.scheme.equals("file", ignoreCase = true)) return null
        val rootPath = vaultRootUri.path ?: return null
        val docPath = docUri.path ?: return null
        val root =
            runCatching { File(rootPath).canonicalFile }
                .getOrDefault(File(rootPath).absoluteFile)
        val doc =
            runCatching { File(docPath).canonicalFile }
                .getOrDefault(File(docPath).absoluteFile)
        val rel = runCatching { doc.relativeTo(root).invariantSeparatorsPath }.getOrNull() ?: return null
        return rel.trimStart('/').takeIf { it.isNotBlank() }
    }

    private fun resolveVaultFileUri(vaultRootUri: Uri, relativePath: String): Uri? {
        if (!vaultRootUri.scheme.equals("file", ignoreCase = true)) return null
        val rootPath = vaultRootUri.path ?: return null
        val file = File(rootPath, relativePath)
        if (!file.exists()) return null
        return Uri.fromFile(file)
    }

    private fun computeMtimeMs(uri: Uri): Long? {
        if (!uri.scheme.equals("file", ignoreCase = true)) return null
        val path = uri.path ?: return null
        return runCatching { File(path).lastModified() }.getOrNull()
    }
}
