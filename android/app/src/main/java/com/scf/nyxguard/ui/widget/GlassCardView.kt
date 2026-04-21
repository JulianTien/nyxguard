package com.scf.nyxguard.ui.widget

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.util.AttributeSet
import android.widget.FrameLayout
import androidx.core.content.withStyledAttributes
import com.scf.nyxguard.R

class GlassCardView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()
    private val rectF = RectF()

    private var cornerRadius: Float = 20f * resources.displayMetrics.density
    private var backgroundColor: Int = Color.WHITE
    private var backgroundAlpha: Int = 230

    init {
        setWillNotDraw(false)
        
        context.withStyledAttributes(attrs, R.styleable.GlassCardView) {
            cornerRadius = getDimension(R.styleable.GlassCardView_cornerRadius, cornerRadius)
            backgroundColor = getColor(R.styleable.GlassCardView_glassBackgroundColor, Color.WHITE)
            backgroundAlpha = getInteger(R.styleable.GlassCardView_glassBackgroundAlpha, 230)
        }

        paint.color = backgroundColor
        paint.alpha = backgroundAlpha
        paint.style = Paint.Style.FILL
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        rectF.set(0f, 0f, w.toFloat(), h.toFloat())
        path.reset()
        path.addRoundRect(rectF, cornerRadius, cornerRadius, Path.Direction.CW)
    }

    override fun onDraw(canvas: Canvas) {
        canvas.drawPath(path, paint)
        super.onDraw(canvas)
    }
}
