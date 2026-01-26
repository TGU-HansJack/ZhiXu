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
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import app.zhixu.MainActivity
import app.zhixu.R
import app.zhixu.data.DailyReminderSettings
import app.zhixu.data.NotificationPreferences
import app.zhixu.data.TasksPreferences
import app.zhixu.data.VaultRepository
import app.zhixu.data.VaultTasksRepository
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.first

class DailyReminderWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {
    private val repository = VaultRepository(appContext)
    private val tasksPreferences = TasksPreferences(appContext.applicationContext)

    override suspend fun doWork(): Result {
        val prefs = NotificationPreferences(applicationContext)
        val current = runCatching { prefs.dailyReminder.first() }.getOrNull() ?: return Result.success()
        if (!current.enabled) return Result.success()

        runCatching { ensureChannels(applicationContext, current) }

        val now = LocalDateTime.now()
        if (!isSelectedWeekday(current.weekdayMask, now.toLocalDate())) {
            scheduleNext(applicationContext, current)
            return Result.success()
        }

        val tasksPluginEnabled = runCatching { tasksPreferences.enabled.first() }.getOrDefault(false)
        if (!tasksPluginEnabled) {
            scheduleNext(applicationContext, current)
            return Result.success()
        }

        val today = now.toLocalDate()
        val tasks =
            runCatching {
                repository.getTasksDueOn(
                    day = today,
                    limit = 500,
                    status = VaultTasksRepository.TaskStatusFilter.Undone,
                    tag = null,
                )
            }.getOrDefault(emptyList())

        val count = tasks.size
        val title = applicationContext.getString(R.string.daily_reminder_notification_title)
        val body =
            if (count == 0) {
                applicationContext.getString(R.string.daily_reminder_notification_empty)
            } else {
                applicationContext.getString(R.string.daily_reminder_notification_count_fmt, count)
            }

        val openIntent =
            Intent(applicationContext, MainActivity::class.java).addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP,
            )
        val pendingIntent =
            PendingIntent.getActivity(
                applicationContext,
                1001,
                openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )

        val builder =
            NotificationCompat.Builder(applicationContext, channelIdFor(current))
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentTitle(title)
                .setContentText(body)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .setCategory(NotificationCompat.CATEGORY_REMINDER)
                .setPriority(if (current.popupEnabled) NotificationCompat.PRIORITY_HIGH else NotificationCompat.PRIORITY_DEFAULT)

        if (Build.VERSION.SDK_INT < 26) {
            if (current.soundEnabled && current.vibrationEnabled) {
                builder.setDefaults(NotificationCompat.DEFAULT_ALL)
            } else if (current.soundEnabled) {
                builder.setDefaults(NotificationCompat.DEFAULT_SOUND)
            } else if (current.vibrationEnabled) {
                builder.setDefaults(NotificationCompat.DEFAULT_VIBRATE)
                builder.setSound(null)
            } else {
                builder.setSound(null)
                builder.setVibrate(longArrayOf(0L))
            }
            if (current.vibrationEnabled) {
                builder.setVibrate(longArrayOf(0, 200, 120, 220))
            }
        }

        if (count > 0) {
            val lines = tasks.take(5).map { "• ${it.title}" }
            builder.setStyle(
                NotificationCompat.BigTextStyle().bigText(lines.joinToString("\n")),
            )
        }

        runCatching {
            NotificationManagerCompat.from(applicationContext).notify(NOTIFICATION_ID, builder.build())
        }

        scheduleNext(applicationContext, current)
        return Result.success()
    }

    companion object {
        private const val UNIQUE_WORK_NAME = "daily_reminder_once"
        private const val NOTIFICATION_ID = 92001

        fun scheduleNext(
            context: Context,
            settings: DailyReminderSettings,
        ) {
            if (!settings.enabled) {
                WorkManager.getInstance(context).cancelUniqueWork(UNIQUE_WORK_NAME)
                return
            }
            val delay = computeNextDelay(settings)
            val request =
                OneTimeWorkRequestBuilder<DailyReminderWorker>()
                    .setInitialDelay(delay.toMillis().coerceAtLeast(0), TimeUnit.MILLISECONDS)
                    .build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                UNIQUE_WORK_NAME,
                ExistingWorkPolicy.REPLACE,
                request,
            )
        }

        private fun computeNextDelay(settings: DailyReminderSettings): Duration {
            val now = LocalDateTime.now()
            val time = parseTime(settings.timeHHmm) ?: LocalTime.of(9, 0)
            for (offset in 0..13) {
                val date = now.toLocalDate().plusDays(offset.toLong())
                if (!isSelectedWeekday(settings.weekdayMask, date)) continue
                val target = date.atTime(time)
                if (target.isAfter(now)) {
                    return Duration.between(now, target)
                }
            }
            val fallback = now.plusHours(24).truncatedTo(ChronoUnit.MINUTES)
            return Duration.between(now, fallback)
        }

        private fun parseTime(hhmm: String): LocalTime? {
            val parts = hhmm.trim().split(":")
            if (parts.size != 2) return null
            val h = parts[0].toIntOrNull() ?: return null
            val m = parts[1].toIntOrNull() ?: return null
            return runCatching { LocalTime.of(h.coerceIn(0, 23), m.coerceIn(0, 59)) }.getOrNull()
        }

        private fun isSelectedWeekday(mask: Int, date: LocalDate): Boolean {
            val bitIndex = (date.dayOfWeek.value - 1).coerceIn(0, 6) // Mon=1..Sun=7
            return ((mask shr bitIndex) and 1) == 1
        }

        private fun channelIdFor(s: DailyReminderSettings): String {
            val p = if (s.popupEnabled) 1 else 0
            val v = if (s.vibrationEnabled) 1 else 0
            val a = if (s.soundEnabled) 1 else 0
            return "daily_reminder_p${p}_s${a}_v${v}"
        }

        private fun ensureChannels(
            context: Context,
            settings: DailyReminderSettings,
        ) {
            if (Build.VERSION.SDK_INT < 26) return
            val mgr = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            fun createIfMissing(id: String, importance: Int, soundEnabled: Boolean, vibrateEnabled: Boolean) {
                if (mgr.getNotificationChannel(id) != null) return
                val channel =
                    NotificationChannel(
                        id,
                        context.getString(R.string.daily_reminder_channel_name),
                        importance,
                    ).apply {
                        enableVibration(vibrateEnabled)
                        vibrationPattern = if (vibrateEnabled) longArrayOf(0, 200, 120, 220) else longArrayOf(0L)
                        if (!soundEnabled) {
                            setSound(null, null)
                        }
                    }
                mgr.createNotificationChannel(channel)
            }

            val importance = if (settings.popupEnabled) NotificationManager.IMPORTANCE_HIGH else NotificationManager.IMPORTANCE_DEFAULT
            val id = channelIdFor(settings)
            createIfMissing(id, importance, settings.soundEnabled, settings.vibrationEnabled)
        }
    }
}
