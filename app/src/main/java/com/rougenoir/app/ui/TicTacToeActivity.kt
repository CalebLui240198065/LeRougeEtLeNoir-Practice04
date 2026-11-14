package com.rougenoir.app.ui

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.WindowManager
import android.widget.ImageButton
import android.widget.TextView
import androidx.appcompat.widget.AppCompatButton
import com.rougenoir.app.R
import com.rougenoir.app.data.Prefs
import com.rougenoir.app.game.RoundOutcome
import com.rougenoir.app.game.RoundResult
import com.rougenoir.app.game.TicTacToeEngine
import com.rougenoir.app.ui.view.TicTacToeBoardView
import com.rougenoir.app.util.Feedback
import java.util.Random

/** Runs a Tic Tac Toe duel, schedules the rival's moves, and records the outcome. */
class TicTacToeActivity : BaseActivity(), TicTacToeBoardView.OnCellClickListener {

    private lateinit var board: TicTacToeBoardView
    private lateinit var status: TextView
    private lateinit var scoreView: TextView
    private lateinit var armyChip: TextView
    private lateinit var churchChip: TextView
    private lateinit var newDuelButton: AppCompatButton

    private val engine = TicTacToeEngine()
    private val handler = Handler(Looper.getMainLooper())
    private val random = Random()
    private var rivalIsArmy = true
    private var aiThinking = false
    private var recorded = false
    private var difficulty = Prefs.Difficulty.ADVOCATE.level
    private var delayRunnable: Runnable? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_tic_tac_toe)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        bindViews()
        wireActions()
        startNewDuel()
    }

    private fun bindViews() {
        board = findViewById(R.id.tttBoard)
        status = findViewById(R.id.tttStatus)
        scoreView = findViewById(R.id.tttScore)
        armyChip = findViewById(R.id.armyChip)
        churchChip = findViewById(R.id.churchChip)
        newDuelButton = findViewById(R.id.newDuelButton)
        board.listener = this
    }

    private fun wireActions() {
        findViewById<ImageButton>(R.id.backButton).setOnClickListener {
            Feedback.tap(this)
            finish()
        }
        newDuelButton.setOnClickListener {
            Feedback.select(this)
            startNewDuel()
        }
        armyChip.setOnClickListener {
            if (!rivalIsArmy) {
                Feedback.select(this)
                rivalIsArmy = true
                updateRivalChips()
                startNewDuel()
            }
        }
        churchChip.setOnClickListener {
            if (rivalIsArmy) {
                Feedback.select(this)
                rivalIsArmy = false
                updateRivalChips()
                startNewDuel()
            }
        }
    }

    private fun startNewDuel() {
        // The old rival's delayed move must never run on the replacement board.
        delayRunnable?.let { handler.removeCallbacks(it) }
        delayRunnable = null
        aiThinking = false
        recorded = false
        difficulty = Prefs.difficulty(this).level
        engine.reset()
        updateRivalChips()
        updateScore()
        status.setText(getString(R.string.ttt_turn, Prefs.displayName(this)))
        renderBoard()
    }

    private fun updateRivalChips() {
        armyChip.isSelected = rivalIsArmy
        churchChip.isSelected = !rivalIsArmy
    }

    private fun updateScore() {
        scoreView.text = getString(
            R.string.ttt_score,
            Prefs.tttWins(this),
            Prefs.tttLosses(this),
            Prefs.tttDraws(this)
        )
    }

    override fun onCellClick(index: Int) {
        if (aiThinking || engine.isOver || engine.currentPlayer != TicTacToeEngine.TALENT) return
        if (!engine.play(index)) return
        Feedback.mark(this)
        renderBoard(index)
        if (engine.isOver) {
            finishGame()
        } else {
            beginAiTurn()
        }
    }

    private fun beginAiTurn() {
        // Temporarily disable player input while the rival's move is pending.
        aiThinking = true
        status.text = getString(R.string.ttt_ai_turn, rivalName())
        renderBoard()
        val level = difficulty
        val delay = when (level) {
            0 -> 420L
            1 -> 520L
            else -> 620L
        }
        val runnable = Runnable { playAiMove(level) }
        delayRunnable = runnable
        handler.postDelayed(runnable, delay)
    }

    private fun playAiMove(level: Int) {
        if (engine.isOver) {
            aiThinking = false
            return
        }
        val move = engine.chooseRivalMove(level, random)
        aiThinking = false
        delayRunnable = null
        if (move >= 0 && engine.play(move)) {
            Feedback.mark(this)
            renderBoard(move)
        }
        if (engine.isOver) {
            finishGame()
        } else {
            status.setText(getString(R.string.ttt_turn, Prefs.displayName(this)))
            renderBoard()
        }
    }

    private fun finishGame() {
        // Record each finished duel once even when more than one UI path refreshes it.
        if (recorded) return
        recorded = true
        aiThinking = false
        val outcome = when (engine.winner) {
            TicTacToeEngine.TALENT -> {
                Prefs.recordTttWin(this)
                status.setText(R.string.ttt_win)
                Feedback.win(this)
                RoundOutcome.WIN
            }
            TicTacToeEngine.RIVAL -> {
                Prefs.recordTttLoss(this)
                status.text = getString(R.string.ttt_lose, rivalName())
                Feedback.error(this)
                RoundOutcome.LOSS
            }
            else -> {
                Prefs.recordTttDraw(this)
                status.setText(R.string.ttt_draw)
                Feedback.error(this)
                RoundOutcome.DRAW
            }
        }
        val moveCount = engine.cells.count { it != TicTacToeEngine.EMPTY }
        recordCompletedRound(RoundResult.TicTacToe(outcome, rivalIsArmy, difficulty, moveCount))
        updateScore()
        renderBoard()
    }

    private fun renderBoard(animateIndex: Int = -1) {
        board.render(
            engine.cells,
            engine.winningLine,
            inputEnabled = !aiThinking && !engine.isOver,
            armyRival = rivalIsArmy,
            animateIndex = animateIndex
        )
    }

    private fun rivalName(): String = getString(
        if (rivalIsArmy) R.string.ttt_army_short else R.string.ttt_church_short
    )

    override fun onDestroy() {
        delayRunnable?.let { handler.removeCallbacks(it) }
        super.onDestroy()
    }
}
