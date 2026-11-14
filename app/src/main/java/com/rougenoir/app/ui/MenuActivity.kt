package com.rougenoir.app.ui

import android.content.Intent
import android.os.Bundle
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import com.rougenoir.app.R
import com.rougenoir.app.data.Prefs
import com.rougenoir.app.util.Feedback
import java.util.Locale

/** Shows activities and score summaries for the signed-in salon player. */
class MenuActivity : BaseActivity() {

    private lateinit var greeting: TextView
    private lateinit var profileSeal: TextView
    private lateinit var statDuels: TextView
    private lateinit var statSnake: TextView
    private lateinit var statWall: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!Prefs.isLoggedIn(this)) {
            goToLogin()
            return
        }
        setContentView(R.layout.activity_menu)
        bindViews()
        wireClicks()
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                moveTaskToBack(true)
            }
        })
    }

    override fun onResume() {
        super.onResume()
        if (!Prefs.isLoggedIn(this)) {
            goToLogin()
            return
        }
        refreshIdentity()
        refreshStats()
    }

    private fun bindViews() {
        greeting = findViewById(R.id.menuGreeting)
        profileSeal = findViewById(R.id.profileSeal)
        statDuels = findViewById(R.id.statDuels)
        statSnake = findViewById(R.id.statSnake)
        statWall = findViewById(R.id.statWall)
    }

    private fun wireClicks() {
        findViewById<LinearLayout>(R.id.gameBingoCard).setOnClickListener {
            Feedback.tap(this)
            startActivity(Intent(this, BingoActivity::class.java))
        }
        findViewById<LinearLayout>(R.id.gameTttCard).setOnClickListener {
            Feedback.tap(this)
            startActivity(Intent(this, TicTacToeActivity::class.java))
        }
        findViewById<LinearLayout>(R.id.gameSnakeCard).setOnClickListener {
            Feedback.tap(this)
            startActivity(Intent(this, SnakeActivity::class.java))
        }
        findViewById<LinearLayout>(R.id.gameBreakoutCard).setOnClickListener {
            Feedback.tap(this)
            startActivity(Intent(this, BreakoutActivity::class.java))
        }
        findViewById<LinearLayout>(R.id.settingsCard).setOnClickListener {
            Feedback.tap(this)
            startActivity(Intent(this, SettingsActivity::class.java))
        }
        profileSeal.setOnClickListener {
            Feedback.tap(this)
            startActivity(Intent(this, SettingsActivity::class.java))
        }
    }

    private fun refreshIdentity() {
        val displayName = Prefs.displayName(this)
        greeting.text = getString(R.string.menu_greeting, displayName)
        // Use the player's first visible letter as the profile seal.
        val initial = displayName.trim().takeIf { it.isNotEmpty() }?.first()?.toString()
            ?.uppercase(Locale.getDefault()) ?: "T"
        profileSeal.text = initial
    }

    private fun refreshStats() {
        statDuels.text = Prefs.tttWins(this).toString()
        statSnake.text = Prefs.snakeBest(this).toString()
        statWall.text = Prefs.breakoutBest(this).toString()
    }

    private fun goToLogin() {
        // Clear the task so Back cannot return to a signed-out menu.
        startActivity(
            Intent(this, LoginActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
        )
        finish()
    }
}
