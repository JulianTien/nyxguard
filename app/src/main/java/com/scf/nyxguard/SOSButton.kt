package com.scf.nyxguard

import android.animation.ObjectAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.drawable.ShapeDrawable
import android.graphics.drawable.shapes.OvalShape
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateDecelerateInterpolator
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import com.google.android.material.button.MaterialButton
import com.google.android.material.progressindicator.CircularProgressIndicator

class SOSButton @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : ConstraintLayout(context, attrs, defStyleAttr) {

    private var button: MaterialButton? = null
    private var progressIndicator: CircularProgressIndicator? = null
    private var gestureDetector: GestureDetector? = null
    private var isLongPressing = false
    private var progressAnimator: ObjectAnimator? = null
    private var onSOSActivated: (() -> Unit)? = null

    init {
        setupView()
        setupGestureDetector()
    }

    private fun setupView() {
        button = MaterialButton(context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            text = "SOS"
            textSize = 24f
            setTextColor(ContextCompat.getColor(context, android.R.color.white))
            setBackgroundColor(ContextCompat.getColor(context, R.color.sos_emergency_red))
            cornerRadius = 100
        }
        addView(button)
        
        progressIndicator = CircularProgressIndicator(context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            setIndicatorSize(180)
            setIndicatorColor(ContextCompat.getColor(context, android.R.color.white))
            trackColor = ContextCompat.getColor(context, android.R.color.transparent)
            progress = 0
            max = 100
            visibility = View.GONE
        }
        addView(progressIndicator)
    }

    private fun setupGestureDetector() {
        gestureDetector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
            override fun onLongPress(e: MotionEvent) {
                startLongPressAnimation()
            }
        })
    }

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        gestureDetector?.onTouchEvent(ev)
        return super.onInterceptTouchEvent(ev)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        gestureDetector?.onTouchEvent(event)
        
        when (event.action) {
            MotionEvent.ACTION_UP,
            MotionEvent.ACTION_CANCEL -> {
                if (isLongPressing) {
                    cancelLongPress()
                }
            }
        }
        
        return true
    }

    private fun startLongPressAnimation() {
        if (isLongPressing) return
        
        isLongPressing = true
        progressIndicator?.visibility = View.VISIBLE
        
        ObjectAnimator.ofFloat(this, View.SCALE_X, 1f, 0.9f).apply {
            duration = 300
            interpolator = AccelerateDecelerateInterpolator()
            start()
        }
        
        ObjectAnimator.ofFloat(this, View.SCALE_Y, 1f, 0.9f).apply {
            duration = 300
            interpolator = AccelerateDecelerateInterpolator()
            start()
        }
        
        progressAnimator = ObjectAnimator.ofInt(progressIndicator, "progress", 0, 100).apply {
            duration = 3000
            start()
            addUpdateListener { animation ->
                val progress = animation.animatedValue as Int
                if (progress == 100) {
                    activateSOS()
                }
            }
        }
    }

    override fun cancelLongPress() {
        super.cancelLongPress()
        isLongPressing = false
        progressIndicator?.visibility = View.GONE
        progressIndicator?.progress = 0
        progressAnimator?.cancel()
        
        ObjectAnimator.ofFloat(this, View.SCALE_X, 0.9f, 1f).apply {
            duration = 300
            interpolator = AccelerateDecelerateInterpolator()
            start()
        }
        
        ObjectAnimator.ofFloat(this, View.SCALE_Y, 0.9f, 1f).apply {
            duration = 300
            interpolator = AccelerateDecelerateInterpolator()
            start()
        }
    }

    private fun activateSOS() {
        isLongPressing = false
        onSOSActivated?.invoke()
    }

    fun setOnSOSActivatedListener(listener: () -> Unit) {
        onSOSActivated = listener
    }
}
