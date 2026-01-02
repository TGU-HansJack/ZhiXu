package app.zhixu.data

import android.net.Uri
import kotlinx.coroutines.flow.SharedFlow

/**
 * Read-only view of the document index (SQLite-backed).
 *
 * UI should consume this for doc listing/search, and use an updater to request re-indexing.
 */
class DocumentIndex(
    private val repository: VaultRepository,
) {
    val changes: SharedFlow<Long> = repository.indexChanges

    suspend fun listDocs(
        vaultRootUri: Uri,
        limit: Int = 2000,
    ): List<UiDoc> = repository.listIndexedDocs(vaultRootUri, limit = limit)

    suspend fun search(
        query: String,
        limit: Int = 50,
    ): List<SearchResult> = repository.search(rootUri = null, query = query).take(limit)
}

