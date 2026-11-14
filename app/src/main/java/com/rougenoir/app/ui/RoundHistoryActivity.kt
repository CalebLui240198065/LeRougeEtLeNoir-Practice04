package com.rougenoir.app.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.ImageButton
import android.widget.ListView
import android.widget.TextView
import androidx.appcompat.widget.AppCompatSpinner
import androidx.core.view.isVisible
import com.rougenoir.app.R
import com.rougenoir.app.RougeNoirApp
import com.rougenoir.app.data.Prefs
import com.rougenoir.app.data.RoundDatabase
import com.rougenoir.app.game.GameType
import com.rougenoir.app.game.RoundOutcome
import com.rougenoir.app.game.RoundResult
import com.rougenoir.app.game.SnakeEnding
import com.rougenoir.app.util.Feedback
import java.text.DateFormat
import java.util.Date

/** Shows the signed-in player's completed rounds, with an optional game filter. */
class RoundHistoryActivity : BaseActivity() {
    private lateinit var history: ListView
    private lateinit var message: TextView
    private lateinit var retry: View
    private lateinit var username: String
    private val filters = listOf(null) + GameType.entries
    private var selectedFilter = 0
    private var requestNumber = 0
    private val dateTimeFormat = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.MEDIUM)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!Prefs.isLoggedIn(this)) {
            finish()
            return
        }
        username = Prefs.username(this)
        setContentView(R.layout.activity_round_history)
        history = findViewById(R.id.historyList)
        message = findViewById(R.id.historyMessage)
        retry = findViewById(R.id.historyRetry)
        findViewById<ImageButton>(R.id.backButton).setOnClickListener {
            Feedback.tap(this)
            finish()
        }
        retry.setOnClickListener { loadHistory() }
        bindFilter(savedInstanceState)
        loadHistory()
    }

    private fun bindFilter(savedInstanceState: Bundle?) {
        val filter = findViewById<AppCompatSpinner>(R.id.historyGameFilter)
        val requestedGame = intent.getStringExtra(GAME_FILTER)
        selectedFilter = savedInstanceState?.getInt(GAME_FILTER)
            ?: filters.indexOfFirst { it?.key == requestedGame }.coerceAtLeast(0)
        filter.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_item,
            filters.map { getString(gameLabel(it)) }
        ).apply { setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }
        filter.setSelection(selectedFilter)
        filter.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (selectedFilter == position) return
                selectedFilter = position
                loadHistory()
            }

            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putInt(GAME_FILTER, selectedFilter)
        super.onSaveInstanceState(outState)
    }

    private fun loadHistory() {
        val request = ++requestNumber
        history.isVisible = false
        retry.isVisible = false
        message.isVisible = true
        message.setText(R.string.history_loading)
        (application as RougeNoirApp).roundDatabase.readRounds(username, filters[selectedFilter]) { result ->
            // A rapid filter change should never show the response for a previous selection.
            if (isFinishing || isDestroyed || request != requestNumber) return@readRounds
            result.fold(
                onSuccess = { rounds ->
                    history.adapter = RoundAdapter(rounds)
                    history.isVisible = rounds.isNotEmpty()
                    message.isVisible = rounds.isEmpty()
                    message.setText(R.string.history_empty)
                },
                onFailure = {
                    message.setText(R.string.history_load_error)
                    retry.isVisible = true
                }
            )
        }
    }

    /** The row layout is reused as the player scrolls. Bind every field for each result. */
    private inner class RoundAdapter(rounds: List<RoundDatabase.Round>) :
        ArrayAdapter<RoundDatabase.Round>(this@RoundHistoryActivity, R.layout.item_game_round, rounds) {
        override fun areAllItemsEnabled() = false
        override fun isEnabled(position: Int) = false

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val row = convertView ?: layoutInflater.inflate(R.layout.item_game_round, parent, false)
            val round = checkNotNull(getItem(position))
            val result = round.result
            row.findViewById<TextView>(R.id.historyRoundTitle).text =
                getString(R.string.history_round_title, getString(gameLabel(result.game)), round.number)
            row.findViewById<TextView>(R.id.historyOutcome).setText(
                when (result.outcome) {
                    RoundOutcome.WIN -> R.string.history_won
                    RoundOutcome.LOSS -> R.string.history_lost
                    RoundOutcome.DRAW -> R.string.history_draw
                }
            )
            row.findViewById<TextView>(R.id.historyRoundDetails).text = describe(result)
            row.findViewById<TextView>(R.id.historyRoundMeta).text = getString(
                R.string.history_round_meta,
                round.username,
                dateTimeFormat.format(Date(round.completedAtMillis))
            )
            return row
        }
    }

    private fun describe(result: RoundResult): String = when (result) {
        is RoundResult.Bingo -> {
            val numbers = result.winningNumbers.joinToString(" · ") { getString(R.string.bingo_number, it) }
            val line = getString(R.string.history_bingo_numbers, numbers)
            // Older Bingo records do not have a draw count to display.
            result.drawCount?.let { getString(R.string.history_bingo_round, line, it) } ?: line
        }
        is RoundResult.TicTacToe -> getString(
            R.string.history_duel_round,
            getString(if (result.armyRival) R.string.ttt_army_short else R.string.ttt_church_short),
            result.moveCount,
            difficultyName(result.difficulty)
        )
        is RoundResult.Snake -> getString(
            R.string.history_snake_round,
            result.score,
            getString(
                when (result.ending) {
                    SnakeEnding.WALL -> R.string.history_snake_wall
                    SnakeEnding.TAIL -> R.string.history_snake_tail
                    SnakeEnding.BOARD_FILLED -> R.string.snake_board_filled
                }
            ),
            difficultyName(result.difficulty)
        )
        is RoundResult.Breakout -> getString(
            R.string.history_breakout_round,
            result.score,
            result.livesLeft,
            difficultyName(result.difficulty)
        )
    }

    private fun difficultyName(level: Int): String = getString(
        when (Prefs.Difficulty.fromLevel(level)) {
            Prefs.Difficulty.APPRENTICE -> R.string.settings_difficulty_easy
            Prefs.Difficulty.ADVOCATE -> R.string.settings_difficulty_medium
            Prefs.Difficulty.MASTER -> R.string.settings_difficulty_hard
        }
    )

    private fun gameLabel(game: GameType?): Int = when (game) {
        null -> R.string.history_all_games
        GameType.BINGO -> R.string.bingo_title
        GameType.TIC_TAC_TOE -> R.string.history_tic_tac_toe
        GameType.SNAKE -> R.string.history_snake
        GameType.BREAKOUT -> R.string.history_breakout
    }

    companion object {
        private const val GAME_FILTER = "gameFilter"

        internal fun intent(context: Context, game: GameType? = null): Intent =
            Intent(context, RoundHistoryActivity::class.java).apply {
                putExtra(GAME_FILTER, game?.key)
            }
    }
}
