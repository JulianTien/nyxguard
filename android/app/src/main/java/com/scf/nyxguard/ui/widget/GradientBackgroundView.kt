package com.scf.nyxguard.ui.widget

import android.content.Context
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import com.scf.nyxguard.R

class GradientBackgroundView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var gradient: LinearGradient? = null

    private var startColor: Int = ContextCompat.getColor(context, R.color.ng_primary)
    private var centerColor: Int = ContextCompat.getColor(context, R.color.ng_secondary)
    private var endColor: Int = ContextCompat.getColor(context, R.color.sos_warning_orange)

    init {
        attrs?.let {
            val typedArray = context.obtainStyledAttributes(it, R.styleable.GradientBackgroundView)
            startColor = typedArray.getColor(R.styleable.GradientBackgroundView_startColor, startColor)
            centerColor = typedArray.getColor(R.styleable.GradientBackgroundView_centerColor, centerColor)
            endColor = typedArray.getColor(R.styleable.GradientBackgroundView_endColor, endColor)
            typedArray.recycle()
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        gradient = LinearGradient(
            0f, 0f,
            w.toFloat(), h.toFloat(),
            intArrayOf(startColor, centerColor, endColor),
            floatArrayOf(0f, 0.5f, 1f),
            Shader.TileMode.CLAMP
        )
        paint.shader = gradient
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
    }
}
