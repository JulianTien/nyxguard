package com.scf.nyxguard

import android.app.Application
import com.scf.nyxguard.common.ThemePreferenceStore

class NyxGuardApp : Application() {
    override fun onCreate() {
        super.onCreate()
        ThemePreferenceStore.applyStoredTheme(this)
        LocaleManager.initialize(this)
    }
}
