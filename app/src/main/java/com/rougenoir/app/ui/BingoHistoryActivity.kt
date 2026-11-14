package com.rougenoir.app.ui

import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.ImageButton
import android.widget.ListView
import android.widget.TextView
import androidx.core.view.isVisible
import com.rougenoir.app.R
import com.rougenoir.app.RougeNoirApp
import com.rougenoir.app.data.BingoDatabase
import com.rougenoir.app.data.Prefs
import com.rougenoir.app.util.Feedback
import java.text.DateFormat
import java.util.Date

/** Presents completed Bingo rounds for the account that's signed in on this device. */
class BingoHistoryActivity : BaseActivity() {
    private lateinit var history: ListView
    private lateinit var message: TextView
    private lateinit var retry: View
    private lateinit var username: String
    private val dateTimeFormat = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.MEDIUM)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!Prefs.isLoggedIn(this)) {
            finish()
            return
        }
        username = Prefs.username(this)
        setContentView(R.layout.activity_bingo_history)
        history = findViewById(R.id.bingoHistoryList)
        message = findViewById(R.id.bingoHistoryMessage)
        retry = findViewById(R.id.bingoHistoryRetry)
        findViewById<ImageButton>(R.id.backButton).setOnClickListener {
            Feedback.tap(this)
            finish()
        }
        retry.setOnClickListener { loadHistory() }
        loadHistory()
    }

    private fun loadHistory() {
        retry.isVisible = false
        message.setText(R.string.bingo_history_loading)
        (application as RougeNoirApp).bingoDatabase.readRounds(username) { result ->
            if (isFinishing || isDestroyed) return@readRounds
            result.fold(
                onSuccess = { rounds ->
                    history.adapter = RoundAdapter(rounds)
                    message.isVisible = rounds.isEmpty()
                    message.setText(R.string.bingo_history_empty)
                },
                onFailure = {
                    message.setText(R.string.bingo_history_error)
                    retry.isVisible = true
                }
            )
        }
    }

    /** ListView reuses the visible row views as the player scrolls through their rounds. */
    private inner class RoundAdapter(rounds: List<BingoDatabase.Round>) :
        ArrayAdapter<BingoDatabase.Round>(this@BingoHistoryActivity, R.layout.item_bingo_round, rounds) {
        override fun areAllItemsEnabled() = false
        override fun isEnabled(position: Int) = false

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val row = convertView ?: layoutInflater.inflate(R.layout.item_bingo_round, parent, false)
            val round = checkNotNull(getItem(position))
            row.findViewById<TextView>(R.id.bingoHistoryRound).text = getString(R.string.bingo_round_number, round.number)
            row.findViewById<TextView>(R.id.bingoHistoryNumbers).text = round.winningNumbers.joinToString(" · ") {
                getString(R.string.bingo_number, it)
            }
            row.findViewById<TextView>(R.id.bingoHistoryDetails).text = getString(
                R.string.bingo_history_details,
                round.username,
                dateTimeFormat.format(Date(round.completedAtMillis))
            )
            return row
        }
    }
}
