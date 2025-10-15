
package com.example.automathtapper.ui

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import kotlin.math.max
import kotlin.math.min

class SelectView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    enum class Mode { QUESTION, ANSWERS }
    var mode = Mode.QUESTION

    private var downX = 0f
    private var downY = 0f
    private var curRect: RectF? = null

    var questionRectNorm: RectF? = null
    var answersRectNorm: RectF? = null

    private val pRect = Paint().apply { style = Paint.Style.STROKE; strokeWidth = 6f }
    private val pFill = Paint().apply { style = Paint.Style.FILL; color = 0x3300FF00 }

    fun reset() { questionRectNorm = null; answersRectNorm = null; curRect = null; invalidate() }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> { downX = e.x; downY = e.y; curRect = RectF(downX, downY, downX, downY) }
            MotionEvent.ACTION_MOVE -> { curRect?.let { it.right = e.x; it.bottom = e.y; invalidate() } }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                curRect?.let {
                    val l = min(it.left, it.right) / width
                    val t = min(it.top, it.bottom) / height
                    val r = max(it.left, it.right) / width
                    val b = max(it.top, it.bottom) / height
                    val norm = RectF(l, t, r, b)
                    if (mode == Mode.QUESTION) questionRectNorm = norm else answersRectNorm = norm
                }
                curRect = null; invalidate()
            }
        }
        return true
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        questionRectNorm?.let {
            pRect.color = Color.GREEN
            drawRectNorm(canvas, it)
        }
        answersRectNorm?.let {
            pRect.color = Color.YELLOW
            drawRectNorm(canvas, it)
        }
        curRect?.let {
            pRect.color = Color.CYAN
            canvas.drawRect(it, pRect)
        }
    }

    private fun drawRectNorm(canvas: Canvas, r: RectF) {
        val rf = RectF(r.left*width, r.top*height, r.right*width, r.bottom*height)
        canvas.drawRect(rf, pRect)
        canvas.drawRect(rf, pFill)
    }
}
