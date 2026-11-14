package com.rougenoir.app.ui

import android.animation.ObjectAnimator
import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.text.method.PasswordTransformationMethod
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.ImageButton
import android.widget.TextView
import androidx.appcompat.widget.AppCompatButton
import androidx.appcompat.widget.AppCompatCheckBox
import androidx.core.content.res.ResourcesCompat
import com.rougenoir.app.R
import com.rougenoir.app.data.Prefs
import com.rougenoir.app.util.Feedback

/** Signs the player into the salon's local demo account and restores opted-in sessions. */
class LoginActivity : BaseActivity() {

    private lateinit var usernameInput: EditText
    private lateinit var passwordInput: EditText
    private lateinit var passwordToggle: ImageButton
    private lateinit var rememberCheck: AppCompatCheckBox
    private lateinit var errorText: TextView
    private lateinit var loginButton: AppCompatButton
    private var passwordVisible = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (Prefs.remember(this) && Prefs.isLoggedIn(this)) {
            openSalon()
            return
        }
        if (!Prefs.remember(this)) Prefs.setLoggedIn(this, false)

        setContentView(R.layout.activity_login)
        bindViews()
        prepareForm()
    }

    private fun bindViews() {
        usernameInput = findViewById(R.id.usernameInput)
        passwordInput = findViewById(R.id.passwordInput)
        passwordToggle = findViewById(R.id.passwordToggle)
        rememberCheck = findViewById(R.id.rememberCheck)
        errorText = findViewById(R.id.errorText)
        loginButton = findViewById(R.id.loginButton)
        val demoButton = findViewById<TextView>(R.id.demoButton)

        loginButton.setOnClickListener { attemptLogin() }
        demoButton.setOnClickListener { fillDemo() }
        passwordToggle.setOnClickListener { togglePasswordVisibility() }
        passwordInput.setOnEditorActionListener { _, actionId, event ->
            val pressedDone = event?.keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_DOWN
            if (actionId == EditorInfo.IME_ACTION_DONE || pressedDone) {
                attemptLogin()
                true
            } else {
                false
            }
        }

        // Clear the previous validation result as soon as the player edits either field.
        val clearError = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                if (errorText.visibility == View.VISIBLE) errorText.visibility = View.GONE
            }

            override fun afterTextChanged(s: Editable?) = Unit
        }
        usernameInput.addTextChangedListener(clearError)
        passwordInput.addTextChangedListener(clearError)
    }

    private fun prepareForm() {
        usernameInput.setText(Prefs.username(this))
        rememberCheck.isChecked = Prefs.remember(this)
        passwordInput.typeface = ResourcesCompat.getFont(this, R.font.nunito_semibold)
        if (usernameInput.text.isNullOrBlank()) {
            usernameInput.requestFocus()
        } else {
            passwordInput.requestFocus()
        }
        window.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_UNCHANGED)
    }

    /** Validate both fields before committing the remember-me and sign-in settings. */
    private fun attemptLogin() {
        val username = usernameInput.text.toString().trim()
        val password = passwordInput.text.toString()
        if (username.isEmpty() || password.isEmpty()) {
            showError(getString(R.string.login_error_empty))
            shake(if (username.isEmpty()) usernameInput else passwordInput)
            return
        }
        if (!Prefs.authenticate(this, username, password)) {
            showError(getString(R.string.login_error_invalid))
            shake(passwordInput)
            Feedback.error(this)
            return
        }
        Prefs.setRemember(this, rememberCheck.isChecked)
        Prefs.setLoggedIn(this, true)
        Feedback.win(this)
        hideKeyboard()
        openSalon()
    }

    private fun openSalon() {
        startActivity(Intent(this, MenuActivity::class.java))
        finish()
    }

    private fun fillDemo() {
        usernameInput.setText(Prefs.DEFAULT_USERNAME)
        passwordInput.setText(Prefs.DEFAULT_PASSWORD)
        errorText.visibility = View.GONE
        passwordInput.requestFocus()
        passwordInput.setSelection(passwordInput.text.length)
        Feedback.select(this)
    }

    /** Preserve the cursor and custom font when switching the input transformation. */
    private fun togglePasswordVisibility() {
        passwordVisible = !passwordVisible
        val cursor = passwordInput.selectionStart
        if (passwordVisible) {
            passwordInput.inputType =
                InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
            passwordInput.transformationMethod = null
            passwordToggle.setImageResource(R.drawable.ic_eye_off)
            passwordToggle.contentDescription = getString(R.string.hide_password)
        } else {
            passwordInput.inputType =
                InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            passwordInput.transformationMethod = PasswordTransformationMethod.getInstance()
            passwordToggle.setImageResource(R.drawable.ic_eye)
            passwordToggle.contentDescription = getString(R.string.show_password)
        }
        passwordInput.typeface = ResourcesCompat.getFont(this, R.font.nunito_semibold)
        passwordInput.setSelection(cursor.coerceIn(0, passwordInput.text.length))
        Feedback.tap(this)
    }

    private fun showError(message: String) {
        errorText.text = message
        errorText.visibility = View.VISIBLE
    }

    private fun shake(view: View) {
        ObjectAnimator.ofFloat(view, "translationX", 0f, 10f, -9f, 7f, -5f, 0f).apply {
            duration = 340
            start()
        }
    }

    private fun hideKeyboard() {
        val manager = getSystemService(INPUT_METHOD_SERVICE) as? InputMethodManager
        manager?.hideSoftInputFromWindow(window.decorView.windowToken, 0)
    }
}
