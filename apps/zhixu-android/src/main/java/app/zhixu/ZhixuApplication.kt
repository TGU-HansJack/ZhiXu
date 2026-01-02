package app.zhixu

import android.app.Application
import app.zhixu.perf.DebugMonitors

class ZhixuApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        DebugMonitors.install(this)
    }
}

