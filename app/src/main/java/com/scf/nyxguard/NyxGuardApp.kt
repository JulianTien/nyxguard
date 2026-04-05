package com.scf.nyxguard

import android.app.Application
import com.scf.nyxguard.common.ThemePreferenceStore
import com.scf.nyxguard.util.AmapSdkInitializer

class NyxGuardApp : Application() {
    override fun onCreate() {
        super.onCreate()
        AmapSdkInitializer.ensureInitialized(this)
        ThemePreferenceStore.applyStoredTheme(this)
        LocaleManager.initialize(this)
    }
}
