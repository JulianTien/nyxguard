package com.scf.nyxguard

import android.app.Activity
import android.content.Context
import android.content.Intent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.scf.nyxguard.network.TokenManager
import com.scf.nyxguard.ui.LoginActivity
import org.junit.After
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SafeActivityInstrumentedTest {

    private val instrumentation
        get() = InstrumentationRegistry.getInstrumentation()

    private val context: Context
        get() = instrumentation.targetContext

    @After
    fun tearDown() {
        TokenManager.clearStoredLogin(context)
    }

    @Test
    fun launchesLoginWhenSessionMissing() {
        launchAndWaitFor(LoginActivity::class.java)
    }

    @Test
    fun launchesMainWhenSessionIsValid() {
        TokenManager.saveLogin(context, VALID_TOKEN, USER_ID, NICKNAME)
        launchAndWaitFor(MainActivity::class.java)
    }

    @Test
    fun launchesLoginWhenSessionExpired() {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putString(LEGACY_TOKEN_KEY, EXPIRED_TOKEN)
            .putInt(USER_ID_KEY, USER_ID)
            .putString(NICKNAME_KEY, NICKNAME)
            .apply()

        launchAndWaitFor(LoginActivity::class.java)
    }

    private fun launchAndWaitFor(target: Class<out Activity>) {
        val monitor = instrumentation.addMonitor(target.name, null, false)
        context.startActivity(
            Intent(context, SafeActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        )
        val launchedActivity = instrumentation.waitForMonitorWithTimeout(monitor, ACTIVITY_TIMEOUT_MS)
        assertNotNull("Expected ${target.simpleName} to launch", launchedActivity)
        launchedActivity?.finish()
        instrumentation.removeMonitor(monitor)
    }

    private companion object {
        const val PREFS_NAME = "nyxguard_auth"
        const val LEGACY_TOKEN_KEY = "jwt_token"
        const val USER_ID_KEY = "user_id"
        const val NICKNAME_KEY = "nickname"
        const val USER_ID = 7
        const val NICKNAME = "Nyx"
        const val ACTIVITY_TIMEOUT_MS = 7_000L
        const val VALID_TOKEN =
            "eyJhbGciOiJIUzI1NiJ9.eyJleHAiOjQ3MDAwMDAwMDAsInN1YiI6IjcifQ.signature"
        const val EXPIRED_TOKEN =
            "eyJhbGciOiJIUzI1NiJ9.eyJleHAiOjEwLCJzdWIiOiI3In0.signature"
    }
}
