package com.rougenoir.app.ui.view

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.animation.OvershootInterpolator
import androidx.core.content.ContextCompat
import com.rougenoir.app.R
import com.rougenoir.app.game.TicTacToeEngine
import kotlin.math.min

/** Renders a Tic Tac Toe board and converts taps into cell indices for its activity. */
class TicTacToeBoardView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    interface OnCellClickListener {
        fun onCellClick(index: Int)
    }

    var listener: OnCellClickListener? = null

    private val density = resources.displayMetrics.density
    private val cellRects = Array(9) { RectF() }
    private var cells = IntArray(9)
    private var winningLine: IntArray? = null
    private var enabledForInput = true
    private var rivalIsArmy = true
    private var lastAnimatedCell = -1
    private var markScale = 1f

    private val cellPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.parchment)
    }
    private val highlightPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.gold_light)
        style = Paint.Style.FILL
    }
    private val highlightStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.gold)
        style = Paint.Style.STROKE
        strokeWidth = dp(2.5f)
    }
    private val sealPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val darkPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.ink)
        style = Paint.Style.STROKE
        strokeWidth = dp(2f)
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val lightPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.cream)
        style = Paint.Style.STROKE
        strokeWidth = dp(2.5f)
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val goldPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.gold)
        style = Paint.Style.FILL_AND_STROKE
        strokeWidth = dp(1.5f)
    }
    private val brownPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.brown)
        style = Paint.Style.STROKE
        strokeWidth = dp(2.5f)
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    private val scaleAnimator = ValueAnimator.ofFloat(0.55f, 1f).apply {
        duration = 220
        interpolator = OvershootInterpolator(1.7f)
        addUpdateListener {
            markScale = it.animatedValue as Float
            invalidate()
        }
    }

    init {
        isClickable = true
        isFocusable = true
    }

    /** Apply the latest board snapshot and optionally animate the newly placed mark. */
    fun render(
        newCells: IntArray,
        line: IntArray?,
        inputEnabled: Boolean,
        armyRival: Boolean,
        animateIndex: Int = -1
    ) {
        cells = newCells.copyOf()
        winningLine = line
        enabledForInput = inputEnabled
        rivalIsArmy = armyRival
        lastAnimatedCell = animateIndex
        if (animateIndex in 0..8) {
            scaleAnimator.cancel()
            scaleAnimator.start()
        } else {
            markScale = 1f
        }
        invalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val desired = width
        val height = resolveSize(desired, heightMeasureSpec)
        setMeasuredDimension(width, min(desired, height))
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        layoutCells()
    }

    private fun layoutCells() {
        val innerWidth = width - paddingLeft - paddingRight
        val innerHeight = height - paddingTop - paddingBottom
        val size = min(innerWidth, innerHeight).toFloat()
        val gap = dp(7f)
        val inner = size - gap * 4
        val cell = inner / 3f
        val startX = paddingLeft + (innerWidth - size) / 2f + gap
        val startY = paddingTop + (innerHeight - size) / 2f + gap
        for (index in 0 until 9) {
            val row = index / 3
            val col = index % 3
            val left = startX + col * (cell + gap)
            val top = startY + row * (cell + gap)
            cellRects[index].set(left, top, left + cell, top + cell)
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        for (index in 0 until 9) {
            val rect = cellRects[index]
            if (winningLine?.contains(index) == true) {
                canvas.drawRoundRect(rect, dp(16f), dp(16f), highlightPaint)
                canvas.drawRoundRect(rect, dp(16f), dp(16f), highlightStroke)
            } else {
                canvas.drawRoundRect(rect, dp(16f), dp(16f), cellPaint)
            }
            if (cells[index] != TicTacToeEngine.EMPTY) {
                val scale = if (index == lastAnimatedCell) markScale else 1f
                drawMark(canvas, rect, index, cells[index], scale)
            }
        }
    }

    private fun drawMark(canvas: Canvas, cell: RectF, index: Int, player: Int, scale: Float) {
        val cx = cell.centerX()
        val cy = cell.centerY()
        val radius = min(cell.width(), cell.height()) * 0.33f * scale

        canvas.save()
        canvas.translate(cx, cy)

        when {
            player == TicTacToeEngine.TALENT -> {
                sealPaint.color = ContextCompat.getColor(context, R.color.brown)
                canvas.drawCircle(0f, 0f, radius, sealPaint)
                sealPaint.style = Paint.Style.STROKE
                sealPaint.strokeWidth = dp(2f)
                sealPaint.color = ContextCompat.getColor(context, R.color.brown_dark)
                canvas.drawCircle(0f, 0f, radius, sealPaint)
                sealPaint.style = Paint.Style.FILL
                drawQuill(canvas, radius)
            }
            rivalIsArmy -> {
                sealPaint.color = ContextCompat.getColor(context, R.color.red)
                canvas.drawCircle(0f, 0f, radius, sealPaint)
                sealPaint.style = Paint.Style.STROKE
                sealPaint.strokeWidth = dp(2f)
                sealPaint.color = ContextCompat.getColor(context, R.color.red_dark)
                canvas.drawCircle(0f, 0f, radius, sealPaint)
                sealPaint.style = Paint.Style.FILL
                drawSabre(canvas, radius)
            }
            else -> {
                sealPaint.color = ContextCompat.getColor(context, R.color.ink)
                canvas.drawCircle(0f, 0f, radius, sealPaint)
                sealPaint.style = Paint.Style.STROKE
                sealPaint.strokeWidth = dp(2f)
                sealPaint.color = ContextCompat.getColor(context, R.color.ink_muted)
                canvas.drawCircle(0f, 0f, radius, sealPaint)
                sealPaint.style = Paint.Style.FILL
                drawCross(canvas, radius)
            }
        }
        canvas.restore()
    }

    private fun drawQuill(canvas: Canvas, radius: Float) {
        canvas.save()
        canvas.rotate(-18f)
        lightPaint.strokeWidth = dp(2.4f)
        canvas.drawLine(-radius * 0.50f, radius * 0.55f, radius * 0.40f, -radius * 0.55f, lightPaint)
        goldPaint.color = ContextCompat.getColor(context, R.color.gold)
        val nib = Path().apply {
            moveTo(radius * 0.24f, -radius * 0.32f)
            lineTo(radius * 0.50f, -radius * 0.64f)
            lineTo(radius * 0.40f, -radius * 0.22f)
            close()
        }
        canvas.drawPath(nib, goldPaint)
        for (i in 0..2) {
            val t = 0.15f + i * 0.16f
            canvas.drawLine(
                -radius * 0.50f + radius * 0.9f * t,
                radius * 0.55f - radius * 1.1f * t,
                -radius * 0.70f + radius * 0.9f * t,
                radius * 0.35f - radius * 1.1f * t,
                lightPaint
            )
            canvas.drawLine(
                -radius * 0.50f + radius * 0.9f * t,
                radius * 0.55f - radius * 1.1f * t,
                -radius * 0.22f + radius * 0.9f * t,
                radius * 0.26f - radius * 1.1f * t,
                lightPaint
            )
        }
        canvas.restore()
    }

    private fun drawSabre(canvas: Canvas, radius: Float) {
        canvas.save()
        canvas.rotate(-45f)
        lightPaint.strokeWidth = dp(3f)
        canvas.drawLine(0f, -radius * 0.58f, 0f, radius * 0.53f, lightPaint)
        canvas.drawLine(-radius * 0.26f, radius * 0.20f, radius * 0.26f, radius * 0.20f, goldPaint)
        goldPaint.color = ContextCompat.getColor(context, R.color.gold)
        canvas.drawCircle(0f, radius * 0.55f, radius * 0.07f, goldPaint)
        canvas.restore()
    }

    private fun drawCross(canvas: Canvas, radius: Float) {
        canvas.drawLine(0f, -radius * 0.55f, 0f, radius * 0.58f, lightPaint)
        canvas.drawLine(-radius * 0.40f, -radius * 0.18f, radius * 0.40f, -radius * 0.18f, lightPaint)
        goldPaint.color = ContextCompat.getColor(context, R.color.gold)
        canvas.drawCircle(0f, radius * 0.47f, radius * 0.08f, goldPaint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!enabledForInput) return false
        if (event.action == MotionEvent.ACTION_UP) {
            for (index in 0 until 9) {
                if (cellRects[index].contains(event.x, event.y) &&
                    cells[index] == TicTacToeEngine.EMPTY
                ) {
                    performClick()
                    listener?.onCellClick(index)
                    return true
                }
            }
        }
        return true
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    override fun onDetachedFromWindow() {
        scaleAnimator.cancel()
        super.onDetachedFromWindow()
    }

    private fun dp(value: Float): Float = value * density
}
