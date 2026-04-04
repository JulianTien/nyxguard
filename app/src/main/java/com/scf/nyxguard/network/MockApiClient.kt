package com.scf.nyxguard.network

import com.scf.nyxguard.BuildConfig
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.mock.MockRetrofit
import retrofit2.mock.NetworkBehavior

object MockApiClient {

    val isEnabled: Boolean
        get() = BuildConfig.DEBUG && BuildConfig.NYXGUARD_ENABLE_DEBUG_MOCK_FALLBACK

    private val retrofit = Retrofit.Builder()
        .baseUrl("https://mock.nyxguard.com/")
        .addConverterFactory(GsonConverterFactory.create())
        .build()

    private val behavior = NetworkBehavior.create()
    
    private val mockRetrofit = MockRetrofit.Builder(retrofit)
        .networkBehavior(behavior)
        .build()

    private val delegate = mockRetrofit.create(ApiService::class.java)
    private val authDelegate = mockRetrofit.create(AuthApiService::class.java)

    val service: ApiService
        get() = if (isEnabled) MockApiService(delegate) else ApiClient.service

    val authService: AuthApiService
        get() = if (isEnabled) MockAuthApiService(authDelegate) else ApiClient.authService
}
