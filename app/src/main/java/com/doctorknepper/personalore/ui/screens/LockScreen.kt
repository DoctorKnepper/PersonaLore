package com.doctorknepper.personalore.ui.screens

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Backspace
import androidx.compose.material.icons.outlined.Fingerprint
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.doctorknepper.personalore.data.Store
import com.doctorknepper.personalore.ui.Snacks
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Shown while the journal is locked, or if it can't be opened at all. Covers the whole app. */
@Composable
fun LockScreen(activity: AppCompatActivity) {
    // Back from the lock screen leaves the app instead of reaching the journal underneath.
    BackHandler { activity.moveTaskToBack(true) }
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).clickable(enabled = false) {}) {
        if (!Store.lockEnabled) OpenErrorScreen() else PinScreen(activity)
    }
}

@Composable
private fun PinScreen(activity: AppCompatActivity) {
    val scope = rememberCoroutineScope()
    var pin by remember { mutableStateOf("") }
    var message by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var lockoutMs by remember { mutableLongStateOf(Store.lockoutRemainingMs()) }
    var showRecovery by remember { mutableStateOf(false) }
    val pinLength = Store.pinLength
    val bioCipherAvailable = remember { Store.biometricUnlockCipher() != null }
    val bioReady = bioCipherAvailable &&
        BiometricManager.from(activity).canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG) == BiometricManager.BIOMETRIC_SUCCESS

    LaunchedEffect(lockoutMs > 0) {
        while (lockoutMs > 0) { delay(1000); lockoutMs = Store.lockoutRemainingMs() }
    }

    fun biometric() {
        val cipher = Store.biometricUnlockCipher() ?: run { message = "Fingerprint unlock was turned off because your fingerprints changed. Use your PIN."; return }
        val prompt = BiometricPrompt(activity, ContextCompat.getMainExecutor(activity), object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                val c = result.cryptoObject?.cipher ?: return
                if (!Store.unlockWithBiometric(c)) message = "Fingerprint unlock didn't work. Use your PIN."
            }
        })
        prompt.authenticate(
            BiometricPrompt.PromptInfo.Builder().setTitle("Unlock PersonaLore").setNegativeButtonText("Use PIN")
                .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG).build(),
            BiometricPrompt.CryptoObject(cipher),
        )
    }
    LaunchedEffect(Unit) { if (bioReady && lockoutMs == 0L) biometric() }
    LaunchedEffect(error) { if (error) { delay(700); error = false; pin = "" } }

    fun submit(p: String) {
        busy = true
        scope.launch {
            when (val r = withContext(Dispatchers.Default) { Store.unlockWithPin(p) }) {
                Store.PinResult.Ok -> {}
                is Store.PinResult.OkWithNewRecoveryKey -> Store.recoveryKeyToShow = r.recoveryKey
                Store.PinResult.Wrong -> { error = true; message = "That PIN didn't match"; lockoutMs = Store.lockoutRemainingMs() }
                is Store.PinResult.LockedOut -> { lockoutMs = r.ms; pin = "" }
                Store.PinResult.KeystoreLost -> { pin = ""; message = "This phone's secure key storage was reset, so your PIN can't open the journal anymore. Use your recovery key." }
            }
            busy = false
        }
    }

    fun press(d: String) {
        if (error || busy || lockoutMs > 0 || pin.length >= pinLength) return
        pin += d
        if (pin.length == pinLength) submit(pin)
    }

    Column(
        Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState()).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
    ) {
        Icon(Icons.Outlined.Lock, null, Modifier.size(40.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(12.dp))
        Text("PersonaLore", style = MaterialTheme.typography.headlineMedium)
        Text(
            when {
                lockoutMs > 0 -> "Too many tries. Try again in ${(lockoutMs + 999) / 1000}s"
                busy -> "Unlocking…"
                else -> message ?: "Enter your PIN"
            },
            style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center,
            color = if (error || lockoutMs > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(20.dp))
        if (busy) CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp)
        else Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            repeat(pinLength) { i ->
                Box(Modifier.size(14.dp).clip(CircleShape)
                    .background(if (i < pin.length) (if (error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary) else MaterialTheme.colorScheme.surfaceContainerHighest))
            }
        }
        Spacer(Modifier.height(28.dp))
        val rows = listOf(listOf("1", "2", "3"), listOf("4", "5", "6"), listOf("7", "8", "9"), listOf("bio", "0", "del"))
        rows.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(20.dp), modifier = Modifier.padding(vertical = 8.dp)) {
                row.forEach { k ->
                    Box(
                        Modifier.size(72.dp).clip(CircleShape)
                            .then(if (k.length == 1) Modifier.border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape) else Modifier)
                            .clickable(enabled = (k != "bio" || bioReady) && lockoutMs == 0L && !busy) {
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
        Spacer(Modifier.height(12.dp))
        TextButton(onClick = { showRecovery = true }) { Text("Forgot PIN?") }
    }

    if (showRecovery) ForgotPinDialog(onDismiss = { showRecovery = false })
}

@Composable
private fun ForgotPinDialog(onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    var key by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var wrong by remember { mutableStateOf(false) }
    var erase by remember { mutableStateOf(false) }
    if (erase) { EraseDialog(onDismiss = { erase = false }); return }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Forgot your PIN?") },
        text = {
            Column {
                if (Store.hasRecoveryKey) {
                    Text("Enter the recovery key you saved when you set up the lock. Then you'll choose a new PIN.", style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(key, { key = it; wrong = false }, label = { Text("Recovery key") }, singleLine = true,
                        placeholder = { Text("XXXXX-XXXXX-XXXXX-XXXXX") }, isError = wrong,
                        supportingText = if (wrong) ({ Text("That recovery key didn't work") }) else null, modifier = Modifier.fillMaxWidth())
                } else Text("This journal has no recovery key.", style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(12.dp))
                Text("Lost it too? You can erase the journal and start over, then restore a backup file if you have one.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                TextButton(onClick = { erase = true }) { Text("Erase journal…", color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            if (Store.hasRecoveryKey) TextButton(enabled = key.isNotBlank() && !busy, onClick = {
                busy = true
                scope.launch {
                    val ok = withContext(Dispatchers.Default) { Store.unlockWithRecoveryKey(key) }
                    busy = false
                    if (ok) { Store.mustSetNewPin = true; onDismiss() } else wrong = true
                }
            }) { Text(if (busy) "Checking…" else "Unlock") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
fun EraseDialog(onDismiss: () -> Unit) {
    var typed by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Erase the whole journal?") },
        text = {
            Column {
                Text("Every entry, photo and voice note on this phone will be permanently deleted. This can't be undone. Afterwards you can restore a backup file from Settings.",
                    style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(typed, { typed = it }, label = { Text("Type ERASE to confirm") }, singleLine = true)
            }
        },
        confirmButton = {
            TextButton(enabled = typed.trim().equals("ERASE", ignoreCase = true), onClick = {
                Store.eraseEverything(); onDismiss()
                Snacks.show("Journal erased. You can restore a backup from Settings.")
            }) { Text("Erase everything", color = MaterialTheme.colorScheme.error) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun OpenErrorScreen() {
    var erase by remember { mutableStateOf(false) }
    Column(
        Modifier.fillMaxSize().safeDrawingPadding().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
    ) {
        Icon(Icons.Outlined.Lock, null, Modifier.size(40.dp), tint = MaterialTheme.colorScheme.error)
        Spacer(Modifier.height(12.dp))
        Text("Can't open your journal", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(8.dp))
        Text(Store.openError ?: "Something went wrong opening your journal.", textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(20.dp))
        Button(onClick = { erase = true }) { Text("Erase and start over") }
    }
    if (erase) EraseDialog(onDismiss = { erase = false })
}
