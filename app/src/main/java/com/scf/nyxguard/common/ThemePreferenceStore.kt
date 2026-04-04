package com.scf.nyxguard.common

import android.content.Context
import android.content.SharedPreferences
import androidx.appcompat.app.AppCompatDelegate

enum class ThemePreference {
    SYSTEM,
    LIGHT,
    DARK,
}

object ThemePreferenceStore {

    private const val PREFS_NAME = "nyxguard_theme"
    private const val KEY_THEME = "theme_preference"

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getThemePreference(context: Context): ThemePreference {
        val stored = prefs(context).getString(KEY_THEME, ThemePreference.SYSTEM.name)
        return ThemePreference.entries.firstOrNull { it.name == stored } ?: ThemePreference.SYSTEM
    }

    fun setThemePreference(context: Context, preference: ThemePreference) {
        prefs(context).edit()
            .putString(KEY_THEME, preference.name)
            .apply()
        apply(preference)
    }

    fun toggleLightDark(context: Context) {
        val next = when (getThemePreference(context)) {
            ThemePreference.DARK -> ThemePreference.LIGHT
            ThemePreference.LIGHT -> ThemePreference.DARK
            ThemePreference.SYSTEM -> ThemePreference.DARK
        }
        setThemePreference(context, next)
    }

    fun applyStoredTheme(context: Context) {
        apply(getThemePreference(context))
    }

    private fun apply(preference: ThemePreference) {
        val mode = when (preference) {
            ThemePreference.SYSTEM -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
            ThemePreference.LIGHT -> AppCompatDelegate.MODE_NIGHT_NO
            ThemePreference.DARK -> AppCompatDelegate.MODE_NIGHT_YES
        }
        AppCompatDelegate.setDefaultNightMode(mode)
    }
}
