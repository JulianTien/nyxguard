package com.scf.nyxguard.util

import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions

object FirebaseBootstrap {

    private const val TAG = "FirebaseBootstrap"
    private const val META_API_KEY = "nyxguard_firebase_api_key"
    private const val META_APP_ID = "nyxguard_firebase_app_id"
    private const val META_PROJECT_ID = "nyxguard_firebase_project_id"
    private const val META_SENDER_ID = "nyxguard_firebase_sender_id"
    private const val META_STORAGE_BUCKET = "nyxguard_firebase_storage_bucket"

    fun ensureInitialized(context: Context): Boolean {
        val appContext = context.applicationContext
        if (FirebaseApp.getApps(appContext).isNotEmpty()) {
            return true
        }

        val options = readOptions(appContext) ?: return false
        return runCatching {
            FirebaseApp.initializeApp(appContext, options) != null
        }.getOrElse { error ->
            Log.w(TAG, "Firebase initialization skipped", error)
            false
        }
    }

    private fun readOptions(context: Context): FirebaseOptions? {
        val metaData = readMetaData(context) ?: run {
            Log.i(TAG, "Firebase metadata is missing; push integration will stay disabled")
            return null
        }

        val apiKey = metaData.getString(META_API_KEY)?.trim().orEmpty()
        val appId = metaData.getString(META_APP_ID)?.trim().orEmpty()
        val projectId = metaData.getString(META_PROJECT_ID)?.trim().orEmpty()
        val senderId = metaData.getString(META_SENDER_ID)?.trim().orEmpty()

        if (apiKey.isBlank() || appId.isBlank() || projectId.isBlank() || senderId.isBlank()) {
            Log.i(TAG, "Firebase metadata is incomplete; push integration will stay disabled")
            return null
        }

        return FirebaseOptions.Builder()
            .setApiKey(apiKey)
            .setApplicationId(appId)
            .setProjectId(projectId)
            .setGcmSenderId(senderId)
            .apply {
                metaData.getString(META_STORAGE_BUCKET)?.trim()?.takeIf { it.isNotBlank() }?.let {
                    setStorageBucket(it)
                }
            }
            .build()
    }

    private fun readMetaData(context: Context): Bundle? {
        return runCatching {
            val info = context.packageManager.getApplicationInfo(
                context.packageName,
                PackageManager.GET_META_DATA
            )
            info.metaData
        }.getOrNull()
    }
}
