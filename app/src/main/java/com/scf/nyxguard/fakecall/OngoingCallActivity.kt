package com.scf.nyxguard.fakecall

import android.os.Bundle
import android.os.SystemClock
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import com.scf.nyxguard.R
import com.scf.nyxguard.databinding.ActivityOngoingCallBinding

class OngoingCallActivity : AppCompatActivity() {

    private lateinit var binding: ActivityOngoingCallBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.addFlags(
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
            WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
        )

        binding = ActivityOngoingCallBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val callerName = intent.getStringExtra(EXTRA_CALLER_NAME) ?: getString(R.string.unknown_caller)
        binding.callerName.text = callerName

        // 通话计时
        binding.callTimer.base = SystemClock.elapsedRealtime()
        binding.callTimer.start()

        // 挂断
        binding.btnHangup.setOnClickListener {
            binding.callTimer.stop()
            finish()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        binding.callTimer.stop()
    }

    @Deprecated("Use OnBackPressedCallback")
    override fun onBackPressed() {
        // 通话中禁止返回键
    }

    companion object {
        const val EXTRA_CALLER_NAME = "caller_name"
    }
}
