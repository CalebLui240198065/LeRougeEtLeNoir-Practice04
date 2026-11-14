package com.rougenoir.app.ui

import android.os.Bundle
import android.view.WindowManager
import android.widget.ImageButton
import android.widget.TextView
import androidx.appcompat.widget.AppCompatButton
import com.rougenoir.app.R
import com.rougenoir.app.data.Prefs
import com.rougenoir.app.ui.view.BreakoutView
import com.rougenoir.app.util.Feedback

/** Connects the Breakout board to its controls, score and lives display, and feedback. */
class BreakoutActivity : BaseActivity(), BreakoutView.Listener {

    private lateinit var breakoutView: BreakoutView
    private lateinit var scoreView: TextView
    private lateinit var livesView: TextView
    private lateinit var statusView: TextView
    private lateinit var pauseButton: AppCompatButton

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_breakout)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        bindViews()
        breakoutView.setDifficulty(Prefs.difficulty(this).level)
        breakoutView.listener = this
        breakoutView.newGame()
        updateState(BreakoutView.State.READY)
        wireActions()
    }

    private fun bindViews() {
        breakoutView = findViewById(R.id.breakoutView)
        scoreView = findViewById(R.id.breakoutScore)
        livesView = findViewById(R.id.breakoutLives)
        statusView = findViewById(R.id.breakoutStatus)
        pauseButton = findViewById(R.id.breakoutPauseButton)
    }

    private fun wireActions() {
        findViewById<ImageButton>(R.id.backButton).setOnClickListener {
            Feedback.tap(this)
            finish()
        }
        findViewById<AppCompatButton>(R.id.newSiegeButton).setOnClickListener {
            Feedback.select(this)
            breakoutView.newGame()
            updateState(BreakoutView.State.READY)
        }
        pauseButton.setOnClickListener {
            Feedback.tap(this)
            breakoutView.togglePause()
        }
    }

    override fun onScoreChanged(score: Int, lives: Int) {
        scoreView.text = score.toString()
        livesView.text = when (lives) {
            3 -> "III"
            2 -> "II"
            1 -> "I"
            else -> "–"
        }
    }

    override fun onStateChanged(state: BreakoutView.State) {
        updateState(state)
    }

    private fun updateState(state: BreakoutView.State) {
        statusView.setText(
            when (state) {
                BreakoutView.State.READY -> R.string.breakout_ready
                BreakoutView.State.RUNNING -> R.string.breakout_running
                BreakoutView.State.PAUSED -> R.string.breakout_paused
                BreakoutView.State.WON -> R.string.breakout_win
                BreakoutView.State.LOST -> R.string.breakout_over
            }
        )
        val canPause = state == BreakoutView.State.RUNNING || state == BreakoutView.State.PAUSED
        pauseButton.isEnabled = canPause
        pauseButton.alpha = if (canPause) 1f else 0.45f
        pauseButton.setText(
            if (state == BreakoutView.State.PAUSED) {
                R.string.breakout_resume
            } else {
                R.string.breakout_pause
            }
        )
    }

    override fun onBrickHit() {
        Feedback.strike(this)
    }

    override fun onPaddleHit() {
        Feedback.tap(this)
    }

    override fun onLifeLost() {
        Feedback.error(this)
    }

    override fun onGameOver(score: Int, won: Boolean): Boolean {
        return Prefs.recordBreakoutScore(this, score)
    }

    override fun onPause() {
        // Stop advancing the ball while this activity is in the background.
        super.onPause()
        breakoutView.pause()
    }
}
