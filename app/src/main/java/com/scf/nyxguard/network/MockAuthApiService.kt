package com.scf.nyxguard.network

import retrofit2.Call
import retrofit2.mock.BehaviorDelegate

class MockAuthApiService(private val delegate: BehaviorDelegate<AuthApiService>) : AuthApiService {

    override fun register(body: RegisterRequest): Call<AuthResponse> {
        val response = AuthResponse(
            token = "mock_token_${System.currentTimeMillis()}",
            user = UserDto(
                id = (1000..9999).random(),
                nickname = body.nickname.ifBlank { "测试用户" },
                phone = body.phone,
                email = body.email
            )
        )
        return delegate.returningResponse(response).register(body)
    }

    override fun login(body: LoginRequest): Call<AuthResponse> {
        val account = body.account.ifBlank { "mock@nyxguard.app" }
        val response = AuthResponse(
            token = "mock_token_${System.currentTimeMillis()}",
            user = UserDto(
                id = (1000..9999).random(),
                nickname = "测试用户",
                phone = account.takeIf { it.all(Char::isDigit) },
                email = account.takeIf { it.contains("@") }
            )
        )
        return delegate.returningResponse(response).login(body)
    }
}
