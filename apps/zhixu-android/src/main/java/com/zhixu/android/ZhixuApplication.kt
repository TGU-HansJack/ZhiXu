package com.zhixu.android

import android.app.Application
import com.zhixu.android.perf.DebugMonitors

class ZhixuApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        DebugMonitors.install(this)
    }
}

