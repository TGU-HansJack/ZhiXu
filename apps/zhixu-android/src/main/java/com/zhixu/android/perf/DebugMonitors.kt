package com.zhixu.android.perf

import android.app.Application

object DebugMonitors {
    fun install(application: Application) {
        runCatching {
            val clazz = Class.forName("com.zhixu.android.perf.DebugMonitorsImpl")
            val method = clazz.getDeclaredMethod("install", Application::class.java)
            method.invoke(null, application)
        }
    }
}
