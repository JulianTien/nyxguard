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
import com.scf.nyxguard.databinding.ActivityRegisterBinding
import com.scf.nyxguard.network.ApiClient
import com.scf.nyxguard.network.MockApiClient
import com.scf.nyxguard.network.RegisterRequest
import com.scf.nyxguard.network.TokenManager
import com.scf.nyxguard.network.enqueue
import com.scf.nyxguard.util.ValidationUtils

class RegisterActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRegisterBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRegisterBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }

        binding.btnRegister.setOnClickListener { doRegister() }
        binding.btnBackLogin.setOnClickListener { finish() }
        
        setupValidation()
    }

    private fun setupValidation() {
        binding.inputNickname.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) {
                val nickname = binding.inputNickname.text?.toString()?.trim() ?: ""
                if (nickname.isNotEmpty() && !ValidationUtils.isValidNickname(nickname)) {
                    binding.layoutNickname.error = "昵称需要2-20个字符"
                } else {
                    binding.layoutNickname.error = null
                }
            }
        }

        binding.inputPhone.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) {
                val phone = binding.inputPhone.text?.toString()?.trim() ?: ""
                if (phone.isNotEmpty() && !ValidationUtils.isValidPhone(phone)) {
                    binding.layoutPhone.error = "请输入正确的手机号"
                } else {
                    binding.layoutPhone.error = null
                }
            }
        }

        binding.inputPassword.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) {
                val password = binding.inputPassword.text?.toString() ?: ""
                if (password.isNotEmpty() && !ValidationUtils.isValidPassword(password)) {
                    binding.layoutPassword.error = "密码需要6-20位，包含字母和数字"
                } else {
                    binding.layoutPassword.error = null
                }
            }
        }
    }

    private fun doRegister() {
        val nickname = binding.inputNickname.text?.toString()?.trim() ?: ""
        val phone = binding.inputPhone.text?.toString()?.trim() ?: ""
        val password = binding.inputPassword.text?.toString() ?: ""

        if (!validateInput(nickname, phone, password)) return

        setLoading(true)
        val request = RegisterRequest(
            nickname = nickname,
            phone = phone.ifBlank { null },
            password = password
        )

        ApiClient.authService.register(request).enqueue(
            onSuccess = { auth -> completeRegister(auth.token, auth.user.id, auth.user.nickname) },
            onError = { msg ->
                if (MockApiClient.isEnabled) {
                    MockApiClient.authService.register(request).enqueue(
                        onSuccess = { auth ->
                            completeRegister(auth.token, auth.user.id, auth.user.nickname)
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

    private fun completeRegister(token: String, userId: Int, nickname: String) {
        TokenManager.saveLogin(this, token, userId, nickname)

        Toast.makeText(this, getString(R.string.register_success), Toast.LENGTH_SHORT).show()
        startActivity(Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        })
        finish()
    }

    private fun validateInput(nickname: String, phone: String, password: String): Boolean {
        if (nickname.isEmpty()) {
            binding.layoutNickname.error = getString(R.string.register_nickname_required)
            return false
        }
        if (!ValidationUtils.isValidNickname(nickname)) {
            binding.layoutNickname.error = "昵称需要2-20个字符"
            return false
        }

        if (phone.isEmpty()) {
            binding.layoutPhone.error = getString(R.string.register_phone_required)
            return false
        }
        if (!ValidationUtils.isValidPhone(phone)) {
            binding.layoutPhone.error = "请输入正确的手机号"
            return false
        }

        if (password.isEmpty()) {
            binding.layoutPassword.error = "请输入密码"
            return false
        }
        if (!ValidationUtils.isValidPassword(password)) {
            binding.layoutPassword.error = "密码需要6-20位，包含字母和数字"
            return false
        }

        return true
    }

    private fun setLoading(loading: Boolean) {
        binding.progress.visibility = if (loading) View.VISIBLE else View.GONE
        binding.btnRegister.isEnabled = !loading
    }
}
