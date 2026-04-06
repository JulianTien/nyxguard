package com.scf.nyxguard.util

import android.content.Context
import android.util.Log
import com.amap.api.location.AMapLocationClient
import com.amap.api.maps.AMap
import com.amap.api.maps.MapsInitializer
import com.amap.api.services.core.ServiceSettings
import com.scf.nyxguard.LocaleManager

object AmapSdkInitializer {

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

    fun applyMapLanguage(context: Context, map: AMap?) {
        val targetMap = map ?: return
        val language = if (LocaleManager.isChinese(context)) AMap.CHINESE else AMap.ENGLISH
        runCatching {
            targetMap.setMapLanguage(language)
        }.onFailure { error ->
            Log.w(TAG, "Failed to apply AMap language: $language", error)
        }
    }

    private const val TAG = "AmapSdkInitializer"
}
