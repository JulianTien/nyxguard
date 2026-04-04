package com.scf.nyxguard.util

import android.os.Handler
import android.os.Looper
import com.amap.api.maps.model.LatLng

/**
 * 调试用：模拟步行轨迹，沿路线点逐步回调位置。
 * 在真机上使用真实 GPS，仅在调试时使用此类。
 */
class MockLocationHelper(
    private val routePoints: List<LatLng>,
    private val intervalMs: Long = 3_000,  // 3秒一步，方便快速测试
    private val onLocation: (LatLng) -> Unit
) {
    private val handler = Handler(Looper.getMainLooper())
    private var currentIndex = 0
    private var running = false

    // 在路线点之间插值，生成更密集的模拟点
    private val interpolatedPoints: List<LatLng> by lazy {
        if (routePoints.size < 2) return@lazy routePoints
        val result = mutableListOf<LatLng>()
        for (i in 0 until routePoints.size - 1) {
            val from = routePoints[i]
            val to = routePoints[i + 1]
            val dist = GeoUtils.distanceBetween(from, to)
            // 每50米插一个点
            val steps = (dist / 50).toInt().coerceAtLeast(1)
            for (s in 0 until steps) {
                val ratio = s.toDouble() / steps
                result.add(
                    LatLng(
                        from.latitude + (to.latitude - from.latitude) * ratio,
                        from.longitude + (to.longitude - from.longitude) * ratio
                    )
                )
            }
        }
        result.add(routePoints.last())
        result
    }

    private val tickRunnable = object : Runnable {
        override fun run() {
            if (!running || currentIndex >= interpolatedPoints.size) {
                running = false
                return
            }
            onLocation(interpolatedPoints[currentIndex])
            currentIndex++
            handler.postDelayed(this, intervalMs)
        }
    }

    fun start() {
        if (running) return
        running = true
        currentIndex = 0
        handler.post(tickRunnable)
    }

    fun stop() {
        running = false
        handler.removeCallbacks(tickRunnable)
    }
}
