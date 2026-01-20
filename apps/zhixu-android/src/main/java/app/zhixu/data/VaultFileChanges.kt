package app.zhixu.data

import android.net.Uri

enum class VaultFileChangeSource {
    OFFICIAL_SYNC,
    WEBDAV_SYNC,
}

/**
 * "Vault file changed" events emitted by sync (and potentially other non-UI producers).
 *
 * These events should describe local filesystem mutations (download / delete / etc). The indexing layer
 * can then decide whether to do incremental updates (per-path) or a heavier rebuild.
 */
data class VaultFileChangeBatch(
    val rootUri: Uri,
    val upsertPaths: List<String>,
    val deletePaths: List<String>,
    val source: VaultFileChangeSource,
    val emittedAtMs: Long = System.currentTimeMillis(),
)

