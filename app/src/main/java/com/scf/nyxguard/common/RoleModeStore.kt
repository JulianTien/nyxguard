package com.scf.nyxguard.common

import android.content.Context
import android.content.SharedPreferences

enum class RoleMode(val storageValue: String) {
    TRAVELER("traveler"),
    GUARDIAN("guardian");

    companion object {
        fun fromStorage(value: String?): RoleMode {
            return entries.firstOrNull { it.storageValue == value } ?: TRAVELER
        }
    }
}

object RoleModeStore {

    private const val PREFS_NAME = "nyxguard_role_mode"
    private const val KEY_ROLE = "role_mode"

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun get(context: Context): RoleMode {
        return RoleMode.fromStorage(prefs(context).getString(KEY_ROLE, RoleMode.TRAVELER.storageValue))
    }

    fun set(context: Context, roleMode: RoleMode) {
        prefs(context).edit()
            .putString(KEY_ROLE, roleMode.storageValue)
            .apply()
    }

    fun toggle(context: Context): RoleMode {
        val next = when (get(context)) {
            RoleMode.TRAVELER -> RoleMode.GUARDIAN
            RoleMode.GUARDIAN -> RoleMode.TRAVELER
        }
        set(context, next)
        return next
    }

    fun isGuardian(context: Context): Boolean = get(context) == RoleMode.GUARDIAN
}
