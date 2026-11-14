package com.rougenoir.app.ui

import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.rougenoir.app.R
import com.rougenoir.app.RougeNoirApp
import com.rougenoir.app.data.Prefs
import com.rougenoir.app.game.RoundResult
import java.util.UUID

/**
 * All screens use the same edge-to-edge treatment: the parchment content is inset above
 * and below the system bars, while ink scrims keep the status and navigation areas
 * legible even when a scroll view has moved its header out of sight.
 */
abstract class BaseActivity : AppCompatActivity() {

    private var insetContent: View? = null
    private var statusScrim: View? = null
    private var navigationScrim: View? = null

    /**
     * Queue the finished result independently of the board's remaining lifetime.
     * A retry uses the original round ID and clock time even if gameplay has moved on.
     */
    internal fun recordCompletedRound(result: RoundResult) {
        val time = System.currentTimeMillis()
        val id = UUID.randomUUID().toString()
        val username = Prefs.username(this)
        val database = (application as RougeNoirApp).roundDatabase

        fun save() {
            database.saveRound(id, username, result, time) { saved ->
                if (saved.isFailure && !isFinishing && !isDestroyed) {
                    AlertDialog.Builder(this)
                        .setMessage(R.string.history_save_failed)
                        .setPositiveButton(R.string.history_retry) { _, _ -> save() }
                        .setNegativeButton(R.string.close, null)
                        .show()
                }
            }
        }
        save()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT
        WindowInsetsControllerCompat(window, window.decorView).apply {
            isAppearanceLightStatusBars = false
            isAppearanceLightNavigationBars = false
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
            window.isStatusBarContrastEnforced = false
        }
    }

    override fun onContentChanged() {
        super.onContentChanged()
        val content = findViewById<View>(R.id.contentRoot) ?: return
        if (insetContent === content) return
        insetContent = content

        val root = content.parent as? FrameLayout
        if (root != null) {
            statusScrim = View(this).apply {
                setBackgroundColor(ContextCompat.getColor(this@BaseActivity, R.color.ink))
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                elevation = 100f
            }
            navigationScrim = View(this).apply {
                setBackgroundColor(ContextCompat.getColor(this@BaseActivity, R.color.ink))
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                elevation = 100f
            }
            root.addView(
                statusScrim,
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    0,
                    Gravity.TOP
                )
            )
            root.addView(
                navigationScrim,
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    0,
                    Gravity.BOTTOM
                )
            )
        }

        // Remember layout padding so repeated inset dispatches do not compound it.
        val baseLeft = content.paddingLeft
        val baseTop = content.paddingTop
        val baseRight = content.paddingRight
        val baseBottom = content.paddingBottom

        ViewCompat.setOnApplyWindowInsetsListener(content) { view, insets ->
            val bars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.ime()
            )
            val statusBars = insets.getInsets(WindowInsetsCompat.Type.statusBars())
            val navigationBars = insets.getInsets(WindowInsetsCompat.Type.navigationBars())
            view.setPadding(
                baseLeft,
                baseTop + bars.top,
                baseRight,
                baseBottom + bars.bottom
            )
            (statusScrim?.layoutParams as? FrameLayout.LayoutParams)?.let {
                it.height = statusBars.top
                statusScrim?.layoutParams = it
            }
            (navigationScrim?.layoutParams as? FrameLayout.LayoutParams)?.let {
                it.height = navigationBars.bottom
                navigationScrim?.layoutParams = it
            }
            insets
        }
        ViewCompat.requestApplyInsets(content)
    }
}
