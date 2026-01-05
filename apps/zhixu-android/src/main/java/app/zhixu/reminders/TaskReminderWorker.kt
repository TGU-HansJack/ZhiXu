package app.zhixu.reminders

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import app.zhixu.MainActivity
import app.zhixu.R
import app.zhixu.data.VaultIndexRepository
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class TaskReminderWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {
    private val index = VaultIndexRepository(appContext)
    private val dueFormatter = DateTimeFormatter.ofPattern("MM-dd HH:mm")

    override suspend fun doWork(): Result {
        runCatching { ensureChannel() }.getOrElse { return Result.success() }

        val now = System.currentTimeMillis()
        val tasks = runCatching { index.getDueTasksForReminder(nowEpochMillis = now) }.getOrElse { return Result.success() }
        for (task in tasks) {
            val due = task.dueEpochMillis
            val trigger = task.remindEpochMillis ?: due ?: continue
            val key = task.taskId ?: "${task.docUri}#${task.lineIndex}"
            if (!task.remindPersistent) {
                if (runCatching { index.wasReminderNotified(key, trigger) }.getOrDefault(false)) continue
            }

            val dueText = due?.let { dueFormatter.format(Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault())) }
            val intent = Intent(applicationContext, MainActivity::class.java)
                .putExtra(EXTRA_DOC_URI, task.docUri.toString())
                .putExtra(EXTRA_LINE_INDEX, task.lineIndex)
            val pendingIntent =
                PendingIntent.getActivity(
                    applicationContext,
                    key.hashCode(),
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                )

            val contentText =
                if (dueText != null) {
                    "${task.docName} · $dueText"
                } else {
                    task.docName
                }
            val notification =
                NotificationCompat.Builder(applicationContext, CHANNEL_ID)
                    .setSmallIcon(R.drawable.ic_launcher_foreground)
                    .setContentTitle(task.title)
                    .setContentText(contentText)
                    .setContentIntent(pendingIntent)
                    .setAutoCancel(true)
                    .setCategory(NotificationCompat.CATEGORY_REMINDER)
                    .setPriority(NotificationCompat.PRIORITY_HIGH)
                    .setDefaults(NotificationCompat.DEFAULT_ALL)
                    .setVibrate(longArrayOf(0, 250, 150, 250))
                    .build()

            runCatching { NotificationManagerCompat.from(applicationContext).notify(key.hashCode(), notification) }
            if (!task.remindPersistent) {
                runCatching { index.markReminderNotified(key, trigger) }
            }
        }

        return Result.success()
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < 26) return
        val mgr = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel =
            NotificationChannel(
                CHANNEL_ID,
                applicationContext.getString(R.string.reminder_channel_name),
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 250, 150, 250)
            }
        mgr.createNotificationChannel(channel)
    }

    companion object {
        const val CHANNEL_ID = "tasks_due"
        const val EXTRA_DOC_URI = "task_doc_uri"
        const val EXTRA_LINE_INDEX = "task_line_index"
    }
}
