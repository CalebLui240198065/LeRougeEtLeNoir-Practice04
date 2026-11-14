package com.rougenoir.app

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate
import com.rougenoir.app.data.BingoDatabase
import com.rougenoir.app.data.Prefs

/** Sets up the local demo account, theme, and shared app services. */
class RougeNoirApp : Application() {
    // Open SQLite only when Bingo is used. Both Bingo screens share this connection.
    internal val bingoDatabase by lazy { BingoDatabase(this) }

    override fun onCreate() {
        super.onCreate()
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
        Prefs.ensureInitialized(this)
    }
}
