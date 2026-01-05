package app.zhixu.data

import android.net.Uri

sealed interface SearchResult {
    val title: String
}

data class DocSearchResult(
    override val title: String,
    val uri: Uri,
    val snippet: String?,
) : SearchResult

data class TaskSearchResult(
    override val title: String,
    val docUri: Uri,
    val lineIndex: Int,
    val taskId: String?,
    val dueEpochMillis: Long?,
) : SearchResult

data class UiTask(
    val title: String,
    val docUri: Uri,
    val docName: String,
    val lineIndex: Int,
    val checked: Boolean,
    val taskId: String?,
    val dueEpochMillis: Long?,
    val remindEpochMillis: Long?,
    val remindPersistent: Boolean,
    val priority: Int?,
)
