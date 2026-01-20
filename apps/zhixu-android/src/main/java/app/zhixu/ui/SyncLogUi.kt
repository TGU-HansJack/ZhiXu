package app.zhixu.ui

import android.content.Context
import app.zhixu.R
import app.zhixu.sync.SyncServerClient
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

object SyncLogUi {
    fun formatEpochMs(ms: Long): String {
        if (ms <= 0L) return "-"
        return runCatching {
            Instant
                .ofEpochMilli(ms)
                .atZone(ZoneId.systemDefault())
                .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))
        }.getOrElse { ms.toString() }
    }

    fun actionLabel(context: Context, action: String): String {
        return when (action.trim().lowercase()) {
            "file_put" -> context.getString(R.string.sync_log_op_upload)
            "file_get" -> context.getString(R.string.sync_log_op_download)
            "file_delete" -> context.getString(R.string.sync_log_op_delete_remote)
            "changes_snapshot" -> context.getString(R.string.account_sync_logs_action_changes_snapshot)
            "changes_delta" -> context.getString(R.string.account_sync_logs_action_changes_delta)
            else -> action.ifBlank { "-" }
        }
    }

    fun formatLatestStatus(context: Context, log: SyncServerClient.AccountSyncLog): String {
        val parts = ArrayList<String>(3)
        parts += actionLabel(context, log.action)
        val path = log.path.trim()
        if (path.isNotBlank()) parts += path
        val time = formatEpochMs(log.createdAtMs)
        if (time != "-") parts += time
        return parts.joinToString(" · ").ifBlank { "-" }
    }
}

