package app.zhixu

import android.app.Application
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import app.zhixu.pomodoro.PomodoroService
import app.zhixu.perf.DebugMonitors

class ZhixuApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        DebugMonitors.install(this)
        createPomodoroNotificationChannel()
    }

    private fun createPomodoroNotificationChannel() {
        val manager = getSystemService(NotificationManager::class.java) ?: return
        val channel =
            NotificationChannel(
                PomodoroService.NOTIFICATION_CHANNEL_ID,
                "番茄计时",
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = "番茄计时前台服务通知"
                setShowBadge(false)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
        manager.createNotificationChannel(channel)
    }
}
