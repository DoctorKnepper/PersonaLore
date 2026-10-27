package com.doctorknepper.personalore

import android.graphics.Color
import android.os.Bundle
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.foundation.isSystemInDarkTheme
import com.doctorknepper.personalore.data.Store
import com.doctorknepper.personalore.ui.AppRoot
import com.doctorknepper.personalore.ui.PersonaLoreTheme
import com.doctorknepper.personalore.ui.ThemeMode
import com.doctorknepper.personalore.util.Reminders

class MainActivity : AppCompatActivity() {

    /** True while the lock screen should show. */
    var locked by mutableStateOf(false)
    private var backgroundedAt = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Store.init(this)
        locked = Store.lockEnabled
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
            PersonaLoreTheme(Store.colorway, Store.themeMode) {
                AppRoot(activity = this, locked = locked && Store.lockEnabled, onUnlocked = { locked = false })
            }
        }
    }

    override fun onStart() {
        super.onStart()
        // Re-lock after more than a minute away. The short grace period keeps the journal open
        // while you pick a photo or answer a permission dialog.
        if (Store.lockEnabled && backgroundedAt > 0 && System.currentTimeMillis() - backgroundedAt > 60_000) locked = true
        Store.ensureDailyPrompt()
    }

    override fun onStop() {
        super.onStop()
        backgroundedAt = System.currentTimeMillis()
    }
}
