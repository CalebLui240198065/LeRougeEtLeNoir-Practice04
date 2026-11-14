package com.rougenoir.app.ui

import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.ImageButton
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatDialog
import androidx.appcompat.widget.AppCompatButton
import androidx.appcompat.widget.SwitchCompat
import com.rougenoir.app.R
import com.rougenoir.app.data.Prefs
import com.rougenoir.app.util.Feedback

/** Lets the player manage the local profile, game preferences, and original score ledger. */
class SettingsActivity : BaseActivity() {

    private lateinit var displayNameInput: EditText
    private lateinit var usernameLabel: TextView
    private lateinit var difficultyGroup: RadioGroup
    private lateinit var difficultyDescription: TextView
    private lateinit var soundSwitch: SwitchCompat
    private lateinit var hapticsSwitch: SwitchCompat

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!Prefs.isLoggedIn(this)) {
            goToLogin()
            return
        }
        setContentView(R.layout.activity_settings)
        bindViews()
        // Set initial values before attaching listeners so loading the screen is silent.
        populate()
        wireActions()
    }

    private fun bindViews() {
        displayNameInput = findViewById(R.id.displayNameInput)
        usernameLabel = findViewById(R.id.settingsUsername)
        difficultyGroup = findViewById(R.id.difficultyGroup)
        difficultyDescription = findViewById(R.id.difficultyDescription)
        soundSwitch = findViewById(R.id.soundSwitch)
        hapticsSwitch = findViewById(R.id.hapticsSwitch)
    }

    private fun populate() {
        usernameLabel.text = getString(R.string.settings_username_format, Prefs.username(this))
        displayNameInput.setText(Prefs.displayName(this))
        displayNameInput.setSelection(displayNameInput.text.length)
        soundSwitch.isChecked = Prefs.soundEnabled(this)
        hapticsSwitch.isChecked = Prefs.hapticsEnabled(this)

        when (Prefs.difficulty(this)) {
            Prefs.Difficulty.APPRENTICE -> difficultyGroup.check(R.id.difficultyEasy)
            Prefs.Difficulty.ADVOCATE -> difficultyGroup.check(R.id.difficultyMedium)
            Prefs.Difficulty.MASTER -> difficultyGroup.check(R.id.difficultyHard)
        }
        difficultyDescription.setText(descriptionFor(Prefs.difficulty(this)))
        refreshLedger()
    }

    private fun wireActions() {
        findViewById<ImageButton>(R.id.backButton).setOnClickListener {
            Feedback.tap(this)
            finish()
        }

        findViewById<AppCompatButton>(R.id.saveIdentityButton).setOnClickListener {
            saveIdentity()
        }
        displayNameInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                saveIdentity()
                true
            } else {
                false
            }
        }

        findViewById<AppCompatButton>(R.id.changePasswordButton).setOnClickListener {
            Feedback.tap(this)
            showChangePasswordDialog()
        }

        soundSwitch.setOnCheckedChangeListener { _, checked ->
            Prefs.setSoundEnabled(this, checked)
            Feedback.tap(this)
        }
        hapticsSwitch.setOnCheckedChangeListener { _, checked ->
            Prefs.setHapticsEnabled(this, checked)
            Feedback.tap(this)
        }

        difficultyGroup.setOnCheckedChangeListener { _, checkedId ->
            val difficulty = when (checkedId) {
                R.id.difficultyEasy -> Prefs.Difficulty.APPRENTICE
                R.id.difficultyHard -> Prefs.Difficulty.MASTER
                else -> Prefs.Difficulty.ADVOCATE
            }
            Prefs.setDifficulty(this, difficulty)
            difficultyDescription.setText(descriptionFor(difficulty))
            Feedback.select(this)
        }

        findViewById<AppCompatButton>(R.id.resetRecordsButton).setOnClickListener {
            showConfirmDialog(
                title = getString(R.string.settings_reset_title),
                message = getString(R.string.settings_reset_message),
                action = getString(R.string.settings_reset_confirm)
            ) {
                Prefs.resetRecords(this)
                refreshLedger()
                Toast.makeText(this, R.string.settings_reset_done, Toast.LENGTH_SHORT).show()
                Feedback.select(this)
            }
        }

        findViewById<AppCompatButton>(R.id.signOutButton).setOnClickListener {
            showConfirmDialog(
                title = getString(R.string.settings_sign_out_title),
                message = getString(R.string.settings_sign_out_message),
                action = getString(R.string.settings_sign_out_confirm)
            ) {
                Prefs.logout(this)
                Toast.makeText(this, R.string.settings_signed_out, Toast.LENGTH_SHORT).show()
                goToLogin()
            }
        }
    }

    private fun saveIdentity() {
        val name = displayNameInput.text.toString().trim()
        if (name.isEmpty()) {
            Toast.makeText(this, R.string.settings_name_empty, Toast.LENGTH_SHORT).show()
            Feedback.error(this)
            return
        }
        Prefs.setDisplayName(this, name)
        displayNameInput.setText(name)
        displayNameInput.setSelection(displayNameInput.text.length)
        Toast.makeText(this, R.string.settings_identity_saved, Toast.LENGTH_SHORT).show()
        Feedback.select(this)
    }

    private fun descriptionFor(difficulty: Prefs.Difficulty): Int = when (difficulty) {
        Prefs.Difficulty.APPRENTICE -> R.string.settings_difficulty_easy_desc
        Prefs.Difficulty.ADVOCATE -> R.string.settings_difficulty_medium_desc
        Prefs.Difficulty.MASTER -> R.string.settings_difficulty_hard_desc
    }

    private fun refreshLedger() {
        findViewById<TextView>(R.id.settingsTttWins).text = Prefs.tttWins(this).toString()
        findViewById<TextView>(R.id.settingsTttDraws).text = Prefs.tttDraws(this).toString()
        findViewById<TextView>(R.id.settingsSnakeBest).text = Prefs.snakeBest(this).toString()
        findViewById<TextView>(R.id.settingsBreakoutBest).text = Prefs.breakoutBest(this).toString()
    }

    /** Keep the dialog open on validation errors so the player can correct their entries. */
    private fun showChangePasswordDialog() {
        val content = layoutInflater.inflate(R.layout.dialog_change_password, null)
        val dialog = createDialog(content)
        val current = content.findViewById<EditText>(R.id.currentPasswordInput)
        val newPassword = content.findViewById<EditText>(R.id.newPasswordInput)
        val confirmation = content.findViewById<EditText>(R.id.confirmPasswordInput)
        val error = content.findViewById<TextView>(R.id.passwordError)
        content.findViewById<AppCompatButton>(R.id.cancelPasswordButton).setOnClickListener {
            dialog.dismiss()
        }
        val saveButton = content.findViewById<AppCompatButton>(R.id.savePasswordButton)
        confirmation.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                saveButton.performClick()
                true
            } else {
                false
            }
        }
        saveButton.setOnClickListener {
            if (current.text.isBlank() || newPassword.text.isBlank() || confirmation.text.isBlank()) {
                error.text = getString(R.string.settings_password_empty)
                error.visibility = View.VISIBLE
                Feedback.error(this)
                return@setOnClickListener
            }
            if (newPassword.text.toString() != confirmation.text.toString()) {
                error.text = getString(R.string.settings_password_mismatch)
                error.visibility = View.VISIBLE
                Feedback.error(this)
                return@setOnClickListener
            }
            if (newPassword.text.toString().length < 4) {
                error.text = getString(R.string.settings_password_short)
                error.visibility = View.VISIBLE
                Feedback.error(this)
                return@setOnClickListener
            }
            when (Prefs.changePassword(
                this,
                current.text.toString(),
                newPassword.text.toString()
            )) {
                Prefs.PasswordChange.WRONG_CURRENT -> {
                    error.text = getString(R.string.settings_password_wrong)
                    error.visibility = View.VISIBLE
                    Feedback.error(this)
                }
                Prefs.PasswordChange.TOO_SHORT -> {
                    error.text = getString(R.string.settings_password_short)
                    error.visibility = View.VISIBLE
                    Feedback.error(this)
                }
                Prefs.PasswordChange.OK -> {
                    dialog.dismiss()
                    Toast.makeText(this, R.string.settings_password_done, Toast.LENGTH_SHORT).show()
                    Feedback.win(this)
                }
            }
        }
        dialog.show()
        dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        current.requestFocus()
    }

    /** Require a deliberate tap before signing out or clearing the original game scores. */
    private fun showConfirmDialog(title: String, message: String, action: String, onConfirm: () -> Unit) {
        val content = layoutInflater.inflate(R.layout.dialog_confirm, null)
        val dialog = createDialog(content)
        content.findViewById<TextView>(R.id.confirmEyebrow).text = getString(R.string.settings_confirm_eyebrow)
        content.findViewById<TextView>(R.id.confirmTitle).text = title
        content.findViewById<TextView>(R.id.confirmMessage).text = message
        content.findViewById<AppCompatButton>(R.id.confirmActionButton).apply {
            text = action
            setOnClickListener {
                dialog.dismiss()
                onConfirm()
            }
        }
        content.findViewById<AppCompatButton>(R.id.confirmCancelButton).setOnClickListener {
            dialog.dismiss()
        }
        dialog.show()
    }

    private fun createDialog(content: View): AppCompatDialog {
        val dialog = AppCompatDialog(this, R.style.Theme_RougeNoir_Dialog)
        dialog.setContentView(content)
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        dialog.window?.setLayout(
            (resources.displayMetrics.widthPixels * 0.90f).toInt(),
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        dialog.setCanceledOnTouchOutside(true)
        return dialog
    }

    private fun goToLogin() {
        startActivity(
            Intent(this, LoginActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
        )
        finish()
    }
}
