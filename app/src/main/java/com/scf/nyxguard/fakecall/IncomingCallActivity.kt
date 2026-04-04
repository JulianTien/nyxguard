package com.scf.nyxguard.fakecall

import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.Ringtone
import android.media.RingtoneManager
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import com.scf.nyxguard.R
import com.scf.nyxguard.databinding.ActivityIncomingCallBinding

class IncomingCallActivity : AppCompatActivity() {

    private lateinit var binding: ActivityIncomingCallBinding
    private var ringtone: Ringtone? = null
    private var vibrator: Vibrator? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 全屏沉浸 + 在锁屏上方显示
        window.addFlags(
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
            WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
            WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
        )

        binding = ActivityIncomingCallBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val callerName = intent.getStringExtra(EXTRA_CALLER_NAME) ?: getString(R.string.unknown_caller)
        val callerPhone = intent.getStringExtra(EXTRA_CALLER_PHONE) ?: ""

        binding.callerName.text = callerName
        binding.callerPhone.text = callerPhone

        binding.btnAnswer.setOnClickListener {
            stopRinging()
            val intent = Intent(this, OngoingCallActivity::class.java).apply {
                putExtra(OngoingCallActivity.EXTRA_CALLER_NAME, callerName)
            }
            startActivity(intent)
            finish()
        }

        binding.btnReject.setOnClickListener {
            stopRinging()
            finish()
        }

        startRinging()
    }

    private fun startRinging() {
        // 播放系统铃声
        try {
            val ringtoneUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
            ringtone = RingtoneManager.getRingtone(this, ringtoneUri)?.apply {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    isLooping = true
                }
                audioAttributes = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
                play()
            }
        } catch (_: Exception) { }

        // 震动
        try {
            vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
                vm.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val pattern = longArrayOf(0, 800, 400, 800, 400)
                vibrator?.vibrate(VibrationEffect.createWaveform(pattern, 0))
            }
        } catch (_: Exception) { }
    }

    private fun stopRinging() {
        ringtone?.stop()
        ringtone = null
        vibrator?.cancel()
        vibrator = null
    }

    override fun onDestroy() {
        super.onDestroy()
        stopRinging()
    }

    companion object {
        const val EXTRA_CALLER_NAME = "caller_name"
        const val EXTRA_CALLER_PHONE = "caller_phone"
    }
}
