package com.scf.nyxguard.util

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.util.Log
import com.google.firebase.messaging.FirebaseMessaging
import com.scf.nyxguard.BuildConfig
import com.scf.nyxguard.network.TokenManager
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.net.URI
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

object PushTokenSyncManager {

    private const val TAG = "PushTokenSyncManager"
    private const val PREFS_NAME = "nyxguard_push_sync"
    private const val KEY_USER_ID = "user_id"
    private const val KEY_TOKEN = "token"
    private const val KEY_ENDPOINT = "endpoint"
    private const val KEY_LAST_SYNC_AT = "last_sync_at"
    private val jsonType = "application/json; charset=utf-8".toMediaType()

    private val registerPaths = listOf(
        "api/notifications/tokens",
        "api/notifications/devices",
        "api/v2/push-tokens",
    )
    private val deregisterPaths = listOf(
        "api/notifications/tokens",
        "api/notifications/devices",
        "api/v2/push-tokens",
    )

    private val client by lazy {
        OkHttpClient.Builder()
            .callTimeout(10, TimeUnit.SECONDS)
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .build()
    }

    fun syncIfPossible(context: Context) {
        syncIfPossible(context, null)
    }

    fun onNewToken(context: Context, token: String) {
        syncIfPossible(context, token)
    }

    fun onLogout(context: Context) {
        val appContext = context.applicationContext
        thread(name = "nyxguard-push-deregister", isDaemon = true) {
            runCatching {
                deregisterCurrentToken(appContext)
            }.onFailure { error ->
                Log.w(TAG, "Push token deregistration skipped", error)
            }
            clearSyncState(appContext)
        }
    }

    private fun syncIfPossible(context: Context, forcedToken: String?) {
        val appContext = context.applicationContext
        thread(name = "nyxguard-push-sync", isDaemon = true) {
            runCatching {
                syncBlocking(appContext, forcedToken)
            }.onFailure { error ->
                Log.w(TAG, "Push token sync skipped", error)
            }
        }
    }

    private fun syncBlocking(context: Context, forcedToken: String?) {
        if (!FirebaseBootstrap.ensureInitialized(context)) {
            return
        }

        val authToken = TokenManager.getToken(context) ?: return
        val userId = TokenManager.getUserId(context)
        if (userId <= 0) return

        val pushToken = forcedToken ?: resolveFirebaseToken() ?: return
        val state = loadState(context)
        if (state.userId == userId && state.token == pushToken) {
            return
        }

        if (registerToken(context, authToken, userId, pushToken)) {
            saveState(context, userId, pushToken)
        }
    }

    private fun resolveFirebaseToken(): String? {
        val latch = CountDownLatch(1)
        var token: String? = null
        var failed: Throwable? = null

        runCatching {
            FirebaseMessaging.getInstance().token
                .addOnSuccessListener { value ->
                    token = value
                    latch.countDown()
                }
                .addOnFailureListener { error ->
                    failed = error
                    latch.countDown()
                }
        }.onFailure { error ->
            failed = error
            latch.countDown()
        }

        latch.await(10, TimeUnit.SECONDS)
        if (token.isNullOrBlank() && failed != null) {
            Log.i(TAG, "Firebase token is unavailable", failed)
        }
        return token
    }

    private fun registerToken(context: Context, authToken: String, userId: Int, pushToken: String): Boolean {
        val payload = JSONObject()
            .put("token", pushToken)
            .put("platform", "android")
            .put("provider", "fcm")
            .put("user_id", userId)
            .put("app_id", BuildConfig.APPLICATION_ID)
            .put("app_version", BuildConfig.VERSION_NAME)
            .put("build_type", BuildConfig.BUILD_TYPE)
            .put("device_model", Build.MODEL)
            .put("device_manufacturer", Build.MANUFACTURER)

        val body = payload.toString().toRequestBody(jsonType)
        return tryCandidatePost(authToken, body, registerPaths)
    }

    private fun deregisterCurrentToken(context: Context) {
        val authToken = TokenManager.getToken(context) ?: return
        val userId = TokenManager.getUserId(context)
        if (userId <= 0) return

        val state = loadState(context)
        val token = state.token.ifBlank { resolveFirebaseToken().orEmpty() }
        if (token.isBlank()) return

        tryCandidateDelete(authToken, token, deregisterPaths)
    }

    private fun tryCandidatePost(authToken: String, body: okhttp3.RequestBody, paths: List<String>): Boolean {
        val baseUrl = resolveBaseUrl()
        for (path in paths) {
            val request = Request.Builder()
                .url("${baseUrl.trimEnd('/')}/$path")
                .addHeader("Authorization", "Bearer $authToken")
                .post(body)
                .build()

            val response = runCatching { client.newCall(request).execute() }.getOrNull() ?: continue
            val shouldContinue = response.use {
                when {
                    it.isSuccessful -> return true
                    it.code == 404 || it.code == 405 -> true
                    else -> {
                        Log.i(TAG, "Token register rejected at $path with HTTP ${it.code}")
                        return false
                    }
                }
            }
            if (shouldContinue) {
                continue
            }
        }
        return false
    }

    private fun tryCandidateDelete(authToken: String, token: String, paths: List<String>): Boolean {
        val baseUrl = resolveBaseUrl()
        val encodedToken = URLEncoder.encode(token, StandardCharsets.UTF_8.name())
        for (path in paths) {
            val request = Request.Builder()
                .url("${baseUrl.trimEnd('/')}/$path/$encodedToken")
                .addHeader("Authorization", "Bearer $authToken")
                .delete()
                .build()

            val response = runCatching { client.newCall(request).execute() }.getOrNull() ?: continue
            val shouldContinue = response.use {
                when {
                    it.isSuccessful -> return true
                    it.code == 404 || it.code == 405 -> true
                    else -> {
                        Log.i(TAG, "Token deregister rejected at $path with HTTP ${it.code}")
                        return false
                    }
                }
            }
            if (shouldContinue) {
                continue
            }
        }
        return false
    }

    private fun resolveBaseUrl(): String {
        val configuredBaseUrl = BuildConfig.NYXGUARD_API_BASE_URL
        return remapLoopbackForAndroidEmulator(configuredBaseUrl)
    }

    private fun remapLoopbackForAndroidEmulator(baseUrl: String): String {
        val normalized = if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/"
        if (!BuildConfig.DEBUG) {
            return normalized
        }

        val uri = runCatching { URI(normalized) }.getOrNull() ?: return normalized
        val host = uri.host?.lowercase() ?: return normalized
        if (host != "127.0.0.1" && host != "localhost") {
            return normalized
        }

        val port = if (uri.port >= 0) ":${uri.port}" else ""
        val path = uri.rawPath?.takeIf { it.isNotEmpty() } ?: "/"
        val query = uri.rawQuery?.let { "?$it" } ?: ""
        val fragment = uri.rawFragment?.let { "#$it" } ?: ""
        return "${uri.scheme}://10.0.2.2$port$path$query$fragment"
    }

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private data class SyncState(
        val userId: Int,
        val token: String,
        val endpoint: String,
        val lastSyncAt: Long,
    )

    private fun loadState(context: Context): SyncState {
        val store = prefs(context)
        return SyncState(
            userId = store.getInt(KEY_USER_ID, -1),
            token = store.getString(KEY_TOKEN, "").orEmpty(),
            endpoint = store.getString(KEY_ENDPOINT, "").orEmpty(),
            lastSyncAt = store.getLong(KEY_LAST_SYNC_AT, 0L),
        )
    }

    private fun saveState(context: Context, userId: Int, token: String) {
        prefs(context).edit()
            .putInt(KEY_USER_ID, userId)
            .putString(KEY_TOKEN, token)
            .putString(KEY_ENDPOINT, registerPaths.first())
            .putLong(KEY_LAST_SYNC_AT, System.currentTimeMillis())
            .apply()
    }

    private fun clearSyncState(context: Context) {
        prefs(context).edit().clear().apply()
    }
}
