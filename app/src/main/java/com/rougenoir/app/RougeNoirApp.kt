package com.rougenoir.app

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate
import com.rougenoir.app.data.Prefs
import com.rougenoir.app.data.RoundDatabase

/** Sets up the local demo account, theme, and shared app services. */
class RougeNoirApp : Application() {
    // Games and the history screen share one connection and background queue.
    internal val roundDatabase by lazy { RoundDatabase(this) }

    override fun onCreate() {
        super.onCreate()
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
        Prefs.ensureInitialized(this)
    }
}
