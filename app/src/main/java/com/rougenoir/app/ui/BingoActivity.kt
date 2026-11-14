package com.rougenoir.app.ui

import android.content.Intent
import android.os.Bundle
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.widget.AppCompatButton
import com.rougenoir.app.R
import com.rougenoir.app.RougeNoirApp
import com.rougenoir.app.data.Prefs
import com.rougenoir.app.game.BingoGame
import com.rougenoir.app.util.Feedback
import java.util.UUID

/**
 * Controls one Bingo card. The game owns the rules, this screen handles input
 * and rendering, and the application database owns completed round records.
 */
class BingoActivity : BaseActivity() {
    private lateinit var game: BingoGame
    private lateinit var roundId: String
    private lateinit var username: String
    private var completionTime: Long? = null
    private var savedRoundNumber: Long? = null
    private var saving = false

    private lateinit var tiles: List<AppCompatButton>
    private lateinit var drawNumber: TextView
    private lateinit var progress: TextView
    private lateinit var status: TextView
    private lateinit var action: AppCompatButton

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!Prefs.isLoggedIn(this)) {
            finish()
            return
        }
        username = Prefs.username(this)
        restoreRound(savedInstanceState)
        setContentView(R.layout.activity_bingo)
        drawNumber = findViewById(R.id.bingoDrawNumber)
        progress = findViewById(R.id.bingoProgress)
        status = findViewById(R.id.bingoStatus)
        action = findViewById(R.id.bingoAction)
        tiles = buildCard(findViewById(R.id.bingoCard))

        findViewById<ImageButton>(R.id.backButton).setOnClickListener {
            Feedback.tap(this)
            finish()
        }
        findViewById<AppCompatButton>(R.id.bingoHistoryButton).setOnClickListener {
            Feedback.tap(this)
            startActivity(Intent(this, BingoHistoryActivity::class.java))
        }
        action.setOnClickListener {
            Feedback.tap(this)
            when {
                savedRoundNumber != null -> {
                    startRound()
                    render()
                }
                game.winningLine != null -> saveWin()
                game.canCall -> {
                    game.callNext()
                    render()
                }
            }
        }

        if (game.winningLine != null && savedRoundNumber == null) saveWin() else render()
    }

    private fun startRound() {
        game = BingoGame.shuffled()
        roundId = UUID.randomUUID().toString()
        completionTime = null
        savedRoundNumber = null
    }

    private fun restoreRound(state: Bundle?) {
        val card = state?.getIntArray("card")
        val order = state?.getIntArray("callOrder")
        val id = state?.getString("roundId")
        if (state == null || card == null || order == null || id == null) {
            startRound()
            return
        }
        game = BingoGame(
            card.toList(),
            order.toList(),
            state.getInt("calledCount"),
            state.getIntArray("marks")?.toList().orEmpty()
        )
        roundId = id
        if (state.containsKey("completionTime")) completionTime = state.getLong("completionTime")
        savedRoundNumber = state.getLong("savedRoundNumber").takeIf { it > 0 }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        if (!::game.isInitialized) return
        // Restore the exact call order, card and marks when Android recreates the
        // activity. Reuse the ID and timestamp if a finished round needs retrying.
        outState.putIntArray("card", game.card.toIntArray())
        outState.putIntArray("callOrder", game.callOrder.toIntArray())
        outState.putIntArray("marks", game.markedIndices.toIntArray())
        outState.putInt("calledCount", game.calledCount)
        outState.putString("roundId", roundId)
        completionTime?.let { outState.putLong("completionTime", it) }
        savedRoundNumber?.let { outState.putLong("savedRoundNumber", it) }
    }

    /** Use real buttons so each square is a separate touch and accessibility target. */
    private fun buildCard(card: LinearLayout): List<AppCompatButton> = buildList {
        repeat(BingoGame.SIDE) { rowIndex ->
            val row = LinearLayout(this@BingoActivity).apply { orientation = LinearLayout.HORIZONTAL }
            card.addView(
                row,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
            )
            repeat(BingoGame.SIDE) { columnIndex ->
                val index = rowIndex * BingoGame.SIDE + columnIndex
                val tile = layoutInflater.inflate(R.layout.item_bingo_square, row, false) as AppCompatButton
                tile.setOnClickListener {
                    if (game.mark(index)) {
                        Feedback.mark(this@BingoActivity)
                        if (game.winningLine != null) {
                            completionTime = System.currentTimeMillis()
                            saveWin()
                        } else {
                            render()
                        }
                    }
                }
                row.addView(tile)
                add(tile)
            }
        }
    }

    private fun saveWin() {
        val line = game.winningLine ?: return
        if (saving) return
        val completedAt = completionTime ?: System.currentTimeMillis().also { completionTime = it }
        saving = true
        render()

        (application as RougeNoirApp).bingoDatabase.saveRound(
            roundId, username, line.map { game.card[it] }, completedAt
        ) { result ->
            // SQLite can finish while this activity is being replaced or closed.
            if (isFinishing || isDestroyed) return@saveRound
            saving = false
            savedRoundNumber = result.getOrNull()
            if (savedRoundNumber != null) Feedback.win(this) else Feedback.error(this)
            render()
        }
    }

    private fun render() {
        val line = game.winningLine
        val marks = game.markedIndices
        val roundNumber = savedRoundNumber
        val lastCall = game.calledNumbers.lastOrNull()
        drawNumber.text = lastCall?.let { getString(R.string.bingo_number, it) }
            ?: getString(R.string.bingo_draw_placeholder)
        progress.text = getString(R.string.bingo_progress, game.calledCount, BingoGame.CELL_COUNT)

        tiles.forEachIndexed { index, tile ->
            val marked = index in marks
            val called = game.isCalled(index)
            val won = line?.contains(index) == true
            val value = game.card[index]
            tile.text = getString(if (marked) R.string.bingo_checked_number else R.string.bingo_number, value)
            tile.isSelected = marked
            tile.isActivated = won
            tile.isEnabled = called && !marked && line == null
            tile.contentDescription = getString(
                when {
                    won -> R.string.bingo_square_winner
                    marked -> R.string.bingo_square_marked
                    called -> R.string.bingo_square_called
                    else -> R.string.bingo_square_uncalled
                },
                value
            )
        }

        status.text = when {
            roundNumber != null -> getString(R.string.bingo_saved, roundNumber)
            saving -> getString(R.string.bingo_saving)
            line != null -> getString(R.string.bingo_save_failed)
            !game.canCall -> getString(R.string.bingo_finish_marks)
            else -> getString(R.string.bingo_instructions)
        }
        action.setText(
            when {
                roundNumber != null -> R.string.bingo_new_round
                saving -> R.string.bingo_saving_action
                line != null -> R.string.bingo_retry
                !game.canCall -> R.string.bingo_no_more_calls
                else -> R.string.bingo_draw
            }
        )
        action.isEnabled = !saving && (line != null || game.canCall)
        action.alpha = if (action.isEnabled) 1f else 0.55f
    }
}
