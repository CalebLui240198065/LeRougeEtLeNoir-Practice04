package com.rougenoir.app.data

import android.content.Context
import android.content.SharedPreferences
import android.util.Base64
import java.security.MessageDigest
import java.security.SecureRandom

/**
 * Stores the local demo identity, sign-in state, settings, and the original games'
 * scores in one SharedPreferences file. Completed Bingo rounds are kept in SQLite.
 */
object Prefs {

    const val DEFAULT_USERNAME = "julien"
    const val DEFAULT_PASSWORD = "rouge1820"
    const val DEFAULT_DISPLAY_NAME = "Julien"

    private const val FILE = "rouge_noir_prefs"
    private const val KEY_INITIALIZED = "initialized"
    private const val KEY_USERNAME = "username"
    private const val KEY_DISPLAY_NAME = "display_name"
    private const val KEY_PASSWORD_HASH = "password_hash"
    private const val KEY_PASSWORD_SALT = "password_salt"
    private const val KEY_REMEMBER = "remember_me"
    private const val KEY_LOGGED_IN = "logged_in"
    private const val KEY_SOUND = "sound_enabled"
    private const val KEY_HAPTICS = "haptics_enabled"
    private const val KEY_DIFFICULTY = "difficulty"
    private const val KEY_TTT_WINS = "ttt_wins"
    private const val KEY_TTT_LOSSES = "ttt_losses"
    private const val KEY_TTT_DRAWS = "ttt_draws"
    private const val KEY_SNAKE_BEST = "snake_best"
    private const val KEY_BREAKOUT_BEST = "breakout_best"

    enum class Difficulty(val level: Int) {
        APPRENTICE(0),
        ADVOCATE(1),
        MASTER(2);

        companion object {
            fun fromLevel(level: Int): Difficulty = entries.firstOrNull { it.level == level } ?: ADVOCATE
        }
    }

    enum class PasswordChange { OK, WRONG_CURRENT, TOO_SHORT }

    private fun store(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    /** Creates the demo identity exactly once. */
    fun ensureInitialized(context: Context) {
        val prefs = store(context)
        if (prefs.getBoolean(KEY_INITIALIZED, false)) return

        val salt = newSalt()
        prefs.edit()
            .putBoolean(KEY_INITIALIZED, true)
            .putString(KEY_USERNAME, DEFAULT_USERNAME)
            .putString(KEY_DISPLAY_NAME, DEFAULT_DISPLAY_NAME)
            .putString(KEY_PASSWORD_SALT, salt)
            .putString(KEY_PASSWORD_HASH, hash(DEFAULT_PASSWORD, salt))
            .putBoolean(KEY_REMEMBER, false)
            .putBoolean(KEY_LOGGED_IN, false)
            .putBoolean(KEY_SOUND, true)
            .putBoolean(KEY_HAPTICS, true)
            .putInt(KEY_DIFFICULTY, Difficulty.ADVOCATE.level)
            .apply()
    }

    fun username(context: Context): String =
        store(context).getString(KEY_USERNAME, DEFAULT_USERNAME) ?: DEFAULT_USERNAME

    fun displayName(context: Context): String =
        store(context).getString(KEY_DISPLAY_NAME, DEFAULT_DISPLAY_NAME) ?: DEFAULT_DISPLAY_NAME

    fun setDisplayName(context: Context, name: String) {
        store(context).edit().putString(KEY_DISPLAY_NAME, name.trim()).apply()
    }

    fun remember(context: Context): Boolean = store(context).getBoolean(KEY_REMEMBER, false)

    fun setRemember(context: Context, remember: Boolean) {
        store(context).edit().putBoolean(KEY_REMEMBER, remember).apply()
    }

    fun isLoggedIn(context: Context): Boolean = store(context).getBoolean(KEY_LOGGED_IN, false)

    fun setLoggedIn(context: Context, loggedIn: Boolean) {
        store(context).edit().putBoolean(KEY_LOGGED_IN, loggedIn).apply()
    }

    /** Compare a normalized username and the salted hash created at setup or password change. */
    fun authenticate(context: Context, username: String, password: String): Boolean {
        if (!username.trim().equals(this.username(context), ignoreCase = true)) return false
        val prefs = store(context)
        val salt = prefs.getString(KEY_PASSWORD_SALT, null) ?: return false
        val savedHash = prefs.getString(KEY_PASSWORD_HASH, null) ?: return false
        return constantTimeEquals(savedHash, hash(password, salt))
    }

    fun changePassword(context: Context, currentPassword: String, newPassword: String): PasswordChange {
        val prefs = store(context)
        val salt = prefs.getString(KEY_PASSWORD_SALT, "") ?: ""
        val savedHash = prefs.getString(KEY_PASSWORD_HASH, "") ?: ""
        if (!constantTimeEquals(savedHash, hash(currentPassword, salt))) {
            return PasswordChange.WRONG_CURRENT
        }
        if (newPassword.length < 4) return PasswordChange.TOO_SHORT

        val newSalt = newSalt()
        prefs.edit()
            .putString(KEY_PASSWORD_SALT, newSalt)
            .putString(KEY_PASSWORD_HASH, hash(newPassword, newSalt))
            .apply()
        return PasswordChange.OK
    }

    fun soundEnabled(context: Context): Boolean = store(context).getBoolean(KEY_SOUND, true)

    fun setSoundEnabled(context: Context, enabled: Boolean) {
        store(context).edit().putBoolean(KEY_SOUND, enabled).apply()
    }

    fun hapticsEnabled(context: Context): Boolean = store(context).getBoolean(KEY_HAPTICS, true)

    fun setHapticsEnabled(context: Context, enabled: Boolean) {
        store(context).edit().putBoolean(KEY_HAPTICS, enabled).apply()
    }

    fun difficulty(context: Context): Difficulty =
        Difficulty.fromLevel(store(context).getInt(KEY_DIFFICULTY, Difficulty.ADVOCATE.level))

    fun setDifficulty(context: Context, difficulty: Difficulty) {
        store(context).edit().putInt(KEY_DIFFICULTY, difficulty.level).apply()
    }

    fun tttWins(context: Context): Int = store(context).getInt(KEY_TTT_WINS, 0)

    fun tttLosses(context: Context): Int = store(context).getInt(KEY_TTT_LOSSES, 0)

    fun tttDraws(context: Context): Int = store(context).getInt(KEY_TTT_DRAWS, 0)

    fun recordTttWin(context: Context) {
        store(context).edit().putInt(KEY_TTT_WINS, tttWins(context) + 1).apply()
    }

    fun recordTttLoss(context: Context) {
        store(context).edit().putInt(KEY_TTT_LOSSES, tttLosses(context) + 1).apply()
    }

    fun recordTttDraw(context: Context) {
        store(context).edit().putInt(KEY_TTT_DRAWS, tttDraws(context) + 1).apply()
    }

    fun snakeBest(context: Context): Int = store(context).getInt(KEY_SNAKE_BEST, 0)

    /** Save only a new personal best and report whether the record changed. */
    fun recordSnakeScore(context: Context, score: Int): Boolean {
        if (score <= snakeBest(context)) return false
        store(context).edit().putInt(KEY_SNAKE_BEST, score).apply()
        return true
    }

    fun breakoutBest(context: Context): Int = store(context).getInt(KEY_BREAKOUT_BEST, 0)

    fun recordBreakoutScore(context: Context, score: Int): Boolean {
        if (score <= breakoutBest(context)) return false
        store(context).edit().putInt(KEY_BREAKOUT_BEST, score).apply()
        return true
    }

    fun resetRecords(context: Context) {
        store(context).edit()
            .putInt(KEY_TTT_WINS, 0)
            .putInt(KEY_TTT_LOSSES, 0)
            .putInt(KEY_TTT_DRAWS, 0)
            .putInt(KEY_SNAKE_BEST, 0)
            .putInt(KEY_BREAKOUT_BEST, 0)
            .apply()
    }

    fun logout(context: Context) {
        store(context).edit()
            .putBoolean(KEY_LOGGED_IN, false)
            .putBoolean(KEY_REMEMBER, false)
            .apply()
    }

    private fun newSalt(): String {
        val bytes = ByteArray(16)
        SecureRandom().nextBytes(bytes)
        return Base64.encodeToString(bytes, Base64.NO_WRAP)
    }

    // This is the original offline demo's password format. Keep it consistent
    // across account setup, sign-in, and password changes.
    private fun hash(password: String, salt: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val bytes = digest.digest((salt + password).toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it.toInt() and 0xff) }
    }

    private fun constantTimeEquals(a: String, b: String): Boolean {
        if (a.length != b.length) return false
        var result = 0
        for (i in a.indices) result = result or (a[i].code xor b[i].code)
        return result == 0
    }
}
