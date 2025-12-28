package com.zhixu.android.perf

import android.app.Activity
import android.app.Application
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.os.StrictMode
import android.util.Log
import androidx.metrics.performance.FrameData
import androidx.metrics.performance.JankStats
import java.util.WeakHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

object DebugMonitorsImpl {
    private const val TAG = "DebugMonitors"

    private val trackedJankStats = WeakHashMap<Activity, JankStats>()
    private var blockWatcher: MainThreadBlockWatcher? = null

    @JvmStatic
    fun install(application: Application) {
        installStrictMode()
        installJankStats(application)
        installMainThreadBlockWatcher()
    }

    private fun installStrictMode() {
        StrictMode.setThreadPolicy(
            StrictMode.ThreadPolicy.Builder()
                .detectDiskReads()
                .detectDiskWrites()
                .detectNetwork()
                .penaltyLog()
                .build(),
        )
        StrictMode.setVmPolicy(
            StrictMode.VmPolicy.Builder()
                .detectLeakedSqlLiteObjects()
                .detectLeakedClosableObjects()
                .penaltyLog()
                .build(),
        )
    }

    private fun installJankStats(application: Application) {
        application.registerActivityLifecycleCallbacks(
            object : Application.ActivityLifecycleCallbacks {
                override fun onActivityResumed(activity: Activity) {
                    if (trackedJankStats.containsKey(activity)) return

                    fun tryInstall(): Boolean {
                        return runCatching {
                            val jankStats =
                                JankStats.createAndTrack(
                                    activity.window,
                                ) { frameData: FrameData ->
                                    if (!frameData.isJank) return@createAndTrack
                                    val ms = frameData.frameDurationUiNanos / 1_000_000.0
                                    Log.w(TAG, "Jank frame: %.2fms states=%s".format(ms, frameData.states.joinToString()))
                                }
                            trackedJankStats[activity] = jankStats
                            true
                        }.getOrElse { false }
                    }

                    if (tryInstall()) return

                    // Some devices can resume before the Window decor view is ready; retry after view is attached.
                    activity.window.decorView?.post { tryInstall() }
                }

                override fun onActivityDestroyed(activity: Activity) {
                    trackedJankStats.remove(activity)?.isTrackingEnabled = false
                }

                override fun onActivityStarted(activity: Activity) = Unit

                override fun onActivityPaused(activity: Activity) = Unit

                override fun onActivityStopped(activity: Activity) = Unit

                override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit

                override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
            },
        )
    }

    private fun installMainThreadBlockWatcher() {
        if (blockWatcher != null) return
        blockWatcher =
            MainThreadBlockWatcher(
                blockThresholdMs = 700,
                sampleIntervalMs = 200,
            ).also { it.start() }
    }
}

private class MainThreadBlockWatcher(
    private val blockThresholdMs: Long,
    private val sampleIntervalMs: Long,
) : Thread("MainThreadBlockWatcher") {
    private val mainHandler = Handler(Looper.getMainLooper())
    private val mainThread = Looper.getMainLooper().thread
    @Volatile private var running = true

    override fun run() {
        while (running) {
            val start = SystemClock.elapsedRealtime()
            val latch = CountDownLatch(1)
            mainHandler.post { latch.countDown() }

            val ok = latch.await(blockThresholdMs, TimeUnit.MILLISECONDS)
            if (!ok) {
                val blockedForMs = SystemClock.elapsedRealtime() - start
                val stack = mainThread.stackTrace?.joinToString(separator = "\n") { "  at $it" }.orEmpty()
                Log.e("MainThreadBlock", "Blocked ~${blockedForMs}ms\n$stack")
            }

            SystemClock.sleep(sampleIntervalMs)
        }
    }

    fun stopWatching() {
        running = false
        interrupt()
    }
}
