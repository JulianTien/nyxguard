package com.scf.nyxguard

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat

object LocaleManager {

    const val LANGUAGE_ENGLISH = "en"
    const val LANGUAGE_SIMPLIFIED_CHINESE = "zh-CN"

    private const val PREFS_NAME = "app_settings"
    private const val KEY_LANGUAGE_TAG = "language_tag"

    fun initialize(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val savedTag = prefs.getString(KEY_LANGUAGE_TAG, null) ?: LANGUAGE_ENGLISH.also { tag ->
            prefs.edit().putString(KEY_LANGUAGE_TAG, tag).apply()
        }
        applyLanguage(savedTag)
    }

    fun setLanguage(context: Context, languageTag: String) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_LANGUAGE_TAG, languageTag)
            .apply()
        applyLanguage(languageTag)
    }

    fun currentLanguageTag(context: Context? = null): String {
        val appLocales = AppCompatDelegate.getApplicationLocales().toLanguageTags()
        if (appLocales.isNotBlank()) {
            return normalizeTag(appLocales)
        }

        val savedTag = context?.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            ?.getString(KEY_LANGUAGE_TAG, null)
        return normalizeTag(savedTag ?: LANGUAGE_ENGLISH)
    }

    fun isChinese(context: Context? = null): Boolean {
        return currentLanguageTag(context).startsWith("zh", ignoreCase = true)
    }

    private fun applyLanguage(languageTag: String) {
        AppCompatDelegate.setApplicationLocales(
            LocaleListCompat.forLanguageTags(normalizeTag(languageTag))
        )
    }

    private fun normalizeTag(languageTag: String): String {
        return if (languageTag.startsWith("zh", ignoreCase = true)) {
            LANGUAGE_SIMPLIFIED_CHINESE
        } else {
            LANGUAGE_ENGLISH
        }
    }
}
