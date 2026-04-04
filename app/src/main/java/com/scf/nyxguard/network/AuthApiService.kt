package com.scf.nyxguard.network

import retrofit2.Call
import retrofit2.http.Body
import retrofit2.http.POST

interface AuthApiService {

    @POST("api/auth/register")
    fun register(@Body body: RegisterRequest): Call<AuthResponse>

    @POST("api/auth/login")
    fun login(@Body body: LoginRequest): Call<AuthResponse>
}
