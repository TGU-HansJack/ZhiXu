package com.zhixu.android.perf

import android.app.Application
import com.zhixu.android.BuildConfig

object DebugMonitors {
    fun install(application: Application) {
        if (!BuildConfig.DEBUG) return
        runCatching {
            val clazz = Class.forName("com.zhixu.android.perf.DebugMonitorsImpl")
            val method = clazz.getDeclaredMethod("install", Application::class.java)
            method.invoke(null, application)
        }
    }
}
