package com.scf.nyxguard.util

import com.amap.api.maps.AMapUtils
import com.amap.api.maps.model.LatLng

object GeoUtils {

    /** 两点之间的距离（米） */
    fun distanceBetween(a: LatLng, b: LatLng): Float {
        return AMapUtils.calculateLineDistance(a, b)
    }

    /** 点到折线的最短距离（米） */
    fun distanceToPolyline(point: LatLng, polyline: List<LatLng>): Float {
        if (polyline.size < 2) {
            return if (polyline.isEmpty()) Float.MAX_VALUE
            else distanceBetween(point, polyline[0])
        }
        var minDist = Float.MAX_VALUE
        for (i in 0 until polyline.size - 1) {
            val dist = pointToSegmentDistance(point, polyline[i], polyline[i + 1])
            if (dist < minDist) minDist = dist
        }
        return minDist
    }

    /** 根据距离估算步行时间（分钟），按 5km/h */
    fun estimateWalkingMinutes(distanceMeters: Int): Int {
        return (distanceMeters / 5000.0 * 60).toInt().coerceAtLeast(1)
    }

    /** 点到线段的最短距离 */
    private fun pointToSegmentDistance(p: LatLng, a: LatLng, b: LatLng): Float {
        val ab = distanceBetween(a, b)
        if (ab < 0.01f) return distanceBetween(p, a)

        val ap = distanceBetween(a, p)
        val bp = distanceBetween(b, p)

        // 使用余弦定理判断投影是否在线段上
        val cosA = (ab * ab + ap * ap - bp * bp) / (2 * ab * ap + 0.001f)
        val cosB = (ab * ab + bp * bp - ap * ap) / (2 * ab * bp + 0.001f)

        return when {
            cosA < 0 -> ap  // 投影在 A 点外
            cosB < 0 -> bp  // 投影在 B 点外
            else -> {
                // 投影在线段上，用海伦公式算面积再求高
                val s = (ab + ap + bp) / 2f
                val area = Math.sqrt((s * (s - ab) * (s - ap) * (s - bp)).toDouble().coerceAtLeast(0.0)).toFloat()
                2 * area / ab
            }
        }
    }
}
