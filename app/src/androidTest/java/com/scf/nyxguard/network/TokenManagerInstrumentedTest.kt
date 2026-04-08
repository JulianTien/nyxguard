package com.scf.nyxguard.network

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TokenManagerInstrumentedTest {

    private val context: Context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    @After
    fun tearDown() {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().clear().apply()
    }

    @Test
    fun saveLogin_encryptsTokenAndPreservesMetadata() {
        TokenManager.saveLogin(context, TOKEN, USER_ID, NICKNAME)

        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        assertEquals(USER_ID, TokenManager.getUserId(context))
        assertEquals(NICKNAME, TokenManager.getNickname(context))
        assertEquals(TOKEN, TokenManager.getToken(context))
        assertFalse(prefs.contains(LEGACY_TOKEN_KEY))
        assertNotNull(prefs.getString(ENCRYPTED_TOKEN_KEY, null))
        assertNotNull(prefs.getString(TOKEN_IV_KEY, null))
    }

    @Test
    fun getToken_migratesLegacyPlaintextToken() {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putString(LEGACY_TOKEN_KEY, TOKEN)
            .putInt(USER_ID_KEY, USER_ID)
            .putString(NICKNAME_KEY, NICKNAME)
            .apply()

        assertEquals(TOKEN, TokenManager.getToken(context))
        assertFalse(prefs.contains(LEGACY_TOKEN_KEY))
        assertNotNull(prefs.getString(ENCRYPTED_TOKEN_KEY, null))
        assertNotNull(prefs.getString(TOKEN_IV_KEY, null))
    }

    @Test
    fun hasValidSession_clearsExpiredLegacyToken() {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putString(LEGACY_TOKEN_KEY, EXPIRED_TOKEN)
            .putInt(USER_ID_KEY, USER_ID)
            .putString(NICKNAME_KEY, NICKNAME)
            .apply()

        assertFalse(TokenManager.hasValidSession(context))
        assertNull(TokenManager.getToken(context))
        assertTrue(prefs.all.isEmpty())
    }

    @Test
    fun logout_clearsEncryptedTokenState() {
        TokenManager.saveLogin(context, TOKEN, USER_ID, NICKNAME)

        TokenManager.logout(context)

        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        assertNull(TokenManager.getToken(context))
        assertTrue(prefs.all.isEmpty())
    }

    private companion object {
        const val PREFS_NAME = "nyxguard_auth"
        const val LEGACY_TOKEN_KEY = "jwt_token"
        const val ENCRYPTED_TOKEN_KEY = "jwt_token_encrypted"
        const val TOKEN_IV_KEY = "jwt_token_iv"
        const val USER_ID_KEY = "user_id"
        const val NICKNAME_KEY = "nickname"
        const val USER_ID = 7
        const val NICKNAME = "Nyx"
        const val TOKEN =
            "eyJhbGciOiJIUzI1NiJ9.eyJleHAiOjQ3MDAwMDAwMDAsInN1YiI6IjcifQ.signature"
        const val EXPIRED_TOKEN =
            "eyJhbGciOiJIUzI1NiJ9.eyJleHAiOjEwLCJzdWIiOiI3In0.signature"
    }
}
