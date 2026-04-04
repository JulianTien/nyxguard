package com.scf.nyxguard.model

data class User(
    val id: Int,
    val nickname: String,
    val email: String? = null,
    val phone: String? = null,
    val avatarUrl: String? = null,
    val emergencyPhone: String? = null,
    val createdAt: String? = null
)

data class LoginRequest(
    val account: String,
    val password: String
)

data class RegisterRequest(
    val nickname: String,
    val phone: String? = null,
    val email: String? = null,
    val password: String
)

data class LoginResponse(
    val token: String,
    val userId: Int,
    val nickname: String
)

data class ApiResponse<T>(
    val success: Boolean,
    val data: T? = null,
    val message: String? = null
)
