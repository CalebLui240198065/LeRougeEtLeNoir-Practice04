package com.rougenoir.app.ui

import android.os.Bundle
import android.view.WindowManager
import android.widget.ImageButton
import android.widget.TextView
import androidx.appcompat.widget.AppCompatButton
import com.rougenoir.app.R
import com.rougenoir.app.data.Prefs
import com.rougenoir.app.game.RoundResult
import com.rougenoir.app.game.SnakeEnding
import com.rougenoir.app.ui.view.SnakeView
import com.rougenoir.app.util.Feedback

/** Connects the compass and pause controls to Snake and reports the player's best score. */
class SnakeActivity : BaseActivity(), SnakeView.Listener {

    private lateinit var snakeView: SnakeView
    private lateinit var scoreView: TextView
    private lateinit var bestView: TextView
    private lateinit var statusView: TextView
    private lateinit var actionButton: AppCompatButton
    private var difficulty = Prefs.Difficulty.ADVOCATE.level

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_snake)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        bindViews()
        difficulty = Prefs.difficulty(this).level
        snakeView.setDifficulty(difficulty)
        snakeView.listener = this
        scoreView.text = "0"
        bestView.text = Prefs.snakeBest(this).toString()
        updateState(SnakeView.State.READY)
        wireActions()
    }

    private fun bindViews() {
        snakeView = findViewById(R.id.snakeView)
        scoreView = findViewById(R.id.snakeScore)
        bestView = findViewById(R.id.snakeBest)
        statusView = findViewById(R.id.snakeStatus)
        actionButton = findViewById(R.id.snakeActionButton)
    }

    private fun wireActions() {
        findViewById<ImageButton>(R.id.backButton).setOnClickListener {
            Feedback.tap(this)
            finish()
        }
        actionButton.setOnClickListener {
            when (snakeView.state) {
                SnakeView.State.READY, SnakeView.State.OVER -> {
                    Feedback.select(this)
                    snakeView.startGame()
                }
                SnakeView.State.RUNNING -> {
                    Feedback.tap(this)
                    snakeView.pause()
                }
                SnakeView.State.PAUSED -> {
                    Feedback.tap(this)
                    snakeView.resume()
                }
            }
        }
        findViewById<ImageButton>(R.id.snakeUp).setOnClickListener {
            nudge(SnakeView.Direction.UP)
        }
        findViewById<ImageButton>(R.id.snakeDown).setOnClickListener {
            nudge(SnakeView.Direction.DOWN)
        }
        findViewById<ImageButton>(R.id.snakeLeft).setOnClickListener {
            nudge(SnakeView.Direction.LEFT)
        }
        findViewById<ImageButton>(R.id.snakeRight).setOnClickListener {
            nudge(SnakeView.Direction.RIGHT)
        }
    }

    private fun nudge(direction: SnakeView.Direction) {
        snakeView.nudge(direction)
        Feedback.tap(this)
    }

    override fun onScoreChanged(score: Int) {
        scoreView.text = score.toString()
    }

    override fun onStateChanged(state: SnakeView.State) {
        updateState(state)
    }

    private fun updateState(state: SnakeView.State) {
        statusView.setText(
            when (state) {
                SnakeView.State.READY -> R.string.snake_ready
                SnakeView.State.RUNNING -> R.string.snake_running
                SnakeView.State.PAUSED -> R.string.snake_paused
                SnakeView.State.OVER -> if (snakeView.ending == SnakeEnding.BOARD_FILLED) {
                    R.string.snake_board_filled
                } else {
                    R.string.snake_game_over
                }
            }
        )
        actionButton.setText(
            when (state) {
                SnakeView.State.READY -> R.string.snake_begin
                SnakeView.State.RUNNING -> R.string.snake_pause
                SnakeView.State.PAUSED -> R.string.snake_resume
                SnakeView.State.OVER -> R.string.snake_again
            }
        )
    }

    override fun onGameOver(score: Int, ending: SnakeEnding): Boolean {
        recordCompletedRound(RoundResult.Snake(score, ending, difficulty))
        if (ending == SnakeEnding.BOARD_FILLED) Feedback.win(this)
        val isRecord = Prefs.recordSnakeScore(this, score)
        bestView.text = Prefs.snakeBest(this).toString()
        return isRecord
    }

    override fun onEat() {
        Feedback.eat(this)
    }

    override fun onCrash() {
        Feedback.error(this)
    }

    override fun onPause() {
        // Leaving the screen freezes the game clock and keeps the current board.
        super.onPause()
        snakeView.pause()
    }
}
