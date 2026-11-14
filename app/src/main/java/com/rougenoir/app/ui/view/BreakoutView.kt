package com.rougenoir.app.ui.view

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.os.SystemClock
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import com.rougenoir.app.R
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.sqrt
import kotlin.random.Random

/** Simulates the ball, paddle, and bricks and draws them on the same timed board. */
class BreakoutView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    enum class State { READY, RUNNING, PAUSED, WON, LOST }

    interface Listener {
        fun onScoreChanged(score: Int, lives: Int)
        fun onStateChanged(state: State)
        fun onBrickHit()
        fun onPaddleHit()
        fun onLifeLost()
        fun onGameOver(score: Int, won: Boolean): Boolean
    }

    private class Brick(val rect: RectF, val isArmy: Boolean, var alive: Boolean = true)

    var listener: Listener? = null
        set(value) {
            field = value
            value?.onScoreChanged(score, lives)
            value?.onStateChanged(state)
        }

    var state: State = State.READY
        private set

    var score = 0
        private set

    var lives = 3
        private set

    private val density = resources.displayMetrics.density
    private val random = Random(System.nanoTime())
    private val board = RectF()
    private val bricks = mutableListOf<Brick>()
    private val paddleRect = RectF()
    private val ballRect = RectF()
    private var difficultyLevel = 1
    private var paddleWidth = dp(94f)
    private var paddleY = 0f
    private var ballX = 0f
    private var ballY = 0f
    private var ballVx = 0f
    private var ballVy = 0f
    private var ballRadius = dp(6.5f)
    private var lastFrame = 0L
    private var newRecord = false

    private val surfacePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.cream)
    }
    private val surfaceShadow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.scrim_light)
    }
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.ink)
        style = Paint.Style.STROKE
        strokeWidth = dp(2.2f)
    }
    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.line)
        alpha = 90
        strokeWidth = dp(0.7f)
    }
    private val armyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.red)
    }
    private val churchPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.ink)
    }
    private val brickOutline = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.ink)
        style = Paint.Style.STROKE
        strokeWidth = dp(1.25f)
    }
    private val brickHighlight = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.cream)
        alpha = 55
    }
    private val motifPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.cream)
        style = Paint.Style.STROKE
        strokeWidth = dp(1.3f)
        strokeCap = Paint.Cap.ROUND
    }
    private val goldPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.gold)
    }
    private val paddlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.brown)
    }
    private val paddleOutline = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.brown_dark)
        style = Paint.Style.STROKE
        strokeWidth = dp(1.6f)
    }
    private val paddleLine = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.gold)
        strokeWidth = dp(1.8f)
    }
    private val ballPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.gold)
    }
    private val ballOutline = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.ink)
        style = Paint.Style.STROKE
        strokeWidth = dp(1.6f)
    }
    private val ballShine = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.cream)
    }
    private val overlayScrimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.parchment)
        alpha = 82
    }
    private val overlayCardPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.cream)
    }
    private val overlayLine = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.gold)
        style = Paint.Style.STROKE
        strokeWidth = dp(1.5f)
    }
    private val eyebrowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.brown)
        textAlign = Paint.Align.CENTER
        typeface = ResourcesCompat.getFont(context, R.font.nunito_bold)
        textSize = sp(9f)
    }
    private val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.ink)
        textAlign = Paint.Align.CENTER
        typeface = ResourcesCompat.getFont(context, R.font.cormorant_semibold)
        textSize = sp(23f)
    }
    private val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.muted)
        textAlign = Paint.Align.CENTER
        typeface = ResourcesCompat.getFont(context, R.font.nunito_regular)
        textSize = sp(12f)
    }
    private val scorePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.brown)
        textAlign = Paint.Align.CENTER
        typeface = ResourcesCompat.getFont(context, R.font.cormorant_bold)
        textSize = sp(32f)
    }

    private val frame = object : Runnable {
        override fun run() {
            if (state != State.RUNNING) return
            val now = SystemClock.uptimeMillis()
            val delta = ((now - lastFrame) / 1000f).coerceIn(0f, 0.026f)
            lastFrame = now
            update(delta)
            invalidate()
            if (state == State.RUNNING) postOnAnimation(this)
        }
    }

    fun setDifficulty(level: Int) {
        difficultyLevel = level.coerceIn(0, 2)
        paddleWidth = when (difficultyLevel) {
            0 -> dp(112f)
            1 -> dp(92f)
            else -> dp(74f)
        }
        if (width > 0) {
            layoutPaddle()
            if (state == State.READY) resetBall()
            invalidate()
        }
    }

    fun newGame() {
        removeCallbacks(frame)
        score = 0
        lives = 3
        newRecord = false
        state = State.READY
        buildBricks()
        resetBall()
        listener?.onScoreChanged(score, lives)
        listener?.onStateChanged(state)
        invalidate()
    }

    fun pause() {
        if (state != State.RUNNING) return
        state = State.PAUSED
        removeCallbacks(frame)
        listener?.onStateChanged(state)
        invalidate()
    }

    fun resume() {
        if (state != State.PAUSED) return
        state = State.RUNNING
        lastFrame = SystemClock.uptimeMillis()
        listener?.onStateChanged(state)
        postOnAnimation(frame)
        invalidate()
    }

    fun togglePause() {
        when (state) {
            State.RUNNING -> pause()
            State.PAUSED -> resume()
            else -> Unit
        }
    }

    /** Launch the stationary ball upward at a slight angle after the player serves. */
    private fun serve() {
        if (state != State.READY) return
        state = State.RUNNING
        val speed = baseSpeed()
        val horizontal = speed * (0.35f + random.nextFloat() * 0.22f)
        val sign = if (random.nextBoolean()) 1f else -1f
        ballVx = horizontal * sign
        ballVy = -sqrt((speed * speed - horizontal * horizontal).coerceAtLeast(1f))
        lastFrame = SystemClock.uptimeMillis()
        listener?.onStateChanged(state)
        postOnAnimation(frame)
        invalidate()
    }

    private fun baseSpeed(): Float = when (difficultyLevel) {
        0 -> dp(270f)
        1 -> dp(330f)
        else -> dp(390f)
    }

    /** Advance one frame, resolve collisions, and report any score or life changes. */
    private fun update(delta: Float) {
        if (delta <= 0f) return
        val previousY = ballY
        ballX += ballVx * delta
        ballY += ballVy * delta

        if (ballX - ballRadius < board.left) {
            ballX = board.left + ballRadius
            ballVx = abs(ballVx)
        } else if (ballX + ballRadius > board.right) {
            ballX = board.right - ballRadius
            ballVx = -abs(ballVx)
        }
        if (ballY - ballRadius < board.top) {
            ballY = board.top + ballRadius
            ballVy = abs(ballVy)
        }

        if (ballVy > 0f && intersectsPaddle()) {
            ballY = paddleRect.top - ballRadius
            val hit = ((ballX - paddleRect.centerX()) / (paddleRect.width() / 2f)).coerceIn(-1f, 1f)
            val speed = min(
                sqrt(ballVx * ballVx + ballVy * ballVy) * 1.015f,
                baseSpeed() * 1.42f
            )
            ballVx = hit * speed * 0.78f
            ballVy = -sqrt((speed * speed - ballVx * ballVx).coerceAtLeast(1f))
            listener?.onPaddleHit()
        }

        var hitBrick = false
        for (brick in bricks) {
            if (!brick.alive) continue
            if (!RectF.intersects(ballRectAt(ballX, ballY), brick.rect)) continue
            brick.alive = false
            score += 10
            hitBrick = true
            val movingDown = ballVy > 0f
            val previousBottom = previousY + ballRadius
            val previousTop = previousY - ballRadius
            if (movingDown && previousBottom <= brick.rect.top + dp(2f)) {
                ballY = brick.rect.top - ballRadius
                ballVy = -abs(ballVy)
            } else if (!movingDown && previousTop >= brick.rect.bottom - dp(2f)) {
                ballY = brick.rect.bottom + ballRadius
                ballVy = abs(ballVy)
            } else {
                ballVx = -ballVx
            }
            listener?.onBrickHit()
            listener?.onScoreChanged(score, lives)
            break
        }
        if (hitBrick && bricks.none { it.alive }) {
            win()
            return
        }

        if (ballY - ballRadius > board.bottom) {
            loseLife()
        }
    }

    private fun intersectsPaddle(): Boolean {
        if (ballY + ballRadius < paddleRect.top) return false
        if (ballY - ballRadius > paddleRect.bottom) return false
        return ballX + ballRadius > paddleRect.left && ballX - ballRadius < paddleRect.right
    }

    private fun loseLife() {
        lives--
        listener?.onLifeLost()
        listener?.onScoreChanged(score, lives)
        if (lives <= 0) {
            state = State.LOST
            removeCallbacks(frame)
            newRecord = listener?.onGameOver(score, false) ?: false
            listener?.onStateChanged(state)
        } else {
            state = State.READY
            resetBall()
            listener?.onStateChanged(state)
        }
        invalidate()
    }

    private fun win() {
        state = State.WON
        removeCallbacks(frame)
        newRecord = listener?.onGameOver(score, true) ?: false
        listener?.onStateChanged(state)
        invalidate()
    }

    private fun resetBall() {
        if (width == 0 || height == 0) return
        layoutPaddle()
        ballX = paddleRect.centerX()
        ballY = paddleRect.top - ballRadius - dp(3f)
        ballVx = 0f
        ballVy = 0f
    }

    private fun buildBricks() {
        bricks.clear()
        if (width == 0 || height == 0) return
        layoutBoard()
        layoutPaddle()
        val left = board.left + dp(16f)
        val right = board.right - dp(16f)
        val top = board.top + dp(32f)
        val columns = 6
        val rows = 4
        val gap = dp(5f)
        val brickWidth = (right - left - gap * (columns - 1)) / columns
        val brickHeight = dp(19f)
        for (row in 0 until rows) {
            for (col in 0 until columns) {
                val x = left + col * (brickWidth + gap)
                val y = top + row * (brickHeight + dp(6f))
                val army = (row + col) % 2 == 0
                bricks += Brick(RectF(x, y, x + brickWidth, y + brickHeight), army)
            }
        }
    }

    private fun layoutBoard() {
        val inset = dp(2f)
        board.set(
            inset,
            inset,
            width - inset,
            height - inset
        )
    }

    /** Fit the paddle to the board and difficulty, then clamp it to the play area. */
    private fun layoutPaddle() {
        if (width == 0) return
        layoutBoard()
        val availableLeft = board.left + dp(14f)
        val availableRight = board.right - dp(14f)
        val center = if (paddleRect.width() > 0f) paddleRect.centerX() else board.centerX()
        val half = min(paddleWidth, availableRight - availableLeft) / 2f
        val left = (center - half).coerceIn(availableLeft, availableRight - half * 2f)
        paddleY = board.bottom - dp(38f)
        paddleRect.set(left, paddleY, left + half * 2f, paddleY + dp(13f))
    }

    private fun movePaddleTo(x: Float) {
        if (width == 0) return
        val availableLeft = board.left + dp(14f)
        val availableRight = board.right - dp(14f)
        val half = paddleRect.width() / 2f
        val center = x.coerceIn(availableLeft + half, availableRight - half)
        paddleRect.set(center - half, paddleY, center + half, paddleY + dp(13f))
        if (state == State.READY) {
            ballX = center
            ballY = paddleRect.top - ballRadius - dp(3f)
        }
        invalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val desiredHeight = (width * 1.15f).toInt()
        val height = resolveSize(desiredHeight, heightMeasureSpec)
        setMeasuredDimension(width, height)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        ballRadius = dp(6.5f)
        layoutBoard()
        layoutPaddle()
        if (bricks.isEmpty()) {
            buildBricks()
            resetBall()
        } else if (state == State.READY) {
            resetBall()
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (width == 0 || height == 0) return
        canvas.drawRoundRect(
            board.left + dp(2f), board.top + dp(4f),
            board.right + dp(2f), board.bottom + dp(4f),
            dp(22f), dp(22f), surfaceShadow
        )
        canvas.drawRoundRect(board, dp(22f), dp(22f), surfacePaint)
        canvas.drawRoundRect(board, dp(22f), dp(22f), borderPaint)

        // A quiet horizontal guide under the brick ranks.
        canvas.drawLine(
            board.left + dp(16f), board.top + dp(148f),
            board.right - dp(16f), board.top + dp(148f),
            gridPaint
        )

        for (brick in bricks) {
            if (!brick.alive) continue
            val paint = if (brick.isArmy) armyPaint else churchPaint
            canvas.drawRoundRect(brick.rect, dp(6f), dp(6f), paint)
            canvas.drawRoundRect(brick.rect, dp(6f), dp(6f), brickOutline)
            canvas.drawRoundRect(
                brick.rect.left + dp(5f), brick.rect.top + dp(3f),
                brick.rect.right - dp(5f), brick.rect.top + dp(5f),
                dp(1f), dp(1f), brickHighlight
            )
            drawBrickMotif(canvas, brick)
        }

        drawPaddle(canvas)
        drawBall(canvas)
        drawOverlay(canvas)
    }

    private fun drawBrickMotif(canvas: Canvas, brick: Brick) {
        val cx = brick.rect.centerX()
        val cy = brick.rect.centerY()
        if (brick.isArmy) {
            canvas.drawLine(
                cx - dp(2f), cy + dp(3f),
                cx + dp(2f), cy - dp(3f),
                motifPaint
            )
            goldPaint.alpha = 150
            canvas.drawCircle(cx + dp(2.3f), cy - dp(3f), dp(1f), goldPaint)
        } else {
            canvas.drawLine(cx, cy - dp(3.5f), cx, cy + dp(3.5f), motifPaint)
            canvas.drawLine(cx - dp(2.4f), cy - dp(1f), cx + dp(2.4f), cy - dp(1f), motifPaint)
        }
    }

    private fun drawPaddle(canvas: Canvas) {
        canvas.drawRoundRect(paddleRect, dp(8f), dp(8f), paddlePaint)
        canvas.drawRoundRect(paddleRect, dp(8f), dp(8f), paddleOutline)
        canvas.drawLine(
            paddleRect.left + dp(10f), paddleRect.centerY(),
            paddleRect.right - dp(10f), paddleRect.centerY(),
            paddleLine
        )
    }

    private fun drawBall(canvas: Canvas) {
        canvas.drawCircle(ballX, ballY, ballRadius, ballPaint)
        canvas.drawCircle(ballX, ballY, ballRadius, ballOutline)
        canvas.drawCircle(
            ballX - ballRadius * 0.28f,
            ballY - ballRadius * 0.30f,
            ballRadius * 0.25f,
            ballShine
        )
    }

    private fun drawOverlay(canvas: Canvas) {
        if (state == State.RUNNING) return
        canvas.drawRoundRect(board, dp(22f), dp(22f), overlayScrimPaint)
        val hasScore = state == State.WON || state == State.LOST
        val cardWidth = min(board.width() * 0.86f, dp(332f))
        val cardHeight = if (hasScore) dp(208f) else dp(166f)
        val card = RectF(
            board.centerX() - cardWidth / 2f,
            board.centerY() - cardHeight / 2f,
            board.centerX() + cardWidth / 2f,
            board.centerY() + cardHeight / 2f
        )
        canvas.drawRoundRect(card, dp(24f), dp(24f), overlayCardPaint)
        canvas.drawRoundRect(card, dp(24f), dp(24f), overlayLine)
        titlePaint.textSize = sp(if (cardWidth < dp(260f)) 18f else 22f)
        val cx = card.centerX()
        var y = card.top + if (hasScore) dp(36f) else dp(43f)
        when (state) {
            State.READY -> {
                canvas.drawText(context.getString(R.string.breakout_ready_eyebrow), cx, y, eyebrowPaint)
                y += dp(40f)
                canvas.drawText(context.getString(R.string.breakout_ready_title), cx, y, titlePaint)
                y += dp(37f)
                canvas.drawText(context.getString(R.string.breakout_ready_detail), cx, y, bodyPaint)
            }
            State.PAUSED -> {
                canvas.drawText(context.getString(R.string.breakout_paused_eyebrow), cx, y, eyebrowPaint)
                y += dp(40f)
                canvas.drawText(context.getString(R.string.breakout_paused_title), cx, y, titlePaint)
                y += dp(37f)
                canvas.drawText(context.getString(R.string.breakout_paused_detail), cx, y, bodyPaint)
            }
            State.WON -> {
                canvas.drawText(context.getString(R.string.breakout_won_eyebrow), cx, y, eyebrowPaint)
                y += dp(30f)
                canvas.drawText(context.getString(R.string.breakout_won_title), cx, y, titlePaint)
                y += dp(45f)
                canvas.drawText(score.toString(), cx, y, scorePaint)
                y += dp(23f)
                canvas.drawText(
                    context.getString(
                        if (newRecord) R.string.breakout_won_record else R.string.breakout_won_detail,
                        score
                    ),
                    cx, y, bodyPaint
                )
            }
            State.LOST -> {
                canvas.drawText(context.getString(R.string.breakout_lost_eyebrow), cx, y, eyebrowPaint)
                y += dp(30f)
                canvas.drawText(context.getString(R.string.breakout_lost_title), cx, y, titlePaint)
                y += dp(45f)
                canvas.drawText(score.toString(), cx, y, scorePaint)
                y += dp(23f)
                canvas.drawText(context.getString(R.string.breakout_lost_detail), cx, y, bodyPaint)
            }
            else -> Unit
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                movePaddleTo(event.x)
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                movePaddleTo(event.x)
                return true
            }
            MotionEvent.ACTION_UP -> {
                if (state == State.READY) serve()
                performClick()
                return true
            }
        }
        return true
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    override fun onDetachedFromWindow() {
        removeCallbacks(frame)
        super.onDetachedFromWindow()
    }

    private fun ballRectAt(x: Float, y: Float): RectF {
        ballRect.set(x - ballRadius, y - ballRadius, x + ballRadius, y + ballRadius)
        return ballRect
    }

    private fun dp(value: Float): Float = value * density
    private fun sp(value: Float): Float = value * resources.displayMetrics.scaledDensity
}
