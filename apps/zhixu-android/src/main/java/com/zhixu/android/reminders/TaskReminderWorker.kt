package com.zhixu.android.reminders

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
import com.zhixu.android.MainActivity
import com.zhixu.android.R
import com.zhixu.android.data.VaultIndexRepository
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
            val due = task.dueEpochMillis ?: continue
            val key = task.taskId ?: "${task.docUri}#${task.lineIndex}"
            if (runCatching { index.wasReminderNotified(key, due) }.getOrDefault(false)) continue

            val dueText = dueFormatter.format(Instant.ofEpochMilli(due).atZone(ZoneId.systemDefault()))
            val intent = Intent(applicationContext, MainActivity::class.java)
            val pendingIntent =
                PendingIntent.getActivity(
                    applicationContext,
                    key.hashCode(),
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                )

            val notification =
                NotificationCompat.Builder(applicationContext, CHANNEL_ID)
                    .setSmallIcon(R.drawable.ic_launcher_foreground)
                    .setContentTitle(task.title)
                    .setContentText("${task.docName} · $dueText")
                    .setContentIntent(pendingIntent)
                    .setAutoCancel(true)
                    .build()

            runCatching { NotificationManagerCompat.from(applicationContext).notify(key.hashCode(), notification) }
            runCatching { index.markReminderNotified(key, due) }
        }

        return Result.success()
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < 26) return
        val mgr = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (mgr.getNotificationChannel(CHANNEL_ID) != null) return
        mgr.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                applicationContext.getString(R.string.reminder_channel_name),
                NotificationManager.IMPORTANCE_DEFAULT,
            ),
        )
    }

    companion object {
        const val CHANNEL_ID = "tasks_due"
    }
}
