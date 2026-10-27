package com.doctorknepper.personalore.ui.screens

import androidx.appcompat.app.AppCompatActivity
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Backspace
import androidx.compose.material.icons.outlined.Fingerprint
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.doctorknepper.personalore.data.Store
import kotlinx.coroutines.delay

@Composable
fun LockScreen(activity: AppCompatActivity, onUnlocked: () -> Unit) {
    var pin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf(false) }
    val bioReady = Store.biometricEnabled &&
        BiometricManager.from(activity).canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK) == BiometricManager.BIOMETRIC_SUCCESS

    fun biometric() {
        val prompt = BiometricPrompt(activity, ContextCompat.getMainExecutor(activity), object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) { onUnlocked() }
        })
        prompt.authenticate(
            BiometricPrompt.PromptInfo.Builder().setTitle("Unlock PersonaLore").setNegativeButtonText("Use PIN")
                .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_WEAK).build(),
        )
    }
    LaunchedEffect(Unit) { if (bioReady) biometric() }
    LaunchedEffect(error) { if (error) { delay(700); error = false; pin = "" } }

    fun press(d: String) {
        if (error || pin.length >= Store.pinLength) return
        pin += d
        if (pin.length == Store.pinLength) { if (Store.checkPin(pin)) onUnlocked() else error = true }
    }

    Column(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).safeDrawingPadding().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
    ) {
        Icon(Icons.Outlined.Lock, null, Modifier.size(40.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(12.dp))
        Text("PersonaLore", style = MaterialTheme.typography.headlineMedium)
        Text(if (error) "That PIN didn't match" else "Enter your PIN", style = MaterialTheme.typography.bodyMedium,
            color = if (error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(20.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            repeat(Store.pinLength) { i ->
                Box(Modifier.size(14.dp).clip(CircleShape)
                    .background(if (i < pin.length) (if (error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary) else MaterialTheme.colorScheme.surfaceContainerHighest))
            }
        }
        Spacer(Modifier.height(32.dp))
        val rows = listOf(listOf("1", "2", "3"), listOf("4", "5", "6"), listOf("7", "8", "9"), listOf("bio", "0", "del"))
        rows.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(20.dp), modifier = Modifier.padding(vertical = 8.dp)) {
                row.forEach { k ->
                    Box(
                        Modifier.size(72.dp).clip(CircleShape)
                            .then(if (k.length == 1) Modifier.border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape) else Modifier)
                            .clickable(enabled = k != "bio" || bioReady) {
                                when (k) { "del" -> pin = pin.dropLast(1); "bio" -> biometric(); else -> press(k) }
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        when (k) {
                            "del" -> Icon(Icons.AutoMirrored.Outlined.Backspace, "Delete")
                            "bio" -> if (bioReady) Icon(Icons.Outlined.Fingerprint, "Use fingerprint", Modifier.size(32.dp), tint = MaterialTheme.colorScheme.primary)
                            else -> Text(k, style = MaterialTheme.typography.headlineSmall)
                        }
                    }
                }
            }
        }
    }
}
