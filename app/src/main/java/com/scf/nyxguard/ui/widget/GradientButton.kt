package com.scf.nyxguard.ui.widget

import android.content.Context
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.util.AttributeSet
import androidx.appcompat.widget.AppCompatButton
import androidx.core.content.ContextCompat
import com.scf.nyxguard.R

class GradientButton @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : AppCompatButton(context, attrs, defStyleAttr) {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var gradient: LinearGradient? = null

    private var startColor: Int = ContextCompat.getColor(context, R.color.ng_secondary)
    private var endColor: Int = ContextCompat.getColor(context, R.color.sos_warning_orange)
    private var cornerRadius: Float = 24f * resources.displayMetrics.density

    init {
        paint.style = Paint.Style.FILL
        paint.isAntiAlias = true
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        gradient = LinearGradient(
            0f, 0f,
            w.toFloat(), 0f,
            startColor,
            endColor,
            Shader.TileMode.CLAMP
        )
        paint.shader = gradient
    }

    override fun onDraw(canvas: Canvas) {
        val rect = android.graphics.RectF(0f, 0f, width.toFloat(), height.toFloat())
        canvas.drawRoundRect(rect, cornerRadius, cornerRadius, paint)
        super.onDraw(canvas)
    }
}
