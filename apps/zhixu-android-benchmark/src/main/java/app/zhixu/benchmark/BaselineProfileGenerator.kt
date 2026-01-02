package com.zhixu.android.benchmark

import androidx.benchmark.macro.ExperimentalMetricApi
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@LargeTest
class BaselineProfileGenerator {
    @get:Rule
    val baselineProfileRule = BaselineProfileRule()

    @Test
    fun generate() =
        baselineProfileRule.collect(packageName = "com.zhixu.android") {
            pressHome()
            startActivityAndWait()

            // Trigger typical startup work + initial scroll on docs list.
            device.waitForIdle()
            repeat(5) { device.swipe(device.displayWidth / 2, device.displayHeight * 3 / 4, device.displayWidth / 2, device.displayHeight / 4, 30) }

            // Switch to Tasks page (pager swipe) and scroll.
            device.swipe(device.displayWidth * 3 / 4, device.displayHeight / 2, device.displayWidth / 4, device.displayHeight / 2, 40)
            device.waitForIdle()
            repeat(3) { device.swipe(device.displayWidth / 2, device.displayHeight * 3 / 4, device.displayWidth / 2, device.displayHeight / 4, 30) }
        }
}

@RunWith(AndroidJUnit4::class)
@LargeTest
class Macrobenchmarks {
    @get:Rule
    val benchmarkRule = MacrobenchmarkRule()

    @OptIn(ExperimentalMetricApi::class)
    @Test
    fun startupAndScrollJank() {
        benchmarkRule.measureRepeated(
            packageName = "com.zhixu.android",
            metrics = listOf(StartupTimingMetric(), FrameTimingMetric()),
            startupMode = StartupMode.COLD,
            iterations = 5,
            setupBlock = {
                pressHome()
            },
            measureBlock = {
                startActivityAndWait()
                device.waitForIdle()
                repeat(6) { device.swipe(device.displayWidth / 2, device.displayHeight * 3 / 4, device.displayWidth / 2, device.displayHeight / 4, 30) }
                device.swipe(device.displayWidth * 3 / 4, device.displayHeight / 2, device.displayWidth / 4, device.displayHeight / 2, 40)
                device.waitForIdle()
                repeat(4) { device.swipe(device.displayWidth / 2, device.displayHeight * 3 / 4, device.displayWidth / 2, device.displayHeight / 4, 30) }
            },
        )
    }
}
