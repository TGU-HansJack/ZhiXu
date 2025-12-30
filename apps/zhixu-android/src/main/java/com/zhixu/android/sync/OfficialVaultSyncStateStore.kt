package com.zhixu.android.sync

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.zhixu.android.data.VaultRepository
import com.zhixu.android.data.vaultRootToDocumentFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

data class OfficialVaultSyncFileState(
    val baseRev: Long,
    val sha256: String,
    val size: Long,
    val localMtimeMs: Long,
    val deleted: Boolean,
)

data class OfficialVaultSyncStateV2(
    val serverCursor: Long,
    val files: Map<String, OfficialVaultSyncFileState>,
) {
    fun withUploaded(path: String, rev: Long, sha256: String, size: Long, localMtimeMs: Long): OfficialVaultSyncStateV2 {
        val nextFiles =
            files.toMutableMap().apply {
                this[path] =
                    OfficialVaultSyncFileState(
                        baseRev = rev,
                        sha256 = sha256,
                        size = size,
                        localMtimeMs = localMtimeMs,
                        deleted = false,
                    )
            }
        return copy(files = nextFiles)
    }

    fun withDeleted(path: String, rev: Long): OfficialVaultSyncStateV2 {
        val prev = files[path]
        val nextFiles =
            files.toMutableMap().apply {
                this[path] =
                    OfficialVaultSyncFileState(
                        baseRev = rev,
                        sha256 = prev?.sha256.orEmpty(),
                        size = 0L,
                        localMtimeMs = System.currentTimeMillis(),
                        deleted = true,
                    )
            }
        return copy(files = nextFiles)
    }
}

class OfficialVaultSyncStateStore(
    private val context: Context,
    private val repository: VaultRepository,
) {
    private val statePath = ".zhixu/sync/official_state_v2.json"

    suspend fun load(rootUri: Uri): OfficialVaultSyncStateV2 = withContext(Dispatchers.IO) {
        repository.ensureVaultStructure(rootUri)
        val root = vaultRootToDocumentFile(context, rootUri) ?: return@withContext OfficialVaultSyncStateV2(0L, emptyMap())
        val stateFile = ensureLocalFile(root, statePath) ?: return@withContext OfficialVaultSyncStateV2(0L, emptyMap())
        val raw = runCatching { repository.readText(stateFile.uri) }.getOrNull().orEmpty()
        val obj = runCatching { JSONObject(raw) }.getOrNull() ?: return@withContext OfficialVaultSyncStateV2(0L, emptyMap())
        val cursor = obj.optLong("serverCursor", 0L).coerceAtLeast(0L)
        val filesObj = obj.optJSONObject("files") ?: JSONObject()
        val it = filesObj.keys()
        val out = LinkedHashMap<String, OfficialVaultSyncFileState>()
        while (it.hasNext()) {
            val path = it.next().orEmpty().trim().trimStart('/')
            if (path.isBlank()) continue
            val f = filesObj.optJSONObject(path) ?: continue
            out[path] =
                OfficialVaultSyncFileState(
                    baseRev = f.optLong("baseRev", 0L).coerceAtLeast(0L),
                    sha256 = f.optString("sha256").orEmpty(),
                    size = f.optLong("size", 0L).coerceAtLeast(0L),
                    localMtimeMs = f.optLong("localMtimeMs", 0L).coerceAtLeast(0L),
                    deleted = f.optBoolean("deleted", false),
                )
        }
        OfficialVaultSyncStateV2(serverCursor = cursor, files = out)
    }

    suspend fun save(rootUri: Uri, state: OfficialVaultSyncStateV2) = withContext(Dispatchers.IO) {
        repository.ensureVaultStructure(rootUri)
        val root = vaultRootToDocumentFile(context, rootUri) ?: return@withContext
        val stateFile = ensureLocalFile(root, statePath) ?: return@withContext

        val filesObj = JSONObject()
        for ((path, f) in state.files) {
            filesObj.put(
                path,
                JSONObject()
                    .put("baseRev", f.baseRev)
                    .put("sha256", f.sha256)
                    .put("size", f.size)
                    .put("localMtimeMs", f.localMtimeMs)
                    .put("deleted", f.deleted),
            )
        }

        val obj =
            JSONObject()
                .put("version", 2)
                .put("savedAt", System.currentTimeMillis())
                .put("serverCursor", state.serverCursor)
                .put("files", filesObj)
        runCatching { repository.writeText(stateFile.uri, obj.toString()) }
    }

    private fun ensureLocalFile(root: DocumentFile, path: String): DocumentFile? {
        val parts = path.split('/').filter { it.isNotBlank() }
        if (parts.isEmpty()) return null
        var dir = root
        for (i in 0 until parts.size - 1) {
            val name = parts[i]
            dir = dir.findFile(name) ?: dir.createDirectory(name) ?: return null
        }
        val name = parts.last()
        return dir.findFile(name) ?: dir.createFile("application/json", name)
    }
}

