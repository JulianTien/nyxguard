package com.scf.nyxguard.network

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import com.scf.nyxguard.util.PushTokenSyncManager
import org.json.JSONObject
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

object TokenManager {

    private const val PREFS_NAME = "nyxguard_auth"
    private const val KEY_LEGACY_TOKEN = "jwt_token"
    private const val KEY_ENCRYPTED_TOKEN = "jwt_token_encrypted"
    private const val KEY_TOKEN_IV = "jwt_token_iv"
    private const val KEY_USER_ID = "user_id"
    private const val KEY_NICKNAME = "nickname"
    private const val EXPIRY_SKEW_MILLIS = 30_000L
    private const val KEYSTORE_PROVIDER = "AndroidKeyStore"
    private const val KEY_ALIAS = "nyxguard_jwt_token_key"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val GCM_TAG_LENGTH_BITS = 128

    private data class EncryptedToken(
        val iv: String,
        val ciphertext: String,
    )

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun saveLogin(context: Context, token: String, userId: Int, nickname: String) {
        val encryptedToken = encryptToken(token)
        prefs(context).edit()
            .remove(KEY_LEGACY_TOKEN)
            .putString(KEY_TOKEN_IV, encryptedToken.iv)
            .putString(KEY_ENCRYPTED_TOKEN, encryptedToken.ciphertext)
            .putInt(KEY_USER_ID, userId)
            .putString(KEY_NICKNAME, nickname)
            .apply()
    }

    fun getToken(context: Context): String? {
        migrateLegacyTokenIfNeeded(context)
        val store = prefs(context)
        val iv = store.getString(KEY_TOKEN_IV, null) ?: return null
        val ciphertext = store.getString(KEY_ENCRYPTED_TOKEN, null) ?: return null
        return decryptToken(iv, ciphertext)
    }

    fun getUserId(context: Context): Int = prefs(context).getInt(KEY_USER_ID, -1)

    fun getNickname(context: Context): String = prefs(context).getString(KEY_NICKNAME, "") ?: ""

    fun isLoggedIn(context: Context): Boolean = getToken(context) != null

    fun hasValidSession(context: Context): Boolean {
        val token = getToken(context) ?: return false
        if (isTokenExpired(token)) {
            clearStoredLogin(context)
            return false
        }
        return true
    }

    fun clearStoredLogin(context: Context) {
        prefs(context).edit().clear().apply()
    }

    fun logout(context: Context) {
        PushTokenSyncManager.onLogout(context)
        clearStoredLogin(context)
    }

    private fun isTokenExpired(token: String, nowMillis: Long = System.currentTimeMillis()): Boolean {
        val expiryMillis = parseExpiryMillis(token) ?: return true
        return nowMillis >= (expiryMillis - EXPIRY_SKEW_MILLIS)
    }

    private fun parseExpiryMillis(token: String): Long? {
        val parts = token.split(".")
        if (parts.size < 2) return null

        return runCatching {
            val payload = Base64.decode(parts[1], Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
            val payloadJson = JSONObject(String(payload, Charsets.UTF_8))
            val expSeconds = payloadJson.optLong("exp", 0L)
            expSeconds.takeIf { it > 0L }?.times(1000L)
        }.getOrNull()
    }

    private fun migrateLegacyTokenIfNeeded(context: Context) {
        val store = prefs(context)
        if (!store.getString(KEY_ENCRYPTED_TOKEN, null).isNullOrBlank()) {
            return
        }

        val legacyToken = store.getString(KEY_LEGACY_TOKEN, null) ?: return
        val encryptedToken = encryptToken(legacyToken)
        store.edit()
            .remove(KEY_LEGACY_TOKEN)
            .putString(KEY_TOKEN_IV, encryptedToken.iv)
            .putString(KEY_ENCRYPTED_TOKEN, encryptedToken.ciphertext)
            .apply()
    }

    private fun encryptToken(token: String): EncryptedToken {
        return runCatching {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, getOrCreateSecretKey())
            val encryptedBytes = cipher.doFinal(token.toByteArray(Charsets.UTF_8))
            EncryptedToken(
                iv = encodeBase64(cipher.iv),
                ciphertext = encodeBase64(encryptedBytes),
            )
        }.getOrElse { error ->
            throw IllegalStateException("Failed to encrypt auth token", error)
        }
    }

    private fun decryptToken(iv: String, ciphertext: String): String? {
        return runCatching {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            val spec = GCMParameterSpec(GCM_TAG_LENGTH_BITS, decodeBase64(iv))
            cipher.init(Cipher.DECRYPT_MODE, getOrCreateSecretKey(), spec)
            val decryptedBytes = cipher.doFinal(decodeBase64(ciphertext))
            String(decryptedBytes, Charsets.UTF_8)
        }.getOrNull()
    }

    private fun getOrCreateSecretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER).apply { load(null) }
        val existing = keyStore.getKey(KEY_ALIAS, null) as? SecretKey
        if (existing != null) {
            return existing
        }

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE_PROVIDER)
        val spec = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setRandomizedEncryptionRequired(true)
            .build()
        generator.init(spec)
        return generator.generateKey()
    }

    private fun encodeBase64(bytes: ByteArray): String =
        Base64.encodeToString(bytes, Base64.NO_WRAP)

    private fun decodeBase64(value: String): ByteArray =
        Base64.decode(value, Base64.NO_WRAP)
}
