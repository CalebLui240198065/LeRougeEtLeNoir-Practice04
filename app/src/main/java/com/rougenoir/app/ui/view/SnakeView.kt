package com.rougenoir.app.ui.view

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import com.rougenoir.app.R
import kotlin.math.abs
import kotlin.math.min
import kotlin.random.Random

/** Advances Snake by whole grid cells and draws the board, food, and game overlays. */
class SnakeView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    enum class State { READY, RUNNING, PAUSED, OVER }

    enum class Direction(val dx: Int, val dy: Int) {
        UP(0, -1), DOWN(0, 1), LEFT(-1, 0), RIGHT(1, 0);

        fun isOpposite(other: Direction): Boolean = dx + other.dx == 0 && dy + other.dy == 0
    }

    interface Listener {
        fun onScoreChanged(score: Int)
        fun onStateChanged(state: State)
        fun onGameOver(score: Int): Boolean
        fun onEat()
        fun onCrash()
    }

    var listener: Listener? = null
    var state: State = State.READY
        private set

    private var cols = 17
    private var rows = 15
    private var gridChosen = false
    private var difficultyLevel = 1
    private val density = resources.displayMetrics.density
    private val random = Random(System.nanoTime())
    private val handler = Handler(Looper.getMainLooper())
    private val body = ArrayDeque<Int>()
    private val board = RectF()
    private var cellSize = 0f
    private var direction = Direction.RIGHT
    private var nextDirection = Direction.RIGHT
    private var foodIndex = -1
    private var foodIsArmy = true
    private var score = 0
    private var tickMs = 190L
    private var newRecord = false
    private var downX = 0f
    private var downY = 0f

    private val boardPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.cream)
    }
    private val boardShadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.scrim_light)
    }
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.ink)
        style = Paint.Style.STROKE
        strokeWidth = dp(2.2f)
    }
    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.line)
        style = Paint.Style.STROKE
        strokeWidth = dp(0.7f)
        alpha = 95
    }
    private val snakePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val snakeOutline = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.brown_dark)
        style = Paint.Style.STROKE
        strokeWidth = dp(1.4f)
    }
    private val snakeHighlight = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.gold_light)
        alpha = 80
        style = Paint.Style.FILL
    }
    private val foodPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val foodOutline = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.ink)
        style = Paint.Style.STROKE
        strokeWidth = dp(1.5f)
    }
    private val tokenPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.cream)
        style = Paint.Style.STROKE
        strokeWidth = dp(1.8f)
        strokeCap = Paint.Cap.ROUND
    }
    private val eyePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.cream)
    }
    private val pupilPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.ink)
    }
    private val tonguePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.red_bright)
        style = Paint.Style.STROKE
        strokeWidth = dp(1.5f)
        strokeCap = Paint.Cap.ROUND
    }
    private val goldPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.gold)
        style = Paint.Style.FILL
    }
    private val overlayScrimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.parchment)
        alpha = 120
    }
    private val overlayCardPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.cream)
    }
    private val overlayLinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.gold)
        style = Paint.Style.STROKE
        strokeWidth = dp(1.5f)
    }
    private val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.ink)
        textAlign = Paint.Align.CENTER
        typeface = ResourcesCompat.getFont(context, R.font.cormorant_semibold)
        textSize = sp(23f)
    }
    private val bodyTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.muted)
        textAlign = Paint.Align.CENTER
        typeface = ResourcesCompat.getFont(context, R.font.nunito_regular)
        textSize = sp(12f)
    }
    private val scoreTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.brown)
        textAlign = Paint.Align.CENTER
        typeface = ResourcesCompat.getFont(context, R.font.cormorant_bold)
        textSize = sp(32f)
    }
    private val eyebrowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.brown)
        textAlign = Paint.Align.CENTER
        typeface = ResourcesCompat.getFont(context, R.font.nunito_bold)
        textSize = sp(9f)
    }

    private val tick = object : Runnable {
        override fun run() {
            if (state != State.RUNNING) return
            step()
            if (state == State.RUNNING) {
                handler.postDelayed(this, tickMs)
            }
        }
    }

    init {
        isClickable = true
        isFocusable = true
        prepareReadyBoard()
    }

    private fun prepareReadyBoard() {
        body.clear()
        score = 0
        direction = Direction.RIGHT
        nextDirection = Direction.RIGHT
        val row = 1.coerceAtMost(rows - 2)
        val headCol = (cols - 3).coerceAtLeast(4)
        for (offset in 0..3) {
            body.addLast(row * cols + (headCol - offset).coerceAtLeast(0))
        }
        foodIndex = (cols - 3).coerceAtLeast(1)
        foodIsArmy = true
    }

    fun setDifficulty(level: Int) {
        difficultyLevel = level.coerceIn(0, 2)
    }

    fun startGame() {
        handler.removeCallbacks(tick)
        body.clear()
        score = 0
        newRecord = false
        direction = Direction.RIGHT
        nextDirection = Direction.RIGHT
        tickMs = baseTick()
        val headCol = (cols / 4).coerceAtLeast(3)
        val headRow = rows / 2 + 1
        val head = headRow * cols + headCol
        for (offset in 0..3) body.addLast(head - offset)
        spawnFood()
        state = State.RUNNING
        listener?.onScoreChanged(score)
        listener?.onStateChanged(state)
        val firstMoveDelay = when (difficultyLevel) {
            0 -> 1050L
            1 -> 900L
            else -> 700L
        }
        handler.postDelayed(tick, firstMoveDelay)
        invalidate()
    }

    fun togglePause() {
        when (state) {
            State.RUNNING -> pause()
            State.PAUSED -> resume()
            else -> Unit
        }
    }

    fun pause() {
        if (state != State.RUNNING) return
        state = State.PAUSED
        handler.removeCallbacks(tick)
        listener?.onStateChanged(state)
        invalidate()
    }

    fun resume() {
        if (state != State.PAUSED) return
        state = State.RUNNING
        listener?.onStateChanged(state)
        handler.postDelayed(tick, tickMs)
        invalidate()
    }

    fun nudge(direction: Direction) {
        if (state == State.READY) startGame()
        if (state != State.RUNNING) return
        if (!direction.isOpposite(this.direction)) nextDirection = direction
    }

    /** Move the head one cell, check collisions, then grow or remove the tail. */
    private fun step() {
        direction = nextDirection
        val head = body.first()
        val col = head % cols + direction.dx
        val row = head / cols + direction.dy
        if (col !in 0 until cols || row !in 0 until rows) {
            endGame()
            return
        }
        val newHead = row * cols + col
        if (body.contains(newHead)) {
            endGame()
            return
        }
        body.addFirst(newHead)
        if (newHead == foodIndex) {
            score++
            tickMs = nextTick()
            listener?.onScoreChanged(score)
            listener?.onEat()
            spawnFood()
        } else {
            body.removeLast()
        }
        invalidate()
    }

    private fun endGame() {
        state = State.OVER
        handler.removeCallbacks(tick)
        listener?.onCrash()
        newRecord = listener?.onGameOver(score) ?: false
        listener?.onStateChanged(state)
        invalidate()
    }

    /** Pick a vacant grid cell. A completely filled board ends the run. */
    private fun spawnFood() {
        val occupied = body.toHashSet()
        val free = (0 until cols * rows).filter { it !in occupied }
        if (free.isEmpty()) {
            endGame()
            return
        }
        foodIndex = free[random.nextInt(free.size)]
        foodIsArmy = random.nextBoolean()
    }

    private fun baseTick(): Long = when (difficultyLevel) {
        0 -> 235L
        1 -> 195L
        else -> 160L
    }

    private fun nextTick(): Long =
        (baseTick() - score * 4L - when (difficultyLevel) {
            0 -> 0L
            1 -> 8L
            else -> 16L
        }).coerceAtLeast(78L)

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val height = resolveSize((width * 1.04f).toInt(), heightMeasureSpec)
        setMeasuredDimension(width, height)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        val outer = dp(4f)
        val availableW = (w - outer * 2).coerceAtLeast(1f)
        val availableH = (h - outer * 2).coerceAtLeast(1f)
        if (!gridChosen || state == State.READY) {
            val grid = chooseGrid(availableW, availableH)
            if (grid[0] != cols || grid[1] != rows) {
                cols = grid[0]
                rows = grid[1]
            }
            if (state == State.READY) prepareReadyBoard()
            gridChosen = true
        }
        cellSize = min(availableW / cols, availableH / rows)
        val boardW = cellSize * cols
        val boardH = cellSize * rows
        board.set(
            (w - boardW) / 2f,
            (h - boardH) / 2f,
            (w + boardW) / 2f,
            (h + boardH) / 2f
        )
    }

    /**
     * Picks a grid whose aspect ratio follows the available board area, so the playfield
     * fills the screen on both short Android 7 phones and tall modern devices.
     */
    private fun chooseGrid(availableW: Float, availableH: Float): IntArray {
        var bestCols = 17
        var bestRows = 15
        var bestScore = -1f
        val minCell = dp(16f)
        for (candidateCols in 12..24) {
            for (candidateRows in 12..24) {
                val candidateCell = min(
                    availableW / candidateCols,
                    availableH / candidateRows
                )
                if (candidateCell < minCell) continue
                val fill = (candidateCell * candidateCols * candidateCell * candidateRows) /
                    (availableW * availableH)
                var score = fill
                if (candidateCell < dp(18f)) score -= 0.04f
                if (candidateCell > dp(34f)) score -= 0.04f
                if (score > bestScore) {
                    bestScore = score
                    bestCols = candidateCols
                    bestRows = candidateRows
                }
            }
        }
        return intArrayOf(bestCols, bestRows)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (cellSize <= 0f) return
        canvas.drawRoundRect(
            board.left + dp(2f), board.top + dp(4f),
            board.right + dp(2f), board.bottom + dp(4f),
            dp(22f), dp(22f), boardShadowPaint
        )
        canvas.drawRoundRect(board, dp(22f), dp(22f), boardPaint)
        canvas.drawRoundRect(board, dp(22f), dp(22f), borderPaint)

        for (col in 1 until cols) {
            val x = board.left + col * cellSize
            canvas.drawLine(x, board.top + dp(4f), x, board.bottom - dp(4f), gridPaint)
        }
        for (row in 1 until rows) {
            val y = board.top + row * cellSize
            canvas.drawLine(board.left + dp(4f), y, board.right - dp(4f), y, gridPaint)
        }

        drawFood(canvas)
        drawSnake(canvas)

        when (state) {
            State.READY -> drawOverlay(
                canvas,
                eyebrow = context.getString(R.string.snake_ready_eyebrow),
                title = context.getString(R.string.snake_ready_title),
                body = context.getString(R.string.snake_ready_detail)
            )
            State.PAUSED -> drawOverlay(
                canvas,
                eyebrow = context.getString(R.string.snake_paused_eyebrow),
                title = context.getString(R.string.snake_paused_title),
                body = context.getString(R.string.snake_paused_detail)
            )
            State.OVER -> drawOverlay(
                canvas,
                eyebrow = context.getString(R.string.snake_over_eyebrow),
                title = context.getString(R.string.snake_over_title),
                body = if (newRecord) {
                    context.getString(R.string.snake_over_record, score)
                } else {
                    context.getString(R.string.snake_over_detail, score)
                },
                showScore = true
            )
            State.RUNNING -> Unit
        }
    }

    private fun drawFood(canvas: Canvas) {
        if (foodIndex < 0) return
        val col = foodIndex % cols
        val row = foodIndex / cols
        val cx = board.left + (col + 0.5f) * cellSize
        val cy = board.top + (row + 0.5f) * cellSize
        val radius = cellSize * 0.34f
        foodPaint.color = ContextCompat.getColor(
            context,
            if (foodIsArmy) R.color.red else R.color.ink
        )
        canvas.drawCircle(cx, cy, radius, foodPaint)
        canvas.drawCircle(cx, cy, radius, foodOutline)
        if (foodIsArmy) {
            tokenPaint.color = ContextCompat.getColor(context, R.color.cream)
            canvas.drawLine(
                cx - radius * 0.42f, cy + radius * 0.44f,
                cx + radius * 0.34f, cy - radius * 0.34f,
                tokenPaint
            )
            canvas.drawLine(
                cx - radius * 0.16f, cy + radius * 0.08f,
                cx + radius * 0.22f, cy + radius * 0.08f,
                tokenPaint
            )
            goldPaint.color = ContextCompat.getColor(context, R.color.gold)
            canvas.drawCircle(cx + radius * 0.40f, cy - radius * 0.40f, radius * 0.11f, goldPaint)
        } else {
            tokenPaint.color = ContextCompat.getColor(context, R.color.cream)
            canvas.drawLine(cx, cy - radius * 0.50f, cx, cy + radius * 0.48f, tokenPaint)
            canvas.drawLine(cx - radius * 0.36f, cy - radius * 0.12f, cx + radius * 0.36f, cy - radius * 0.12f, tokenPaint)
            goldPaint.color = ContextCompat.getColor(context, R.color.gold)
            canvas.drawCircle(cx, cy + radius * 0.44f, radius * 0.09f, goldPaint)
        }
    }

    private fun drawSnake(canvas: Canvas) {
        if (body.isEmpty()) return
        for (index in body.size - 1 downTo 0) {
            val cell = body.elementAt(index)
            val col = cell % cols
            val row = cell / cols
            val left = board.left + col * cellSize + dp(1.7f)
            val top = board.top + row * cellSize + dp(1.7f)
            val right = left + cellSize - dp(3.4f)
            val bottom = top + cellSize - dp(3.4f)
            snakePaint.color = ContextCompat.getColor(context, R.color.brown)
            canvas.drawRoundRect(left, top, right, bottom, dp(7f), dp(7f), snakePaint)
            canvas.drawRoundRect(left, top, right, bottom, dp(7f), dp(7f), snakeOutline)
            if (index % 3 == 0) {
                val r = (right - left) * 0.12f
                canvas.drawCircle((left + right) / 2f, top + (bottom - top) * 0.32f, r, snakeHighlight)
            }
        }
        drawHead(canvas, body.first())
    }

    private fun drawHead(canvas: Canvas, cell: Int) {
        val col = cell % cols
        val row = cell / cols
        val cx = board.left + (col + 0.5f) * cellSize
        val cy = board.top + (row + 0.5f) * cellSize
        val radius = cellSize * 0.47f
        snakePaint.color = ContextCompat.getColor(context, R.color.brown_dark)
        canvas.drawCircle(cx, cy, radius, snakePaint)
        snakeOutline.strokeWidth = dp(2f)
        canvas.drawCircle(cx, cy, radius, snakeOutline)

        val eyeOffset = cellSize * 0.16f
        val eyeForward = cellSize * 0.15f
        val eyeRadius = maxOf(dp(1.8f), cellSize * 0.10f)
        val pupilRadius = eyeRadius * 0.48f
        val perpX = -direction.dy
        val perpY = direction.dx
        for (side in intArrayOf(-1, 1)) {
            val ex = cx + direction.dx * eyeForward + perpX * eyeOffset * side
            val ey = cy + direction.dy * eyeForward + perpY * eyeOffset * side
            canvas.drawCircle(ex, ey, eyeRadius, eyePaint)
            canvas.drawCircle(
                ex + direction.dx * eyeRadius * 0.25f,
                ey + direction.dy * eyeRadius * 0.25f,
                pupilRadius, pupilPaint
            )
        }
        val tongueX = cx + direction.dx * (radius + dp(2f))
        val tongueY = cy + direction.dy * (radius + dp(2f))
        val path = Path().apply {
            moveTo(cx + direction.dx * radius * 0.85f, cy + direction.dy * radius * 0.85f)
            lineTo(tongueX, tongueY)
            lineTo(tongueX + perpX * dp(3f) - direction.dx * dp(2f), tongueY + perpY * dp(3f) - direction.dy * dp(2f))
            moveTo(tongueX, tongueY)
            lineTo(tongueX - perpX * dp(3f) - direction.dx * dp(2f), tongueY - perpY * dp(3f) - direction.dy * dp(2f))
        }
        canvas.drawPath(path, tonguePaint)
    }

    private fun drawOverlay(
        canvas: Canvas,
        eyebrow: String,
        title: String,
        body: String,
        showScore: Boolean = false
    ) {
        canvas.drawRoundRect(board, dp(22f), dp(22f), overlayScrimPaint)
        val cardWidth = min(board.width() * 0.86f, dp(332f))
        val cardHeight = if (showScore) dp(206f) else dp(166f)
        val card = RectF(
            board.centerX() - cardWidth / 2f,
            board.centerY() - cardHeight / 2f,
            board.centerX() + cardWidth / 2f,
            board.centerY() + cardHeight / 2f
        )
        canvas.drawRoundRect(card, dp(24f), dp(24f), overlayCardPaint)
        canvas.drawRoundRect(card, dp(24f), dp(24f), overlayLinePaint)
        titlePaint.textSize = sp(if (cardWidth < dp(260f)) 18f else 22f)
        val cx = card.centerX()
        var y = card.top + if (showScore) dp(36f) else dp(43f)
        canvas.drawText(eyebrow, cx, y, eyebrowPaint)
        y += if (showScore) dp(31f) else dp(40f)
        canvas.drawText(title, cx, y, titlePaint)
        y += if (showScore) dp(46f) else dp(38f)
        if (showScore) {
            canvas.drawText(score.toString(), cx, y, scoreTextPaint)
            y += dp(23f)
        }
        canvas.drawText(body, cx, y, bodyTextPaint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.x
                downY = event.y
                parent?.requestDisallowInterceptTouchEvent(true)
                return true
            }
            MotionEvent.ACTION_UP -> {
                val dx = event.x - downX
                val dy = event.y - downY
                val threshold = dp(28f)
                if (abs(dx) < threshold && abs(dy) < threshold) {
                    if (state == State.READY) {
                        startGame()
                    } else if (state == State.PAUSED) {
                        resume()
                    }
                } else {
                    if (state == State.READY) startGame()
                    val next = if (abs(dx) > abs(dy)) {
                        if (dx > 0) Direction.RIGHT else Direction.LEFT
                    } else {
                        if (dy > 0) Direction.DOWN else Direction.UP
                    }
                    nudge(next)
                }
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
        handler.removeCallbacks(tick)
        super.onDetachedFromWindow()
    }

    private fun dp(value: Float): Float = value * density
    private fun sp(value: Float): Float = value * resources.displayMetrics.scaledDensity
}
