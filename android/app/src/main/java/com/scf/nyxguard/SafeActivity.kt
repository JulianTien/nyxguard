package com.scf.nyxguard

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.scf.nyxguard.databinding.ActivitySafeBinding
import com.scf.nyxguard.network.ApiClient
import com.scf.nyxguard.network.TokenManager
import com.scf.nyxguard.ui.LoginActivity

class SafeActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySafeBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivitySafeBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupWindowInsets()

        // 初始化网络层
        ApiClient.init(this)

        Handler(Looper.getMainLooper()).postDelayed({
            // 检查用户是否已登录
            val target = if (TokenManager.isLoggedIn(this)) {
                // 已登录，跳转到主页面
                Intent(this, MainActivity::class.java)
            } else {
                // 未登录，跳转到登录页面
                Intent(this, LoginActivity::class.java)
            }
            startActivity(target)
            finish()
        }, 1500)
    }

    private fun setupWindowInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.coordinatorLayout) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
    }
}
