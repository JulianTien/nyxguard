package com.scf.nyxguard.fakecall

import android.content.Intent
import android.os.Bundle
import android.os.CountDownTimer
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.scf.nyxguard.R
import com.scf.nyxguard.databinding.ActivityFakeCallSettingBinding

class FakeCallSettingActivity : AppCompatActivity() {

    private lateinit var binding: ActivityFakeCallSettingBinding
    private var countdownTimer: CountDownTimer? = null
    private var countdownRunning = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityFakeCallSettingBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.coordinator) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }

        binding.toolbar.setNavigationOnClickListener { finish() }
        binding.countdownText.text = getString(R.string.edge_fake_call_preview)

        binding.btnStart.setOnClickListener {
            if (countdownRunning) {
                cancelCountdown()
            } else {
                startCountdown()
            }
        }
    }

    private fun getSelectedDelay(): Long {
        return when (binding.delayChipGroup.checkedChipId) {
            R.id.chip_now -> 0L
            R.id.chip_10s -> 10_000L
            R.id.chip_30s -> 30_000L
            R.id.chip_1m -> 60_000L
            R.id.chip_5m -> 300_000L
            else -> 0L
        }
    }

    private fun startCountdown() {
        val callerName = binding.inputCallerName.text?.toString()?.trim()
        val callerPhone = binding.inputCallerPhone.text?.toString()?.trim()

        if (callerName.isNullOrEmpty()) {
            binding.inputCallerName.error = getString(R.string.fake_call_name_required)
            return
        }

        val delay = getSelectedDelay()
        if (delay == 0L) {
            launchIncomingCall(callerName, callerPhone ?: "")
            return
        }

        countdownRunning = true
        binding.btnStart.text = getString(R.string.fake_call_cancel)
        binding.countdownText.visibility = View.VISIBLE
        binding.delayChipGroup.isEnabled = false

        countdownTimer = object : CountDownTimer(delay, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                val seconds = millisUntilFinished / 1000
                val display = String.format("%d:%02d", seconds / 60, seconds % 60)
                binding.countdownText.text = getString(R.string.edge_fake_call_countdown, display)
            }

            override fun onFinish() {
                countdownRunning = false
                binding.countdownText.visibility = View.GONE
                binding.btnStart.text = getString(R.string.fake_call_start)
                launchIncomingCall(callerName, callerPhone ?: "")
            }
        }.start()
    }

    private fun cancelCountdown() {
        countdownTimer?.cancel()
        countdownRunning = false
        binding.countdownText.visibility = View.GONE
        binding.btnStart.text = getString(R.string.fake_call_start)
        binding.delayChipGroup.isEnabled = true
    }

    private fun launchIncomingCall(name: String, phone: String) {
        val intent = Intent(this, IncomingCallActivity::class.java).apply {
            putExtra(IncomingCallActivity.EXTRA_CALLER_NAME, name)
            putExtra(IncomingCallActivity.EXTRA_CALLER_PHONE, phone)
        }
        startActivity(intent)
        finish()
    }

    override fun onDestroy() {
        super.onDestroy()
        countdownTimer?.cancel()
    }
}
