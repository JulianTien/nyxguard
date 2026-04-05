package com.scf.nyxguard.network

import android.content.Context
import android.content.SharedPreferences
import com.scf.nyxguard.util.PushTokenSyncManager

object TokenManager {

    private const val PREFS_NAME = "nyxguard_auth"
    private const val KEY_TOKEN = "jwt_token"
    private const val KEY_USER_ID = "user_id"
    private const val KEY_NICKNAME = "nickname"

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun saveLogin(context: Context, token: String, userId: Int, nickname: String) {
        prefs(context).edit()
            .putString(KEY_TOKEN, token)
            .putInt(KEY_USER_ID, userId)
            .putString(KEY_NICKNAME, nickname)
            .apply()
    }

    fun getToken(context: Context): String? = prefs(context).getString(KEY_TOKEN, null)

    fun getUserId(context: Context): Int = prefs(context).getInt(KEY_USER_ID, -1)

    fun getNickname(context: Context): String = prefs(context).getString(KEY_NICKNAME, "") ?: ""

    fun isLoggedIn(context: Context): Boolean = getToken(context) != null

    fun logout(context: Context) {
        PushTokenSyncManager.onLogout(context)
        prefs(context).edit().clear().apply()
    }
}
