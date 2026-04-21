package com.scf.nyxguard.ui

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.scf.nyxguard.MainActivity
import com.scf.nyxguard.R
import com.scf.nyxguard.databinding.ActivityLoginBinding
import com.scf.nyxguard.network.ApiClient
import com.scf.nyxguard.network.LoginRequest
import com.scf.nyxguard.network.MockApiClient
import com.scf.nyxguard.network.TokenManager
import com.scf.nyxguard.network.enqueue

class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }

        binding.btnLogin.setOnClickListener { doLogin() }
        binding.btnRegister.setOnClickListener {
            startActivity(Intent(this, RegisterActivity::class.java))
        }
    }

    private fun doLogin() {
        val account = binding.inputAccount.text?.toString()?.trim() ?: ""
        val password = binding.inputPassword.text?.toString() ?: ""

        if (account.isEmpty() || password.isEmpty()) {
            Toast.makeText(this, getString(R.string.login_missing_credentials), Toast.LENGTH_SHORT).show()
            return
        }

        setLoading(true)
        val request = LoginRequest(account = account, password = password)

        ApiClient.authService.login(request)
            .enqueue(
                onSuccess = { auth -> completeLogin(auth.token, auth.user.id, auth.user.nickname) },
                onError = { msg ->
                    if (MockApiClient.isEnabled) {
                        MockApiClient.authService.login(request).enqueue(
                            onSuccess = { auth ->
                                completeLogin(auth.token, auth.user.id, auth.user.nickname)
                            },
                            onError = { fallbackMsg ->
                                setLoading(false)
                                Toast.makeText(this, fallbackMsg.ifBlank { msg }, Toast.LENGTH_SHORT).show()
                            }
                        )
                    } else {
                        setLoading(false)
                        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
                    }
                }
            )
    }

    private fun completeLogin(token: String, userId: Int, nickname: String) {
        TokenManager.saveLogin(this, token, userId, nickname)

        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }

    private fun setLoading(loading: Boolean) {
        binding.progress.visibility = if (loading) View.VISIBLE else View.GONE
        binding.btnLogin.isEnabled = !loading
    }
}
