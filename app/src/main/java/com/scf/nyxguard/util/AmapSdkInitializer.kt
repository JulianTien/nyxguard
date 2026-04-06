package com.scf.nyxguard.util

import android.content.Context
import android.util.Log
import com.amap.api.location.AMapLocationClient
import com.amap.api.maps.AMap
import com.amap.api.maps.MapsInitializer
import com.amap.api.maps.model.LatLng
import com.amap.api.services.core.ServiceSettings
import com.scf.nyxguard.LocaleManager

object AmapSdkInitializer {

    private val cjkRegex = Regex("[\\u4e00-\\u9fff]")

    @Volatile
    private var initialized = false

    fun ensureInitialized(context: Context) {
        if (initialized) return
        synchronized(this) {
            if (initialized) return

            val appContext = context.applicationContext

            MapsInitializer.updatePrivacyShow(appContext, true, true)
            MapsInitializer.updatePrivacyAgree(appContext, true)
            AMapLocationClient.updatePrivacyShow(appContext, true, true)
            AMapLocationClient.updatePrivacyAgree(appContext, true)
            ServiceSettings.updatePrivacyShow(appContext, true, true)
            ServiceSettings.updatePrivacyAgree(appContext, true)

            // 按高德官方世界地图文档启用世界地图能力，便于海外模拟器也能正常渲染底图。
            runCatching {
                MapsInitializer.loadWorldVectorMap(true)
            }.onFailure { error ->
                Log.w(TAG, "Failed to enable AMap world vector map", error)
            }

            initialized = true
        }
    }

    fun applyMapLanguage(
        context: Context,
        map: AMap?,
        scenePoints: Iterable<LatLng> = emptyList(),
        sceneLabels: Iterable<String> = emptyList(),
    ) {
        val targetMap = map ?: return
        val language = if (shouldUseEnglishMap(context, scenePoints, sceneLabels)) {
            AMap.ENGLISH
        } else {
            AMap.CHINESE
        }
        runCatching {
            targetMap.setMapLanguage(language)
        }.onFailure { error ->
            Log.w(TAG, "Failed to apply AMap language: $language", error)
        }
    }

    private fun shouldUseEnglishMap(
        context: Context,
        scenePoints: Iterable<LatLng>,
        sceneLabels: Iterable<String>,
    ): Boolean {
        if (LocaleManager.isChinese(context)) {
            return false
        }

        if (sceneLabels.any { cjkRegex.containsMatchIn(it) }) {
            return false
        }

        val points = scenePoints.toList()
        if (points.isEmpty()) {
            return false
        }

        return points.all { !isLikelyInChina(it) }
    }

    private fun isLikelyInChina(point: LatLng): Boolean {
        return point.latitude in 3.0..54.5 && point.longitude in 73.0..135.5
    }

    private const val TAG = "AmapSdkInitializer"
}
