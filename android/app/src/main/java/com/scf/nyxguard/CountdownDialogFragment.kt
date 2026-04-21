package com.scf.nyxguard

import android.os.Bundle
import android.os.CountDownTimer
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.DialogFragment
import com.google.android.material.button.MaterialButton

class CountdownDialogFragment : DialogFragment() {

    private var countdownText: TextView? = null
    private var cancelButton: MaterialButton? = null
    private var countdownTimer: CountDownTimer? = null
    private var onSOSConfirmed: (() -> Unit)? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_countdown_dialog, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        countdownText = view.findViewById(R.id.countdown_text)
        cancelButton = view.findViewById(R.id.cancel_button)
        
        cancelButton?.setOnClickListener {
            cancelAndDismiss()
        }
        
        startCountdown()
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.setLayout(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
        dialog?.setCancelable(false)
    }

    private fun startCountdown() {
        var secondsLeft = 5
        countdownText?.text = secondsLeft.toString()
        
        countdownTimer = object : CountDownTimer(5000, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                secondsLeft--
                countdownText?.text = secondsLeft.toString()
            }
            
            override fun onFinish() {
                onSOSConfirmed?.invoke()
                dismissAllowingStateLoss()
            }
        }.start()
    }

    private fun cancelAndDismiss() {
        countdownTimer?.cancel()
        countdownTimer = null
        dismissAllowingStateLoss()
    }

    fun setOnSOSConfirmedListener(listener: () -> Unit) {
        onSOSConfirmed = listener
    }

    override fun onDestroyView() {
        super.onDestroyView()
        countdownTimer?.cancel()
        countdownTimer = null
        countdownText = null
        cancelButton = null
    }
}
