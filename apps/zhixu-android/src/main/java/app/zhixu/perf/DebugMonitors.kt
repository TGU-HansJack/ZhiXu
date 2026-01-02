package app.zhixu.perf

import android.app.Application
import app.zhixu.BuildConfig

object DebugMonitors {
    fun install(application: Application) {
        if (!BuildConfig.DEBUG) return
        runCatching {
            val clazz = Class.forName("${BuildConfig.APPLICATION_ID}.perf.DebugMonitorsImpl")
            val method = clazz.getDeclaredMethod("install", Application::class.java)
            method.invoke(null, application)
        }
    }
}
