package com.scf.nyxguard.network

import android.content.Context
import com.scf.nyxguard.BuildConfig
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object ApiClient {

    private var retrofit: Retrofit? = null
    private var appContext: Context? = null
    private var overriddenBaseUrl: String? = null

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    /** 动态设置后端地址（如 MuMu 用宿主机 IP） */
    fun init(context: Context, baseUrl: String) {
        appContext = context.applicationContext
        overriddenBaseUrl = normalizeBaseUrl(baseUrl)
        retrofit = buildRetrofit(overriddenBaseUrl!!)
    }

    val service: ApiService
        get() {
            if (retrofit == null) {
                retrofit = buildRetrofit(resolveBaseUrl())
            }
            return retrofit!!.create(ApiService::class.java)
        }

    val authService: AuthApiService
        get() {
            if (retrofit == null) {
                retrofit = buildRetrofit(resolveBaseUrl())
            }
            return retrofit!!.create(AuthApiService::class.java)
        }

    private fun resolveBaseUrl(): String = overriddenBaseUrl ?: normalizeBaseUrl(BuildConfig.NYXGUARD_API_BASE_URL)

    private fun normalizeBaseUrl(baseUrl: String): String =
        if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/"

    private fun buildRetrofit(baseUrl: String): Retrofit {
        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }

        val authInterceptor = Interceptor { chain ->
            val token = appContext?.let { TokenManager.getToken(it) }
            val request = if (token != null) {
                chain.request().newBuilder()
                    .addHeader("Authorization", "Bearer $token")
                    .build()
            } else {
                chain.request()
            }
            chain.proceed(request)
        }

        val client = OkHttpClient.Builder()
            .addInterceptor(authInterceptor)
            .addInterceptor(logging)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()

        return Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }
}
