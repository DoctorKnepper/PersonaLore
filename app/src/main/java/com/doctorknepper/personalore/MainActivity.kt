package com.doctorknepper.personalore

import android.graphics.Color
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.LaunchedEffect
import com.doctorknepper.personalore.data.Store
import com.doctorknepper.personalore.ui.AppLock
import com.doctorknepper.personalore.ui.AppRoot
import com.doctorknepper.personalore.ui.PersonaLoreTheme
import com.doctorknepper.personalore.ui.ThemeMode
import com.doctorknepper.personalore.util.Reminders

class MainActivity : AppCompatActivity() {

    private var backgroundedAt = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Store.init(this)
        applySecureFlag(Store.blockScreenshots)
        Reminders.schedule(this)
        setContent {
            val dark = when (Store.themeMode) {
                ThemeMode.DARK -> true
                ThemeMode.LIGHT -> false
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
            }
            LaunchedEffect(dark) {
                val style = if (dark) SystemBarStyle.dark(Color.TRANSPARENT)
                else SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
            }
            LaunchedEffect(Store.blockScreenshots) { applySecureFlag(Store.blockScreenshots) }
            PersonaLoreTheme(Store.colorway, Store.themeMode) { AppRoot(activity = this) }
        }
    }

    /** Blocks screenshots and blanks the preview in the recent-apps switcher. */
    private fun applySecureFlag(on: Boolean) {
        if (on) window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        else window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
    }

    override fun onStart() {
        super.onStart()
        val away = System.currentTimeMillis() - backgroundedAt
        val viaPicker = AppLock.consumePickerGrace()
        if (Store.lockEnabled && Store.unlocked && backgroundedAt > 0 && !viaPicker && away >= Store.relockAfterSec * 1000L) Store.lock()
        backgroundedAt = 0
        Store.ensureDailyPrompt()
    }

    override fun onStop() {
        super.onStop()
        if (isChangingConfigurations) return
        backgroundedAt = System.currentTimeMillis()
        // Lock straight away (rather than on return) so the journal isn't sitting decrypted in memory.
        if (Store.relockAfterSec == 0 && System.currentTimeMillis() - AppLock.leftForPickerAt > 5 * 60_000) Store.lock()
    }
}
