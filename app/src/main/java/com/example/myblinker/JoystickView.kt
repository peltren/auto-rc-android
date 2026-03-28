package com.example.myblinker

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.animation.DecelerateInterpolator
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Joystick circular: centro = (512, 512), rango por eje 0–1023.
 * X crece hacia la derecha, Y hacia abajo (coordenadas de pantalla).
 */
class JoystickView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    var onPositionChange: ((x: Int, y: Int, fromUser: Boolean) -> Unit)? = null
    var onRelease: (() -> Unit)? = null
    var onSnapAnimationEnd: (() -> Unit)? = null

    private var knobOffsetX = 0f
    private var knobOffsetY = 0f

    private var centerX = 0f
    private var centerY = 0f
    private var travelRadius = 0f
    private var knobDrawRadius = 0f

    private val paintArea =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFFE8E8ED.toInt()
            style = Paint.Style.FILL
        }
    private val paintBorder =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFFCCCCCC.toInt()
            style = Paint.Style.STROKE
            strokeWidth = 4f * resources.displayMetrics.density
        }
    private val paintKnob =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFF3D3D3D.toInt()
            style = Paint.Style.FILL
        }

    private var snapAnimator: ValueAnimator? = null

    fun cancelSnapAnimation() {
        snapAnimator?.cancel()
        snapAnimator = null
    }

    fun snapToCenter(animated: Boolean = true) {
        cancelSnapAnimation()
        val sx = knobOffsetX
        val sy = knobOffsetY
        if (sx == 0f && sy == 0f) {
            onSnapAnimationEnd?.invoke()
            return
        }
        if (!animated) {
            knobOffsetX = 0f
            knobOffsetY = 0f
            notifyPosition(fromUser = false)
            invalidate()
            onSnapAnimationEnd?.invoke()
            return
        }
        ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 220L
            interpolator = DecelerateInterpolator()
            addUpdateListener { a ->
                val t = a.animatedValue as Float
                knobOffsetX = sx * (1f - t)
                knobOffsetY = sy * (1f - t)
                notifyPosition(fromUser = false)
                invalidate()
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    snapAnimator = null
                    onSnapAnimationEnd?.invoke()
                }
            })
            snapAnimator = this
            start()
        }
    }

    private fun notifyPosition(fromUser: Boolean) {
        val x = offsetToValue(knobOffsetX)
        val y = offsetToValue(knobOffsetY)
        onPositionChange?.invoke(x, y, fromUser)
    }

    private fun offsetToValue(offset: Float): Int {
        if (travelRadius <= 0f) return CENTER
        val t = (offset / travelRadius).coerceIn(-1f, 1f)
        return (CENTER + t * CENTER).roundToInt().coerceIn(0, MAX_VALUE)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        centerX = w / 2f
        centerY = h / 2f
        val minSide = min(w, h).toFloat()
        knobDrawRadius = minSide * 0.12f
        travelRadius = minSide / 2f - knobDrawRadius - 8f * resources.displayMetrics.density
        if (travelRadius < knobDrawRadius) {
            travelRadius = knobDrawRadius
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawCircle(centerX, centerY, travelRadius + knobDrawRadius * 0.35f, paintArea)
        canvas.drawCircle(centerX, centerY, travelRadius + knobDrawRadius * 0.35f, paintBorder)
        canvas.drawCircle(
            centerX + knobOffsetX,
            centerY + knobOffsetY,
            knobDrawRadius,
            paintKnob
        )
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                parent.requestDisallowInterceptTouchEvent(true)
                cancelSnapAnimation()
                updateKnob(event.x, event.y)
                notifyPosition(fromUser = true)
                invalidate()
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                updateKnob(event.x, event.y)
                notifyPosition(fromUser = true)
                invalidate()
                return true
            }
            MotionEvent.ACTION_UP -> {
                parent.requestDisallowInterceptTouchEvent(false)
                performClick()
                onRelease?.invoke()
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                parent.requestDisallowInterceptTouchEvent(false)
                onRelease?.invoke()
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    private fun updateKnob(touchX: Float, touchY: Float) {
        var dx = touchX - centerX
        var dy = touchY - centerY
        val d = hypot(dx.toDouble(), dy.toDouble()).toFloat()
        if (d > travelRadius && d > 0f) {
            dx *= travelRadius / d
            dy *= travelRadius / d
        }
        knobOffsetX = dx
        knobOffsetY = dy
    }

    companion object {
        private const val MAX_VALUE = 1023
        private const val CENTER = 512
    }
}
